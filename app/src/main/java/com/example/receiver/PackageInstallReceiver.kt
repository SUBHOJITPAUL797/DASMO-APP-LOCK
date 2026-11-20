package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.data.db.AppGuardDatabase
import com.example.domain.repository.AppLockRepository
import com.example.util.SecurePreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Automatically locks newly installed launchable apps when the user has enabled
 * "Auto-Lock Newly Installed Apps" in Settings -> Auto-Lock.
 * Ignores in-place app updates (EXTRA_REPLACING = true) and DASMO LOCK itself.
 */
class PackageInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_PACKAGE_ADDED) return
        if (intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) return

        val installedPackage = intent.data?.schemeSpecificPart?.trim().orEmpty()
        if (installedPackage.isEmpty() || installedPackage == context.packageName) return

        val appContext = context.applicationContext
        val securePrefs = SecurePreferences.getInstance(appContext)
        if (!securePrefs.currentSettings().autoLockNewAppsEnabled) return

        val pm = appContext.packageManager
        if (pm.getLaunchIntentForPackage(installedPackage) == null) return

        val label = try {
            val info = pm.getApplicationInfo(installedPackage, 0)
            pm.getApplicationLabel(info).toString().ifBlank { installedPackage }
        } catch (_: Exception) {
            installedPackage
        }

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao = AppGuardDatabase.getDatabase(appContext).appLockDao()
                val repository = AppLockRepository(dao, securePrefs)
                repository.lockApp(installedPackage, label)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
