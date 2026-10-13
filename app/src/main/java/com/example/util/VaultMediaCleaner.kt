package com.example.util

import android.app.RecoverableSecurityException
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.provider.Settings
import android.util.Log
import java.io.File

object VaultMediaCleaner {
    private const val TAG = "VaultMediaCleaner"

    data class MediaEntry(
        val canonicalUri: Uri?,
        val realPath: String?,
        val displayName: String
    )

    data class DeleteResult(
        val isDeleted: Boolean,
        val canonicalUri: Uri?,
        val realPath: String?
    )

    /**
     * Checks if the app has full external storage management access (Android 11+).
     */
    fun hasAllFilesAccess(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }
    }

    /**
     * Opens Android System Settings to grant All Files Access to DASMO.
     */
    fun openAllFilesAccessSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                try {
                    val fallback = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(fallback)
                } catch (ex: Exception) {
                    Log.e(TAG, "Cannot open storage settings", ex)
                }
            }
        }
    }

    /**
     * Resolves any input content URI (PhotoPicker, DocumentsProvider, MediaStore, Google Photos)
     * into a canonical MediaStore URI and absolute filesystem path.
     */
    fun resolveCanonicalEntry(context: Context, uri: Uri, isVideo: Boolean): MediaEntry {
        val contentResolver = context.contentResolver
        var displayName = ""
        var size = 0L
        var candidateId: Long? = null
        var candidatePath: String? = null

        // 1. Query the input URI for basic metadata (DISPLAY_NAME, SIZE, DATA)
        try {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIdx != -1) displayName = cursor.getString(nameIdx) ?: ""

                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIdx != -1) size = cursor.getLong(sizeIdx)

                    val dataIdx = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                    if (dataIdx != -1) candidatePath = cursor.getString(dataIdx)

                    val idIdx = cursor.getColumnIndex(MediaStore.MediaColumns._ID)
                    if (idIdx != -1) candidateId = cursor.getLong(idIdx)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Query metadata on $uri failed: ${e.message}")
        }

        // 2. Extract numeric ID from URI path if possible (e.g. photo picker uri: .../media/1000000035)
        if (candidateId == null) {
            candidateId = uri.lastPathSegment?.toLongOrNull()
        }

        // 3. Inspect documentId if it's a DocumentsContract URI
        if (DocumentsContract.isDocumentUri(context, uri)) {
            try {
                val docId = DocumentsContract.getDocumentId(uri)
                if (docId != null && docId.contains(":")) {
                    val split = docId.split(":")
                    val type = split[0]
                    val rest = split[1]

                    if (type.equals("primary", ignoreCase = true)) {
                        candidatePath = "${Environment.getExternalStorageDirectory().absolutePath}/$rest"
                    } else if (rest.startsWith("raw:", ignoreCase = true)) {
                        candidatePath = rest.substring(4)
                    } else {
                        val numId = rest.toLongOrNull()
                        if (numId != null) candidateId = numId
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Parsing docId failed: ${e.message}")
            }
        }

        val collection = if (isVideo) {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        // 4. Strategy A: Query MediaStore by candidateId
        if (candidateId != null && candidateId!! > 0) {
            val candidateUri = ContentUris.withAppendedId(collection, candidateId!!)
            try {
                contentResolver.query(
                    collection,
                    arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DATA, MediaStore.MediaColumns.DISPLAY_NAME),
                    "${MediaStore.MediaColumns._ID}=?",
                    arrayOf(candidateId.toString()),
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val dataIdx = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                        val path = if (dataIdx != -1) cursor.getString(dataIdx) else null
                        val nameIdx = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                        val name = if (nameIdx != -1) cursor.getString(nameIdx) else displayName
                        return MediaEntry(candidateUri, path ?: candidatePath, name.ifBlank { displayName })
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Query by ID failed: ${e.message}")
            }
        }

        // 5. Strategy B: Query MediaStore by candidatePath
        if (!candidatePath.isNullOrBlank()) {
            try {
                contentResolver.query(
                    collection,
                    arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DATA, MediaStore.MediaColumns.DISPLAY_NAME),
                    "${MediaStore.MediaColumns.DATA}=?",
                    arrayOf(candidatePath),
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
                        val nameIdx = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                        val name = if (nameIdx != -1) cursor.getString(nameIdx) else displayName
                        return MediaEntry(ContentUris.withAppendedId(collection, id), candidatePath, name.ifBlank { displayName })
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Query by DATA failed: ${e.message}")
            }
        }

        // 6. Strategy C: Query MediaStore by DISPLAY_NAME and SIZE
        if (displayName.isNotBlank() && size > 0) {
            try {
                contentResolver.query(
                    collection,
                    arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DATA),
                    "${MediaStore.MediaColumns.DISPLAY_NAME}=? AND ${MediaStore.MediaColumns.SIZE}=?",
                    arrayOf(displayName, size.toString()),
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
                        val dataIdx = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                        val path = if (dataIdx != -1) cursor.getString(dataIdx) else candidatePath
                        return MediaEntry(ContentUris.withAppendedId(collection, id), path, displayName)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Query by name+size failed: ${e.message}")
            }
        }

        // 7. Strategy D: Query MediaStore by DISPLAY_NAME alone
        if (displayName.isNotBlank()) {
            try {
                contentResolver.query(
                    collection,
                    arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DATA),
                    "${MediaStore.MediaColumns.DISPLAY_NAME}=?",
                    arrayOf(displayName),
                    "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
                        val dataIdx = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                        val path = if (dataIdx != -1) cursor.getString(dataIdx) else candidatePath
                        return MediaEntry(ContentUris.withAppendedId(collection, id), path, displayName)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Query by name failed: ${e.message}")
            }
        }

        // 8. Strategy E: Check standard device directories for displayName
        if (displayName.isNotBlank()) {
            val commonDirs = listOf(
                File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "Camera"),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            )
            for (dir in commonDirs) {
                val candidateFile = File(dir, displayName)
                if (candidateFile.exists()) {
                    candidatePath = candidateFile.absolutePath
                    try {
                        contentResolver.query(
                            collection,
                            arrayOf(MediaStore.MediaColumns._ID),
                            "${MediaStore.MediaColumns.DATA}=?",
                            arrayOf(candidateFile.absolutePath),
                            null
                        )?.use { cursor ->
                            if (cursor.moveToFirst()) {
                                val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
                                return MediaEntry(ContentUris.withAppendedId(collection, id), candidateFile.absolutePath, displayName)
                            }
                        }
                    } catch (_: Exception) {}
                    return MediaEntry(null, candidateFile.absolutePath, displayName)
                }
            }
        }

        // Fallback: If uri is already canonical MediaStore URI
        val fallbackCanonical = if (uri.authority == "media" && (uri.path?.contains("/images/media") == true || uri.path?.contains("/video/media") == true)) {
            uri
        } else {
            null
        }

        return MediaEntry(fallbackCanonical, candidatePath, displayName)
    }

    /**
     * Deletes the original unencrypted media from device storage and gallery.
     * Uses direct file deletion, ContentResolver deletion, and MediaScanner eviction.
     */
    fun deleteOriginalMedia(context: Context, uri: Uri, entry: MediaEntry): DeleteResult {
        var isDeleted = false
        val contentResolver = context.contentResolver
        val path = entry.realPath

        // 1. Direct file deletion (works 100% when MANAGE_EXTERNAL_STORAGE is granted or on Android 9/10)
        if (!path.isNullOrBlank()) {
            try {
                val file = File(path)
                if (file.exists() && file.delete()) {
                    isDeleted = true
                    Log.d(TAG, "Direct File.delete succeeded: $path")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Direct File.delete failed: ${e.message}")
            }
        }

        // 2. ContentResolver deletion using canonical MediaStore URI
        val canonicalUri = entry.canonicalUri
        if (canonicalUri != null) {
            try {
                val rows = contentResolver.delete(canonicalUri, null, null)
                if (rows > 0) {
                    isDeleted = true
                    Log.d(TAG, "ContentResolver.delete succeeded on $canonicalUri ($rows rows)")
                }
            } catch (e: RecoverableSecurityException) {
                Log.d(TAG, "RecoverableSecurityException on delete: user consent required")
            } catch (e: SecurityException) {
                Log.d(TAG, "SecurityException on delete: user consent required (Android 11+)")
            } catch (e: Exception) {
                Log.w(TAG, "ContentResolver.delete failed: ${e.message}")
            }
        }

        // 3. Fallback: try original URI if different and not deleted yet
        if (!isDeleted && uri != canonicalUri) {
            try {
                if (DocumentsContract.isDocumentUri(context, uri)) {
                    if (DocumentsContract.deleteDocument(contentResolver, uri)) {
                        isDeleted = true
                    }
                } else {
                    val rows = contentResolver.delete(uri, null, null)
                    if (rows > 0) isDeleted = true
                }
            } catch (_: Exception) {}
        }

        // 4. Force MediaScanner to immediately evict thumbnail and index from Gallery
        if (isDeleted && !path.isNullOrBlank()) {
            try {
                MediaScannerConnection.scanFile(context, arrayOf(path), null) { scannedPath, _ ->
                    Log.d(TAG, "MediaScanner evicted: $scannedPath")
                }
            } catch (_: Exception) {}
        }

        return DeleteResult(
            isDeleted = isDeleted,
            canonicalUri = canonicalUri,
            realPath = path
        )
    }

    /**
     * Creates a system delete confirmation intent sender for Android 11+ (API 30+)
     * using canonical MediaStore URIs.
     */
    fun createSystemDeleteIntentSender(context: Context, uris: List<Uri>): IntentSender? {
        if (uris.isEmpty()) return null
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val validUris = uris.mapNotNull { uri ->
                    if (uri.authority == "media" && (uri.path?.contains("/images/media") == true || uri.path?.contains("/video/media") == true)) {
                        uri
                    } else {
                        resolveCanonicalEntry(context, uri, uri.toString().contains("video")).canonicalUri
                    }
                }.distinct()

                if (validUris.isNotEmpty()) {
                    Log.d(TAG, "Creating MediaStore.createDeleteRequest for ${validUris.size} canonical URIs")
                    MediaStore.createDeleteRequest(context.contentResolver, validUris).intentSender
                } else {
                    Log.w(TAG, "No valid canonical MediaStore URIs available for delete request")
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed creating MediaStore delete request", e)
                null
            }
        } else {
            null
        }
    }

    /**
     * Batch scans filesystem paths with MediaScannerConnection to update Gallery caches.
     */
    fun scanFilePaths(context: Context, paths: List<String>) {
        if (paths.isEmpty()) return
        try {
            MediaScannerConnection.scanFile(context, paths.toTypedArray(), null) { path, uri ->
                Log.d(TAG, "Scanned: $path, uri: $uri")
            }
        } catch (e: Exception) {
            Log.w(TAG, "scanFilePaths failed: ${e.message}")
        }
    }
}
