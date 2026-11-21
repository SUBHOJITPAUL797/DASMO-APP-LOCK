package com.example.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import com.example.presentation.screens.home.InstalledAppEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Process-wide, disk-backed cache for installed launchable apps and their decoded icons.
 *
 * Eliminates the repeated delay when navigating back from Settings/Vault/Intruders or
 * reopening DASMO LOCK by:
 * 1. Keeping the app list and decoded app icons warm in memory across screen transitions.
 * 2. Reading a tiny JSON snapshot from cacheDir (~3ms) on cold start so the UI populates instantly.
 * 3. Using a single PackageManager.queryIntentActivities(ACTION_MAIN + CATEGORY_LAUNCHER) binder
 *    call instead of iterating getInstalledApplications(GET_META_DATA) + getLaunchIntentForPackage().
 */
object InstalledAppsCache {
    private const val TAG = "InstalledAppsCache"
    private const val CACHE_FILE_NAME = "installed_apps_cache_v1.json"
    private const val STALE_THRESHOLD_MS = 30_000L

    private val refreshMutex = Mutex()
    private val _appsFlow = MutableStateFlow<List<InstalledAppEntry>>(emptyList())
    val appsFlow: StateFlow<List<InstalledAppEntry>> = _appsFlow.asStateFlow()

    private val _isLoadingFlow = MutableStateFlow(false)
    val isLoadingFlow: StateFlow<Boolean> = _isLoadingFlow.asStateFlow()

    @Volatile
    private var lastRefreshedAtMillis: Long = 0L

    @Volatile
    private var diskLoadedOnce: Boolean = false

    private val iconCache = LruCache<String, ImageBitmap>(180)

    fun getCachedIcon(packageName: String): ImageBitmap? {
        if (packageName.isBlank()) return null
        return iconCache.get(packageName)
    }

    suspend fun loadIcon(context: Context, packageName: String): ImageBitmap? {
        if (packageName.isBlank()) return null
        iconCache.get(packageName)?.let { return it }
        return withContext(Dispatchers.IO) {
            try {
                val drawable = context.packageManager.getApplicationIcon(packageName)
                val bitmap = drawable.toBitmap(width = 112, height = 112).asImageBitmap()
                iconCache.put(packageName, bitmap)
                bitmap
            } catch (_: Exception) {
                null
            }
        }
    }

    /**
     * Marks the in-memory app list as stale so the next screen open refreshes in the background
     * without clearing currently displayed rows.
     */
    fun invalidate() {
        lastRefreshedAtMillis = 0L
    }

    /**
     * Clears in-memory state (used by unit tests).
     */
    internal fun resetForTesting() {
        _appsFlow.value = emptyList()
        _isLoadingFlow.value = false
        lastRefreshedAtMillis = 0L
        diskLoadedOnce = false
        iconCache.evictAll()
    }

    /**
     * Preloads disk cache immediately and triggers a background refresh while the user is still
     * unlocking the app entrance screen.
     */
    suspend fun preload(context: Context) {
        refreshIfStale(context = context, force = false)
    }

