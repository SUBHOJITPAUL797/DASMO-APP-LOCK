package com.example.util

import android.content.Context
import android.util.Log

class SecurePreferences(context: Context) {
    private val appContext = context.applicationContext
    private val securePrefs = appContext.getSharedPreferences("secure_appguard_prefs", Context.MODE_PRIVATE)
    private val oldPrefs = appContext.getSharedPreferences("appguard_prefs", Context.MODE_PRIVATE)

    init {
        migrateIfNeeded()
        // If this is a fresh install after uninstallation, automatically restore from persistent backup!
        if (!hasMasterPin()) {
            val restored = VaultStorageManager.restoreVaultBackup(appContext, this)
            if (restored) {
                Log.d("SecurePreferences", "Restored PIN and settings from persistent vault backup!")
            }
        }
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

    fun contains(key: String): Boolean {
        return securePrefs.contains(key)
    }

    fun hasMasterPin(): Boolean {
        return securePrefs.contains("app_pin") && getString("app_pin", "").isNotBlank()
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

            // Automatically persist critical security settings so they survive app uninstall!
            if (key in listOf(
                    "app_pin",
                    "security_question",
                    "security_answer",
                    "decoy_pin",
                    "uninstall_protection",
                    "calculator_disguise",
                    "auto_lock_timeout",
                    "dynamic_time_pin",
                    "scramble_keypad",
                    "break_in_siren",
                    "prevent_screenshots"
                )
            ) {
                VaultStorageManager.saveVaultBackup(appContext, this)
            }
        } catch (e: Exception) {
            Log.e("SecurePreferences", "Failed to encrypt key: $key", e)
        }
    }

    fun syncPersistentBackup() {
        VaultStorageManager.saveVaultBackup(appContext, this)
    }

    fun getSecurityQuestion(): String {
        return getString("security_question", "")
    }

    fun setSecurityRecovery(question: String, answer: String) {
        putString("security_question", question.trim())
        putString("security_answer", answer.trim().lowercase())
    }

    fun verifySecurityAnswer(inputAnswer: String): Boolean {
        val saved = getString("security_answer", "")
        if (saved.isBlank()) return false
        return saved.equals(inputAnswer.trim().lowercase(), ignoreCase = true)
    }
}
