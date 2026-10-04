package com.example.util.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * Enterprise-grade in-app update manager backed directly by GitHub Releases.
 * Connects to: https://github.com/SUBHOJITPAUL797/DASMO-APP-LOCK
 */
class AppUpdateManager {

    companion object {
        const val GITHUB_OWNER = "SUBHOJITPAUL797"
        const val GITHUB_REPO = "DASMO-APP-LOCK"
        const val RELEASES_API_URL = "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"
        const val REPO_RELEASES_WEB_URL = "https://github.com/$GITHUB_OWNER/$GITHUB_REPO/releases"

        @Volatile
        private var instance: AppUpdateManager? = null

        fun getInstance(): AppUpdateManager {
            return instance ?: synchronized(this) {
                instance ?: AppUpdateManager().also { instance = it }
            }
        }
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    /**
     * Checks GitHub Releases API for the latest release published on the repository.
     */
    suspend fun checkForUpdate(context: Context): UpdateStatus = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(RELEASES_API_URL)
                .addHeader("Accept", "application/vnd.github.v3+json")
                .addHeader("User-Agent", "DasmoAppLock-Android/${BuildConfig.VERSION_NAME}")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.code == 404) {
                    return@withContext UpdateStatus.Error(
                        "No releases found yet on repository ($GITHUB_OWNER/$GITHUB_REPO)."
                    )
                }

                if (!response.isSuccessful) {
                    return@withContext UpdateStatus.Error(
                        "GitHub returned HTTP status code ${response.code}."
                    )
                }

                val responseBody = response.body?.string() ?: return@withContext UpdateStatus.Error("Empty response from GitHub.")
                val json = JSONObject(responseBody)

                val tagName = json.optString("tag_name", "").trim()
                val releaseName = json.optString("name", tagName)
                val releaseBody = json.optString("body", "")
                val htmlUrl = json.optString("html_url", REPO_RELEASES_WEB_URL)
                val publishedAt = json.optString("published_at", "")
                val isDraft = json.optBoolean("draft", false)
                val isPrerelease = json.optBoolean("prerelease", false)

                if (isDraft) {
                    return@withContext UpdateStatus.UpToDate(BuildConfig.VERSION_NAME)
                }

                val assetsJson = json.optJSONArray("assets") ?: JSONArray()
                val assets = mutableListOf<GitHubAsset>()
                var apkAsset: GitHubAsset? = null

                for (i in 0 until assetsJson.length()) {
                    val assetObj = assetsJson.getJSONObject(i)
                    val id = assetObj.optLong("id")
                    val name = assetObj.optString("name", "")
                    val size = assetObj.optLong("size", 0L)
                    val downloadUrl = assetObj.optString("browser_download_url", "")
                    val contentType = assetObj.optString("content_type", "")

                    val asset = GitHubAsset(id, name, size, downloadUrl, contentType)
                    assets.add(asset)

                    if (name.endsWith(".apk", ignoreCase = true) ||
                        contentType.equals("application/vnd.android.package-archive", ignoreCase = true)
                    ) {
                        apkAsset = asset
                    }
                }

                val currentVersionName = BuildConfig.VERSION_NAME
                val currentVersionCode = BuildConfig.VERSION_CODE

                val cleanLatestVersion = cleanVersionTag(tagName)
                val latestVersionCode = extractVersionCode(releaseBody, cleanLatestVersion)

                val hasNewVersion = isNewerVersion(
                    currentVer = currentVersionName,
                    currentCode = currentVersionCode,
                    latestVer = cleanLatestVersion,
                    latestCode = latestVersionCode
                )

