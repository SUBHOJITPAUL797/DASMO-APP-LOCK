package com.example.domain.repository

import com.example.data.db.dao.AppLockDao
import com.example.data.db.entities.LockedApp
import kotlinx.coroutines.flow.Flow

class AppLockRepository(private val appLockDao: AppLockDao) {
    val allLockedApps: Flow<List<LockedApp>> = appLockDao.getAllLockedApps()
    val activeLockedApps: Flow<List<LockedApp>> = appLockDao.getActiveLockedApps()

    suspend fun getLockedAppByPackage(packageName: String): LockedApp? {
        return appLockDao.getLockedAppByPackage(packageName)
    }

    suspend fun lockApp(packageName: String, appLabel: String) {
        val existing = appLockDao.getLockedAppByPackage(packageName)
        if (existing == null) {
            appLockDao.insertLockedApp(LockedApp(packageName = packageName, appLabel = appLabel, isLocked = true))
        } else {
            appLockDao.insertLockedApp(existing.copy(isLocked = true))
        }
    }

    suspend fun unlockApp(packageName: String) {
        appLockDao.deleteLockedApp(packageName)
    }
}
