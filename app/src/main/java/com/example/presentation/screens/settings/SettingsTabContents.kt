package com.example.presentation.screens.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import com.example.R
import com.example.domain.model.AutoLockPolicy
import com.example.domain.model.PasswordValidator
import com.example.domain.model.PatternValidator
import com.example.domain.model.TimeUnitOption
import com.example.domain.model.UnlockMethod
import com.example.ui.components.Icon3DBadge
import com.example.ui.components.PatternLockView
import com.example.ui.components.Section3DHeader
import com.example.ui.components.Tactile3DCard
import com.example.ui.components.TactileSettingSwitchCard
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SecurityCategoryContent(
    draft: SettingsDraftState,
    hasSavedRecoveryQuestion: Boolean,
    isDeviceAdminActive: Boolean,
    onDraftChange: (SettingsDraftState) -> Unit,
    onEnableUninstallProtection: () -> Unit,
    onRequestDisableUninstallProtection: () -> Unit,
    onGrantDeviceAdmin: () -> Unit
) {
    var showPinText by remember { mutableStateOf(false) }
    var showPasswordText by remember { mutableStateOf(false) }
    var isQuestionDropdownExpanded by remember { mutableStateOf(false) }
    val liveTimePinExample = remember {
        SimpleDateFormat("HHmm", Locale.US).format(Date())
    }

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
                    "Choose PIN, 3×3 Pattern, or Password unlock, configure recovery questions, and manage active shields.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }
    }

    Section3DHeader(
        icon = Icons.Default.LockOpen,
        title = "Unlock Method",
        subtitle = "Active lock mechanism for apps, entrance & Vault"
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        UnlockMethod.entries.forEach { method ->
            val selected = draft.unlockMethod == method
            val (accentStart, accentEnd) = when (method) {
                UnlockMethod.PIN -> ElectricPurple to DeepViolet
                UnlockMethod.PATTERN -> CyberCyan to Color(0xFF1D4ED8)
                UnlockMethod.PASSWORD -> VividAmethyst to Color(0xFF4C1D95)
            }
            val methodIcon = when (method) {
                UnlockMethod.PIN -> Icons.Default.Pin
                UnlockMethod.PATTERN -> Icons.Default.Pattern
                UnlockMethod.PASSWORD -> Icons.Default.Password
            }
            Tactile3DCard(
                highlighted = selected,
                shape = RoundedCornerShape(18.dp),
                onClick = {
                    onDraftChange(
                        draft.copy(
                            unlockMethod = method,
                            pinError = null,
                            patternError = null,
                            passwordError = null
                        )
                    )
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag("unlock_method_${method.name.lowercase()}")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon3DBadge(
                        icon = methodIcon,
                        contentDescription = null,
                        size = 38.dp,
                        accentStart = if (selected) accentStart else Color(0xFF342457),
                        accentEnd = if (selected) accentEnd else Color(0xFF1A122E)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = method.label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        color = if (selected) PureWhite else TextSecondary
                    )
                    Text(
                        text = method.subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) NeonViolet else TextTertiary,
                        maxLines = 1
                    )
                }
            }
        }
    }

    AnimatedContent(
        targetState = draft.unlockMethod,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "unlock_method_credential_card"
    ) { activeMethod ->
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (activeMethod) {
                UnlockMethod.PIN -> {
                    Section3DHeader(
                        icon = Icons.Default.Pin,
                        title = "Master PIN Credentials",
                        subtitle = "4-digit numeric PIN (also unlocks Calculator disguise)"
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
                                    modifier = Modifier.size(48.dp)
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
                }

                UnlockMethod.PATTERN -> {
                    Section3DHeader(
                        icon = Icons.Default.Pattern,
                        title = "3×3 Pattern Credentials",
                        subtitle = "Connect at least 4 dots on the grid",
                        accentStart = CyberCyan,
                        accentEnd = Color(0xFF1D4ED8)
                    )

                    Tactile3DCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val badgeText = when {
                                    draft.patternStep == PatternDraftStep.READY_TO_SAVE -> "New Pattern Confirmed"
                                    draft.patternStep == PatternDraftStep.AWAITING_CONFIRM -> "Confirm Pattern (Draw Again)"
                                    PatternValidator.isValidEncodedPattern(draft.savedMasterPattern) -> "Pattern Configured"
                                    else -> "Pattern Not Set"
                                }
                                val badgeColor = when {
                                    draft.patternStep == PatternDraftStep.READY_TO_SAVE -> NeonEmerald
                                    draft.patternStep == PatternDraftStep.AWAITING_CONFIRM -> NeonViolet
                                    PatternValidator.isValidEncodedPattern(draft.savedMasterPattern) -> CyberCyan
                                    else -> CrimsonAlert
                                }
                                Surface(
                                    shape = CircleShape,
                                    color = badgeColor.copy(alpha = 0.16f),
                                    border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.65f))
                                ) {
                                    Text(
                                        text = badgeText,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = badgeColor,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                    )
                                }

                                if (draft.newPattern.isNotEmpty() || draft.confirmPattern.isNotEmpty()) {
                                    TextButton(
                                        onClick = {
                                            onDraftChange(
                                                draft.copy(
                                                    newPattern = "",
                                                    confirmPattern = "",
                                                    patternStep = PatternDraftStep.IDLE_OR_DRAW_NEW,
                                                    patternError = null
                                                )
                                            )
                                        },
                                        modifier = Modifier.testTag("settings_pattern_reset_btn")
                                    ) {
                                        Text("Reset", color = CrimsonAlert)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = when (draft.patternStep) {
                                    PatternDraftStep.IDLE_OR_DRAW_NEW -> if (PatternValidator.isValidEncodedPattern(draft.savedMasterPattern)) {
                                        "Draw a new pattern to change it, or tap Save & Apply to use your saved pattern."
                                    } else {
                                        "Step 1: Draw a pattern connecting at least 4 dots."
                                    }
                                    PatternDraftStep.AWAITING_CONFIRM -> "Step 2: Draw the exact same pattern again to confirm."
                                    PatternDraftStep.READY_TO_SAVE -> "Pattern matched! Tap Save & Apply below to activate."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            PatternLockView(
                                isError = draft.patternError != null,
                                previewNodes = if (draft.patternStep == PatternDraftStep.READY_TO_SAVE) {
                                    PatternValidator.decode(draft.confirmPattern)
                                } else {
                                    null
                                },
                                resetKey = draft.patternStep to draft.patternError,
                                testTag = "settings_pattern_pad",
                                modifier = Modifier.fillMaxWidth(0.76f),
                                onPatternStart = {
                                    if (draft.patternError != null) {
                                        onDraftChange(draft.copy(patternError = null))
                                    }
                                },
                                onPatternComplete = { nodes ->
                                    val encoded = PatternValidator.encode(nodes)
                                    when (draft.patternStep) {
                                        PatternDraftStep.IDLE_OR_DRAW_NEW, PatternDraftStep.READY_TO_SAVE -> {
                                            if (!PatternValidator.isValidPattern(nodes)) {
                                                onDraftChange(
                                                    draft.copy(
                                                        newPattern = "",
                                                        confirmPattern = "",
                                                        patternStep = PatternDraftStep.IDLE_OR_DRAW_NEW,
                                                        patternError = "Pattern must connect at least ${PatternValidator.MIN_NODES} dots"
                                                    )
                                                )
                                            } else {
                                                onDraftChange(
                                                    draft.copy(
                                                        newPattern = encoded,
                                                        confirmPattern = "",
                                                        patternStep = PatternDraftStep.AWAITING_CONFIRM,
                                                        patternError = null
                                                    )
                                                )
                                            }
                                        }

                                        PatternDraftStep.AWAITING_CONFIRM -> {
                                            if (encoded == draft.newPattern) {
                                                onDraftChange(
                                                    draft.copy(
                                                        confirmPattern = encoded,
                                                        patternStep = PatternDraftStep.READY_TO_SAVE,
                                                        patternError = null
                                                    )
                                                )
                                            } else {
                                                onDraftChange(
                                                    draft.copy(
                                                        newPattern = "",
                                                        confirmPattern = "",
                                                        patternStep = PatternDraftStep.IDLE_OR_DRAW_NEW,
                                                        patternError = "Patterns did not match. Draw a new pattern again."
                                                    )
                                                )
                                            }
                                        }
                                    }
                                }
                            )

                            AnimatedVisibility(visible = draft.patternError != null) {
                                Text(
                                    text = draft.patternError.orEmpty(),
                                    color = CrimsonAlert,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier
                                        .padding(top = 10.dp)
                                        .testTag("settings_pattern_error")
                                )
                            }
                        }
                    }
                }

                UnlockMethod.PASSWORD -> {
                    Section3DHeader(
                        icon = Icons.Default.Password,
                        title = "Master Password Credentials",
                        subtitle = "Alphanumeric passphrase (${PasswordValidator.MIN_LENGTH}–${PasswordValidator.MAX_LENGTH} characters)",
                        accentStart = VividAmethyst,
                        accentEnd = Color(0xFF4C1D95)
                    )

                    Tactile3DCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val configured = PasswordValidator.isValidPassword(draft.savedMasterPassword)
                                Surface(
                                    shape = CircleShape,
                                    color = if (configured) NeonEmerald.copy(alpha = 0.16f) else ObsidianSurfaceVariant,
                                    border = BorderStroke(1.dp, if (configured) NeonEmerald else ObsidianBorder)
                                ) {
                                    Text(
                                        text = if (configured) "Password Configured" else "Password Not Set",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (configured) NeonEmerald else TextSecondary,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                                IconButton(
                                    onClick = { showPasswordText = !showPasswordText },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        imageVector = if (showPasswordText) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = if (showPasswordText) "Hide Password" else "Show Password",
                                        tint = NeonViolet
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            val visual = if (showPasswordText) VisualTransformation.None else PasswordVisualTransformation()
                            OutlinedTextField(
                                value = draft.newPassword,
                                onValueChange = { raw ->
                                    onDraftChange(
                                        draft.copy(
                                            newPassword = raw.take(PasswordValidator.MAX_LENGTH),
                                            passwordError = null
                                        )
                                    )
                                },
                                label = { Text("New Password") },
                                placeholder = {
                                    Text(
                                        if (PasswordValidator.isValidPassword(draft.savedMasterPassword)) {
                                            "Leave blank to keep current"
                                        } else {
                                            "Enter 4–32 characters"
                                        }
                                    )
                                },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                visualTransformation = visual,
                                isError = draft.passwordError != null,
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("settings_new_password_input")
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            OutlinedTextField(
                                value = draft.confirmPassword,
                                onValueChange = { raw ->
                                    onDraftChange(
                                        draft.copy(
                                            confirmPassword = raw.take(PasswordValidator.MAX_LENGTH),
                                            passwordError = null
                                        )
                                    )
                                },
                                label = { Text("Confirm New Password") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                visualTransformation = visual,
                                isError = draft.passwordError != null,
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("settings_confirm_password_input")
                            )

                            AnimatedVisibility(visible = draft.passwordError != null) {
                                Text(
                                    text = draft.passwordError.orEmpty(),
                                    color = CrimsonAlert,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier
                                        .padding(top = 8.dp)
                                        .testTag("settings_password_error")
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    Section3DHeader(
        icon = Icons.Default.HelpOutline,
        title = "Recovery Security Question",
        subtitle = "Recover access if you forget your Master PIN"
    )

    Tactile3DCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            ExposedDropdownMenuBox(
                expanded = isQuestionDropdownExpanded,
                onExpandedChange = { isQuestionDropdownExpanded = !isQuestionDropdownExpanded }
            ) {
                OutlinedTextField(
                    value = draft.recoveryQuestion,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Security Question") },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = isQuestionDropdownExpanded)
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        .testTag("settings_recovery_question_dropdown")
                )
                ExposedDropdownMenu(
                    expanded = isQuestionDropdownExpanded,
                    onDismissRequest = { isQuestionDropdownExpanded = false }
                ) {
                    SecurityQuestions.DEFAULT_LIST.forEach { q ->
                        DropdownMenuItem(
                            text = { Text(q) },
                            onClick = {
                                onDraftChange(draft.copy(recoveryQuestion = q, recoveryError = null))
                                isQuestionDropdownExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = draft.recoveryAnswerInput,
                onValueChange = { raw ->
                    onDraftChange(draft.copy(recoveryAnswerInput = raw, recoveryError = null))
                },
                label = { Text("Security Answer") },
                placeholder = {
                    Text(if (hasSavedRecoveryQuestion) "Set new answer (optional)" else "Enter secret answer")
                },
                isError = draft.recoveryError != null,
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settings_recovery_answer_input")
            )

            AnimatedVisibility(visible = draft.recoveryError != null) {
                Text(
                    text = draft.recoveryError.orEmpty(),
                    color = CrimsonAlert,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .testTag("settings_recovery_error")
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
        icon = Icons.Default.Videocam,
        title = "Intruder Video Capture (3+ Fails)",
        description = "Silently records an 8-second front-camera video clip in Intruder Vault after 3 consecutive wrong attempts instead of a still photo.",
        checked = draft.intruderVideoEnabled,
        badgeText = if (draft.intruderVideoEnabled) "8s Video Trap" else null,
        accentStart = Color(0xFFEC4899),
        accentEnd = Color(0xFF9D174D),
        testTag = "switch_intruder_video",
        onCheckedChange = { onDraftChange(draft.copy(intruderVideoEnabled = it)) }
    )

    TactileSettingSwitchCard(
        icon = Icons.Default.AdminPanelSettings,
        title = "Uninstall Protection",
        description = "Blocks Package Installer & Android Settings with your Master PIN and activates Device Admin to stop force-stop or uninstall.",
        checked = draft.uninstallProtectionEnabled,
        badgeText = when {
            draft.uninstallProtectionEnabled && isDeviceAdminActive -> "Admin Active"
            draft.uninstallProtectionEnabled -> "Settings Locked"
            else -> null
        },
        accentStart = NeonEmerald,
        accentEnd = Color(0xFF047857),
        testTag = "switch_uninstall_protection",
        onCheckedChange = { enable ->
            if (enable) {
                onEnableUninstallProtection()
            } else {
                onRequestDisableUninstallProtection()
            }
        }
    )

    if (draft.uninstallProtectionEnabled && !isDeviceAdminActive) {
        OutlinedButton(
            onClick = onGrantDeviceAdmin,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, NeonEmerald.copy(alpha = 0.6f)),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("grant_device_admin_btn")
        ) {
            Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = NeonEmerald, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Activate Android Device Admin Shield", color = NeonEmerald, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
internal fun DecoyCategoryContent(
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
                    modifier = Modifier.size(48.dp)
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
internal fun DisguiseCategoryContent(
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
internal fun AutoLockCategoryContent(
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
        icon = Icons.Default.AddModerator,
        title = "Auto-Lock Newly Installed Apps",
        description = "Instantly locks any newly installed app from Play Store or APK so new apps stay protected automatically.",
        checked = draft.autoLockNewAppsEnabled,
        accentStart = NeonEmerald,
        accentEnd = Color(0xFF047857),
        testTag = "switch_auto_lock_new_apps",
        onCheckedChange = { onDraftChange(draft.copy(autoLockNewAppsEnabled = it)) }
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
