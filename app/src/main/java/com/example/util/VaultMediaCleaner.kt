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
     * Returns an Intent configured to open the All Files Access settings for DASMO.
     */
    fun getAllFilesAccessIntent(context: Context): Intent {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                return Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            } catch (_: Exception) {
                try {
                    return Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                } catch (_: Exception) {}
            }
        }
        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /**
     * Directly opens Android System Settings to grant All Files Access to DASMO.
     */
    fun openAllFilesAccessSettings(context: Context) {
        try {
            val intent = getAllFilesAccessIntent(context)
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Cannot open storage settings", e)
        }
    }

    /**
     * Exhaustively searches standard storage directories on the device for a file matching
     * [displayName] and optional [expectedSize]. Works directly on external storage.
     */
    fun findFileOnDevice(displayName: String, expectedSize: Long = 0L): File? {
        if (displayName.isBlank()) return null

        val storageRoot = Environment.getExternalStorageDirectory()

        // 1. Common target directories where photos and videos are saved
        val searchDirs = listOf(
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "Camera"),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "Screenshots"),
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "Restored"),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Screenshots"),
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Telegram"),
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "WhatsApp"),
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Instagram"),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "Telegram"),
            File(storageRoot, "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Images"),
            File(storageRoot, "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Video"),
            File(storageRoot, "WhatsApp/Media/WhatsApp Images"),
            File(storageRoot, "WhatsApp/Media/WhatsApp Video")
        )

        // Step A: Exact filename lookup in standard folders
        for (dir in searchDirs) {
            if (!dir.exists() || !dir.isDirectory) continue
            val directMatch = File(dir, displayName)
            if (directMatch.exists() && directMatch.isFile) {
                if (expectedSize <= 0L || directMatch.length() == expectedSize || Math.abs(directMatch.length() - expectedSize) < 2048) {
                    Log.d(TAG, "Direct match found in ${dir.name}: ${directMatch.absolutePath}")
                    return directMatch
                }
            }
        }

        // Step B: Case-insensitive filename match in standard folders
        for (dir in searchDirs) {
            if (!dir.exists() || !dir.isDirectory) continue
            val matched = dir.listFiles { f ->
                f.isFile && f.name.equals(displayName, ignoreCase = true)
            }?.firstOrNull()
            if (matched != null && matched.exists()) {
                Log.d(TAG, "Case-insensitive match found in ${dir.name}: ${matched.absolutePath}")
                return matched
            }
        }

        // Step C: Check 1st-level subdirectories of DCIM, Pictures, and Movies
        val parentContainers = listOf(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
        )
        for (parent in parentContainers) {
            if (!parent.exists() || !parent.isDirectory) continue
            val subFolders = parent.listFiles { f -> f.isDirectory } ?: continue
            for (sub in subFolders) {
                val candidate = File(sub, displayName)
                if (candidate.exists() && candidate.isFile) {
                    Log.d(TAG, "Subdirectory match found in ${sub.name}: ${candidate.absolutePath}")
                    return candidate
                }
            }
        }

        // Step D: Match by exact size if size is known and displayName was synthetic
        if (expectedSize > 1024L) {
            val cameraDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "Camera")
            if (cameraDir.exists()) {
                val sizeMatches = cameraDir.listFiles { f ->
                    f.isFile && f.length() == expectedSize
                }
                if (sizeMatches != null && sizeMatches.size == 1) {
                    Log.d(TAG, "Unique size match found in Camera: ${sizeMatches[0].absolutePath}")
                    return sizeMatches[0]
                }
            }
        }

        return null
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

        // 1. Query the input URI for basic metadata (DISPLAY_NAME, SIZE, DATA, _ID)
        try {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIdx != -1) displayName = cursor.getString(nameIdx) ?: ""

                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIdx != -1) size = cursor.getLong(sizeIdx)

                    val dataIdx = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                    if (dataIdx != -1) {
                        val d = cursor.getString(dataIdx)
                        if (!d.isNullOrBlank()) candidatePath = d
                    }

                    val idIdx = cursor.getColumnIndex(MediaStore.MediaColumns._ID)
                    if (idIdx != -1) candidateId = cursor.getLong(idIdx)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Query metadata on $uri failed: ${e.message}")
        }

        // 2. Extract numeric ID from URI path if possible (e.g. photo picker uri: .../media/1000000035)
        if (candidateId == null || candidateId!! <= 0) {
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
                    } else if (type.equals("image", ignoreCase = true) || type.equals("video", ignoreCase = true)) {
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

        val canonicalById = if (candidateId != null && candidateId!! > 0) {
            ContentUris.withAppendedId(collection, candidateId!!)
        } else {
            null
        }

        // 4. Strategy A: Query MediaStore by candidateId
        if (candidateId != null && candidateId!! > 0) {
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
                        val resolvedPath = path ?: candidatePath
                        return MediaEntry(canonicalById, resolvedPath, (name ?: "").ifBlank { displayName })
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
                        return MediaEntry(ContentUris.withAppendedId(collection, id), candidatePath, (name ?: "").ifBlank { displayName })
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Query by DATA failed: ${e.message}")
            }
        }

        // 6. Strategy C: Query MediaStore by DISPLAY_NAME
        if (displayName.isNotBlank()) {
            try {
                val selection = if (size > 0) "${MediaStore.MediaColumns.DISPLAY_NAME}=? AND ${MediaStore.MediaColumns.SIZE}=?" else "${MediaStore.MediaColumns.DISPLAY_NAME}=?"
                val selectionArgs = if (size > 0) arrayOf(displayName, size.toString()) else arrayOf(displayName)
                contentResolver.query(
                    collection,
                    arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DATA, MediaStore.MediaColumns.DISPLAY_NAME),
                    selection,
                    selectionArgs,
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
                Log.w(TAG, "Query by DISPLAY_NAME failed: ${e.message}")
            }
        }

        // 7. Strategy D: Search device storage directly for physical file
        if (candidatePath.isNullOrBlank() || !File(candidatePath).exists()) {
            val found = findFileOnDevice(displayName, size)
            if (found != null && found.exists()) {
                candidatePath = found.absolutePath
                // Try to resolve canonical URI from found path
                try {
                    contentResolver.query(
                        collection,
                        arrayOf(MediaStore.MediaColumns._ID),
                        "${MediaStore.MediaColumns.DATA}=?",
                        arrayOf(candidatePath),
                        null
                    )?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
                            return MediaEntry(ContentUris.withAppendedId(collection, id), candidatePath, displayName)
                        }
                    }
                } catch (_: Exception) {}
                return MediaEntry(canonicalById, candidatePath, displayName)
            }
        }

        // 8. Fallback: Check if original uri is already canonical MediaStore URI
        val isOriginalMedia = uri.authority == "media" && (uri.path?.contains("/images/media") == true || uri.path?.contains("/video/media") == true)
        val finalCanonical = if (isOriginalMedia) uri else canonicalById

        return MediaEntry(finalCanonical, candidatePath, displayName)
    }

    /**
     * Deletes the original unencrypted media completely from device storage and gallery.
     * Uses direct file deletion, ContentResolver deletion, and MediaScanner eviction.
     */
    fun deleteOriginalMedia(context: Context, uri: Uri, entry: MediaEntry): DeleteResult {
        var isDeleted = false
        val contentResolver = context.contentResolver

        // 1. Resolve path if missing
        var resolvedPath = entry.realPath
        if (resolvedPath.isNullOrBlank() || !File(resolvedPath).exists()) {
            val found = findFileOnDevice(entry.displayName)
            if (found != null && found.exists()) {
                resolvedPath = found.absolutePath
            }
        }

        // 2. Direct physical file deletion (works 100% when MANAGE_EXTERNAL_STORAGE is granted or on Android 9/10)
        if (!resolvedPath.isNullOrBlank()) {
            try {
                val file = File(resolvedPath)
                if (file.exists() && file.delete()) {
                    isDeleted = true
                    Log.d(TAG, "Direct File.delete succeeded on disk: $resolvedPath")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Direct File.delete failed: ${e.message}")
            }
        }

        // 3. ContentResolver deletion using canonical MediaStore URI
        val canonicalUri = entry.canonicalUri
        if (canonicalUri != null) {
            try {
                val rows = contentResolver.delete(canonicalUri, null, null)
                if (rows > 0) {
                    isDeleted = true
                    Log.d(TAG, "ContentResolver.delete succeeded on canonicalUri: $canonicalUri ($rows rows)")
                }
            } catch (e: RecoverableSecurityException) {
                Log.d(TAG, "RecoverableSecurityException on delete: user consent required")
            } catch (e: SecurityException) {
                Log.d(TAG, "SecurityException on delete: user consent required (Android 11+)")
            } catch (e: Exception) {
                Log.w(TAG, "ContentResolver.delete failed: ${e.message}")
            }
        }

        // 4. Also delete MediaStore record by DATA path if known
        if (!resolvedPath.isNullOrBlank()) {
            try {
                val isVideo = (canonicalUri?.toString()?.contains("video") == true) || uri.toString().contains("video")
                val collection = if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                contentResolver.delete(collection, "${MediaStore.MediaColumns.DATA}=?", arrayOf(resolvedPath))
            } catch (_: Exception) {}
        }

        // 5. Fallback: try original URI if different and not deleted yet
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

        // 6. Force MediaScanner to immediately evict thumbnail and index from Gallery
        if (!resolvedPath.isNullOrBlank()) {
            try {
                MediaScannerConnection.scanFile(context, arrayOf(resolvedPath), null) { scannedPath, _ ->
                    Log.d(TAG, "MediaScanner evicted: $scannedPath")
                }
            } catch (_: Exception) {}

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                try {
                    context.sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(File(resolvedPath))))
                } catch (_: Exception) {}
            }
        }

        return DeleteResult(
            isDeleted = isDeleted,
            canonicalUri = canonicalUri,
            realPath = resolvedPath
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
                        val isVideo = uri.toString().contains("video", ignoreCase = true)
                        val entry = resolveCanonicalEntry(context, uri, isVideo)
                        entry.canonicalUri ?: if (uri.lastPathSegment?.toLongOrNull() != null) {
                            val collection = if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                            ContentUris.withAppendedId(collection, uri.lastPathSegment!!.toLong())
                        } else null
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
