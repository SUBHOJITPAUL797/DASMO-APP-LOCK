package com.example.util

import android.content.Context
import android.util.Log

class SecurePreferences(context: Context) {
    private val securePrefs = context.getSharedPreferences("secure_appguard_prefs", Context.MODE_PRIVATE)
    private val oldPrefs = context.getSharedPreferences("appguard_prefs", Context.MODE_PRIVATE)

    init {
        migrateIfNeeded()
    }

    private fun migrateIfNeeded() {
        if (!securePrefs.contains("app_pin") && oldPrefs.contains("app_pin")) {
            val oldPin = oldPrefs.getString("app_pin", "1234") ?: "1234"
            try {
                putString("app_pin", oldPin)
                Log.d("SecurePreferences", "Successfully migrated old PIN to secure storage")
            } catch (e: Exception) {
                Log.e("SecurePreferences", "Failed to migrate PIN", e)
            }
        }
    }

    fun getString(key: String, defaultValue: String): String {
        val encryptedValue = securePrefs.getString(key, null) ?: return defaultValue
        return try {
            CryptoManager.decrypt(encryptedValue)
        } catch (e: Exception) {
            Log.e("SecurePreferences", "Failed to decrypt key: $key. Returning default.", e)
            defaultValue
        }
    }

    fun putString(key: String, value: String) {
        try {
            val encryptedValue = CryptoManager.encrypt(value)
            securePrefs.edit().putString(key, encryptedValue).apply()
        } catch (e: Exception) {
            Log.e("SecurePreferences", "Failed to encrypt key: $key", e)
        }
    }
}
