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

enum class UnlockMethod(val label: String, val subtitle: String) {
    PIN("PIN", "4-digit numeric code"),
    PATTERN("Pattern", "3×3 connect dots"),
    PASSWORD("Password", "Alphanumeric phrase")
}

data class SecuritySettings(
    val masterPin: String = "1234",
    val unlockMethod: UnlockMethod = UnlockMethod.PIN,
    val masterPattern: String = "",
    val masterPassword: String = "",
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

object PatternValidator {
    const val MIN_NODES = 4
    const val GRID_SIZE = 3

    fun encode(nodes: List<Int>): String = nodes.joinToString("-")

    fun decode(encoded: String): List<Int> {
        if (encoded.isBlank()) return emptyList()
        return encoded.split("-").mapNotNull { it.trim().toIntOrNull() }
    }

    fun isValidPattern(nodes: List<Int>): Boolean {
        return nodes.size >= MIN_NODES &&
            nodes.all { it in 0..8 } &&
            nodes.distinct().size == nodes.size
    }

    fun isValidEncodedPattern(encoded: String): Boolean {
        return isValidPattern(decode(encoded))
    }

    /**
     * Appends [nextNode] to [current] while automatically including any unvisited
     * collinear midpoint node on the 3×3 grid (e.g. 0 -> 2 includes 1; 0 -> 8 includes 4).
     */
    fun appendNodeWithMidpoint(current: List<Int>, nextNode: Int): List<Int> {
        if (nextNode !in 0..8 || current.contains(nextNode)) return current
        val last = current.lastOrNull() ?: return listOf(nextNode)
        val c1 = last % GRID_SIZE
        val r1 = last / GRID_SIZE
        val c2 = nextNode % GRID_SIZE
        val r2 = nextNode / GRID_SIZE
        val updated = current.toMutableList()
        if ((c1 + c2) % 2 == 0 && (r1 + r2) % 2 == 0) {
            val midCol = (c1 + c2) / 2
            val midRow = (r1 + r2) / 2
            val midNode = midRow * GRID_SIZE + midCol
            if (midNode != last && midNode != nextNode && !updated.contains(midNode)) {
                updated.add(midNode)
            }
        }
        updated.add(nextNode)
        return updated
    }

    fun validatePatternChange(
        newPattern: String,
        confirmPattern: String,
        isPatternActive: Boolean,
        existingSavedPattern: String
    ): PinValidationResult {
        if (newPattern.isEmpty() && confirmPattern.isEmpty()) {
            return if (isPatternActive && !isValidEncodedPattern(existingSavedPattern)) {
                PinValidationResult.Invalid("Draw and confirm a pattern (at least 4 dots) to enable Pattern lock")
            } else {
                PinValidationResult.Valid
            }
        }
        if (!isValidEncodedPattern(newPattern)) {
            return PinValidationResult.Invalid("Pattern must connect at least $MIN_NODES unique dots")
        }
        if (newPattern != confirmPattern) {
            return PinValidationResult.Invalid("Confirmation pattern does not match")
        }
        return PinValidationResult.Valid
    }
}

object PasswordValidator {
    const val MIN_LENGTH = 4
    const val MAX_LENGTH = 32

    fun isValidPassword(value: String): Boolean {
        return value.length in MIN_LENGTH..MAX_LENGTH && value.isNotBlank()
    }

    fun validatePasswordChange(
        newPassword: String,
        confirmPassword: String,
        isPasswordActive: Boolean,
        existingSavedPassword: String
    ): PinValidationResult {
        if (newPassword.isEmpty() && confirmPassword.isEmpty()) {
            return if (isPasswordActive && !isValidPassword(existingSavedPassword)) {
                PinValidationResult.Invalid("Enter and confirm a password (min $MIN_LENGTH characters) to enable Password lock")
            } else {
                PinValidationResult.Valid
            }
        }
        if (!isValidPassword(newPassword)) {
            return PinValidationResult.Invalid("Password must be $MIN_LENGTH–$MAX_LENGTH characters (not blank)")
        }
        if (newPassword != confirmPassword) {
            return PinValidationResult.Invalid("Passwords do not match")
        }
        return PinValidationResult.Valid
    }
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

    fun verifyAppUnlockPattern(
        enteredNodes: List<Int>,
        settings: SecuritySettings
    ): Boolean {
        if (settings.masterPattern.isBlank()) return false
        if (!PatternValidator.isValidPattern(enteredNodes)) return false
        return PatternValidator.encode(enteredNodes) == settings.masterPattern
    }

    fun verifyAppUnlockPassword(
        enteredPassword: String,
        settings: SecuritySettings
    ): Boolean {
        if (!PasswordValidator.isValidPassword(settings.masterPassword)) return false
        return enteredPassword == settings.masterPassword
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
