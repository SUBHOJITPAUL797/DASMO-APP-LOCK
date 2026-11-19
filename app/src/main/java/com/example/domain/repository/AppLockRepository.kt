package com.example.domain.repository

import android.content.Context
import com.example.data.db.AppLockDao
import com.example.data.db.LockedAppEntity
import com.example.util.SecurePreferences
import com.example.util.VaultStorageManager
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject

object LockedAppBackupCodec {
    fun encode(apps: List<LockedAppEntity>): String {
        val arr = JSONArray()
        apps.filter { it.isLocked && it.packageName.isNotBlank() }.forEach { app ->
            val obj = JSONObject()
            obj.put("pkg", app.packageName)
            obj.put("label", app.appLabel)
            obj.put("ts", app.timestamp)
            arr.put(obj)
        }
        return arr.toString()
    }

    fun decode(rawJson: String): List<LockedAppEntity> {
        if (rawJson.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(rawJson)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val pkg = obj.optString("pkg", "").trim()
                    if (pkg.isEmpty()) continue
                    val label = obj.optString("label", pkg).ifBlank { pkg }
                    val ts = obj.optLong("ts", System.currentTimeMillis())
                    add(
                        LockedAppEntity(
                            packageName = pkg,
                            appLabel = label,
                            isLocked = true,
                            timestamp = ts
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}

class AppLockRepository(
    private val appLockDao: AppLockDao,
    private val securePrefs: SecurePreferences? = null
) {
    val allLockedApps: Flow<List<LockedAppEntity>> = appLockDao.getAllLockedApps()

    suspend fun lockApp(packageName: String, appLabel: String) {
        appLockDao.insertLockedApp(
            LockedAppEntity(
                packageName = packageName,
                appLabel = appLabel,
                isLocked = true
            )
        )
        syncBackupNow()
    }

    suspend fun unlockApp(packageName: String) {
        appLockDao.deleteLockedApp(packageName)
        syncBackupNow()
    }

    suspend fun syncBackupNow() {
        val prefs = securePrefs ?: return
        val snapshot = appLockDao.getAllLockedAppsSnapshot()
        prefs.saveLockedAppsBackupJson(LockedAppBackupCodec.encode(snapshot))
    }

    /**
     * Ensures locked apps survive app updates, database resets, or reinstalls:
     * - If Room already contains locked apps, ensures the encrypted backup is up to date.
     * - If Room is empty, restores locked apps from encrypted SecurePreferences or persistent vault backup.
     */
    suspend fun restoreLockedAppsIfNeeded(context: Context? = null): Int {
        val existing = appLockDao.getAllLockedAppsSnapshot()
        if (existing.isNotEmpty()) {
            val prefs = securePrefs
            if (prefs != null && prefs.getLockedAppsBackupJson().isBlank()) {
                prefs.saveLockedAppsBackupJson(LockedAppBackupCodec.encode(existing))
            }
            return existing.size
        }

        val prefs = securePrefs ?: return 0
        var backupJson = prefs.getLockedAppsBackupJson()
        if (backupJson.isBlank() && context != null) {
            backupJson = VaultStorageManager.readBackedUpKey(
                context = context,
                key = SecurePreferences.KEY_LOCKED_APPS_BACKUP
            )
            if (backupJson.isNotBlank()) {
                prefs.putStringWithoutBackup(SecurePreferences.KEY_LOCKED_APPS_BACKUP, backupJson)
            }
        }

        val restored = LockedAppBackupCodec.decode(backupJson)
        if (restored.isNotEmpty()) {
            appLockDao.insertLockedApps(restored)
        }
        return restored.size
    }
}
