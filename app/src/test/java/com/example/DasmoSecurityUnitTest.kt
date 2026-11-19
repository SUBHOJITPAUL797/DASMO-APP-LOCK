package com.example

import com.example.domain.model.AutoLockPolicy
import com.example.domain.model.PasswordValidator
import com.example.domain.model.PatternValidator
import com.example.domain.model.PinMatchResult
import com.example.domain.model.PinValidationResult
import com.example.domain.model.PinValidator
import com.example.domain.model.SecuritySettings
import com.example.domain.model.TimeUnitOption
import com.example.domain.model.UnlockMethod
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
import com.example.presentation.screens.settings.PatternDraftStep
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
    fun `calculator supports leading-zero PINs and leading-zero dynamic time PIN without breaking math`() {
        val zeroPrefixSettings = SecuritySettings(masterPin = "0123")
        var state = CalculatorState()
        listOf("0", "1", "2", "3").forEach {
            state = CalculatorStateMachine.reduce(state, CalculatorAction.Digit(it), zeroPrefixSettings).nextState
        }
        assertEquals("123", state.display)
        assertTrue(CalculatorStateMachine.reduce(state, CalculatorAction.Equals, zeroPrefixSettings).unlocked)

        // All-zeros PIN "0000" unlocks cleanly
        val allZeroSettings = SecuritySettings(masterPin = "0000")
        state = CalculatorState()
        repeat(4) {
            state = CalculatorStateMachine.reduce(state, CalculatorAction.Digit("0"), allZeroSettings).nextState
        }
        assertEquals("0", state.display)
        assertTrue(CalculatorStateMachine.reduce(state, CalculatorAction.Equals, allZeroSettings).unlocked)

        // Dynamic Time PIN "0235" unlocks cleanly at 02:35
        val earlyMorning = SimpleDateFormat("HHmm", Locale.US).parse("0235")!!
        val timePinSettings = SecuritySettings(masterPin = "9999", dynamicTimePinEnabled = true)
        state = CalculatorState()
        listOf("0", "2", "3", "5").forEach {
            state = CalculatorStateMachine.reduce(state, CalculatorAction.Digit(it), timePinSettings, earlyMorning).nextState
        }
        assertTrue(CalculatorStateMachine.reduce(state, CalculatorAction.Equals, timePinSettings, earlyMorning).unlocked)

        // Decimal "0.123" must NOT unlock "0123"
        state = CalculatorState()
        listOf(
            CalculatorAction.Digit("0"),
            CalculatorAction.Decimal,
            CalculatorAction.Digit("1"),
            CalculatorAction.Digit("2"),
            CalculatorAction.Digit("3")
        ).forEach {
            state = CalculatorStateMachine.reduce(state, it, zeroPrefixSettings).nextState
        }
        assertFalse(CalculatorStateMachine.reduce(state, CalculatorAction.Equals, zeroPrefixSettings).unlocked)

        // Math after leading-zero digits ("0123 + 1 =") computes 124 and does NOT unlock
        state = CalculatorState()
        listOf(
            CalculatorAction.Digit("0"),
            CalculatorAction.Digit("1"),
            CalculatorAction.Digit("2"),
            CalculatorAction.Digit("3"),
            CalculatorAction.Operator(CalcOperator.ADD),
            CalculatorAction.Digit("1")
        ).forEach {
            state = CalculatorStateMachine.reduce(state, it, zeroPrefixSettings).nextState
        }
        val mathOut = CalculatorStateMachine.reduce(state, CalculatorAction.Equals, zeroPrefixSettings)
        assertFalse(mathOut.unlocked)
        assertEquals("124", mathOut.nextState.display)
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
    fun `lock session tracker protects package installer and android settings only when uninstall protection is enabled`() {
        val tracker = LockSessionTracker(ownPackageName = "com.example")
        val disabled = SecuritySettings(uninstallProtectionEnabled = false)
        val enabled = SecuritySettings(uninstallProtectionEnabled = true)

        val protectedPackages = listOf(
            "com.google.android.packageinstaller",
            "com.oplus.packageinstaller",
            "com.android.settings"
        )

        protectedPackages.forEach { pkg ->
            val ignoredAction = tracker.onForegroundAppChanged(
                targetPackageName = pkg,
                lockedPackages = emptySet(),
                settings = disabled,
                nowMillis = 1_000L
            )
            assertTrue("Expected Ignore when disabled for $pkg", ignoredAction is ForegroundAction.Ignore)
        }

        protectedPackages.forEachIndexed { index, pkg ->
            val lockAction = tracker.onForegroundAppChanged(
                targetPackageName = pkg,
                lockedPackages = emptySet(),
                settings = enabled,
                nowMillis = 2_000L + index
            )
            assertTrue("Expected RequireLock when enabled for $pkg", lockAction is ForegroundAction.RequireLock)
            assertTrue((lockAction as ForegroundAction.RequireLock).isUninstallProtection)
        }
    }

    @Test
    fun `settings validator enforces recovery answer when question changes and returns recoveryUpdate`() {
        val base = SecuritySettings(masterPin = "1234")
        val existingQuestion = "What city were you born in?"
        val initialDraft = SettingsDraftState.fromSettings(base, savedQuestion = existingQuestion)
        assertFalse(initialDraft.hasUnsavedChanges(base))

        // Changing question without providing a new answer must fail validation
        val missingAnswerDraft = initialDraft.copy(
            activeTab = SettingsTab.AUTO_LOCK,
            recoveryQuestion = "What was your childhood nickname?"
        )
        assertTrue(missingAnswerDraft.hasUnsavedChanges(base))
        val invalid = SettingsValidator.validateAndBuild(missingAnswerDraft, base)
        assertTrue(invalid is SettingsSaveResult.Invalid)
        val invalidResult = invalid as SettingsSaveResult.Invalid
        assertEquals(SettingsTab.SECURITY, invalidResult.nextDraft.activeTab)
        assertNotNull(invalidResult.nextDraft.recoveryError)

        // Providing a new answer succeeds and emits recoveryUpdate pair
        val validDraft = missingAnswerDraft.copy(recoveryAnswerInput = "Rocky")
        val valid = SettingsValidator.validateAndBuild(validDraft, base)
        assertTrue(valid is SettingsSaveResult.Valid)
        val validResult = valid as SettingsSaveResult.Valid
        assertEquals("What was your childhood nickname?" to "Rocky", validResult.recoveryUpdate)
    }

    @Test
    fun `pattern validator handles collinear midpoint snapping and enforces minimum 4 unique dots`() {
        // 0 -> 2 automatically inserts midpoint 1
        var nodes = PatternValidator.appendNodeWithMidpoint(emptyList(), 0)
        nodes = PatternValidator.appendNodeWithMidpoint(nodes, 2)
        assertEquals(listOf(0, 1, 2), nodes)

        // 2 -> 6 automatically inserts diagonal center 4
        nodes = PatternValidator.appendNodeWithMidpoint(nodes, 6)
        assertEquals(listOf(0, 1, 2, 4, 6), nodes)

        // Duplicate node is ignored
        nodes = PatternValidator.appendNodeWithMidpoint(nodes, 1)
        assertEquals(listOf(0, 1, 2, 4, 6), nodes)

        // Valid 5-node encoded pattern roundtrips cleanly
        val encoded = PatternValidator.encode(nodes)
        assertEquals("0-1-2-4-6", encoded)
        assertEquals(nodes, PatternValidator.decode(encoded))
        assertTrue(PatternValidator.isValidEncodedPattern(encoded))

        // Short pattern (< 4 dots) rejected
        assertFalse(PatternValidator.isValidPattern(listOf(0, 1, 2)))
        assertTrue(
            PatternValidator.validatePatternChange(
                newPattern = "0-1-2",
                confirmPattern = "0-1-2",
                isPatternActive = true,
                existingSavedPattern = ""
            ) is PinValidationResult.Invalid
        )

        // Mismatch rejected
        assertTrue(
            PatternValidator.validatePatternChange(
                newPattern = "0-1-2-5",
                confirmPattern = "0-1-2-4",
                isPatternActive = true,
                existingSavedPattern = ""
            ) is PinValidationResult.Invalid
        )
    }

    @Test
    fun `password validator enforces min and max length, non-blank, and matching confirmation`() {
        assertFalse(PasswordValidator.isValidPassword("123"))
        assertFalse(PasswordValidator.isValidPassword("    "))
        assertTrue(PasswordValidator.isValidPassword("Dasmo@2026"))
        assertFalse(PasswordValidator.isValidPassword("a".repeat(33)))

        assertTrue(
            PasswordValidator.validatePasswordChange(
                newPassword = "",
                confirmPassword = "",
                isPasswordActive = true,
                existingSavedPassword = ""
            ) is PinValidationResult.Invalid
        )
        assertTrue(
            PasswordValidator.validatePasswordChange(
                newPassword = "Alpha1",
                confirmPassword = "Alpha2",
                isPasswordActive = true,
                existingSavedPassword = ""
            ) is PinValidationResult.Invalid
        )
        assertTrue(
            PasswordValidator.validatePasswordChange(
                newPassword = "Alpha1",
                confirmPassword = "Alpha1",
                isPasswordActive = true,
                existingSavedPassword = ""
            ) is PinValidationResult.Valid
        )
    }

    @Test
    fun `lock state machine supports Pattern and Password unlock with intruder capture and siren`() {
        val patternSettings = SecuritySettings(
            unlockMethod = UnlockMethod.PATTERN,
            masterPattern = "0-1-2-5",
            breakInSirenEnabled = true
        )
        val hiddenTarget = LockTarget("com.test.vaulted", "Vaulted App", isHidden = true)

        // Fake crash decoy reveals PatternAuthentication when Pattern is active
        var stage = LockScreenStage.initialFor(hiddenTarget, patternSettings)
        assertTrue(stage is LockScreenStage.FakeCrashDecoy)
        stage = LockStateMachine.revealPinFromCrashDecoy(patternSettings)
        assertTrue(stage is LockScreenStage.PatternAuthentication)

        // Short pattern (< 4 dots) gives hint without triggering intruder capture or incrementing failed count
        val shortRes = LockStateMachine.onPatternCompleted(stage, listOf(0, 1, 2), patternSettings)
        assertTrue(shortRes.effects.isEmpty())
        assertEquals(0, shortRes.stage.currentFailedAttempts)
        assertNotNull(shortRes.stage.currentErrorMessage)

        // 3 wrong patterns -> attempt 3 triggers both selfie and siren
        var patternStage = shortRes.stage
        repeat(2) {
            val res = LockStateMachine.onPatternCompleted(patternStage, listOf(0, 3, 6, 7), patternSettings)
            assertEquals(listOf(LockEffect.TriggerIntruderCapture), res.effects)
            patternStage = res.stage
        }
        val thirdWrong = LockStateMachine.onPatternCompleted(patternStage, listOf(0, 3, 6, 7), patternSettings)
        assertEquals(
            listOf(LockEffect.TriggerIntruderCapture, LockEffect.TriggerBreakInSiren),
            thirdWrong.effects
        )
        assertEquals(3, thirdWrong.stage.currentFailedAttempts)

        // Correct pattern unlocks cleanly
        val unlockedPattern = LockStateMachine.onPatternCompleted(thirdWrong.stage, listOf(0, 1, 2, 5), patternSettings)
        assertEquals(LockScreenStage.Unlocked, unlockedPattern.stage)
        assertEquals(listOf(LockEffect.UnlockGranted), unlockedPattern.effects)

        // Password flow: initialFor creates PasswordAuthentication
        val passwordSettings = SecuritySettings(
            unlockMethod = UnlockMethod.PASSWORD,
            masterPassword = "ShadowKey99",
            breakInSirenEnabled = true
        )
        val visibleTarget = LockTarget("com.test.chat", "Chat", isHidden = false)
        var passStage = LockScreenStage.initialFor(visibleTarget, passwordSettings)
        assertTrue(passStage is LockScreenStage.PasswordAuthentication)

        // Blank submit warns without intruder capture
        val blankRes = LockStateMachine.onPasswordSubmitted(passStage, passwordSettings)
        assertTrue(blankRes.effects.isEmpty())
        assertEquals(0, blankRes.stage.currentFailedAttempts)

        // Wrong password triggers capture and clears input
        passStage = LockStateMachine.onPasswordChanged(blankRes.stage, "WrongPass")
        val wrongPassRes = LockStateMachine.onPasswordSubmitted(passStage, passwordSettings)
        assertEquals(listOf(LockEffect.TriggerIntruderCapture), wrongPassRes.effects)
        assertEquals("", (wrongPassRes.stage as LockScreenStage.PasswordAuthentication).enteredPassword)

        // Correct password unlocks
        passStage = LockStateMachine.onPasswordChanged(wrongPassRes.stage, "ShadowKey99")
        val validPassRes = LockStateMachine.onPasswordSubmitted(passStage, passwordSettings)
        assertEquals(LockScreenStage.Unlocked, validPassRes.stage)
        assertEquals(listOf(LockEffect.UnlockGranted), validPassRes.effects)
    }

    @Test
    fun `settings validator enforces credentials when switching unlock method to Pattern or Password`() {
        val base = SecuritySettings(masterPin = "1234", unlockMethod = UnlockMethod.PIN)

        // Switching to PATTERN without drawing/confirming fails cleanly
        val emptyPatternDraft = SettingsDraftState.fromSettings(base).copy(
            activeTab = SettingsTab.AUTO_LOCK,
            unlockMethod = UnlockMethod.PATTERN
        )
        val invalidPattern = SettingsValidator.validateAndBuild(emptyPatternDraft, base) as SettingsSaveResult.Invalid
        assertEquals(SettingsTab.SECURITY, invalidPattern.nextDraft.activeTab)
        assertNotNull(invalidPattern.nextDraft.patternError)

        // Valid confirmed Pattern succeeds and persists unlockMethod + masterPattern
        val validPatternDraft = emptyPatternDraft.copy(
            newPattern = "0-1-2-5",
            confirmPattern = "0-1-2-5",
            patternStep = PatternDraftStep.READY_TO_SAVE
        )
        val savedPattern = SettingsValidator.validateAndBuild(validPatternDraft, base) as SettingsSaveResult.Valid
        assertEquals(UnlockMethod.PATTERN, savedPattern.updatedSettings.unlockMethod)
        assertEquals("0-1-2-5", savedPattern.updatedSettings.masterPattern)

        // Switching to PASSWORD without entering password fails cleanly
        val emptyPassDraft = SettingsDraftState.fromSettings(base).copy(
            unlockMethod = UnlockMethod.PASSWORD
        )
        val invalidPass = SettingsValidator.validateAndBuild(emptyPassDraft, base) as SettingsSaveResult.Invalid
        assertNotNull(invalidPass.nextDraft.passwordError)

        // Valid confirmed Password succeeds and persists unlockMethod + masterPassword
        val validPassDraft = emptyPassDraft.copy(
            newPassword = "Obsidian#7",
            confirmPassword = "Obsidian#7"
        )
        val savedPass = SettingsValidator.validateAndBuild(validPassDraft, base) as SettingsSaveResult.Valid
        assertEquals(UnlockMethod.PASSWORD, savedPass.updatedSettings.unlockMethod)
        assertEquals("Obsidian#7", savedPass.updatedSettings.masterPassword)
    }
}
