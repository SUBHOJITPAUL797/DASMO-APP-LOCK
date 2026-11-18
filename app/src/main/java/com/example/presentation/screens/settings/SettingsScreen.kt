package com.example.presentation.screens.settings

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.domain.model.AutoLockPolicy
import com.example.domain.model.TimeUnitOption
import com.example.service.AppLockService
import com.example.ui.components.Icon3DBadge
import com.example.ui.components.Section3DHeader
import com.example.ui.components.Tactile3DCard
import com.example.ui.components.TactileSettingSwitchCard
import com.example.ui.theme.*
import com.example.util.SecurePreferences
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun SettingsTab.icon(): ImageVector = when (this) {
    SettingsTab.SECURITY -> Icons.Default.Key
    SettingsTab.DECOY -> Icons.Default.VerifiedUser
    SettingsTab.DISGUISE -> Icons.Default.Calculate
    SettingsTab.AUTO_LOCK -> Icons.Default.Timer
}

private fun SettingsTab.accentColors(): Pair<Color, Color> = when (this) {
    SettingsTab.SECURITY -> ElectricPurple to DeepViolet
    SettingsTab.DECOY -> CyberCyan to Color(0xFF1D4ED8)
    SettingsTab.DISGUISE -> VividAmethyst to Color(0xFF4C1D95)
    SettingsTab.AUTO_LOCK -> NeonEmerald to Color(0xFF047857)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    context: Context,
    onBack: () -> Unit,
    onSaved: () -> Unit = onBack
) {
    val haptic = LocalHapticFeedback.current
    val securePrefs = remember { SecurePreferences.getInstance(context) }
    val savedSettings by securePrefs.settingsFlow.collectAsStateWithLifecycle()
    var draft by remember { mutableStateOf(SettingsDraftState.fromSettings(securePrefs.currentSettings())) }
    var showDiscardDialog by remember { mutableStateOf(false) }

    val hasUnsavedChanges = remember(draft, savedSettings) {
        draft.hasUnsavedChanges(savedSettings)
    }

    fun attemptExit() {
        if (hasUnsavedChanges) {
            showDiscardDialog = true
        } else {
            onBack()
        }
    }

    BackHandler {
        attemptExit()
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
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
                        showDiscardDialog = false
                        onBack()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonAlert)
                ) {
                    Text("Discard")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text("Keep Editing")
                }
            }
        )
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
                                    if (result.updatedSettings.autoLockTimeoutMillis == 0L) {
                                        AppLockService.unlockedApps.clear()
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
            // Category Navigation Pills with 3D Badges & Smooth Selection
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

            // Animated Category Body Transition
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
                            onDraftChange = { draft = it }
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
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun SecurityCategoryContent(
    draft: SettingsDraftState,
    onDraftChange: (SettingsDraftState) -> Unit
) {
    var showPinText by remember { mutableStateOf(false) }
    val liveTimePinExample = remember {
        SimpleDateFormat("HHmm", Locale.US).format(Date())
    }

    // 3D Hero Card with img_shield_3d
    Tactile3DCard(highlighted = true, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        listOf(Color(0xFF281448), Color(0xFF130D22))
                    )
                )
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_shield_3d),
                contentDescription = "3D Security Shield",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(16.dp))
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Access & Active Defense",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = PureWhite
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Configure your 4-digit Master PIN and real-time anti-intruder shields.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }
    }

    Section3DHeader(
        icon = Icons.Default.Pin,
        title = "Master PIN Credentials",
        subtitle = "Required to unlock apps, Vault & Calculator disguise"
    )

    Tactile3DCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (i in 0 until 4) {
                        val filled = i < draft.newPin.length
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .background(
                                    color = if (filled) NeonViolet else ObsidianSurfaceVariant,
                                    shape = CircleShape
                                )
                        )
                    }
                }
                IconButton(
                    onClick = { showPinText = !showPinText },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (showPinText) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (showPinText) "Hide PIN" else "Show PIN",
                        tint = NeonViolet
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            val visual = if (showPinText) VisualTransformation.None else PasswordVisualTransformation()
            OutlinedTextField(
                value = draft.newPin,
                onValueChange = { raw ->
                    val filtered = raw.filter { it.isDigit() }.take(4)
                    onDraftChange(draft.copy(newPin = filtered, pinError = null))
                },
                label = { Text("New PIN (4 digits)") },
                placeholder = { Text("Leave blank to keep current") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                visualTransformation = visual,
                isError = draft.pinError != null,
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settings_new_pin_input")
            )

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = draft.confirmPin,
                onValueChange = { raw ->
                    val filtered = raw.filter { it.isDigit() }.take(4)
                    onDraftChange(draft.copy(confirmPin = filtered, pinError = null))
                },
                label = { Text("Confirm New PIN") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                visualTransformation = visual,
                isError = draft.pinError != null,
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settings_confirm_pin_input")
            )

            AnimatedVisibility(visible = draft.pinError != null) {
                Text(
                    text = draft.pinError.orEmpty(),
                    color = CrimsonAlert,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .testTag("settings_pin_error")
                )
            }
        }
    }

    Section3DHeader(
        icon = Icons.Default.Shield,
        title = "Active Defense Modules",
        subtitle = "Intelligent lockscreen countermeasures"
    )

    TactileSettingSwitchCard(
        icon = Icons.Default.Schedule,
        title = "Dynamic Time PIN",
        description = "Accepts current clock time (right now: $liveTimePinExample). Shoulder surfers cannot reuse an old PIN.",
        checked = draft.dynamicTimePinEnabled,
        badgeText = if (draft.dynamicTimePinEnabled) "Now: $liveTimePinExample" else null,
        accentStart = ElectricPurple,
        accentEnd = DeepViolet,
        testTag = "switch_dynamic_time_pin",
        onCheckedChange = { onDraftChange(draft.copy(dynamicTimePinEnabled = it)) }
    )

    TactileSettingSwitchCard(
        icon = Icons.Default.Dialpad,
        title = "Anti-Peep Scrambled Keypad",
        description = "Shuffles digits (0–9) into a fresh random layout each time an app lock opens.",
        checked = draft.scrambleKeypadEnabled,
        accentStart = CyberCyan,
        accentEnd = Color(0xFF1D4ED8),
        testTag = "switch_scramble_keypad",
        onCheckedChange = { onDraftChange(draft.copy(scrambleKeypadEnabled = it)) }
    )

    TactileSettingSwitchCard(
        icon = Icons.Default.NotificationsActive,
        title = "Break-In Siren Alarm",
        description = "Triggers a loud alarm tone & tactile vibration alert after 3 consecutive failed unlock attempts.",
        checked = draft.breakInSirenEnabled,
        accentStart = CrimsonAlert,
        accentEnd = Color(0xFF9F1239),
        testTag = "switch_break_in_siren",
        onCheckedChange = { onDraftChange(draft.copy(breakInSirenEnabled = it)) }
    )

    TactileSettingSwitchCard(
        icon = Icons.Default.AdminPanelSettings,
        title = "Uninstall Protection",
        description = "Locks Android Package Installer screens with your Master PIN so unauthorized users cannot uninstall apps.",
        checked = draft.uninstallProtectionEnabled,
        accentStart = NeonEmerald,
        accentEnd = Color(0xFF047857),
        testTag = "switch_uninstall_protection",
        onCheckedChange = { onDraftChange(draft.copy(uninstallProtectionEnabled = it)) }
    )
}

