package com.example.presentation.screens.settings

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.data.db.AppGuardDatabase
import com.example.domain.repository.AppLockRepository
import com.example.service.AppLockService
import com.example.ui.components.Icon3DBadge
import com.example.ui.components.Section3DHeader
import com.example.ui.components.Tactile3DCard
import com.example.ui.theme.*
import com.example.util.SecurePreferences
import com.example.util.update.AppUpdateManager
import com.example.util.update.UpdateStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun UpdatesCategoryContent(
    context: Context,
    scope: CoroutineScope,
    updateManager: AppUpdateManager,
    updateStatus: UpdateStatus,
    onUpdateStatusChange: (UpdateStatus) -> Unit,
    onSuppressNextRelock: () -> Unit
) {
    val haptic = LocalHapticFeedback.current

    Section3DHeader(
        icon = Icons.Default.SystemUpdate,
        title = "Software Updates",
        subtitle = "Over-the-air GitHub Releases delivery",
        accentStart = Color(0xFFEC4899),
        accentEnd = Color(0xFF9D174D)
    )

    Tactile3DCard(highlighted = true, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        listOf(Color(0xFF281448), Color(0xFF130D22))
                    )
                )
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon3DBadge(
                        icon = Icons.Default.Verified,
                        contentDescription = null,
                        size = 40.dp,
                        accentStart = ElectricPurple,
                        accentEnd = DeepViolet
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "DASMO LOCK",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = PureWhite
                        )
                        Text(
                            text = "Repo: ${AppUpdateManager.GITHUB_OWNER}/${AppUpdateManager.GITHUB_REPO}",
                            style = MaterialTheme.typography.labelSmall,
                            color = NeonViolet
                        )
                    }
                }

                Surface(
                    shape = CircleShape,
                    color = ContainerPurple,
                    border = BorderStroke(1.dp, ElectricPurple.copy(alpha = 0.6f))
                ) {
                    Text(
                        text = "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = PureWhite,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }
        }
    }

    Tactile3DCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (updateStatus) {
                is UpdateStatus.Idle -> {
                    Text(
                        text = "Check GitHub Releases to keep your app lock engine, vault encryption, and intruder defenses up to date.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            scope.launch {
                                onUpdateStatusChange(UpdateStatus.Checking)
                                onUpdateStatusChange(updateManager.checkForUpdate(context))
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ElectricPurple,
                            contentColor = PureWhite
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("updates_check_btn")
                    ) {
                        Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Check for Updates", fontWeight = FontWeight.Bold)
                    }
                }

                is UpdateStatus.Checking -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.5.dp,
                            color = NeonViolet
                        )
                        Text(
                            text = "Connecting to GitHub Releases API...",
                            style = MaterialTheme.typography.bodySmall,
                            color = PureWhite
                        )
                    }
                }

                is UpdateStatus.UpToDate -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = NeonEmerald,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "App is up to date (v${updateStatus.currentVersion})",
                            fontWeight = FontWeight.Bold,
                            color = NeonEmerald
                        )
                    }
                    FilledTonalButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            scope.launch {
                                onUpdateStatusChange(UpdateStatus.Checking)
                                onUpdateStatusChange(updateManager.checkForUpdate(context))
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Text("Re-check")
                    }
                }

                is UpdateStatus.UpdateAvailable -> {
                    val info = updateStatus.updateInfo
                    Text(
                        text = "New Version Available: v${info.latestVersion}",
                        fontWeight = FontWeight.Bold,
                        color = NeonViolet,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = "Package Size: ${info.formattedSize}",
                        style = MaterialTheme.typography.labelSmall,
                        color = CyberCyan
                    )
                    Surface(
                        color = ObsidianSurfaceVariant,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, ObsidianBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = info.changelog.take(260) + if (info.changelog.length > 260) "..." else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                scope.launch {
                                    try {
                                        onUpdateStatusChange(
                                            UpdateStatus.Downloading(info, 0, 0, info.apkSizeBytes)
                                        )
                                        val apk = updateManager.downloadApk(context, info) { progress ->
                                            onUpdateStatusChange(progress)
                                        }
                                        onUpdateStatusChange(UpdateStatus.ReadyToInstall(info, apk))
                                        withContext(Dispatchers.IO) {
                                            val prefs = SecurePreferences.getInstance(context)
                                            val repo = AppLockRepository(
                                                AppGuardDatabase.getDatabase(context).appLockDao(),
                                                prefs
                                            )
                                            repo.syncBackupNow()
                                            prefs.syncPersistentBackup()
                                        }
                                        AppLockService.markPackageInstallersUnlocked()
                                        onSuppressNextRelock()
                                        updateManager.installApk(context, apk)
                                    } catch (e: Exception) {
                                        onUpdateStatusChange(
                                            UpdateStatus.Error(e.localizedMessage ?: "Download failed")
                                        )
                                    }
                                }
                            },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ElectricPurple,
                                contentColor = PureWhite
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                        ) {
                            Text("Update Now (APK)", fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = {
                                onSuppressNextRelock()
                                updateManager.openReleaseInBrowser(context, info.htmlUrl)
                            },
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, ObsidianBorder),
                            modifier = Modifier.height(48.dp)
                        ) {
                            Text("View", color = LavenderGlow)
                        }
                    }
                }

                is UpdateStatus.Downloading -> {
                    Text(
                        text = "Downloading v${updateStatus.updateInfo.latestVersion}... ${updateStatus.progressPercent}%",
                        fontWeight = FontWeight.Bold,
                        color = PureWhite
                    )
                    LinearProgressIndicator(
                        progress = { updateStatus.progressPercent / 100f },
                        color = ElectricPurple,
                        trackColor = ObsidianSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                }

                is UpdateStatus.ReadyToInstall -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.DownloadDone,
                            contentDescription = null,
                            tint = NeonEmerald,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Download Complete — Ready to Install",
                            fontWeight = FontWeight.Bold,
                            color = NeonEmerald
                        )
                    }
                    Button(
                        onClick = {
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    val prefs = SecurePreferences.getInstance(context)
                                    val repo = AppLockRepository(
                                        AppGuardDatabase.getDatabase(context).appLockDao(),
                                        prefs
                                    )
                                    repo.syncBackupNow()
                                    prefs.syncPersistentBackup()
                                }
                                AppLockService.markPackageInstallersUnlocked()
                                onSuppressNextRelock()
                                updateManager.installApk(context, updateStatus.apkFile)
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ElectricPurple,
                            contentColor = PureWhite
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Text("Install APK Now", fontWeight = FontWeight.Bold)
                    }
                }

                is UpdateStatus.Error -> {
                    Text(
                        text = updateStatus.message,
                        color = CrimsonAlert,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FilledTonalButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                scope.launch {
                                    onUpdateStatusChange(UpdateStatus.Checking)
                                    onUpdateStatusChange(updateManager.checkForUpdate(context))
                                }
                            },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                        ) {
                            Text("Retry")
                        }
                        OutlinedButton(
                            onClick = {
                                onSuppressNextRelock()
                                updateManager.openReleaseInBrowser(context, AppUpdateManager.REPO_RELEASES_WEB_URL)
                            },
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, ObsidianBorder),
                            modifier = Modifier.height(48.dp)
                        ) {
                            Text("GitHub", color = LavenderGlow)
                        }
                    }
                }
            }
        }
    }
}
