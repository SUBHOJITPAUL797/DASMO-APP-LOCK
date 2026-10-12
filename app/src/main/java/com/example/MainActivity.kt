package com.example

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.db.AppGuardDatabase
import com.example.domain.repository.AppLockRepository
import com.example.presentation.screens.disguise.CalculatorDisguiseScreen
import com.example.presentation.screens.home.AppGuardHomeScreen
import com.example.presentation.screens.intruders.IntruderVaultScreen
import com.example.presentation.screens.lock.AppEntranceLockScreen
import com.example.presentation.screens.vault.SecureVaultScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.util.SecurePreferences
import com.example.util.VaultStorageManager

class MainActivity : FragmentActivity() {
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

        // Migrate any legacy files from internal storage to persistent storage
        VaultStorageManager.migrateLegacyInternalStorage(this)

        repository = AppLockRepository(AppGuardDatabase.getDatabase(this).appLockDao())

        setContent {
            MyApplicationTheme {
                val lifecycleOwner = LocalLifecycleOwner.current
                val isCalculatorDisguise = remember {
                    securePrefs.getString("calculator_disguise", "false") == "true"
                }
                val masterPin = remember {
                    securePrefs.getString("app_pin", "")
                }
                val decoyPin = remember {
                    securePrefs.getString("decoy_pin", "")
                }

                // App entrance lock state: requires PIN on app launch
                var isAppUnlocked by remember { mutableStateOf(false) }
                var currentScreen by remember { mutableStateOf("home") }

                // Automatically re-lock whenever the app goes into the background
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_STOP) {
                            isAppUnlocked = false
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose {
                        lifecycleOwner.lifecycle.removeObserver(observer)
                    }
                }

                if (!isAppUnlocked) {
                    if (isCalculatorDisguise && securePrefs.hasMasterPin()) {
                        CalculatorDisguiseScreen(
                            masterPin = masterPin,
                            decoyPin = decoyPin,
                            onUnlock = { isAppUnlocked = true }
                        )
                    } else {
                        AppEntranceLockScreen(
                            activity = this,
                            securePrefs = securePrefs,
                            onUnlockSuccess = { isAppUnlocked = true }
                        )
                    }
                } else {
                    when (currentScreen) {
                        "home" -> {
                            BackHandler {
                                moveTaskToBack(true)
                            }
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