    /**
     * Ensures [appsFlow] is populated immediately (from memory or disk cache) and refreshes from
     * PackageManager only when stale (> 30s) or forced. Never clears existing items during refresh.
     */
    suspend fun refreshIfStale(context: Context, force: Boolean = false) {
        val appContext = context.applicationContext
        val now = System.currentTimeMillis()
        if (!force && _appsFlow.value.isNotEmpty() && (now - lastRefreshedAtMillis) < STALE_THRESHOLD_MS) {
            return
        }

        refreshMutex.withLock {
            val lockNow = System.currentTimeMillis()
            if (!force && _appsFlow.value.isNotEmpty() && (lockNow - lastRefreshedAtMillis) < STALE_THRESHOLD_MS) {
                return
            }

            // Step 1: Hydrate instantly from disk cache on cold start if memory is still empty
            if (!diskLoadedOnce && _appsFlow.value.isEmpty()) {
                diskLoadedOnce = true
                val cached = withContext(Dispatchers.IO) { readDiskCache(appContext) }
                if (cached.isNotEmpty()) {
                    _appsFlow.value = cached
                }
            }

            val shouldShowSpinner = _appsFlow.value.isEmpty()
            if (shouldShowSpinner) {
                _isLoadingFlow.value = true
            }

            try {
                val fresh = withContext(Dispatchers.IO) {
                    queryLaunchableAppsFast(appContext)
                }
                if (fresh != _appsFlow.value) {
                    _appsFlow.value = fresh
                }
                lastRefreshedAtMillis = System.currentTimeMillis()
                withContext(Dispatchers.IO) {
                    writeDiskCache(appContext, fresh)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Fast app list refresh failed: ${e.message}")
            } finally {
                _isLoadingFlow.value = false
            }
        }
    }

    /**
     * Queries all launchable apps in a SINGLE PackageManager IPC call via ACTION_MAIN /
     * CATEGORY_LAUNCHER instead of calling getLaunchIntentForPackage() hundreds of times.
     */
    internal fun queryLaunchableAppsFast(context: Context): List<InstalledAppEntry> {
        val pm = context.packageManager
        val ownPackage = context.packageName
        val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val resolved = try {
            pm.queryIntentActivities(launcherIntent, 0)
        } catch (_: Exception) {
            emptyList()
        }

        val byPackage = LinkedHashMap<String, InstalledAppEntry>(resolved.size + 4)

        for (info in resolved) {
            val appInfo = info.activityInfo?.applicationInfo ?: continue
            val pkg = appInfo.packageName ?: continue
            if (pkg == ownPackage || byPackage.containsKey(pkg)) continue
            val label = try {
                info.loadLabel(pm)?.toString()?.takeIf { it.isNotBlank() }
                    ?: pm.getApplicationLabel(appInfo).toString()
            } catch (_: Exception) {
                pkg
            }
            byPackage[pkg] = InstalledAppEntry(
                packageName = pkg,
                label = label,
                flags = appInfo.flags
            )
        }

        // Ensure critical protectable system targets are always selectable even if ROM hides them from launcher
        val criticalSystemPackages = listOf("com.android.settings", "com.android.vending")
        for (pkg in criticalSystemPackages) {
            if (pkg == ownPackage || byPackage.containsKey(pkg)) continue
            try {
                val appInfo = pm.getApplicationInfo(pkg, 0)
                val label = pm.getApplicationLabel(appInfo).toString().ifBlank { pkg }
                byPackage[pkg] = InstalledAppEntry(
                    packageName = pkg,
                    label = label,
                    flags = appInfo.flags
                )
            } catch (_: Exception) {
                // Not installed on this device
            }
        }

        // Fallback if a test environment / Robolectric ShadowPackageManager only registered installed applications
        if (byPackage.isEmpty()) {
            val installed = try {
                pm.getInstalledApplications(0)
            } catch (_: Exception) {
                emptyList()
            }
            for (appInfo in installed) {
                val pkg = appInfo.packageName
                if (pkg == ownPackage || byPackage.containsKey(pkg)) continue
                if (pm.getLaunchIntentForPackage(pkg) == null &&
                    !pkg.contains("settings") &&
                    !pkg.contains("vending")
                ) continue
                val label = try {
                    appInfo.loadLabel(pm).toString()
                } catch (_: Exception) {
                    pkg
                }
                byPackage[pkg] = InstalledAppEntry(
                    packageName = pkg,
                    label = label,
                    flags = appInfo.flags
                )
            }
        }

        return byPackage.values.sortedBy { it.label.lowercase() }
    }

    internal fun encodeAppsJson(apps: List<InstalledAppEntry>): String {
        val array = JSONArray()
        for (app in apps) {
            val obj = JSONObject()
            obj.put("p", app.packageName)
            obj.put("l", app.label)
            obj.put("f", app.flags)
            array.put(obj)
        }
        return array.toString()
    }

    internal fun decodeAppsJson(raw: String): List<InstalledAppEntry> {
        if (raw.isBlank()) return emptyList()
        return try {
            val array = JSONArray(raw)
            buildList(array.length()) {
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    val pkg = obj.optString("p", "").trim()
                    val label = obj.optString("l", "").trim()
                    val flags = obj.optInt("f", 0)
                    if (pkg.isNotEmpty() && label.isNotEmpty()) {
                        add(InstalledAppEntry(packageName = pkg, label = label, flags = flags))
                    }
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun readDiskCache(context: Context): List<InstalledAppEntry> {
        return try {
            val file = File(context.cacheDir, CACHE_FILE_NAME)
            if (!file.exists() || file.length() == 0L) return emptyList()
            decodeAppsJson(file.readText(Charsets.UTF_8))
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun writeDiskCache(context: Context, apps: List<InstalledAppEntry>) {
        try {
            if (apps.isEmpty()) return
            val file = File(context.cacheDir, CACHE_FILE_NAME)
            file.writeText(encodeAppsJson(apps), Charsets.UTF_8)
        } catch (_: Exception) {
            // Non-critical optimization cache
        }
    }
}
