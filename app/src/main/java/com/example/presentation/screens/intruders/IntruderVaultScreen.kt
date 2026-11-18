package com.example.presentation.screens.intruders

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed interface IntruderContentState {
    data object Loading : IntruderContentState
    data object Empty : IntruderContentState
    data class Loaded(val photos: List<File>) : IntruderContentState
}

sealed interface IntruderModalState {
    data object None : IntruderModalState
    data class PreviewPhoto(val photo: File) : IntruderModalState
    data object ConfirmClearAll : IntruderModalState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IntruderVaultScreen(context: Context, onBack: () -> Unit) {
    val coroutineScope = rememberCoroutineScope()
    var contentState by remember { mutableStateOf<IntruderContentState>(IntruderContentState.Loading) }
    var modalState by remember { mutableStateOf<IntruderModalState>(IntruderModalState.None) }
    val dateFormatter = remember { SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()) }

    fun reloadPhotos() {
        coroutineScope.launch {
            val files = withContext(Dispatchers.IO) {
                val dir = context.getDir("intruders", Context.MODE_PRIVATE)
                if (dir.exists()) {
                    dir.listFiles()?.filter { it.isFile }?.sortedByDescending { it.lastModified() } ?: emptyList()
                } else {
                    emptyList()
                }
            }
            contentState = if (files.isEmpty()) IntruderContentState.Empty else IntruderContentState.Loaded(files)
        }
    }

    LaunchedEffect(Unit) {
        reloadPhotos()
    }

    BackHandler {
        if (modalState != IntruderModalState.None) {
            modalState = IntruderModalState.None
        } else {
            onBack()
        }
    }

    val photoCount = (contentState as? IntruderContentState.Loaded)?.photos?.size ?: 0

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Intruder Vault", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "$photoCount break-in attempts recorded",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("intruder_back_btn")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (photoCount > 0) {
                        IconButton(
                            onClick = { modalState = IntruderModalState.ConfirmClearAll },
                            modifier = Modifier.testTag("intruder_clear_all_btn")
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Clear All", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        when (val state = contentState) {
            is IntruderContentState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            is IntruderContentState.Empty -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Safe",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(80.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "No Intruders Detected",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Whenever someone tries an incorrect PIN or biometric, DASMO LOCK snaps a silent front-camera selfie.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            is IntruderContentState.Loaded -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    items(state.photos, key = { it.absolutePath }) { photo ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(3f / 4f)
                                .clickable { modalState = IntruderModalState.PreviewPhoto(photo) },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                AsyncImage(
                                    model = photo,
                                    contentDescription = "Intruder",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f))
                                        .padding(6.dp)
                                ) {
                                    Text(
                                        text = dateFormatter.format(Date(photo.lastModified())),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.align(Alignment.Center)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        when (val modal = modalState) {
            is IntruderModalState.None -> Unit

            is IntruderModalState.PreviewPhoto -> {
                Dialog(onDismissRequest = { modalState = IntruderModalState.None }) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column {
                            AsyncImage(
                                model = modal.photo,
                                contentDescription = "Intruder Large",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(320.dp)
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "Intruder Caught",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = dateFormatter.format(Date(modal.photo.lastModified())),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        coroutineScope.launch {
                                            withContext(Dispatchers.IO) { modal.photo.delete() }
                                            modalState = IntruderModalState.None
                                            reloadPhotos()
                                        }
                                    }
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }

            is IntruderModalState.ConfirmClearAll -> {
                AlertDialog(
                    onDismissRequest = { modalState = IntruderModalState.None },
                    title = { Text("Clear Intruder Log?") },
                    text = { Text("This will permanently delete all $photoCount captured intruder photos.") },
                    confirmButton = {
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    withContext(Dispatchers.IO) {
                                        val dir = context.getDir("intruders", Context.MODE_PRIVATE)
                                        dir.listFiles()?.forEach { it.delete() }
                                    }
                                    modalState = IntruderModalState.None
                                    reloadPhotos()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Delete All")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { modalState = IntruderModalState.None }) {
                            Text("Cancel")
                        }
                    }
                )
            }
        }
    }
}
