package com.example.service

import com.example.domain.model.SecuritySettings
import java.util.concurrent.ConcurrentHashMap

sealed interface ForegroundAction {
    data object Ignore : ForegroundAction
    data class RequireLock(
        val packageName: String,
        val isHidden: Boolean,
        val isUninstallProtection: Boolean = false
    ) : ForegroundAction
    data class AllowUnlocked(val packageName: String) : ForegroundAction
}

class LockSessionTracker(
    val ownPackageName: String,
    val unlockedApps: MutableSet<String> = ConcurrentHashMap.newKeySet(),
    val lastBackgroundTime: MutableMap<String, Long> = ConcurrentHashMap()
) {
    @Volatile
    var currentForegroundPackage: String? = null

    fun onForegroundAppChanged(
        targetPackageName: String,
        lockedPackages: Set<String>,
        settings: SecuritySettings,
        nowMillis: Long = System.currentTimeMillis()
    ): ForegroundAction {
        if (targetPackageName == ownPackageName) {
            currentForegroundPackage = targetPackageName
            return ForegroundAction.Ignore
        }

        val previousPkg = currentForegroundPackage
        if (previousPkg != null && previousPkg != targetPackageName && previousPkg != ownPackageName) {
            lastBackgroundTime[previousPkg] = nowMillis
        }
        currentForegroundPackage = targetPackageName

        val isUninstallTarget = settings.uninstallProtectionEnabled && isPackageInstallerApp(targetPackageName)
        val isHidden = settings.hiddenApps.contains(targetPackageName)
        val isLocked = lockedPackages.contains(targetPackageName)

        if (!isLocked && !isHidden && !isUninstallTarget) {
            return ForegroundAction.Ignore
        }

        val isUnlocked = unlockedApps.contains(targetPackageName)
        val bgTime = lastBackgroundTime[targetPackageName]
        val timeInBackground = if (bgTime != null) (nowMillis - bgTime).coerceAtLeast(0L) else 0L

        return if (!isUnlocked || timeInBackground > settings.autoLockTimeoutMillis) {
            unlockedApps.remove(targetPackageName)
            ForegroundAction.RequireLock(
                packageName = targetPackageName,
                isHidden = isHidden,
                isUninstallProtection = isUninstallTarget
            )
        } else {
            ForegroundAction.AllowUnlocked(packageName = targetPackageName)
        }
    }

    fun markAppUnlocked(packageName: String) {
        if (packageName.isNotEmpty()) {
            unlockedApps.add(packageName)
            lastBackgroundTime.remove(packageName)
        }
    }

    fun onScreenOff(nowMillis: Long = System.currentTimeMillis()) {
        val active = currentForegroundPackage
        if (active != null && active != ownPackageName) {
            lastBackgroundTime[active] = nowMillis
        }
        unlockedApps.clear()
        currentForegroundPackage = null
    }

    companion object {
        private val INSTALLER_PACKAGES = setOf(
            "com.android.packageinstaller",
            "com.google.android.packageinstaller",
            "com.samsung.android.packageinstaller",
            "com.miui.packageinstaller",
            "com.coloros.packageinstaller",
            "com.oppo.packageinstaller",
            "com.vivo.packageinstaller",
            "com.sec.android.app.samsungapps"
        )

        fun isPackageInstallerApp(pkg: String): Boolean = INSTALLER_PACKAGES.contains(pkg.lowercase())
    }
}
