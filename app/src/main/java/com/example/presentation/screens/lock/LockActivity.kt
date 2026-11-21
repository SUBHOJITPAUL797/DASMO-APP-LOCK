package com.example.presentation.screens.lock

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.domain.model.SecuritySettings
import com.example.domain.model.UnlockMethod
import com.example.service.AppLockService
import com.example.ui.components.PatternLockView
import com.example.ui.components.tactileScale
import com.example.ui.theme.*
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import com.example.presentation.screens.intruders.IntruderLogStore
import com.example.service.LockSessionTracker
import com.example.util.AlarmAlertHelper
import com.example.util.MasterKeyManager
import com.example.util.SecurePreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

sealed interface LockRecoveryDialogState {
    data object Hidden : LockRecoveryDialogState
    data class Visible(val input: String = "", val error: String? = null) : LockRecoveryDialogState
}

class LockActivity : FragmentActivity() {

    private var currentTarget by mutableStateOf(LockTarget("", "", false))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        parseIntent(intent)

        val securePrefs = SecurePreferences.getInstance(this)
        setContent {
            val settings by securePrefs.settingsFlow.collectAsStateWithLifecycle()

            LaunchedEffect(settings.preventScreenshots) {
                if (settings.preventScreenshots) {
                    window.setFlags(
                        WindowManager.LayoutParams.FLAG_SECURE,
                        WindowManager.LayoutParams.FLAG_SECURE
                    )
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }

            MyApplicationTheme {
                LockScreenContent(
                    target = currentTarget,
                    settings = settings,
                    securePrefs = securePrefs,
                    onUnlockSuccess = {
                        AppLockService.markAppUnlocked(currentTarget.packageName)
                        finish()
                    },
                    activity = this
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        parseIntent(intent)
    }

    private fun parseIntent(intent: Intent?) {
        val targetPackage = intent?.getStringExtra("TARGET_PACKAGE").orEmpty()
        val appLabel = intent?.getStringExtra("APP_LABEL") ?: targetPackage
        val isHidden = intent?.getBooleanExtra("IS_HIDDEN", false) ?: false
        currentTarget = LockTarget(
            packageName = targetPackage,
            appLabel = appLabel,
            isHidden = isHidden,
            instanceNonce = System.nanoTime()
        )
    }
}

@Composable
fun LockScreenContent(
    target: LockTarget,
    settings: SecuritySettings,
    onUnlockSuccess: () -> Unit,
    activity: FragmentActivity,
    securePrefs: SecurePreferences = remember(activity) { SecurePreferences.getInstance(activity) }
) {
    val haptic = LocalHapticFeedback.current

    fun goToHome() {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        activity.startActivity(homeIntent)
        activity.finish()
    }

    BackHandler { goToHome() }

    var stage by remember(
        target.packageName,
        target.isHidden,
        target.instanceNonce,
        settings.scrambleKeypadEnabled,
        settings.unlockMethod,
        settings.masterPattern,
        settings.masterPassword
    ) {
        mutableStateOf(LockStateMachine.revealInitial(target, settings))
    }
    var recoveryState by remember(target.packageName, target.instanceNonce) {
        mutableStateOf<LockRecoveryDialogState>(LockRecoveryDialogState.Hidden)
    }
    val securityQuestion = remember { securePrefs.getSecurityQuestion() }

    val offsetX = remember { Animatable(0f) }
    val failedCount = stage.currentFailedAttempts

    LaunchedEffect(failedCount) {
        if (failedCount > 0) {
            offsetX.animateTo(
                targetValue = 0f,
                animationSpec = keyframes {
                    durationMillis = 360
                    0f at 0
                    20f at 45
                    -20f at 90
                    20f at 135
                    -20f at 180
                    14f at 225
                    -14f at 270
                    0f at 360
                }
            )
        }
    }

    val latestSettings by rememberUpdatedState(settings)

    LaunchedEffect(stage) {
        if (stage is LockScreenStage.Unlocked) {
            IntruderCameraCapture.stopActiveVideoIfRunning()
            AlarmAlertHelper.stopBreakInAlarm(activity)
            delay(280)
            onUnlockSuccess()
        }
    }

    fun handleEffects(
        effects: List<LockEffect>,
        authMethodOverride: String? = null,
        attemptCount: Int = stage.currentFailedAttempts
    ) {
        val activeSettings = latestSettings
        val resolvedMethod = authMethodOverride ?: when (stage) {
            is LockScreenStage.PatternAuthentication -> "Pattern"
            is LockScreenStage.PasswordAuthentication -> "Password"
            else -> "PIN"
        }
        val isUninstallTarget = activeSettings.uninstallProtectionEnabled &&
            LockSessionTracker.isUninstallProtectedPackage(target.packageName)
        effects.forEach { effect ->
            when (effect) {
                LockEffect.UnlockGranted -> {
                    IntruderCameraCapture.stopActiveVideoIfRunning()
                    AlarmAlertHelper.stopBreakInAlarm(activity)
                }
                LockEffect.TriggerIntruderCapture -> IntruderCameraCapture.takeIntruderSelfie(
                    activity = activity,
                    targetPackageName = target.packageName,
                    targetAppLabel = target.appLabel.ifBlank { "Locked App" },
                    authMethodLabel = resolvedMethod,
                    failedAttemptNumber = attemptCount.coerceAtLeast(1),
                    isUninstallProtection = isUninstallTarget
                )
                LockEffect.TriggerIntruderVideo -> IntruderCameraCapture.recordIntruderVideo(
                    activity = activity,
                    targetPackageName = target.packageName,
                    targetAppLabel = target.appLabel.ifBlank { "Locked App" },
                    authMethodLabel = resolvedMethod,
                    failedAttemptNumber = attemptCount.coerceAtLeast(3),
                    isUninstallProtection = isUninstallTarget
                )
                LockEffect.TriggerBreakInSiren -> AlarmAlertHelper.playBreakInAlarm(activity)
            }
        }
    }

    val showBiometricPrompt = remember(activity, target.appLabel, settings.unlockMethod) {
        {
            val executor = ContextCompat.getMainExecutor(activity)
            val biometricPrompt = BiometricPrompt(
                activity,
                executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        super.onAuthenticationError(errorCode, errString)
                        if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                            errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON
                        ) {
                            stage = when (val current = stage) {
                                is LockScreenStage.PinAuthentication -> current.copy(
                                    errorMessage = errString.toString(),
                                    biometricState = BiometricAuthState.IDLE
                                )
                                is LockScreenStage.PatternAuthentication -> current.copy(
                                    errorMessage = errString.toString(),
                                    biometricState = BiometricAuthState.IDLE
                                )
                                is LockScreenStage.PasswordAuthentication -> current.copy(
                                    errorMessage = errString.toString(),
                                    biometricState = BiometricAuthState.IDLE
                                )
                                else -> current
                            }
                        }
                    }

                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        super.onAuthenticationSucceeded(result)
                        stage = LockScreenStage.Unlocked
                    }

                    override fun onAuthenticationFailed() {
                        super.onAuthenticationFailed()
                        val outcome = LockStateMachine.onBiometricFailed(stage, latestSettings)
                        stage = outcome.stage
                        handleEffects(
                            effects = outcome.effects,
                            authMethodOverride = "Biometric",
                            attemptCount = outcome.stage.currentFailedAttempts
                        )
                    }
                }
            )

            val negativeLabel = when (settings.unlockMethod) {
                UnlockMethod.PIN -> "Use PIN"
                UnlockMethod.PATTERN -> "Use Pattern"
                UnlockMethod.PASSWORD -> "Use Password"
            }

            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock DASMO LOCK")
                .setSubtitle("Authenticate to access ${target.appLabel}")
                .setNegativeButtonText(negativeLabel)
                .build()

            biometricPrompt.authenticate(promptInfo)
        }
    }

    val isBiometricAvailable = remember(activity) {
        BiometricManager.from(activity).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }

    LaunchedEffect(stage::class, target.instanceNonce) {
        if (isBiometricAvailable &&
            stage.isAuthInputStage &&
            recoveryState is LockRecoveryDialogState.Hidden
        ) {
            showBiometricPrompt()
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, stage::class) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME &&
                isBiometricAvailable &&
                stage.isAuthInputStage &&
                recoveryState is LockRecoveryDialogState.Hidden
            ) {
                showBiometricPrompt()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    AnimatedContent(
        targetState = stage,
        contentKey = { it::class },
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "lock_stage_transition"
    ) { currentStage ->
        when (currentStage) {
            is LockScreenStage.FakeCrashDecoy -> {
                FakeCrashDecoyContent(
                    appLabel = target.appLabel,
                    onGoHome = ::goToHome,
                    onRevealPin = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        stage = LockStateMachine.revealPinFromCrashDecoy(settings)
                    }
                )
            }

            is LockScreenStage.PinAuthentication,
            is LockScreenStage.PatternAuthentication,
            is LockScreenStage.PasswordAuthentication,
            is LockScreenStage.Unlocked -> {
                val pinStage = currentStage as? LockScreenStage.PinAuthentication
                val patternStage = currentStage as? LockScreenStage.PatternAuthentication
                val passwordStage = currentStage as? LockScreenStage.PasswordAuthentication
                val isUnlocked = currentStage is LockScreenStage.Unlocked
                val errorText = currentStage.currentErrorMessage

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(ObsidianBlack, Color(0xFF130B24), ObsidianBlack)
                            )
                        )
                        .systemBarsPadding()
                        .imePadding()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    if (isUnlocked) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF064E3B),
                            border = BorderStroke(2.dp, NeonEmerald),
                            modifier = Modifier.size(84.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.LockOpen,
                                    contentDescription = "Unlocked",
                                    tint = NeonEmerald,
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                        }
                    } else {
                        val isUninstallTarget = settings.uninstallProtectionEnabled &&
                            LockSessionTracker.isUninstallProtectedPackage(target.packageName)
                        var targetAppIcon by remember(target.packageName) { mutableStateOf<ImageBitmap?>(null) }

                        LaunchedEffect(target.packageName, isUninstallTarget) {
                            targetAppIcon = if (target.packageName.isNotBlank() && !isUninstallTarget) {
                                withContext(Dispatchers.IO) {
                                    try {
                                        activity.packageManager.getApplicationIcon(target.packageName)
                                            .toBitmap(width = 160, height = 160)
                                            .asImageBitmap()
                                    } catch (_: Exception) {
                                        null
                                    }
                                }
                            } else {
                                null
                            }
                        }

                        Box(modifier = Modifier.size(92.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(86.dp)
                                    .align(Alignment.Center)
                                    .shadow(
                                        elevation = 18.dp,
                                        shape = RoundedCornerShape(24.dp),
                                        ambientColor = ElectricPurple.copy(alpha = 0.6f),
                                        spotColor = ElectricPurple.copy(alpha = 0.7f)
                                    )
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(ObsidianElevated)
                                    .border(1.5.dp, NeonViolet.copy(alpha = 0.7f), RoundedCornerShape(24.dp))
                            ) {
                                if (targetAppIcon != null) {
                                    Image(
                                        bitmap = targetAppIcon!!,
                                        contentDescription = "${target.appLabel} Icon",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(10.dp)
                                            .clip(RoundedCornerShape(16.dp))
                                    )
                                } else {
                                    Image(
                                        painter = painterResource(id = R.drawable.img_shield_3d),
                                        contentDescription = "DASMO Shield",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }

                            Surface(
                                shape = CircleShape,
                                color = if (isUninstallTarget) CrimsonAlert else ElectricPurple,
                                border = BorderStroke(2.dp, ObsidianBlack),
                                modifier = Modifier
                                    .size(30.dp)
                                    .align(Alignment.BottomEnd)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (isUninstallTarget) Icons.Default.AdminPanelSettings else Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = PureWhite,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = target.appLabel,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = PureWhite
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = when {
                            isUnlocked -> "Access Granted"
                            patternStage != null -> "Draw your unlock pattern"
                            passwordStage != null -> "Enter your master password"
                            else -> "DASMO LOCK • Enter Master PIN"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = NeonViolet
                    )

                    when {
                        patternStage != null -> {
                            Spacer(modifier = Modifier.height(18.dp))
                            if (!errorText.isNullOrBlank()) {
                                Text(
                                    text = errorText,
                                    color = CrimsonAlert,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier
                                        .offset(x = offsetX.value.dp)
                                        .testTag("lock_error_text")
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                            }

                            PatternLockView(
                                isError = !errorText.isNullOrBlank(),
                                resetKey = patternStage.failedAttempts to target.instanceNonce,
                                testTag = "lock_pattern_pad",
                                modifier = Modifier
                                    .fillMaxWidth(0.84f)
                                    .offset(x = offsetX.value.dp),
                                onPatternStart = {
                                    stage = LockStateMachine.onPatternStarted(stage)
                                },
                                onPatternComplete = { nodes ->
                                    val res = LockStateMachine.onPatternCompleted(stage, nodes, settings)
                                    stage = res.stage
                                    handleEffects(
                                        effects = res.effects,
                                        authMethodOverride = "Pattern",
                                        attemptCount = res.stage.currentFailedAttempts
                                    )
                                }
                            )

                            if (isBiometricAvailable) {
                                Spacer(modifier = Modifier.height(14.dp))
                                OutlinedButton(
                                    onClick = { showBiometricPrompt() },
                                    border = BorderStroke(1.dp, ElectricPurple.copy(alpha = 0.7f)),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .height(48.dp)
                                        .testTag("lock_biometric_btn")
                                ) {
                                    Icon(Icons.Default.Fingerprint, contentDescription = null, tint = NeonViolet)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Use Fingerprint / Face", color = LavenderGlow)
                                }
                            }
                        }

                        passwordStage != null -> {
                            Spacer(modifier = Modifier.height(22.dp))
                            OutlinedTextField(
                                value = passwordStage.enteredPassword,
                                onValueChange = { stage = LockStateMachine.onPasswordChanged(stage, it) },
                                label = { Text("Master Password") },
                                singleLine = true,
                                isError = !errorText.isNullOrBlank(),
                                visualTransformation = if (passwordStage.passwordVisible) {
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
                                        val res = LockStateMachine.onPasswordSubmitted(stage, settings)
                                        stage = res.stage
                                        handleEffects(
                                            effects = res.effects,
                                            authMethodOverride = "Password",
                                            attemptCount = res.stage.currentFailedAttempts
                                        )
                                    }
                                ),
                                trailingIcon = {
                                    IconButton(
                                        onClick = { stage = LockStateMachine.onTogglePasswordVisibility(stage) },
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (passwordStage.passwordVisible) {
                                                Icons.Default.VisibilityOff
                                            } else {
                                                Icons.Default.Visibility
                                            },
                                            contentDescription = if (passwordStage.passwordVisible) "Hide Password" else "Show Password",
                                            tint = NeonViolet
                                        )
                                    }
                                },
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .widthIn(max = 360.dp)
                                    .offset(x = offsetX.value.dp)
                                    .testTag("lock_password_input")
                            )

                            if (!errorText.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = errorText,
                                    color = CrimsonAlert,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.testTag("lock_error_text")
                                )
                            }

                            Spacer(modifier = Modifier.height(18.dp))
                            Button(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    val res = LockStateMachine.onPasswordSubmitted(stage, settings)
                                    stage = res.stage
                                    handleEffects(
                                        effects = res.effects,
                                        authMethodOverride = "Password",
                                        attemptCount = res.stage.currentFailedAttempts
                                    )
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ElectricPurple,
                                    contentColor = PureWhite
                                ),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .widthIn(max = 360.dp)
                                    .height(52.dp)
                                    .testTag("lock_password_submit_btn")
                            ) {
                                Text("Unlock ${target.appLabel}", fontWeight = FontWeight.Bold)
                            }

                            if (isBiometricAvailable) {
                                Spacer(modifier = Modifier.height(12.dp))
                                OutlinedButton(
                                    onClick = { showBiometricPrompt() },
                                    border = BorderStroke(1.dp, ElectricPurple.copy(alpha = 0.7f)),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .height(48.dp)
                                        .testTag("lock_biometric_btn")
                                ) {
                                    Icon(Icons.Default.Fingerprint, contentDescription = null, tint = NeonViolet)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Use Fingerprint / Face", color = LavenderGlow)
                                }
                            }
                        }

                        else -> {
                            val enteredLength = if (isUnlocked) 4 else pinStage?.enteredPin?.length ?: 0

                            Spacer(modifier = Modifier.height(26.dp))

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(18.dp),
                                modifier = Modifier
                                    .offset(x = offsetX.value.dp)
                                    .testTag("lock_pin_dots")
                            ) {
                                for (i in 0 until 4) {
                                    val isFilled = i < enteredLength
                                    Box(
                                        modifier = Modifier
                                            .size(18.dp)
                                            .clip(CircleShape)
                                            .background(if (isFilled) NeonViolet else ObsidianSurfaceVariant)
                                            .border(
                                                width = 1.dp,
                                                color = if (isFilled) PureWhite.copy(alpha = 0.7f) else ObsidianBorder,
                                                shape = CircleShape
                                            )
                                    )
                                }
                            }

                            if (!errorText.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    text = errorText,
                                    color = CrimsonAlert,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.testTag("lock_error_text")
                                )
                            }

                            if (settings.scrambleKeypadEnabled || settings.dynamicTimePinEnabled) {
                                Spacer(modifier = Modifier.height(14.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (settings.scrambleKeypadEnabled) {
                                        Surface(
                                            color = ContainerPurple,
                                            shape = CircleShape,
                                            border = BorderStroke(1.dp, ElectricPurple.copy(alpha = 0.6f))
                                        ) {
                                            Text(
                                                "Anti-Peep Keypad",
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = LavenderGlow
                                            )
                                        }
                                    }
                                    if (settings.dynamicTimePinEnabled) {
                                        Surface(
                                            color = ContainerPurple,
                                            shape = CircleShape,
                                            border = BorderStroke(1.dp, ElectricPurple.copy(alpha = 0.6f))
                                        ) {
                                            Text(
                                                "Time PIN Active",
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = LavenderGlow
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(24.dp))

                            val digits = pinStage?.keypadDigits ?: LockScreenStage.DEFAULT_DIGITS
                            val rows = remember(digits) {
                                listOf(
                                    listOf(digits[0], digits[1], digits[2]),
                                    listOf(digits[3], digits[4], digits[5]),
                                    listOf(digits[6], digits[7], digits[8]),
                                    listOf("FP", digits[9], "<")
                                )
                            }

                            rows.forEach { row ->
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(22.dp),
                                    modifier = Modifier.padding(vertical = 8.dp)
                                ) {
                                    row.forEach { key ->
                                        if (key == "FP" && !isBiometricAvailable) {
                                            Spacer(modifier = Modifier.size(72.dp))
                                        } else {
                                            val isFp = key == "FP"
                                            val isBack = key == "<"
                                            val interactionSource = remember { MutableInteractionSource() }
                                            val pressed by interactionSource.collectIsPressedAsState()

                                            Surface(
                                                onClick = {
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    when (key) {
                                                        "<" -> stage = LockStateMachine.onBackspacePressed(stage)
                                                        "FP" -> showBiometricPrompt()
                                                        else -> {
                                                            val res = LockStateMachine.onDigitPressed(stage, key, settings)
                                                            stage = res.stage
                                                            handleEffects(
                                                                effects = res.effects,
                                                                authMethodOverride = "PIN",
                                                                attemptCount = res.stage.currentFailedAttempts
                                                            )
                                                        }
                                                    }
                                                },
                                                interactionSource = interactionSource,
                                                modifier = Modifier
                                                    .size(72.dp)
                                                    .tactileScale(pressed = pressed, pressedScale = 0.9f)
                                                    .testTag("lock_key_$key"),
                                                shape = CircleShape,
                                                color = when {
                                                    isFp -> ContainerPurple
                                                    isBack -> ObsidianElevated
                                                    else -> ObsidianSurface
                                                },
                                                contentColor = if (isFp) NeonViolet else PureWhite,
                                                border = BorderStroke(
                                                    1.dp,
                                                    if (isFp) ElectricPurple else ObsidianBorder
                                                ),
                                                shadowElevation = 6.dp
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    if (isFp) {
                                                        Icon(
                                                            imageVector = Icons.Default.Fingerprint,
                                                            contentDescription = "Biometric",
                                                            modifier = Modifier.size(32.dp)
                                                        )
                                                    } else {
                                                        Text(
                                                            text = key,
                                                            fontSize = 24.sp,
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
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    TextButton(
                        onClick = { recoveryState = LockRecoveryDialogState.Visible() },
                        modifier = Modifier.testTag("lock_forgot_pin_btn")
                    ) {
                        Text(
                            text = when (settings.unlockMethod) {
                                UnlockMethod.PIN -> "Forgot PIN? / Emergency Recovery"
                                UnlockMethod.PATTERN -> "Forgot Pattern? / Emergency Recovery"
                                UnlockMethod.PASSWORD -> "Forgot Password? / Emergency Recovery"
                            },
                            color = NeonViolet,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }

    val recoveryModal = recoveryState
    if (recoveryModal is LockRecoveryDialogState.Visible) {
        AlertDialog(
            onDismissRequest = { recoveryState = LockRecoveryDialogState.Hidden },
            containerColor = ObsidianSurface,
            title = {
                Text(
                    text = "Emergency Recovery",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = PureWhite
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Unlock ${target.appLabel} using your security answer or Universal Master Key.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    if (securityQuestion.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = ObsidianElevated,
                            border = BorderStroke(1.dp, ObsidianBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "Security Question:",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = NeonViolet
                                )
                                Text(
                                    text = securityQuestion,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = PureWhite
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = recoveryModal.input,
                        onValueChange = {
                            recoveryState = recoveryModal.copy(input = it, error = null)
                        },
                        label = { Text(if (securityQuestion.isNotBlank()) "Security Answer or Master Key" else "Master Recovery Key") },
                        singleLine = true,
                        isError = recoveryModal.error != null,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (recoveryModal.error != null) {
                        Text(
                            text = recoveryModal.error,
                            color = CrimsonAlert,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val isMaster = MasterKeyManager.isMasterKey(recoveryModal.input)
                        val isAnswer = securityQuestion.isNotBlank() &&
                            securePrefs.verifySecurityAnswer(recoveryModal.input)
                        if (isMaster || isAnswer) {
                            recoveryState = LockRecoveryDialogState.Hidden
                            stage = LockScreenStage.Unlocked
                        } else {
                            recoveryState = recoveryModal.copy(
                                error = "Invalid recovery credentials. Access Denied."
                            )
                        }
                    }
                ) {
                    Text("Unlock App")
                }
            },
            dismissButton = {
                TextButton(onClick = { recoveryState = LockRecoveryDialogState.Hidden }) {
                    Text("Cancel")
                }
            }
        )
    }
}

private fun LockStateMachine.revealInitial(
    target: LockTarget,
    settings: SecuritySettings
): LockScreenStage = LockScreenStage.initialFor(target, settings)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FakeCrashDecoyContent(
    appLabel: String,
    onGoHome: () -> Unit,
    onRevealPin: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0A10)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp)
                .background(Color(0xFF1A1822), shape = RoundedCornerShape(18.dp))
                .border(1.dp, Color(0xFF2B2738), RoundedCornerShape(18.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .combinedClickable(
                        onClick = {},
                        onLongClick = onRevealPin
                    )
                    .testTag("fake_crash_warning_icon"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Error",
                    tint = Color(0xFFE57373),
                    modifier = Modifier.size(44.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "$appLabel keeps stopping",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Unfortunately, $appLabel has stopped. You can try closing it or sending feedback.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFB0BEC5),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(22.dp))
            HorizontalDivider(color = Color(0xFF2C2938))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                TextButton(
                    onClick = onGoHome,
                    modifier = Modifier.testTag("fake_crash_feedback_btn")
                ) {
                    Text("Send feedback", color = Color(0xFF64B5F6))
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .combinedClickable(
                            onClick = onGoHome,
                            onLongClick = onRevealPin
                        )
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                        .testTag("fake_crash_close_btn"),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Close app", color = Color(0xFF64B5F6), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
