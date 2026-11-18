package com.example.presentation.screens.disguise

import com.example.domain.model.PinValidator
import com.example.domain.model.SecuritySettings
import java.util.Date
import java.util.Locale

enum class CalcOperator(val symbol: String) {
    ADD("+"),
    SUBTRACT("-"),
    MULTIPLY("×"),
    DIVIDE("÷");

    fun apply(first: Double, second: Double): Double = when (this) {
        ADD -> first + second
        SUBTRACT -> first - second
        MULTIPLY -> first * second
        DIVIDE -> if (second != 0.0) first / second else Double.NaN
    }
}

enum class CalcInputPhase {
    FRESH,
    ENTERING_FIRST,
    AWAITING_SECOND,
    ENTERING_SECOND,
    RESULT_DISPLAYED,
    ERROR
}

data class CalculatorState(
    val display: String = "0",
    val expression: String = "",
    val firstOperand: Double? = null,
    val pendingOperator: CalcOperator? = null,
    val phase: CalcInputPhase = CalcInputPhase.FRESH
)

sealed interface CalculatorAction {
    data class Digit(val value: String) : CalculatorAction
    data object Decimal : CalculatorAction
    data object Clear : CalculatorAction
    data object ToggleSign : CalculatorAction
    data object Percent : CalculatorAction
    data class Operator(val op: CalcOperator) : CalculatorAction
    data object Equals : CalculatorAction
}

data class CalculatorStepOutcome(
    val nextState: CalculatorState,
    val unlocked: Boolean = false
)

object CalculatorStateMachine {

