package com.example

import com.example.domain.model.AutoLockPolicy
import com.example.domain.model.PinMatchResult
import com.example.domain.model.PinValidator
import com.example.domain.model.SecuritySettings
import com.example.domain.model.TimeUnitOption
import com.example.presentation.screens.disguise.CalcInputPhase
import com.example.presentation.screens.disguise.CalcOperator
import com.example.presentation.screens.disguise.CalculatorAction
import com.example.presentation.screens.disguise.CalculatorState
import com.example.presentation.screens.disguise.CalculatorStateMachine
import com.example.presentation.screens.lock.LockEffect
import com.example.presentation.screens.lock.LockScreenStage
import com.example.presentation.screens.lock.LockStateMachine
import com.example.presentation.screens.lock.LockTarget
import com.example.presentation.screens.settings.AutoLockDraftSelection
import com.example.presentation.screens.settings.SettingsDraftState
import com.example.presentation.screens.settings.SettingsSaveResult
import com.example.presentation.screens.settings.SettingsTab
import com.example.presentation.screens.settings.SettingsValidator
import com.example.presentation.screens.vault.VaultStorage
import com.example.presentation.screens.vault.VaultTab
import com.example.service.ForegroundAction
import com.example.service.LockSessionTracker
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DasmoSecurityUnitTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `calculator chained arithmetic and decimal edge cases work cleanly`() {
        val settings = SecuritySettings(masterPin = "9999")
        var state = CalculatorState()

        fun press(action: CalculatorAction): Boolean {
            val out = CalculatorStateMachine.reduce(state, action, settings)
            state = out.nextState
            return out.unlocked
        }

        // 5 + 3 x 2 = 16
        assertFalse(press(CalculatorAction.Digit("5")))
        assertFalse(press(CalculatorAction.Operator(CalcOperator.ADD)))
        assertFalse(press(CalculatorAction.Digit("3")))
        assertFalse(press(CalculatorAction.Operator(CalcOperator.MULTIPLY)))
        assertEquals("8", state.display)
        assertFalse(press(CalculatorAction.Digit("2")))
        assertFalse(press(CalculatorAction.Equals))
        assertEquals("16", state.display)
        assertEquals(CalcInputPhase.RESULT_DISPLAYED, state.phase)

        // Pressing '.' right after a result starts "0." instead of "16."
        assertFalse(press(CalculatorAction.Decimal))
        assertEquals("0.", state.display)

        // Pressing 5 -> 0.5 + 1.5 = 2
        press(CalculatorAction.Digit("5"))
        press(CalculatorAction.Operator(CalcOperator.ADD))
        press(CalculatorAction.Digit("1"))
        press(CalculatorAction.Decimal)
        press(CalculatorAction.Digit("5"))
        press(CalculatorAction.Equals)
        assertEquals("2", state.display)

        // Divide by zero enters Error and recovers on next digit
        press(CalculatorAction.Operator(CalcOperator.DIVIDE))
        press(CalculatorAction.Digit("0"))
        press(CalculatorAction.Equals)
        assertEquals(CalcInputPhase.ERROR, state.phase)
        assertEquals("Error", state.display)
        press(CalculatorAction.Digit("7"))
        assertEquals("7", state.display)
        assertEquals(CalcInputPhase.ENTERING_FIRST, state.phase)
    }

    @Test
    fun `calculator unlocks only on exact PIN and equals without pending math`() {
        val settings = SecuritySettings(masterPin = "2580", dynamicTimePinEnabled = true)
        var state = CalculatorState()

        // Typing PIN after an operator (e.g. 1 + 2580 =) must NOT unlock, must compute 2581
        listOf(
            CalculatorAction.Digit("1"),
            CalculatorAction.Operator(CalcOperator.ADD),
            CalculatorAction.Digit("2"),
            CalculatorAction.Digit("5"),
            CalculatorAction.Digit("8"),
            CalculatorAction.Digit("0")
        ).forEach {
            state = CalculatorStateMachine.reduce(state, it, settings).nextState
        }
        val mathResult = CalculatorStateMachine.reduce(state, CalculatorAction.Equals, settings)
        assertFalse(mathResult.unlocked)
        assertEquals("2581", mathResult.nextState.display)

        // Entering 2580 fresh and pressing '=' unlocks
        state = CalculatorState()
        listOf("2", "5", "8", "0").forEach {
            state = CalculatorStateMachine.reduce(state, CalculatorAction.Digit(it), settings).nextState
        }
        val unlockOutcome = CalculatorStateMachine.reduce(state, CalculatorAction.Equals, settings)
        assertTrue(unlockOutcome.unlocked)
    }

    @Test
    fun `settings validator prevents master-decoy PIN collision and validates custom timeout`() {
        val base = SecuritySettings(masterPin = "1234", decoyPin = "")

        // Master == Decoy collision rejected and routes to DECOY tab
        val collisionDraft = SettingsDraftState.fromSettings(base).copy(
            activeTab = SettingsTab.AUTO_LOCK,
            decoyPinInput = "1234"
        )
        val collisionRes = SettingsValidator.validateAndBuild(collisionDraft, base)
        assertTrue(collisionRes is SettingsSaveResult.Invalid)
        val invalidCollision = collisionRes as SettingsSaveResult.Invalid
        assertEquals(SettingsTab.DECOY, invalidCollision.nextDraft.activeTab)
        assertNotNull(invalidCollision.nextDraft.decoyPinError)

        // Short PIN rejected and routes back to SECURITY tab even if saved from AUTO_LOCK tab
        val badPinDraft = SettingsDraftState.fromSettings(base).copy(
            activeTab = SettingsTab.AUTO_LOCK,
            newPin = "12",
            confirmPin = "12"
        )
        val badPinRes = SettingsValidator.validateAndBuild(badPinDraft, base) as SettingsSaveResult.Invalid
        assertEquals(SettingsTab.SECURITY, badPinRes.nextDraft.activeTab)
        assertNotNull(badPinRes.nextDraft.pinError)

        // Valid custom duration (2 Minutes -> 120000 ms) accepted
        val validDraft = SettingsDraftState.fromSettings(base).copy(
            newPin = "4321",
            confirmPin = "4321",
            decoyPinInput = "0000",
            autoLockSelection = AutoLockDraftSelection.Custom("2", TimeUnitOption.MINUTES)
        )
        val validRes = SettingsValidator.validateAndBuild(validDraft, base) as SettingsSaveResult.Valid
        assertEquals("4321", validRes.updatedSettings.masterPin)
        assertEquals("0000", validRes.updatedSettings.decoyPin)
        assertEquals(120_000L, validRes.updatedSettings.autoLockTimeoutMillis)

        // AutoLockPolicy roundtrip preserves custom unit
        val policy = AutoLockPolicy.fromMillis(120_000L)
        assertTrue(policy is AutoLockPolicy.Custom)
        assertEquals(2L, (policy as AutoLockPolicy.Custom).amount)
        assertEquals(TimeUnitOption.MINUTES, policy.unit)
    }

    @Test
    fun `lock state machine handles fake crash decoy and break-in siren after 3 wrong PINs`() {
        val settings = SecuritySettings(masterPin = "5678", breakInSirenEnabled = true)
        val hiddenTarget = LockTarget("com.test.app", "Test App", isHidden = true)

        var stage = LockScreenStage.initialFor(hiddenTarget, settings)
        assertTrue(stage is LockScreenStage.FakeCrashDecoy)

        stage = LockStateMachine.revealPinFromCrashDecoy(settings)
        assertTrue(stage is LockScreenStage.PinAuthentication)

        fun enterWrongPin(): List<LockEffect> {
            var collected = emptyList<LockEffect>()
            listOf("1", "1", "1", "1").forEach { digit ->
                val res = LockStateMachine.onDigitPressed(stage, digit, settings)
                stage = res.stage
                collected = res.effects
            }
            return collected
        }

        // Attempt 1 & 2: selfie only
        assertEquals(listOf(LockEffect.TriggerIntruderCapture), enterWrongPin())
        assertEquals(1, (stage as LockScreenStage.PinAuthentication).failedAttempts)
        assertEquals(listOf(LockEffect.TriggerIntruderCapture), enterWrongPin())
        assertEquals(2, (stage as LockScreenStage.PinAuthentication).failedAttempts)

        // Attempt 3: selfie + siren
        assertEquals(
            listOf(LockEffect.TriggerIntruderCapture, LockEffect.TriggerBreakInSiren),
            enterWrongPin()
        )
        assertEquals(3, (stage as LockScreenStage.PinAuthentication).failedAttempts)

        // Correct PIN unlocks cleanly
        var finalEffects = emptyList<LockEffect>()
        listOf("5", "6", "7", "8").forEach { digit ->
            val res = LockStateMachine.onDigitPressed(stage, digit, settings)
            stage = res.stage
            finalEffects = res.effects
        }
        assertEquals(LockScreenStage.Unlocked, stage)
        assertEquals(listOf(LockEffect.UnlockGranted), finalEffects)
    }

    @Test
    fun `lock session tracker enforces timeout and clears unlocked apps on screen off`() {
        val tracker = LockSessionTracker(ownPackageName = "com.example")
        val locked = setOf("com.whatsapp")
        val settings = SecuritySettings(autoLockTimeoutMillis = 30_000L)

        // 1. Launching locked app initially requires lock
        val first = tracker.onForegroundAppChanged("com.whatsapp", locked, settings, nowMillis = 1_000L)
        assertTrue(first is ForegroundAction.RequireLock)

        // 2. User unlocks app
        tracker.markAppUnlocked("com.whatsapp")

        // 3. Switch to launcher for 10s (under 30s timeout) -> remains unlocked
        tracker.onForegroundAppChanged("com.launcher", locked, settings, nowMillis = 2_000L)
        val resumeSoon = tracker.onForegroundAppChanged("com.whatsapp", locked, settings, nowMillis = 12_000L)
        assertTrue(resumeSoon is ForegroundAction.AllowUnlocked)

        // 4. Screen turns off -> unlocked set cleared immediately!
        tracker.onScreenOff(nowMillis = 15_000L)
        val afterScreenOn = tracker.onForegroundAppChanged("com.whatsapp", locked, settings, nowMillis = 16_000L)
        assertTrue(afterScreenOn is ForegroundAction.RequireLock)

        // 5. Re-unlock and exceed 30s timeout in background -> requires lock again
        tracker.markAppUnlocked("com.whatsapp")
        tracker.onForegroundAppChanged("com.launcher", locked, settings, nowMillis = 20_000L)
        val afterTimeout = tracker.onForegroundAppChanged("com.whatsapp", locked, settings, nowMillis = 55_000L)
        assertTrue(afterTimeout is ForegroundAction.RequireLock)
    }

    @Test
    fun `vault storage classifies uppercase photo and video extensions and sanitizes paths`() {
        assertEquals(VaultTab.PHOTOS, VaultStorage.classifyLegacyFileName("IMG_001.JPG.enc"))
        assertEquals(VaultTab.PHOTOS, VaultStorage.classifyLegacyFileName("avatar.HEIC.enc"))
        assertEquals(VaultTab.PHOTOS, VaultStorage.classifyLegacyFileName("pic.WEBP.enc"))
        assertEquals(VaultTab.VIDEOS, VaultStorage.classifyLegacyFileName("clip.MOV.enc"))
        assertEquals(VaultTab.VIDEOS, VaultStorage.classifyLegacyFileName("rec.WEBM.enc"))
        assertEquals(VaultTab.NOTES, VaultStorage.classifyLegacyFileName("Secret Note.enc"))

        val sanitized = VaultStorage.sanitizeFileName("../../etc/passwd")
        assertFalse(sanitized.contains("/"))
        assertFalse(sanitized.contains(".."))

        val f1 = VaultStorage.resolveUniqueFile(tempFolder.root, "My Note").apply { createNewFile() }
        val f2 = VaultStorage.resolveUniqueFile(tempFolder.root, "My Note")
        assertNotEquals(f1.name, f2.name)
    }

    @Test
    fun `dynamic time PIN and decoy vault PIN verification work deterministically`() {
        val fixedTime = SimpleDateFormat("HHmm", Locale.US).parse("1435")!!
        val settings = SecuritySettings(
            masterPin = "9876",
            decoyPin = "0000",
            dynamicTimePinEnabled = true
        )

        assertEquals(PinMatchResult.MasterMatch, PinValidator.verifyVaultPin("9876", settings, fixedTime))
        assertEquals(PinMatchResult.MasterMatch, PinValidator.verifyVaultPin("1435", settings, fixedTime))
        assertEquals(PinMatchResult.MasterMatch, PinValidator.verifyVaultPin("0235", settings, fixedTime))
        assertEquals(PinMatchResult.DecoyMatch, PinValidator.verifyVaultPin("0000", settings, fixedTime))
        assertEquals(PinMatchResult.Mismatch, PinValidator.verifyVaultPin("1111", settings, fixedTime))
    }

    @Test
    fun `lock session tracker protects package installer only when uninstall protection is enabled`() {
        val tracker = LockSessionTracker(ownPackageName = "com.example")
        val disabled = SecuritySettings(uninstallProtectionEnabled = false)
        val enabled = SecuritySettings(uninstallProtectionEnabled = true)

        val ignoredAction = tracker.onForegroundAppChanged(
            targetPackageName = "com.google.android.packageinstaller",
            lockedPackages = emptySet(),
            settings = disabled,
            nowMillis = 1_000L
        )
        assertTrue(ignoredAction is ForegroundAction.Ignore)

        val lockAction = tracker.onForegroundAppChanged(
            targetPackageName = "com.google.android.packageinstaller",
            lockedPackages = emptySet(),
            settings = enabled,
            nowMillis = 2_000L
        )
        assertTrue(lockAction is ForegroundAction.RequireLock)
        assertTrue((lockAction as ForegroundAction.RequireLock).isUninstallProtection)
    }
}
