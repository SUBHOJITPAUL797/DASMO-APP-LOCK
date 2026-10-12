package com.example.presentation.screens.vault

import android.content.Context
import android.graphics.BitmapFactory
import android.widget.MediaController
import android.widget.Toast
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.util.CryptoManager
import com.example.util.MasterKeyManager
import com.example.util.SecurePreferences
import com.example.util.VaultRestoreManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun EncryptedImageThumbnail(file: File) {
    var bitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(file) {
        withContext(Dispatchers.IO) {
            try {
                val encryptedBytes = file.readBytes()
                val decryptedBytes = CryptoManager.decryptBytes(encryptedBytes)
                val options = BitmapFactory.Options().apply {
                    inSampleSize = 4
                }
                val decBitmap = BitmapFactory.decodeByteArray(decryptedBytes, 0, decryptedBytes.size, options)
                bitmap = decBitmap?.asImageBitmap()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap!!,
            contentDescription = file.name,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    } else {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Image, contentDescription = "Loading Image", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * Animated Modal Dialog showing cryptographic progress (pulsing shield, rotating neon ring, progress bar).
 */
@Composable
fun CryptoProgressModal(
    title: String,
    currentStepText: String,
    currentFileText: String,
    progressFraction: Float
) {
    val infiniteTransition = rememberInfiniteTransition(label = "crypto_infinite")
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Pulsing Shield with Neon Rotating Ring
                Box(
                    modifier = Modifier.size(90.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .rotate(rotationAngle)
                            .border(
                                width = 3.dp,
                                brush = Brush.sweepGradient(
                                    colors = listOf(
                                        Color(0xFF38BDF8),
                                        Color(0xFFA855F7),
                                        Color(0xFFEC4899),
                                        Color(0xFF38BDF8)
                                    )
                                ),
                                shape = CircleShape
                            )
                    )
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier
                            .size(68.dp)
                            .scale(pulseScale)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = "Security Processing",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                }

                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                if (currentStepText.isNotBlank()) {
                    Text(
                        text = currentStepText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                }

                if (currentFileText.isNotBlank()) {
                    Text(
                        text = currentFileText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        textAlign = TextAlign.Center
                    )
                }

                LinearProgressIndicator(
                    progress = { progressFraction.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecureVaultScreen(context: Context, onBack: () -> Unit) {
    val coroutineScope = rememberCoroutineScope()
    val securePrefs = remember { SecurePreferences(context) }
    
    // Check if user has initialized their master PIN
    var hasMasterPin by remember { mutableStateOf(securePrefs.hasMasterPin()) }
    var setupStep by remember { mutableStateOf(0) } // 0: Enter New PIN, 1: Confirm PIN, 2: Security Recovery
    var setupFirstPin by remember { mutableStateOf("") }
    var setupConfirmPin by remember { mutableStateOf("") }
    var setupError by remember { mutableStateOf<String?>(null) }
    
    val defaultQuestions = listOf(
        "What was the name of your first school?",
        "What is your mother's maiden name?",
        "What city were you born in?",
        "What was your childhood nickname?",
        "What is your favorite book or movie?"
    )
    var setupQuestion by remember { mutableStateOf(defaultQuestions[0]) }
    var setupAnswer by remember { mutableStateOf("") }
    var isQuestionDropdownExpanded by remember { mutableStateOf(false) }

    var isUnlocked by remember { mutableStateOf(false) }
    var isDecoyMode by remember { mutableStateOf(false) }
    var enteredPin by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }
    var showRecoveryDialog by remember { mutableStateOf(false) }
    var recoveryInput by remember { mutableStateOf("") }
    var recoveryError by remember { mutableStateOf<String?>(null) }
    var isKeypadBouncing by remember { mutableStateOf(false) }

    var secureFiles by remember { mutableStateOf<List<File>>(emptyList()) }
    var selectedFile by remember { mutableStateOf<File?>(null) }
    var decryptedContent by remember { mutableStateOf<String?>(null) }
    
    // Multi-Selection state
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedFilePaths by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showBatchDeleteConfirm by remember { mutableStateOf(false) }

    // Progress Dialog States
    var isProgressVisible by remember { mutableStateOf(false) }
    var progressTitle by remember { mutableStateOf("") }
    var progressStepText by remember { mutableStateOf("") }
    var progressFileText by remember { mutableStateOf("") }
    var progressFraction by remember { mutableStateOf(0f) }

    var showCreateDialog by remember { mutableStateOf(false) }
    var newFileName by remember { mutableStateOf("") }
    var newFileContent by remember { mutableStateOf("") }
    var saveError by remember { mutableStateOf<String?>(null) }
    
    val dateFormatter = remember { SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()) }
    val correctPin = remember(hasMasterPin) { securePrefs.getString("app_pin", "") }
    val decoyPin = remember { securePrefs.getString("decoy_pin", "") }

    fun getVaultDir(): File {
        val dirName = if (isDecoyMode) "decoy_vault" else "secure_vault"
        val dir = context.getDir(dirName, Context.MODE_PRIVATE)
        if (!dir.exists()) dir.mkdirs()

        if (isDecoyMode) {
            val existing = dir.listFiles()?.filter { it.name.endsWith(".enc") }
            if (existing.isNullOrEmpty()) {
                try {
                    val note1 = File(dir, "Weekly Grocery List.enc")
                    note1.writeText(CryptoManager.encrypt("1. Milk (Whole)\n2. Eggs (Dozen)\n3. Brown Bread\n4. Apples & Oranges\n5. Green Tea"), Charsets.UTF_8)
                    val note2 = File(dir, "Workout Plan.enc")
                    note2.writeText(CryptoManager.encrypt("Mon: Chest & Triceps\nWed: Back & Biceps\nFri: Legs & Core\nSun: 5km morning run"), Charsets.UTF_8)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        return dir
    }

    fun loadSecureFiles() {
        val dir = getVaultDir()
        secureFiles = dir.listFiles()?.toList()?.filter { it.name.endsWith(".enc") }?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    var activeTab by remember { mutableStateOf(0) } // 0: Notes, 1: Photos, 2: Videos
    var decryptedImageBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var decryptedVideoFile by remember { mutableStateOf<File?>(null) }

    // Multi-photo importer
    val photosPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNullOrEmpty()) return@rememberLauncherForActivityResult
        coroutineScope.launch(Dispatchers.IO) {
            val total = uris.size
            withContext(Dispatchers.Main) {
                isProgressVisible = true
                progressTitle = "Encrypting Photos"
                progressFraction = 0f
            }
            val contentResolver = context.contentResolver
            val dir = getVaultDir()

            uris.forEachIndexed { index, uri ->
                try {
                    var fileName = "photo_${System.currentTimeMillis()}_$index.jpg"
                    val cursor = contentResolver.query(uri, null, null, null, null)
                    cursor?.use { c ->
                        if (c.moveToFirst()) {
                            val nameIndex = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                            if (nameIndex != -1) {
                                val originalName = c.getString(nameIndex)
                                if (originalName.isNotEmpty()) fileName = originalName
                            }
                        }
                    }
                    if (!fileName.endsWith(".enc", ignoreCase = true)) {
                        fileName = "$fileName.enc"
                    }

                    withContext(Dispatchers.Main) {
                        progressStepText = "Encrypting file ${index + 1} of $total"
                        progressFileText = fileName.removeSuffix(".enc")
                        progressFraction = (index + 1f) / total
                    }

                    val inputStream = contentResolver.openInputStream(uri)
                    val bytes = inputStream?.readBytes()
                    if (bytes != null && bytes.isNotEmpty()) {
                        val encryptedBytes = CryptoManager.encryptBytes(bytes)
                        val targetFile = File(dir, fileName)
                        targetFile.writeBytes(encryptedBytes)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            withContext(Dispatchers.Main) {
                isProgressVisible = false
                loadSecureFiles()
                Toast.makeText(context, "$total photos securely encrypted in vault", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Multi-video importer
    val videosPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNullOrEmpty()) return@rememberLauncherForActivityResult
        coroutineScope.launch(Dispatchers.IO) {
            val total = uris.size
            withContext(Dispatchers.Main) {
                isProgressVisible = true
                progressTitle = "Encrypting Videos"
                progressFraction = 0f
            }
            val contentResolver = context.contentResolver
            val dir = getVaultDir()

            uris.forEachIndexed { index, uri ->
                try {
                    var fileName = "video_${System.currentTimeMillis()}_$index.mp4"
                    val cursor = contentResolver.query(uri, null, null, null, null)
                    cursor?.use { c ->
                        if (c.moveToFirst()) {
                            val nameIndex = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                            if (nameIndex != -1) {
                                val originalName = c.getString(nameIndex)
                                if (originalName.isNotEmpty()) fileName = originalName
                            }
                        }
                    }
                    if (!fileName.endsWith(".enc", ignoreCase = true)) {
                        fileName = "$fileName.enc"
                    }

                    withContext(Dispatchers.Main) {
                        progressStepText = "Encrypting video ${index + 1} of $total"
                        progressFileText = fileName.removeSuffix(".enc")
                        progressFraction = (index + 1f) / total
                    }

                    val inputStream = contentResolver.openInputStream(uri)
                    val bytes = inputStream?.readBytes()
                    if (bytes != null && bytes.isNotEmpty()) {
                        val encryptedBytes = CryptoManager.encryptBytes(bytes)
                        val targetFile = File(dir, fileName)
                        targetFile.writeBytes(encryptedBytes)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            withContext(Dispatchers.Main) {
                isProgressVisible = false
                loadSecureFiles()
                Toast.makeText(context, "$total videos securely encrypted in vault", Toast.LENGTH_SHORT).show()
            }
        }
    }

    LaunchedEffect(isUnlocked) {
        if (isUnlocked) {
            withContext(Dispatchers.IO) {
                loadSecureFiles()
            }
        }
    }

    // Progress Modal overlay
    if (isProgressVisible) {
        CryptoProgressModal(
            title = progressTitle,
            currentStepText = progressStepText,
            currentFileText = progressFileText,
            progressFraction = progressFraction
        )
    }

    // -------------------------------------------------------------
    // FLOW 1: FIRST TIME SETUP WIZARD (If Master PIN is NOT yet set)
    // -------------------------------------------------------------
    if (!hasMasterPin) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Vault Initialization") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(72.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = "Security",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    when (setupStep) {
                        0 -> {
                            Text(
                                text = "Create Master PIN",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Set a 4-digit PIN to secure your private vault.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(24.dp))

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                for (i in 1..4) {
                                    val active = setupFirstPin.length >= i
                                    Box(
                                        modifier = Modifier
                                            .size(18.dp)
                                            .background(
                                                color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                                shape = CircleShape
                                            )
                                    )
                                }
                            }

                            if (setupError != null) {
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(setupError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                            }

                            Spacer(modifier = Modifier.height(32.dp))

                            // Keypad
                            val buttons = listOf(
                                listOf("1", "2", "3"),
                                listOf("4", "5", "6"),
                                listOf("7", "8", "9"),
                                listOf("C", "0", "⌫")
                            )
                            for (row in buttons) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    modifier = Modifier.padding(vertical = 6.dp)
                                ) {
                                    for (key in row) {
                                        Button(
                                            onClick = {
                                                setupError = null
                                                when (key) {
                                                    "C" -> setupFirstPin = ""
                                                    "⌫" -> if (setupFirstPin.isNotEmpty()) setupFirstPin = setupFirstPin.dropLast(1)
                                                    else -> {
                                                        if (setupFirstPin.length < 4) {
                                                            setupFirstPin += key
                                                            if (setupFirstPin.length == 4) {
                                                                setupStep = 1
                                                            }
                                                        }
                                                    }
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (key == "C" || key == "⌫") MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                                contentColor = if (key == "C" || key == "⌫") MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                            ),
                                            shape = RoundedCornerShape(16.dp),
                                            modifier = Modifier.size(68.dp)
                                        ) {
                                            Text(text = key, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }

                        1 -> {
                            Text(
                                text = "Confirm Master PIN",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Re-enter your 4-digit PIN to confirm.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(24.dp))

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                for (i in 1..4) {
                                    val active = setupConfirmPin.length >= i
                                    Box(
                                        modifier = Modifier
                                            .size(18.dp)
                                            .background(
                                                color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                                shape = CircleShape
                                            )
                                    )
                                }
                            }

                            if (setupError != null) {
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(setupError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                            }

                            Spacer(modifier = Modifier.height(32.dp))

                            val buttons = listOf(
                                listOf("1", "2", "3"),
                                listOf("4", "5", "6"),
                                listOf("7", "8", "9"),
                                listOf("C", "0", "⌫")
                            )
                            for (row in buttons) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    modifier = Modifier.padding(vertical = 6.dp)
                                ) {
                                    for (key in row) {
                                        Button(
                                            onClick = {
                                                setupError = null
                                                when (key) {
                                                    "C" -> setupConfirmPin = ""
                                                    "⌫" -> if (setupConfirmPin.isNotEmpty()) setupConfirmPin = setupConfirmPin.dropLast(1)
                                                    else -> {
                                                        if (setupConfirmPin.length < 4) {
                                                            setupConfirmPin += key
                                                            if (setupConfirmPin.length == 4) {
                                                                if (setupConfirmPin == setupFirstPin) {
                                                                    setupStep = 2
                                                                } else {
                                                                    setupError = "PINs do not match. Please try again."
                                                                    setupConfirmPin = ""
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (key == "C" || key == "⌫") MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                                contentColor = if (key == "C" || key == "⌫") MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                            ),
                                            shape = RoundedCornerShape(16.dp),
                                            modifier = Modifier.size(68.dp)
                                        ) {
                                            Text(text = key, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }

                        2 -> {
                            Text(
                                text = "Security Recovery Setup",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Choose a recovery question in case you forget your PIN. Universal Master Key recovery is also enabled.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(24.dp))

                            ExposedDropdownMenuBox(
                                expanded = isQuestionDropdownExpanded,
                                onExpandedChange = { isQuestionDropdownExpanded = !isQuestionDropdownExpanded }
                            ) {
                                OutlinedTextField(
                                    value = setupQuestion,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Security Question") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isQuestionDropdownExpanded) },
                                    modifier = Modifier.fillMaxWidth().menuAnchor()
                                )
                                ExposedDropdownMenu(
                                    expanded = isQuestionDropdownExpanded,
                                    onDismissRequest = { isQuestionDropdownExpanded = false }
                                ) {
                                    defaultQuestions.forEach { q ->
                                        DropdownMenuItem(
                                            text = { Text(q) },
                                            onClick = {
                                                setupQuestion = q
                                                isQuestionDropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            OutlinedTextField(
                                value = setupAnswer,
                                onValueChange = { setupAnswer = it; setupError = null },
                                label = { Text("Your Secret Answer") },
                                placeholder = { Text("e.g. Springfield") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            if (setupError != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(setupError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                            }

                            Spacer(modifier = Modifier.height(24.dp))

                            Button(
                                onClick = {
                                    if (setupAnswer.isBlank()) {
                                        setupError = "Please enter an answer for recovery."
                                        return@Button
                                    }
                                    // Save credentials
                                    securePrefs.putString("app_pin", setupFirstPin)
                                    securePrefs.setSecurityRecovery(setupQuestion, setupAnswer)
                                    hasMasterPin = true
                                    isUnlocked = true
                                    Toast.makeText(context, "Vault PIN & Security Recovery configured successfully!", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text("Save & Open Vault", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
        return
    }

    // -------------------------------------------------------------
    // FLOW 2: STANDARD VAULT PIN ENTRY (With Animations & Recovery)
    // -------------------------------------------------------------
    if (!isUnlocked) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Secure Vault Access") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(68.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Encrypted",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Enter Vault PIN",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Enter your 4-digit Master PIN to decrypt files",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        for (i in 1..4) {
                            val active = enteredPin.length >= i
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .background(
                                        color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                        shape = CircleShape
                                    )
                            )
                        }
                    }
                    
                    if (pinError != null) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = pinError!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(28.dp))
                    
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val buttons = listOf(
                            listOf("1", "2", "3"),
                            listOf("4", "5", "6"),
                            listOf("7", "8", "9"),
                            listOf("C", "0", "⌫")
                        )
                        for (row in buttons) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                for (key in row) {
                                    Button(
                                        onClick = {
                                            pinError = null
                                            when (key) {
                                                "C" -> enteredPin = ""
                                                "⌫" -> if (enteredPin.isNotEmpty()) enteredPin = enteredPin.dropLast(1)
                                                else -> {
                                                    if (enteredPin.length < 4) {
                                                        enteredPin += key
                                                        if (enteredPin.length == 4) {
                                                            if (enteredPin == correctPin) {
                                                                isUnlocked = true
                                                                isDecoyMode = false
                                                                enteredPin = ""
                                                            } else if (decoyPin.isNotEmpty() && enteredPin == decoyPin) {
                                                                isUnlocked = true
                                                                isDecoyMode = true
                                                                enteredPin = ""
                                                            } else {
                                                                pinError = "Incorrect PIN. Access Denied."
                                                                enteredPin = ""
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (key == "C" || key == "⌫") MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                            contentColor = if (key == "C" || key == "⌫") MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                        ),
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier.size(68.dp)
                                    ) {
                                        Text(
                                            text = key,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    TextButton(
                        onClick = {
                            recoveryInput = ""
                            recoveryError = null
                            showRecoveryDialog = true
                        }
                    ) {
                        Text(
                            text = "Forgot PIN? / Master Recovery",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // Recovery Dialog
        if (showRecoveryDialog) {
            val savedQuestion = remember { securePrefs.getSecurityQuestion() }
            AlertDialog(
                onDismissRequest = { showRecoveryDialog = false },
                title = {
                    Text(
                        text = "Vault Recovery",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Unlock using your security answer or the Universal Master Key.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (savedQuestion.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = "Security Question:",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                    Text(
                                        text = savedQuestion,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }

                        OutlinedTextField(
                            value = recoveryInput,
                            onValueChange = {
                                recoveryInput = it
                                recoveryError = null
                            },
                            label = { Text(if (savedQuestion.isNotBlank()) "Security Answer or Master Key" else "Master Recovery Key") },
                            placeholder = { Text("Enter credentials") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (recoveryError != null) {
                            Text(
                                text = recoveryError!!,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val isMaster = MasterKeyManager.isMasterKey(recoveryInput)
                            val isSecAnswer = savedQuestion.isNotBlank() && securePrefs.verifySecurityAnswer(recoveryInput)

                            if (isMaster || isSecAnswer) {
                                showRecoveryDialog = false
                                isUnlocked = true
                                isDecoyMode = false
                                Toast.makeText(context, "Recovery verified! Vault unlocked.", Toast.LENGTH_SHORT).show()
                            } else {
                                recoveryError = "Invalid recovery credentials."
                            }
                        }
                    ) {
                        Text("Unlock Vault")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showRecoveryDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
        return
    }

    // -------------------------------------------------------------
    // FLOW 3: UNLOCKED VAULT (With Legacy Restore, Batch & Multi-Select)
    // -------------------------------------------------------------
    val photosFiles = secureFiles.filter { VaultRestoreManager.isImageFile(it.name) }
    val videosFiles = secureFiles.filter { VaultRestoreManager.isVideoFile(it.name) }
    val notesFiles = secureFiles.filter { !VaultRestoreManager.isImageFile(it.name) && !VaultRestoreManager.isVideoFile(it.name) }

    val currentTabFiles = when (activeTab) {
        0 -> notesFiles
        1 -> photosFiles
        else -> videosFiles
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (isSelectionMode) {
                        Text("${selectedFilePaths.size} Selected", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    } else {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Secure Vault", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                if (isDecoyMode) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        shape = CircleShape
                                    ) {
                                        Text(
                                            "Decoy Mode",
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                            Text(if (isDecoyMode) "Standard Storage" else "AES-256 Encrypted", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                        }
                    }
                },
                navigationIcon = {
                    if (isSelectionMode) {
                        IconButton(onClick = {
                            isSelectionMode = false
                            selectedFilePaths = emptySet()
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "Exit Selection Mode")
                        }
                    } else {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    if (isSelectionMode) {
                        val currentTabPaths = currentTabFiles.map { it.absolutePath }
                        val isAllSelected = currentTabPaths.isNotEmpty() && selectedFilePaths.containsAll(currentTabPaths)
                        IconButton(onClick = {
                            selectedFilePaths = if (isAllSelected) {
                                emptySet()
                            } else {
                                currentTabPaths.toSet()
                            }
                        }) {
                            Icon(
                                imageVector = if (isAllSelected) Icons.Default.Deselect else Icons.Default.SelectAll,
                                contentDescription = "Toggle Select All"
                            )
                        }
                    } else {
                        if (activeTab != 0 && currentTabFiles.isNotEmpty()) {
                            IconButton(onClick = { isSelectionMode = true }) {
                                Icon(Icons.Default.CheckCircle, contentDescription = "Select Multiple")
                            }
                        }
                        IconButton(onClick = { isUnlocked = false; isDecoyMode = false }) {
                            Icon(Icons.Default.Lock, contentDescription = "Lock Vault", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (isSelectionMode && selectedFilePaths.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shadowElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Batch Delete
                        OutlinedButton(
                            onClick = { showBatchDeleteConfirm = true },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Selected", modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Delete (${selectedFilePaths.size})")
                        }

                        // Batch Restore to Gallery (Available for Photos & Videos)
                        if (activeTab != 0) {
                            Button(
                                onClick = {
                                    val filesToRestore = currentTabFiles.filter { selectedFilePaths.contains(it.absolutePath) }
                                    coroutineScope.launch(Dispatchers.IO) {
                                        val total = filesToRestore.size
                                        withContext(Dispatchers.Main) {
                                            isProgressVisible = true
                                            progressTitle = "Restoring to Gallery"
                                            progressFraction = 0f
                                        }

                                        var successCount = 0
                                        filesToRestore.forEachIndexed { idx, file ->
                                            withContext(Dispatchers.Main) {
                                                progressStepText = "Restoring file ${idx + 1} of $total"
                                                progressFileText = file.name.removeSuffix(".enc")
                                                progressFraction = (idx + 1f) / total
                                            }

                                            val result = VaultRestoreManager.restoreFileToGallery(
                                                context = context,
                                                vaultFile = file,
                                                deleteVaultFileOnSuccess = true
                                            )
                                            if (result.isSuccess) successCount++
                                        }

                                        withContext(Dispatchers.Main) {
                                            isProgressVisible = false
                                            isSelectionMode = false
                                            selectedFilePaths = emptySet()
                                            loadSecureFiles()
                                            Toast.makeText(context, "Successfully restored $successCount files to Gallery!", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1.3f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Restore, contentDescription = "Restore", modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Restore (${selectedFilePaths.size})")
                            }
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            if (!isSelectionMode) {
                FloatingActionButton(
                    onClick = {
                        if (activeTab == 0) {
                            newFileName = ""
                            newFileContent = ""
                            saveError = null
                            showCreateDialog = true
                        } else if (activeTab == 1) {
                            photosPickerLauncher.launch("image/*")
                        } else {
                            videosPickerLauncher.launch("video/*")
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Encrypted File")
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            TabRow(selectedTabIndex = activeTab) {
                Tab(
                    selected = activeTab == 0,
                    onClick = {
                        activeTab = 0
                        isSelectionMode = false
                        selectedFilePaths = emptySet()
                    },
                    text = { Text("Notes (${notesFiles.size})") },
                    icon = { Icon(Icons.Default.Folder, contentDescription = "Notes") }
                )
                Tab(
                    selected = activeTab == 1,
                    onClick = {
                        activeTab = 1
                        isSelectionMode = false
                        selectedFilePaths = emptySet()
                    },
                    text = { Text("Photos (${photosFiles.size})") },
                    icon = { Icon(Icons.Default.Image, contentDescription = "Photos") }
                )
                Tab(
                    selected = activeTab == 2,
                    onClick = {
                        activeTab = 2
                        isSelectionMode = false
                        selectedFilePaths = emptySet()
                    },
                    text = { Text("Videos (${videosFiles.size})") },
                    icon = { Icon(Icons.Default.Movie, contentDescription = "Videos") }
                )
            }

            if (currentTabFiles.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(
                            imageVector = when (activeTab) {
                                0 -> Icons.Default.Folder
                                1 -> Icons.Default.Image
                                else -> Icons.Default.Movie
                            },
                            contentDescription = "Empty",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(80.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = when (activeTab) {
                                0 -> "No Encrypted Notes"
                                1 -> "No Secure Photos"
                                else -> "No Secure Videos"
                            },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = when (activeTab) {
                                0 -> "Write and secure private text documents. They are protected using AES-256."
                                1 -> "Import photos securely into the vault. Tap any photo to preview or restore it to your Gallery."
                                else -> "Import private videos. Tap any video to play inside the vault or restore it to your Gallery."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(currentTabFiles) { file ->
                        val isSelected = selectedFilePaths.contains(file.absolutePath)
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (isSelectionMode) {
                                        selectedFilePaths = if (isSelected) {
                                            selectedFilePaths - file.absolutePath
                                        } else {
                                            selectedFilePaths + file.absolutePath
                                        }
                                    } else {
                                        if (activeTab == 0) {
                                            try {
                                                val encryptedText = file.readText(Charsets.UTF_8)
                                                val decrypted = CryptoManager.decrypt(encryptedText)
                                                decryptedContent = decrypted
                                            } catch (e: Exception) {
                                                decryptedContent = "Error: Decryption failed."
                                            }
                                        }
                                        selectedFile = file
                                    }
                                },
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surfaceVariant
                            ),
                            border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(120.dp)
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.05f))
                                ) {
                                    if (activeTab == 1) {
                                        EncryptedImageThumbnail(file)
                                    } else {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = when (activeTab) {
                                                    0 -> Icons.Default.Folder
                                                    else -> Icons.Default.PlayArrow
                                                },
                                                contentDescription = "File Type",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(40.dp)
                                            )
                                        }
                                    }

                                    if (isSelectionMode) {
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(8.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                                contentDescription = "Selection",
                                                tint = if (isSelected) MaterialTheme.colorScheme.primary else Color.White
                                            )
                                        }
                                    }
                                }
                                
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = file.name.removeSuffix(".enc"),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "${file.length() / 1024} KB",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = dateFormatter.format(Date(file.lastModified())),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // -------------------------------------------------------------
    // FILE PREVIEW MODAL DIALOG (With Single File "Restore to Gallery")
    // -------------------------------------------------------------
    if (selectedFile != null) {
        val fileToPreview = selectedFile!!
        val isPhoto = VaultRestoreManager.isImageFile(fileToPreview.name)
        val isVideo = VaultRestoreManager.isVideoFile(fileToPreview.name)

        LaunchedEffect(fileToPreview) {
            if (isPhoto) {
                withContext(Dispatchers.IO) {
                    try {
                        val bytes = fileToPreview.readBytes()
                        val decBytes = CryptoManager.decryptBytes(bytes)
                        val bitmap = BitmapFactory.decodeByteArray(decBytes, 0, decBytes.size)
                        decryptedImageBitmap = bitmap?.asImageBitmap()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            } else if (isVideo) {
                withContext(Dispatchers.IO) {
                    try {
                        val bytes = fileToPreview.readBytes()
                        val decBytes = CryptoManager.decryptBytes(bytes)
                        val temp = File(context.cacheDir, "temp_vault_video.mp4")
                        temp.writeBytes(decBytes)
                        decryptedVideoFile = temp
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }

        Dialog(onDismissRequest = {
            selectedFile = null
            decryptedContent = null
            decryptedImageBitmap = null
            decryptedVideoFile?.delete()
            decryptedVideoFile = null
        }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = fileToPreview.name.removeSuffix(".enc"),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                            Text(
                                text = "Decrypted Preview",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        IconButton(
                            onClick = {
                                fileToPreview.delete()
                                selectedFile = null
                                decryptedContent = null
                                decryptedImageBitmap = null
                                decryptedVideoFile?.delete()
                                decryptedVideoFile = null
                                loadSecureFiles()
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    if (isPhoto) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(250.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                if (decryptedImageBitmap != null) {
                                    Image(
                                        bitmap = decryptedImageBitmap!!,
                                        contentDescription = "Decrypted",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Fit
                                    )
                                } else {
                                    CircularProgressIndicator()
                                }
                            }
                        }
                    } else if (isVideo) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(250.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                if (decryptedVideoFile != null) {
                                    AndroidView(
                                        factory = { ctx ->
                                            VideoView(ctx).apply {
                                                setVideoPath(decryptedVideoFile!!.absolutePath)
                                                val mediaController = MediaController(ctx)
                                                mediaController.setAnchorView(this)
                                                setMediaController(mediaController)
                                                setOnPreparedListener { start() }
                                            }
                                        },
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    CircularProgressIndicator()
                                }
                            }
                        }
                    } else {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 120.dp, max = 240.dp)
                                .verticalScroll(rememberScrollState()),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = decryptedContent ?: "Decrypting...",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(20.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Restore button for Photos and Videos
                        if (isPhoto || isVideo) {
                            FilledTonalButton(
                                onClick = {
                                    coroutineScope.launch(Dispatchers.IO) {
                                        withContext(Dispatchers.Main) {
                                            isProgressVisible = true
                                            progressTitle = "Restoring to Gallery"
                                            progressStepText = "Exporting decrypted file"
                                            progressFileText = fileToPreview.name.removeSuffix(".enc")
                                            progressFraction = 0.5f
                                        }

                                        val res = VaultRestoreManager.restoreFileToGallery(
                                            context = context,
                                            vaultFile = fileToPreview,
                                            deleteVaultFileOnSuccess = true
                                        )

                                        withContext(Dispatchers.Main) {
                                            isProgressVisible = false
                                            if (res.isSuccess) {
                                                Toast.makeText(context, "Restored to Gallery (${res.getOrNull()?.restoredFileName})", Toast.LENGTH_LONG).show()
                                                selectedFile = null
                                                decryptedContent = null
                                                decryptedImageBitmap = null
                                                decryptedVideoFile?.delete()
                                                decryptedVideoFile = null
                                                loadSecureFiles()
                                            } else {
                                                Toast.makeText(context, "Failed to restore: ${res.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Restore, contentDescription = "Restore to Gallery", modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Restore to Gallery")
                            }
                        } else {
                            Spacer(modifier = Modifier.width(8.dp))
                        }

                        Button(
                            onClick = {
                                selectedFile = null
                                decryptedContent = null
                                decryptedImageBitmap = null
                                decryptedVideoFile?.delete()
                                decryptedVideoFile = null
                            },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Close")
                        }
                    }
                }
            }
        }
    }

    // Batch Delete Confirmation Dialog
    if (showBatchDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showBatchDeleteConfirm = false },
            title = { Text("Delete ${selectedFilePaths.size} Files?", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to permanently delete the selected files from the vault? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        val filesToDelete = currentTabFiles.filter { selectedFilePaths.contains(it.absolutePath) }
                        filesToDelete.forEach { it.delete() }
                        showBatchDeleteConfirm = false
                        isSelectionMode = false
                        selectedFilePaths = emptySet()
                        loadSecureFiles()
                        Toast.makeText(context, "Files deleted from vault", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBatchDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Create Encrypted Note Dialog
    if (showCreateDialog) {
        Dialog(onDismissRequest = { showCreateDialog = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                ) {
                    Text(
                        text = "New Encrypted Note",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    OutlinedTextField(
                        value = newFileName,
                        onValueChange = { newFileName = it },
                        label = { Text("Note Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    OutlinedTextField(
                        value = newFileContent,
                        onValueChange = { newFileContent = it },
                        label = { Text("Note Content") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                    )
                    
                    if (saveError != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(saveError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showCreateDialog = false }) {
                            Text("Cancel")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (newFileName.isBlank() || newFileContent.isBlank()) {
                                    saveError = "Fields cannot be empty."
                                } else {
                                    try {
                                        val dir = getVaultDir()
                                        val file = File(dir, "${newFileName.trim()}.enc")
                                        val encrypted = CryptoManager.encrypt(newFileContent)
                                        file.writeText(encrypted, Charsets.UTF_8)
                                        showCreateDialog = false
                                        loadSecureFiles()
                                    } catch (e: Exception) {
                                        saveError = "Failed to encrypt/save note."
                                    }
                                }
                            }
                        ) {
                            Text("Encrypt & Save")
                        }
                    }
                }
            }
        }
    }
}
