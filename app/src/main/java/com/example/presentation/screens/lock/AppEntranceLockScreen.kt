package com.example.presentation.screens.lock

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.domain.model.PasswordValidator
import com.example.domain.model.PatternValidator
import com.example.domain.model.PinValidator
import com.example.domain.model.UnlockMethod
import com.example.ui.components.Icon3DBadge
import com.example.ui.components.PatternLockView
import com.example.ui.components.Tactile3DCard
import com.example.ui.components.tactileScale
import com.example.ui.theme.*
import com.example.util.AlarmAlertHelper
import com.example.util.MasterKeyManager
import com.example.util.SecurePreferences
import kotlinx.coroutines.delay

sealed interface EntranceStage {
    data class SetupPin(
        val method: UnlockMethod = UnlockMethod.PIN,
        val pin: String = "",
        val password: String = "",
        val passwordVisible: Boolean = false,
        val patternAttemptNonce: Int = 0,
        val error: String? = null
    ) : EntranceStage

    data class SetupConfirmPin(
        val method: UnlockMethod = UnlockMethod.PIN,
        val firstPin: String = "",
        val confirmPin: String = "",
        val firstPattern: String = "",
        val patternAttemptNonce: Int = 0,
        val firstPassword: String = "",
        val confirmPassword: String = "",
        val passwordVisible: Boolean = false,
        val error: String? = null
    ) : EntranceStage

    data class SetupRecovery(
        val method: UnlockMethod = UnlockMethod.PIN,
        val confirmedPin: String = "1234",
        val confirmedPattern: String = "",
        val confirmedPassword: String = "",
        val question: String,
        val answer: String = "",
        val dropdownExpanded: Boolean = false,
        val error: String? = null
    ) : EntranceStage

    data class PinVerify(
        val enteredPin: String = "",
        val failedAttempts: Int = 0,
        val error: String? = null,
        val keypadDigits: List<String>
    ) : EntranceStage

    data class PatternVerify(
        val failedAttempts: Int = 0,
        val error: String? = null
    ) : EntranceStage

    data class PasswordVerify(
        val enteredPassword: String = "",
        val passwordVisible: Boolean = false,
        val failedAttempts: Int = 0,
        val error: String? = null
    ) : EntranceStage

    data object Unlocked : EntranceStage

    val currentFailedAttempts: Int
        get() = when (this) {
            is PinVerify -> failedAttempts
            is PatternVerify -> failedAttempts
            is PasswordVerify -> failedAttempts
            else -> 0
        }

    val isVerifyStage: Boolean
        get() = this is PinVerify || this is PatternVerify || this is PasswordVerify
}

sealed interface EntranceRecoveryModal {
    data object Hidden : EntranceRecoveryModal
    data class VerifyAnswer(val input: String = "", val error: String? = null) : EntranceRecoveryModal
    data class ResetPin(
        val newPin: String = "",
        val confirmPin: String = "",
        val newPattern: String = "",
        val confirmPattern: String = "",
        val newPassword: String = "",
        val confirmPassword: String = "",
        val resetStepConfirmPattern: Boolean = false,
        val error: String? = null
    ) : EntranceRecoveryModal
}

