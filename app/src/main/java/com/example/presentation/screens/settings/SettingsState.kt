package com.example.presentation.screens.settings

import com.example.domain.model.AutoLockPolicy
import com.example.domain.model.PasswordValidator
import com.example.domain.model.PatternValidator
import com.example.domain.model.PinValidationResult
import com.example.domain.model.PinValidator
import com.example.domain.model.SecuritySettings
import com.example.domain.model.TimeUnitOption
import com.example.domain.model.UnlockMethod

object SecurityQuestions {
    val DEFAULT_LIST = listOf(
        "What was the name of your first school?",
        "What is your mother's maiden name?",
        "What city were you born in?",
        "What was your childhood nickname?",
        "What is your favorite book or movie?"
    )
}

enum class SettingsTab(val label: String) {
    SECURITY("Access & Auth"),
    DECOY("Decoy Mode"),
    DISGUISE("Camouflage"),
    AUTO_LOCK("Auto-Lock"),
    UPDATES("Updates")
}

sealed interface AutoLockDraftSelection {
    data class Preset(val timeoutMillis: Long) : AutoLockDraftSelection
    data class Custom(val numberInput: String, val unit: TimeUnitOption) : AutoLockDraftSelection
}

sealed interface SettingsModalState {
    data object None : SettingsModalState
    data object ConfirmDiscardChanges : SettingsModalState
    data class ConfirmDisableUninstallProtection(
        val pinOrMasterKey: String = "",
        val useRecoveryInput: Boolean = false,
        val error: String? = null
    ) : SettingsModalState
}

enum class PatternDraftStep {
    IDLE_OR_DRAW_NEW,
    AWAITING_CONFIRM,
    READY_TO_SAVE
}

