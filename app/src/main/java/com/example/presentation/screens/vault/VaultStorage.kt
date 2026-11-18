package com.example.presentation.screens.vault

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.util.CryptoManager
import com.example.util.VaultStorageManager
import java.io.File

object VaultStorage {

    private val PHOTO_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "heic", "heif", "gif", "bmp")
    private val VIDEO_EXTENSIONS = setOf("mp4", "3gp", "mkv", "mov", "webm", "avi", "m4v")

    fun getRootDir(context: Context, mode: VaultMode): File {
        val dir = VaultStorageManager.getVaultDirectory(context, isDecoy = (mode == VaultMode.DECOY))
        if (!dir.exists()) dir.mkdirs()

        if (mode == VaultMode.DECOY) {
            ensureDecoySeeded(dir)
        }
        return dir
    }

    fun getTabDir(rootDir: File, tab: VaultTab): File {
        val sub = when (tab) {
            VaultTab.NOTES -> "notes"
            VaultTab.PHOTOS -> "photos"
            VaultTab.VIDEOS -> "videos"
        }
        val dir = File(rootDir, sub)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun ensureDecoySeeded(decoyRoot: File) {
        val notesDir = getTabDir(decoyRoot, VaultTab.NOTES)
        val hasNotes = (notesDir.listFiles()?.any { it.name.endsWith(".enc") } == true) ||
            (decoyRoot.listFiles()?.any { it.isFile && it.name.endsWith(".enc") } == true)
        if (!hasNotes) {
            try {
                File(notesDir, "Weekly Grocery List.enc").writeText(
                    CryptoManager.encrypt("1. Milk (Whole)\n2. Eggs (Dozen)\n3. Brown Bread\n4. Apples & Oranges\n5. Green Tea"),
                    Charsets.UTF_8
                )
                File(notesDir, "Workout Plan.enc").writeText(
                    CryptoManager.encrypt("Mon: Chest & Triceps\nWed: Back & Biceps\nFri: Legs & Core\nSun: 5km morning run"),
                    Charsets.UTF_8
                )
            } catch (_: Exception) {
                // Ignore if cipher unavailable in isolated JVM test
            }
        }
    }

    fun countRealVaultItems(context: Context): Int {
        val rootDir = VaultStorageManager.getVaultDirectory(context, isDecoy = false)
        if (!rootDir.exists()) return 0
        return collectVaultFiles(rootDir).size
    }

    fun loadVaultItems(context: Context, mode: VaultMode): List<VaultItem> {
        val rootDir = getRootDir(context, mode)
        return collectVaultFiles(rootDir)
            .sortedByDescending { it.lastModified }
    }

    private fun collectVaultFiles(rootDir: File): List<VaultItem> {
        val items = mutableListOf<VaultItem>()

        VaultTab.entries.forEach { tab ->
            val subDir = getTabDir(rootDir, tab)
            subDir.listFiles()
                ?.filter { it.isFile && it.name.endsWith(".enc", ignoreCase = true) }
                ?.forEach { file ->
                    items.add(
                        VaultItem(
                            file = file,
                            displayName = file.name.removeSuffix(".enc"),
                            tab = tab,
                            sizeBytes = file.length(),
                            lastModified = file.lastModified()
                        )
                    )
                }
        }

        // Backward compatibility for legacy files stored directly inside rootDir (skip hidden config)
        rootDir.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".enc", ignoreCase = true) && !it.name.startsWith(".") }
            ?.forEach { file ->
                val tab = classifyLegacyFileName(file.name)
                items.add(
                    VaultItem(
                        file = file,
                        displayName = file.name.removeSuffix(".enc"),
                        tab = tab,
                        sizeBytes = file.length(),
                        lastModified = file.lastModified()
                    )
                )
            }

        return items
    }

    fun classifyLegacyFileName(fileName: String): VaultTab {
        val withoutEnc = fileName.removeSuffix(".enc").removeSuffix(".ENC")
        val ext = withoutEnc.substringAfterLast('.', "").lowercase()
        return when {
            ext in PHOTO_EXTENSIONS || withoutEnc.lowercase().startsWith("img_") || withoutEnc.lowercase().startsWith("photo_") -> VaultTab.PHOTOS
            ext in VIDEO_EXTENSIONS || withoutEnc.lowercase().startsWith("vid_") || withoutEnc.lowercase().startsWith("video_") -> VaultTab.VIDEOS
            else -> VaultTab.NOTES
        }
    }

    fun sanitizeFileName(rawTitle: String, fallback: String = "untitled"): String {
        val cleaned = rawTitle
            .trim()
            .replace(Regex("[/\\\\:*?\"<>|\\x00-\\x1F]"), "_")
            .replace("..", "_")
            .trim('.', ' ')
        return if (cleaned.isEmpty()) fallback else cleaned.take(80)
    }

    fun resolveUniqueFile(dir: File, baseNameWithoutEnc: String): File {
        val safeBase = sanitizeFileName(baseNameWithoutEnc)
        var candidate = File(dir, "$safeBase.enc")
        var counter = 1
        while (candidate.exists()) {
            candidate = File(dir, "${safeBase}_$counter.enc")
            counter++
        }
        return candidate
    }

    fun saveEncryptedNote(context: Context, mode: VaultMode, title: String, content: String): File {
        val rootDir = getRootDir(context, mode)
        val notesDir = getTabDir(rootDir, VaultTab.NOTES)
        val targetFile = resolveUniqueFile(notesDir, title)
        val encrypted = CryptoManager.encrypt(content)
        targetFile.writeText(encrypted, Charsets.UTF_8)
        return targetFile
    }

    fun saveEncryptedBytes(
        context: Context,
        mode: VaultMode,
        tab: VaultTab,
        baseFileNameWithoutEnc: String,
        encryptedBytes: ByteArray
    ): File {
        val rootDir = getRootDir(context, mode)
        val targetDir = getTabDir(rootDir, tab)
        val targetFile = resolveUniqueFile(targetDir, baseFileNameWithoutEnc)
        targetFile.writeBytes(encryptedBytes)
        return targetFile
    }

    fun resolveDisplayName(context: Context, uri: Uri, fallback: String): String {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx != -1) {
                    val queried = cursor.getString(idx)
                    if (!queried.isNullOrBlank()) return queried
                }
            }
        }
        return fallback
    }

    fun clearTempVideoCache(cacheDir: File) {
        cacheDir.listFiles()?.filter { it.name.startsWith("temp_vault_video_") }?.forEach {
            it.delete()
        }
    }
}