private val DEFAULT_SECURITY_QUESTIONS = listOf(
    "What was the name of your first school?",
    "What is your mother's maiden name?",
    "What city were you born in?",
    "What was your childhood nickname?",
    "What is your favorite book or movie?"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppEntranceLockScreen(
    activity: FragmentActivity,
    securePrefs: SecurePreferences,
    onUnlockSuccess: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val settings by securePrefs.settingsFlow.collectAsStateWithLifecycle()

    var stage by remember {
        mutableStateOf<EntranceStage>(
            if (!securePrefs.hasMasterPin()) {
                EntranceStage.SetupPin()
            } else {
                val baseDigits = LockScreenStage.DEFAULT_DIGITS
                when (settings.unlockMethod) {
                    UnlockMethod.PIN -> EntranceStage.PinVerify(
                        keypadDigits = if (settings.scrambleKeypadEnabled) baseDigits.shuffled() else baseDigits
                    )
                    UnlockMethod.PATTERN -> {
                        if (PatternValidator.isValidEncodedPattern(settings.masterPattern)) {
                            EntranceStage.PatternVerify()
                        } else {
                            EntranceStage.PinVerify(
                                keypadDigits = if (settings.scrambleKeypadEnabled) baseDigits.shuffled() else baseDigits
                            )
                        }
                    }
                    UnlockMethod.PASSWORD -> {
                        if (settings.masterPassword.isNotBlank()) {
                            EntranceStage.PasswordVerify()
                        } else {
                            EntranceStage.PinVerify(
                                keypadDigits = if (settings.scrambleKeypadEnabled) baseDigits.shuffled() else baseDigits
                            )
                        }
                    }
                }
            }
        )
    }

    var recoveryModal by remember { mutableStateOf<EntranceRecoveryModal>(EntranceRecoveryModal.Hidden) }
    val securityQuestion = remember(stage::class) { securePrefs.getSecurityQuestion() }

    val isBiometricAvailable = remember(activity) {
        BiometricManager.from(activity).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }

    LaunchedEffect(stage) {
        if (stage is EntranceStage.Unlocked) {
            delay(160)
            onUnlockSuccess()
        }
    }

    val showBiometricPrompt = remember(activity, settings.unlockMethod) {
        {
            val executor = ContextCompat.getMainExecutor(activity)
            val prompt = BiometricPrompt(
                activity,
                executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        super.onAuthenticationSucceeded(result)
                        stage = EntranceStage.Unlocked
                    }

                    override fun onAuthenticationFailed() {
                        super.onAuthenticationFailed()
                        val current = stage
                        val nextFails = current.currentFailedAttempts + 1
                        takeIntruderSelfie(
                            activity = activity,
                            targetPackageName = activity.packageName,
                            targetAppLabel = "DASMO LOCK",
                            authMethodLabel = "Biometric",
                            failedAttemptNumber = nextFails
                        )
                        if (nextFails >= 3 && settings.breakInSirenEnabled) {
                            AlarmAlertHelper.playBreakInAlarm(activity)
                        }
                        stage = when (current) {
                            is EntranceStage.PinVerify -> current.copy(
                                failedAttempts = nextFails,
                                error = "Biometric verification failed"
                            )
                            is EntranceStage.PatternVerify -> current.copy(
                                failedAttempts = nextFails,
                                error = "Biometric verification failed"
                            )
                            is EntranceStage.PasswordVerify -> current.copy(
                                failedAttempts = nextFails,
                                error = "Biometric verification failed"
                            )
                            else -> current
                        }
                    }
                }
            )
            val negativeText = when (settings.unlockMethod) {
                UnlockMethod.PIN -> "Use PIN"
                UnlockMethod.PATTERN -> "Use Pattern"
                UnlockMethod.PASSWORD -> "Use Password"
            }
            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock DASMO LOCK")
                .setSubtitle("Confirm fingerprint or face to open DASMO App Lock")
                .setNegativeButtonText(negativeText)
                .build()
            prompt.authenticate(promptInfo)
        }
    }

    LaunchedEffect(Unit) {
        if (stage.isVerifyStage && isBiometricAvailable) {
            showBiometricPrompt()
        }
    }

    val shakeOffset = remember { Animatable(0f) }
    val failedAttempts = stage.currentFailedAttempts
    LaunchedEffect(failedAttempts) {
        if (failedAttempts > 0) {
            shakeOffset.animateTo(
                targetValue = 0f,
                animationSpec = keyframes {
                    durationMillis = 340
                    0f at 0
                    18f at 45
                    -18f at 90
                    18f at 135
                    -18f at 180
                    10f at 225
                    0f at 340
                }
            )
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = ObsidianBlack
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(ObsidianBlack, Color(0xFF130B24), ObsidianBlack)
                    )
                )
                .systemBarsPadding()
                .imePadding()
        ) {
            AnimatedContent(
                targetState = stage,
                contentKey = { it::class },
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "entrance_stage_transition"
            ) { current ->
                when (current) {
                    is EntranceStage.SetupPin -> {
                        SetupCredentialContent(
                            stepTitle = when (current.method) {
                                UnlockMethod.PIN -> "Step 1 of 3: Create your 4-digit Master PIN"
                                UnlockMethod.PATTERN -> "Step 1 of 3: Draw your Master Pattern (min 4 dots)"
                                UnlockMethod.PASSWORD -> "Step 1 of 3: Create your Master Password"
                            },
                            selectedMethod = current.method,
                            allowMethodSwitch = true,
                            enteredPinLength = current.pin.length,
                            passwordValue = current.password,
                            passwordVisible = current.passwordVisible,
                            patternAttemptNonce = current.patternAttemptNonce,
                            error = current.error,
                            onSelectMethod = { method ->
                                stage = current.copy(
                                    method = method,
                                    pin = "",
                                    password = "",
                                    error = null
                                )
                            },
                            onDigit = { d ->
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                if (current.pin.length < 4) {
                                    val updated = current.pin + d
                                    stage = if (updated.length == 4) {
                                        EntranceStage.SetupConfirmPin(
                                            method = UnlockMethod.PIN,
                                            firstPin = updated
                                        )
                                    } else {
                                        current.copy(pin = updated, error = null)
                                    }
                                }
                            },
                            onBackspace = {
                                stage = current.copy(pin = current.pin.dropLast(1), error = null)
                            },
                            onClear = {
                                stage = current.copy(pin = "", error = null)
                            },
                            onPatternComplete = { nodes ->
                                if (!PatternValidator.isValidPattern(nodes)) {
                                    stage = current.copy(
                                        patternAttemptNonce = current.patternAttemptNonce + 1,
                                        error = "Connect at least ${PatternValidator.MIN_NODES} dots"
                                    )
                                } else {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    stage = EntranceStage.SetupConfirmPin(
                                        method = UnlockMethod.PATTERN,
                                        firstPattern = PatternValidator.encode(nodes)
                                    )
                                }
                            },
                            onPasswordChange = { value ->
                                stage = current.copy(password = value.take(PasswordValidator.MAX_LENGTH), error = null)
                            },
                            onTogglePasswordVisible = {
                                stage = current.copy(passwordVisible = !current.passwordVisible)
                            },
                            onPasswordSubmit = {
                                if (!PasswordValidator.isValidPassword(current.password)) {
                                    stage = current.copy(
                                        error = "Password must be ${PasswordValidator.MIN_LENGTH}–${PasswordValidator.MAX_LENGTH} characters"
                                    )
                                } else {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    stage = EntranceStage.SetupConfirmPin(
                                        method = UnlockMethod.PASSWORD,
                                        firstPassword = current.password
                                    )
                                }
                            }
                        )
                    }

                    is EntranceStage.SetupConfirmPin -> {
                        SetupCredentialContent(
                            stepTitle = when (current.method) {
                                UnlockMethod.PIN -> "Step 2 of 3: Confirm your 4-digit Master PIN"
                                UnlockMethod.PATTERN -> "Step 2 of 3: Draw Pattern again to confirm"
                                UnlockMethod.PASSWORD -> "Step 2 of 3: Confirm your Master Password"
                            },
                            selectedMethod = current.method,
                            allowMethodSwitch = false,
                            enteredPinLength = current.confirmPin.length,
                            passwordValue = current.confirmPassword,
                            passwordVisible = current.passwordVisible,
                            patternAttemptNonce = current.patternAttemptNonce,
                            error = current.error,
                            onSelectMethod = {},
                            onDigit = { d ->
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                if (current.confirmPin.length < 4) {
                                    val updated = current.confirmPin + d
                                    if (updated.length == 4) {
                                        if (updated == current.firstPin) {
                                            stage = EntranceStage.SetupRecovery(
                                                method = UnlockMethod.PIN,
                                                confirmedPin = updated,
                                                question = DEFAULT_SECURITY_QUESTIONS[0]
                                            )
                                        } else {
                                            stage = current.copy(
                                                confirmPin = "",
                                                error = "PINs do not match. Please try again."
                                            )
                                        }
                                    } else {
                                        stage = current.copy(confirmPin = updated, error = null)
                                    }
                                }
                            },
                            onBackspace = {
                                stage = current.copy(confirmPin = current.confirmPin.dropLast(1), error = null)
                            },
                            onClear = {
                                stage = current.copy(confirmPin = "", error = null)
                            },
                            onPatternComplete = { nodes ->
                                val encoded = PatternValidator.encode(nodes)
                                if (encoded == current.firstPattern) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    stage = EntranceStage.SetupRecovery(
                                        method = UnlockMethod.PATTERN,
                                        confirmedPin = "1234",
                                        confirmedPattern = encoded,
                                        question = DEFAULT_SECURITY_QUESTIONS[0]
                                    )
                                } else {
                                    stage = current.copy(
                                        patternAttemptNonce = current.patternAttemptNonce + 1,
                                        error = "Pattern did not match. Draw again."
                                    )
                                }
                            },
                            onPasswordChange = { value ->
                                stage = current.copy(confirmPassword = value.take(PasswordValidator.MAX_LENGTH), error = null)
                            },
                            onTogglePasswordVisible = {
                                stage = current.copy(passwordVisible = !current.passwordVisible)
                            },
                            onPasswordSubmit = {
                                if (current.confirmPassword == current.firstPassword) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    stage = EntranceStage.SetupRecovery(
                                        method = UnlockMethod.PASSWORD,
                                        confirmedPin = "1234",
                                        confirmedPassword = current.confirmPassword,
                                        question = DEFAULT_SECURITY_QUESTIONS[0]
                                    )
                                } else {
                                    stage = current.copy(
                                        confirmPassword = "",
                                        error = "Passwords do not match. Try again."
                                    )
                                }
                            },
                            onBackStep = {
                                stage = EntranceStage.SetupPin(method = current.method)
                            }
                        )
                    }

                    is EntranceStage.SetupRecovery -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Spacer(modifier = Modifier.height(12.dp))

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon3DBadge(
                                    icon = Icons.Default.Key,
                                    contentDescription = "Setup Recovery",
                                    size = 64.dp
                                )
                                Text(
                                    text = "Welcome to DASMO LOCK",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = PureWhite,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = "Step 3 of 3: Set Security Recovery Question",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = NeonViolet,
                                    fontWeight = FontWeight.SemiBold,
                                    textAlign = TextAlign.Center
                                )

                                Tactile3DCard(
                                    highlighted = true,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text(
                                            "Select Security Question",
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = PureWhite
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))

                                        ExposedDropdownMenuBox(
                                            expanded = current.dropdownExpanded,
                                            onExpandedChange = {
                                                stage = current.copy(dropdownExpanded = !current.dropdownExpanded)
                                            }
                                        ) {
                                            OutlinedTextField(
                                                value = current.question,
                                                onValueChange = {},
                                                readOnly = true,
                                                trailingIcon = {
                                                    ExposedDropdownMenuDefaults.TrailingIcon(
                                                        expanded = current.dropdownExpanded
                                                    )
                                                },
                                                shape = RoundedCornerShape(14.dp),
                                                modifier = Modifier
                                                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                                    .fillMaxWidth()
                                            )
                                            ExposedDropdownMenu(
                                                expanded = current.dropdownExpanded,
                                                onDismissRequest = {
                                                    stage = current.copy(dropdownExpanded = false)
                                                }
                                            ) {
                                                DEFAULT_SECURITY_QUESTIONS.forEach { q ->
                                                    DropdownMenuItem(
                                                        text = { Text(q) },
                                                        onClick = {
                                                            stage = current.copy(
                                                                question = q,
                                                                dropdownExpanded = false
                                                            )
                                                        }
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text(
                                            "Your Secret Answer",
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = PureWhite
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        OutlinedTextField(
                                            value = current.answer,
                                            onValueChange = {
                                                stage = current.copy(answer = it, error = null)
                                            },
                                            placeholder = { Text("e.g. Lincoln High School") },
                                            singleLine = true,
                                            shape = RoundedCornerShape(14.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("setup_recovery_answer_input")
                                        )

                                        if (current.error != null) {
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = current.error,
                                                color = CrimsonAlert,
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(14.dp))
                                        Surface(
                                            color = ContainerPurple.copy(alpha = 0.7f),
                                            shape = RoundedCornerShape(12.dp),
                                            border = BorderStroke(1.dp, ElectricPurple.copy(alpha = 0.4f))
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(10.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Key,
                                                    contentDescription = null,
                                                    tint = NeonViolet,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    "Master Recovery Key: ${MasterKeyManager.MASTER_KEY} is pre-configured for emergency recovery.",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = LavenderGlow
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Button(
                                onClick = {
                                    if (current.answer.isBlank()) {
                                        stage = current.copy(error = "Please enter an answer for your security question.")
                                        return@Button
                                    }
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    securePrefs.updateSettings { s ->
                                        s.copy(
                                            unlockMethod = current.method,
                                            masterPin = current.confirmedPin,
                                            masterPattern = current.confirmedPattern,
                                            masterPassword = current.confirmedPassword
                                        )
                                    }
                                    securePrefs.setSecurityRecovery(current.question, current.answer)
                                    stage = EntranceStage.Unlocked
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ElectricPurple,
                                    contentColor = PureWhite
                                ),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(54.dp)
                                    .testTag("setup_complete_button")
                            ) {
                                Text("Complete Setup & Open DASMO LOCK", fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    is EntranceStage.PinVerify,
                    is EntranceStage.PatternVerify,
                    is EntranceStage.PasswordVerify,
                    is EntranceStage.Unlocked -> {
                        val verifyPin = current as? EntranceStage.PinVerify
                        val verifyPattern = current as? EntranceStage.PatternVerify
                        val verifyPassword = current as? EntranceStage.PasswordVerify
                        val enteredLength = if (current is EntranceStage.Unlocked) 4 else verifyPin?.enteredPin?.length ?: 0
                        val digits = verifyPin?.keypadDigits ?: LockScreenStage.DEFAULT_DIGITS

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Spacer(modifier = Modifier.height(10.dp))

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(84.dp)
                                        .shadow(
                                            elevation = 16.dp,
                                            shape = RoundedCornerShape(24.dp),
                                            ambientColor = ElectricPurple.copy(alpha = 0.6f),
                                            spotColor = ElectricPurple.copy(alpha = 0.7f)
                                        )
                                        .clip(RoundedCornerShape(24.dp))
                                        .border(1.5.dp, NeonViolet.copy(alpha = 0.7f), RoundedCornerShape(24.dp))
                                ) {
                                    Image(
                                        painter = painterResource(id = R.drawable.img_shield_3d),
                                        contentDescription = "DASMO Shield",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }

                                Text(
                                    text = "DASMO APP LOCK",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = PureWhite
                                )
                                Text(
                                    text = when {
                                        current is EntranceStage.Unlocked -> "Unlocked"
                                        verifyPattern != null -> "Draw your unlock pattern"
                                        verifyPassword != null -> "Enter your Master Password"
                                        else -> "Enter your 4-digit Master PIN to unlock"
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary
                                )

                                if (verifyPattern == null && verifyPassword == null) {
                                    Row(
                                        modifier = Modifier
                                            .offset(x = shakeOffset.value.dp)
                                            .padding(vertical = 10.dp),
                                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        repeat(4) { index ->
                                            val filled = index < enteredLength
                                            Box(
                                                modifier = Modifier
                                                    .size(16.dp)
                                                    .clip(CircleShape)
                                                    .background(if (filled) NeonViolet else ObsidianSurfaceVariant)
                                                    .border(
                                                        1.dp,
                                                        if (filled) PureWhite.copy(alpha = 0.7f) else ObsidianBorder,
                                                        CircleShape
                                                    )
                                            )
                                        }
                                    }
                                }

                                val errorMsg = verifyPin?.error ?: verifyPattern?.error ?: verifyPassword?.error
                                if (!errorMsg.isNullOrBlank()) {
                                    Text(
                                        text = errorMsg,
                                        color = CrimsonAlert,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.testTag("entrance_error_text")
                                    )
                                }
                            }

                            when {
                                verifyPattern != null -> {
                                    PatternLockView(
                                        isError = verifyPattern.error != null,
                                        resetKey = verifyPattern.failedAttempts,
                                        testTag = "entrance_pattern_pad",
                                        modifier = Modifier
                                            .fillMaxWidth(0.84f)
                                            .offset(x = shakeOffset.value.dp),
                                        onPatternStart = {
                                            if (verifyPattern.error != null) {
                                                stage = verifyPattern.copy(error = null)
                                            }
                                        },
                                        onPatternComplete = { nodes ->
                                            if (!PatternValidator.isValidPattern(nodes)) {
                                                stage = verifyPattern.copy(error = "Connect at least ${PatternValidator.MIN_NODES} dots")
                                            } else if (PinValidator.verifyAppUnlockPattern(nodes, settings)) {
                                                stage = EntranceStage.Unlocked
                                            } else {
                                                val nextFails = verifyPattern.failedAttempts + 1
                                                takeIntruderSelfie(
                                                    activity = activity,
                                                    targetPackageName = activity.packageName,
                                                    targetAppLabel = "DASMO LOCK",
                                                    authMethodLabel = "Pattern",
                                                    failedAttemptNumber = nextFails
                                                )
                                                if (nextFails >= 3 && settings.breakInSirenEnabled) {
                                                    AlarmAlertHelper.playBreakInAlarm(activity)
                                                }
                                                stage = verifyPattern.copy(
                                                    failedAttempts = nextFails,
                                                    error = "Incorrect Pattern (Attempt $nextFails)"
                                                )
                                            }
                                        }
                                    )
                                }

                                verifyPassword != null -> {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .widthIn(max = 360.dp)
                                            .offset(x = shakeOffset.value.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = verifyPassword.enteredPassword,
                                            onValueChange = {
                                                stage = verifyPassword.copy(enteredPassword = it.take(PasswordValidator.MAX_LENGTH), error = null)
                                            },
                                            label = { Text("Master Password") },
                                            singleLine = true,
                                            isError = verifyPassword.error != null,
                                            visualTransformation = if (verifyPassword.passwordVisible) {
                                                VisualTransformation.None
                                            } else {
                                                PasswordVisualTransformation()
                                            },
                                            keyboardOptions = KeyboardOptions(
                                                keyboardType = KeyboardType.Password,
                                                imeAction = ImeAction.Done
                                            ),
                                            keyboardActions = KeyboardActions(
                                                onDone = {
                                                    if (PinValidator.verifyAppUnlockPassword(verifyPassword.enteredPassword, settings)) {
                                                        stage = EntranceStage.Unlocked
                                                    } else {
                                                        val nextFails = verifyPassword.failedAttempts + 1
                                                        takeIntruderSelfie(
                                                            activity = activity,
                                                            targetPackageName = activity.packageName,
                                                            targetAppLabel = "DASMO LOCK",
                                                            authMethodLabel = "Password",
                                                            failedAttemptNumber = nextFails
                                                        )
                                                        if (nextFails >= 3 && settings.breakInSirenEnabled) {
                                                            AlarmAlertHelper.playBreakInAlarm(activity)
                                                        }
                                                        stage = verifyPassword.copy(
                                                            enteredPassword = "",
                                                            failedAttempts = nextFails,
                                                            error = "Incorrect Password (Attempt $nextFails)"
                                                        )
                                                    }
                                                }
                                            ),
                                            trailingIcon = {
                                                IconButton(
                                                    onClick = { stage = verifyPassword.copy(passwordVisible = !verifyPassword.passwordVisible) },
                                                    modifier = Modifier.size(48.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = if (verifyPassword.passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                        contentDescription = if (verifyPassword.passwordVisible) "Hide Password" else "Show Password",
                                                        tint = NeonViolet
                                                    )
                                                }
                                            },
                                            shape = RoundedCornerShape(16.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("entrance_password_input")
                                        )
                                        Spacer(modifier = Modifier.height(14.dp))
                                        Button(
                                            onClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                if (PinValidator.verifyAppUnlockPassword(verifyPassword.enteredPassword, settings)) {
                                                    stage = EntranceStage.Unlocked
                                                } else {
                                                    val nextFails = verifyPassword.failedAttempts + 1
                                                    takeIntruderSelfie(
                                                        activity = activity,
                                                        targetPackageName = activity.packageName,
                                                        targetAppLabel = "DASMO LOCK",
                                                        authMethodLabel = "Password",
                                                        failedAttemptNumber = nextFails
                                                    )
                                                    if (nextFails >= 3 && settings.breakInSirenEnabled) {
                                                        AlarmAlertHelper.playBreakInAlarm(activity)
                                                    }
                                                    stage = verifyPassword.copy(
                                                        enteredPassword = "",
                                                        failedAttempts = nextFails,
                                                        error = "Incorrect Password (Attempt $nextFails)"
                                                    )
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = ElectricPurple, contentColor = PureWhite),
                                            shape = RoundedCornerShape(16.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(52.dp)
                                                .testTag("entrance_password_submit_btn")
                                        ) {
                                            Text("Unlock DASMO LOCK", fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }

                                else -> {
                                    EntranceKeypadGrid(
                                        digits = digits,
                                        onDigit = { d ->
                                            if (verifyPin != null && verifyPin.enteredPin.length < 4) {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                val updated = verifyPin.enteredPin + d
                                                if (updated.length < 4) {
                                                    stage = verifyPin.copy(enteredPin = updated, error = null)
                                                } else {
                                                    val matched = PinValidator.verifyAppUnlockPin(updated, settings)
                                                    if (matched) {
                                                        stage = EntranceStage.Unlocked
                                                    } else {
                                                        val nextFails = verifyPin.failedAttempts + 1
                                                        takeIntruderSelfie(
                                                            activity = activity,
                                                            targetPackageName = activity.packageName,
                                                            targetAppLabel = "DASMO LOCK",
                                                            authMethodLabel = "PIN",
                                                            failedAttemptNumber = nextFails
                                                        )
                                                        if (nextFails >= 3 && settings.breakInSirenEnabled) {
                                                            AlarmAlertHelper.playBreakInAlarm(activity)
                                                        }
                                                        stage = verifyPin.copy(
                                                            enteredPin = "",
                                                            failedAttempts = nextFails,
                                                            error = "Incorrect PIN (Attempt $nextFails)"
                                                        )
                                                    }
                                                }
                                            }
                                        },
                                        onClear = {
                                            if (verifyPin != null) stage = verifyPin.copy(enteredPin = "", error = null)
                                        },
                                        onBackspace = {
                                            if (verifyPin != null && verifyPin.enteredPin.isNotEmpty()) {
                                                stage = verifyPin.copy(enteredPin = verifyPin.enteredPin.dropLast(1), error = null)
                                            }
                                        }
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isBiometricAvailable) {
                                    TextButton(onClick = { showBiometricPrompt() }) {
                                        Icon(
                                            Icons.Default.Fingerprint,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Biometric", color = CyberCyan)
                                    }
                                } else {
                                    Spacer(modifier = Modifier.width(10.dp))
                                }

                                TextButton(onClick = { recoveryModal = EntranceRecoveryModal.VerifyAnswer() }) {
                                    Text(
                                        when (settings.unlockMethod) {
                                            UnlockMethod.PIN -> "Forgot PIN?"
                                            UnlockMethod.PATTERN -> "Forgot Pattern?"
                                            UnlockMethod.PASSWORD -> "Forgot Password?"
                                        },
                                        color = NeonViolet,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    when (val modal = recoveryModal) {
        EntranceRecoveryModal.Hidden -> Unit

        is EntranceRecoveryModal.VerifyAnswer -> {
            AlertDialog(
                onDismissRequest = { recoveryModal = EntranceRecoveryModal.Hidden },
                containerColor = ObsidianSurface,
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.HelpOutline, contentDescription = null, tint = ElectricPurple)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("PIN Recovery", fontWeight = FontWeight.Bold, color = PureWhite)
                    }
                },
                text = {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        if (securityQuestion.isNotBlank()) {
                            Text(
                                "Security Question:",
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodySmall,
                                color = NeonViolet
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                securityQuestion,
                                style = MaterialTheme.typography.bodyMedium,
                                color = PureWhite
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                        Text(
                            "Answer your security question, or enter your Master Recovery Key:",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = modal.input,
                            onValueChange = { recoveryModal = modal.copy(input = it, error = null) },
                            label = { Text("Answer or Master Key") },
                            isError = modal.error != null,
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (modal.error != null) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(modal.error, color = CrimsonAlert, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val isMasterKey = MasterKeyManager.isMasterKey(modal.input)
                            val isCorrectAnswer = securePrefs.verifySecurityAnswer(modal.input)
                            recoveryModal = if (isMasterKey || isCorrectAnswer) {
                                EntranceRecoveryModal.ResetPin()
                            } else {
                                modal.copy(error = "Incorrect answer or invalid Master Key!")
                            }
                        }
                    ) {
                        Text("Verify")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { recoveryModal = EntranceRecoveryModal.Hidden }) {
                        Text("Cancel")
                    }
                }
            )
        }

        is EntranceRecoveryModal.ResetPin -> {
            AlertDialog(
                onDismissRequest = {},
                containerColor = ObsidianSurface,
                title = {
                    Text(
                        text = when (settings.unlockMethod) {
                            UnlockMethod.PIN -> "Reset Master PIN"
                            UnlockMethod.PATTERN -> "Reset Unlock Pattern"
                            UnlockMethod.PASSWORD -> "Reset Master Password"
                        },
                        fontWeight = FontWeight.Bold,
                        color = PureWhite
                    )
                },
                text = {
                    Column {
                        when (settings.unlockMethod) {
                            UnlockMethod.PATTERN -> {
                                Text(
                                    text = if (!modal.resetStepConfirmPattern) {
                                        "Identity verified! Draw a new Pattern (at least 4 dots):"
                                    } else {
                                        "Draw your new Pattern again to confirm:"
                                    },
                                    color = TextSecondary
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                PatternLockView(
                                    isError = modal.error != null,
                                    resetKey = modal.resetStepConfirmPattern to modal.error,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .align(Alignment.CenterHorizontally),
                                    onPatternStart = {
                                        if (modal.error != null) recoveryModal = modal.copy(error = null)
                                    },
                                    onPatternComplete = { nodes ->
                                        val encoded = PatternValidator.encode(nodes)
                                        if (!modal.resetStepConfirmPattern) {
                                            if (!PatternValidator.isValidPattern(nodes)) {
                                                recoveryModal = modal.copy(error = "Connect at least 4 dots")
                                            } else {
                                                recoveryModal = modal.copy(
                                                    newPattern = encoded,
                                                    resetStepConfirmPattern = true,
                                                    error = null
                                                )
                                            }
                                        } else {
                                            if (encoded == modal.newPattern) {
                                                securePrefs.updateSettings { s -> s.copy(masterPattern = encoded) }
                                                recoveryModal = EntranceRecoveryModal.Hidden
                                                stage = EntranceStage.Unlocked
                                            } else {
                                                recoveryModal = modal.copy(
                                                    newPattern = "",
                                                    resetStepConfirmPattern = false,
                                                    error = "Pattern didn't match. Start over."
                                                )
                                            }
                                        }
                                    }
                                )
                            }

                            UnlockMethod.PASSWORD -> {
                                Text(
                                    "Identity verified! Set your new Master Password:",
                                    color = TextSecondary
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                OutlinedTextField(
                                    value = modal.newPassword,
                                    onValueChange = {
                                        recoveryModal = modal.copy(newPassword = it.take(PasswordValidator.MAX_LENGTH), error = null)
                                    },
                                    label = { Text("New Password (min 4 chars)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                    visualTransformation = PasswordVisualTransformation(),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = modal.confirmPassword,
                                    onValueChange = {
                                        recoveryModal = modal.copy(confirmPassword = it.take(PasswordValidator.MAX_LENGTH), error = null)
                                    },
                                    label = { Text("Confirm New Password") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                    visualTransformation = PasswordVisualTransformation(),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            UnlockMethod.PIN -> {
                                Text(
                                    "Identity verified! Set your new 4-digit Master PIN:",
                                    color = TextSecondary
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                OutlinedTextField(
                                    value = modal.newPin,
                                    onValueChange = {
                                        val clean = it.filter { c -> c.isDigit() }.take(4)
                                        recoveryModal = modal.copy(newPin = clean, error = null)
                                    },
                                    label = { Text("New PIN (4 digits)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                    visualTransformation = PasswordVisualTransformation(),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = modal.confirmPin,
                                    onValueChange = {
                                        val clean = it.filter { c -> c.isDigit() }.take(4)
                                        recoveryModal = modal.copy(confirmPin = clean, error = null)
                                    },
                                    label = { Text("Confirm New PIN") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                    visualTransformation = PasswordVisualTransformation(),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        if (modal.error != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(modal.error, color = CrimsonAlert, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                },
                confirmButton = {
                    if (settings.unlockMethod == UnlockMethod.PIN || settings.unlockMethod == UnlockMethod.PASSWORD) {
                        Button(
                            onClick = {
                                if (settings.unlockMethod == UnlockMethod.PASSWORD) {
                                    if (!PasswordValidator.isValidPassword(modal.newPassword)) {
                                        recoveryModal = modal.copy(error = "Password must be at least 4 characters")
                                        return@Button
                                    }
                                    if (modal.newPassword != modal.confirmPassword) {
                                        recoveryModal = modal.copy(error = "Passwords do not match")
                                        return@Button
                                    }
                                    securePrefs.updateSettings { s -> s.copy(masterPassword = modal.newPassword) }
                                } else {
                                    if (modal.newPin.length != 4) {
                                        recoveryModal = modal.copy(error = "PIN must be exactly 4 digits")
                                        return@Button
                                    }
                                    if (modal.newPin != modal.confirmPin) {
                                        recoveryModal = modal.copy(error = "PINs do not match")
                                        return@Button
                                    }
                                    if (settings.decoyPin.isNotEmpty() && modal.newPin == settings.decoyPin) {
                                        recoveryModal = modal.copy(error = "Master PIN cannot match Decoy PIN")
                                        return@Button
                                    }
                                    securePrefs.updateSettings { s -> s.copy(masterPin = modal.newPin) }
                                }
                                recoveryModal = EntranceRecoveryModal.Hidden
                                stage = EntranceStage.Unlocked
                            }
                        ) {
                            Text("Save & Open")
                        }
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            securePrefs.updateSettings { s -> s.copy(unlockMethod = UnlockMethod.PIN) }
                            recoveryModal = modal.copy(error = null)
                        }
                    ) {
                        if (settings.unlockMethod != UnlockMethod.PIN) {
                            Text("Switch to PIN", color = NeonViolet)
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun SetupCredentialContent(
    stepTitle: String,
    selectedMethod: UnlockMethod,
    allowMethodSwitch: Boolean,
    enteredPinLength: Int,
    passwordValue: String,
    passwordVisible: Boolean,
    patternAttemptNonce: Int,
    error: String?,
    onSelectMethod: (UnlockMethod) -> Unit,
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    onPatternComplete: (List<Int>) -> Unit,
    onPasswordChange: (String) -> Unit,
    onTogglePasswordVisible: () -> Unit,
    onPasswordSubmit: () -> Unit,
    onBackStep: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon3DBadge(
                icon = when (selectedMethod) {
                    UnlockMethod.PIN -> Icons.Default.Pin
                    UnlockMethod.PATTERN -> Icons.Default.Pattern
                    UnlockMethod.PASSWORD -> Icons.Default.Password
                },
                contentDescription = "Setup",
                size = 64.dp
            )
            Text(
                text = "Welcome to DASMO LOCK",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = PureWhite,
                textAlign = TextAlign.Center
            )
            Text(
                text = stepTitle,
                style = MaterialTheme.typography.bodyMedium,
                color = NeonViolet,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )

            if (allowMethodSwitch) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    UnlockMethod.entries.forEach { method ->
                        val active = method == selectedMethod
                        Tactile3DCard(
                            highlighted = active,
                            shape = RoundedCornerShape(14.dp),
                            onClick = { onSelectMethod(method) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("setup_method_${method.name.lowercase()}")
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 10.dp, horizontal = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = method.label,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                    color = if (active) PureWhite else TextSecondary
                                )
                            }
                        }
                    }
                }
            }

            if (selectedMethod == UnlockMethod.PIN) {
                Row(
                    modifier = Modifier.padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    repeat(4) { index ->
                        val filled = index < enteredPinLength
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(if (filled) NeonViolet else ObsidianSurfaceVariant)
                                .border(
                                    1.dp,
                                    if (filled) PureWhite.copy(alpha = 0.7f) else ObsidianBorder,
                                    CircleShape
                                )
                        )
                    }
                }
            }

            if (!error.isNullOrBlank()) {
                Text(
                    text = error,
                    color = CrimsonAlert,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        when (selectedMethod) {
            UnlockMethod.PIN -> {
                EntranceKeypadGrid(
                    digits = LockScreenStage.DEFAULT_DIGITS,
                    onDigit = onDigit,
                    onClear = onClear,
                    onBackspace = onBackspace
                )
            }

            UnlockMethod.PATTERN -> {
                PatternLockView(
                    isError = !error.isNullOrBlank(),
                    resetKey = patternAttemptNonce to stepTitle,
                    testTag = "setup_pattern_pad",
                    modifier = Modifier.fillMaxWidth(0.84f),
                    onPatternComplete = onPatternComplete
                )
            }

            UnlockMethod.PASSWORD -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 360.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    OutlinedTextField(
                        value = passwordValue,
                        onValueChange = onPasswordChange,
                        label = { Text("Master Password (4–32 chars)") },
                        singleLine = true,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = { onPasswordSubmit() }),
                        trailingIcon = {
                            IconButton(
                                onClick = onTogglePasswordVisible,
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (passwordVisible) "Hide Password" else "Show Password",
                                    tint = NeonViolet
                                )
                            }
                        },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("setup_password_input")
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = onPasswordSubmit,
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricPurple, contentColor = PureWhite),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("setup_password_continue_btn")
                    ) {
                        Text("Continue", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (onBackStep != null) {
            TextButton(onClick = onBackStep) {
                Text("Back to Step 1", color = TextSecondary)
            }
        } else {
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun EntranceKeypadGrid(
    digits: List<String>,
    onDigit: (String) -> Unit,
    onClear: () -> Unit,
    onBackspace: () -> Unit
) {
    val rows = remember(digits) {
        listOf(
            listOf(digits[0], digits[1], digits[2]),
            listOf(digits[3], digits[4], digits[5]),
            listOf(digits[6], digits[7], digits[8]),
            listOf("C", digits[9], "<")
        )
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                row.forEach { key ->
                    val interactionSource = remember { MutableInteractionSource() }
                    val pressed by interactionSource.collectIsPressedAsState()
                    val isAction = key == "C" || key == "<"

                    Surface(
                        onClick = {
                            when (key) {
                                "C" -> onClear()
                                "<" -> onBackspace()
                                else -> onDigit(key)
                            }
                        },
                        interactionSource = interactionSource,
                        shape = RoundedCornerShape(20.dp),
                        color = if (isAction) ObsidianElevated else ObsidianSurface,
                        contentColor = if (isAction) LavenderGlow else PureWhite,
                        border = BorderStroke(1.dp, ObsidianBorder),
                        shadowElevation = 6.dp,
                        modifier = Modifier
                            .size(72.dp)
                            .tactileScale(pressed = pressed, pressedScale = 0.9f)
                            .testTag("entrance_key_$key")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (key == "<") {
                                Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = "Backspace")
                            } else {
                                Text(
                                    text = key,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
