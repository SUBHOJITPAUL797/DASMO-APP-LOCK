package com.example.presentation.screens.settings

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.model.PinValidator
import com.example.receiver.DasmoDeviceAdminReceiver
import com.example.service.AppLockService
import com.example.ui.components.Icon3DBadge
import com.example.ui.components.Tactile3DCard
import com.example.ui.theme.*
import com.example.util.MasterKeyManager
import com.example.util.SecurePreferences
import com.example.util.update.AppUpdateManager
import com.example.util.update.UpdateStatus

private fun SettingsTab.icon(): ImageVector = when (this) {
    SettingsTab.SECURITY -> Icons.Default.Key
    SettingsTab.DECOY -> Icons.Default.VerifiedUser
    SettingsTab.DISGUISE -> Icons.Default.Calculate
    SettingsTab.AUTO_LOCK -> Icons.Default.Timer
    SettingsTab.UPDATES -> Icons.Default.SystemUpdate
}

private fun SettingsTab.accentColors(): Pair<Color, Color> = when (this) {
    SettingsTab.SECURITY -> ElectricPurple to DeepViolet
    SettingsTab.DECOY -> CyberCyan to Color(0xFF1D4ED8)
    SettingsTab.DISGUISE -> VividAmethyst to Color(0xFF4C1D95)
    SettingsTab.AUTO_LOCK -> NeonEmerald to Color(0xFF047857)
    SettingsTab.UPDATES -> Color(0xFFEC4899) to Color(0xFF9D174D)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    context: Context,
    onBack: () -> Unit,
    onSaved: () -> Unit = onBack,
    onSuppressNextRelock: () -> Unit = {}
) {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val securePrefs = remember { SecurePreferences.getInstance(context) }
    val savedSettings by securePrefs.settingsFlow.collectAsStateWithLifecycle()
    val savedQuestion = remember { securePrefs.getSecurityQuestion() }

    val dpm = remember { context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager }
    val adminComponent = remember { ComponentName(context, DasmoDeviceAdminReceiver::class.java) }

    fun queryAdminActive(): Boolean = try {
        dpm?.isAdminActive(adminComponent) == true
    } catch (_: Exception) {
        false
    }

    var isDeviceAdminActive by remember { mutableStateOf(queryAdminActive()) }

    var draft by remember {
        val initialSettings = securePrefs.currentSettings()
        mutableStateOf(
            SettingsDraftState.fromSettings(
                settings = if (isDeviceAdminActive && !initialSettings.uninstallProtectionEnabled) {
                    initialSettings.copy(uninstallProtectionEnabled = true)
                } else {
                    initialSettings
                },
                savedQuestion = savedQuestion
            )
        )
    }

    LaunchedEffect(isDeviceAdminActive) {
        if (isDeviceAdminActive && !securePrefs.currentSettings().uninstallProtectionEnabled) {
            securePrefs.updateSettings { it.copy(uninstallProtectionEnabled = true) }
            draft = draft.copy(uninstallProtectionEnabled = true)
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isDeviceAdminActive = queryAdminActive()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val deviceAdminLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        isDeviceAdminActive = queryAdminActive()
    }

    var modalState by remember { mutableStateOf<SettingsModalState>(SettingsModalState.None) }

    val updateManager = remember { AppUpdateManager.getInstance() }
    var updateStatus by remember { mutableStateOf<UpdateStatus>(UpdateStatus.Idle) }

    val hasUnsavedChanges = remember(draft, savedSettings) {
        draft.hasUnsavedChanges(savedSettings)
    }

    fun launchDeviceAdminActivation() {
        onSuppressNextRelock()
        AppLockService.markAppUnlocked("com.android.settings")
        try {
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                putExtra(
                    DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    "Activate DASMO Uninstall Protection to prevent intruders from removing the app without your Master PIN."
                )
            }
            deviceAdminLauncher.launch(intent)
        } catch (_: Exception) {
            // Safe fallback if system Device Admin activity is unavailable
        }
    }

    fun attemptExit() {
        if (modalState != SettingsModalState.None) {
            modalState = SettingsModalState.None
        } else if (hasUnsavedChanges) {
            modalState = SettingsModalState.ConfirmDiscardChanges
        } else {
            onBack()
        }
    }

    BackHandler {
        attemptExit()
    }

    when (val modal = modalState) {
        SettingsModalState.None -> Unit

        SettingsModalState.ConfirmDiscardChanges -> {
            AlertDialog(
                onDismissRequest = { modalState = SettingsModalState.None },
                containerColor = ObsidianSurface,
                title = { Text("Discard Unsaved Changes?", fontWeight = FontWeight.Bold, color = PureWhite) },
                text = {
                    Text(
                        "You have modified security settings that have not been applied yet.",
                        color = TextSecondary
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            modalState = SettingsModalState.None
                            onBack()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CrimsonAlert)
                    ) {
                        Text("Discard")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { modalState = SettingsModalState.None }) {
                        Text("Keep Editing")
                    }
                }
            )
        }

        is SettingsModalState.ConfirmDisableUninstallProtection -> {
            val activeUnlockMethod = securePrefs.currentSettings().unlockMethod
            val showPatternChallenge = activeUnlockMethod == com.example.domain.model.UnlockMethod.PATTERN &&
                com.example.domain.model.PatternValidator.isValidEncodedPattern(securePrefs.currentSettings().masterPattern) &&
                !modal.useRecoveryInput

            AlertDialog(
                onDismissRequest = { modalState = SettingsModalState.None },
                containerColor = ObsidianSurface,
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.SecurityUpdateWarning,
                            contentDescription = null,
                            tint = CrimsonAlert
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Deactivate Uninstall Protection",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = PureWhite
                        )
                    }
                },
                text = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (showPatternChallenge) {
                            Text(
                                "Draw your unlock Pattern to turn off uninstall protection:",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            com.example.ui.components.PatternLockView(
                                isError = modal.error != null,
                                resetKey = modal.error,
                                testTag = "disable_uninstall_pattern_pad",
                                modifier = Modifier.fillMaxWidth(0.85f),
                                onPatternStart = {
                                    if (modal.error != null) modalState = modal.copy(error = null)
                                },
                                onPatternComplete = { nodes ->
                                    val current = securePrefs.currentSettings()
                                    if (PinValidator.verifyAppUnlockPattern(nodes, current)) {
                                        try {
                                            dpm?.removeActiveAdmin(adminComponent)
                                        } catch (_: Exception) {
                                        }
                                        isDeviceAdminActive = false
                                        draft = draft.copy(uninstallProtectionEnabled = false)
                                        securePrefs.updateSettings { it.copy(uninstallProtectionEnabled = false) }
                                        modalState = SettingsModalState.None
                                    } else {
                                        modalState = modal.copy(error = "Incorrect Pattern!")
                                    }
                                }
                            )
                            TextButton(
                                onClick = {
                                    modalState = modal.copy(useRecoveryInput = true, error = null)
                                }
                            ) {
                                Text("Use Master PIN or Recovery Key instead", color = NeonViolet)
                            }
                        } else {
                            Text(
                                "Enter your Master PIN, Password, or Master Recovery Key to turn off uninstall protection:",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            OutlinedTextField(
                                value = modal.pinOrMasterKey,
                                onValueChange = {
                                    modalState = modal.copy(pinOrMasterKey = it, error = null)
                                },
                                label = { Text("Credential or Recovery Key") },
                                isError = modal.error != null,
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("disable_uninstall_pin_input")
                            )
                            if (activeUnlockMethod == com.example.domain.model.UnlockMethod.PATTERN &&
                                com.example.domain.model.PatternValidator.isValidEncodedPattern(securePrefs.currentSettings().masterPattern)
                            ) {
                                TextButton(
                                    onClick = {
                                        modalState = modal.copy(useRecoveryInput = false, error = null)
                                    },
                                    modifier = Modifier.align(Alignment.End)
                                ) {
                                    Text("Draw Pattern instead", color = NeonViolet)
                                }
                            }
                        }
                        if (modal.error != null) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = modal.error,
                                color = CrimsonAlert,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.testTag("disable_uninstall_error")
                            )
                        }
                    }
                },
                confirmButton = {
                    if (!showPatternChallenge) {
                        Button(
                            colors = ButtonDefaults.buttonColors(containerColor = CrimsonAlert),
                            onClick = {
                                val current = securePrefs.currentSettings()
                                val validPin = PinValidator.verifyAppUnlockPin(modal.pinOrMasterKey, current)
                                val validPassword = PinValidator.verifyAppUnlockPassword(modal.pinOrMasterKey, current)
                                val validMasterKey = MasterKeyManager.isMasterKey(modal.pinOrMasterKey)
                                if (validPin || validPassword || validMasterKey) {
                                    try {
                                        dpm?.removeActiveAdmin(adminComponent)
                                    } catch (_: Exception) {
                                        // Safe fallback
                                    }
                                    isDeviceAdminActive = false
                                    draft = draft.copy(uninstallProtectionEnabled = false)
                                    securePrefs.updateSettings { it.copy(uninstallProtectionEnabled = false) }
                                    modalState = SettingsModalState.None
                                } else {
                                    modalState = modal.copy(error = "Incorrect credential or Master Key!")
                                }
                            },
                            modifier = Modifier.testTag("disable_uninstall_confirm_btn")
                        ) {
                            Text("Verify & Deactivate")
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { modalState = SettingsModalState.None }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }

    Scaffold(
        containerColor = ObsidianBlack,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ObsidianBlack,
                    titleContentColor = PureWhite
                ),
                title = {
                    Column {
                        Text(
                            "Security Control Center",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            if (hasUnsavedChanges) "Unsaved changes ready to apply" else "All protections synced",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (hasUnsavedChanges) NeonViolet else CyberCyan
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = { attemptExit() },
                        modifier = Modifier.testTag("settings_back_btn")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                color = ObsidianSurface,
                tonalElevation = 8.dp,
                border = BorderStroke(1.dp, ObsidianBorder.copy(alpha = 0.7f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onBack()
                        },
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, ObsidianBorder),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("settings_cancel_button")
                    ) {
                        Text("Cancel", color = TextSecondary, fontWeight = FontWeight.SemiBold)
                    }

                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            when (val result = SettingsValidator.validateAndBuild(draft, securePrefs.currentSettings())) {
                                is SettingsSaveResult.Invalid -> {
                                    draft = result.nextDraft
                                }
                                is SettingsSaveResult.Valid -> {
                                    securePrefs.updateSettings { result.updatedSettings }
                                    result.recoveryUpdate?.let { (q, a) ->
                                        securePrefs.setSecurityRecovery(q, a)
                                    }
                                    if (result.updatedSettings.autoLockTimeoutMillis == 0L) {
                                        AppLockService.clearUnlockedSessions()
                                    }
                                    onSaved()
                                }
                            }
                        },
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ElectricPurple,
                            contentColor = PureWhite
                        ),
                        modifier = Modifier
                            .weight(1.4f)
                            .height(50.dp)
                            .testTag("settings_save_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Save & Apply", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SettingsTab.entries.forEach { tab ->
                    val isSelected = draft.activeTab == tab
                    val (accentStart, accentEnd) = tab.accentColors()
                    Tactile3DCard(
                        highlighted = isSelected,
                        shape = RoundedCornerShape(16.dp),
                        onClick = { draft = draft.copy(activeTab = tab) },
                        modifier = Modifier.testTag("settings_tab_${tab.name.lowercase()}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon3DBadge(
                                icon = tab.icon(),
                                contentDescription = null,
                                size = 30.dp,
                                accentStart = if (isSelected) accentStart else Color(0xFF342457),
                                accentEnd = if (isSelected) accentEnd else Color(0xFF1A122E)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = tab.label,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) PureWhite else TextSecondary
                            )
                        }
                    }
                }
            }

            AnimatedContent(
                targetState = draft.activeTab,
                transitionSpec = {
                    val forward = targetState.ordinal >= initialState.ordinal
                    val enterOffset = if (forward) 180 else -180
                    val exitOffset = if (forward) -180 else 180
                    (slideInHorizontally(animationSpec = tween(260)) { enterOffset } + fadeIn(tween(220)))
                        .togetherWith(slideOutHorizontally(animationSpec = tween(220)) { exitOffset } + fadeOut(tween(180)))
                },
                label = "settings_category_transition",
                modifier = Modifier.weight(1f)
            ) { currentTab ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    when (currentTab) {
                        SettingsTab.SECURITY -> SecurityCategoryContent(
                            draft = draft,
                            hasSavedRecoveryQuestion = savedQuestion.isNotBlank(),
                            isDeviceAdminActive = isDeviceAdminActive,
                            onDraftChange = { draft = it },
                            onEnableUninstallProtection = {
                                draft = draft.copy(uninstallProtectionEnabled = true)
                                securePrefs.updateSettings { it.copy(uninstallProtectionEnabled = true) }
                                launchDeviceAdminActivation()
                            },
                            onRequestDisableUninstallProtection = {
                                modalState = SettingsModalState.ConfirmDisableUninstallProtection()
                            },
                            onGrantDeviceAdmin = { launchDeviceAdminActivation() }
                        )

                        SettingsTab.DECOY -> DecoyCategoryContent(
                            draft = draft,
                            onDraftChange = { draft = it }
                        )

                        SettingsTab.DISGUISE -> DisguiseCategoryContent(
                            draft = draft,
                            onDraftChange = { draft = it }
                        )

                        SettingsTab.AUTO_LOCK -> AutoLockCategoryContent(
                            draft = draft,
                            onDraftChange = { draft = it }
                        )

                        SettingsTab.UPDATES -> UpdatesCategoryContent(
                            context = context,
                            scope = scope,
                            updateManager = updateManager,
                            updateStatus = updateStatus,
                            onUpdateStatusChange = { updateStatus = it },
                            onSuppressNextRelock = onSuppressNextRelock
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}
