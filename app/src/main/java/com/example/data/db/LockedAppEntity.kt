package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "locked_apps")
data class LockedAppEntity(
    @PrimaryKey val packageName: String,
    val appLabel: String,
    val isLocked: Boolean = true,
    val timestamp: Long = System.currentTimeMillis()
)
