package com.example.presentation.screens.settings

import com.example.domain.model.AutoLockPolicy
import com.example.domain.model.PinValidationResult
import com.example.domain.model.PinValidator
import com.example.domain.model.SecuritySettings
import com.example.domain.model.TimeUnitOption

enum class SettingsTab(val label: String) {
    SECURITY("Access & PIN"),
    DECOY("Decoy Mode"),
    DISGUISE("Camouflage"),
    AUTO_LOCK("Auto-Lock")
}

sealed interface AutoLockDraftSelection {
    data class Preset(val timeoutMillis: Long) : AutoLockDraftSelection
    data class Custom(val numberInput: String, val unit: TimeUnitOption) : AutoLockDraftSelection
}

data class SettingsDraftState(
    val activeTab: SettingsTab = SettingsTab.SECURITY,
    val newPin: String = "",
    val confirmPin: String = "",
    val pinError: String? = null,
    val decoyPinInput: String = "",
    val decoyPinError: String? = null,
    val dynamicTimePinEnabled: Boolean = false,
    val scrambleKeypadEnabled: Boolean = false,
    val breakInSirenEnabled: Boolean = false,
    val preventScreenshots: Boolean = true,
    val calculatorDisguiseEnabled: Boolean = false,
    val uninstallProtectionEnabled: Boolean = false,
    val autoLockSelection: AutoLockDraftSelection = AutoLockDraftSelection.Preset(300_000L),
    val customTimeoutError: String? = null
) {
    fun hasUnsavedChanges(saved: SecuritySettings): Boolean {
        val baseline = fromSettings(saved)
        return newPin.isNotEmpty() ||
            confirmPin.isNotEmpty() ||
            decoyPinInput != baseline.decoyPinInput ||
            dynamicTimePinEnabled != baseline.dynamicTimePinEnabled ||
            scrambleKeypadEnabled != baseline.scrambleKeypadEnabled ||
            breakInSirenEnabled != baseline.breakInSirenEnabled ||
            preventScreenshots != baseline.preventScreenshots ||
            calculatorDisguiseEnabled != baseline.calculatorDisguiseEnabled ||
            uninstallProtectionEnabled != baseline.uninstallProtectionEnabled ||
            autoLockSelection != baseline.autoLockSelection
    }

    companion object {
        fun fromSettings(settings: SecuritySettings): SettingsDraftState {
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
                decoyPinInput = settings.decoyPin,
                dynamicTimePinEnabled = settings.dynamicTimePinEnabled,
                scrambleKeypadEnabled = settings.scrambleKeypadEnabled,
                breakInSirenEnabled = settings.breakInSirenEnabled,
                preventScreenshots = settings.preventScreenshots,
                calculatorDisguiseEnabled = settings.calculatorDisguiseEnabled,
                uninstallProtectionEnabled = settings.uninstallProtectionEnabled,
                autoLockSelection = selection
            )
        }
    }
}

sealed interface SettingsSaveResult {
    data class Valid(val updatedSettings: SecuritySettings) : SettingsSaveResult
    data class Invalid(val nextDraft: SettingsDraftState) : SettingsSaveResult
}

object SettingsValidator {
    fun validateAndBuild(
        draft: SettingsDraftState,
        currentSettings: SecuritySettings
    ): SettingsSaveResult {
        val clearedDraft = draft.copy(
            pinError = null,
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

        return SettingsSaveResult.Valid(
            currentSettings.copy(
                masterPin = effectiveMasterPin,
                decoyPin = draft.decoyPinInput,
                dynamicTimePinEnabled = draft.dynamicTimePinEnabled,
                scrambleKeypadEnabled = draft.scrambleKeypadEnabled,
                breakInSirenEnabled = draft.breakInSirenEnabled,
                preventScreenshots = draft.preventScreenshots,
                calculatorDisguiseEnabled = draft.calculatorDisguiseEnabled,
                uninstallProtectionEnabled = draft.uninstallProtectionEnabled,
                autoLockTimeoutMillis = resolvedTimeoutMillis
            )
        )
    }
}
