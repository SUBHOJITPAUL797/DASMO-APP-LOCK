package com.example.presentation.screens.home

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.db.LockedAppEntity
import com.example.domain.model.SecuritySettings
import com.example.domain.repository.AppLockRepository
import com.example.presentation.screens.vault.VaultStorage
import com.example.service.AppLockService
import com.example.ui.components.Icon3DBadge
import com.example.ui.components.Tactile3DCard
import com.example.ui.theme.*
import com.example.util.SecurePreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class AppCategory(val chipLabel: String) {
    ALL("All"),
    LOCKED("Locked"),
    SOCIAL("Social & Chat"),
    FINANCE("Finance & Pay"),
    SYSTEM("System")
}

data class InstalledAppEntry(
    val packageName: String,
    val label: String,
    val flags: Int
)

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

fun calculateSecurityHealthScore(
    lockedApps: List<LockedAppEntity>,
    settings: SecuritySettings
): Int {
    var score = 30
    val hasSettingsLocked = lockedApps.any { it.isLocked && (it.packageName.contains("settings") || it.packageName.contains("vending")) }
    if (hasSettingsLocked) score += 25
    val hasSocialLocked = lockedApps.any { it.isLocked && isSocialApp(it.packageName, it.appLabel) }
    if (hasSocialLocked) score += 15
    if (settings.scrambleKeypadEnabled) score += 10
    if (settings.dynamicTimePinEnabled) score += 10
    if (settings.preventScreenshots) score += 10
    return score.coerceIn(0, 100)
}