data class SettingsDraftState(
    val activeTab: SettingsTab = SettingsTab.SECURITY,
    val unlockMethod: UnlockMethod = UnlockMethod.PIN,
    val newPin: String = "",
    val confirmPin: String = "",
    val pinError: String? = null,
    val savedMasterPattern: String = "",
    val newPattern: String = "",
    val confirmPattern: String = "",
    val patternStep: PatternDraftStep = PatternDraftStep.IDLE_OR_DRAW_NEW,
    val patternError: String? = null,
    val savedMasterPassword: String = "",
    val newPassword: String = "",
    val confirmPassword: String = "",
    val passwordError: String? = null,
    val savedRecoveryQuestion: String = "",
    val recoveryQuestion: String = SecurityQuestions.DEFAULT_LIST.first(),
    val recoveryAnswerInput: String = "",
    val recoveryError: String? = null,
    val decoyPinInput: String = "",
    val decoyPinError: String? = null,
    val dynamicTimePinEnabled: Boolean = false,
    val scrambleKeypadEnabled: Boolean = false,
    val breakInSirenEnabled: Boolean = false,
    val preventScreenshots: Boolean = true,
    val calculatorDisguiseEnabled: Boolean = false,
    val uninstallProtectionEnabled: Boolean = false,
    val autoLockNewAppsEnabled: Boolean = false,
    val autoLockSelection: AutoLockDraftSelection = AutoLockDraftSelection.Preset(300_000L),
    val customTimeoutError: String? = null
) {
    fun hasUnsavedChanges(saved: SecuritySettings): Boolean {
        val baseline = fromSettings(saved, savedRecoveryQuestion)
        val recoveryChanged = recoveryAnswerInput.isNotBlank() ||
            (savedRecoveryQuestion.isNotBlank() && recoveryQuestion != savedRecoveryQuestion)
        return unlockMethod != baseline.unlockMethod ||
            newPin.isNotEmpty() ||
            confirmPin.isNotEmpty() ||
            newPattern.isNotEmpty() ||
            confirmPattern.isNotEmpty() ||
            newPassword.isNotEmpty() ||
            confirmPassword.isNotEmpty() ||
            recoveryChanged ||
            decoyPinInput != baseline.decoyPinInput ||
            dynamicTimePinEnabled != baseline.dynamicTimePinEnabled ||
            scrambleKeypadEnabled != baseline.scrambleKeypadEnabled ||
            breakInSirenEnabled != baseline.breakInSirenEnabled ||
            preventScreenshots != baseline.preventScreenshots ||
            calculatorDisguiseEnabled != baseline.calculatorDisguiseEnabled ||
            uninstallProtectionEnabled != baseline.uninstallProtectionEnabled ||
            autoLockNewAppsEnabled != baseline.autoLockNewAppsEnabled ||
            autoLockSelection != baseline.autoLockSelection
    }

    companion object {
        fun fromSettings(
            settings: SecuritySettings,
            savedQuestion: String = ""
        ): SettingsDraftState {
            val policy = AutoLockPolicy.fromMillis(settings.autoLockTimeoutMillis)
            val selection = when (policy) {
                is AutoLockPolicy.Immediate -> AutoLockDraftSelection.Preset(0L)
                is AutoLockPolicy.Preset -> AutoLockDraftSelection.Preset(policy.timeoutMillis)
                is AutoLockPolicy.Custom -> AutoLockDraftSelection.Custom(
                    numberInput = policy.amount.toString(),
                    unit = policy.unit
                )
            }
            return SettingsDraftState(
                unlockMethod = settings.unlockMethod,
                savedMasterPattern = settings.masterPattern,
                savedMasterPassword = settings.masterPassword,
                savedRecoveryQuestion = savedQuestion,
                recoveryQuestion = savedQuestion.ifBlank { SecurityQuestions.DEFAULT_LIST.first() },
                decoyPinInput = settings.decoyPin,
                dynamicTimePinEnabled = settings.dynamicTimePinEnabled,
                scrambleKeypadEnabled = settings.scrambleKeypadEnabled,
                breakInSirenEnabled = settings.breakInSirenEnabled,
                preventScreenshots = settings.preventScreenshots,
                calculatorDisguiseEnabled = settings.calculatorDisguiseEnabled,
                uninstallProtectionEnabled = settings.uninstallProtectionEnabled,
                autoLockNewAppsEnabled = settings.autoLockNewAppsEnabled,
                autoLockSelection = selection
            )
        }
    }
}

sealed interface SettingsSaveResult {
    data class Valid(
        val updatedSettings: SecuritySettings,
        val recoveryUpdate: Pair<String, String>? = null
    ) : SettingsSaveResult

    data class Invalid(val nextDraft: SettingsDraftState) : SettingsSaveResult
}

