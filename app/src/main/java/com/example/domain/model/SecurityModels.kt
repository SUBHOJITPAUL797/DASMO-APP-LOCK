package com.example.domain.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class TimeUnitOption(val label: String, val multiplierMillis: Long) {
    SECONDS("Seconds", 1_000L),
    MINUTES("Minutes", 60_000L)
}

sealed interface AutoLockPolicy {
    val timeoutMillis: Long

    data object Immediate : AutoLockPolicy {
        override val timeoutMillis: Long = 0L
    }

    data class Preset(override val timeoutMillis: Long) : AutoLockPolicy

    data class Custom(val amount: Long, val unit: TimeUnitOption) : AutoLockPolicy {
        override val timeoutMillis: Long = amount.coerceAtLeast(1L) * unit.multiplierMillis
    }

    companion object {
        val PRESET_TIMEOUTS_MILLIS: List<Pair<Long, String>> = listOf(
            0L to "Immediately",
            15_000L to "15 Seconds",
            30_000L to "30 Seconds",
            60_000L to "1 Minute",
            300_000L to "5 Minutes",
            600_000L to "10 Minutes"
        )

        fun fromMillis(millis: Long): AutoLockPolicy {
            val coerced = millis.coerceAtLeast(0L)
            if (coerced == 0L) return Immediate
            if (PRESET_TIMEOUTS_MILLIS.any { it.first == coerced }) {
                return Preset(coerced)
            }
            return if (coerced >= 60_000L && coerced % 60_000L == 0L) {
                Custom(coerced / 60_000L, TimeUnitOption.MINUTES)
            } else {
                Custom((coerced / 1_000L).coerceAtLeast(1L), TimeUnitOption.SECONDS)
            }
        }
    }
}

data class SecuritySettings(
    val masterPin: String = "1234",
    val decoyPin: String = "",
    val dynamicTimePinEnabled: Boolean = false,
    val scrambleKeypadEnabled: Boolean = false,
    val breakInSirenEnabled: Boolean = false,
    val preventScreenshots: Boolean = true,
    val calculatorDisguiseEnabled: Boolean = false,
    val uninstallProtectionEnabled: Boolean = false,
    val autoLockTimeoutMillis: Long = 300_000L,
    val hiddenApps: Set<String> = emptySet()
)

sealed interface PinMatchResult {
    data object MasterMatch : PinMatchResult
    data object DecoyMatch : PinMatchResult
    data object Mismatch : PinMatchResult
}

sealed interface PinValidationResult {
    data object Valid : PinValidationResult
    data class Invalid(val message: String) : PinValidationResult
}

object PinValidator {
    fun isFourDigitPin(value: String): Boolean = value.length == 4 && value.all { it.isDigit() }

    fun validateMasterPinChange(
        newPin: String,
        confirmPin: String,
        effectiveDecoyPin: String
    ): PinValidationResult {
        if (newPin.isEmpty() && confirmPin.isEmpty()) return PinValidationResult.Valid
        if (!isFourDigitPin(newPin)) return PinValidationResult.Invalid("Master PIN must be exactly 4 digits")
        if (newPin != confirmPin) return PinValidationResult.Invalid("PINs do not match")
        if (effectiveDecoyPin.isNotEmpty() && newPin == effectiveDecoyPin) {
            return PinValidationResult.Invalid("Master PIN cannot match Decoy PIN")
        }
        return PinValidationResult.Valid
    }

    fun validateDecoyPin(
        decoyPin: String,
        effectiveMasterPin: String
    ): PinValidationResult {
        if (decoyPin.isEmpty()) return PinValidationResult.Valid
        if (!isFourDigitPin(decoyPin)) return PinValidationResult.Invalid("Decoy PIN must be 4 digits or left empty")
        if (decoyPin == effectiveMasterPin) return PinValidationResult.Invalid("Decoy PIN cannot be the same as Master PIN")
        return PinValidationResult.Valid
    }

    fun verifyAppUnlockPin(
        enteredPin: String,
        settings: SecuritySettings,
        now: Date = Date()
    ): Boolean {
        if (!isFourDigitPin(enteredPin)) return false
        if (enteredPin == settings.masterPin) return true
        if (settings.dynamicTimePinEnabled) {
            val time24 = SimpleDateFormat("HHmm", Locale.US).format(now)
            val time12 = SimpleDateFormat("hhmm", Locale.US).format(now)
            if (enteredPin == time24 || enteredPin == time12) return true
        }
        return false
    }

    fun verifyVaultPin(
        enteredPin: String,
        settings: SecuritySettings,
        now: Date = Date()
    ): PinMatchResult {
        if (!isFourDigitPin(enteredPin)) return PinMatchResult.Mismatch
        if (settings.decoyPin.isNotEmpty() && enteredPin == settings.decoyPin) {
            return PinMatchResult.DecoyMatch
        }
        if (verifyAppUnlockPin(enteredPin, settings, now)) {
            return PinMatchResult.MasterMatch
        }
        return PinMatchResult.Mismatch
    }
}
