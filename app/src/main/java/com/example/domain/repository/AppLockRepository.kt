package com.example.domain.repository

import com.example.data.db.AppLockDao
import com.example.data.db.LockedAppEntity
import kotlinx.coroutines.flow.Flow

class AppLockRepository(private val appLockDao: AppLockDao) {
    val allLockedApps: Flow<List<LockedAppEntity>> = appLockDao.getAllLockedApps()

    suspend fun lockApp(packageName: String, appLabel: String) {
        appLockDao.insertLockedApp(
            LockedAppEntity(
                packageName = packageName,
                appLabel = appLabel,
                isLocked = true
            )
        )
    }

    suspend fun unlockApp(packageName: String) {
        appLockDao.deleteLockedApp(packageName)
    }
}
