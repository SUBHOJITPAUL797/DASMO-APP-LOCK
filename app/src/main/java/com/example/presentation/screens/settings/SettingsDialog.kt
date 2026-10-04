package com.example.presentation.screens.settings

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.util.SecurePreferences

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDialog(onDismiss: () -> Unit, context: Context) {
    var activeTab by remember { mutableStateOf(0) }

    val securePrefs = remember { SecurePreferences(context) }
    
    // PIN States
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf("") }

    // Security Features States
    var dynamicTimePin by remember { mutableStateOf(securePrefs.getString("dynamic_time_pin", "false") == "true") }
    var scrambleKeypad by remember { mutableStateOf(securePrefs.getString("scramble_keypad", "false") == "true") }
    var breakInSiren by remember { mutableStateOf(securePrefs.getString("break_in_siren", "false") == "true") }
    var preventScreenshots by remember { mutableStateOf(securePrefs.getString("prevent_screenshots", "true") == "true") }

    // Decoy PIN States
    val savedDecoyPin = remember { securePrefs.getString("decoy_pin", "") }
    var decoyPinInput by remember { mutableStateOf(savedDecoyPin) }
    var isDecoyEnabled by remember { mutableStateOf(savedDecoyPin.isNotEmpty()) }

    // Disguise States
    var isCalculatorDisguise by remember { mutableStateOf(securePrefs.getString("calculator_disguise", "false") == "true") }
    
    // Auto-Lock States
    val timeoutOptions = listOf(
        0L to "Immediately",
        15000L to "15 Seconds",
        30000L to "30 Seconds",
        60000L to "1 Minute",
        300000L to "5 Minutes",
        600000L to "10 Minutes"
    )
    val savedTimeout = remember { securePrefs.getString("auto_lock_timeout", "300000").toLongOrNull() ?: 300000L }
    var selectedTimeout by remember { mutableStateOf(savedTimeout) }
    
    var customNumber by remember { mutableStateOf("") }
    var customUnit by remember { mutableStateOf("Seconds") }
    var isCustomSelected by remember { 
        mutableStateOf(timeoutOptions.none { it.first == savedTimeout }) 
    }
    
    LaunchedEffect(Unit) {
        if (isCustomSelected) {
            if (savedTimeout % 60000L == 0L) {
                customNumber = (savedTimeout / 60000L).toString()
                customUnit = "Minutes"
            } else {
                customNumber = (savedTimeout / 1000L).toString()
                customUnit = "Seconds"
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Settings & Security", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(12.dp))
                ScrollableTabRow(selectedTabIndex = activeTab, edgePadding = 0.dp) {
                    Tab(
                        selected = activeTab == 0,
                        onClick = { activeTab = 0 },
                        text = { Text("Security") }
                    )
                    Tab(
                        selected = activeTab == 1,
                        onClick = { activeTab = 1 },
                        text = { Text("Decoy PIN") }
                    )
                    Tab(
                        selected = activeTab == 2,
                        onClick = { activeTab = 2 },
                        text = { Text("Disguise") }
                    )
                    Tab(
                        selected = activeTab == 3,
                        onClick = { activeTab = 3 },
                        text = { Text("Auto-Lock") }
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(top = 8.dp)
            ) {
                when (activeTab) {
                    0 -> {
                        // Security Tab
                        Text("Master PIN", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = pin,
                            onValueChange = { if (it.length <= 4) pin = it.filter { c -> c.isDigit() } },
                            label = { Text("New PIN (4 digits)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = confirmPin,
                            onValueChange = { if (it.length <= 4) confirmPin = it.filter { c -> c.isDigit() } },
                            label = { Text("Confirm PIN") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (pinError.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(pinError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

                        Text("Advanced Protections", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))

                        // Dynamic Time PIN
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Dynamic Time PIN", fontWeight = FontWeight.SemiBold)
                                    Text(
                                        "Matches current clock (e.g. 10:45 = 1045). Peepers cannot reuse PIN.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = dynamicTimePin,
                                    onCheckedChange = {
                                        dynamicTimePin = it
                                        securePrefs.putString("dynamic_time_pin", it.toString())
                                    }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Scrambled Keypad
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Anti-Peep Scrambled Keypad", fontWeight = FontWeight.SemiBold)
                                    Text(
                                        "Randomizes digits (0-9) layout on lockscreen to block finger tracking.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = scrambleKeypad,
                                    onCheckedChange = {
                                        scrambleKeypad = it
                                        securePrefs.putString("scramble_keypad", it.toString())
                                    }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Break-In Siren
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Break-In Siren Alarm", fontWeight = FontWeight.SemiBold)
                                    Text(
                                        "Sounds loud alarm tone & vibration on 3 consecutive failed attempts.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = breakInSiren,
                                    onCheckedChange = {
                                        breakInSiren = it
                                        securePrefs.putString("break_in_siren", it.toString())
                                    }
                                )
                            }
                        }
                    }
                    1 -> {
                        // Decoy PIN Tab
                        Text("Decoy / Panic PIN", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "If anyone coerces or forces you to open your vault, enter this Decoy PIN instead of your Master PIN. The app will unlock into a realistic decoy vault with harmless notes, keeping all real files completely hidden!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        OutlinedTextField(
                            value = decoyPinInput,
                            onValueChange = { if (it.length <= 4) decoyPinInput = it.filter { c -> c.isDigit() } },
                            label = { Text("Decoy PIN (4 digits)") },
                            placeholder = { Text("e.g. 0000") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Leave empty or clear to disable Decoy mode.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                    2 -> {
                        // Disguise Tab
                        Text("Camouflage Disguise", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "Disguise DASMO LOCK as a fully functional Calculator app. When launched, anyone opening the app will see a working calculator.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Calculator Disguise", fontWeight = FontWeight.Bold)
                                    Text(
                                        "Type your PIN and press '=' to unlock.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = isCalculatorDisguise,
                                    onCheckedChange = {
                                        isCalculatorDisguise = it
                                        securePrefs.putString("calculator_disguise", it.toString())
                                    }
                                )
                            }
                        }
                    }
                    3 -> {
                        // Auto-Lock Tab
                        Text("Lock Timeout", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Choose when apps should re-lock after going to background:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        timeoutOptions.forEach { (millis, label) ->
                            val isSelected = !isCustomSelected && selectedTimeout == millis
                            Card(
                                onClick = {
                                    isCustomSelected = false
                                    selectedTimeout = millis
                                },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .padding(12.dp)
                                        .fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(label, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = {
                                            isCustomSelected = false
                                            selectedTimeout = millis
                                        }
                                    )
                                }
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                        // Prevent Screenshots / Recents Privacy
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Prevent Screenshots & Blur Recents", fontWeight = FontWeight.SemiBold)
                                    Text(
                                        "Hides app contents in Android Recents screen and prevents spyware screen grabs.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = preventScreenshots,
                                    onCheckedChange = {
                                        preventScreenshots = it
                                        securePrefs.putString("prevent_screenshots", it.toString())
                                    }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (pin.isNotEmpty()) {
                    if (pin.length != 4) {
                        pinError = "PIN must be 4 digits"
                        return@Button
                    }
                    if (pin != confirmPin) {
                        pinError = "PINs do not match"
                        return@Button
                    }
                    securePrefs.putString("app_pin", pin)
                }

                // Save decoy pin
                securePrefs.putString("decoy_pin", decoyPinInput)

                // Save timeout
                val finalTimeout = if (isCustomSelected) {
                    val num = customNumber.toLongOrNull() ?: 15L
                    val unitMultiplier = if (customUnit == "Minutes") 60000L else 1000L
                    num * unitMultiplier
                } else {
                    selectedTimeout
                }
                securePrefs.putString("auto_lock_timeout", finalTimeout.toString())

                onDismiss()
            }) {
                Text("Save & Apply")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
