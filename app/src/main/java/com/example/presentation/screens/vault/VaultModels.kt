package com.example.presentation.screens.vault

import androidx.compose.ui.graphics.ImageBitmap
import java.io.File

enum class VaultMode {
    REAL,
    DECOY
}

enum class VaultTab(val label: String) {
    NOTES("Notes"),
    PHOTOS("Photos"),
    VIDEOS("Videos")
}

data class VaultItem(
    val file: File,
    val displayName: String,
    val tab: VaultTab,
    val sizeBytes: Long,
    val lastModified: Long
)

sealed interface VaultSelectionState {
    data object Inactive : VaultSelectionState
    data class Active(val selectedPaths: Set<String> = emptySet()) : VaultSelectionState
}

sealed interface VaultAccessState {
    data class Locked(
        val enteredPin: String = "",
        val enteredPassword: String = "",
        val passwordVisible: Boolean = false,
        val patternAttemptCount: Int = 0,
        val forcePinMode: Boolean = false,
        val errorMessage: String? = null
    ) : VaultAccessState

    data class Unlocked(
        val mode: VaultMode,
        val activeTab: VaultTab = VaultTab.NOTES,
        val items: List<VaultItem> = emptyList(),
        val isLoading: Boolean = false,
        val selection: VaultSelectionState = VaultSelectionState.Inactive
    ) : VaultAccessState
}

sealed interface DecryptState<out T> {
    data object Loading : DecryptState<Nothing>
    data class Ready<T>(val data: T) : DecryptState<T>
    data class Error(val message: String) : DecryptState<Nothing>
}

sealed interface VaultProgressState {
    data object Hidden : VaultProgressState
    data class Active(
        val title: String,
        val stepText: String = "",
        val fileText: String = "",
        val fraction: Float = 0f,
        val bytesProcessed: Long = 0L,
        val totalBytes: Long = 0L
    ) : VaultProgressState
}

sealed interface VaultRecoveryState {
    data object Hidden : VaultRecoveryState
    data class Visible(val input: String = "", val error: String? = null) : VaultRecoveryState
}

sealed interface VaultModalState {
    data object None : VaultModalState

    data class CreateNote(
        val title: String = "",
        val content: String = "",
        val error: String? = null,
        val isSaving: Boolean = false
    ) : VaultModalState

    data class PreviewNote(
        val item: VaultItem,
        val contentState: DecryptState<String> = DecryptState.Loading
    ) : VaultModalState

    data class PreviewPhoto(
        val item: VaultItem,
        val bitmapState: DecryptState<ImageBitmap> = DecryptState.Loading
    ) : VaultModalState

    data class PreviewVideo(
        val item: VaultItem,
        val videoState: DecryptState<File> = DecryptState.Loading
    ) : VaultModalState

    data class ChooseMediaSource(val tab: VaultTab) : VaultModalState

    data class ConfirmAllFilesAccess(val pendingTab: VaultTab) : VaultModalState

    data class ConfirmBatchDelete(val targets: List<VaultItem>) : VaultModalState
}
