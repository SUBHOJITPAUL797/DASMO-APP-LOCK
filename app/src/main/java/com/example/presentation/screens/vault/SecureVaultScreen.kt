package com.example.presentation.screens.vault

import android.content.Context
import android.graphics.BitmapFactory
import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import com.example.util.CryptoManager
import com.example.util.SecurePreferences
import kotlinx.coroutines.Dispatchers
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
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Image, contentDescription = "Loading Image", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecureVaultScreen(context: Context, onBack: () -> Unit) {
    var isUnlocked by remember { mutableStateOf(false) }
    var isDecoyMode by remember { mutableStateOf(false) }
    var enteredPin by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }
    
    var secureFiles by remember { mutableStateOf<List<File>>(emptyList()) }
    var selectedFile by remember { mutableStateOf<File?>(null) }
    var decryptedContent by remember { mutableStateOf<String?>(null) }
    
    var showCreateDialog by remember { mutableStateOf(false) }
    var newFileName by remember { mutableStateOf("") }
    var newFileContent by remember { mutableStateOf("") }
    var saveError by remember { mutableStateOf<String?>(null) }
    
    val dateFormatter = remember { SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()) }
    val securePrefs = remember { SecurePreferences(context) }
    val correctPin = remember { securePrefs.getString("app_pin", "1234") }
    val decoyPin = remember { securePrefs.getString("decoy_pin", "") }

    fun getVaultDir(): File {
        val dirName = if (isDecoyMode) "decoy_vault" else "secure_vault"
        val dir = context.getDir(dirName, Context.MODE_PRIVATE)
        if (!dir.exists()) dir.mkdirs()

        // If decoy mode and empty, pre-populate dummy notes
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
    val coroutineScope = rememberCoroutineScope()

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val contentResolver = context.contentResolver
                    var fileName = "photo_${System.currentTimeMillis()}.jpg"
                    
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
                    if (!fileName.endsWith(".enc")) {
                        fileName = "$fileName.enc"
                    }
                    
                    val inputStream = contentResolver.openInputStream(uri)
                    val bytes = inputStream?.readBytes() ?: return@launch
                    
                    val encryptedBytes = CryptoManager.encryptBytes(bytes)
                    val dir = getVaultDir()
                    val targetFile = File(dir, fileName)
                    targetFile.writeBytes(encryptedBytes)
                    
                    withContext(Dispatchers.Main) {
                        loadSecureFiles()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val contentResolver = context.contentResolver
                    var fileName = "video_${System.currentTimeMillis()}.mp4"
                    
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
                    if (!fileName.endsWith(".enc")) {
                        fileName = "$fileName.enc"
                    }
                    
                    val inputStream = contentResolver.openInputStream(uri)
                    val bytes = inputStream?.readBytes() ?: return@launch
                    
                    val encryptedBytes = CryptoManager.encryptBytes(bytes)
                    val dir = getVaultDir()
                    val targetFile = File(dir, fileName)
                    targetFile.writeBytes(encryptedBytes)
                    
                    withContext(Dispatchers.Main) {
                        loadSecureFiles()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
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
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Encrypted",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(64.dp)
                    )
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
                                    .size(16.dp)
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
                    
                    Spacer(modifier = Modifier.height(32.dp))
                    
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
                }
            }
        }
    } else {
        val notesFiles = secureFiles.filter { !it.name.endsWith(".jpg.enc") && !it.name.endsWith(".png.enc") && !it.name.endsWith(".jpeg.enc") && !it.name.endsWith(".mp4.enc") && !it.name.endsWith(".3gp.enc") && !it.name.endsWith(".mkv.enc") }
        val photosFiles = secureFiles.filter { it.name.endsWith(".jpg.enc") || it.name.endsWith(".png.enc") || it.name.endsWith(".jpeg.enc") }
        val videosFiles = secureFiles.filter { it.name.endsWith(".mp4.enc") || it.name.endsWith(".3gp.enc") || it.name.endsWith(".mkv.enc") }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
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
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = { isUnlocked = false; isDecoyMode = false }) {
                            Icon(Icons.Default.Lock, contentDescription = "Lock Vault", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = {
                        if (activeTab == 0) {
                            newFileName = ""
                            newFileContent = ""
                            saveError = null
                            showCreateDialog = true
                        } else if (activeTab == 1) {
                            photoPickerLauncher.launch("image/*")
                        } else {
                            videoPickerLauncher.launch("video/*")
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Encrypted File")
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
                        onClick = { activeTab = 0 },
                        text = { Text("Notes (${notesFiles.size})") },
                        icon = { Icon(Icons.Default.Folder, contentDescription = "Notes") }
                    )
                    Tab(
                        selected = activeTab == 1,
                        onClick = { activeTab = 1 },
                        text = { Text("Photos (${photosFiles.size})") },
                        icon = { Icon(Icons.Default.Image, contentDescription = "Photos") }
                    )
                    Tab(
                        selected = activeTab == 2,
                        onClick = { activeTab = 2 },
                        text = { Text("Videos (${videosFiles.size})") },
                        icon = { Icon(Icons.Default.Movie, contentDescription = "Videos") }
                    )
                }

                val currentTabFiles = when (activeTab) {
                    0 -> notesFiles
                    1 -> photosFiles
                    else -> videosFiles
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
                                    1 -> "Import sensitive photos from your gallery. They are fully encrypted on-device."
                                    else -> "Import private videos. They can only be played inside this vault on-the-fly."
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
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
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
                                    },
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
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
    }

    if (selectedFile != null) {
        val isPhoto = selectedFile!!.name.endsWith(".jpg.enc") || selectedFile!!.name.endsWith(".png.enc") || selectedFile!!.name.endsWith(".jpeg.enc")
        val isVideo = selectedFile!!.name.endsWith(".mp4.enc") || selectedFile!!.name.endsWith(".3gp.enc") || selectedFile!!.name.endsWith(".mkv.enc")

        LaunchedEffect(selectedFile) {
            if (isPhoto) {
                withContext(Dispatchers.IO) {
                    try {
                        val bytes = selectedFile!!.readBytes()
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
                        val bytes = selectedFile!!.readBytes()
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
                        .padding(24.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = selectedFile!!.name.removeSuffix(".enc"),
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
                                selectedFile?.delete()
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
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Button(
                        onClick = {
                            selectedFile = null
                            decryptedContent = null
                            decryptedImageBitmap = null
                            decryptedVideoFile?.delete()
                            decryptedVideoFile = null
                        },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Close")
                    }
                }
            }
        }
    }

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