@Composable
private fun DecoyCategoryContent(
    draft: SettingsDraftState,
    onDraftChange: (SettingsDraftState) -> Unit
) {
    var showDecoyText by remember { mutableStateOf(false) }

    Tactile3DCard(highlighted = draft.decoyPinInput.length == 4, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        listOf(Color(0xFF102A43), Color(0xFF130D22))
                    )
                )
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_vault_3d),
                contentDescription = "3D Encrypted Vault",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(16.dp))
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Decoy / Panic Vault Mode",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = PureWhite
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "If coerced, unlock the Vault using this PIN to show harmless decoy notes while real encrypted files stay invisible.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }
    }

    Section3DHeader(
        icon = Icons.Default.VerifiedUser,
        title = "Decoy PIN Setup",
        subtitle = "Must differ from your Master PIN",
        accentStart = CyberCyan,
        accentEnd = Color(0xFF1D4ED8)
    )

    Tactile3DCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = if (draft.decoyPinInput.length == 4) CyberCyan.copy(alpha = 0.2f) else ObsidianSurfaceVariant,
                    border = BorderStroke(
                        1.dp,
                        if (draft.decoyPinInput.length == 4) CyberCyan else ObsidianBorder
                    )
                ) {
                    Text(
                        text = if (draft.decoyPinInput.length == 4) "Decoy PIN Configured" else "Decoy Disabled",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (draft.decoyPinInput.length == 4) CyberCyan else TextSecondary,
                        fontWeight = FontWeight.Bold
                    )
                }

                IconButton(
                    onClick = { showDecoyText = !showDecoyText },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (showDecoyText) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (showDecoyText) "Hide Decoy PIN" else "Show Decoy PIN",
                        tint = CyberCyan
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = draft.decoyPinInput,
                onValueChange = { raw ->
                    val filtered = raw.filter { it.isDigit() }.take(4)
                    onDraftChange(draft.copy(decoyPinInput = filtered, decoyPinError = null))
                },
                label = { Text("Decoy PIN (4 digits)") },
                placeholder = { Text("e.g. 0000") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                visualTransformation = if (showDecoyText) VisualTransformation.None else PasswordVisualTransformation(),
                isError = draft.decoyPinError != null,
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settings_decoy_pin_input")
            )

            AnimatedVisibility(visible = draft.decoyPinError != null) {
                Text(
                    text = draft.decoyPinError.orEmpty(),
                    color = CrimsonAlert,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .testTag("settings_decoy_error")
                )
            }

            if (draft.decoyPinInput.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                TextButton(
                    onClick = {
                        onDraftChange(draft.copy(decoyPinInput = "", decoyPinError = null))
                    },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Clear & Disable Decoy Mode", color = CrimsonAlert)
                }
            } else {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Leave blank to keep Decoy Mode disabled.",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary
                )
            }
        }
    }
}

