package com.example.presentation.screens.disguise

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.SecuritySettings
import com.example.ui.components.tactileScale
import com.example.ui.theme.*

@Composable
fun CalculatorDisguiseScreen(
    settings: SecuritySettings,
    onUnlock: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var calcState by remember { mutableStateOf(CalculatorState()) }

    fun dispatch(action: CalculatorAction) {
        val outcome = CalculatorStateMachine.reduce(
            state = calcState,
            action = action,
            settings = settings
        )
        calcState = outcome.nextState
        if (outcome.unlocked) {
            onUnlock()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(ObsidianBlack, Color(0xFF0E081B), ObsidianBlack)
                )
            )
            .navigationBarsPadding()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.Bottom
    ) {
        // Sleek Obsidian Display Panel
        Surface(
            shape = RoundedCornerShape(26.dp),
            color = ObsidianSurface,
            border = BorderStroke(1.dp, ObsidianBorder.copy(alpha = 0.7f)),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(bottom = 18.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF150E26), Color(0xFF0C0815))
                        )
                    )
                    .padding(24.dp),
                verticalArrangement = Arrangement.Bottom,
                horizontalAlignment = Alignment.End
            ) {
                if (calcState.expression.isNotEmpty()) {
                    Text(
                        text = calcState.expression,
                        color = TextTertiary,
                        fontSize = 20.sp,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }
                Text(
                    text = calcState.display,
                    color = PureWhite,
                    fontSize = when {
                        calcState.display.length > 10 -> 38.sp
                        calcState.display.length > 7 -> 48.sp
                        else -> 60.sp
                    },
                    fontWeight = FontWeight.Light,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("calc_display")
                )
            }
        }

        val buttonRows = listOf(
            listOf("C", "±", "%", "÷"),
            listOf("7", "8", "9", "×"),
            listOf("4", "5", "6", "-"),
            listOf("1", "2", "3", "+"),
            listOf("0", ".", "=")
        )

        buttonRows.forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                row.forEach { btn ->
                    val isEquals = btn == "="
                    val isOperator = btn in listOf("÷", "×", "-", "+")
                    val isUtility = btn in listOf("C", "±", "%")

                    val keyBrush = when {
                        isEquals -> Brush.verticalGradient(listOf(ElectricPurple, DeepViolet))
                        isOperator -> Brush.verticalGradient(listOf(Color(0xFF6B21A8), Color(0xFF4C1D95)))
                        isUtility -> Brush.verticalGradient(listOf(Color(0xFF2B2046), Color(0xFF1D1533)))
                        else -> Brush.verticalGradient(listOf(Color(0xFF1E1633), Color(0xFF120C21)))
                    }

                    val borderColor = when {
                        isEquals -> NeonViolet.copy(alpha = 0.85f)
                        isOperator -> ElectricPurple.copy(alpha = 0.55f)
                        else -> ObsidianBorder.copy(alpha = 0.7f)
                    }

                    val interactionSource = remember { MutableInteractionSource() }
                    val pressed by interactionSource.collectIsPressedAsState()

                    val keyModifier = if (btn == "0") {
                        Modifier.weight(2f)
                    } else {
                        Modifier.weight(1f)
                    }

                    Box(
                        modifier = keyModifier
                            .height(74.dp)
                            .tactileScale(pressed = pressed, pressedScale = 0.93f)
                            .shadow(
                                elevation = if (isEquals) 12.dp else 6.dp,
                                shape = RoundedCornerShape(22.dp),
                                ambientColor = if (isEquals || isOperator) ElectricPurple.copy(alpha = 0.4f) else Color.Black,
                                spotColor = if (isEquals || isOperator) ElectricPurple.copy(alpha = 0.55f) else Color.Black
                            )
                            .clip(RoundedCornerShape(22.dp))
                            .background(keyBrush, RoundedCornerShape(22.dp))
                            .border(
                                width = 1.dp,
                                brush = Brush.verticalGradient(
                                    listOf(borderColor, Color.Black.copy(alpha = 0.5f))
                                ),
                                shape = RoundedCornerShape(22.dp)
                            )
                            .clickable(
                                interactionSource = interactionSource,
                                indication = ripple(color = NeonViolet.copy(alpha = 0.35f)),
                                onClick = {
                                    haptic.performHapticFeedback(
                                        if (isEquals) HapticFeedbackType.LongPress else HapticFeedbackType.TextHandleMove
                                    )
                                    val action = when (btn) {
                                        "C" -> CalculatorAction.Clear
                                        "±" -> CalculatorAction.ToggleSign
                                        "%" -> CalculatorAction.Percent
                                        "." -> CalculatorAction.Decimal
                                        "÷" -> CalculatorAction.Operator(CalcOperator.DIVIDE)
                                        "×" -> CalculatorAction.Operator(CalcOperator.MULTIPLY)
                                        "-" -> CalculatorAction.Operator(CalcOperator.SUBTRACT)
                                        "+" -> CalculatorAction.Operator(CalcOperator.ADD)
                                        "=" -> CalculatorAction.Equals
                                        else -> CalculatorAction.Digit(btn)
                                    }
                                    dispatch(action)
                                }
                            )
                            .testTag("calc_btn_$btn"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = btn,
                            fontSize = 26.sp,
                            fontWeight = if (isEquals || isOperator) FontWeight.SemiBold else FontWeight.Medium,
                            color = if (isUtility) LavenderGlow else PureWhite
                        )
                    }
                }
            }
        }
    }
}
