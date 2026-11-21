package com.example.presentation.screens.vault

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Article
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
import androidx.compose.ui.graphics.asImageBitmap
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.domain.model.PasswordValidator
import com.example.domain.model.PatternValidator
import com.example.domain.model.PinMatchResult
import com.example.domain.model.PinValidator
import com.example.domain.model.UnlockMethod
import com.example.presentation.screens.lock.IntruderCameraCapture
import com.example.ui.components.Icon3DBadge
import com.example.ui.components.PatternLockView
import com.example.ui.components.Tactile3DCard
import com.example.ui.theme.*
import com.example.util.AlarmAlertHelper
import com.example.util.CryptoManager
import com.example.util.MasterKeyManager
import com.example.util.SecurePreferences
import com.example.util.VaultMediaCleaner
import com.example.util.VaultRestoreManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class PendingCaptureType { PHOTO, VIDEO }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecureVaultScreen(
    context: Context,
    onBack: () -> Unit,
    onSuppressNextRelock: () -> Unit = {}
) {
    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val securePrefs = remember { SecurePreferences.getInstance(context) }
    val settings by securePrefs.settingsFlow.collectAsStateWithLifecycle()

    var accessState by remember { mutableStateOf<VaultAccessState>(VaultAccessState.Locked()) }
    var modalState by remember { mutableStateOf<VaultModalState>(VaultModalState.None) }
    var progressState by remember { mutableStateOf<VaultProgressState>(VaultProgressState.Hidden) }
    var recoveryState by remember { mutableStateOf<VaultRecoveryState>(VaultRecoveryState.Hidden) }

    var pendingTempCaptureFile by remember { mutableStateOf<File?>(null) }
    var pendingCaptureType by remember { mutableStateOf<PendingCaptureType?>(null) }
    var pendingDeletePaths by remember { mutableStateOf<List<String>>(emptyList()) }
    var hasAllFilesAccess by remember { mutableStateOf(VaultMediaCleaner.hasAllFilesAccess()) }

    val dateFormatter = remember { SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()) }

    fun reloadVaultItems(mode: VaultMode) {
        coroutineScope.launch {
            val loaded = withContext(Dispatchers.IO) { VaultStorage.loadVaultItems(context, mode) }
            val current = accessState
            if (current is VaultAccessState.Unlocked && current.mode == mode) {
                accessState = current.copy(items = loaded, isLoading = false)
            }
        }
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { VaultStorage.clearTempVideoCache(context.cacheDir) }
    }

    DisposableEffect(Unit) {
        onDispose {
            pendingTempCaptureFile?.delete()
            VaultStorage.clearTempVideoCache(context.cacheDir)
        }
    }

    val deleteRequestLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && pendingDeletePaths.isNotEmpty()) {
            VaultMediaCleaner.scanFilePaths(context, pendingDeletePaths)
            Toast.makeText(context, "Original media deleted from Gallery", Toast.LENGTH_SHORT).show()
        } else if (pendingDeletePaths.isNotEmpty()) {
            Toast.makeText(context, "Encrypted in vault (system deletion cancelled)", Toast.LENGTH_SHORT).show()
        }
        pendingDeletePaths = emptyList()
    }

    fun handleImportedUris(uris: List<Uri>, tab: VaultTab) {
        val current = accessState as? VaultAccessState.Unlocked ?: return
        if (uris.isEmpty()) return

        coroutineScope.launch(Dispatchers.IO) {
            val resolver = context.contentResolver
            val isVideo = tab == VaultTab.VIDEOS
            val sizes = uris.map { uri -> resolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: 0L }
            val grandTotal = sizes.sum()
            var grandProcessed = 0L

            withContext(Dispatchers.Main) {
                progressState = VaultProgressState.Active(
                    title = if (isVideo) "Encrypting Videos" else "Encrypting Photos",
                    totalBytes = grandTotal
                )
            }

            val unDeletedUris = mutableListOf<Uri>()
            val directlyDeletedPaths = mutableListOf<String>()
            val consentPaths = mutableListOf<String>()

            uris.forEachIndexed { index, uri ->
                try {
                    val mediaEntry = VaultMediaCleaner.resolveCanonicalEntry(context, uri, isVideo = isVideo)
                    val fallbackName = "${if (isVideo) "video" else "photo"}_${System.currentTimeMillis()}_$index.${if (isVideo) "mp4" else "jpg"}"
                    val displayName = mediaEntry.displayName.ifBlank {
                        VaultStorage.resolveDisplayName(context, uri, fallbackName)
                    }.removeSuffix(".enc")

                    val fileSize = sizes[index]
                    val baseProcessed = grandProcessed

                    withContext(Dispatchers.Main) {
                        progressState = VaultProgressState.Active(
                            title = if (isVideo) "Encrypting Videos" else "Encrypting Photos",
                            stepText = "Item ${index + 1} of ${uris.size}",
                            fileText = displayName,
                            fraction = if (grandTotal > 0) baseProcessed.toFloat() / grandTotal else index.toFloat() / uris.size,
                            bytesProcessed = baseProcessed,
                            totalBytes = grandTotal
                        )
                    }

                    resolver.openInputStream(uri)?.let { inputStream ->
                        var lastReportedPercent = -1
                        val encrypted = CryptoManager.encryptBytesWithProgress(
                            inputStream = inputStream,
                            totalBytes = fileSize
                        ) { readSoFar, _ ->
                            val currentGrand = baseProcessed + readSoFar
                            val percent = if (grandTotal > 0) ((currentGrand * 100) / grandTotal).toInt() else 0
                            if (percent != lastReportedPercent) {
                                lastReportedPercent = percent
                                coroutineScope.launch(Dispatchers.Main) {
                                    val currentProgress = progressState
                                    if (currentProgress is VaultProgressState.Active) {
                                        progressState = currentProgress.copy(
                                            bytesProcessed = currentGrand,
                                            fraction = if (grandTotal > 0) currentGrand.toFloat() / grandTotal else (index + 1f) / uris.size
                                        )
                                    }
                                }
                            }
                        }

                        VaultStorage.saveEncryptedBytes(
                            context = context,
                            mode = current.mode,
                            tab = tab,
                            baseFileNameWithoutEnc = displayName,
                            encryptedBytes = encrypted
                        )
                        grandProcessed += fileSize

                        val deleteResult = VaultMediaCleaner.deleteOriginalMedia(context, uri, mediaEntry)
                        if (deleteResult.isDeleted) {
                            deleteResult.realPath?.takeIf { it.isNotBlank() }?.let(directlyDeletedPaths::add)
                        } else {
                            unDeletedUris.add(deleteResult.canonicalUri ?: uri)
                            deleteResult.realPath?.takeIf { it.isNotBlank() }?.let(consentPaths::add)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            withContext(Dispatchers.Main) {
                progressState = VaultProgressState.Hidden
                reloadVaultItems(current.mode)
                if (directlyDeletedPaths.isNotEmpty()) {
                    VaultMediaCleaner.scanFilePaths(context, directlyDeletedPaths)
                }
                if (unDeletedUris.isNotEmpty()) {
                    val sender = VaultMediaCleaner.createSystemDeleteIntentSender(context, unDeletedUris)
                    if (sender != null) {
                        pendingDeletePaths = consentPaths
                        onSuppressNextRelock()
                        deleteRequestLauncher.launch(IntentSenderRequest.Builder(sender).build())
                    } else {
                        Toast.makeText(context, "${uris.size} item(s) encrypted in vault", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(context, "${uris.size} item(s) encrypted and removed from Gallery", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val photosPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        handleImportedUris(uris, VaultTab.PHOTOS)
    }

    val videosPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        handleImportedUris(uris, VaultTab.VIDEOS)
    }

    fun launchPickerForTab(tab: VaultTab) {
        onSuppressNextRelock()
        if (tab == VaultTab.PHOTOS) {
            photosPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        } else {
            videosPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
        }
    }

    fun handleCameraCaptureResult(success: Boolean, isVideo: Boolean) {
        val tempFile = pendingTempCaptureFile
        pendingTempCaptureFile = null
        val current = accessState as? VaultAccessState.Unlocked
        if (!success || tempFile == null || !tempFile.exists() || tempFile.length() == 0L || current == null) {
            tempFile?.delete()
            return
        }
        val tab = if (isVideo) VaultTab.VIDEOS else VaultTab.PHOTOS
        val baseName = if (isVideo) {
            "VID_PRIVATE_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.mp4"
        } else {
            "IMG_PRIVATE_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.jpg"
        }

        coroutineScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                progressState = VaultProgressState.Active(
                    title = if (isVideo) "Encrypting Private Video" else "Encrypting Private Photo",
                    stepText = "Securing capture inside AES-256 Vault",
                    fileText = baseName,
                    fraction = 0.6f,
                    totalBytes = tempFile.length()
                )
            }
            try {
                val encrypted = CryptoManager.encryptBytes(tempFile.readBytes())
                VaultStorage.saveEncryptedBytes(context, current.mode, tab, baseName, encrypted)
                tempFile.delete()
                withContext(Dispatchers.Main) {
                    progressState = VaultProgressState.Hidden
                    reloadVaultItems(current.mode)
                    Toast.makeText(context, "Captured privately into encrypted vault", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                tempFile.delete()
                withContext(Dispatchers.Main) {
                    progressState = VaultProgressState.Hidden
                    Toast.makeText(context, "Failed to save capture: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val cameraPhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success -> handleCameraCaptureResult(success, isVideo = false) }

    val cameraVideoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CaptureVideo()
    ) { success -> handleCameraCaptureResult(success, isVideo = true) }

    fun launchCameraCapture(type: PendingCaptureType) {
        try {
            val ext = if (type == PendingCaptureType.VIDEO) "mp4" else "jpg"
            val tempFile = File(context.cacheDir, "temp_private_capture_${System.currentTimeMillis()}.$ext")
            tempFile.createNewFile()
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", tempFile)
            pendingTempCaptureFile = tempFile
            onSuppressNextRelock()
            if (type == PendingCaptureType.VIDEO) cameraVideoLauncher.launch(uri) else cameraPhotoLauncher.launch(uri)
        } catch (e: Exception) {
            Toast.makeText(context, "Unable to launch camera: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        val pending = pendingCaptureType
        pendingCaptureType = null
        if (granted && pending != null) {
            launchCameraCapture(pending)
        } else if (!granted) {
            Toast.makeText(context, "Camera permission is required for private capture", Toast.LENGTH_SHORT).show()
        }
    }

    fun startPrivateCapture(type: PendingCaptureType) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            launchCameraCapture(type)
        } else {
            pendingCaptureType = type
            onSuppressNextRelock()
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val allFilesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        hasAllFilesAccess = VaultMediaCleaner.hasAllFilesAccess()
        val modal = modalState
        if (modal is VaultModalState.ConfirmAllFilesAccess) {
            modalState = VaultModalState.None
            launchPickerForTab(modal.pendingTab)
        }
    }

    BackHandler {
        val current = accessState
        when {
            modalState !is VaultModalState.None -> modalState = VaultModalState.None
            current is VaultAccessState.Unlocked && current.selection is VaultSelectionState.Active -> {
                accessState = current.copy(selection = VaultSelectionState.Inactive)
            }
            else -> onBack()
        }
    }

    when (val currentAccess = accessState) {
        is VaultAccessState.Locked -> {
            val activeVaultMethod = when {
                currentAccess.forcePinMode -> UnlockMethod.PIN
                settings.unlockMethod == UnlockMethod.PATTERN &&
                    PatternValidator.isValidEncodedPattern(settings.masterPattern) -> UnlockMethod.PATTERN
                settings.unlockMethod == UnlockMethod.PASSWORD &&
                    PasswordValidator.isValidPassword(settings.masterPassword) -> UnlockMethod.PASSWORD
                else -> UnlockMethod.PIN
            }

            VaultLockedContent(
                state = currentAccess,
                activeMethod = activeVaultMethod,
                canTogglePinMode = settings.unlockMethod != UnlockMethod.PIN,
                preferredMethodLabel = settings.unlockMethod.label,
                recoveryState = recoveryState,
                securityQuestion = remember { securePrefs.getSecurityQuestion() },
                onBack = onBack,
                onKey = { key ->
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    when (key) {
                        "C" -> accessState = currentAccess.copy(enteredPin = "", errorMessage = null)
                        "⌫" -> accessState = currentAccess.copy(
                            enteredPin = currentAccess.enteredPin.dropLast(1),
                            errorMessage = null
                        )
                        else -> if (currentAccess.enteredPin.length < 4) {
                            val updated = currentAccess.enteredPin + key
                            if (updated.length < 4) {
                                accessState = currentAccess.copy(enteredPin = updated, errorMessage = null)
                            } else {
                                when (PinValidator.verifyVaultPin(updated, settings)) {
                                    PinMatchResult.MasterMatch -> {
                                        IntruderCameraCapture.stopActiveVideoIfRunning()
                                        AlarmAlertHelper.stopBreakInAlarm(context)
                                        accessState = VaultAccessState.Unlocked(mode = VaultMode.REAL, isLoading = true)
                                        reloadVaultItems(VaultMode.REAL)
                                    }
                                    PinMatchResult.DecoyMatch -> {
                                        IntruderCameraCapture.stopActiveVideoIfRunning()
                                        AlarmAlertHelper.stopBreakInAlarm(context)
                                        accessState = VaultAccessState.Unlocked(mode = VaultMode.DECOY, isLoading = true)
                                        reloadVaultItems(VaultMode.DECOY)
                                    }
                                    PinMatchResult.Mismatch -> {
                                        val nextFails = currentAccess.failedAttempts + 1
                                        (context as? FragmentActivity)?.let { hostActivity ->
                                            IntruderCameraCapture.captureEvidence(
                                                activity = hostActivity,
                                                settings = settings,
                                                targetPackageName = context.packageName,
                                                targetAppLabel = "Secure Vault",
                                                authMethodLabel = "PIN",
                                                failedAttemptNumber = nextFails
                                            )
                                        }
                                        if (nextFails >= 3 && settings.breakInSirenEnabled) {
                                            AlarmAlertHelper.playBreakInAlarm(context)
                                        }
                                        accessState = currentAccess.copy(
                                            enteredPin = "",
                                            failedAttempts = nextFails,
                                            errorMessage = "Incorrect PIN (Attempt $nextFails)"
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                onPatternStart = {
                    if (currentAccess.errorMessage != null) {
                        accessState = currentAccess.copy(errorMessage = null)
                    }
                },
                onPatternComplete = { nodes ->
                    if (!PatternValidator.isValidPattern(nodes)) {
                        accessState = currentAccess.copy(
                            patternAttemptCount = currentAccess.patternAttemptCount + 1,
                            errorMessage = "Connect at least ${PatternValidator.MIN_NODES} dots"
                        )
                    } else if (PinValidator.verifyAppUnlockPattern(nodes, settings)) {
                        IntruderCameraCapture.stopActiveVideoIfRunning()
                        AlarmAlertHelper.stopBreakInAlarm(context)
                        accessState = VaultAccessState.Unlocked(mode = VaultMode.REAL, isLoading = true)
                        reloadVaultItems(VaultMode.REAL)
                    } else {
                        val nextFails = currentAccess.failedAttempts + 1
                        (context as? FragmentActivity)?.let { hostActivity ->
                            IntruderCameraCapture.captureEvidence(
                                activity = hostActivity,
                                settings = settings,
                                targetPackageName = context.packageName,
                                targetAppLabel = "Secure Vault",
                                authMethodLabel = "Pattern",
                                failedAttemptNumber = nextFails
                            )
                        }
                        if (nextFails >= 3 && settings.breakInSirenEnabled) {
                            AlarmAlertHelper.playBreakInAlarm(context)
                        }
                        accessState = currentAccess.copy(
                            patternAttemptCount = currentAccess.patternAttemptCount + 1,
                            failedAttempts = nextFails,
                            errorMessage = "Incorrect Pattern (Attempt $nextFails)"
                        )
                    }
                },
                onPasswordChanged = { raw ->
                    accessState = currentAccess.copy(
                        enteredPassword = raw.take(PasswordValidator.MAX_LENGTH),
                        errorMessage = null
                    )
                },
                onTogglePasswordVisible = {
                    accessState = currentAccess.copy(passwordVisible = !currentAccess.passwordVisible)
                },
                onPasswordSubmit = {
                    if (PinValidator.verifyAppUnlockPassword(currentAccess.enteredPassword, settings)) {
                        IntruderCameraCapture.stopActiveVideoIfRunning()
                        AlarmAlertHelper.stopBreakInAlarm(context)
                        accessState = VaultAccessState.Unlocked(mode = VaultMode.REAL, isLoading = true)
                        reloadVaultItems(VaultMode.REAL)
                    } else {
                        val nextFails = currentAccess.failedAttempts + 1
                        (context as? FragmentActivity)?.let { hostActivity ->
                            IntruderCameraCapture.captureEvidence(
                                activity = hostActivity,
                                settings = settings,
                                targetPackageName = context.packageName,
                                targetAppLabel = "Secure Vault",
                                authMethodLabel = "Password",
                                failedAttemptNumber = nextFails
                            )
                        }
                        if (nextFails >= 3 && settings.breakInSirenEnabled) {
                            AlarmAlertHelper.playBreakInAlarm(context)
                        }
                        accessState = currentAccess.copy(
                            enteredPassword = "",
                            failedAttempts = nextFails,
                            errorMessage = "Incorrect Password (Attempt $nextFails)"
                        )
                    }
                },
                onTogglePinMode = {
                    accessState = currentAccess.copy(
                        forcePinMode = !currentAccess.forcePinMode,
                        enteredPin = "",
                        enteredPassword = "",
                        errorMessage = null
                    )
                },
                onOpenRecovery = { recoveryState = VaultRecoveryState.Visible() },
                onUpdateRecovery = { recoveryState = it },
                onVerifyRecovery = { input ->
                    val isMaster = MasterKeyManager.isMasterKey(input)
                    val isAnswer = securePrefs.verifySecurityAnswer(input)
                    if (isMaster || isAnswer) {
                        IntruderCameraCapture.stopActiveVideoIfRunning()
                        AlarmAlertHelper.stopBreakInAlarm(context)
                        recoveryState = VaultRecoveryState.Hidden
                        accessState = VaultAccessState.Unlocked(mode = VaultMode.REAL, isLoading = true)
                        reloadVaultItems(VaultMode.REAL)
                    } else {
                        recoveryState = VaultRecoveryState.Visible(input = input, error = "Invalid recovery credentials.")
                    }
                }
            )
        }

        is VaultAccessState.Unlocked -> {
            val tabItems = remember(currentAccess.items, currentAccess.activeTab) {
                currentAccess.items.filter { it.tab == currentAccess.activeTab }
            }
            val notesCount = remember(currentAccess.items) { currentAccess.items.count { it.tab == VaultTab.NOTES } }
            val photosCount = remember(currentAccess.items) { currentAccess.items.count { it.tab == VaultTab.PHOTOS } }
            val videosCount = remember(currentAccess.items) { currentAccess.items.count { it.tab == VaultTab.VIDEOS } }
            val selectionActive = currentAccess.selection as? VaultSelectionState.Active

            fun restoreSingleItem(item: VaultItem) {
                coroutineScope.launch(Dispatchers.IO) {
                    withContext(Dispatchers.Main) {
                        progressState = VaultProgressState.Active(
                            title = "Restoring to Gallery",
                            stepText = "Decrypting file",
                            fileText = item.displayName,
                            fraction = 0.6f
                        )
                    }
                    val result = VaultRestoreManager.restoreFileToGallery(context, item.file, deleteVaultFileOnSuccess = true)
                    withContext(Dispatchers.Main) {
                        progressState = VaultProgressState.Hidden
                        if (result.isSuccess) {
                            modalState = VaultModalState.None
                            reloadVaultItems(currentAccess.mode)
                            Toast.makeText(context, "Restored to Gallery", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Restore failed: ${result.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }

            fun deleteSingleItem(item: VaultItem) {
                coroutineScope.launch(Dispatchers.IO) {
                    item.file.delete()
                    withContext(Dispatchers.Main) {
                        modalState = VaultModalState.None
                        reloadVaultItems(currentAccess.mode)
                        Toast.makeText(context, "Deleted from vault", Toast.LENGTH_SHORT).show()
                    }
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
                            if (selectionActive != null) {
                                Text("${selectionActive.selectedPaths.size} Selected", fontWeight = FontWeight.Bold)
                            } else {
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("Secure Vault", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                        if (currentAccess.mode == VaultMode.DECOY) {
                                            Spacer(Modifier.width(8.dp))
                                            Surface(color = ObsidianSurfaceVariant, shape = CircleShape) {
                                                Text(
                                                    "Decoy Mode",
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = TextSecondary
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        if (currentAccess.mode == VaultMode.DECOY) "Standard Storage" else "AES-256 Encrypted",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = CyberCyan
                                    )
                                }
                            }
                        },
                        navigationIcon = {
                            IconButton(
                                onClick = {
                                    if (selectionActive != null) {
                                        accessState = currentAccess.copy(selection = VaultSelectionState.Inactive)
                                    } else {
                                        onBack()
                                    }
                                },
                                modifier = Modifier.testTag("vault_back_btn")
                            ) {
                                Icon(
                                    imageVector = if (selectionActive != null) Icons.Default.Close else Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back"
                                )
                            }
                        },
                        actions = {
                            if (selectionActive != null) {
                                val allPaths = tabItems.map { it.file.absolutePath }.toSet()
                                val allSelected = allPaths.isNotEmpty() && selectionActive.selectedPaths.containsAll(allPaths)
                                IconButton(
                                    onClick = {
                                        accessState = currentAccess.copy(
                                            selection = VaultSelectionState.Active(if (allSelected) emptySet() else allPaths)
                                        )
                                    }
                                ) {
                                    Icon(
                                        imageVector = if (allSelected) Icons.Default.Deselect else Icons.Default.SelectAll,
                                        contentDescription = "Toggle Select All"
                                    )
                                }
                            } else {
                                if (currentAccess.activeTab != VaultTab.NOTES && tabItems.isNotEmpty()) {
                                    IconButton(
                                        onClick = {
                                            accessState = currentAccess.copy(selection = VaultSelectionState.Active())
                                        }
                                    ) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = "Select Multiple")
                                    }
                                }
                                IconButton(
                                    onClick = {
                                        modalState = VaultModalState.None
                                        accessState = VaultAccessState.Locked()
                                    },
                                    modifier = Modifier.testTag("vault_lock_now_btn")
                                ) {
                                    Icon(Icons.Default.Lock, contentDescription = "Lock Vault", tint = ElectricPurple)
                                }
                            }
                        }
                    )
                },
                bottomBar = {
                    if (selectionActive != null && selectionActive.selectedPaths.isNotEmpty()) {
                        val selectedItems = tabItems.filter { selectionActive.selectedPaths.contains(it.file.absolutePath) }
                        Surface(
                            color = ObsidianSurface,
                            border = BorderStroke(1.dp, ObsidianBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { modalState = VaultModalState.ConfirmBatchDelete(selectedItems) },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = CrimsonAlert),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Delete (${selectedItems.size})")
                                }
                                if (currentAccess.activeTab != VaultTab.NOTES) {
                                    Button(
                                        onClick = {
                                            coroutineScope.launch(Dispatchers.IO) {
                                                var successCount = 0
                                                selectedItems.forEachIndexed { idx, item ->
                                                    withContext(Dispatchers.Main) {
                                                        progressState = VaultProgressState.Active(
                                                            title = "Restoring to Gallery",
                                                            stepText = "Item ${idx + 1} of ${selectedItems.size}",
                                                            fileText = item.displayName,
                                                            fraction = (idx + 1f) / selectedItems.size
                                                        )
                                                    }
                                                    if (VaultRestoreManager.restoreFileToGallery(context, item.file, true).isSuccess) {
                                                        successCount++
                                                    }
                                                }
                                                withContext(Dispatchers.Main) {
                                                    progressState = VaultProgressState.Hidden
                                                    accessState = currentAccess.copy(selection = VaultSelectionState.Inactive)
                                                    reloadVaultItems(currentAccess.mode)
                                                    Toast.makeText(context, "Restored $successCount files to Gallery", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        },
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier.weight(1.2f)
                                    ) {
                                        Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Restore (${selectedItems.size})")
                                    }
                                }
                            }
                        }
                    }
                },
                floatingActionButton = {
                    if (selectionActive == null) {
                        FloatingActionButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                modalState = when (currentAccess.activeTab) {
                                    VaultTab.NOTES -> VaultModalState.CreateNote()
                                    VaultTab.PHOTOS -> VaultModalState.ChooseMediaSource(VaultTab.PHOTOS)
                                    VaultTab.VIDEOS -> VaultModalState.ChooseMediaSource(VaultTab.VIDEOS)
                                }
                            },
                            containerColor = ElectricPurple,
                            contentColor = PureWhite,
                            modifier = Modifier.testTag("vault_add_fab")
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
                    TabRow(
                        selectedTabIndex = currentAccess.activeTab.ordinal,
                        containerColor = ObsidianSurface,
                        contentColor = PureWhite
                    ) {
                        VaultTab.entries.forEach { tab ->
                            val count = when (tab) {
                                VaultTab.NOTES -> notesCount
                                VaultTab.PHOTOS -> photosCount
                                VaultTab.VIDEOS -> videosCount
                            }
                            Tab(
                                selected = currentAccess.activeTab == tab,
                                onClick = {
                                    accessState = currentAccess.copy(
                                        activeTab = tab,
                                        selection = VaultSelectionState.Inactive
                                    )
                                },
                                text = { Text("${tab.label} ($count)") },
                                icon = {
                                    Icon(
                                        imageVector = when (tab) {
                                            VaultTab.NOTES -> Icons.Default.Folder
                                            VaultTab.PHOTOS -> Icons.Default.Image
                                            VaultTab.VIDEOS -> Icons.Default.Movie
                                        },
                                        contentDescription = tab.label
                                    )
                                },
                                modifier = Modifier.testTag("vault_tab_${tab.name.lowercase()}")
                            )
                        }
                    }

                    if (currentAccess.activeTab != VaultTab.NOTES && !hasAllFilesAccess) {
                        Tactile3DCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon3DBadge(icon = Icons.Default.Security, contentDescription = null, size = 36.dp)
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Auto-Clean Gallery", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = PureWhite)
                                    Text(
                                        "Allow All Files Access to auto-remove originals without OS confirmation dialogs.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextSecondary
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        onSuppressNextRelock()
                                        allFilesLauncher.launch(VaultMediaCleaner.getAllFilesAccessIntent(context))
                                    },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text("Enable", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }

                    if (tabItems.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(24.dp)
                            ) {
                                Icon3DBadge(
                                    icon = when (currentAccess.activeTab) {
                                        VaultTab.NOTES -> Icons.Default.Folder
                                        VaultTab.PHOTOS -> Icons.Default.Image
                                        VaultTab.VIDEOS -> Icons.Default.Movie
                                    },
                                    contentDescription = "Empty",
                                    size = 72.dp
                                )
                                Spacer(Modifier.height(16.dp))
                                Text(
                                    text = "No ${currentAccess.activeTab.label} Stored",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = PureWhite
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = "Tap + to encrypt and store private ${currentAccess.activeTab.label.lowercase()} inside your AES-256 vault.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            contentPadding = PaddingValues(14.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(tabItems, key = { it.file.absolutePath }) { item ->
                                val isSelected = selectionActive?.selectedPaths?.contains(item.file.absolutePath) == true
                                Tactile3DCard(
                                    highlighted = isSelected,
                                    shape = RoundedCornerShape(16.dp),
                                    onClick = {
                                        if (selectionActive != null) {
                                            val updatedSet = if (isSelected) {
                                                selectionActive.selectedPaths - item.file.absolutePath
                                            } else {
                                                selectionActive.selectedPaths + item.file.absolutePath
                                            }
                                            accessState = currentAccess.copy(selection = VaultSelectionState.Active(updatedSet))
                                        } else {
                                            when (item.tab) {
                                                VaultTab.NOTES -> {
                                                    modalState = VaultModalState.PreviewNote(item, DecryptState.Loading)
                                                    coroutineScope.launch {
                                                        val decState = withContext(Dispatchers.IO) {
                                                            try {
                                                                DecryptState.Ready(CryptoManager.decrypt(item.file.readText(Charsets.UTF_8)))
                                                            } catch (e: Exception) {
                                                                DecryptState.Error(e.message ?: "Decryption failed")
                                                            }
                                                        }
                                                        modalState = VaultModalState.PreviewNote(item, decState)
                                                    }
                                                }
                                                VaultTab.PHOTOS -> {
                                                    modalState = VaultModalState.PreviewPhoto(item, DecryptState.Loading)
                                                    coroutineScope.launch {
                                                        val decState = withContext(Dispatchers.IO) {
                                                            try {
                                                                val bytes = CryptoManager.decryptBytes(item.file.readBytes())
                                                                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                                                                if (bmp != null) DecryptState.Ready(bmp)
                                                                else DecryptState.Error("Unsupported image format")
                                                            } catch (e: Exception) {
                                                                DecryptState.Error(e.message ?: "Decryption failed")
                                                            }
                                                        }
                                                        modalState = VaultModalState.PreviewPhoto(item, decState)
                                                    }
                                                }
                                                VaultTab.VIDEOS -> {
                                                    modalState = VaultModalState.PreviewVideo(item, DecryptState.Loading)
                                                    coroutineScope.launch {
                                                        val decState = withContext(Dispatchers.IO) {
                                                            try {
                                                                val bytes = CryptoManager.decryptBytes(item.file.readBytes())
                                                                val tempVideo = File(context.cacheDir, "temp_vault_video_${System.currentTimeMillis()}.mp4")
                                                                tempVideo.writeBytes(bytes)
                                                                DecryptState.Ready(tempVideo)
                                                            } catch (e: Exception) {
                                                                DecryptState.Error(e.message ?: "Decryption failed")
                                                            }
                                                        }
                                                        modalState = VaultModalState.PreviewVideo(item, decState)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                ) {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(if (item.tab == VaultTab.NOTES) 86.dp else 132.dp)
                                                .background(ObsidianElevated)
                                        ) {
                                            when (item.tab) {
                                                VaultTab.PHOTOS -> EncryptedImageThumbnail(item.file)
                                                VaultTab.VIDEOS -> Box(
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .background(Brush.verticalGradient(listOf(Color(0xFF281448), ObsidianBlack))),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Surface(shape = CircleShape, color = Color.White.copy(alpha = 0.16f), modifier = Modifier.size(46.dp)) {
                                                        Box(contentAlignment = Alignment.Center) {
                                                            Icon(Icons.Default.PlayArrow, contentDescription = "Play Video", tint = PureWhite, modifier = Modifier.size(28.dp))
                                                        }
                                                    }
                                                }
                                                VaultTab.NOTES -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                    Icon3DBadge(icon = Icons.AutoMirrored.Filled.Article, contentDescription = "Note", size = 44.dp)
                                                }
                                            }
                                            if (selectionActive != null) {
                                                Box(modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                                                    Icon(
                                                        imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                                        contentDescription = "Selection",
                                                        tint = if (isSelected) ElectricPurple else PureWhite
                                                    )
                                                }
                                            }
                                        }
                                        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                                            Text(item.displayName, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, color = PureWhite)
                                            Spacer(Modifier.height(2.dp))
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text("${(item.sizeBytes / 1024).coerceAtLeast(1)} KB", style = MaterialTheme.typography.labelSmall, color = TextTertiary)
                                                Text(dateFormatter.format(Date(item.lastModified)), style = MaterialTheme.typography.labelSmall, color = NeonViolet)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            when (val modal = modalState) {
                VaultModalState.None -> Unit

                is VaultModalState.CreateNote -> {
                    Dialog(onDismissRequest = { modalState = VaultModalState.None }) {
                        Tactile3DCard(highlighted = true, modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(22.dp)) {
                                Text("New Encrypted Note", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = PureWhite)
                                Spacer(Modifier.height(14.dp))
                                OutlinedTextField(
                                    value = modal.title,
                                    onValueChange = { modalState = modal.copy(title = it, error = null) },
                                    label = { Text("Note Title") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(Modifier.height(10.dp))
                                OutlinedTextField(
                                    value = modal.content,
                                    onValueChange = { modalState = modal.copy(content = it, error = null) },
                                    label = { Text("Note Content") },
                                    modifier = Modifier.fillMaxWidth().height(140.dp)
                                )
                                if (modal.error != null) {
                                    Spacer(Modifier.height(8.dp))
                                    Text(modal.error, color = CrimsonAlert, style = MaterialTheme.typography.bodySmall)
                                }
                                Spacer(Modifier.height(18.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                    TextButton(onClick = { modalState = VaultModalState.None }) { Text("Cancel") }
                                    Spacer(Modifier.width(8.dp))
                                    Button(
                                        enabled = !modal.isSaving,
                                        onClick = {
                                            if (modal.title.isBlank() || modal.content.isBlank()) {
                                                modalState = modal.copy(error = "Title and content cannot be empty.")
                                            } else {
                                                modalState = modal.copy(isSaving = true)
                                                coroutineScope.launch(Dispatchers.IO) {
                                                    try {
                                                        VaultStorage.saveEncryptedNote(context, currentAccess.mode, modal.title, modal.content)
                                                        withContext(Dispatchers.Main) {
                                                            modalState = VaultModalState.None
                                                            reloadVaultItems(currentAccess.mode)
                                                        }
                                                    } catch (e: Exception) {
                                                        withContext(Dispatchers.Main) {
                                                            modalState = modal.copy(isSaving = false, error = "Encryption failed: ${e.message}")
                                                        }
                                                    }
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

                is VaultModalState.PreviewNote -> {
                    Dialog(
                        onDismissRequest = { modalState = VaultModalState.None },
                        properties = DialogProperties(usePlatformDefaultWidth = false)
                    ) {
                        Tactile3DCard(highlighted = true, modifier = Modifier.fillMaxWidth(0.92f)) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(modal.item.displayName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = PureWhite)
                                        Text("Decrypted Note", style = MaterialTheme.typography.labelSmall, color = CyberCyan)
                                    }
                                    IconButton(onClick = { deleteSingleItem(modal.item) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = CrimsonAlert)
                                    }
                                }
                                Spacer(Modifier.height(14.dp))
                                Surface(
                                    color = ObsidianElevated,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 280.dp)
                                ) {
                                    Box(modifier = Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
                                        when (val dec = modal.contentState) {
                                            DecryptState.Loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                                            is DecryptState.Ready -> Text(dec.data, color = PureWhite, style = MaterialTheme.typography.bodyMedium)
                                            is DecryptState.Error -> Text(dec.message, color = CrimsonAlert)
                                        }
                                    }
                                }
                                Spacer(Modifier.height(16.dp))
                                Button(onClick = { modalState = VaultModalState.None }, modifier = Modifier.align(Alignment.End)) {
                                    Text("Close")
                                }
                            }
                        }
                    }
                }

                is VaultModalState.PreviewPhoto -> {
                    when (val dec = modal.bitmapState) {
                        DecryptState.Loading -> DecryptingDialog(label = "Opening Photo…") { modalState = VaultModalState.None }
                        is DecryptState.Ready -> FullScreenImageViewer(
                            bitmap = dec.data,
                            fileName = modal.item.file.name,
                            onRestore = { restoreSingleItem(modal.item) },
                            onDelete = { deleteSingleItem(modal.item) },
                            onClose = { modalState = VaultModalState.None }
                        )
                        is DecryptState.Error -> {
                            Toast.makeText(context, dec.message, Toast.LENGTH_SHORT).show()
                            modalState = VaultModalState.None
                        }
                    }
                }

                is VaultModalState.PreviewVideo -> {
                    when (val dec = modal.videoState) {
                        DecryptState.Loading -> DecryptingDialog(label = "Opening Video…") { modalState = VaultModalState.None }
                        is DecryptState.Ready -> FullScreenVideoViewer(
                            videoFile = dec.data,
                            fileName = modal.item.file.name,
                            onRestore = { restoreSingleItem(modal.item) },
                            onDelete = { deleteSingleItem(modal.item) },
                            onClose = { modalState = VaultModalState.None }
                        )
                        is DecryptState.Error -> {
                            Toast.makeText(context, dec.message, Toast.LENGTH_SHORT).show()
                            modalState = VaultModalState.None
                        }
                    }
                }

                is VaultModalState.ChooseMediaSource -> {
                    val isVideo = modal.tab == VaultTab.VIDEOS
                    AlertDialog(
                        onDismissRequest = { modalState = VaultModalState.None },
                        containerColor = ObsidianSurface,
                        title = {
                            Text(if (isVideo) "Add Secure Video" else "Add Secure Photo", fontWeight = FontWeight.Bold, color = PureWhite)
                        },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Tactile3DCard(
                                    highlighted = true,
                                    onClick = {
                                        modalState = VaultModalState.None
                                        startPrivateCapture(if (isVideo) PendingCaptureType.VIDEO else PendingCaptureType.PHOTO)
                                    }
                                ) {
                                    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon3DBadge(
                                            icon = if (isVideo) Icons.Default.Videocam else Icons.Default.CameraAlt,
                                            contentDescription = null,
                                            size = 40.dp
                                        )
                                        Spacer(Modifier.width(12.dp))
                                        Column {
                                            Text(if (isVideo) "Record Private Video" else "Take Private Photo", fontWeight = FontWeight.Bold, color = PureWhite)
                                            Text("Captures directly into encrypted vault", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                        }
                                    }
                                }
                                Tactile3DCard(
                                    onClick = {
                                        if (!VaultMediaCleaner.hasAllFilesAccess()) {
                                            modalState = VaultModalState.ConfirmAllFilesAccess(modal.tab)
                                        } else {
                                            modalState = VaultModalState.None
                                            launchPickerForTab(modal.tab)
                                        }
                                    }
                                ) {
                                    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon3DBadge(
                                            icon = if (isVideo) Icons.Default.VideoLibrary else Icons.Default.PhotoLibrary,
                                            contentDescription = null,
                                            size = 40.dp,
                                            accentStart = CyberCyan,
                                            accentEnd = Color(0xFF1D4ED8)
                                        )
                                        Spacer(Modifier.width(12.dp))
                                        Column {
                                            Text("Import from Gallery", fontWeight = FontWeight.Bold, color = PureWhite)
                                            Text("Encrypts & cleans original media", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                        }
                                    }
                                }
                            }
                        },
                        confirmButton = {},
                        dismissButton = {
                            TextButton(onClick = { modalState = VaultModalState.None }) { Text("Cancel") }
                        }
                    )
                }

                is VaultModalState.ConfirmAllFilesAccess -> {
                    AlertDialog(
                        onDismissRequest = {
                            val tab = modal.pendingTab
                            modalState = VaultModalState.None
                            launchPickerForTab(tab)
                        },
                        containerColor = ObsidianSurface,
                        title = { Text("Auto-Clean Gallery", fontWeight = FontWeight.Bold, color = PureWhite) },
                        text = {
                            Text(
                                "Allow 'All files access' to automatically remove imported media from your gallery, or tap Continue to approve deletions per item.",
                                color = TextSecondary
                            )
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    onSuppressNextRelock()
                                    allFilesLauncher.launch(VaultMediaCleaner.getAllFilesAccessIntent(context))
                                }
                            ) {
                                Text("Allow Access")
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = {
                                    val tab = modal.pendingTab
                                    modalState = VaultModalState.None
                                    launchPickerForTab(tab)
                                }
                            ) {
                                Text("Continue")
                            }
                        }
                    )
                }

                is VaultModalState.ConfirmBatchDelete -> {
                    AlertDialog(
                        onDismissRequest = { modalState = VaultModalState.None },
                        containerColor = ObsidianSurface,
                        title = { Text("Delete ${modal.targets.size} Files?", fontWeight = FontWeight.Bold, color = PureWhite) },
                        text = { Text("This will permanently delete the selected encrypted files from your vault.", color = TextSecondary) },
                        confirmButton = {
                            Button(
                                colors = ButtonDefaults.buttonColors(containerColor = CrimsonAlert),
                                onClick = {
                                    coroutineScope.launch(Dispatchers.IO) {
                                        modal.targets.forEach { it.file.delete() }
                                        withContext(Dispatchers.Main) {
                                            modalState = VaultModalState.None
                                            accessState = currentAccess.copy(selection = VaultSelectionState.Inactive)
                                            reloadVaultItems(currentAccess.mode)
                                        }
                                    }
                                }
                            ) {
                                Text("Delete")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { modalState = VaultModalState.None }) { Text("Cancel") }
                        }
                    )
                }
            }
        }
    }

    val activeProgress = progressState
    if (activeProgress is VaultProgressState.Active) {
        CryptoProgressModal(progress = activeProgress)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VaultLockedContent(
    state: VaultAccessState.Locked,
    activeMethod: UnlockMethod,
    canTogglePinMode: Boolean,
    preferredMethodLabel: String,
    recoveryState: VaultRecoveryState,
    securityQuestion: String,
    onBack: () -> Unit,
    onKey: (String) -> Unit,
    onPatternStart: () -> Unit,
    onPatternComplete: (List<Int>) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onTogglePasswordVisible: () -> Unit,
    onPasswordSubmit: () -> Unit,
    onTogglePinMode: () -> Unit,
    onOpenRecovery: () -> Unit,
    onUpdateRecovery: (VaultRecoveryState) -> Unit,
    onVerifyRecovery: (String) -> Unit
) {
    Scaffold(
        containerColor = ObsidianBlack,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ObsidianBlack, titleContentColor = PureWhite),
                title = { Text("Secure Vault Access", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(86.dp)
                    .shadow(16.dp, RoundedCornerShape(24.dp), ambientColor = ElectricPurple, spotColor = ElectricPurple)
                    .clip(RoundedCornerShape(24.dp))
                    .border(1.5.dp, NeonViolet.copy(alpha = 0.7f), RoundedCornerShape(24.dp))
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_vault_3d),
                    contentDescription = "3D Encrypted Vault",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(Modifier.height(14.dp))
            Text(
                text = when (activeMethod) {
                    UnlockMethod.PIN -> "Enter Vault PIN"
                    UnlockMethod.PATTERN -> "Draw Vault Pattern"
                    UnlockMethod.PASSWORD -> "Enter Vault Password"
                },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = PureWhite
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = when (activeMethod) {
                    UnlockMethod.PIN -> "Enter your Master PIN or Decoy PIN to unlock"
                    UnlockMethod.PATTERN -> "Draw your Master Pattern to decrypt files"
                    UnlockMethod.PASSWORD -> "Enter your Master Password to decrypt files"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(20.dp))

            when (activeMethod) {
                UnlockMethod.PATTERN -> {
                    if (!state.errorMessage.isNullOrBlank()) {
                        Text(state.errorMessage, color = CrimsonAlert, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(12.dp))
                    }
                    PatternLockView(
                        isError = state.errorMessage != null,
                        resetKey = state.patternAttemptCount,
                        testTag = "vault_pattern_pad",
                        modifier = Modifier.fillMaxWidth(0.84f),
                        onPatternStart = onPatternStart,
                        onPatternComplete = onPatternComplete
                    )
                }

                UnlockMethod.PASSWORD -> {
                    OutlinedTextField(
                        value = state.enteredPassword,
                        onValueChange = onPasswordChanged,
                        label = { Text("Master Password") },
                        singleLine = true,
                        isError = state.errorMessage != null,
                        visualTransformation = if (state.passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
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
                                    imageVector = if (state.passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (state.passwordVisible) "Hide Password" else "Show Password",
                                    tint = NeonViolet
                                )
                            }
                        },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 360.dp)
                            .testTag("vault_password_input")
                    )
                    if (!state.errorMessage.isNullOrBlank()) {
                        Spacer(Modifier.height(10.dp))
                        Text(state.errorMessage, color = CrimsonAlert, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = onPasswordSubmit,
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricPurple, contentColor = PureWhite),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 360.dp)
                            .height(50.dp)
                            .testTag("vault_password_submit_btn")
                    ) {
                        Text("Unlock Vault", fontWeight = FontWeight.Bold)
                    }
                }

                UnlockMethod.PIN -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        repeat(4) { idx ->
                            val filled = idx < state.enteredPin.length
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .background(if (filled) NeonViolet else ObsidianSurfaceVariant)
                                    .border(1.dp, if (filled) PureWhite.copy(alpha = 0.7f) else ObsidianBorder, CircleShape)
                            )
                        }
                    }

                    if (!state.errorMessage.isNullOrBlank()) {
                        Spacer(Modifier.height(14.dp))
                        Text(state.errorMessage, color = CrimsonAlert, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                    }

                    Spacer(Modifier.height(24.dp))
                    val rows = remember { listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("C", "0", "⌫")) }
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        rows.forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                                row.forEach { key ->
                                    val isAction = key == "C" || key == "⌫"
                                    Surface(
                                        onClick = { onKey(key) },
                                        shape = RoundedCornerShape(18.dp),
                                        color = if (isAction) ObsidianElevated else ObsidianSurface,
                                        contentColor = if (isAction) LavenderGlow else PureWhite,
                                        border = BorderStroke(1.dp, ObsidianBorder),
                                        shadowElevation = 6.dp,
                                        modifier = Modifier
                                            .size(70.dp)
                                            .testTag("vault_key_$key")
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            if (key == "⌫") {
                                                Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = "Backspace")
                                            } else {
                                                Text(key, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (canTogglePinMode) {
                Spacer(Modifier.height(14.dp))
                TextButton(
                    onClick = onTogglePinMode,
                    modifier = Modifier.testTag("vault_toggle_pin_mode_btn")
                ) {
                    Text(
                        text = if (state.forcePinMode) "Use $preferredMethodLabel instead" else "Switch to PIN (or Decoy PIN)",
                        color = CyberCyan,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onOpenRecovery) {
                Text("Forgot Credential? / Master Recovery", color = NeonViolet, fontWeight = FontWeight.SemiBold)
            }
        }
    }

    if (recoveryState is VaultRecoveryState.Visible) {
        AlertDialog(
            onDismissRequest = { onUpdateRecovery(VaultRecoveryState.Hidden) },
            containerColor = ObsidianSurface,
            title = { Text("Vault Recovery", fontWeight = FontWeight.Bold, color = PureWhite) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Unlock using your security answer or Universal Master Key.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    if (securityQuestion.isNotBlank()) {
                        Surface(color = ObsidianElevated, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("Security Question:", style = MaterialTheme.typography.labelSmall, color = NeonViolet)
                                Text(securityQuestion, color = PureWhite, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                    OutlinedTextField(
                        value = recoveryState.input,
                        onValueChange = { onUpdateRecovery(recoveryState.copy(input = it, error = null)) },
                        label = { Text(if (securityQuestion.isNotBlank()) "Security Answer or Master Key" else "Master Recovery Key") },
                        singleLine = true,
                        isError = recoveryState.error != null,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (recoveryState.error != null) {
                        Text(recoveryState.error, color = CrimsonAlert, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(onClick = { onVerifyRecovery(recoveryState.input) }) { Text("Unlock Vault") }
            },
            dismissButton = {
                TextButton(onClick = { onUpdateRecovery(VaultRecoveryState.Hidden) }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun DecryptingDialog(label: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Tactile3DCard(highlighted = true) {
            Row(
                modifier = Modifier.padding(24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                CircularProgressIndicator(modifier = Modifier.size(30.dp), strokeWidth = 3.dp, color = ElectricPurple)
                Column {
                    Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = PureWhite)
                    Text("Decrypting securely on-the-fly…", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
            }
        }
    }
}
