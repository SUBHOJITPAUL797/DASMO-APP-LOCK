package com.example

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.*
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.example.data.db.AppGuardDatabase
import com.example.domain.repository.AppLockRepository
import com.example.presentation.screens.disguise.CalculatorDisguiseScreen
import com.example.presentation.screens.home.AppGuardHomeScreen
import com.example.presentation.screens.intruders.IntruderVaultScreen
import com.example.presentation.screens.lock.AppEntranceLockScreen
import com.example.presentation.screens.settings.SettingsScreen
import com.example.presentation.screens.vault.SecureVaultScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.util.SecurePreferences
import com.example.util.VaultStorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

enum class AppDestination {
    HOME,
    INTRUDERS,
    VAULT,
    SETTINGS
}

sealed interface AppGateState {
    data object Locked : AppGateState
    data class Unlocked(val destination: AppDestination = AppDestination.HOME) : AppGateState
}

class MainActivity : FragmentActivity() {
    private lateinit var repository: AppLockRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val securePrefs = SecurePreferences.getInstance(this)
        repository = AppLockRepository(AppGuardDatabase.getDatabase(this).appLockDao())

        lifecycleScope.launch(Dispatchers.IO) {
            VaultStorageManager.migrateLegacyInternalStorage(this@MainActivity)
        }

        setContent {
            val settings by securePrefs.settingsFlow.collectAsStateWithLifecycle()

            LaunchedEffect(settings.preventScreenshots) {
                if (settings.preventScreenshots) {
                    window.setFlags(
                        WindowManager.LayoutParams.FLAG_SECURE,
                        WindowManager.LayoutParams.FLAG_SECURE
                    )
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }

            MyApplicationTheme {
                val lifecycleOwner = LocalLifecycleOwner.current
                var gateState by remember { mutableStateOf<AppGateState>(AppGateState.Locked) }
                var suppressNextAutoLock by remember { mutableStateOf(false) }

                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        when (event) {
                            Lifecycle.Event.ON_STOP -> {
                                if (!isChangingConfigurations && !suppressNextAutoLock) {
                                    gateState = AppGateState.Locked
                                }
                            }
                            Lifecycle.Event.ON_RESUME -> {
                                suppressNextAutoLock = false
                            }
                            else -> Unit
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }

                AnimatedContent(
                    targetState = gateState,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "app_gate_transition"
                ) { currentGate ->
                    when (currentGate) {
                        is AppGateState.Locked -> {
                            if (settings.calculatorDisguiseEnabled && securePrefs.hasMasterPin()) {
                                CalculatorDisguiseScreen(
                                    settings = settings,
                                    onUnlock = {
                                        gateState = AppGateState.Unlocked(AppDestination.HOME)
                                    }
                                )
                            } else {
                                AppEntranceLockScreen(
                                    activity = this@MainActivity,
                                    securePrefs = securePrefs,
                                    onUnlockSuccess = {
                                        gateState = AppGateState.Unlocked(AppDestination.HOME)
                                    }
                                )
                            }
                        }

                        is AppGateState.Unlocked -> {
                            AnimatedContent(
                                targetState = currentGate.destination,
                                transitionSpec = { fadeIn() togetherWith fadeOut() },
                                label = "destination_transition"
                            ) { destination ->
                                when (destination) {
                                    AppDestination.HOME -> {
                                        BackHandler { moveTaskToBack(true) }
                                        AppGuardHomeScreen(
                                            repository = repository,
                                            context = this@MainActivity,
                                            onNavigateToIntruders = {
                                                gateState = AppGateState.Unlocked(AppDestination.INTRUDERS)
                                            },
                                            onNavigateToVault = {
                                                gateState = AppGateState.Unlocked(AppDestination.VAULT)
                                            },
                                            onNavigateToSettings = {
                                                gateState = AppGateState.Unlocked(AppDestination.SETTINGS)
                                            },
                                            onLockToDisguise = {
                                                gateState = AppGateState.Locked
                                            },
                                            onSuppressNextRelock = {
                                                suppressNextAutoLock = true
                                            }
                                        )
                                    }

                                    AppDestination.INTRUDERS -> {
                                        BackHandler {
                                            gateState = AppGateState.Unlocked(AppDestination.HOME)
                                        }
                                        IntruderVaultScreen(
                                            context = this@MainActivity,
                                            onBack = {
                                                gateState = AppGateState.Unlocked(AppDestination.HOME)
                                            }
                                        )
                                    }

                                    AppDestination.VAULT -> {
                                        BackHandler {
                                            gateState = AppGateState.Unlocked(AppDestination.HOME)
                                        }
                                        SecureVaultScreen(
                                            context = this@MainActivity,
                                            onBack = {
                                                gateState = AppGateState.Unlocked(AppDestination.HOME)
                                            },
                                            onSuppressNextRelock = {
                                                suppressNextAutoLock = true
                                            }
                                        )
                                    }

                                    AppDestination.SETTINGS -> {
                                        BackHandler {
                                            gateState = AppGateState.Unlocked(AppDestination.HOME)
                                        }
                                        SettingsScreen(
                                            context = this@MainActivity,
                                            onBack = {
                                                gateState = AppGateState.Unlocked(AppDestination.HOME)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
