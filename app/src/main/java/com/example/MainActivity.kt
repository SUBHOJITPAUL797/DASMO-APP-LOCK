package com.example

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import com.example.data.db.AppGuardDatabase
import com.example.domain.repository.AppLockRepository
import com.example.presentation.screens.disguise.CalculatorDisguiseScreen
import com.example.presentation.screens.home.AppGuardHomeScreen
import com.example.presentation.screens.intruders.IntruderVaultScreen
import com.example.presentation.screens.vault.SecureVaultScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.util.SecurePreferences

class MainActivity : ComponentActivity() {
    private lateinit var repository: AppLockRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val securePrefs = SecurePreferences(this)
        if (securePrefs.getString("prevent_screenshots", "true") == "true") {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
        }

        repository = AppLockRepository(AppGuardDatabase.getDatabase(this).appLockDao())

        setContent {
            MyApplicationTheme {
                val isCalculatorDisguise = remember {
                    securePrefs.getString("calculator_disguise", "false") == "true"
                }
                val masterPin = remember {
                    securePrefs.getString("app_pin", "1234")
                }
                var isDisguisePassed by remember { mutableStateOf(!isCalculatorDisguise) }
                var currentScreen by remember { mutableStateOf("home") }

                if (!isDisguisePassed) {
                    CalculatorDisguiseScreen(
                        masterPin = masterPin,
                        onUnlock = { isDisguisePassed = true }
                    )
                } else {
                    when (currentScreen) {
                        "home" -> {
                            AppGuardHomeScreen(
                                repository = repository,
                                context = this,
                                onNavigateToIntruders = { currentScreen = "intruders" },
                                onNavigateToVault = { currentScreen = "vault" }
                            )
                        }
                        "intruders" -> {
                            BackHandler { currentScreen = "home" }
                            IntruderVaultScreen(
                                context = this,
                                onBack = { currentScreen = "home" }
                            )
                        }
                        "vault" -> {
                            BackHandler { currentScreen = "home" }
                            SecureVaultScreen(
                                context = this,
                                onBack = { currentScreen = "home" }
                            )
                        }
                    }
                }
            }
        }
    }
}
