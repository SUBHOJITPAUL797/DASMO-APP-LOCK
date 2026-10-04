package com.example.presentation.screens.home

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.repository.AppLockRepository
import com.example.presentation.screens.settings.SettingsDialog
import com.example.util.SecurePreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.BorderStroke

@Composable
fun AppIcon(packageName: String, packageManager: PackageManager) {
    var icon by remember { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(packageName) {
        withContext(Dispatchers.IO) {
            try {
                val drawable = packageManager.getApplicationIcon(packageName)
                val bitmap = drawable.toBitmap(width = 120, height = 120)
                icon = bitmap.asImageBitmap()
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    if (icon != null) {
        Image(
            bitmap = icon!!,
            contentDescription = null,
            modifier = Modifier.size(40.dp)
        )
    } else {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
        )
    }
}

private fun isSocialApp(pkg: String, label: String): Boolean {
    val lowPkg = pkg.lowercase()
    val lowLabel = label.lowercase()
    val keywords = listOf(
        "whatsapp", "instagram", "facebook", "telegram", "twitter", "snapchat",
        "tiktok", "reddit", "discord", "messenger", "signal", "viber", "wechat",
        "chat", "social", "skype", "threads"
    )
    return keywords.any { lowPkg.contains(it) || lowLabel.contains(it) }
}

private fun isFinanceApp(pkg: String, label: String): Boolean {
    val lowPkg = pkg.lowercase()
    val lowLabel = label.lowercase()
    val keywords = listOf(
        "pay", "bank", "wallet", "cash", "crypto", "binance", "coinbase",
        "chase", "paypal", "stripe", "revolut", "venmo", "phonepe", "paytm",
        "cred", "finance", "money", "invest"
    )
    return keywords.any { lowPkg.contains(it) || lowLabel.contains(it) }
}

private fun isSystemApp(pkg: String, label: String, flags: Int): Boolean {
    val lowPkg = pkg.lowercase()
    val lowLabel = label.lowercase()
    val isSysFlag = (flags and ApplicationInfo.FLAG_SYSTEM) != 0
    val keywords = listOf("settings", "vending", "camera", "gallery", "photos", "contacts", "dialer", "files", "packageinstaller")
    return isSysFlag || keywords.any { lowPkg.contains(it) || lowLabel.contains(it) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppGuardHomeScreen(
    repository: AppLockRepository,
    context: Context,
    onNavigateToIntruders: () -> Unit,
    onNavigateToVault: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var installedApps by remember { mutableStateOf<List<Triple<String, String, Int>>>(emptyList()) }
    val lockedApps by repository.allLockedApps.collectAsStateWithLifecycle(initialValue = emptyList())
    var isUsageStatsEnabled by remember { mutableStateOf(false) }
    var isOverlayEnabled by remember { mutableStateOf(false) }

    var showSystemApps by remember { mutableStateOf(true) }
    var showPinDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") } // "All", "Locked", "Social", "Finance", "System"

    val pm = context.packageManager
    val securePrefs = remember { SecurePreferences(context) }
    var hiddenApps by remember { mutableStateOf<Set<String>>(emptySet()) }
    var intruderCount by remember { mutableStateOf(0) }
    var vaultCount by remember { mutableStateOf(0) }

    fun loadCounts() {
        val hiddenAppsStr = securePrefs.getString("hidden_apps", "") ?: ""
        hiddenApps = hiddenAppsStr.split(",").filter { it.isNotEmpty() }.toSet()
        val intDir = context.getDir("intruders", Context.MODE_PRIVATE)
        intruderCount = intDir.listFiles()?.size ?: 0
        val vaultDir = context.getDir("secure_vault", Context.MODE_PRIVATE)
        vaultCount = vaultDir.listFiles()?.filter { it.name.endsWith(".enc") }?.size ?: 0
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            loadCounts()
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { _ -> }
    )

    LaunchedEffect(showSystemApps) {
        withContext(Dispatchers.IO) {
            val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            val appList = packages.mapNotNull { 
                if (it.packageName == context.packageName) return@mapNotNull null
                if (!showSystemApps && (it.flags and ApplicationInfo.FLAG_SYSTEM) != 0) return@mapNotNull null
                if (pm.getLaunchIntentForPackage(it.packageName) == null && !it.packageName.contains("settings") && !it.packageName.contains("vending")) return@mapNotNull null
                
                Triple(it.packageName, it.loadLabel(pm).toString(), it.flags)
            }.sortedBy { it.second }
            installedApps = appList
        }
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                loadCounts()
                val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
                val mode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    appOps.unsafeCheckOpNoThrow(
                        android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                        android.os.Process.myUid(),
                        context.packageName
                    )
                } else {
                    appOps.checkOpNoThrow(
                        android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                        android.os.Process.myUid(),
                        context.packageName
                    )
                }
                isUsageStatsEnabled = mode == android.app.AppOpsManager.MODE_ALLOWED
                isOverlayEnabled = Settings.canDrawOverlays(context)
                
                if (isUsageStatsEnabled && isOverlayEnabled) {
                    val intent = Intent(context, com.example.service.AppLockService::class.java)
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        context.startForegroundService(intent)
                    } else {
                        context.startService(intent)
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
        }
    }

    if (showPinDialog) {
        SettingsDialog(
            onDismiss = {
                showPinDialog = false
                loadCounts()
            },
            context = context
        )
    }

    // Filter apps
    val filteredApps = remember(installedApps, lockedApps, searchQuery, selectedCategory) {
        installedApps.filter { (pkg, label, flags) ->
            val matchesSearch = searchQuery.isBlank() || 
                label.contains(searchQuery, ignoreCase = true) || 
                pkg.contains(searchQuery, ignoreCase = true)

            val matchesCategory = when (selectedCategory) {
                "Locked" -> lockedApps.any { it.packageName == pkg && it.isLocked }
                "Social" -> isSocialApp(pkg, label)
                "Finance" -> isFinanceApp(pkg, label)
                "System" -> isSystemApp(pkg, label, flags)
                else -> true
            }

            matchesSearch && matchesCategory
        }
    }

    // Calculate Privacy Health Score
    val healthScore = remember(lockedApps, securePrefs) {
        var score = 30 // Base baseline
        val hasSettingsLocked = lockedApps.any { it.isLocked && (it.packageName.contains("settings") || it.packageName.contains("vending")) }
        if (hasSettingsLocked) score += 25
        val hasSocialLocked = lockedApps.any { it.isLocked && isSocialApp(it.packageName, it.appLabel) }
        if (hasSocialLocked) score += 15
        if (securePrefs.getString("scramble_keypad", "false") == "true") score += 10
        if (securePrefs.getString("dynamic_time_pin", "false") == "true") score += 10
        if (securePrefs.getString("prevent_screenshots", "true") == "true") score += 10
        score.coerceIn(0, 100)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("DASMO LOCK", fontWeight = FontWeight.Bold)
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToVault) {
                        BadgedBox(badge = {
                            if (vaultCount > 0) {
                                Badge { Text(vaultCount.toString()) }
                            }
                        }) {
                            Icon(Icons.Default.Lock, contentDescription = "Vault")
                        }
                    }
                    IconButton(onClick = onNavigateToIntruders) {
                        BadgedBox(badge = {
                            if (intruderCount > 0) {
                                Badge(containerColor = MaterialTheme.colorScheme.error) { Text(intruderCount.toString()) }
                            }
                        }) {
                            Icon(Icons.Default.Person, contentDescription = "Intruders")
                        }
                    }
                    IconButton(onClick = { showPinDialog = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
        ) {
            // Permission Warnings
            if (!isUsageStatsEnabled) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Usage Access Required", color = MaterialTheme.colorScheme.onErrorContainer, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("DASMO LOCK needs Usage Access to detect foreground apps.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = {
                            context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                        }) {
                            Text("Grant Usage Access")
                        }
                    }
                }
            }

            if (isUsageStatsEnabled && !isOverlayEnabled) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Overlay Permission Required", color = MaterialTheme.colorScheme.onErrorContainer, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Required to display the secure lockscreen over protected apps.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = {
                            val intent = Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}")
                            )
                            context.startActivity(intent)
                        }) {
                            Text("Grant Overlay Permission")
                        }
                    }
                }
            }

            // Privacy Health Score Card
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF161028)),
                border = BorderStroke(1.dp, Color(0xFF4C2A85)),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0xFF24123E), Color(0xFF130E20))
                            )
                        )
                        .padding(16.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    "Security Health",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                val statusText = when {
                                    healthScore >= 80 -> "Shield Active • Highly Protected"
                                    healthScore >= 50 -> "Moderate • Recommended Actions"
                                    else -> "Vulnerable • Protect Settings & Apps"
                                }
                                val statusColor = when {
                                    healthScore >= 80 -> Color(0xFFC084FC)
                                    healthScore >= 50 -> Color(0xFFFBBF24)
                                    else -> Color(0xFFF43F5E)
                                }
                                Text(statusText, style = MaterialTheme.typography.labelSmall, color = statusColor)
                            }
                            Surface(
                                shape = CircleShape,
                                color = when {
                                    healthScore >= 80 -> Color(0xFF3B185F)
                                    healthScore >= 50 -> Color(0xFF452205)
                                    else -> Color(0xFF4C0519)
                                },
                                border = BorderStroke(1.dp, when {
                                    healthScore >= 80 -> Color(0xFFA855F7)
                                    healthScore >= 50 -> Color(0xFFF59E0B)
                                    else -> Color(0xFFF43F5E)
                                })
                            ) {
                                Text(
                                    "$healthScore%",
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                                    fontWeight = FontWeight.ExtraBold,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = Color.White
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        LinearProgressIndicator(
                            progress = { healthScore / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            trackColor = Color(0xFF261D3D),
                            color = when {
                                healthScore >= 80 -> Color(0xFFA855F7)
                                healthScore >= 50 -> Color(0xFFF59E0B)
                                else -> Color(0xFFF43F5E)
                            }
                        )

                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "🔒 ${lockedApps.count { it.isLocked }} Apps Locked",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFC4B8DE)
                            )
                            Text(
                                "📸 $intruderCount Intruders",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFC4B8DE)
                            )
                            Text(
                                "🗄️ $vaultCount Vault Files",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFC4B8DE)
                            )
                        }
                    }
                }
            }

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                placeholder = { Text("Search installed apps...", color = Color(0xFF8B7FA8)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = Color(0xFFA855F7)) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear", tint = Color(0xFFC4B8DE))
                        }
                    }
                },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFF130E20),
                    unfocusedContainerColor = Color(0xFF130E20),
                    focusedBorderColor = Color(0xFFA855F7),
                    unfocusedBorderColor = Color(0xFF2E2048),
                    cursorColor = Color(0xFFA855F7)
                ),
                shape = RoundedCornerShape(20.dp)
            )

            // Category Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "All" to "All (${installedApps.size})",
                    "Locked" to "Locked (${lockedApps.count { it.isLocked }})",
                    "Social" to "Social & Chat",
                    "Finance" to "Finance & Pay",
                    "System" to "System"
                ).forEach { (catId, label) ->
                    FilterChip(
                        selected = selectedCategory == catId,
                        onClick = { selectedCategory = catId },
                        label = { Text(label) },
                        leadingIcon = if (selectedCategory == catId) {
                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        } else null
                    )
                }
            }

            // Batch Action Buttons when specific category is active
            if (selectedCategory != "All" && selectedCategory != "Locked" && filteredApps.isNotEmpty()) {
                val allCategoryLocked = filteredApps.all { app -> lockedApps.any { it.packageName == app.first && it.isLocked } }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${filteredApps.size} apps found",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = {
                            coroutineScope.launch(Dispatchers.IO) {
                                if (allCategoryLocked) {
                                    filteredApps.forEach { repository.unlockApp(it.first) }
                                } else {
                                    filteredApps.forEach { repository.lockApp(it.first, it.second) }
                                }
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Text(if (allCategoryLocked) "Unlock All" else "Lock All ${selectedCategory}")
                    }
                }
            }

            // Apps List
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                items(filteredApps, key = { it.first }) { (pkg, label, _) ->
                    val isLocked = lockedApps.any { it.packageName == pkg && it.isLocked }
                    val isHidden = hiddenApps.contains(pkg)

                    ListItem(
                        leadingContent = { AppIcon(pkg, pm) },
                        headlineContent = { Text(label, fontWeight = FontWeight.Medium) },
                        supportingContent = {
                            Column {
                                Text(pkg, style = MaterialTheme.typography.bodySmall)
                                if (isHidden) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "HIDDEN (Shows fake crash)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        },
                        trailingContent = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                IconButton(
                                    onClick = {
                                        val updated = if (isHidden) hiddenApps - pkg else hiddenApps + pkg
                                        hiddenApps = updated
                                        securePrefs.putString("hidden_apps", updated.joinToString(","))
                                    }
                                ) {
                                    Icon(
                                        imageVector = if (isHidden) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = if (isHidden) "Unhide App" else "Hide App",
                                        tint = if (isHidden) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = isLocked,
                                    onCheckedChange = { checked ->
                                        coroutineScope.launch(Dispatchers.IO) {
                                            if (checked) {
                                                repository.lockApp(pkg, label)
                                            } else {
                                                repository.unlockApp(pkg)
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}