@Composable
fun AppIcon(packageName: String, packageManager: PackageManager) {
    var icon by remember(packageName) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(packageName) {
        icon = withContext(Dispatchers.IO) {
            try {
                val drawable = packageManager.getApplicationIcon(packageName)
                drawable.toBitmap(width = 120, height = 120).asImageBitmap()
            } catch (_: Exception) {
                null
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppGuardHomeScreen(
    repository: AppLockRepository,
    context: Context,
    onNavigateToIntruders: () -> Unit,
    onNavigateToVault: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onLockToDisguise: () -> Unit,
    onSuppressNextRelock: () -> Unit = {}
) {
    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val pm = context.packageManager
    val securePrefs = remember { SecurePreferences.getInstance(context) }
    val settings by securePrefs.settingsFlow.collectAsStateWithLifecycle()

    var installedApps by remember { mutableStateOf<List<InstalledAppEntry>>(emptyList()) }
    val lockedApps by repository.allLockedApps.collectAsStateWithLifecycle(initialValue = emptyList())
    var isUsageStatsEnabled by remember { mutableStateOf(false) }
    var isOverlayEnabled by remember { mutableStateOf(false) }

    var showSystemInAll by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(AppCategory.ALL) }

    var intruderCount by remember { mutableStateOf(0) }
    var vaultCount by remember { mutableStateOf(0) }

    fun refreshCounts() {
        coroutineScope.launch {
            val (iCount, vCount) = withContext(Dispatchers.IO) {
                val intDir = context.getDir("intruders", Context.MODE_PRIVATE)
                val intruders = intDir.listFiles()?.count { it.isFile } ?: 0
                val vault = VaultStorage.countRealVaultItems(context)
                intruders to vault
            }
            intruderCount = iCount
            vaultCount = vCount
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { _ -> }
    )

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { _ ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    )

    LaunchedEffect(Unit) {
        refreshCounts()
        installedApps = withContext(Dispatchers.IO) {
            val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            packages.mapNotNull {
                if (it.packageName == context.packageName) return@mapNotNull null
                if (pm.getLaunchIntentForPackage(it.packageName) == null &&
                    !it.packageName.contains("settings") &&
                    !it.packageName.contains("vending")
                ) return@mapNotNull null

                InstalledAppEntry(
                    packageName = it.packageName,
                    label = it.loadLabel(pm).toString(),
                    flags = it.flags
                )
            }.sortedBy { it.label.lowercase() }
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshCounts()
                val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
                val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    appOps.unsafeCheckOpNoThrow(
                        android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                        android.os.Process.myUid(),
                        context.packageName
                    )
                } else {
                    @Suppress("DEPRECATION")
                    appOps.checkOpNoThrow(
                        android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                        android.os.Process.myUid(),
                        context.packageName
                    )
                }
                isUsageStatsEnabled = mode == android.app.AppOpsManager.MODE_ALLOWED
                isOverlayEnabled = Settings.canDrawOverlays(context)

                if (isUsageStatsEnabled && isOverlayEnabled) {
                    val intent = Intent(context, AppLockService::class.java)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
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

    val lockedPackageSet = remember(lockedApps) {
        lockedApps.filter { it.isLocked }.map { it.packageName }.toSet()
    }

    val filteredApps = remember(installedApps, lockedPackageSet, searchQuery, selectedCategory, showSystemInAll) {
        installedApps.filter { app ->
            val matchesSearch = searchQuery.isBlank() ||
                app.label.contains(searchQuery, ignoreCase = true) ||
                app.packageName.contains(searchQuery, ignoreCase = true)

            val matchesCategory = when (selectedCategory) {
                AppCategory.ALL -> showSystemInAll || !isSystemApp(app.packageName, app.label, app.flags)
                AppCategory.LOCKED -> lockedPackageSet.contains(app.packageName)
                AppCategory.SOCIAL -> isSocialApp(app.packageName, app.label)
                AppCategory.FINANCE -> isFinanceApp(app.packageName, app.label)
                AppCategory.SYSTEM -> isSystemApp(app.packageName, app.label, app.flags)
            }

            matchesSearch && matchesCategory
        }
    }

    val healthScore = remember(lockedApps, settings) {
        calculateSecurityHealthScore(lockedApps, settings)
    }
    val animatedHealthProgress by animateFloatAsState(
        targetValue = healthScore / 100f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "health_progress"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ObsidianBlack,
                    titleContentColor = PureWhite
                ),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon3DBadge(
                            icon = Icons.Default.Security,
                            contentDescription = null,
                            size = 34.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("DASMO LOCK", fontWeight = FontWeight.Bold)
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onLockToDisguise()
                        },
                        modifier = Modifier.testTag("home_engage_disguise_btn")
                    ) {
                        Icon(
                            imageVector = if (settings.calculatorDisguiseEnabled) Icons.Default.Calculate else Icons.Default.ScreenLockPortrait,
                            contentDescription = if (settings.calculatorDisguiseEnabled) "Engage Disguise Lock" else "Lock App Now"
                        )
                    }
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onNavigateToVault()
                        },
                        modifier = Modifier.testTag("home_vault_btn")
                    ) {
                        BadgedBox(badge = {
                            if (vaultCount > 0) {
                                Badge { Text(vaultCount.toString()) }
                            }
                        }) {
                            Icon(Icons.Default.Lock, contentDescription = "Vault")
                        }
                    }
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onNavigateToIntruders()
                        },
                        modifier = Modifier.testTag("home_intruders_btn")
                    ) {
                        BadgedBox(badge = {
                            if (intruderCount > 0) {
                                Badge(containerColor = MaterialTheme.colorScheme.error) { Text(intruderCount.toString()) }
                            }
                        }) {
                            Icon(Icons.Default.Person, contentDescription = "Intruders")
                        }
                    }
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onNavigateToSettings()
                        },
                        modifier = Modifier.testTag("home_settings_btn")
                    ) {
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
                        Text(
                            "DASMO LOCK needs Usage Access to detect foreground apps.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = {
                            onSuppressNextRelock()
                            try {
                                context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                            } catch (_: Exception) {
                                context.startActivity(
                                    Intent(
                                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        Uri.parse("package:${context.packageName}")
                                    )
                                )
                            }
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
                        Text(
                            "Required to display the secure lockscreen over protected apps.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = {
                            onSuppressNextRelock()
                            try {
                                context.startActivity(
                                    Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:${context.packageName}")
                                    )
                                )
                            } catch (_: Exception) {
                                context.startActivity(
                                    Intent(
                                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        Uri.parse("package:${context.packageName}")
                                    )
                                )
                            }
                        }) {
                            Text("Grant Overlay Permission")
                        }
                    }
                }
            }

            Tactile3DCard(
                highlighted = healthScore >= 80,
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0xFF281448), Color(0xFF130E20))
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
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.img_shield_3d),
                                    contentDescription = "DASMO Shield",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(50.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        "Security Health",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = PureWhite
                                    )
                                    val statusText = when {
                                        healthScore >= 80 -> "Shield Active • Highly Protected"
                                        healthScore >= 50 -> "Moderate • Recommended Actions"
                                        else -> "Vulnerable • Protect Settings & Apps"
                                    }
                                    val statusColor = when {
                                        healthScore >= 80 -> NeonViolet
                                        healthScore >= 50 -> Color(0xFFFBBF24)
                                        else -> CrimsonAlert
                                    }
                                    Text(statusText, style = MaterialTheme.typography.labelSmall, color = statusColor)
                                }
                            }
                            Surface(
                                shape = CircleShape,
                                color = when {
                                    healthScore >= 80 -> ContainerPurple
                                    healthScore >= 50 -> Color(0xFF452205)
                                    else -> Color(0xFF4C0519)
                                },
                                border = BorderStroke(
                                    1.dp,
                                    when {
                                        healthScore >= 80 -> ElectricPurple
                                        healthScore >= 50 -> Color(0xFFF59E0B)
                                        else -> CrimsonAlert
                                    }
                                )
                            ) {
                                Text(
                                    "$healthScore%",
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                                    fontWeight = FontWeight.ExtraBold,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = PureWhite
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        LinearProgressIndicator(
                            progress = { animatedHealthProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            trackColor = ObsidianSurfaceVariant,
                            color = when {
                                healthScore >= 80 -> ElectricPurple
                                healthScore >= 50 -> Color(0xFFF59E0B)
                                else -> CrimsonAlert
                            }
                        )

                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "${lockedPackageSet.size} Apps Locked",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                            Text(
                                "$intruderCount Intruders",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                            Text(
                                "$vaultCount Vault Files",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .testTag("home_search_input"),
                placeholder = { Text("Search installed apps...", color = TextTertiary) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = ElectricPurple) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear", tint = TextSecondary)
                        }
                    }
                },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = ObsidianSurface,
                    unfocusedContainerColor = ObsidianSurface,
                    focusedBorderColor = ElectricPurple,
                    unfocusedBorderColor = ObsidianBorder,
                    cursorColor = ElectricPurple
                ),
                shape = RoundedCornerShape(20.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppCategory.entries.forEach { category ->
                    val label = when (category) {
                        AppCategory.ALL -> "All (${installedApps.size})"
                        AppCategory.LOCKED -> "Locked (${lockedPackageSet.size})"
                        else -> category.chipLabel
                    }
                    FilterChip(
                        selected = selectedCategory == category,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            selectedCategory = category
                        },
                        label = { Text(label) },
                        leadingIcon = if (selectedCategory == category) {
                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        } else null,
                        modifier = Modifier.testTag("category_chip_${category.name.lowercase()}")
                    )
                }

                if (selectedCategory == AppCategory.ALL) {
                    FilterChip(
                        selected = showSystemInAll,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            showSystemInAll = !showSystemInAll
                        },
                        label = { Text(if (showSystemInAll) "System: Shown" else "System: Hidden") },
                        modifier = Modifier.testTag("toggle_system_apps_chip")
                    )
                }
            }

            if (selectedCategory != AppCategory.ALL &&
                selectedCategory != AppCategory.LOCKED &&
                filteredApps.isNotEmpty()
            ) {
                val allCategoryLocked = filteredApps.all { lockedPackageSet.contains(it.packageName) }
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
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            coroutineScope.launch(Dispatchers.IO) {
                                if (allCategoryLocked) {
                                    filteredApps.forEach { repository.unlockApp(it.packageName) }
                                } else {
                                    filteredApps.forEach { repository.lockApp(it.packageName, it.label) }
                                }
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier
                            .height(34.dp)
                            .testTag("category_batch_lock_btn")
                    ) {
                        Text(if (allCategoryLocked) "Unlock All" else "Lock All ${selectedCategory.chipLabel}")
                    }
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                items(filteredApps, key = { it.packageName }) { app ->
                    val isLocked = lockedPackageSet.contains(app.packageName)
                    val isHidden = settings.hiddenApps.contains(app.packageName)

                    ListItem(
                        leadingContent = { AppIcon(app.packageName, pm) },
                        headlineContent = { Text(app.label, fontWeight = FontWeight.Medium) },
                        supportingContent = {
                            Column {
                                Text(app.packageName, style = MaterialTheme.typography.bodySmall)
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
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        securePrefs.toggleHiddenApp(app.packageName)
                                    },
                                    modifier = Modifier.testTag("hide_app_btn_${app.packageName}")
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
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        coroutineScope.launch(Dispatchers.IO) {
                                            if (checked) {
                                                repository.lockApp(app.packageName, app.label)
                                            } else {
                                                repository.unlockApp(app.packageName)
                                            }
                                        }
                                    },
                                    modifier = Modifier.testTag("lock_switch_${app.packageName}")
                                )
                            }
                        }
                    )
                    HorizontalDivider(color = ObsidianBorder.copy(alpha = 0.45f))
                }
            }
        }
    }
}
