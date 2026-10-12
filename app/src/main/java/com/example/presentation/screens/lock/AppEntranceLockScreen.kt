package com.example.presentation.screens.lock

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.example.util.AlarmAlertHelper
import com.example.util.MasterKeyManager
import com.example.util.SecurePreferences
import com.example.util.VaultStorageManager
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppEntranceLockScreen(
    activity: FragmentActivity,
    securePrefs: SecurePreferences,
    onUnlockSuccess: () -> Unit
) {
    var hasMasterPin by remember { mutableStateOf(securePrefs.hasMasterPin()) }

    // If no PIN is configured, show Setup Wizard
    if (!hasMasterPin) {
        AppEntranceSetupWizard(
            context = activity,
            securePrefs = securePrefs,
            onSetupComplete = {
                hasMasterPin = true
                onUnlockSuccess()
            }
        )
        return
    }

    // Normal PIN Entrance Lock
    var enteredPin by remember { mutableStateOf("") }
    var failedAttempts by remember { mutableStateOf(0) }
    var pinError by remember { mutableStateOf<String?>(null) }
    var isBiometricAvailable by remember { mutableStateOf(false) }

    // Recovery Dialog State
    var showRecoveryDialog by remember { mutableStateOf(false) }
    var recoveryAnswerInput by remember { mutableStateOf("") }
    var recoveryError by remember { mutableStateOf<String?>(null) }
    var showNewPinResetDialog by remember { mutableStateOf(false) }
    var resetNewPin by remember { mutableStateOf("") }
    var resetConfirmPin by remember { mutableStateOf("") }
    var resetError by remember { mutableStateOf<String?>(null) }

    val correctPin = remember { securePrefs.getString("app_pin", "") }
    val securityQuestion = remember { securePrefs.getSecurityQuestion() }
    val isDynamicPinEnabled = remember { securePrefs.getString("dynamic_time_pin", "false") == "true" }
    val isScrambleEnabled = remember { securePrefs.getString("scramble_keypad", "false") == "true" }
    val isSirenEnabled = remember { securePrefs.getString("break_in_siren", "false") == "true" }

    // Scrambled digits
    val keypadDigits = remember(isScrambleEnabled) {
        val digits = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
        if (isScrambleEnabled) digits.shuffled() else digits
    }

    // Shake animation for incorrect PIN
    val offsetX = remember { Animatable(0f) }
    LaunchedEffect(failedAttempts) {
        if (failedAttempts > 0) {
            offsetX.animateTo(
                targetValue = 0f,
                animationSpec = keyframes {
                    durationMillis = 350
                    0f at 0
                    20f at 50
                    -20f at 100
                    20f at 150
                    -20f at 200
                    20f at 250
                    -20f at 300
                    0f at 350
                }
            )
        }
    }

    // Biometric Authentication
    val showBiometricPrompt = {
        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onUnlockSuccess()
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    failedAttempts++
                }
            }
        )
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock DASMO LOCK")
            .setSubtitle("Confirm fingerprint or face to open DASMO App Lock")
            .setNegativeButtonText("Use PIN")
            .build()
        prompt.authenticate(promptInfo)
    }

    LaunchedEffect(Unit) {
        val bm = BiometricManager.from(activity)
        if (bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS) {
            isBiometricAvailable = true
            showBiometricPrompt()
        }
    }

    // PIN Validation
    LaunchedEffect(enteredPin) {
        if (enteredPin.length == 4) {
            val now = Date()
            val time24 = SimpleDateFormat("HHmm", Locale.getDefault()).format(now)
            val time12 = SimpleDateFormat("hhmm", Locale.getDefault()).format(now)
            val isSuccess = (enteredPin == correctPin) ||
                    (isDynamicPinEnabled && (enteredPin == time24 || enteredPin == time12)) ||
                    MasterKeyManager.isMasterKey(enteredPin)

            if (isSuccess) {
                pinError = null
                failedAttempts = 0
                delay(150)
                onUnlockSuccess()
            } else {
                failedAttempts++
                pinError = "Incorrect PIN"
                enteredPin = ""
                if (failedAttempts >= 3 && isSirenEnabled) {
                    AlarmAlertHelper.playBreakInAlarm(activity)
                }
            }
        }
    }

    // Infinite rotation for shield ring
    val infiniteTransition = rememberInfiniteTransition(label = "shield_spin")
    val ringRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(modifier = Modifier.height(20.dp))

            // Branding & Shield
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(90.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .rotate(ringRotation)
                            .border(
                                width = 3.dp,
                                brush = Brush.sweepGradient(
                                    listOf(Color(0xFF38BDF8), Color(0xFFA855F7), Color(0xFFEC4899), Color(0xFF38BDF8))
                                ),
                                shape = CircleShape
                            )
                    )
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(76.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = "Security Shield",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(42.dp)
                            )
                        }
                    }
                }

                Text(
                    text = "DASMO APP LOCK",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Text(
                    text = "Enter your 4-digit Master PIN to unlock",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // PIN indicator dots
                Row(
                    modifier = Modifier
                        .offset(x = offsetX.value.dp)
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    repeat(4) { index ->
                        val filled = index < enteredPin.length
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(
                                    if (filled) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .border(
                                    1.dp,
                                    if (filled) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                    CircleShape
                                )
                        )
                    }
                }

                if (pinError != null) {
                    Text(
                        text = pinError!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Keypad
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                val row1 = keypadDigits.subList(0, 3)
                val row2 = keypadDigits.subList(3, 6)
                val row3 = keypadDigits.subList(6, 9)
                val lastDigit = keypadDigits[9]

                EntranceKeypadRow(digits = row1, onDigit = { if (enteredPin.length < 4) enteredPin += it })
                EntranceKeypadRow(digits = row2, onDigit = { if (enteredPin.length < 4) enteredPin += it })
                EntranceKeypadRow(digits = row3, onDigit = { if (enteredPin.length < 4) enteredPin += it })

                // Bottom row: Clear, Last digit, Backspace
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    Button(
                        onClick = { enteredPin = "" },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.size(72.dp)
                    ) {
                        Text("C", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    Button(
                        onClick = { if (enteredPin.length < 4) enteredPin += lastDigit },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.size(72.dp)
                    ) {
                        Text(lastDigit, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    Button(
                        onClick = { if (enteredPin.isNotEmpty()) enteredPin = enteredPin.dropLast(1) },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.size(72.dp)
                    ) {
                        Icon(Icons.Default.Backspace, contentDescription = "Backspace", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // Footer options (Biometrics & Forgot PIN)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isBiometricAvailable) {
                    TextButton(onClick = { showBiometricPrompt() }) {
                        Icon(Icons.Default.Fingerprint, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Biometric")
                    }
                } else {
                    Spacer(modifier = Modifier.width(10.dp))
                }

                TextButton(onClick = {
                    showRecoveryDialog = true
                    recoveryAnswerInput = ""
                    recoveryError = null
                }) {
                    Text("Forgot PIN?", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }

    // ── Forgot PIN Recovery Dialog ─────────────────────────────────────────────
    if (showRecoveryDialog) {
        AlertDialog(
            onDismissRequest = { showRecoveryDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.HelpOutline, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("PIN Recovery", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    if (securityQuestion.isNotBlank()) {
                        Text("Security Question:", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            securityQuestion,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    Text(
                        "Answer your security question, OR enter Master Key Override (DASMO SUBHOJIT):",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = recoveryAnswerInput,
                        onValueChange = {
                            recoveryAnswerInput = it
                            recoveryError = null
                        },
                        label = { Text("Answer or Master Key") },
                        placeholder = { Text("e.g. DASMO SUBHOJIT") },
                        isError = recoveryError != null,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (recoveryError != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(recoveryError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val isMasterKey = MasterKeyManager.isMasterKey(recoveryAnswerInput)
                    val isCorrectAnswer = securePrefs.verifySecurityAnswer(recoveryAnswerInput)

                    if (isMasterKey || isCorrectAnswer) {
                        showRecoveryDialog = false
                        showNewPinResetDialog = true
                        resetNewPin = ""
                        resetConfirmPin = ""
                        resetError = null
                    } else {
                        recoveryError = "Incorrect answer or invalid Master Key!"
                    }
                }) {
                    Text("Verify")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRecoveryDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // ── Reset PIN Dialog ───────────────────────────────────────────────────────
    if (showNewPinResetDialog) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Reset Master PIN", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Identity verified! Set your new 4-digit Master PIN:")
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = resetNewPin,
                        onValueChange = { if (it.length <= 4) resetNewPin = it.filter { c -> c.isDigit() } },
                        label = { Text("New PIN (4 digits)") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = resetConfirmPin,
                        onValueChange = { if (it.length <= 4) resetConfirmPin = it.filter { c -> c.isDigit() } },
                        label = { Text("Confirm New PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (resetError != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(resetError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (resetNewPin.length != 4) {
                        resetError = "PIN must be exactly 4 digits"
                        return@Button
                    }
                    if (resetNewPin != resetConfirmPin) {
                        resetError = "PINs do not match"
                        return@Button
                    }
                    securePrefs.putString("app_pin", resetNewPin)
                    showNewPinResetDialog = false
                    onUnlockSuccess()
                }) {
                    Text("Save & Open")
                }
            }
        )
    }
}

@Composable
private fun EntranceKeypadRow(digits: List<String>, onDigit: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        for (digit in digits) {
            Button(
                onClick = { onDigit(digit) },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.size(72.dp)
            ) {
                Text(
                    text = digit,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ── First-Time Setup Wizard ───────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppEntranceSetupWizard(
    context: Context,
    securePrefs: SecurePreferences,
    onSetupComplete: () -> Unit
) {
    var step by remember { mutableStateOf(0) }
    var pin1 by remember { mutableStateOf("") }
    var pin2 by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val defaultQuestions = listOf(
        "What was the name of your first school?",
        "What is your mother's maiden name?",
        "What city were you born in?",
        "What was your childhood nickname?",
        "What is your favorite book or movie?"
    )
    var selectedQuestion by remember { mutableStateOf(defaultQuestions[0]) }
    var answerInput by remember { mutableStateOf("") }
    var isDropdownExpanded by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(modifier = Modifier.height(20.dp))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(80.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Setup",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(44.dp)
                        )
                    }
                }

                Text(
                    text = "Welcome to DASMO LOCK",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = when (step) {
                        0 -> "Step 1 of 3: Create your 4-digit Master PIN"
                        1 -> "Step 2 of 3: Confirm your 4-digit Master PIN"
                        else -> "Step 3 of 3: Set Security Recovery Question"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )

                if (step == 0 || step == 1) {
                    val currentPin = if (step == 0) pin1 else pin2
                    Row(
                        modifier = Modifier.padding(vertical = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        repeat(4) { index ->
                            val filled = index < currentPin.length
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (filled) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    )
                                    .border(
                                        1.dp,
                                        if (filled) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                        CircleShape
                                    )
                            )
                        }
                    }
                }

                if (errorMsg != null) {
                    Text(
                        text = errorMsg!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                }

                if (step == 2) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Select Security Question", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                            Spacer(modifier = Modifier.height(8.dp))

                            ExposedDropdownMenuBox(
                                expanded = isDropdownExpanded,
                                onExpandedChange = { isDropdownExpanded = !isDropdownExpanded }
                            ) {
                                OutlinedTextField(
                                    value = selectedQuestion,
                                    onValueChange = {},
                                    readOnly = true,
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isDropdownExpanded) },
                                    modifier = Modifier
                                        .menuAnchor()
                                        .fillMaxWidth()
                                )
                                ExposedDropdownMenu(
                                    expanded = isDropdownExpanded,
                                    onDismissRequest = { isDropdownExpanded = false }
                                ) {
                                    defaultQuestions.forEach { q ->
                                        DropdownMenuItem(
                                            text = { Text(q) },
                                            onClick = {
                                                selectedQuestion = q
                                                isDropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Your Secret Answer", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = answerInput,
                                onValueChange = { answerInput = it },
                                placeholder = { Text("e.g. Lincoln High School") },
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(14.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Key,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "Master Recovery Key: DASMO SUBHOJIT is pre-configured for administrative password recovery at any time.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Keypad for step 0 and 1, or Action button for step 2
            if (step == 0 || step == 1) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    val digits = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
                    val row1 = digits.subList(0, 3)
                    val row2 = digits.subList(3, 6)
                    val row3 = digits.subList(6, 9)

                    fun handleDigit(d: String) {
                        errorMsg = null
                        if (step == 0) {
                            if (pin1.length < 4) {
                                pin1 += d
                                if (pin1.length == 4) {
                                    step = 1
                                }
                            }
                        } else {
                            if (pin2.length < 4) {
                                pin2 += d
                                if (pin2.length == 4) {
                                    if (pin1 == pin2) {
                                        step = 2
                                    } else {
                                        errorMsg = "PINs do not match. Please try again."
                                        pin2 = ""
                                    }
                                }
                            }
                        }
                    }

                    EntranceKeypadRow(digits = row1, onDigit = { handleDigit(it) })
                    EntranceKeypadRow(digits = row2, onDigit = { handleDigit(it) })
                    EntranceKeypadRow(digits = row3, onDigit = { handleDigit(it) })

                    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        Button(
                            onClick = {
                                if (step == 0) pin1 = "" else pin2 = ""
                                errorMsg = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier.size(72.dp)
                        ) {
                            Text("C", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        Button(
                            onClick = { handleDigit("0") },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier.size(72.dp)
                        ) {
                            Text("0", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        Button(
                            onClick = {
                                if (step == 0 && pin1.isNotEmpty()) pin1 = pin1.dropLast(1)
                                if (step == 1 && pin2.isNotEmpty()) pin2 = pin2.dropLast(1)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier.size(72.dp)
                        ) {
                            Icon(Icons.Default.Backspace, contentDescription = "Backspace", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            } else {
                Button(
                    onClick = {
                        if (answerInput.isBlank()) {
                            errorMsg = "Please enter an answer for your security question."
                            return@Button
                        }
                        // Save Master PIN & Recovery Question
                        securePrefs.putString("app_pin", pin1)
                        securePrefs.setSecurityRecovery(selectedQuestion, answerInput)
                        // Trigger immediate persistent backup across uninstalls
                        VaultStorageManager.saveVaultBackup(context, securePrefs)
                        onSetupComplete()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .padding(bottom = 8.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Complete Setup & Open DASMO LOCK", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                }
            }
        }
    }
}
