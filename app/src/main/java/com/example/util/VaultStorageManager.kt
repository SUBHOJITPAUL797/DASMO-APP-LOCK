package com.example.util

import android.content.Context
import android.os.Environment
import android.util.Log
import org.json.JSONObject
import java.io.File

/**
 * Manages persistent storage for the DASMO Secure Vault and configuration backup.
 * Stores encrypted files and security backup in persistent external documents directory
 * so that encrypted photos, videos, notes, and the user's PIN/settings SURVIVE
 * application uninstallation and are automatically restored upon re-installation!
 */
object VaultStorageManager {
    private const val TAG = "VaultStorageManager"
    private const val VAULT_DIR_NAME = ".dasmo_secure_vault"
    private const val DECOY_DIR_NAME = ".dasmo_decoy_vault"
    private const val CONFIG_BACKUP_NAME = ".vault_config.enc"

    /**
     * Resolves the persistent vault directory.
     * Prioritizes public Documents directory so it persists across uninstall,
     * with fallbacks to external root and internal private storage.
     */
    fun getVaultDirectory(context: Context, isDecoy: Boolean = false): File {
        val targetName = if (isDecoy) DECOY_DIR_NAME else VAULT_DIR_NAME

        val candidates = listOf(
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), targetName),
            File(Environment.getExternalStorageDirectory(), targetName),
            File(context.filesDir, targetName),
            context.getDir(if (isDecoy) "decoy_vault" else "secure_vault", Context.MODE_PRIVATE)
        )

        for (candidate in candidates) {
            try {
                if (!candidate.exists()) {
                    candidate.mkdirs()
                }
                if (candidate.exists() && candidate.canWrite()) {
                    // Guarantee hidden media status so gallery apps don't index .enc files
                    ensureNoMedia(candidate)
                    return candidate
                }
            } catch (e: Exception) {
                Log.w(TAG, "Directory candidate ${candidate.absolutePath} not writable: ${e.message}")
            }
        }

        // Final fallback: internal app directory
        val fallback = context.getDir(if (isDecoy) "decoy_vault" else "secure_vault", Context.MODE_PRIVATE)
        if (!fallback.exists()) fallback.mkdirs()
        ensureNoMedia(fallback)
        return fallback
    }

    private fun ensureNoMedia(directory: File) {
        try {
            val noMedia = File(directory, ".nomedia")
            if (!noMedia.exists()) {
                noMedia.createNewFile()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create .nomedia in ${directory.absolutePath}", e)
        }
    }

    /**
     * Migrates files from legacy internal app storage (e.g. app_secure_vault)
     * to the persistent external storage directory.
     */
    fun migrateLegacyInternalStorage(context: Context) {
        try {
            migrateDirectory(context.getDir("secure_vault", Context.MODE_PRIVATE), getVaultDirectory(context, false))
            migrateDirectory(context.getDir("decoy_vault", Context.MODE_PRIVATE), getVaultDirectory(context, true))
        } catch (e: Exception) {
            Log.e(TAG, "Legacy migration error", e)
        }
    }

    private fun migrateDirectory(sourceDir: File, targetDir: File) {
        if (!sourceDir.exists() || sourceDir.absolutePath == targetDir.absolutePath) return
        val sourceFiles = sourceDir.listFiles() ?: return

        for (file in sourceFiles) {
            if (file.name == ".nomedia") continue
            try {
                val destFile = File(targetDir, file.name)
                if (!destFile.exists()) {
                    file.copyTo(destFile, overwrite = true)
                    file.delete()
                    Log.d(TAG, "Migrated ${file.name} to persistent storage: ${destFile.absolutePath}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed migrating file ${file.name}", e)
            }
        }
    }

    /**
     * Checks whether an encrypted configuration backup exists in persistent storage.
     */
    fun hasPersistentBackup(context: Context): Boolean {
        val vaultDir = getVaultDirectory(context, false)
        val configFile = File(vaultDir, CONFIG_BACKUP_NAME)
        return configFile.exists() && configFile.length() > 0
    }

    /**
     * Saves an encrypted backup of all PINs, security recovery questions, and settings
     * to the persistent vault folder. Survives app uninstallation!
     */
    fun saveVaultBackup(context: Context, securePrefs: SecurePreferences) {
        try {
            val json = JSONObject()
            val keysToBackup = listOf(
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

            for (key in keysToBackup) {
                val value = securePrefs.getString(key, "")
                if (value.isNotEmpty()) {
                    json.put(key, value)
                }
            }

            if (json.length() == 0) return

            val encryptedJson = CryptoManager.encrypt(json.toString())
            val vaultDir = getVaultDirectory(context, false)
            val configFile = File(vaultDir, CONFIG_BACKUP_NAME)
            configFile.writeText(encryptedJson, Charsets.UTF_8)
            Log.d(TAG, "Successfully backed up vault config (${json.length()} settings) to ${configFile.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save vault backup", e)
        }
    }

    /**
     * Restores PINs, security recovery, and settings from persistent backup if available.
     * Returns true if backup was found and restored.
     */
    fun restoreVaultBackup(context: Context, securePrefs: SecurePreferences): Boolean {
        try {
            val vaultDir = getVaultDirectory(context, false)
            val configFile = File(vaultDir, CONFIG_BACKUP_NAME)
            if (!configFile.exists() || configFile.length() == 0L) return false

            val encryptedContent = configFile.readText(Charsets.UTF_8)
            val decryptedJsonStr = CryptoManager.decrypt(encryptedContent)
            val json = JSONObject(decryptedJsonStr)

            val keys = json.keys()
            var count = 0
            while (keys.hasNext()) {
                val key = keys.next()
                val value = json.optString(key, "")
                if (value.isNotEmpty()) {
                    securePrefs.putString(key, value)
                    count++
                }
            }

            Log.d(TAG, "Successfully restored $count settings from persistent backup!")
            return count > 0
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restore vault backup", e)
            return false
        }
    }
}
