package com.example.util

import android.content.Context
import android.util.Log
import com.example.domain.model.SecuritySettings
import com.example.domain.model.UnlockMethod
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SecurePreferences(context: Context) {
    private val appContext = context.applicationContext
    private val securePrefs = appContext.getSharedPreferences("secure_appguard_prefs", Context.MODE_PRIVATE)
    private val oldPrefs = appContext.getSharedPreferences("appguard_prefs", Context.MODE_PRIVATE)

    private val _settingsFlow = MutableStateFlow(SecuritySettings())
    val settingsFlow: StateFlow<SecuritySettings> = _settingsFlow.asStateFlow()

    init {
        migrateIfNeeded()
        if (!hasMasterPin()) {
            val restored = VaultStorageManager.restoreVaultBackup(appContext, this)
            if (restored) {
                Log.d("SecurePreferences", "Restored PIN and settings from persistent vault backup!")
            }
        }
        _settingsFlow.value = readSettingsSnapshot()
    }

    private fun migrateIfNeeded() {
        if (!securePrefs.contains("app_pin") && oldPrefs.contains("app_pin")) {
            val oldPin = oldPrefs.getString("app_pin", "1234") ?: "1234"
            try {
                putStringInternal("app_pin", oldPin, triggerBackup = false)
                Log.d("SecurePreferences", "Successfully migrated old PIN to secure storage")
            } catch (e: Exception) {
                Log.e("SecurePreferences", "Failed to migrate PIN", e)
            }
        }
    }

    private fun readSettingsSnapshot(): SecuritySettings {
        val rawHidden = getString(KEY_HIDDEN_APPS, "")
        val hiddenSet = rawHidden.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()

        val methodName = getString(KEY_UNLOCK_METHOD, UnlockMethod.PIN.name)
        val parsedMethod = UnlockMethod.entries.firstOrNull { it.name == methodName } ?: UnlockMethod.PIN

        return SecuritySettings(
            masterPin = getString(KEY_APP_PIN, "1234").ifBlank { "1234" },
            unlockMethod = parsedMethod,
            masterPattern = getString(KEY_APP_PATTERN, ""),
            masterPassword = getString(KEY_APP_PASSWORD, ""),
            decoyPin = getString(KEY_DECOY_PIN, ""),
            dynamicTimePinEnabled = getString(KEY_DYNAMIC_TIME_PIN, "false") == "true",
            scrambleKeypadEnabled = getString(KEY_SCRAMBLE_KEYPAD, "false") == "true",
            breakInSirenEnabled = getString(KEY_BREAK_IN_SIREN, "false") == "true",
            preventScreenshots = getString(KEY_PREVENT_SCREENSHOTS, "true") == "true",
            calculatorDisguiseEnabled = getString(KEY_CALCULATOR_DISGUISE, "false") == "true",
            uninstallProtectionEnabled = getString(KEY_UNINSTALL_PROTECTION, "false") == "true",
            autoLockNewAppsEnabled = getString(KEY_AUTO_LOCK_NEW_APPS, "false") == "true",
            autoLockTimeoutMillis = getString(KEY_AUTO_LOCK_TIMEOUT, "300000").toLongOrNull()?.coerceAtLeast(0L) ?: 300_000L,
            hiddenApps = hiddenSet
        )
    }

    private val writeLock = Any()

    fun currentSettings(): SecuritySettings = _settingsFlow.value

    fun updateSettings(transform: (SecuritySettings) -> SecuritySettings): SecuritySettings = synchronized(writeLock) {
        val updated = transform(readSettingsSnapshot())
        persistAllSettings(updated)
        _settingsFlow.value = updated
        return updated
    }

    fun toggleHiddenApp(packageName: String): SecuritySettings {
        return updateSettings { current ->
            val nextHidden = if (current.hiddenApps.contains(packageName)) {
                current.hiddenApps - packageName
            } else {
                current.hiddenApps + packageName
            }
            current.copy(hiddenApps = nextHidden)
        }
    }

    private fun persistAllSettings(settings: SecuritySettings) {
        val editor = securePrefs.edit()
        encryptIntoEditor(editor, KEY_APP_PIN, settings.masterPin)
        encryptIntoEditor(editor, KEY_UNLOCK_METHOD, settings.unlockMethod.name)
        encryptIntoEditor(editor, KEY_APP_PATTERN, settings.masterPattern)
        encryptIntoEditor(editor, KEY_APP_PASSWORD, settings.masterPassword)
        encryptIntoEditor(editor, KEY_DECOY_PIN, settings.decoyPin)
        encryptIntoEditor(editor, KEY_DYNAMIC_TIME_PIN, settings.dynamicTimePinEnabled.toString())
        encryptIntoEditor(editor, KEY_SCRAMBLE_KEYPAD, settings.scrambleKeypadEnabled.toString())
        encryptIntoEditor(editor, KEY_BREAK_IN_SIREN, settings.breakInSirenEnabled.toString())
        encryptIntoEditor(editor, KEY_PREVENT_SCREENSHOTS, settings.preventScreenshots.toString())
        encryptIntoEditor(editor, KEY_CALCULATOR_DISGUISE, settings.calculatorDisguiseEnabled.toString())
        encryptIntoEditor(editor, KEY_UNINSTALL_PROTECTION, settings.uninstallProtectionEnabled.toString())
        encryptIntoEditor(editor, KEY_AUTO_LOCK_NEW_APPS, settings.autoLockNewAppsEnabled.toString())
        encryptIntoEditor(editor, KEY_AUTO_LOCK_TIMEOUT, settings.autoLockTimeoutMillis.toString())
        encryptIntoEditor(editor, KEY_HIDDEN_APPS, settings.hiddenApps.joinToString(","))
        editor.apply()
        VaultStorageManager.saveVaultBackup(appContext, this)
    }

    private fun encryptIntoEditor(editor: android.content.SharedPreferences.Editor, key: String, value: String) {
        try {
            editor.putString(key, CryptoManager.encrypt(value))
        } catch (e: Exception) {
            Log.e("SecurePreferences", "Failed to encrypt key: $key", e)
        }
    }

    fun contains(key: String): Boolean = securePrefs.contains(key)

    fun hasMasterPin(): Boolean {
        val hasPin = securePrefs.contains(KEY_APP_PIN) && getString(KEY_APP_PIN, "").isNotBlank()
        val hasPattern = securePrefs.contains(KEY_APP_PATTERN) && getString(KEY_APP_PATTERN, "").isNotBlank()
        val hasPassword = securePrefs.contains(KEY_APP_PASSWORD) && getString(KEY_APP_PASSWORD, "").isNotBlank()
        return hasPin || hasPattern || hasPassword
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
        putStringInternal(key, value, triggerBackup = true)
    }

    internal fun putStringWithoutBackup(key: String, value: String) {
        putStringInternal(key, value, triggerBackup = false)
    }

    private fun putStringInternal(key: String, value: String, triggerBackup: Boolean) = synchronized(writeLock) {
        try {
            val encryptedValue = CryptoManager.encrypt(value)
            securePrefs.edit().putString(key, encryptedValue).apply()

            _settingsFlow.value = readSettingsSnapshot()

            if (triggerBackup && key in BACKUP_KEYS) {
                VaultStorageManager.saveVaultBackup(appContext, this)
            }
        } catch (e: Exception) {
            Log.e("SecurePreferences", "Failed to encrypt key: $key", e)
        }
    }

    fun syncPersistentBackup() {
        VaultStorageManager.saveVaultBackup(appContext, this)
    }

    fun getLockedAppsBackupJson(): String = getString(KEY_LOCKED_APPS_BACKUP, "")

    fun saveLockedAppsBackupJson(json: String) {
        putStringInternal(KEY_LOCKED_APPS_BACKUP, json, triggerBackup = true)
    }

    fun getSecurityQuestion(): String = getString(KEY_SECURITY_QUESTION, "")

    fun setSecurityRecovery(question: String, answer: String) {
        putStringInternal(KEY_SECURITY_QUESTION, question.trim(), triggerBackup = false)
        putStringInternal(KEY_SECURITY_ANSWER, answer.trim().lowercase(), triggerBackup = true)
    }

    fun verifySecurityAnswer(inputAnswer: String): Boolean {
        val saved = getString(KEY_SECURITY_ANSWER, "")
        if (saved.isBlank()) return false
        return saved.equals(inputAnswer.trim().lowercase(), ignoreCase = true)
    }

    companion object {
        const val KEY_APP_PIN = "app_pin"
        const val KEY_UNLOCK_METHOD = "unlock_method"
        const val KEY_APP_PATTERN = "app_pattern"
        const val KEY_APP_PASSWORD = "app_password"
        const val KEY_DECOY_PIN = "decoy_pin"
        const val KEY_SECURITY_QUESTION = "security_question"
        const val KEY_SECURITY_ANSWER = "security_answer"
        const val KEY_DYNAMIC_TIME_PIN = "dynamic_time_pin"
        const val KEY_SCRAMBLE_KEYPAD = "scramble_keypad"
        const val KEY_BREAK_IN_SIREN = "break_in_siren"
        const val KEY_PREVENT_SCREENSHOTS = "prevent_screenshots"
        const val KEY_CALCULATOR_DISGUISE = "calculator_disguise"
        const val KEY_UNINSTALL_PROTECTION = "uninstall_protection"
        const val KEY_AUTO_LOCK_NEW_APPS = "auto_lock_new_apps"
        const val KEY_AUTO_LOCK_TIMEOUT = "auto_lock_timeout"
        const val KEY_HIDDEN_APPS = "hidden_apps"
        const val KEY_LOCKED_APPS_BACKUP = "locked_apps_backup"

        private val BACKUP_KEYS = setOf(
            KEY_APP_PIN,
            KEY_UNLOCK_METHOD,
            KEY_APP_PATTERN,
            KEY_APP_PASSWORD,
            KEY_SECURITY_QUESTION,
            KEY_SECURITY_ANSWER,
            KEY_DECOY_PIN,
            KEY_UNINSTALL_PROTECTION,
            KEY_AUTO_LOCK_NEW_APPS,
            KEY_CALCULATOR_DISGUISE,
            KEY_AUTO_LOCK_TIMEOUT,
            KEY_DYNAMIC_TIME_PIN,
            KEY_SCRAMBLE_KEYPAD,
            KEY_BREAK_IN_SIREN,
            KEY_PREVENT_SCREENSHOTS,
            KEY_HIDDEN_APPS,
            KEY_LOCKED_APPS_BACKUP
        )

        @Volatile
        private var INSTANCE: SecurePreferences? = null

        fun getInstance(context: Context): SecurePreferences {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SecurePreferences(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
