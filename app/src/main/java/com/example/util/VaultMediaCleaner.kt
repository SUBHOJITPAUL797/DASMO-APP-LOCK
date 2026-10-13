package com.example.util

import android.app.RecoverableSecurityException
import android.content.ContentUris
import android.content.Context
import android.content.IntentSender
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import java.io.File

object VaultMediaCleaner {
    private const val TAG = "VaultMediaCleaner"

    /**
     * Resolves the real absolute filesystem path of a media URI if possible.
     */
    fun resolveRealPath(context: Context, uri: Uri): String? {
        val contentResolver = context.contentResolver

        // 1. Try DocumentsContract if it's a document URI
        if (DocumentsContract.isDocumentUri(context, uri)) {
            try {
                val docId = DocumentsContract.getDocumentId(uri)
                if (docId != null && docId.contains(":")) {
                    val split = docId.split(":")
                    val type = split[0]
                    val id = split[1]

                    // External storage provider e.g. "primary:DCIM/Camera/IMG_001.jpg"
                    if (type.equals("primary", ignoreCase = true)) {
                        val path = "${Environment.getExternalStorageDirectory().absolutePath}/$id"
                        val f = File(path)
                        if (f.exists()) return f.absolutePath
                    }

                    // Raw path e.g. "raw:/storage/emulated/0/..."
                    if (id.startsWith("raw:", ignoreCase = true)) {
                        val rawPath = id.substring(4)
                        if (File(rawPath).exists()) return rawPath
                    }

                    // Numeric media ID e.g. "image:12345" or "video:12345"
                    val mediaId = id.toLongOrNull()
                    if (mediaId != null) {
                        val contentUri = when {
                            type.contains("video", ignoreCase = true) || uri.toString().contains("video") ->
                                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                            else -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                        }
                        val selection = "${MediaStore.MediaColumns._ID}=?"
                        val selectionArgs = arrayOf(mediaId.toString())

                        contentResolver.query(
                            contentUri,
                            arrayOf(MediaStore.MediaColumns.DATA),
                            selection,
                            selectionArgs,
                            null
                        )?.use { cursor ->
                            val col = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                            if (col != -1 && cursor.moveToFirst()) {
                                val path = cursor.getString(col)
                                if (!path.isNullOrBlank()) return path
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed parsing document ID: ${e.message}")
            }
        }

        // 2. Try standard MediaStore query for DATA column
        try {
            contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)?.use { cursor ->
                val col = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                if (col != -1 && cursor.moveToFirst()) {
                    val path = cursor.getString(col)
                    if (!path.isNullOrBlank()) return path
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed querying DATA column: ${e.message}")
        }

        // 3. If URI has a file scheme
        if (uri.scheme == "file") {
            return uri.path
        }

        return null
    }

    /**
     * Finds a MediaStore content URI matching a given filesystem path.
     */
    fun findMediaStoreUriForPath(context: Context, path: String): Uri? {
        val isVideo = path.endsWith(".mp4", ignoreCase = true) ||
                path.endsWith(".mkv", ignoreCase = true) ||
                path.endsWith(".3gp", ignoreCase = true)
        val contentUri = if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(MediaStore.MediaColumns._ID)
        val selection = "${MediaStore.MediaColumns.DATA}=?"
        val selectionArgs = arrayOf(path)

        try {
            context.contentResolver.query(contentUri, projection, selection, selectionArgs, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
                    return ContentUris.withAppendedId(contentUri, id)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "findMediaStoreUriForPath failed: ${e.message}")
        }
        return null
    }

    /**
     * Converts a Document URI or generic content URI to a direct MediaStore content URI if possible.
     */
    fun toMediaStoreUri(context: Context, uri: Uri): Uri {
        if (uri.authority == "media" || uri.toString().startsWith("content://media/")) {
            return uri
        }

        if (DocumentsContract.isDocumentUri(context, uri)) {
            try {
                val docId = DocumentsContract.getDocumentId(uri)
                if (docId != null && docId.contains(":")) {
                    val split = docId.split(":")
                    val type = split[0].lowercase()
                    val id = split[1].toLongOrNull()
                    if (id != null) {
                        return if (type.contains("video") || uri.toString().contains("video")) {
                            ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                        } else {
                            ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error converting to MediaStore URI: ${e.message}")
            }
        }

        val realPath = resolveRealPath(context, uri)
        if (!realPath.isNullOrBlank()) {
            val foundUri = findMediaStoreUriForPath(context, realPath)
            if (foundUri != null) return foundUri
        }

        return uri
    }

    /**
     * Attempts to delete the original unencrypted media from device storage and gallery.
     * Tries DocumentsContract deletion, direct file deletion, ContentResolver deletion,
     * and forces MediaScanner to update gallery thumbnails.
     * Returns true if deleted immediately, false if system confirmation is required.
     */
    fun deleteOriginalMedia(context: Context, uri: Uri): Boolean {
        var isDeleted = false
        val contentResolver = context.contentResolver
        val realPath = resolveRealPath(context, uri)
        val mediaStoreUri = toMediaStoreUri(context, uri)

        // 1. Try DocumentsContract.deleteDocument if it's a Document URI
        if (DocumentsContract.isDocumentUri(context, uri)) {
            try {
                if (DocumentsContract.deleteDocument(contentResolver, uri)) {
                    isDeleted = true
                    Log.d(TAG, "DocumentsContract deleted document: $uri")
                }
            } catch (e: Exception) {
                Log.w(TAG, "DocumentsContract.deleteDocument failed: ${e.message}")
            }
        }

        // 2. Try direct file deletion if path was resolved
        if (!realPath.isNullOrBlank()) {
            try {
                val file = File(realPath)
                if (file.exists() && file.delete()) {
                    isDeleted = true
                    Log.d(TAG, "Deleted original file directly: $realPath")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Direct file deletion failed: ${e.message}")
            }
        }

        // 3. Try deleting via ContentResolver with the MediaStore URI
        try {
            val rows = contentResolver.delete(mediaStoreUri, null, null)
            if (rows > 0) {
                isDeleted = true
                Log.d(TAG, "ContentResolver deleted $rows rows for $mediaStoreUri")
            }
        } catch (e: RecoverableSecurityException) {
            Log.d(TAG, "RecoverableSecurityException on delete: user consent required")
            return false
        } catch (e: Exception) {
            Log.w(TAG, "ContentResolver.delete failed: ${e.message}")
        }

        // 4. Fallback: Try original URI if different
        if (!isDeleted && mediaStoreUri != uri) {
            try {
                val rows = contentResolver.delete(uri, null, null)
                if (rows > 0) isDeleted = true
            } catch (_: Exception) {}
        }

        // 5. Force MediaScanner to purge thumbnail and index from Gallery
        if (!realPath.isNullOrBlank()) {
            try {
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(realPath),
                    null
                ) { path, scannedUri ->
                    Log.d(TAG, "MediaScanner updated after deletion: $path, uri: $scannedUri")
                }
            } catch (e: Exception) {
                Log.w(TAG, "MediaScanner scanFile failed: ${e.message}")
            }
        }

        return isDeleted
    }

    /**
     * Creates a system delete confirmation intent sender for Android 11+ (API 30+)
     * when silent deletion is blocked by OS Scoped Storage rules.
     */
    fun createSystemDeleteIntentSender(context: Context, uris: List<Uri>): IntentSender? {
        if (uris.isEmpty()) return null
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val mediaStoreUris = uris.map { toMediaStoreUri(context, it) }
                    .filter { it.authority == "media" || it.toString().startsWith("content://media/") }
                if (mediaStoreUris.isNotEmpty()) {
                    MediaStore.createDeleteRequest(context.contentResolver, mediaStoreUris).intentSender
                } else {
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
}