                if (hasNewVersion) {
                    val finalApkUrl = apkAsset?.downloadUrl ?: htmlUrl
                    val finalApkName = apkAsset?.name ?: "dasmo-applock-$cleanLatestVersion.apk"
                    val finalApkSize = apkAsset?.size ?: 0L

                    val updateInfo = AppUpdateInfo(
                        currentVersion = currentVersionName,
                        currentVersionCode = currentVersionCode,
                        latestVersion = cleanLatestVersion,
                        latestVersionCode = latestVersionCode,
                        releaseTitle = releaseName.ifBlank { "Version $cleanLatestVersion" },
                        changelog = cleanChangelog(releaseBody),
                        publishedAt = publishedAt,
                        htmlUrl = htmlUrl,
                        apkUrl = finalApkUrl,
                        apkName = finalApkName,
                        apkSizeBytes = finalApkSize
                    )
                    return@withContext UpdateStatus.UpdateAvailable(updateInfo)
                } else {
                    return@withContext UpdateStatus.UpToDate(currentVersionName)
                }
            }
        } catch (e: IOException) {
            return@withContext UpdateStatus.Error(
                message = "Unable to connect to GitHub. Please check internet connection.",
                isNetworkError = true
            )
        } catch (e: Exception) {
            return@withContext UpdateStatus.Error(
                message = e.localizedMessage ?: "Unexpected error checking for updates."
            )
        }
    }

    /**
     * Downloads the APK file with live progress emission.
     */
    suspend fun downloadApk(
        context: Context,
        updateInfo: AppUpdateInfo,
        onProgress: (UpdateStatus.Downloading) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val updatesDir = File(context.cacheDir, "updates")
        if (!updatesDir.exists()) {
            updatesDir.mkdirs()
        }

        val apkFile = File(updatesDir, updateInfo.apkName)

        // If file already fully downloaded and size matches, re-use it
        if (apkFile.exists() && updateInfo.apkSizeBytes > 0 && apkFile.length() == updateInfo.apkSizeBytes) {
            onProgress(
                UpdateStatus.Downloading(
                    updateInfo = updateInfo,
                    progressPercent = 100,
                    bytesDownloaded = apkFile.length(),
                    totalBytes = apkFile.length()
                )
            )
            return@withContext apkFile
        }

        val request = Request.Builder()
            .url(updateInfo.apkUrl)
            .addHeader("Accept", "application/octet-stream")
            .addHeader("User-Agent", "DasmoAppLock-Android/${BuildConfig.VERSION_NAME}")
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Download failed with HTTP ${response.code}")
            }

            val body = response.body ?: throw IOException("Empty response downloading APK")
            val totalBytes = if (body.contentLength() > 0) body.contentLength() else updateInfo.apkSizeBytes

            body.byteStream().use { input ->
                FileOutputStream(apkFile).use { output ->
                    val buffer = ByteArray(8 * 1024)
                    var bytesRead: Int
                    var totalDownloaded = 0L
                    var lastPercentReported = -1

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalDownloaded += bytesRead

                        val percent = if (totalBytes > 0) {
                            ((totalDownloaded * 100) / totalBytes).toInt().coerceIn(0, 100)
                        } else {
                            0
                        }

                        if (percent != lastPercentReported) {
                            lastPercentReported = percent
                            onProgress(
                                UpdateStatus.Downloading(
                                    updateInfo = updateInfo,
                                    progressPercent = percent,
                                    bytesDownloaded = totalDownloaded,
                                    totalBytes = totalBytes
                                )
                            )
                        }
                    }
                    output.flush()
                }
            }
        }

        return@withContext apkFile
    }

    /**
     * Prompts the Android OS package installer to install the downloaded APK.
     */
    fun installApk(context: Context, apkFile: File) {
        if (!apkFile.exists()) {
            return
        }

        // On Android 8.0+ (Oreo), check if permission to install unknown apps is granted
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                val settingsIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(settingsIntent)
                return
            }
        }

        val contentUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )

        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(contentUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        context.startActivity(installIntent)
    }

    /**
     * Opens the GitHub Release page in user's external browser as an instant fallback.
     */
    fun openReleaseInBrowser(context: Context, url: String) {
        try {
            val targetUrl = if (url.isNotBlank()) url else REPO_RELEASES_WEB_URL
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            // Ignored
        }
    }

    // --- Helper Parsing Methods ---

    internal fun cleanVersionTag(tag: String): String {
        return tag.trim().removePrefix("v").removePrefix("V")
    }

    internal fun extractVersionCode(body: String, fallbackVer: String): Int {
        val pattern = Pattern.compile("(?i)(?:versionCode|code|build)\\s*[:=]\\s*(\\d+)")
        val matcher = pattern.matcher(body)
        if (matcher.find()) {
            return matcher.group(1)?.toIntOrNull() ?: 1
        }
        val parts = fallbackVer.split(".").mapNotNull { it.toIntOrNull() }
        return when (parts.size) {
            1 -> parts[0]
            2 -> parts[0] * 1000 + parts[1]
            3 -> parts[0] * 10000 + parts[1] * 100 + parts[2]
            else -> 1
        }
    }

    internal fun isNewerVersion(
        currentVer: String,
        currentCode: Int,
        latestVer: String,
        latestCode: Int
    ): Boolean {
        if (latestCode > currentCode) return true
        if (latestCode < currentCode && latestCode > 1) return false

        val currentClean = cleanVersionTag(currentVer)
        val latestClean = cleanVersionTag(latestVer)

        val currentParts = currentClean.split(".").map { it.toIntOrNull() ?: 0 }
        val latestParts = latestClean.split(".").map { it.toIntOrNull() ?: 0 }

        val maxLen = maxOf(currentParts.size, latestParts.size)
        for (i in 0 until maxLen) {
            val c = if (i < currentParts.size) currentParts[i] else 0
            val l = if (i < latestParts.size) latestParts[i] else 0
            if (l > c) return true
            if (l < c) return false
        }

        return false
    }

    private fun cleanChangelog(body: String): String {
        if (body.isBlank()) {
            return "• Performance improvements and security fixes.\n• Vault stability and intruder capture optimizations."
        }
        return body.trim()
    }
}
