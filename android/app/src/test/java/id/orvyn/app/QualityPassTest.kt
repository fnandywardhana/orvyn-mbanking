package id.orvyn.app

import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import id.orvyn.app.data.DeviceStore
import id.orvyn.app.ui.bills.billPeriod
import id.orvyn.app.ui.bills.operatorFor
import id.orvyn.app.ui.components.AmountText
import id.orvyn.app.ui.components.Chip
import id.orvyn.app.ui.components.FlowSteps
import id.orvyn.app.ui.components.InputGuard
import id.orvyn.app.ui.components.OrvynButton
import id.orvyn.app.ui.components.PinScreen
import id.orvyn.app.ui.components.SwitchRow
import id.orvyn.app.ui.components.gramsKey
import id.orvyn.app.ui.history.DateFilter
import id.orvyn.app.ui.history.dateChipLabel
import id.orvyn.app.ui.history.dateRange
import id.orvyn.app.ui.isAcceptablePin
import id.orvyn.app.ui.register.CodeStep
import id.orvyn.app.ui.theme.OrvynTheme
import id.orvyn.app.ui.theme.OrvynType
import id.orvyn.app.ui.vyna.VynaScreen
import id.orvyn.app.ui.vyna.bold
import id.orvyn.app.ui.vyna.spoken
import id.orvyn.app.ui.home.bannerLabel
import id.orvyn.app.ui.components.LocalSnacks
import id.orvyn.app.ui.components.OrvynSnackbarHost
import id.orvyn.app.ui.components.ReceiptLine
import id.orvyn.app.ui.components.SuccessScreen
import id.orvyn.app.ui.components.WhiteTextButton
import id.orvyn.app.ui.components.rememberSnacks
import id.orvyn.app.ui.wealth.OpenDepositFlow
import androidx.compose.foundation.layout.Box
import id.orvyn.app.ui.wealth.goldRupiah
import id.orvyn.app.ui.wealth.rateLine
import id.orvyn.app.ui.wealth.signedPercent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/** 0.9.0 quality pass: the acceptance criteria stories.md marks for automated tests. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h640dp-xxhdpi")
class QualityPassTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun set(content: @androidx.compose.runtime.Composable () -> Unit) = compose.setContent { OrvynTheme(dark = false) { content() } }

    // ---- Rules and formats

    @Test fun `weak PINs are refused with the server's rule (Q-D3, PO-3)`() {
        for (p in listOf("123456", "111111", "654321", "000000", "12345", "abcdef")) assertFalse(p, isAcceptablePin(p))
        for (p in listOf("482915", "204060", "135791", "112233")) assertTrue(p, isAcceptablePin(p))
    }

    @Test fun `grams take one point and two decimals, and cost grams times price rounded up (Q-F6)`() {
        var g = ""
        for (k in "0.059") g = gramsKey(g, k)
        assertEquals("0.05", g) // the third decimal is ignored
        assertEquals("0.", gramsKey("", '.'))
        assertEquals("0.5", gramsKey("0.5", '.'))
        assertEquals(75_000L, goldRupiah("0.05", 1_500_000))       // not Rp75,001 from a float
        assertEquals(19_620L, goldRupiah("0.01", 1_962_000))
        assertEquals(1_963L, goldRupiah("0.001", 1_962_001))
    }

    @Test fun `formats, JPY per 100, bill period, true minus, operator by prefix (Q-G4, Q-F5)`() {
        assertEquals("100 JPY = Rp10,802.50", rateLine("JPY", 108.025))
        assertEquals("1 USD = Rp16,420", rateLine("USD", 16420.0))
        assertEquals("Oct 2026", billPeriod("2026-10"))
        assertEquals("−1.30%", signedPercent(-1.3))
        assertEquals("+4.10%", signedPercent(4.1))
        assertEquals("Telkomsel", operatorFor("0812"))
        assertEquals("Smartfren", operatorFor("+62 881 2345 6789"))
        assertNull(operatorFor("0800"))
    }

    @Test fun `history date filters, ranges in WIB days and chip labels (Q-F3)`() {
        val today = LocalDate.of(2026, 10, 16)
        assertEquals(LocalDate.of(2026, 10, 1) to null, dateRange(DateFilter("this"), today))
        assertEquals(LocalDate.of(2026, 9, 1) to LocalDate.of(2026, 10, 1), dateRange(DateFilter("last"), today))
        assertEquals(LocalDate.of(2026, 10, 3) to LocalDate.of(2026, 10, 17), dateRange(DateFilter("pick", "2026-10-03", "2026-10-16"), today))
        assertEquals("3 Oct to 16 Oct", dateChipLabel(DateFilter("pick", "2026-10-03", "2026-10-16"), today))
        assertEquals("3 Oct 2025 to 16 Jan 2026", dateChipLabel(DateFilter("pick", "2025-10-03", "2026-01-16"), today))
        assertEquals("Date", dateChipLabel(DateFilter("any"), today))
    }

    @Test fun `Vyna's bold text is bold, not asterisks (Q-E6 AC3)`() {
        val s = bold("Your balance is **Rp1,000** today")
        assertEquals("Your balance is Rp1,000 today", s.text)
        assertEquals(FontWeight.Bold, s.spanStyles.single().item.fontWeight)
    }

    @Test fun `TalkBack and the reader voice get Vyna's answer without markdown symbols (Q-01-F3)`() {
        assertEquals("Savings Rp16,528,466\nPockets Rp107,000\n1. Ask me anything", spoken("- Savings **Rp16,528,466**\n\n* Pockets **Rp107,000**\n1. Ask me anything"))
        assertEquals("A * star stays", spoken("A * star stays"))
    }

    @Test fun `banner labels get one stop between title and body (Q-01-F9)`() {
        assertEquals("Scan. Pay. Done. Pay at any QRIS sticker with your camera.", bannerLabel("Scan. Pay. Done.", "Pay at any QRIS sticker with your camera."))
        assertEquals("Flying soon? Switch on overseas payments before you go.", bannerLabel("Flying soon?", "Switch on overseas payments before you go."))
        assertEquals("Save more. Every day.", bannerLabel("Save more", "Every day."))
    }

    @Test fun `the open-deposit button says why it is off (Q-01-F6, design 4_7)`() {
        set { OpenDepositFlow(onExit = {}) }
        compose.onNodeWithText("Enter an amount").assertExists()
        compose.mainClock.advanceTimeBy(1_000) // past the screen's input guard (4.3)
        compose.onNodeWithContentDescription("1").performClick()
        compose.onNodeWithText("Minimum Rp1,000,000").assertExists()
    }

    @Test fun `a snackbar sits above the success screen's buttons, never on them (Q-01-F2, design 4_11)`() {
        var snacks: id.orvyn.app.ui.components.Snacks? = null
        set {
            val s = rememberSnacks().also { snacks = it }
            CompositionLocalProvider(LocalSnacks provides s) {
                Box {
                    SuccessScreen("Sent", "Rp50,000", "to Sinta Maharani", details = { ReceiptLine("To", "ORVYN Bank") }, onPrimary = {}, onShare = {},
                        textAction = { WhiteTextButton("Save as a recipient") {} }, buzz = false)
                    OrvynSnackbarHost(s)
                }
            }
        }
        compose.runOnIdle { snacks!!.show("You're offline. Check your connection and try again.", error = true) }
        compose.waitForIdle()
        val snack = compose.onNodeWithText("You're offline. Check your connection and try again.").fetchSemanticsNode().boundsInRoot
        val save = compose.onNodeWithText("Save as a recipient").fetchSemanticsNode().boundsInRoot
        assertTrue("snackbar $snack overlaps $save", snack.bottom <= save.top)
    }

    @Test fun `the keyboard action key runs the field's action, and Next moves to the next field (Q-01-F11, design 17_3)`() {
        var done = 0
        set {
            val next = androidx.compose.runtime.remember { androidx.compose.ui.focus.FocusRequester() }
            Column {
                id.orvyn.app.ui.components.OrvynTextField("Full name", "Raka", {}, imeAction = androidx.compose.ui.text.input.ImeAction.Next, onImeAction = { next.requestFocus() })
                id.orvyn.app.ui.components.OrvynTextField("Password", "rahasia123", {}, password = true, imeAction = androidx.compose.ui.text.input.ImeAction.Done,
                    onImeAction = { done++ }, fieldModifier = Modifier.focusRequester(next))
            }
        }
        compose.onNode(hasText("Raka") and androidx.compose.ui.test.hasSetTextAction()).performClick()
        compose.onNode(hasText("Raka") and androidx.compose.ui.test.hasSetTextAction()).performImeAction()
        compose.onNode(androidx.compose.ui.test.hasSetTextAction() and androidx.compose.ui.test.isFocused()).assert(SemanticsMatcher("is the password field") { n ->
            SemanticsProperties.Password in n.config })
        compose.onNode(androidx.compose.ui.test.hasSetTextAction() and androidx.compose.ui.test.isFocused()).performImeAction()
        assertEquals(1, done)
    }

    // ---- Components

    @Test fun `a long amount wraps only after a thousands comma, and TalkBack reads it plain (Q-A4)`() {
        set {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                AmountText("Rp834,912,398", OrvynType.displayBalance, Modifier.width(150.dp))
            }
        }
        compose.onNodeWithContentDescription("Rp834,912,398").assertExists()
        compose.onNode(hasText("Rp834,​912,​398")).assertExists()
    }

    @Test fun `busy PIN pad keeps the dots full, ignores keys, says what's happening, and blocks screenshots (Q-B5, Q-C4)`() {
        var got = 0
        var busy by mutableStateOf(false)
        var shown by mutableStateOf(true)
        set { if (shown) PinScreen("Enter your PIN", "To pay Rp25,000.", {}, onComplete = { got++; busy = true }, errorCount = 0, errorText = null, busyText = if (busy) "Paying Rp25,000…" else null) }
        val flags = { compose.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE }
        assertTrue("FLAG_SECURE while the pad shows", flags() != 0)
        for (d in "482915") compose.onNodeWithContentDescription("$d").performClick()
        compose.mainClock.advanceTimeBy(200)
        assertEquals(1, got)
        compose.onNodeWithText("Paying Rp25,000…").assertExists()
        compose.onNodeWithContentDescription("6 of 6 digits entered").assertExists()
        compose.onNodeWithContentDescription("1").performClick() // ignored while busy
        compose.onNodeWithContentDescription("6 of 6 digits entered").assertExists()
        shown = false
        compose.waitForIdle()
        assertEquals("screenshots allowed again once the pad is gone", 0, flags())
    }

    @Test fun `typing six digits fast keeps every digit (Q-E4 AC5)`() {
        var pin = ""
        set { PinScreen("Create your PIN", null, null, onComplete = { pin = it }, errorCount = 0, errorText = null) }
        compose.mainClock.autoAdvance = false
        for (d in "204060") compose.onNodeWithContentDescription("$d").performClick()
        compose.mainClock.advanceTimeBy(300)
        assertEquals("204060", pin)
    }

    @Test fun `at large font sizes the PIN explanation is not drawn but TalkBack still reads it (Q-A1 AC4)`() {
        set {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                PinScreen("Create your PIN", "Avoid 123456 or your birthday.", null, onComplete = {}, errorCount = 0, errorText = null)
            }
        }
        compose.onNode(hasText("Avoid 123456 or your birthday.")).assertDoesNotExist()
        compose.onNode(hasContentDescription("Create your PIN. Avoid 123456 or your birthday.")).assertExists()
    }

    @Test fun `a double tap on a button fires once (Q-E4)`() {
        var taps = 0
        set { OrvynButton("Send Rp50,000", onClick = { taps++ }) }
        compose.onNodeWithText("Send Rp50,000").performClick()
        compose.onNodeWithText("Send Rp50,000").performClick()
        assertEquals(1, taps)
    }

    @Test fun `a new screen ignores taps during its 250ms entrance, so a double tap never lands on the PIN pad (F3)`() {
        Settings.Global.putFloat(RuntimeEnvironment.getApplication().contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        var taps = 0
        compose.mainClock.autoAdvance = false
        set { InputGuard { OrvynButton("1", onClick = { taps++ }) } }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("1").performClick()
        assertEquals(0, taps)
        compose.mainClock.advanceTimeBy(300)
        compose.onNodeWithText("1").performClick()
        assertEquals(1, taps)
    }

    @Test fun `with animations off a flow step changes at once (Q-E3 AC3)`() {
        Settings.Global.putFloat(RuntimeEnvironment.getApplication().contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        try {
            set {
                var step by androidx.compose.runtime.remember { mutableIntStateOf(0) }
                FlowSteps(step, index = { it }) { OrvynButton("Step $it", onClick = { step = it + 1 }) }
            }
            compose.waitForIdle()
            compose.mainClock.autoAdvance = false
            compose.onNodeWithText("Step 0").performClick()
            repeat(3) { compose.mainClock.advanceTimeByFrame() } // a few frames, far less than the 250ms slide
            compose.onNodeWithText("Step 1").assertExists()
            compose.onNodeWithText("Step 0").assertDoesNotExist()
        } finally {
            Settings.Global.putFloat(RuntimeEnvironment.getApplication().contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        }
    }

    @Test fun `a switch row is one switch that says on or off and toggles once per tap (Q-E5)`() {
        var on by mutableStateOf(false)
        var changes = 0
        set { SwitchRow("Freeze card", "Stops every payment until you unfreeze it", on, { on = it; changes++ }) }
        val row = compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.Role, androidx.compose.ui.semantics.Role.Switch))
        row.assertIsOff()
        row.performClick()
        row.assertIsOn()
        assertEquals(1, changes)
    }

    @Test fun `a chosen chip says it's selected and shows a check (Q-E1 AC3)`() {
        set { Column { Chip("Schedule", selected = true) {}; Chip("Send now", selected = false) {} } }
        compose.onNode(hasText("Schedule")).assertIsSelected()
    }

    @Test fun `the code shows on screen when notifications are off, and not when they're on (Q-D4)`() {
        var shown by mutableStateOf<String?>("123456")
        set { CodeStep("081234567890", shown, "Step 2 of 8", {}, {}) }
        compose.onNode(hasText("Enter the 6-digit code for 0812 3456 7890. Step 2 of 8.")).assertExists()
        compose.onNode(hasContentDescription("Notifications are off, so here's your code: 1 2 3 4 5 6")).assertExists()
        shown = null
        compose.onNode(hasContentDescription("Notifications are off", substring = true)).assertDoesNotExist()
    }

    @Test fun `Vyna announces the finished answer through one live region, and has the new disclaimer (Q-E6)`() {
        set { VynaScreen(DeviceStore(RuntimeEnvironment.getApplication()), onBack = {}, onAction = {}) }
        compose.onNode(hasTestTag("vyna-announce")).assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion))
        compose.onNodeWithText("Vyna can get things wrong. Check the details before you send money.").assertExists()
        compose.onNode(hasContentDescription("Read replies aloud")).assertIsOff()
    }
}
