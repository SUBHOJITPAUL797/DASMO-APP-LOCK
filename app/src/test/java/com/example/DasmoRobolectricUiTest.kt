package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.fragment.app.FragmentActivity
import androidx.test.core.app.ApplicationProvider
import com.example.domain.model.SecuritySettings
import com.example.presentation.screens.disguise.CalculatorDisguiseScreen
import com.example.presentation.screens.lock.LockScreenContent
import com.example.presentation.screens.lock.LockTarget
import com.example.presentation.screens.settings.SettingsScreen
import com.example.ui.theme.MyApplicationTheme
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
}
