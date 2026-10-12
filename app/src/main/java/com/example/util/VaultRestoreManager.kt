package com.example.util

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

data class RestoredMediaInfo(
    val originalVaultFile: File,
    val restoredFileName: String,
    val restoredUri: Uri?,
    val restoredFilePath: String?,
    val isImage: Boolean,
    val isVideo: Boolean
)

object VaultRestoreManager {

    /**
     * Determines whether a vault file is an image based on its name or decrypted bytes.
     */
    fun isImageFile(fileName: String): Boolean {
        val lower = fileName.lowercase()
        return lower.endsWith(".jpg.enc") || lower.endsWith(".jpeg.enc") ||
               lower.endsWith(".png.enc") || lower.endsWith(".webp.enc") ||
               lower.endsWith(".gif.enc") || lower.endsWith(".bmp.enc") ||
               lower.endsWith(".heic.enc") || lower.startsWith("photo_") ||
               lower.startsWith("img_") || lower.startsWith("image_")
    }

    /**
     * Determines whether a vault file is a video based on its name.
     */
    fun isVideoFile(fileName: String): Boolean {
        val lower = fileName.lowercase()
        return lower.endsWith(".mp4.enc") || lower.endsWith(".3gp.enc") ||
               lower.endsWith(".mkv.enc") || lower.endsWith(".mov.enc") ||
               lower.endsWith(".webm.enc") || lower.endsWith(".avi.enc") ||
               lower.startsWith("video_") || lower.startsWith("vid_")
    }

