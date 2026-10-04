package com.example.util.update

import java.io.File

data class GitHubAsset(
    val id: Long,
    val name: String,
    val size: Long,
    val downloadUrl: String,
    val contentType: String
)

data class GitHubRelease(
    val id: Long,
    val tagName: String,
    val name: String,
    val body: String,
    val htmlUrl: String,
    val publishedAt: String,
    val isPrerelease: Boolean,
    val isDraft: Boolean,
    val assets: List<GitHubAsset>
)

data class AppUpdateInfo(
    val currentVersion: String,
    val currentVersionCode: Int,
    val latestVersion: String,
    val latestVersionCode: Int,
    val releaseTitle: String,
    val changelog: String,
    val publishedAt: String,
    val htmlUrl: String,
    val apkUrl: String,
    val apkName: String,
    val apkSizeBytes: Long
) {
    val formattedSize: String
        get() {
            if (apkSizeBytes <= 0) return "Unknown size"
            val mb = apkSizeBytes / (1024.0 * 1024.0)
            return "%.1f MB".format(mb)
        }
}

sealed interface UpdateStatus {
    object Idle : UpdateStatus
    object Checking : UpdateStatus
    data class UpdateAvailable(val updateInfo: AppUpdateInfo) : UpdateStatus
    data class UpToDate(val currentVersion: String, val checkedAtMillis: Long = System.currentTimeMillis()) : UpdateStatus
    data class Downloading(
        val updateInfo: AppUpdateInfo,
        val progressPercent: Int,
        val bytesDownloaded: Long,
        val totalBytes: Long
    ) : UpdateStatus
    data class ReadyToInstall(
        val updateInfo: AppUpdateInfo,
        val apkFile: File
    ) : UpdateStatus
    data class Error(val message: String, val isNetworkError: Boolean = false) : UpdateStatus
}
