package com.example.data.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "locked_apps")
data class LockedApp(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "package_name") val packageName: String,
    @ColumnInfo(name = "app_label") val appLabel: String,
    @ColumnInfo(name = "is_locked") val isLocked: Boolean = true,
    @ColumnInfo(name = "lock_added_at") val lockAddedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "schedule_id") val scheduleId: Int? = null
)