    fun reduce(
        state: CalculatorState,
        action: CalculatorAction,
        settings: SecuritySettings,
        now: Date = Date()
    ): CalculatorStepOutcome {
        return when (action) {
            is CalculatorAction.Clear -> CalculatorStepOutcome(CalculatorState())

            is CalculatorAction.Digit -> {
                val digit = action.value.take(1)
                if (digit !in "0".."9") return CalculatorStepOutcome(state)
                val nextState = when (state.phase) {
                    CalcInputPhase.FRESH, CalcInputPhase.RESULT_DISPLAYED, CalcInputPhase.ERROR -> {
                        CalculatorState(
                            display = digit,
                            expression = "",
                            firstOperand = null,
                            pendingOperator = null,
                            phase = if (digit == "0") CalcInputPhase.FRESH else CalcInputPhase.ENTERING_FIRST
                        )
                    }
                    CalcInputPhase.AWAITING_SECOND -> {
                        state.copy(
                            display = digit,
                            phase = CalcInputPhase.ENTERING_SECOND
                        )
                    }
                    CalcInputPhase.ENTERING_FIRST, CalcInputPhase.ENTERING_SECOND -> {
                        val updated = if (state.display == "0") {
                            digit
                        } else if (state.display.length < 12) {
                            state.display + digit
                        } else {
                            state.display
                        }
                        state.copy(display = updated)
                    }
                }
                CalculatorStepOutcome(nextState)
            }

            is CalculatorAction.Decimal -> {
                val nextState = when (state.phase) {
                    CalcInputPhase.FRESH, CalcInputPhase.RESULT_DISPLAYED, CalcInputPhase.ERROR -> {
                        CalculatorState(
                            display = "0.",
                            expression = "",
                            firstOperand = null,
                            pendingOperator = null,
                            phase = CalcInputPhase.ENTERING_FIRST
                        )
                    }
                    CalcInputPhase.AWAITING_SECOND -> {
                        state.copy(
                            display = "0.",
                            phase = CalcInputPhase.ENTERING_SECOND
                        )
                    }
                    CalcInputPhase.ENTERING_FIRST, CalcInputPhase.ENTERING_SECOND -> {
                        if (state.display.contains(".")) state else state.copy(display = "${state.display}.")
                    }
                }
                CalculatorStepOutcome(nextState)
            }

            is CalculatorAction.ToggleSign -> {
                if (state.phase == CalcInputPhase.ERROR || state.display == "0" || state.display == "0.") {
                    return CalculatorStepOutcome(state)
                }
                val toggled = if (state.display.startsWith("-")) state.display.drop(1) else "-${state.display}"
                val nextPhase = if (state.phase == CalcInputPhase.RESULT_DISPLAYED) CalcInputPhase.ENTERING_FIRST else state.phase
                CalculatorStepOutcome(state.copy(display = toggled, phase = nextPhase))
            }

            is CalculatorAction.Percent -> {
                if (state.phase == CalcInputPhase.ERROR) return CalculatorStepOutcome(state)
                val value = state.display.toDoubleOrNull() ?: return CalculatorStepOutcome(state)
                val formatted = formatNumber(value / 100.0)
                CalculatorStepOutcome(state.copy(display = formatted))
            }

            is CalculatorAction.Operator -> {
                if (state.phase == CalcInputPhase.ERROR) return CalculatorStepOutcome(state)
                val currentValue = state.display.toDoubleOrNull() ?: 0.0

                // If chaining e.g. 5 + 3 ×, compute 5 + 3 = 8 first
                if (state.phase == CalcInputPhase.ENTERING_SECOND &&
                    state.firstOperand != null &&
                    state.pendingOperator != null
                ) {
                    val intermediate = state.pendingOperator.apply(state.firstOperand, currentValue)
                    if (intermediate.isNaN() || intermediate.isInfinite()) {
                        return CalculatorStepOutcome(
                            CalculatorState(
                                display = "Error",
                                expression = "${formatNumber(state.firstOperand)} ${state.pendingOperator.symbol} ${formatNumber(currentValue)}",
                                phase = CalcInputPhase.ERROR
                            )
                        )
                    }
                    val formatted = formatNumber(intermediate)
                    return CalculatorStepOutcome(
                        CalculatorState(
                            display = formatted,
                            expression = "$formatted ${action.op.symbol}",
                            firstOperand = intermediate,
                            pendingOperator = action.op,
                            phase = CalcInputPhase.AWAITING_SECOND
                        )
                    )
                }

                // If already AWAITING_SECOND, just swap operator
                val baseOperand = if (state.phase == CalcInputPhase.AWAITING_SECOND && state.firstOperand != null) {
                    state.firstOperand
                } else {
                    currentValue
                }
                val formattedBase = formatNumber(baseOperand)
                CalculatorStepOutcome(
                    CalculatorState(
                        display = formattedBase,
                        expression = "$formattedBase ${action.op.symbol}",
                        firstOperand = baseOperand,
                        pendingOperator = action.op,
                        phase = CalcInputPhase.AWAITING_SECOND
                    )
                )
            }

            is CalculatorAction.Equals -> {
                // Only unlock when entering the PIN cleanly (no pending arithmetic calculation)
                val isPinAttempt = state.pendingOperator == null &&
                    (state.phase == CalcInputPhase.ENTERING_FIRST || state.phase == CalcInputPhase.FRESH)
                if (isPinAttempt && PinValidator.verifyAppUnlockPin(state.display, settings, now)) {
                    return CalculatorStepOutcome(
                        nextState = CalculatorState(),
                        unlocked = true
                    )
                }

                if (state.phase == CalcInputPhase.ERROR) return CalculatorStepOutcome(state)

                val first = state.firstOperand ?: return CalculatorStepOutcome(state)
                val op = state.pendingOperator ?: return CalculatorStepOutcome(state)
                val second = state.display.toDoubleOrNull() ?: first
                val result = op.apply(first, second)
                val expr = "${formatNumber(first)} ${op.symbol} ${formatNumber(second)} ="

                if (result.isNaN() || result.isInfinite()) {
                    return CalculatorStepOutcome(
                        CalculatorState(
                            display = "Error",
                            expression = expr,
                            phase = CalcInputPhase.ERROR
                        )
                    )
                }

                return CalculatorStepOutcome(
                    CalculatorState(
                        display = formatNumber(result),
                        expression = expr,
                        firstOperand = null,
                        pendingOperator = null,
                        phase = CalcInputPhase.RESULT_DISPLAYED
                    )
                )
            }
        }
    }

    fun formatNumber(value: Double): String {
        if (value.isNaN() || value.isInfinite()) return "Error"
        val normalized = if (value == -0.0) 0.0 else value
        return if (normalized % 1.0 == 0.0 && kotlin.math.abs(normalized) < 1e15) {
            normalized.toLong().toString()
        } else {
            String.format(Locale.US, "%.6f", normalized).trimEnd('0').trimEnd('.')
        }
    }
}
