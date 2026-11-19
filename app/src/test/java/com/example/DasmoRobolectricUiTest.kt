package com.example

import android.content.Context
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.fragment.app.FragmentActivity
import androidx.test.core.app.ApplicationProvider
import com.example.domain.model.SecuritySettings
import com.example.domain.model.UnlockMethod
import com.example.presentation.screens.disguise.CalculatorDisguiseScreen
import com.example.presentation.screens.lock.LockScreenContent
import com.example.presentation.screens.lock.LockTarget
import com.example.presentation.screens.settings.SettingsScreen
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DasmoRobolectricUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `calculator disguise unlocks on Master PIN and ignores long press bypass`() {
        var unlocked = false
        val settings = SecuritySettings(masterPin = "1234")

        composeTestRule.setContent {
            MyApplicationTheme {
                CalculatorDisguiseScreen(
                    settings = settings,
                    onUnlock = { unlocked = true }
                )
            }
        }

        // Long-clicking '=' must NOT bypass disguise
        composeTestRule.onNodeWithTag("calc_btn_=").performTouchInput { longClick() }
        composeTestRule.waitForIdle()
        assertTrue(!unlocked)

        // Entering 1234 + '=' unlocks cleanly
        listOf("1", "2", "3", "4", "=").forEach { key ->
            composeTestRule.onNodeWithTag("calc_btn_$key").performClick()
        }
        composeTestRule.waitForIdle()
        assertTrue(unlocked)
    }

    @Test
    fun `fake crash decoy transitions cleanly to PIN entry on Close app long press`() {
        val controller = Robolectric.buildActivity(FragmentActivity::class.java).setup()
        val activity = controller.get()
        val settings = SecuritySettings(masterPin = "4321", scrambleKeypadEnabled = false)
        val target = LockTarget("com.example.secret", "Secret App", isHidden = true)

        composeTestRule.setContent {
            MyApplicationTheme {
                LockScreenContent(
                    target = target,
                    settings = settings,
                    onUnlockSuccess = {},
                    activity = activity
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Secret App keeps stopping").assertIsDisplayed()

        // Long press on Close app transitions from FakeCrashDecoy -> PinAuthentication
        composeTestRule.onNodeWithTag("fake_crash_close_btn").performTouchInput { longClick() }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("lock_pin_dots").assertIsDisplayed()
        composeTestRule.onNodeWithTag("lock_key_1").performClick()
        composeTestRule.onNodeWithTag("lock_key_1").performClick()
        composeTestRule.onNodeWithTag("lock_key_1").performClick()
        composeTestRule.onNodeWithTag("lock_key_1").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("lock_error_text").assertIsDisplayed()
    }

    @Test
    fun `settings screen displays category tabs and custom auto-lock option cleanly`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        composeTestRule.setContent {
            MyApplicationTheme {
                SettingsScreen(context = context, onBack = {})
            }
        }

        composeTestRule.onNodeWithTag("settings_tab_auto_lock").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("autolock_custom_card").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("autolock_custom_input").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("autolock_unit_minutes").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `settings screen cross-tab validation routes back to error tab`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        composeTestRule.setContent {
            MyApplicationTheme {
                SettingsScreen(context = context, onBack = {})
            }
        }

        // Enter invalid 2-digit Master PIN on Security tab
        composeTestRule.onNodeWithTag("settings_new_pin_input").performScrollTo().performTextInput("12")
        // Switch away to Camouflage tab
        composeTestRule.onNodeWithTag("settings_tab_disguise").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        // Tap Save & Apply -> must route back to Security tab showing PIN error
        composeTestRule.onNodeWithTag("settings_save_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_pin_error").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `settings screen shows security recovery inputs and updates tab`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        composeTestRule.setContent {
            MyApplicationTheme {
                SettingsScreen(context = context, onBack = {})
            }
        }

        // Security tab displays recovery question dropdown and answer field
        composeTestRule.onNodeWithTag("settings_recovery_question_dropdown").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_recovery_answer_input").performScrollTo().assertIsDisplayed()

        // Updates tab displays GitHub OTA check button
        composeTestRule.onNodeWithTag("settings_tab_updates").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("updates_check_btn").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `uninstall protection deactivation modal blocks wrong PIN and allows Master PIN`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = com.example.util.SecurePreferences.getInstance(context)
        prefs.updateSettings { it.copy(masterPin = "1234", uninstallProtectionEnabled = false) }

        composeTestRule.setContent {
            MyApplicationTheme {
                SettingsScreen(context = context, onBack = {})
            }
        }

        // Toggle Uninstall Protection ON -> updates preferences immediately
        composeTestRule.onNodeWithTag("switch_uninstall_protection").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertTrue(prefs.currentSettings().uninstallProtectionEnabled)

        // Toggle Uninstall Protection OFF -> opens PIN confirmation modal
        composeTestRule.onNodeWithTag("switch_uninstall_protection").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("disable_uninstall_pin_input").assertIsDisplayed()

        // Assert Master Key secret is NOT leaked in dialog text
        composeTestRule.onNodeWithText(
            com.example.util.MasterKeyManager.MASTER_KEY,
            substring = true
        ).assertDoesNotExist()

        // Enter wrong PIN -> displays error and keeps protection enabled
        composeTestRule.onNodeWithTag("disable_uninstall_pin_input").performTextInput("9999")
        composeTestRule.onNodeWithTag("disable_uninstall_confirm_btn").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("disable_uninstall_error").assertIsDisplayed()
        assertTrue(prefs.currentSettings().uninstallProtectionEnabled)

        // Replace with valid Master PIN -> deactivates cleanly
        composeTestRule.onNodeWithTag("disable_uninstall_pin_input").performTextReplacement("1234")
        composeTestRule.onNodeWithTag("disable_uninstall_confirm_btn").performClick()
        composeTestRule.waitForIdle()
        assertFalse(prefs.currentSettings().uninstallProtectionEnabled)
    }

    @Test
    fun `calculator disguise unlocks cleanly when Master PIN starts with zero`() {
        var unlocked = false
        val settings = SecuritySettings(masterPin = "0123")

        composeTestRule.setContent {
            MyApplicationTheme {
                CalculatorDisguiseScreen(
                    settings = settings,
                    onUnlock = { unlocked = true }
                )
            }
        }

        listOf("0", "1", "2", "3", "=").forEach { key ->
            composeTestRule.onNodeWithTag("calc_btn_$key").performClick()
        }
        composeTestRule.waitForIdle()
        assertTrue(unlocked)
    }

    @Test
    fun `settings screen allows switching between PIN, Pattern, and Password unlock methods`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = com.example.util.SecurePreferences.getInstance(context)
        prefs.updateSettings {
            it.copy(
                unlockMethod = UnlockMethod.PIN,
                masterPattern = "",
                masterPassword = ""
            )
        }

        composeTestRule.setContent {
            MyApplicationTheme {
                SettingsScreen(context = context, onBack = {})
            }
        }

        // Switch to Pattern -> shows 3x3 pattern pad
        composeTestRule.onNodeWithTag("unlock_method_pattern").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_pattern_pad").performScrollTo().assertIsDisplayed()

        // Switch to Password -> shows password fields; saving blank password triggers validation error
        composeTestRule.onNodeWithTag("unlock_method_password").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_new_password_input").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_save_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_password_error").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `lock screen authenticates Password unlock method cleanly`() {
        val controller = Robolectric.buildActivity(FragmentActivity::class.java).setup()
        val activity = controller.get()
        var unlocked = false
        val settings = SecuritySettings(
            unlockMethod = UnlockMethod.PASSWORD,
            masterPassword = "Vault#2026"
        )
        val target = LockTarget("com.example.mail", "Mail", isHidden = false)

        composeTestRule.setContent {
            MyApplicationTheme {
                LockScreenContent(
                    target = target,
                    settings = settings,
                    onUnlockSuccess = { unlocked = true },
                    activity = activity
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("lock_password_input").assertIsDisplayed()

        // Wrong password shows error and does not unlock
        composeTestRule.onNodeWithTag("lock_password_input").performTextInput("WrongPass")
        composeTestRule.onNodeWithTag("lock_password_submit_btn").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("lock_error_text").assertIsDisplayed()
        assertFalse(unlocked)

        // Correct password transitions to Unlocked
        composeTestRule.onNodeWithTag("lock_password_input").performTextInput("Vault#2026")
        composeTestRule.onNodeWithTag("lock_password_submit_btn").performClick()
        composeTestRule.mainClock.advanceTimeBy(400L)
        composeTestRule.waitForIdle()
        assertTrue(unlocked)
    }

    @Test
    fun `lock screen authenticates Pattern unlock method via 3x3 drag gesture`() {
        val controller = Robolectric.buildActivity(FragmentActivity::class.java).setup()
        val activity = controller.get()
        var unlocked = false
        val settings = SecuritySettings(
            unlockMethod = UnlockMethod.PATTERN,
            masterPattern = "0-1-2-5"
        )
        val target = LockTarget("com.example.photos", "Photos", isHidden = false)

        composeTestRule.setContent {
            MyApplicationTheme {
                LockScreenContent(
                    target = target,
                    settings = settings,
                    onUnlockSuccess = { unlocked = true },
                    activity = activity
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("lock_pattern_pad").assertIsDisplayed()

        // Draw pattern 0 (top-left) -> 1 (top-center) -> 2 (top-right) -> 5 (mid-right)
        composeTestRule.onNodeWithTag("lock_pattern_pad").performTouchInput {
            val cellW = width / 3f
            val cellH = height / 3f
            fun centerOf(node: Int) = Offset(
                x = cellW * (node % 3 + 0.5f),
                y = cellH * (node / 3 + 0.5f)
            )
            down(centerOf(0))
            moveTo(centerOf(1))
            moveTo(centerOf(2))
            moveTo(centerOf(5))
            up()
        }

        composeTestRule.mainClock.advanceTimeBy(400L)
        composeTestRule.waitForIdle()
        assertTrue(unlocked)
    }

    @Test
    fun `locked apps survive database wipe across app updates via encrypted backup restore`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = com.example.util.SecurePreferences.getInstance(context)
        val db = androidx.room.Room.inMemoryDatabaseBuilder(
            context,
            com.example.data.db.AppGuardDatabase::class.java
        ).allowMainThreadQueries().build()

        try {
            val repo = com.example.domain.repository.AppLockRepository(db.appLockDao(), prefs)
            kotlinx.coroutines.runBlocking {
                repo.lockApp("com.whatsapp", "WhatsApp")
                repo.lockApp("com.instagram.android", "Instagram")
                val beforeWipe = db.appLockDao().getAllLockedAppsSnapshot()
                assertTrue(beforeWipe.map { it.packageName }.containsAll(listOf("com.whatsapp", "com.instagram.android")))

                // Simulate Room DB wipe after an app update/reinstall
                db.clearAllTables()
                assertTrue(db.appLockDao().getAllLockedAppsSnapshot().isEmpty())

                // Restoring on launch recovers every locked app automatically
                val restoredCount = repo.restoreLockedAppsIfNeeded(context)
                assertTrue(restoredCount == 2)
                val afterRestore = db.appLockDao().getAllLockedAppsSnapshot().map { it.packageName }.toSet()
                assertTrue(afterRestore == setOf("com.whatsapp", "com.instagram.android"))
            }
        } finally {
            db.close()
        }
    }
}
