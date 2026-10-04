package com.example.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.db.entities.LockedApp
import kotlinx.coroutines.flow.Flow

@Dao
interface AppLockDao {
    @Query("SELECT * FROM locked_apps ORDER BY app_label ASC")
    fun getAllLockedApps(): Flow<List<LockedApp>>

    @Query("SELECT * FROM locked_apps WHERE is_locked = 1")
    fun getActiveLockedApps(): Flow<List<LockedApp>>

    @Query("SELECT * FROM locked_apps WHERE package_name = :packageName LIMIT 1")
    suspend fun getLockedAppByPackage(packageName: String): LockedApp?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLockedApp(app: LockedApp)

    @Query("DELETE FROM locked_apps WHERE package_name = :packageName")
    suspend fun deleteLockedApp(packageName: String)
}
