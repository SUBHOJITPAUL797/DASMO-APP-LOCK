package com.example.presentation.screens.lock

import com.example.domain.model.PatternValidator
import com.example.domain.model.PinValidator
import com.example.domain.model.SecuritySettings
import com.example.domain.model.UnlockMethod
import java.util.Date

data class LockTarget(
    val packageName: String,
    val appLabel: String,
    val isHidden: Boolean,
    val instanceNonce: Long = 0L
)

enum class BiometricAuthState {
    UNAVAILABLE,
    IDLE,
    PROMPTING
}

sealed interface LockScreenStage {
    data object FakeCrashDecoy : LockScreenStage

    data class PinAuthentication(
        val enteredPin: String = "",
        val failedAttempts: Int = 0,
        val errorMessage: String? = null,
        val biometricState: BiometricAuthState = BiometricAuthState.IDLE,
        val keypadDigits: List<String> = DEFAULT_DIGITS
    ) : LockScreenStage

    data class PatternAuthentication(
        val failedAttempts: Int = 0,
        val errorMessage: String? = null,
        val biometricState: BiometricAuthState = BiometricAuthState.IDLE
    ) : LockScreenStage

    data class PasswordAuthentication(
        val enteredPassword: String = "",
        val passwordVisible: Boolean = false,
        val failedAttempts: Int = 0,
        val errorMessage: String? = null,
        val biometricState: BiometricAuthState = BiometricAuthState.IDLE
    ) : LockScreenStage

    data object Unlocked : LockScreenStage

    val currentFailedAttempts: Int
        get() = when (this) {
            is PinAuthentication -> failedAttempts
            is PatternAuthentication -> failedAttempts
            is PasswordAuthentication -> failedAttempts
            else -> 0
        }

    val currentErrorMessage: String?
        get() = when (this) {
            is PinAuthentication -> errorMessage
            is PatternAuthentication -> errorMessage
            is PasswordAuthentication -> errorMessage
            else -> null
        }

    val isAuthInputStage: Boolean
        get() = this is PinAuthentication || this is PatternAuthentication || this is PasswordAuthentication

    companion object {
        val DEFAULT_DIGITS = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")

        fun createPinStage(
            scrambleEnabled: Boolean,
            biometricState: BiometricAuthState = BiometricAuthState.IDLE
        ): PinAuthentication {
            val digits = if (scrambleEnabled) DEFAULT_DIGITS.shuffled() else DEFAULT_DIGITS
            return PinAuthentication(
                biometricState = biometricState,
                keypadDigits = digits
            )
        }

        fun createAuthStage(
            settings: SecuritySettings,
            biometricState: BiometricAuthState = BiometricAuthState.IDLE
        ): LockScreenStage {
            return when (settings.unlockMethod) {
                UnlockMethod.PIN -> createPinStage(settings.scrambleKeypadEnabled, biometricState)
                UnlockMethod.PATTERN -> {
                    if (PatternValidator.isValidEncodedPattern(settings.masterPattern)) {
                        PatternAuthentication(biometricState = biometricState)
                    } else {
                        createPinStage(settings.scrambleKeypadEnabled, biometricState)
                    }
                }
                UnlockMethod.PASSWORD -> {
                    if (settings.masterPassword.isNotBlank()) {
                        PasswordAuthentication(biometricState = biometricState)
                    } else {
                        createPinStage(settings.scrambleKeypadEnabled, biometricState)
                    }
                }
            }
        }

        fun initialFor(target: LockTarget, settings: SecuritySettings): LockScreenStage {
            return if (target.isHidden) {
                FakeCrashDecoy
            } else {
                createAuthStage(settings)
            }
        }
    }
}

sealed interface LockEffect {
    data object UnlockGranted : LockEffect
    data object TriggerIntruderCapture : LockEffect
    data object TriggerBreakInSiren : LockEffect
}

data class LockTransitionResult(
    val stage: LockScreenStage,
    val effects: List<LockEffect> = emptyList()
)

object LockStateMachine {
    fun revealPinFromCrashDecoy(settings: SecuritySettings): LockScreenStage {
        return LockScreenStage.createAuthStage(settings)
    }

    fun onDigitPressed(
        stage: LockScreenStage,
        digit: String,
        settings: SecuritySettings,
        now: Date = Date()
    ): LockTransitionResult {
        if (stage !is LockScreenStage.PinAuthentication) return LockTransitionResult(stage)
        if (digit.length != 1 || !digit[0].isDigit()) return LockTransitionResult(stage)
        if (stage.enteredPin.length >= 4) return LockTransitionResult(stage)

        val updatedPin = stage.enteredPin + digit
        if (updatedPin.length < 4) {
            return LockTransitionResult(
                stage.copy(enteredPin = updatedPin, errorMessage = null)
            )
        }

        return if (PinValidator.verifyAppUnlockPin(updatedPin, settings, now)) {
            LockTransitionResult(
                stage = LockScreenStage.Unlocked,
                effects = listOf(LockEffect.UnlockGranted)
            )
        } else {
            val nextFailedCount = stage.failedAttempts + 1
            LockTransitionResult(
                stage = stage.copy(
                    enteredPin = "",
                    failedAttempts = nextFailedCount,
                    errorMessage = "Incorrect PIN (Attempt $nextFailedCount)"
                ),
                effects = buildFailureEffects(nextFailedCount, settings)
            )
        }
    }

