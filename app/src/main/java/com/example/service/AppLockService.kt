package com.example.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.data.db.AppGuardDatabase
import com.example.domain.repository.AppLockRepository
import com.example.presentation.screens.lock.LockActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AppLockService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var repository: AppLockRepository
    private var cachedLockedPackages = mapOf<String, String>()
    private var isRunning = false

    companion object {
        val unlockedApps = mutableSetOf<String>()
        val lastBackgroundTime = mutableMapOf<String, Long>()
        var sessionDurationMillis: Long = 5 * 60 * 1000L // Default: 5 minutes
        var currentForegroundPackage: String? = null
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val dao = AppGuardDatabase.getDatabase(this).appLockDao()
        repository = AppLockRepository(dao)

        serviceScope.launch {
            repository.activeLockedApps.collect { apps ->
                cachedLockedPackages = apps.associate { it.packageName to it.appLabel }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        val notification = NotificationCompat.Builder(this, "app_lock_channel")
            .setContentTitle("App Lock Active")
            .setContentText("Protecting your apps")
            .setSmallIcon(android.R.drawable.ic_secure)
            .setOngoing(true)
            .build()
            
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(1, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, notification)
        }
        
        if (!isRunning) {
            isRunning = true
            startMonitoring()
        }
        
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "app_lock_channel",
                "App Lock Service",
                NotificationManager.IMPORTANCE_LOW
            )
            channel.description = "Used for App Lock monitoring"
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun startMonitoring() {
        val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        serviceScope.launch {
            while (isRunning) {
                val endTime = System.currentTimeMillis()
                val beginTime = endTime - 1000 * 5 // last 5 seconds
                val events = usageStatsManager.queryEvents(beginTime, endTime)
                val event = UsageEvents.Event()
                var latestPackage = currentForegroundPackage
                
                while (events.hasNextEvent()) {
                    events.getNextEvent(event)
                    if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                        latestPackage = event.packageName
                    }
                }
                
                if (latestPackage != null && latestPackage != currentForegroundPackage) {
                    onAppForegrounded(latestPackage)
                }
                
                delay(500) // Poll every 500ms
            }
        }
    }

    private fun onAppForegrounded(targetPackageName: String) {
        if (targetPackageName == packageName) {
            currentForegroundPackage = targetPackageName
            return
        }

        if (currentForegroundPackage != targetPackageName && currentForegroundPackage != null) {
            if (currentForegroundPackage != packageName) {
                lastBackgroundTime[currentForegroundPackage!!] = System.currentTimeMillis()
            }
        }
        currentForegroundPackage = targetPackageName

        val securePrefs = com.example.util.SecurePreferences(this)
        val hiddenAppsStr = securePrefs.getString("hidden_apps", "") ?: ""
        val hiddenApps = hiddenAppsStr.split(",").filter { it.isNotEmpty() }.toSet()
        val isHidden = hiddenApps.contains(targetPackageName)
        val isLocked = cachedLockedPackages.containsKey(targetPackageName)

        if (isLocked || isHidden) {
            val appLabel = cachedLockedPackages[targetPackageName] ?: try {
                val pm = packageManager
                val info = pm.getApplicationInfo(targetPackageName, 0)
                pm.getApplicationLabel(info).toString()
            } catch (e: Exception) {
                targetPackageName
            }

            val isUnlocked = unlockedApps.contains(targetPackageName)
            val bgTime = lastBackgroundTime[targetPackageName]
            val timeInBackground = if (bgTime != null) System.currentTimeMillis() - bgTime else 0L

            val currentTimeout = securePrefs.getString("auto_lock_timeout", "300000").toLongOrNull() ?: sessionDurationMillis

            if (!isUnlocked || timeInBackground > currentTimeout) {
                unlockedApps.remove(targetPackageName)
                launchLockScreen(targetPackageName, appLabel, isHidden)
            }
        }
    }

    private fun launchLockScreen(targetPackage: String, appLabel: String, isHidden: Boolean) {
        val intent = Intent(this, LockActivity::class.java).apply {
            putExtra("TARGET_PACKAGE", targetPackage)
            putExtra("APP_LABEL", appLabel)
            putExtra("IS_HIDDEN", isHidden)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        startActivity(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        serviceScope.cancel()
    }
}