@Composable
private fun DisguiseCategoryContent(
    draft: SettingsDraftState,
    onDraftChange: (SettingsDraftState) -> Unit
) {
    Section3DHeader(
        icon = Icons.Default.Calculate,
        title = "Stealth Camouflage",
        subtitle = "Hide DASMO LOCK behind a working utility interface",
        accentStart = VividAmethyst,
        accentEnd = Color(0xFF4C1D95)
    )

    TactileSettingSwitchCard(
        icon = Icons.Default.Calculate,
        title = "Calculator Disguise",
        description = "Displays a working purple-dark calculator when opening DASMO LOCK. Type your Master PIN and press '=' to enter.",
        checked = draft.calculatorDisguiseEnabled,
        badgeText = if (draft.calculatorDisguiseEnabled) "Stealth On" else null,
        accentStart = VividAmethyst,
        accentEnd = Color(0xFF4C1D95),
        testTag = "switch_calculator_disguise",
        onCheckedChange = { onDraftChange(draft.copy(calculatorDisguiseEnabled = it)) }
    )

    Tactile3DCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon3DBadge(
                    icon = Icons.Default.Info,
                    contentDescription = null,
                    size = 34.dp,
                    accentStart = ElectricPurple,
                    accentEnd = DeepViolet
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    "How to Unlock Calculator Camouflage",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = PureWhite
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                "1. Anyone opening the app sees a genuine calculator with arithmetic, decimals, percentages & sign toggle.\n" +
                    "2. Type your 4-digit Master PIN (or Dynamic Time PIN if enabled) on a clean display and press '='.\n" +
                    "3. No secret long-press shortcuts or hints are shown to intruders.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun AutoLockCategoryContent(
    draft: SettingsDraftState,
    onDraftChange: (SettingsDraftState) -> Unit
) {
    val haptic = LocalHapticFeedback.current

    Section3DHeader(
        icon = Icons.Default.Timer,
        title = "Re-Lock Timing",
        subtitle = "Screen-off always re-locks immediately for safety",
        accentStart = NeonEmerald,
        accentEnd = Color(0xFF047857)
    )

    AutoLockPolicy.PRESET_TIMEOUTS_MILLIS.chunked(2).forEach { rowPresets ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            rowPresets.forEach { (millis, label) ->
                val isSelected = draft.autoLockSelection is AutoLockDraftSelection.Preset &&
                    draft.autoLockSelection.timeoutMillis == millis
                Tactile3DCard(
                    highlighted = isSelected,
                    shape = RoundedCornerShape(16.dp),
                    onClick = {
                        onDraftChange(
                            draft.copy(
                                autoLockSelection = AutoLockDraftSelection.Preset(millis),
                                customTimeoutError = null
                            )
                        )
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) PureWhite else TextSecondary,
                            modifier = Modifier.weight(1f)
                        )
                        RadioButton(
                            selected = isSelected,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onDraftChange(
                                    draft.copy(
                                        autoLockSelection = AutoLockDraftSelection.Preset(millis),
                                        customTimeoutError = null
                                    )
                                )
                            }
                        )
                    }
                }
            }
        }
    }

    val customSelection = draft.autoLockSelection as? AutoLockDraftSelection.Custom
    val isCustomSelected = customSelection != null

    Tactile3DCard(
        highlighted = isCustomSelected,
        shape = RoundedCornerShape(18.dp),
        onClick = {
            if (!isCustomSelected) {
                onDraftChange(
                    draft.copy(
                        autoLockSelection = AutoLockDraftSelection.Custom(
                            numberInput = "45",
                            unit = TimeUnitOption.SECONDS
                        ),
                        customTimeoutError = null
                    )
                )
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .testTag("autolock_custom_card")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon3DBadge(
                        icon = Icons.Default.Tune,
                        contentDescription = null,
                        size = 36.dp,
                        accentStart = if (isCustomSelected) NeonEmerald else Color(0xFF3E2C66),
                        accentEnd = if (isCustomSelected) Color(0xFF047857) else Color(0xFF1F1635)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            "Custom Duration",
                            fontWeight = if (isCustomSelected) FontWeight.Bold else FontWeight.SemiBold,
                            color = PureWhite
                        )
                        Text(
                            "Set exact seconds or minutes",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextTertiary
                        )
                    }
                }
                RadioButton(
                    selected = isCustomSelected,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        if (!isCustomSelected) {
                            onDraftChange(
                                draft.copy(
                                    autoLockSelection = AutoLockDraftSelection.Custom(
                                        numberInput = "45",
                                        unit = TimeUnitOption.SECONDS
                                    ),
                                    customTimeoutError = null
                                )
                            )
                        }
                    }
                )
            }

            AnimatedVisibility(
                visible = customSelection != null,
                enter = expandVertically(spring(dampingRatio = Spring.DampingRatioLowBouncy)) + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                if (customSelection != null) {
                    Column(modifier = Modifier.padding(top = 12.dp)) {
                        OutlinedTextField(
                            value = customSelection.numberInput,
                            onValueChange = { raw ->
                                val digits = raw.filter { it.isDigit() }.take(5)
                                onDraftChange(
                                    draft.copy(
                                        autoLockSelection = customSelection.copy(numberInput = digits),
                                        customTimeoutError = null
                                    )
                                )
                            },
                            label = { Text("Duration (${customSelection.unit.label})") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            isError = draft.customTimeoutError != null,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("autolock_custom_input")
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            TimeUnitOption.entries.forEach { unit ->
                                FilterChip(
                                    selected = customSelection.unit == unit,
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onDraftChange(
                                            draft.copy(
                                                autoLockSelection = customSelection.copy(unit = unit),
                                                customTimeoutError = null
                                            )
                                        )
                                    },
                                    label = { Text(unit.label) },
                                    modifier = Modifier.testTag("autolock_unit_${unit.name.lowercase()}")
                                )
                            }
                        }
                        if (draft.customTimeoutError != null) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = draft.customTimeoutError,
                                color = CrimsonAlert,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.testTag("autolock_custom_error")
                            )
                        }
                    }
                }
            }
        }
    }

    Section3DHeader(
        icon = Icons.Default.PhonelinkLock,
        title = "Screen & Recents Privacy",
        subtitle = "Protect sensitive UI from capture",
        accentStart = ElectricPurple,
        accentEnd = DeepViolet
    )

    TactileSettingSwitchCard(
        icon = Icons.Default.ScreenshotMonitor,
        title = "Prevent Screenshots & Blur Recents",
        description = "Applies FLAG_SECURE to black out app previews in Android Recents and block screen recording.",
        checked = draft.preventScreenshots,
        accentStart = ElectricPurple,
        accentEnd = DeepViolet,
        testTag = "switch_prevent_screenshots",
        onCheckedChange = { onDraftChange(draft.copy(preventScreenshots = it)) }
    )
}
