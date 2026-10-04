package com.example.presentation.screens.lock

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.lifecycle.LifecycleOwner
import java.io.File
import android.util.Log
import com.example.service.AppLockService
import com.example.ui.theme.MyApplicationTheme
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.text.style.TextAlign
import com.example.util.AlarmAlertHelper
import com.example.util.SecurePreferences
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.view.WindowManager

fun takeIntruderSelfie(activity: FragmentActivity) {
    if (ContextCompat.checkSelfPermission(activity, android.Manifest.permission.CAMERA) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
        Log.e("Intruder", "Camera permission not granted")
        return
    }

    val cameraProviderFuture = ProcessCameraProvider.getInstance(activity)
    cameraProviderFuture.addListener({
        try {
            val cameraProvider = cameraProviderFuture.get()
            val imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()
            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            cameraProvider.unbindAll()
            
            if (activity.isFinishing || activity.isDestroyed) {
                Log.e("Intruder", "Activity is finishing or destroyed, cannot bind camera")
                return@addListener
            }

            cameraProvider.bindToLifecycle(activity as LifecycleOwner, cameraSelector, imageCapture)

            val dir = File(activity.getDir("intruders", android.content.Context.MODE_PRIVATE).absolutePath)
            if (!dir.exists()) dir.mkdirs()
            
            val file = File(dir, "intruder_${System.currentTimeMillis()}.jpg")
            val outputOptions = ImageCapture.OutputFileOptions.Builder(file).build()

            // Delay capture slightly to allow the camera device to initialize/open properly
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                try {
                    if (activity.isFinishing || activity.isDestroyed) {
                        Log.e("Intruder", "Activity destroyed before capture delayed runnable")
                        cameraProvider.unbindAll()
                        return@postDelayed
                    }
                    imageCapture.takePicture(
                        outputOptions,
                        ContextCompat.getMainExecutor(activity),
                        object : ImageCapture.OnImageSavedCallback {
                            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                Log.d("Intruder", "Photo saved: ${file.absolutePath}")
                                cameraProvider.unbindAll()
                            }
                            override fun onError(exc: ImageCaptureException) {
                                Log.e("Intruder", "Photo capture failed: ${exc.message}", exc)
                                cameraProvider.unbindAll()
                            }
                        }
                    )
                } catch(exc: Exception) {
                    Log.e("Intruder", "Error in postDelayed takePicture: ${exc.message}", exc)
                    cameraProvider.unbindAll()
                }
            }, 800) // 800ms delay for safe camera session warm-up
        } catch(exc: Exception) {
            Log.e("Intruder", "Use case binding failed", exc)
        }
    }, ContextCompat.getMainExecutor(activity))
}

class LockActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val securePrefs = SecurePreferences(this)
        if (securePrefs.getString("prevent_screenshots", "true") == "true") {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
        }
        val targetPackage = intent.getStringExtra("TARGET_PACKAGE") ?: ""
        val appLabel = intent.getStringExtra("APP_LABEL") ?: targetPackage
        val isHidden = intent.getBooleanExtra("IS_HIDDEN", false)

        setContent {
            MyApplicationTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    LockScreenContent(
                        modifier = Modifier.padding(innerPadding),
                        packageName = targetPackage,
                        appLabel = appLabel,
                        isHidden = isHidden,
                        onUnlockSuccess = {
                            AppLockService.unlockedApps.add(targetPackage)
                            AppLockService.lastBackgroundTime.remove(targetPackage)
                            finish()
                        },
                        activity = this
                    )
                }
            }
        }
    }
}