object SettingsValidator {
    fun validateAndBuild(
        draft: SettingsDraftState,
        currentSettings: SecuritySettings
    ): SettingsSaveResult {
        val clearedDraft = draft.copy(
            pinError = null,
            patternError = null,
            passwordError = null,
            recoveryError = null,
            decoyPinError = null,
            customTimeoutError = null
        )

        val effectiveMasterPin = if (draft.newPin.isNotEmpty()) draft.newPin else currentSettings.masterPin

        when (val masterResult = PinValidator.validateMasterPinChange(
            newPin = draft.newPin,
            confirmPin = draft.confirmPin,
            effectiveDecoyPin = draft.decoyPinInput
        )) {
            is PinValidationResult.Invalid -> {
                return SettingsSaveResult.Invalid(
                    clearedDraft.copy(
                        activeTab = SettingsTab.SECURITY,
                        pinError = masterResult.message
                    )
                )
            }
            PinValidationResult.Valid -> Unit
        }

        val effectivePattern = if (draft.confirmPattern.isNotEmpty()) {
            draft.confirmPattern
        } else {
            currentSettings.masterPattern
        }
        when (val patternResult = PatternValidator.validatePatternChange(
            newPattern = draft.newPattern,
            confirmPattern = draft.confirmPattern,
            isPatternActive = draft.unlockMethod == UnlockMethod.PATTERN,
            existingSavedPattern = currentSettings.masterPattern
        )) {
            is PinValidationResult.Invalid -> {
                return SettingsSaveResult.Invalid(
                    clearedDraft.copy(
                        activeTab = SettingsTab.SECURITY,
                        patternError = patternResult.message
                    )
                )
            }
            PinValidationResult.Valid -> Unit
        }

        val effectivePassword = if (draft.newPassword.isNotEmpty()) {
            draft.newPassword
        } else {
            currentSettings.masterPassword
        }
        when (val passwordResult = PasswordValidator.validatePasswordChange(
            newPassword = draft.newPassword,
            confirmPassword = draft.confirmPassword,
            isPasswordActive = draft.unlockMethod == UnlockMethod.PASSWORD,
            existingSavedPassword = currentSettings.masterPassword
        )) {
            is PinValidationResult.Invalid -> {
                return SettingsSaveResult.Invalid(
                    clearedDraft.copy(
                        activeTab = SettingsTab.SECURITY,
                        passwordError = passwordResult.message
                    )
                )
            }
            PinValidationResult.Valid -> Unit
        }

        if (draft.savedRecoveryQuestion.isNotBlank() &&
            draft.recoveryQuestion != draft.savedRecoveryQuestion &&
            draft.recoveryAnswerInput.isBlank()
        ) {
            return SettingsSaveResult.Invalid(
                clearedDraft.copy(
                    activeTab = SettingsTab.SECURITY,
                    recoveryError = "Enter a new secret answer when changing your security question"
                )
            )
        }

        when (val decoyResult = PinValidator.validateDecoyPin(
            decoyPin = draft.decoyPinInput,
            effectiveMasterPin = effectiveMasterPin
        )) {
            is PinValidationResult.Invalid -> {
                return SettingsSaveResult.Invalid(
                    clearedDraft.copy(
                        activeTab = SettingsTab.DECOY,
                        decoyPinError = decoyResult.message
                    )
                )
            }
            PinValidationResult.Valid -> Unit
        }

        val resolvedTimeoutMillis = when (val selection = draft.autoLockSelection) {
            is AutoLockDraftSelection.Preset -> selection.timeoutMillis
            is AutoLockDraftSelection.Custom -> {
                val parsed = selection.numberInput.toLongOrNull()
                val maxAllowed = if (selection.unit == TimeUnitOption.MINUTES) 1_440L else 86_400L
                if (parsed == null || parsed < 1L || parsed > maxAllowed) {
                    return SettingsSaveResult.Invalid(
                        clearedDraft.copy(
                            activeTab = SettingsTab.AUTO_LOCK,
                            customTimeoutError = "Enter a number between 1 and $maxAllowed ${selection.unit.label.lowercase()}"
                        )
                    )
                }
                parsed * selection.unit.multiplierMillis
            }
        }

        val recoveryUpdate = if (draft.recoveryAnswerInput.isNotBlank()) {
            draft.recoveryQuestion.trim() to draft.recoveryAnswerInput.trim()
        } else {
            null
        }

        return SettingsSaveResult.Valid(
            updatedSettings = currentSettings.copy(
                unlockMethod = draft.unlockMethod,
                masterPin = effectiveMasterPin,
                masterPattern = effectivePattern,
                masterPassword = effectivePassword,
                decoyPin = draft.decoyPinInput,
                dynamicTimePinEnabled = draft.dynamicTimePinEnabled,
                scrambleKeypadEnabled = draft.scrambleKeypadEnabled,
                breakInSirenEnabled = draft.breakInSirenEnabled,
                preventScreenshots = draft.preventScreenshots,
                calculatorDisguiseEnabled = draft.calculatorDisguiseEnabled,
                uninstallProtectionEnabled = draft.uninstallProtectionEnabled,
                autoLockNewAppsEnabled = draft.autoLockNewAppsEnabled,
                autoLockTimeoutMillis = resolvedTimeoutMillis
            ),
            recoveryUpdate = recoveryUpdate
        )
    }
}