    /**
     * Inspects magic bytes of decrypted data to determine the accurate extension and mime type.
     */
    private fun detectExtensionAndMime(data: ByteArray, fallbackExtension: String): Pair<String, String> {
        if (data.size >= 4) {
            // JPEG: FF D8 FF
            if ((data[0].toInt() and 0xFF) == 0xFF &&
                (data[1].toInt() and 0xFF) == 0xD8 &&
                (data[2].toInt() and 0xFF) == 0xFF) {
                return Pair("jpg", "image/jpeg")
            }
            // PNG: 89 50 4E 47
            if ((data[0].toInt() and 0xFF) == 0x89 &&
                (data[1].toInt() and 0xFF) == 0x50 &&
                (data[2].toInt() and 0xFF) == 0x4E &&
                (data[3].toInt() and 0xFF) == 0x47) {
                return Pair("png", "image/png")
            }
            // GIF: 47 49 46 38
            if (data.size >= 6 &&
                data[0].toInt().toChar() == 'G' &&
                data[1].toInt().toChar() == 'I' &&
                data[2].toInt().toChar() == 'F') {
                return Pair("gif", "image/gif")
            }
        }
        if (data.size >= 12) {
            // WEBP: RIFF....WEBP
            val isRiff = data[0].toInt().toChar() == 'R' && data[1].toInt().toChar() == 'I' &&
                         data[2].toInt().toChar() == 'F' && data[3].toInt().toChar() == 'F'
            val isWebp = data[8].toInt().toChar() == 'W' && data[9].toInt().toChar() == 'E' &&
                         data[10].toInt().toChar() == 'B' && data[11].toInt().toChar() == 'P'
            if (isRiff && isWebp) {
                return Pair("webp", "image/webp")
            }
            // MP4: ....ftyp
            val isFtyp = data[4].toInt().toChar() == 'f' && data[5].toInt().toChar() == 't' &&
                         data[6].toInt().toChar() == 'y' && data[7].toInt().toChar() == 'p'
            if (isFtyp) {
                return Pair("mp4", "video/mp4")
            }
        }

        val ext = fallbackExtension.lowercase().removePrefix(".")
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: when (ext) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "mp4" -> "video/mp4"
            "3gp" -> "video/3gpp"
            "mkv" -> "video/x-matroska"
            "mov" -> "video/quicktime"
            else -> if (ext.contains("mp4") || ext.contains("video")) "video/mp4" else "image/jpeg"
        }
        return Pair(ext.ifEmpty { "jpg" }, mime)
    }

    /**
     * Restores an encrypted media file from the vault back into the user's Android gallery.
     * Works with all legacy encrypted files, scoped MediaStore (Android 10+), and public external storage (<= API 28).
     *
     * @param context Application/Activity context
     * @param vaultFile The .enc file inside the vault directory
     * @param deleteVaultFileOnSuccess If true, deletes the .enc file after successful restoration.
     * @return Result with RestoredMediaInfo on success, or exception on failure.
     */
    suspend fun restoreFileToGallery(
        context: Context,
        vaultFile: File,
        deleteVaultFileOnSuccess: Boolean = true
    ): Result<RestoredMediaInfo> = withContext(Dispatchers.IO) {
        try {
            if (!vaultFile.exists()) {
                return@withContext Result.failure(IllegalArgumentException("Vault file does not exist: ${vaultFile.name}"))
            }

            // 1. Decrypt raw bytes from vault file
            val encryptedBytes = vaultFile.readBytes()
            val decryptedBytes = CryptoManager.decryptBytes(encryptedBytes)
            if (decryptedBytes.isEmpty()) {
                return@withContext Result.failure(IllegalStateException("Decrypted file content is empty."))
            }

            // 2. Parse clean name and determine extension/MIME
            val rawName = vaultFile.name.removeSuffix(".enc")
            val existingExt = if (rawName.contains(".")) rawName.substringAfterLast(".") else ""
            val baseName = if (rawName.contains(".")) rawName.substringBeforeLast(".") else rawName

            val (detectedExt, mimeType) = detectExtensionAndMime(
                data = decryptedBytes,
                fallbackExtension = existingExt.ifEmpty { if (isVideoFile(vaultFile.name)) "mp4" else "jpg" }
            )

            val finalFileName = "$baseName.$detectedExt"
            val isVideo = mimeType.startsWith("video/")
            val isImage = mimeType.startsWith("image/")

            var savedUri: Uri? = null
            var savedPath: String? = null

            // 3. Write to Gallery
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Scoped MediaStore on Android 10+
                val collectionUri = if (isVideo) {
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                } else {
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                }

                val relativeSubDir = if (isVideo) {
                    Environment.DIRECTORY_MOVIES + "/DASMO_Vault"
                } else {
                    Environment.DIRECTORY_PICTURES + "/DASMO_Vault"
                }

                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, finalFileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, relativeSubDir)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }

                val resolver = context.contentResolver
                val itemUri = resolver.insert(collectionUri, contentValues)
                    ?: return@withContext Result.failure(IllegalStateException("Failed to create MediaStore entry for $finalFileName"))

                resolver.openOutputStream(itemUri).use { outStream ->
                    if (outStream == null) {
                        resolver.delete(itemUri, null, null)
                        return@withContext Result.failure(IllegalStateException("Unable to open output stream for $itemUri"))
                    }
                    outStream.write(decryptedBytes)
                    outStream.flush()
                }

                // Mark complete (IS_PENDING = 0) so it becomes visible to Gallery immediately
                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(itemUri, contentValues, null, null)

                savedUri = itemUri
            } else {
                // Legacy storage for Android 9 (Pie) and older
                val publicDir = if (isVideo) {
                    File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "DASMO_Vault")
                } else {
                    File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "DASMO_Vault")
                }

                if (!publicDir.exists()) {
                    publicDir.mkdirs()
                }

                var targetDest = File(publicDir, finalFileName)
                if (targetDest.exists()) {
                    val uniqueName = "${baseName}_${System.currentTimeMillis()}.$detectedExt"
                    targetDest = File(publicDir, uniqueName)
                }

                FileOutputStream(targetDest).use { fos ->
                    fos.write(decryptedBytes)
                    fos.flush()
                }

                savedPath = targetDest.absolutePath

                // Trigger MediaScanner so Gallery indexes the newly written file immediately
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(targetDest.absolutePath),
                    arrayOf(mimeType),
                    null
                )
            }

            // 4. Clean up encrypted file from vault upon successful restoration
            if (deleteVaultFileOnSuccess) {
                vaultFile.delete()
            }

            val info = RestoredMediaInfo(
                originalVaultFile = vaultFile,
                restoredFileName = finalFileName,
                restoredUri = savedUri,
                restoredFilePath = savedPath,
                isImage = isImage,
                isVideo = isVideo
            )
            Result.success(info)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