    fun onBackspacePressed(stage: LockScreenStage): LockScreenStage {
        if (stage !is LockScreenStage.PinAuthentication) return stage
        if (stage.enteredPin.isEmpty()) return stage
        return stage.copy(enteredPin = stage.enteredPin.dropLast(1), errorMessage = null)
    }

    fun onPatternStarted(stage: LockScreenStage): LockScreenStage {
        return if (stage is LockScreenStage.PatternAuthentication && stage.errorMessage != null) {
            stage.copy(errorMessage = null)
        } else {
            stage
        }
    }

    fun onPatternCompleted(
        stage: LockScreenStage,
        nodes: List<Int>,
        settings: SecuritySettings
    ): LockTransitionResult {
        if (stage !is LockScreenStage.PatternAuthentication) return LockTransitionResult(stage)
        if (!PatternValidator.isValidPattern(nodes)) {
            return LockTransitionResult(
                stage = stage.copy(errorMessage = "Connect at least ${PatternValidator.MIN_NODES} dots")
            )
        }
        return if (PinValidator.verifyAppUnlockPattern(nodes, settings)) {
            LockTransitionResult(
                stage = LockScreenStage.Unlocked,
                effects = listOf(LockEffect.UnlockGranted)
            )
        } else {
            val nextFailedCount = stage.failedAttempts + 1
            LockTransitionResult(
                stage = stage.copy(
                    failedAttempts = nextFailedCount,
                    errorMessage = "Incorrect Pattern (Attempt $nextFailedCount)"
                ),
                effects = buildFailureEffects(nextFailedCount, settings)
            )
        }
    }

    fun onPasswordChanged(stage: LockScreenStage, newValue: String): LockScreenStage {
        if (stage !is LockScreenStage.PasswordAuthentication) return stage
        return stage.copy(enteredPassword = newValue.take(32), errorMessage = null)
    }

    fun onTogglePasswordVisibility(stage: LockScreenStage): LockScreenStage {
        if (stage !is LockScreenStage.PasswordAuthentication) return stage
        return stage.copy(passwordVisible = !stage.passwordVisible)
    }

    fun onPasswordSubmitted(
        stage: LockScreenStage,
        settings: SecuritySettings
    ): LockTransitionResult {
        if (stage !is LockScreenStage.PasswordAuthentication) return LockTransitionResult(stage)
        if (stage.enteredPassword.isBlank()) {
            return LockTransitionResult(
                stage = stage.copy(errorMessage = "Enter your password to unlock")
            )
        }
        return if (PinValidator.verifyAppUnlockPassword(stage.enteredPassword, settings)) {
            LockTransitionResult(
                stage = LockScreenStage.Unlocked,
                effects = listOf(LockEffect.UnlockGranted)
            )
        } else {
            val nextFailedCount = stage.failedAttempts + 1
            LockTransitionResult(
                stage = stage.copy(
                    enteredPassword = "",
                    failedAttempts = nextFailedCount,
                    errorMessage = "Incorrect Password (Attempt $nextFailedCount)"
                ),
                effects = buildFailureEffects(nextFailedCount, settings)
            )
        }
    }

    fun onBiometricFailed(
        stage: LockScreenStage,
        settings: SecuritySettings
    ): LockTransitionResult {
        if (!stage.isAuthInputStage) return LockTransitionResult(stage)
        val nextFailed = stage.currentFailedAttempts + 1
        val effects = buildFailureEffects(nextFailed, settings)
        val nextStage = when (stage) {
            is LockScreenStage.PinAuthentication -> stage.copy(
                failedAttempts = nextFailed,
                errorMessage = "Biometric authentication failed",
                biometricState = BiometricAuthState.IDLE
            )
            is LockScreenStage.PatternAuthentication -> stage.copy(
                failedAttempts = nextFailed,
                errorMessage = "Biometric authentication failed",
                biometricState = BiometricAuthState.IDLE
            )
            is LockScreenStage.PasswordAuthentication -> stage.copy(
                failedAttempts = nextFailed,
                errorMessage = "Biometric authentication failed",
                biometricState = BiometricAuthState.IDLE
            )
            else -> stage
        }
        return LockTransitionResult(stage = nextStage, effects = effects)
    }

    private fun buildFailureEffects(failedCount: Int, settings: SecuritySettings): List<LockEffect> {
        return buildList {
            add(LockEffect.TriggerIntruderCapture)
            if (failedCount >= 3 && settings.breakInSirenEnabled) {
                add(LockEffect.TriggerBreakInSiren)
            }
        }
    }
}
