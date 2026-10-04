package com.example.presentation.screens.disguise

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.ExperimentalFoundationApi

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CalculatorDisguiseScreen(
    masterPin: String,
    onUnlock: () -> Unit
) {
    var display by remember { mutableStateOf("0") }
    var expression by remember { mutableStateOf("") }
    var firstOperand by remember { mutableStateOf<Double?>(null) }
    var pendingOperator by remember { mutableStateOf<String?>(null) }
    var resetOnNextDigit by remember { mutableStateOf(false) }

    fun onDigit(d: String) {
        if (display == "0" || resetOnNextDigit) {
            display = d
            resetOnNextDigit = false
        } else {
            if (display.length < 12) {
                display += d
            }
        }
    }

    fun onOperator(op: String) {
        val currentValue = display.toDoubleOrNull() ?: 0.0
        firstOperand = currentValue
        pendingOperator = op
        expression = "$display $op"
        resetOnNextDigit = true
    }

    fun calculateResult() {
        // First check secret unlock condition
        if (display == masterPin) {
            onUnlock()
            return
        }

        val secondOperand = display.toDoubleOrNull() ?: 0.0
        val first = firstOperand ?: return
        val op = pendingOperator ?: return

        val result = when (op) {
            "+" -> first + secondOperand
            "-" -> first - secondOperand
            "×" -> first * secondOperand
            "÷" -> if (secondOperand != 0.0) first / secondOperand else Double.NaN
            else -> secondOperand
        }

        expression = "$first $op $secondOperand ="
        display = if (result.isNaN()) {
            "Error"
        } else if (result % 1.0 == 0.0) {
            result.toLong().toString()
        } else {
            String.format(java.util.Locale.US, "%.4f", result).trimEnd('0').trimEnd('.')
        }
        firstOperand = null
        pendingOperator = null
        resetOnNextDigit = true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF09070F))
            .padding(16.dp),
        verticalArrangement = Arrangement.Bottom
    ) {
        // Display area
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.Bottom,
            horizontalAlignment = Alignment.End
        ) {
            if (expression.isNotEmpty()) {
                Text(
                    text = expression,
                    color = Color.Gray,
                    fontSize = 20.sp,
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            Text(
                text = display,
                color = Color.White,
                fontSize = if (display.length > 8) 42.sp else 58.sp,
                fontWeight = FontWeight.Light,
                textAlign = TextAlign.End,
                maxLines = 1,
                modifier = Modifier.fillMaxWidth()
            )
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
                    val isOperator = btn in listOf("÷", "×", "-", "+", "=")
                    val isSpecial = btn in listOf("C", "±", "%")

                    val btnColor = when {
                        btn == "=" -> Color(0xFFA855F7)
                        isOperator -> Color(0xFF7E22CE)
                        isSpecial -> Color(0xFF261D3D)
                        else -> Color(0xFF161028)
                    }

                    val textColor = Color.White
                    val modifier = if (btn == "0") {
                        Modifier
                            .weight(2f)
                            .height(72.dp)
                    } else {
                        Modifier
                            .weight(1f)
                            .height(72.dp)
                    }

                    Box(
                        modifier = modifier
                            .background(btnColor, shape = RoundedCornerShape(24.dp))
                            .combinedClickable(
                                onClick = {
                                    when (btn) {
                                        "C" -> {
                                            display = "0"
                                            expression = ""
                                            firstOperand = null
                                            pendingOperator = null
                                        }
                                        "±" -> {
                                            if (display != "0" && display != "Error") {
                                                display = if (display.startsWith("-")) display.drop(1) else "-$display"
                                            }
                                        }
                                        "%" -> {
                                            val value = display.toDoubleOrNull() ?: 0.0
                                            display = (value / 100.0).toString()
                                        }
                                        "." -> {
                                            if (!display.contains(".")) {
                                                display += "."
                                            }
                                        }
                                        "÷", "×", "-", "+" -> onOperator(btn)
                                        "=" -> calculateResult()
                                        else -> onDigit(btn)
                                    }
                                },
                                onLongClick = {
                                    if (btn == "=") {
                                        onUnlock()
                                    }
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = btn,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Medium,
                            color = textColor
                        )
                    }
                }
            }
        }
    }
}
