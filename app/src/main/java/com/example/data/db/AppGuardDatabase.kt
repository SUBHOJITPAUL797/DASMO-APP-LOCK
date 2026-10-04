package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.db.dao.AppLockDao
import com.example.data.db.entities.LockedApp

@Database(entities = [LockedApp::class], version = 1, exportSchema = false)
abstract class AppGuardDatabase : RoomDatabase() {
    abstract fun appLockDao(): AppLockDao

    companion object {
        @Volatile
        private var INSTANCE: AppGuardDatabase? = null

        fun getDatabase(context: Context): AppGuardDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppGuardDatabase::class.java,
                    "appguard_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