@Composable
fun LockScreenContent(modifier: Modifier = Modifier, packageName: String, appLabel: String, isHidden: Boolean = false, onUnlockSuccess: () -> Unit, activity: FragmentActivity) {
    val goToHome = {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        activity.startActivity(homeIntent)
        activity.finish()
    }

    BackHandler {
        goToHome()
    }

    var pin by remember { mutableStateOf("") }
    var showFakeCrash by remember { mutableStateOf(isHidden) }
    val securePrefs = remember { SecurePreferences(activity) }
    val correctPin = securePrefs.getString("app_pin", "1234")
    val isDynamicPinEnabled = remember { securePrefs.getString("dynamic_time_pin", "false") == "true" }
    val isScrambleEnabled = remember { securePrefs.getString("scramble_keypad", "false") == "true" }
    val isSirenEnabled = remember { securePrefs.getString("break_in_siren", "false") == "true" }

    var biometricError by remember { mutableStateOf<String?>(null) }
    var isBiometricAvailable by remember { mutableStateOf(false) }
    var failedAttempts by remember { mutableStateOf(0) }

    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        isVisible = true
    }
    val alpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 500),
        label = "alpha_anim"
    )

    val offsetX = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(failedAttempts) {
        if (failedAttempts > 0) {
            offsetX.animateTo(
                targetValue = 0f,
                animationSpec = androidx.compose.animation.core.keyframes {
                    durationMillis = 400
                    0f at 0
                    20f at 50
                    -20f at 100
                    20f at 150
                    -20f at 200
                    20f at 250
                    -20f at 300
                    0f at 400
                }
            )
        }
    }

    LaunchedEffect(pin) {
        if (pin.length == 4) {
            val now = Date()
            val time24 = SimpleDateFormat("HHmm", Locale.getDefault()).format(now)
            val time12 = SimpleDateFormat("hhmm", Locale.getDefault()).format(now)
            val isSuccess = (pin == correctPin) || (isDynamicPinEnabled && (pin == time24 || pin == time12))

            if (isSuccess) {
                failedAttempts = 0
                onUnlockSuccess()
            } else {
                failedAttempts++
                pin = "" // Reset on fail
                takeIntruderSelfie(activity)
                if (failedAttempts >= 3 && isSirenEnabled) {
                    AlarmAlertHelper.playBreakInAlarm(activity)
                }
            }
        }
    }

    val showBiometricPrompt = {
        biometricError = null
        val executor = ContextCompat.getMainExecutor(activity)
        val biometricPrompt = BiometricPrompt(activity, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    if (errorCode != BiometricPrompt.ERROR_USER_CANCELED && errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                        biometricError = errString.toString()
                    }
                }

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onUnlockSuccess()
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    biometricError = "Authentication failed"
                    failedAttempts++
                    if (failedAttempts >= 1) {
                        takeIntruderSelfie(activity)
                        failedAttempts = 0
                    }
                }
            })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock DASMO LOCK")
            .setSubtitle("Authenticate to access $appLabel")
            .setNegativeButtonText("Use PIN")
            .build()

        biometricPrompt.authenticate(promptInfo)
    }

    LaunchedEffect(Unit) {
        if (!showFakeCrash) {
            val biometricManager = BiometricManager.from(activity)
            if (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS) {
                isBiometricAvailable = true
                showBiometricPrompt()
            }
        }
    }

    if (showFakeCrash) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(androidx.compose.ui.graphics.Color(0xFF121212)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp)
                    .background(androidx.compose.ui.graphics.Color(0xFF1E1E1E), shape = RoundedCornerShape(16.dp))
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Error",
                    tint = androidx.compose.ui.graphics.Color(0xFFE57373),
                    modifier = Modifier
                        .size(48.dp)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onLongPress = {
                                    showFakeCrash = false
                                }
                            )
                        }
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "$appLabel keeps stopping",
                    style = MaterialTheme.typography.titleMedium,
                    color = androidx.compose.ui.graphics.Color.White,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Unfortunately, $appLabel has stopped. You can try closing it or sending feedback.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = androidx.compose.ui.graphics.Color(0xFFB0BEC5),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                
                HorizontalDivider(color = androidx.compose.ui.graphics.Color(0xFF2C2C2C))
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    TextButton(
                        onClick = { goToHome() }
                    ) {
                        Text("Send feedback", color = androidx.compose.ui.graphics.Color(0xFF64B5F6))
                    }
                    TextButton(
                        onClick = { goToHome() },
                        modifier = Modifier.pointerInput(Unit) {
                            detectTapGestures(
                                onLongPress = {
                                    showFakeCrash = false
                                },
                                onTap = {
                                    goToHome()
                                }
                            )
                        }
                    ) {
                        Text("Close app", color = androidx.compose.ui.graphics.Color(0xFF64B5F6), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    } else {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(androidx.compose.ui.graphics.Color(0xFF09070F))
                .alpha(alpha),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                shape = CircleShape,
                color = androidx.compose.ui.graphics.Color(0xFF1E1333),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, androidx.compose.ui.graphics.Color(0xFF7E22CE)),
                modifier = Modifier.size(68.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Lock",
                        tint = androidx.compose.ui.graphics.Color(0xFFC084FC),
                        modifier = Modifier.size(34.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = appLabel,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = androidx.compose.ui.graphics.Color(0xFFFDFCFF)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "DASMO LOCK • Protected",
                fontSize = 13.sp,
                color = androidx.compose.ui.graphics.Color(0xFFA855F7),
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(32.dp))
            
            // PIN Indicators
            Row(
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                modifier = Modifier.offset(x = offsetX.value.dp)
            ) {
                for (i in 0 until 4) {
                    val isFilled = i < pin.length
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .background(
                                color = if (isFilled) androidx.compose.ui.graphics.Color(0xFFC084FC) else androidx.compose.ui.graphics.Color(0xFF161028),
                                shape = CircleShape
                            )
                            .then(
                                if (!isFilled) {
                                    Modifier.background(
                                        color = androidx.compose.ui.graphics.Color(0xFF161028),
                                        shape = CircleShape
                                    )
                                } else Modifier
                            )
                    )
                }
            }
            
            if (biometricError != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = biometricError!!, color = androidx.compose.ui.graphics.Color(0xFFF43F5E))
            }

            if (isScrambleEnabled || isDynamicPinEnabled) {
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isScrambleEnabled) {
                        AssistChip(
                            onClick = {},
                            label = { Text("Anti-Peep Keypad", style = MaterialTheme.typography.labelSmall, color = androidx.compose.ui.graphics.Color(0xFFE9D5FF)) },
                            colors = AssistChipDefaults.assistChipColors(containerColor = androidx.compose.ui.graphics.Color(0xFF261245)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, androidx.compose.ui.graphics.Color(0xFF6B21A8))
                        )
                    }
                    if (isDynamicPinEnabled) {
                        AssistChip(
                            onClick = {},
                            label = { Text("Time PIN Active", style = MaterialTheme.typography.labelSmall, color = androidx.compose.ui.graphics.Color(0xFFE9D5FF)) },
                            colors = AssistChipDefaults.assistChipColors(containerColor = androidx.compose.ui.graphics.Color(0xFF261245)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, androidx.compose.ui.graphics.Color(0xFF6B21A8))
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Numpad
            val shuffledDigits = remember {
                if (isScrambleEnabled) {
                    listOf("0", "1", "2", "3", "4", "5", "6", "7", "8", "9").shuffled()
                } else {
                    listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
                }
            }

            val keys = remember(shuffledDigits) {
                if (isScrambleEnabled) {
                    listOf(
                        listOf(shuffledDigits[0], shuffledDigits[1], shuffledDigits[2]),
                        listOf(shuffledDigits[3], shuffledDigits[4], shuffledDigits[5]),
                        listOf(shuffledDigits[6], shuffledDigits[7], shuffledDigits[8]),
                        listOf("FP", shuffledDigits[9], "<")
                    )
                } else {
                    listOf(
                        listOf("1", "2", "3"),
                        listOf("4", "5", "6"),
                        listOf("7", "8", "9"),
                        listOf("FP", "0", "<")
                    )
                }
            }

            keys.forEach { row ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    modifier = Modifier.padding(vertical = 10.dp)
                ) {
                    row.forEach { key ->
                        if (key == "FP" && !isBiometricAvailable) {
                            Spacer(modifier = Modifier.size(72.dp))
                        } else if (key.isEmpty()) {
                            Spacer(modifier = Modifier.size(72.dp))
                        } else {
                            val isFp = key == "FP"
                            val isBack = key == "<"
                            Button(
                                onClick = {
                                    if (key == "<") {
                                        if (pin.isNotEmpty()) pin = pin.dropLast(1)
                                    } else if (key == "FP") {
                                        showBiometricPrompt()
                                    } else {
                                        if (pin.length < 4) pin += key
                                    }
                                },
                                modifier = Modifier.size(72.dp),
                                shape = CircleShape,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isFp) androidx.compose.ui.graphics.Color(0xFF2A1448) else if (isBack) androidx.compose.ui.graphics.Color(0xFF1E1430) else androidx.compose.ui.graphics.Color(0xFF161024),
                                    contentColor = if (isFp) androidx.compose.ui.graphics.Color(0xFFC084FC) else androidx.compose.ui.graphics.Color.White
                                ),
                                border = androidx.compose.foundation.BorderStroke(1.dp, if (isFp) androidx.compose.ui.graphics.Color(0xFF7E22CE) else androidx.compose.ui.graphics.Color(0xFF2C1F45)),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                if (key == "FP") {
                                    Icon(Icons.Default.Fingerprint, contentDescription = "Biometric", modifier = Modifier.size(34.dp))
                                } else {
                                    Text(text = key, fontSize = 26.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
