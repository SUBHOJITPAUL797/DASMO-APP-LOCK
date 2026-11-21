package com.example.presentation.screens.intruders

import android.content.Context
import org.json.JSONObject
import java.io.File

enum class IntruderMediaType {
    PHOTO,
    VIDEO
}

data class IntruderRecord(
    val photoFile: File,
    val targetPackageName: String,
    val targetAppLabel: String,
    val authMethodLabel: String,
    val failedAttemptNumber: Int,
    val timestampMillis: Long,
    val isUninstallProtection: Boolean,
    val mediaType: IntruderMediaType = IntruderMediaType.PHOTO,
    val durationSeconds: Int = 0
) {
    val isVideo: Boolean get() = mediaType == IntruderMediaType.VIDEO
}

data class IntruderMetadata(
    val targetPackageName: String = "",
    val targetAppLabel: String = "",
    val authMethodLabel: String = "Auth Failed",
    val failedAttemptNumber: Int = 1,
    val timestampMillis: Long = 0L,
    val isUninstallProtection: Boolean = false,
    val mediaType: IntruderMediaType = IntruderMediaType.PHOTO,
    val durationSeconds: Int = 0
)

object IntruderLogStore {
    private const val DIR_NAME = "intruders"
    private const val META_SUFFIX = ".meta.json"
    private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")
    private val VIDEO_EXTENSIONS = setOf("mp4", "mkv", "webm")

    fun getIntruderDir(context: Context): File {
        val dir = context.getDir(DIR_NAME, Context.MODE_PRIVATE)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun isImageFile(file: File): Boolean {
        if (!file.isFile) return false
        val ext = file.extension.lowercase()
        return ext in IMAGE_EXTENSIONS
    }

    fun isVideoFile(file: File): Boolean {
        if (!file.isFile) return false
        val ext = file.extension.lowercase()
        return ext in VIDEO_EXTENSIONS
    }

    fun isMediaFile(file: File): Boolean = isImageFile(file) || isVideoFile(file)

    fun metadataFileFor(mediaFile: File): File {
        return File(mediaFile.parentFile, "${mediaFile.name}$META_SUFFIX")
    }

    fun encodeMetadataJson(metadata: IntruderMetadata): String {
        val json = JSONObject()
        json.put("pkg", metadata.targetPackageName)
        json.put("label", metadata.targetAppLabel)
        json.put("method", metadata.authMethodLabel)
        json.put("attempt", metadata.failedAttemptNumber)
        json.put("ts", metadata.timestampMillis)
        json.put("uninstall", metadata.isUninstallProtection)
        json.put("mediaType", metadata.mediaType.name)
        json.put("durationSec", metadata.durationSeconds)
        return json.toString()
    }

    fun decodeMetadataJson(rawJson: String): IntruderMetadata? {
        if (rawJson.isBlank()) return null
        return try {
            val json = JSONObject(rawJson)
            val parsedType = when (json.optString("mediaType", "PHOTO").uppercase()) {
                "VIDEO" -> IntruderMediaType.VIDEO
                else -> IntruderMediaType.PHOTO
            }
            IntruderMetadata(
                targetPackageName = json.optString("pkg", "").trim(),
                targetAppLabel = json.optString("label", "").trim(),
                authMethodLabel = json.optString("method", "Auth Failed").ifBlank { "Auth Failed" },
                failedAttemptNumber = json.optInt("attempt", 1).coerceAtLeast(1),
                timestampMillis = json.optLong("ts", 0L),
                isUninstallProtection = json.optBoolean("uninstall", false),
                mediaType = parsedType,
                durationSeconds = json.optInt("durationSec", 0).coerceAtLeast(0)
            )
        } catch (_: Exception) {
            null
        }
    }

    fun saveMetadata(
        photoFile: File,
        targetPackageName: String,
        targetAppLabel: String,
        authMethodLabel: String,
        failedAttemptNumber: Int = 1,
        isUninstallProtection: Boolean = false,
        timestampMillis: Long = System.currentTimeMillis(),
        mediaType: IntruderMediaType = if (isVideoFile(photoFile)) IntruderMediaType.VIDEO else IntruderMediaType.PHOTO,
        durationSeconds: Int = 0
    ) {
        try {
            val meta = IntruderMetadata(
                targetPackageName = targetPackageName.trim(),
                targetAppLabel = targetAppLabel.trim(),
                authMethodLabel = authMethodLabel.trim().ifBlank { "Auth Failed" },
                failedAttemptNumber = failedAttemptNumber.coerceAtLeast(1),
                timestampMillis = timestampMillis,
                isUninstallProtection = isUninstallProtection,
                mediaType = mediaType,
                durationSeconds = durationSeconds.coerceAtLeast(0)
            )
            metadataFileFor(photoFile).writeText(encodeMetadataJson(meta), Charsets.UTF_8)
        } catch (_: Exception) {
            // Keep media intact even if sidecar write fails
        }
    }

    fun countIntruders(context: Context): Int {
        val dir = context.getDir(DIR_NAME, Context.MODE_PRIVATE)
        if (!dir.exists()) return 0
        return dir.listFiles()?.count { isMediaFile(it) && it.length() > 0L } ?: 0
    }

    fun loadAllRecords(context: Context): List<IntruderRecord> {
        val dir = context.getDir(DIR_NAME, Context.MODE_PRIVATE)
        if (!dir.exists()) return emptyList()

        val pm = context.packageManager
        val mediaFiles = dir.listFiles()
            ?.filter { isMediaFile(it) && it.length() > 0L }
            ?.sortedByDescending { it.lastModified() }
            ?: return emptyList()

        return mediaFiles.map { file ->
            val metaFile = metadataFileFor(file)
            val parsed = if (metaFile.exists()) {
                try {
                    decodeMetadataJson(metaFile.readText(Charsets.UTF_8))
                } catch (_: Exception) {
                    null
                }
            } else {
                null
            }

            val inferredType = if (isVideoFile(file)) IntruderMediaType.VIDEO else IntruderMediaType.PHOTO
            val resolvedType = parsed?.mediaType?.takeIf { inferredType == it } ?: inferredType

            val pkg = parsed?.targetPackageName.orEmpty()
            val resolvedLabel = when {
                !parsed?.targetAppLabel.isNullOrBlank() -> parsed!!.targetAppLabel
                pkg.isNotBlank() -> try {
                    val info = pm.getApplicationInfo(pkg, 0)
                    pm.getApplicationLabel(info).toString()
                } catch (_: Exception) {
                    pkg
                }
                else -> "Locked App"
            }

            IntruderRecord(
                photoFile = file,
                targetPackageName = pkg,
                targetAppLabel = resolvedLabel,
                authMethodLabel = parsed?.authMethodLabel ?: "Auth Failed",
                failedAttemptNumber = parsed?.failedAttemptNumber ?: 1,
                timestampMillis = parsed?.timestampMillis?.takeIf { it > 0L } ?: file.lastModified(),
                isUninstallProtection = parsed?.isUninstallProtection ?: false,
                mediaType = resolvedType,
                durationSeconds = parsed?.durationSeconds ?: 0
            )
        }
    }

    fun deleteRecord(record: IntruderRecord) {
        try {
            record.photoFile.delete()
            metadataFileFor(record.photoFile).delete()
        } catch (_: Exception) {
            // Safe cleanup
        }
    }

    fun clearAll(context: Context) {
        val dir = context.getDir(DIR_NAME, Context.MODE_PRIVATE)
        if (!dir.exists()) return
        dir.listFiles()?.forEach { it.delete() }
    }
}
