package com.example.presentation.screens.lock

import com.example.domain.model.PinValidator
import com.example.domain.model.SecuritySettings
import java.util.Date

data class LockTarget(
    val packageName: String,
    val appLabel: String,
    val isHidden: Boolean
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

    data object Unlocked : LockScreenStage

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

        fun initialFor(target: LockTarget, settings: SecuritySettings): LockScreenStage {
            return if (target.isHidden) {
                FakeCrashDecoy
            } else {
                createPinStage(settings.scrambleKeypadEnabled)
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
        return LockScreenStage.createPinStage(settings.scrambleKeypadEnabled)
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
            val effects = buildList {
                add(LockEffect.TriggerIntruderCapture)
                if (nextFailedCount >= 3 && settings.breakInSirenEnabled) {
                    add(LockEffect.TriggerBreakInSiren)
                }
            }
            LockTransitionResult(
                stage = stage.copy(
                    enteredPin = "",
                    failedAttempts = nextFailedCount,
                    errorMessage = "Incorrect PIN (Attempt $nextFailedCount)"
                ),
                effects = effects
            )
        }
    }

    fun onBackspacePressed(stage: LockScreenStage): LockScreenStage {
        if (stage !is LockScreenStage.PinAuthentication) return stage
        if (stage.enteredPin.isEmpty()) return stage
        return stage.copy(enteredPin = stage.enteredPin.dropLast(1), errorMessage = null)
    }

    fun onBiometricFailed(
        stage: LockScreenStage,
        settings: SecuritySettings
    ): LockTransitionResult {
        if (stage !is LockScreenStage.PinAuthentication) return LockTransitionResult(stage)
        val nextFailed = stage.failedAttempts + 1
        val effects = buildList {
            add(LockEffect.TriggerIntruderCapture)
            if (nextFailed >= 3 && settings.breakInSirenEnabled) {
                add(LockEffect.TriggerBreakInSiren)
            }
        }
        return LockTransitionResult(
            stage = stage.copy(
                failedAttempts = nextFailed,
                errorMessage = "Biometric authentication failed",
                biometricState = BiometricAuthState.IDLE
            ),
            effects = effects
        )
    }
}
