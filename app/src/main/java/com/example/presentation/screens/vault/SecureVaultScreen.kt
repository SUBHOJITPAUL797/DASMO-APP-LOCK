package com.example.presentation.screens.vault

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.ByteArrayDataSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.util.CryptoManager
import com.example.util.MasterKeyManager
import com.example.util.SecurePreferences
import com.example.util.VaultMediaCleaner
import com.example.util.VaultRestoreManager
import com.example.util.VaultStorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ─── Helpers ──────────────────────────────────────────────────────────────────

private fun Long.toMb(): String = "%.1f MB".format(this / 1_048_576.0)

@Composable
fun EncryptedImageThumbnail(file: File) {
    var bitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(file) {
        withContext(Dispatchers.IO) {
            try {
                val encryptedBytes = file.readBytes()
                val decryptedBytes = CryptoManager.decryptBytes(encryptedBytes)
                val options = BitmapFactory.Options().apply { inSampleSize = 4 }
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
            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
        }
    }
}

// ─── Crypto Progress Modal ─────────────────────────────────────────────────────

/**
 * Modal showing real-time encryption/decryption progress.
 * Displays animated shield + live percentage, MB counter, and file name.
 */
@Composable
fun CryptoProgressModal(
    title: String,
    currentStepText: String,
    currentFileText: String,
    progressFraction: Float,
    bytesProcessed: Long = 0L,
    totalBytes: Long = 0L
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

    val percent = (progressFraction * 100).toInt().coerceIn(0, 100)
    val progressAnim by animateFloatAsState(
        targetValue = progressFraction.coerceIn(0f, 1f),
        animationSpec = tween(200),
        label = "progressAnim"
    )

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(8.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Pulsing shield icon with neon ring
                Box(modifier = Modifier.size(90.dp), contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .rotate(rotationAngle)
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
                        modifier = Modifier.size(68.dp).scale(pulseScale)
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

                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)

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

                // Real progress bar
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    LinearProgressIndicator(
                        progress = { progressAnim },
                        modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        if (totalBytes > 0) {
                            Text(
                                text = "${bytesProcessed.toMb()} / ${totalBytes.toMb()}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Spacer(Modifier.width(1.dp))
                        }
                        Text(
                            text = "$percent%",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

// ─── Full-screen image viewer with pinch-zoom & double-tap zoom ─────────────────

@Composable
fun FullScreenImageViewer(
    bitmap: ImageBitmap,
    fileName: String,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit
) {
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            Image(
                bitmap = bitmap,
                contentDescription = fileName,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offsetX,
                        translationY = offsetY
                    )
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = {
                                if (scale > 1.5f) {
                                    scale = 1f
                                    offsetX = 0f
                                    offsetY = 0f
                                } else {
                                    scale = 3f
                                }
                            }
                        )
                    }
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 6f)
                            if (scale > 1f) {
                                offsetX += pan.x
                                offsetY += pan.y
                            } else {
                                offsetX = 0f
                                offsetY = 0f
                            }
                        }
                    }
            )

            // Top control bar with translucent gradient
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.75f), Color.Transparent)
                        )
                    )
                    .padding(horizontal = 8.dp, vertical = 12.dp)
                    .align(Alignment.TopCenter),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close", tint = Color.White)
                }
                Text(
                    text = fileName.removeSuffix(".enc"),
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onRestore) {
                        Icon(Icons.Default.Restore, contentDescription = "Restore to Gallery", tint = Color(0xFF38BDF8))
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }

            // Bottom zoom hint
            if (scale == 1f) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.Black.copy(alpha = 0.5f),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 24.dp)
                ) {
                    Text(
                        text = "Double-tap or pinch to zoom",
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

// ─── ExoPlayer full-screen video viewer ──────────────────────────────────────

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun FullScreenVideoViewer(
    decryptedBytes: ByteArray,
    fileName: String,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    val exoPlayer = remember {
        val dataSourceFactory = DataSource.Factory { ByteArrayDataSource(decryptedBytes) }
        val safeUri = "bytes://vault_video/${fileName.removeSuffix(".enc")}"
        val mediaSource = ProgressiveMediaSource.Factory(dataSourceFactory)
            .createMediaSource(MediaItem.fromUri(safeUri))
        ExoPlayer.Builder(context)
            .setSeekForwardIncrementMs(10000)
            .setSeekBackIncrementMs(10000)
            .build().apply {
                setMediaSource(mediaSource)
                prepare()
                playWhenReady = true
            }
    }

    DisposableEffect(Unit) {
        onDispose { exoPlayer.release() }
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = true
                        setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                        setShowFastForwardButton(true)
                        setShowRewindButton(true)
                        setShowNextButton(false)
                        setShowPreviousButton(false)
                        resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .align(Alignment.Center)
            )

            // Top control bar with translucent gradient
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.75f), Color.Transparent)
                        )
                    )
                    .padding(horizontal = 8.dp, vertical = 12.dp)
                    .align(Alignment.TopCenter),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close", tint = Color.White)
                }
                Text(
                    text = fileName.removeSuffix(".enc"),
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onRestore) {
                        Icon(Icons.Default.Restore, contentDescription = "Restore to Gallery", tint = Color(0xFF38BDF8))
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

// ─── Main Screen ──────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecureVaultScreen(context: Context, onBack: () -> Unit) {
    val coroutineScope = rememberCoroutineScope()
    val securePrefs = remember { SecurePreferences(context) }

    // First-time setup states
    var hasMasterPin by remember { mutableStateOf(securePrefs.hasMasterPin()) }
    var setupStep by remember { mutableStateOf(0) }
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

    // Unlock states
    var isUnlocked by remember { mutableStateOf(false) }
    var isDecoyMode by remember { mutableStateOf(false) }
    var enteredPin by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }
    var showRecoveryDialog by remember { mutableStateOf(false) }
    var recoveryInput by remember { mutableStateOf("") }
    var recoveryError by remember { mutableStateOf<String?>(null) }

    // File list
    var secureFiles by remember { mutableStateOf<List<File>>(emptyList()) }
    var selectedFile by remember { mutableStateOf<File?>(null) }
    var decryptedContent by remember { mutableStateOf<String?>(null) }

    // Multi-selection
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedFilePaths by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showBatchDeleteConfirm by remember { mutableStateOf(false) }

    // Progress dialog states (real progress)
    var isProgressVisible by remember { mutableStateOf(false) }
    var progressTitle by remember { mutableStateOf("") }
    var progressStepText by remember { mutableStateOf("") }
    var progressFileText by remember { mutableStateOf("") }
    var progressFraction by remember { mutableStateOf(0f) }
    var progressBytesProcessed by remember { mutableStateOf(0L) }
    var progressTotalBytes by remember { mutableStateOf(0L) }

    // Note creation
    var showCreateDialog by remember { mutableStateOf(false) }
    var newFileName by remember { mutableStateOf("") }
    var newFileContent by remember { mutableStateOf("") }
    var saveError by remember { mutableStateOf<String?>(null) }

    // Media preview
    var activeTab by remember { mutableStateOf(0) }
    var decryptedImageBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var decryptedVideoBytes by remember { mutableStateOf<ByteArray?>(null) }
    var isMediaLoading by remember { mutableStateOf(false) }
    var showFullScreenViewer by remember { mutableStateOf(false) }

    // Media action dialogs & Camera capture states
    var showPhotoActionDialog by remember { mutableStateOf(false) }
    var showVideoActionDialog by remember { mutableStateOf(false) }
    var tempCameraFile by remember { mutableStateOf<File?>(null) }
    var pendingCameraAction by remember { mutableStateOf<String?>(null) }

    val dateFormatter = remember { SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()) }
    val correctPin = remember(hasMasterPin) { securePrefs.getString("app_pin", "") }
    val decoyPin = remember { securePrefs.getString("decoy_pin", "") }

    fun getVaultDir(): File {
        val dir = VaultStorageManager.getVaultDirectory(context, isDecoyMode)
        if (isDecoyMode) {
            val existing = dir.listFiles()?.filter { it.name.endsWith(".enc") }
            if (existing.isNullOrEmpty()) {
                try {
                    File(dir, "Weekly Grocery List.enc").writeText(
                        CryptoManager.encrypt("1. Milk\n2. Eggs\n3. Brown Bread\n4. Apples\n5. Green Tea"), Charsets.UTF_8)
                    File(dir, "Workout Plan.enc").writeText(
                        CryptoManager.encrypt("Mon: Chest & Triceps\nWed: Back & Biceps\nFri: Legs & Core"), Charsets.UTF_8)
                } catch (e: Exception) { e.printStackTrace() }
            }
        }
        return dir
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            VaultStorageManager.migrateLegacyInternalStorage(context)
        }
    }

    fun loadSecureFiles() {
        val dir = getVaultDir()
        secureFiles = dir.listFiles()
            ?.filter { it.name.endsWith(".enc") }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }

    // ── System Scoped Storage Delete confirmation launcher ────────────────────
    val deleteRequestLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            Toast.makeText(context, "Original media deleted from Gallery", Toast.LENGTH_SHORT).show()
        }
    }

    // ── Direct Camera Photo Launcher (never enters public Gallery) ─────────────
    val cameraPhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val file = tempCameraFile
        if (success && file != null && file.exists() && file.length() > 0) {
            coroutineScope.launch(Dispatchers.IO) {
                val totalBytes = file.length()
                val dir = getVaultDir()
                val fileName = "IMG_PRIVATE_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.jpg.enc"

                withContext(Dispatchers.Main) {
                    isProgressVisible = true
                    progressTitle = "Encrypting Private Photo"
                    progressStepText = "Saving to AES-256 Vault..."
                    progressFileText = fileName.removeSuffix(".enc")
                    progressFraction = 0.5f
                    progressBytesProcessed = 0L
                    progressTotalBytes = totalBytes
                }
                try {
                    val encryptedBytes = CryptoManager.encryptBytes(file.readBytes())
                    File(dir, fileName).writeBytes(encryptedBytes)
                    // Immediately wipe unencrypted cache file
                    file.delete()
                    withContext(Dispatchers.Main) {
                        isProgressVisible = false
                        loadSecureFiles()
                        Toast.makeText(context, "Private photo captured and securely encrypted in vault!", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    withContext(Dispatchers.Main) {
                        isProgressVisible = false
                        Toast.makeText(context, "Failed to secure photo: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                } finally {
                    tempCameraFile?.delete()
                    tempCameraFile = null
                }
            }
        } else {
            tempCameraFile?.delete()
            tempCameraFile = null
        }
    }

    // ── Direct Camera Video Launcher (never enters public Gallery) ─────────────
    val cameraVideoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CaptureVideo()
    ) { success ->
        val file = tempCameraFile
        if (success && file != null && file.exists() && file.length() > 0) {
            coroutineScope.launch(Dispatchers.IO) {
                val totalBytes = file.length()
                val dir = getVaultDir()
                val fileName = "VID_PRIVATE_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.mp4.enc"

                withContext(Dispatchers.Main) {
                    isProgressVisible = true
                    progressTitle = "Encrypting Private Video"
                    progressStepText = "Saving to AES-256 Vault..."
                    progressFileText = fileName.removeSuffix(".enc")
                    progressFraction = 0.5f
                    progressBytesProcessed = 0L
                    progressTotalBytes = totalBytes
                }
                try {
                    val encryptedBytes = CryptoManager.encryptBytes(file.readBytes())
                    File(dir, fileName).writeBytes(encryptedBytes)
                    // Immediately wipe unencrypted cache file
                    file.delete()
                    withContext(Dispatchers.Main) {
                        isProgressVisible = false
                        loadSecureFiles()
                        Toast.makeText(context, "Private video recorded and securely encrypted in vault!", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    withContext(Dispatchers.Main) {
                        isProgressVisible = false
                        Toast.makeText(context, "Failed to secure video: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                } finally {
                    tempCameraFile?.delete()
                    tempCameraFile = null
                }
            }
        } else {
            tempCameraFile?.delete()
            tempCameraFile = null
        }
    }

    fun startCameraPhoto() {
        try {
            val file = File(context.cacheDir, "temp_private_capture_${System.currentTimeMillis()}.jpg")
            if (file.exists()) file.delete()
            file.createNewFile()
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            tempCameraFile = file
            cameraPhotoLauncher.launch(uri)
        } catch (e: Exception) {
            Toast.makeText(context, "Cannot open camera: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun startCameraVideo() {
        try {
            val file = File(context.cacheDir, "temp_private_capture_${System.currentTimeMillis()}.mp4")
            if (file.exists()) file.delete()
            file.createNewFile()
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            tempCameraFile = file
            cameraVideoLauncher.launch(uri)
        } catch (e: Exception) {
            Toast.makeText(context, "Cannot open video camera: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            if (pendingCameraAction == "video") startCameraVideo() else startCameraPhoto()
        } else {
            Toast.makeText(context, "Camera permission is required to capture private media", Toast.LENGTH_SHORT).show()
        }
        pendingCameraAction = null
    }

    fun launchCameraWithPermissionCheck(action: String) {
        pendingCameraAction = action
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            if (action == "video") startCameraVideo() else startCameraPhoto()
            pendingCameraAction = null
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // ── Photo importer with real byte-level progress & auto-deletion ──────────
    val photosPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNullOrEmpty()) return@rememberLauncherForActivityResult
        coroutineScope.launch(Dispatchers.IO) {
            val total = uris.size
            val contentResolver = context.contentResolver
            val dir = getVaultDir()

            // Pre-calculate total bytes for overall progress
            val fileSizes = uris.map { uri ->
                contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: 0L
            }
            val grandTotal = fileSizes.sum()
            var grandProcessed = 0L

            withContext(Dispatchers.Main) {
                isProgressVisible = true
                progressTitle = "Encrypting Photos"
                progressFraction = 0f
                progressBytesProcessed = 0L
                progressTotalBytes = grandTotal
            }

            val unDeletedUris = mutableListOf<Uri>()

            uris.forEachIndexed { index, uri ->
                try {
                    var fileName = "photo_${System.currentTimeMillis()}_$index.jpg"
                    val cursor = contentResolver.query(uri, null, null, null, null)
                    cursor?.use { c ->
                        if (c.moveToFirst()) {
                            val nameIndex = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                            if (nameIndex != -1) {
                                val original = c.getString(nameIndex)
                                if (original.isNotEmpty()) fileName = original
                            }
                        }
                    }
                    if (!fileName.endsWith(".enc", ignoreCase = true)) fileName = "$fileName.enc"

                    val fileSize = fileSizes[index]
                    val baseGrandProcessed = grandProcessed

                    withContext(Dispatchers.Main) {
                        progressStepText = "File ${index + 1} of $total"
                        progressFileText = fileName.removeSuffix(".enc")
                    }

                    val inputStream = contentResolver.openInputStream(uri)
                    if (inputStream != null) {
                        val encryptedBytes = CryptoManager.encryptBytesWithProgress(
                            inputStream = inputStream,
                            totalBytes = fileSize
                        ) { bytesRead, _ ->
                            val currentGrand = baseGrandProcessed + bytesRead
                            coroutineScope.launch(Dispatchers.Main) {
                                progressBytesProcessed = currentGrand
                                progressFraction = if (grandTotal > 0) currentGrand.toFloat() / grandTotal else (index + 1f) / total
                            }
                        }
                        File(dir, fileName).writeBytes(encryptedBytes)
                        grandProcessed += fileSize

                        // Automatically delete original unencrypted photo from device & gallery
                        val isDeleted = VaultMediaCleaner.deleteOriginalMedia(context, uri)
                        if (!isDeleted) {
                            unDeletedUris.add(uri)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            withContext(Dispatchers.Main) {
                isProgressVisible = false
                loadSecureFiles()
                if (unDeletedUris.isNotEmpty()) {
                    val intentSender = VaultMediaCleaner.createSystemDeleteIntentSender(context, unDeletedUris)
                    if (intentSender != null) {
                        deleteRequestLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
                    } else {
                        Toast.makeText(context, "$total photos securely encrypted in vault", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(context, "$total photos encrypted and removed from gallery", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // ── Video importer with real byte-level progress & auto-deletion ──────────
    val videosPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNullOrEmpty()) return@rememberLauncherForActivityResult
        coroutineScope.launch(Dispatchers.IO) {
            val total = uris.size
            val contentResolver = context.contentResolver
            val dir = getVaultDir()

            val fileSizes = uris.map { uri ->
                contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: 0L
            }
            val grandTotal = fileSizes.sum()
            var grandProcessed = 0L

            withContext(Dispatchers.Main) {
                isProgressVisible = true
                progressTitle = "Encrypting Videos"
                progressFraction = 0f
                progressBytesProcessed = 0L
                progressTotalBytes = grandTotal
            }

            val unDeletedUris = mutableListOf<Uri>()

            uris.forEachIndexed { index, uri ->
                try {
                    var fileName = "video_${System.currentTimeMillis()}_$index.mp4"
                    val cursor = contentResolver.query(uri, null, null, null, null)
                    cursor?.use { c ->
                        if (c.moveToFirst()) {
                            val nameIndex = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                            if (nameIndex != -1) {
                                val original = c.getString(nameIndex)
                                if (original.isNotEmpty()) fileName = original
                            }
                        }
                    }
                    if (!fileName.endsWith(".enc", ignoreCase = true)) fileName = "$fileName.enc"

                    val fileSize = fileSizes[index]
                    val baseGrandProcessed = grandProcessed

                    withContext(Dispatchers.Main) {
                        progressStepText = "Video ${index + 1} of $total"
                        progressFileText = fileName.removeSuffix(".enc")
                    }

                    val inputStream = contentResolver.openInputStream(uri)
                    if (inputStream != null) {
                        val encryptedBytes = CryptoManager.encryptBytesWithProgress(
                            inputStream = inputStream,
                            totalBytes = fileSize
                        ) { bytesRead, _ ->
                            val currentGrand = baseGrandProcessed + bytesRead
                            coroutineScope.launch(Dispatchers.Main) {
                                progressBytesProcessed = currentGrand
                                progressFraction = if (grandTotal > 0) currentGrand.toFloat() / grandTotal else (index + 1f) / total
                            }
                        }
                        File(dir, fileName).writeBytes(encryptedBytes)
                        grandProcessed += fileSize

                        // Automatically delete original unencrypted video from device & gallery
                        val isDeleted = VaultMediaCleaner.deleteOriginalMedia(context, uri)
                        if (!isDeleted) {
                            unDeletedUris.add(uri)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            withContext(Dispatchers.Main) {
                isProgressVisible = false
                loadSecureFiles()
                if (unDeletedUris.isNotEmpty()) {
                    val intentSender = VaultMediaCleaner.createSystemDeleteIntentSender(context, unDeletedUris)
                    if (intentSender != null) {
                        deleteRequestLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
                    } else {
                        Toast.makeText(context, "$total videos securely encrypted in vault", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(context, "$total videos encrypted and removed from gallery", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    LaunchedEffect(isUnlocked) {
        if (isUnlocked) withContext(Dispatchers.IO) { loadSecureFiles() }
    }

    // ── Progress Modal overlay ────────────────────────────────────────────────
    if (isProgressVisible) {
        CryptoProgressModal(
            title = progressTitle,
            currentStepText = progressStepText,
            currentFileText = progressFileText,
            progressFraction = progressFraction,
            bytesProcessed = progressBytesProcessed,
            totalBytes = progressTotalBytes
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // FLOW 1: FIRST-TIME SETUP WIZARD
    // ─────────────────────────────────────────────────────────────────────────
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
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(72.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Security, contentDescription = "Security", tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(40.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))

                    when (setupStep) {
                        0 -> {
                            Text("Create Master PIN", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            Text("Set a 4-digit PIN to secure your private vault.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                            Spacer(Modifier.height(24.dp))
                            PinDots(filled = setupFirstPin.length)
                            if (setupError != null) { Spacer(Modifier.height(16.dp)); Text(setupError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                            Spacer(Modifier.height(32.dp))
                            VaultKeypad(
                                onKey = { k ->
                                    setupError = null
                                    when (k) {
                                        "C" -> setupFirstPin = ""
                                        "⌫" -> if (setupFirstPin.isNotEmpty()) setupFirstPin = setupFirstPin.dropLast(1)
                                        else -> if (setupFirstPin.length < 4) { setupFirstPin += k; if (setupFirstPin.length == 4) setupStep = 1 }
                                    }
                                }
                            )
                        }
                        1 -> {
                            Text("Confirm Master PIN", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            Text("Re-enter your 4-digit PIN to confirm.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                            Spacer(Modifier.height(24.dp))
                            PinDots(filled = setupConfirmPin.length)
                            if (setupError != null) { Spacer(Modifier.height(16.dp)); Text(setupError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                            Spacer(Modifier.height(32.dp))
                            VaultKeypad(
                                onKey = { k ->
                                    setupError = null
                                    when (k) {
                                        "C" -> setupConfirmPin = ""
                                        "⌫" -> if (setupConfirmPin.isNotEmpty()) setupConfirmPin = setupConfirmPin.dropLast(1)
                                        else -> if (setupConfirmPin.length < 4) {
                                            setupConfirmPin += k
                                            if (setupConfirmPin.length == 4) {
                                                if (setupConfirmPin == setupFirstPin) setupStep = 2
                                                else { setupError = "PINs do not match. Please try again."; setupConfirmPin = "" }
                                            }
                                        }
                                    }
                                }
                            )
                        }
                        2 -> {
                            Text("Security Recovery Setup", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            Text("Choose a recovery question in case you forget your PIN. Universal Master Key recovery is also enabled.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                            Spacer(Modifier.height(24.dp))
                            ExposedDropdownMenuBox(
                                expanded = isQuestionDropdownExpanded,
                                onExpandedChange = { isQuestionDropdownExpanded = !isQuestionDropdownExpanded }
                            ) {
                                OutlinedTextField(
                                    value = setupQuestion, onValueChange = {}, readOnly = true,
                                    label = { Text("Security Question") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isQuestionDropdownExpanded) },
                                    modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                )
                                ExposedDropdownMenu(expanded = isQuestionDropdownExpanded, onDismissRequest = { isQuestionDropdownExpanded = false }) {
                                    defaultQuestions.forEach { q ->
                                        DropdownMenuItem(text = { Text(q) }, onClick = { setupQuestion = q; isQuestionDropdownExpanded = false })
                                    }
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            OutlinedTextField(
                                value = setupAnswer, onValueChange = { setupAnswer = it; setupError = null },
                                label = { Text("Your Secret Answer") }, placeholder = { Text("e.g. Springfield") },
                                singleLine = true, modifier = Modifier.fillMaxWidth()
                            )
                            if (setupError != null) { Spacer(Modifier.height(8.dp)); Text(setupError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                            Spacer(Modifier.height(24.dp))
                            Button(
                                onClick = {
                                    if (setupAnswer.isBlank()) { setupError = "Please enter an answer for recovery."; return@Button }
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

    // ─────────────────────────────────────────────────────────────────────────
    // FLOW 2: VAULT PIN ENTRY
    // ─────────────────────────────────────────────────────────────────────────
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
                modifier = Modifier.fillMaxSize().padding(paddingValues).background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth().padding(24.dp).verticalScroll(rememberScrollState())
                ) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(68.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Lock, contentDescription = "Encrypted", tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(36.dp))
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Text("Enter Vault PIN", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text("Enter your 4-digit Master PIN to decrypt files", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(24.dp))
                    PinDots(filled = enteredPin.length)
                    if (pinError != null) { Spacer(Modifier.height(16.dp)); Text(pinError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                    Spacer(Modifier.height(28.dp))
                    VaultKeypad(
                        onKey = { k ->
                            pinError = null
                            when (k) {
                                "C" -> enteredPin = ""
                                "⌫" -> if (enteredPin.isNotEmpty()) enteredPin = enteredPin.dropLast(1)
                                else -> if (enteredPin.length < 4) {
                                    enteredPin += k
                                    if (enteredPin.length == 4) {
                                        when {
                                            enteredPin == correctPin -> { isUnlocked = true; isDecoyMode = false; enteredPin = "" }
                                            decoyPin.isNotEmpty() && enteredPin == decoyPin -> { isUnlocked = true; isDecoyMode = true; enteredPin = "" }
                                            else -> { pinError = "Incorrect PIN. Access Denied."; enteredPin = "" }
                                        }
                                    }
                                }
                            }
                        }
                    )
                    Spacer(Modifier.height(20.dp))
                    TextButton(onClick = { recoveryInput = ""; recoveryError = null; showRecoveryDialog = true }) {
                        Text("Forgot PIN? / Master Recovery", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        if (showRecoveryDialog) {
            val savedQuestion = remember { securePrefs.getSecurityQuestion() }
            AlertDialog(
                onDismissRequest = { showRecoveryDialog = false },
                title = { Text("Vault Recovery", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
                text = {
                    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Unlock using your security answer or the Universal Master Key.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (savedQuestion.isNotBlank()) {
                            Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f), modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text("Security Question:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                                    Text(savedQuestion, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                        OutlinedTextField(
                            value = recoveryInput,
                            onValueChange = { recoveryInput = it; recoveryError = null },
                            label = { Text(if (savedQuestion.isNotBlank()) "Security Answer or Master Key" else "Master Recovery Key") },
                            placeholder = { Text("Enter credentials") },
                            singleLine = true, modifier = Modifier.fillMaxWidth()
                        )
                        if (recoveryError != null) Text(recoveryError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        val isMaster = MasterKeyManager.isMasterKey(recoveryInput)
                        val isSecAnswer = savedQuestion.isNotBlank() && securePrefs.verifySecurityAnswer(recoveryInput)
                        if (isMaster || isSecAnswer) {
                            showRecoveryDialog = false; isUnlocked = true; isDecoyMode = false
                            Toast.makeText(context, "Recovery verified! Vault unlocked.", Toast.LENGTH_SHORT).show()
                        } else recoveryError = "Invalid recovery credentials."
                    }) { Text("Unlock Vault") }
                },
                dismissButton = { TextButton(onClick = { showRecoveryDialog = false }) { Text("Cancel") } }
            )
        }
        return
    }

    // ─────────────────────────────────────────────────────────────────────────
    // FLOW 3: UNLOCKED VAULT
    // ─────────────────────────────────────────────────────────────────────────
    val photosFiles = secureFiles.filter { VaultRestoreManager.isImageFile(it.name) }
    val videosFiles = secureFiles.filter { VaultRestoreManager.isVideoFile(it.name) }
    val notesFiles = secureFiles.filter { !VaultRestoreManager.isImageFile(it.name) && !VaultRestoreManager.isVideoFile(it.name) }
    val currentTabFiles = when (activeTab) { 0 -> notesFiles; 1 -> photosFiles; else -> videosFiles }

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
                                    Spacer(Modifier.width(8.dp))
                                    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = CircleShape) {
                                        Text("Decoy Mode", modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                            Text(if (isDecoyMode) "Standard Storage" else "AES-256 Encrypted", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                        }
                    }
                },
                navigationIcon = {
                    if (isSelectionMode) {
                        IconButton(onClick = { isSelectionMode = false; selectedFilePaths = emptySet() }) {
                            Icon(Icons.Default.Close, contentDescription = "Exit Selection Mode")
                        }
                    } else {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                    }
                },
                actions = {
                    if (isSelectionMode) {
                        val currentPaths = currentTabFiles.map { it.absolutePath }
                        val isAllSelected = currentPaths.isNotEmpty() && selectedFilePaths.containsAll(currentPaths)
                        IconButton(onClick = { selectedFilePaths = if (isAllSelected) emptySet() else currentPaths.toSet() }) {
                            Icon(if (isAllSelected) Icons.Default.Deselect else Icons.Default.SelectAll, contentDescription = "Toggle Select All")
                        }
                    } else {
                        if (activeTab != 0 && currentTabFiles.isNotEmpty()) {
                            IconButton(onClick = { isSelectionMode = true }) { Icon(Icons.Default.CheckCircle, contentDescription = "Select Multiple") }
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
                Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shadowElevation = 8.dp, modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(
                            onClick = { showBatchDeleteConfirm = true },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Selected", modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Delete (${selectedFilePaths.size})")
                        }
                        if (activeTab != 0) {
                            Button(
                                onClick = {
                                    val filesToRestore = currentTabFiles.filter { selectedFilePaths.contains(it.absolutePath) }
                                    coroutineScope.launch(Dispatchers.IO) {
                                        val total = filesToRestore.size
                                        withContext(Dispatchers.Main) {
                                            isProgressVisible = true; progressTitle = "Restoring to Gallery"; progressFraction = 0f
                                            progressBytesProcessed = 0L; progressTotalBytes = 0L
                                        }
                                        var successCount = 0
                                        filesToRestore.forEachIndexed { idx, file ->
                                            withContext(Dispatchers.Main) {
                                                progressStepText = "Restoring ${idx + 1} of $total"
                                                progressFileText = file.name.removeSuffix(".enc")
                                                progressFraction = (idx + 1f) / total
                                            }
                                            val result = VaultRestoreManager.restoreFileToGallery(context = context, vaultFile = file, deleteVaultFileOnSuccess = true)
                                            if (result.isSuccess) successCount++
                                        }
                                        withContext(Dispatchers.Main) {
                                            isProgressVisible = false; isSelectionMode = false; selectedFilePaths = emptySet(); loadSecureFiles()
                                            Toast.makeText(context, "Restored $successCount files to Gallery!", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1.3f), shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Restore, contentDescription = "Restore", modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
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
                        when (activeTab) {
                            0 -> { newFileName = ""; newFileContent = ""; saveError = null; showCreateDialog = true }
                            1 -> showPhotoActionDialog = true
                            else -> showVideoActionDialog = true
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
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            TabRow(selectedTabIndex = activeTab) {
                Tab(selected = activeTab == 0, onClick = { activeTab = 0; isSelectionMode = false; selectedFilePaths = emptySet() },
                    text = { Text("Notes (${notesFiles.size})") }, icon = { Icon(Icons.Default.Folder, contentDescription = "Notes") })
                Tab(selected = activeTab == 1, onClick = { activeTab = 1; isSelectionMode = false; selectedFilePaths = emptySet() },
                    text = { Text("Photos (${photosFiles.size})") }, icon = { Icon(Icons.Default.Image, contentDescription = "Photos") })
                Tab(selected = activeTab == 2, onClick = { activeTab = 2; isSelectionMode = false; selectedFilePaths = emptySet() },
                    text = { Text("Videos (${videosFiles.size})") }, icon = { Icon(Icons.Default.Movie, contentDescription = "Videos") })
            }

            if (currentTabFiles.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                        Icon(
                            imageVector = when (activeTab) { 0 -> Icons.Default.Folder; 1 -> Icons.Default.Image; else -> Icons.Default.Movie },
                            contentDescription = "Empty",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(80.dp)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = when (activeTab) { 0 -> "No Encrypted Notes"; 1 -> "No Secure Photos"; else -> "No Secure Videos" },
                            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(8.dp))
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
                // ── Improved grid: 2 columns for notes, 2 for photos, 2 for videos
                val columns = if (activeTab == 0) 2 else 2
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(currentTabFiles) { file ->
                        val isSelected = selectedFilePaths.contains(file.absolutePath)
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (isSelectionMode) {
                                        selectedFilePaths = if (isSelected) selectedFilePaths - file.absolutePath else selectedFilePaths + file.absolutePath
                                    } else {
                                        selectedFile = file
                                        decryptedImageBitmap = null
                                        decryptedVideoBytes = null
                                        decryptedContent = null
                                        isMediaLoading = activeTab != 0
                                        if (activeTab == 0) {
                                            try {
                                                val enc = file.readText(Charsets.UTF_8)
                                                decryptedContent = CryptoManager.decrypt(enc)
                                            } catch (e: Exception) { decryptedContent = "Error: Decryption failed." }
                                        } else {
                                            coroutineScope.launch(Dispatchers.IO) {
                                                try {
                                                    val enc = file.readBytes()
                                                    val dec = CryptoManager.decryptBytes(enc)
                                                    withContext(Dispatchers.Main) {
                                                        if (activeTab == 1) {
                                                            val bmp = BitmapFactory.decodeByteArray(dec, 0, dec.size)
                                                            decryptedImageBitmap = bmp?.asImageBitmap()
                                                        } else {
                                                            decryptedVideoBytes = dec
                                                        }
                                                        isMediaLoading = false
                                                    }
                                                } catch (e: Exception) {
                                                    e.printStackTrace()
                                                    withContext(Dispatchers.Main) { isMediaLoading = false }
                                                }
                                            }
                                        }
                                    }
                                },
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surfaceVariant
                            ),
                            border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                            shape = RoundedCornerShape(14.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(if (activeTab == 0) 80.dp else 130.dp)
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.06f))
                                ) {
                                    when (activeTab) {
                                        1 -> EncryptedImageThumbnail(file)
                                        2 -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                            // Video thumbnail placeholder with gradient overlay
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .background(Brush.verticalGradient(listOf(Color(0xFF1A1A2E), Color(0xFF16213E))))
                                            )
                                            Surface(shape = CircleShape, color = Color.White.copy(alpha = 0.15f), modifier = Modifier.size(44.dp)) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(Icons.Default.PlayArrow, contentDescription = "Play Video", tint = Color.White, modifier = Modifier.size(28.dp))
                                                }
                                            }
                                        }
                                        else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                            Icon(Icons.AutoMirrored.Filled.Article, contentDescription = "Note", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                                        }
                                    }
                                    if (isSelectionMode) {
                                        Box(modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                                            Icon(
                                                imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                                contentDescription = "Selection",
                                                tint = if (isSelected) MaterialTheme.colorScheme.primary else Color.White,
                                                modifier = Modifier.background(Color.Black.copy(alpha = 0.3f), CircleShape)
                                            )
                                        }
                                    }
                                }
                                Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                                    Text(file.name.removeSuffix(".enc"), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                    Spacer(Modifier.height(2.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                        Text("${file.length() / 1024} KB", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(dateFormatter.format(Date(file.lastModified())), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // FILE PREVIEW / VIEWER
    // ─────────────────────────────────────────────────────────────────────────
    if (selectedFile != null) {
        val fileToPreview = selectedFile!!
        val isPhoto = VaultRestoreManager.isImageFile(fileToPreview.name)
        val isVideo = VaultRestoreManager.isVideoFile(fileToPreview.name)

        val handleRestore: (File) -> Unit = { file ->
            coroutineScope.launch(Dispatchers.IO) {
                withContext(Dispatchers.Main) {
                    isProgressVisible = true
                    progressTitle = "Restoring to Gallery"
                    progressStepText = "Exporting decrypted file"
                    progressFileText = file.name.removeSuffix(".enc")
                    progressFraction = 0.5f
                    progressBytesProcessed = 0L
                    progressTotalBytes = 0L
                }
                val res = VaultRestoreManager.restoreFileToGallery(context = context, vaultFile = file, deleteVaultFileOnSuccess = true)
                withContext(Dispatchers.Main) {
                    isProgressVisible = false
                    if (res.isSuccess) {
                        Toast.makeText(context, "Restored to Gallery (${res.getOrNull()?.restoredFileName})", Toast.LENGTH_LONG).show()
                        selectedFile = null
                        decryptedContent = null
                        decryptedImageBitmap = null
                        decryptedVideoBytes = null
                        isMediaLoading = false
                        showFullScreenViewer = false
                        loadSecureFiles()
                    } else {
                        Toast.makeText(context, "Failed to restore: ${res.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        val handleDelete: (File) -> Unit = { file ->
            file.delete()
            selectedFile = null
            decryptedContent = null
            decryptedImageBitmap = null
            decryptedVideoBytes = null
            isMediaLoading = false
            showFullScreenViewer = false
            loadSecureFiles()
            Toast.makeText(context, "File deleted from vault", Toast.LENGTH_SHORT).show()
        }

        // 1. Loading modal while decrypting media
        if (isMediaLoading && (isPhoto || isVideo)) {
            Dialog(onDismissRequest = {
                isMediaLoading = false
                selectedFile = null
                showFullScreenViewer = false
            }) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(24.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp), strokeWidth = 3.dp)
                        Column {
                            Text(
                                text = if (isPhoto) "Opening Photo…" else "Opening Video…",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Decrypting securely on-the-fly…",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // 2. Full-screen Photo viewer
        if (!isMediaLoading && isPhoto && decryptedImageBitmap != null) {
            FullScreenImageViewer(
                bitmap = decryptedImageBitmap!!,
                fileName = fileToPreview.name,
                onRestore = { handleRestore(fileToPreview) },
                onDelete = { handleDelete(fileToPreview) },
                onClose = {
                    selectedFile = null
                    decryptedImageBitmap = null
                    showFullScreenViewer = false
                }
            )
        }

        // 3. Full-screen Video viewer (ExoPlayer)
        if (!isMediaLoading && isVideo && decryptedVideoBytes != null) {
            FullScreenVideoViewer(
                decryptedBytes = decryptedVideoBytes!!,
                fileName = fileToPreview.name,
                onRestore = { handleRestore(fileToPreview) },
                onDelete = { handleDelete(fileToPreview) },
                onClose = {
                    selectedFile = null
                    decryptedVideoBytes = null
                    showFullScreenViewer = false
                }
            )
        }

        // 4. Encrypted Note detail dialog
        if (!isPhoto && !isVideo) {
            Dialog(
                onDismissRequest = {
                    selectedFile = null
                    decryptedContent = null
                },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .padding(8.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(fileToPreview.name.removeSuffix(".enc"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1)
                                Text("Decrypted Note", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                            }
                            IconButton(onClick = { handleDelete(fileToPreview) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        Card(
                            modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 280.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Box(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                                Text(
                                    text = decryptedContent ?: "Decrypting…",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }

                        Spacer(Modifier.height(20.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            Button(
                                onClick = {
                                    selectedFile = null
                                    decryptedContent = null
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
    }

    // ── Batch Delete Confirm ──────────────────────────────────────────────────
    if (showBatchDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showBatchDeleteConfirm = false },
            title = { Text("Delete ${selectedFilePaths.size} Files?", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to permanently delete the selected files from the vault? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        currentTabFiles.filter { selectedFilePaths.contains(it.absolutePath) }.forEach { it.delete() }
                        showBatchDeleteConfirm = false; isSelectionMode = false; selectedFilePaths = emptySet(); loadSecureFiles()
                        Toast.makeText(context, "Files deleted from vault", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { showBatchDeleteConfirm = false }) { Text("Cancel") } }
        )
    }

    // ── Create Note Dialog ────────────────────────────────────────────────────
    if (showCreateDialog) {
        Dialog(onDismissRequest = { showCreateDialog = false }) {
            Card(modifier = Modifier.fillMaxWidth().padding(16.dp), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
                    Text("New Encrypted Note", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(value = newFileName, onValueChange = { newFileName = it }, label = { Text("Note Title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(value = newFileContent, onValueChange = { newFileContent = it }, label = { Text("Note Content") }, modifier = Modifier.fillMaxWidth().height(140.dp))
                    if (saveError != null) { Spacer(Modifier.height(8.dp)); Text(saveError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    Spacer(Modifier.height(24.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { showCreateDialog = false }) { Text("Cancel") }
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = {
                            if (newFileName.isBlank() || newFileContent.isBlank()) {
                                saveError = "Fields cannot be empty."
                            } else {
                                try {
                                    val dir = getVaultDir()
                                    val file = File(dir, "${newFileName.trim()}.enc")
                                    file.writeText(CryptoManager.encrypt(newFileContent), Charsets.UTF_8)
                                    showCreateDialog = false; loadSecureFiles()
                                } catch (e: Exception) { saveError = "Failed to encrypt/save note." }
                            }
                        }) { Text("Encrypt & Save") }
                    }
                }
            }
        }
    }

    // ── Photo Source Action Dialog (Camera vs Gallery) ────────────────────────
    if (showPhotoActionDialog) {
        AlertDialog(
            onDismissRequest = { showPhotoActionDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Image, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Text("Add Secure Photo", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Choose how you want to add private photos to your encrypted vault:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Option 1: Take Photo with Camera
                    Surface(
                        onClick = {
                            showPhotoActionDialog = false
                            launchCameraWithPermissionCheck("photo")
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.CameraAlt,
                                contentDescription = "Camera",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(30.dp)
                            )
                            Spacer(Modifier.width(14.dp))
                            Column {
                                Text(
                                    "Take Private Photo",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "Snaps directly into vault. Never touches gallery.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Option 2: Import from Gallery
                    Surface(
                        onClick = {
                            showPhotoActionDialog = false
                            photosPickerLauncher.launch("image/*")
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.PhotoLibrary,
                                contentDescription = "Gallery",
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(30.dp)
                            )
                            Spacer(Modifier.width(14.dp))
                            Column {
                                Text(
                                    "Import from Gallery",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "Encrypts and automatically removes original from gallery.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showPhotoActionDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // ── Video Source Action Dialog (Camera vs Gallery) ────────────────────────
    if (showVideoActionDialog) {
        AlertDialog(
            onDismissRequest = { showVideoActionDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Movie, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Text("Add Secure Video", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Choose how you want to add private videos to your encrypted vault:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Option 1: Record Video with Camera
                    Surface(
                        onClick = {
                            showVideoActionDialog = false
                            launchCameraWithPermissionCheck("video")
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Videocam,
                                contentDescription = "Video Camera",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(30.dp)
                            )
                            Spacer(Modifier.width(14.dp))
                            Column {
                                Text(
                                    "Record Private Video",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "Records directly into vault. Never touches gallery.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Option 2: Import from Gallery
                    Surface(
                        onClick = {
                            showVideoActionDialog = false
                            videosPickerLauncher.launch("video/*")
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.VideoLibrary,
                                contentDescription = "Gallery",
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(30.dp)
                            )
                            Spacer(Modifier.width(14.dp))
                            Column {
                                Text(
                                    "Import from Gallery",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "Encrypts and automatically removes original from gallery.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showVideoActionDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

// ─── Reusable UI components ────────────────────────────────────────────────────

@Composable
private fun PinDots(filled: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        for (i in 1..4) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .background(
                        color = if (filled >= i) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        shape = CircleShape
                    )
            )
        }
    }
}

@Composable
private fun VaultKeypad(onKey: (String) -> Unit) {
    val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("C", "0", "⌫"))
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        for (row in rows) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                for (key in row) {
                    Button(
                        onClick = { onKey(key) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (key == "C" || key == "⌫") MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (key == "C" || key == "⌫") MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.size(68.dp)
                    ) {
                        Text(key, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
