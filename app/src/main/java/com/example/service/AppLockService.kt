package com.example.service

import android.app.AppOpsManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.os.Process
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.db.AppGuardDatabase
import com.example.domain.model.SecuritySettings
import com.example.domain.repository.AppLockRepository
import com.example.domain.repository.LockedAppBackupCodec
import com.example.presentation.screens.lock.LockActivity
import com.example.util.SecurePreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

class AppLockService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var repository: AppLockRepository
    private lateinit var securePrefs: SecurePreferences
    private lateinit var tracker: LockSessionTracker

    @Volatile
    private var cachedLockedPackages: Map<String, String> = emptyMap()

    @Volatile
    private var currentSettings: SecuritySettings = SecuritySettings()

    @Volatile
    private var isRunning = false

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                tracker.onScreenOff()
            }
        }
    }

    companion object {
        private const val TAG = "AppLockService"
        val unlockedApps: MutableSet<String> = ConcurrentHashMap.newKeySet()
        val lastBackgroundTime: MutableMap<String, Long> = ConcurrentHashMap()

        @Volatile
        private var activeTracker: LockSessionTracker? = null

        fun markAppUnlocked(packageName: String) {
            unlockedApps.add(packageName)
            lastBackgroundTime.remove(packageName)
            activeTracker?.markAppUnlocked(packageName)
        }

        fun markPackageInstallersUnlocked() {
            LockSessionTracker.INSTALLER_PACKAGES.forEach { pkg ->
                markAppUnlocked(pkg)
            }
        }

        fun clearUnlockedSessions() {
            unlockedApps.clear()
            lastBackgroundTime.clear()
            activeTracker?.onScreenOff()
        }

        fun hasUsageStatsPermission(context: Context): Boolean {
            return try {
                val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
                val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    appOps.unsafeCheckOpNoThrow(
                        AppOpsManager.OPSTR_GET_USAGE_STATS,
                        Process.myUid(),
                        context.packageName
                    )
                } else {
                    @Suppress("DEPRECATION")
                    appOps.checkOpNoThrow(
                        AppOpsManager.OPSTR_GET_USAGE_STATS,
                        Process.myUid(),
                        context.packageName
                    )
                }
                mode == AppOpsManager.MODE_ALLOWED
            } catch (_: Exception) {
                false
            }
        }

        fun startIfPermitted(context: Context): Boolean {
            if (!Settings.canDrawOverlays(context) || !hasUsageStatsPermission(context)) {
                return false
            }
            return try {
                val serviceIntent = Intent(context, AppLockService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
                true
            } catch (e: Exception) {
                Log.w(TAG, "Deferred starting AppLockService: ${e.message}")
                false
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        securePrefs = SecurePreferences.getInstance(this)
        val dao = AppGuardDatabase.getDatabase(this).appLockDao()
        repository = AppLockRepository(dao, securePrefs)
        currentSettings = securePrefs.currentSettings()

        // Prime locked packages immediately from encrypted backup so there is zero gap after update/boot
        val backedUpApps = LockedAppBackupCodec.decode(securePrefs.getLockedAppsBackupJson())
        if (backedUpApps.isNotEmpty()) {
            cachedLockedPackages = backedUpApps.associate { it.packageName to it.appLabel }
        }

        tracker = LockSessionTracker(
            ownPackageName = packageName,
            unlockedApps = unlockedApps,
            lastBackgroundTime = lastBackgroundTime
        )
        activeTracker = tracker

        registerReceiver(screenOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))

        serviceScope.launch {
            repository.restoreLockedAppsIfNeeded(this@AppLockService)
            repository.allLockedApps.collect { apps ->
                cachedLockedPackages = apps.filter { it.isLocked }.associate { it.packageName to it.appLabel }
                if (isRunning) {
                    updateForegroundNotification()
                }
            }
        }

        serviceScope.launch {
            securePrefs.settingsFlow.collect { updated ->
                currentSettings = updated
            }
        }
    }

    private fun buildServiceNotification(): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val contentIntent = PendingIntent.getActivity(this, 0, launchIntent, pendingFlags)
        val count = cachedLockedPackages.size
        val contentText = when {
            count == 1 -> "Shielding 1 locked app in real time"
            count > 1 -> "Shielding $count locked apps in real time"
            else -> "Shield active • Ready to protect apps"
        }

        return NotificationCompat.Builder(this, "app_lock_channel")
            .setContentTitle("DASMO LOCK Active")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_lock_notification)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateForegroundNotification() {
        try {
            val manager = getSystemService(NotificationManager::class.java)
            manager?.notify(1, buildServiceNotification())
        } catch (_: Exception) {
            // Ignore if notifications are suppressed
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        val notification = buildServiceNotification()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    1,
                    notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(1, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Unable to enter foreground right now: ${e.message}", e)
            stopSelf()
            return START_NOT_STICKY
        }

        if (!isRunning) {
            isRunning = true
            startMonitoring()
        }

        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        startIfPermitted(applicationContext)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "app_lock_channel",
                "App Lock Service",
                NotificationManager.IMPORTANCE_LOW
            )
            channel.description = "Used for DASMO LOCK foreground monitoring"
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun startMonitoring() {
        val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val powerManager = getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
        serviceScope.launch {
            while (isRunning) {
                try {
                    if (powerManager?.isInteractive == false) {
                        delay(450)
                        continue
                    }

                    val endTime = System.currentTimeMillis()
                    val lookbackMs = if (tracker.currentForegroundPackage == null) 120_000L else 5_000L
                    val beginTime = endTime - lookbackMs
                    val events = usageStatsManager.queryEvents(beginTime, endTime)
                    val event = UsageEvents.Event()
                    var latestPackage = tracker.currentForegroundPackage

                    while (events.hasNextEvent()) {
                        events.getNextEvent(event)
                        if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                            latestPackage = event.packageName
                        }
                    }

                    // Fallback after long idle/screen-off when no recent ACTIVITY_RESUMED event fell in the window
                    if (latestPackage == null) {
                        val stats = usageStatsManager.queryUsageStats(
                            UsageStatsManager.INTERVAL_DAILY,
                            endTime - 3_600_000L,
                            endTime
                        )
                        latestPackage = stats?.maxByOrNull { it.lastTimeUsed }?.packageName
                    }

                    if (latestPackage != null && latestPackage != tracker.currentForegroundPackage) {
                        val action = tracker.onForegroundAppChanged(
                            targetPackageName = latestPackage,
                            lockedPackages = cachedLockedPackages.keys,
                            settings = currentSettings,
                            nowMillis = System.currentTimeMillis()
                        )
                        if (action is ForegroundAction.RequireLock && Settings.canDrawOverlays(this@AppLockService)) {
                            val label = resolveAppLabel(action.packageName, action.isUninstallProtection)
                            launchLockScreen(action.packageName, label, action.isHidden)
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Monitoring tick recovered from error: ${e.message}")
                }

                delay(350)
            }
        }
    }

    private fun resolveAppLabel(targetPackageName: String, isUninstallProtection: Boolean): String {
        if (isUninstallProtection) return "Uninstall Protection"
        return cachedLockedPackages[targetPackageName] ?: try {
            val info = packageManager.getApplicationInfo(targetPackageName, 0)
            packageManager.getApplicationLabel(info).toString()
        } catch (_: Exception) {
            targetPackageName
        }
    }

    private fun launchLockScreen(targetPackage: String, appLabel: String, isHidden: Boolean) {
        try {
            val intent = Intent(this, LockActivity::class.java).apply {
                putExtra("TARGET_PACKAGE", targetPackage)
                putExtra("APP_LABEL", appLabel)
                putExtra("IS_HIDDEN", isHidden)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch lock overlay for $targetPackage: ${e.message}", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        activeTracker = null
        try {
            unregisterReceiver(screenOffReceiver)
        } catch (_: Exception) {
        }
        serviceScope.cancel()
    }
}
