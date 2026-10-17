package id.orvyn.app

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import com.github.takahirom.roborazzi.captureRoboImage
import id.orvyn.app.data.DeviceStore
import id.orvyn.app.data.Paid
import id.orvyn.app.data.Recipient
import id.orvyn.app.data.parseTransaction
import id.orvyn.app.ui.Money
import id.orvyn.app.ui.QUICK_AMOUNTS
import id.orvyn.app.ui.auth.CreatePinScreen
import id.orvyn.app.ui.auth.SignInScreen
import id.orvyn.app.ui.components.AmountDisplay
import id.orvyn.app.ui.components.AmountKeypad
import id.orvyn.app.ui.components.AmountScreen
import id.orvyn.app.ui.components.OrvynButton
import id.orvyn.app.ui.components.OrvynTextButton
import id.orvyn.app.ui.components.PinScreen
import id.orvyn.app.ui.components.ReceiptLine
import id.orvyn.app.ui.components.SuccessScreen
import id.orvyn.app.ui.components.WhiteTextButton
import id.orvyn.app.ui.home.AccountScreen
import id.orvyn.app.ui.home.HomeScreen
import id.orvyn.app.ui.home.TransactionRow
import id.orvyn.app.ui.theme.OrvynTheme
import id.orvyn.app.ui.transfer.RecipientHeader
import id.orvyn.app.ui.wealth.OpenDepositFlow
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.ParameterizedRobolectricTestRunner.Parameters
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant

/**
 * Definition of done item 3 (stories.md), made permanent from QA's temporary test: font size 2.0 on a 360×640dp and a
 * 411×869dp phone, light and dark. Every screen keeps its keypad keys and main buttons fully on screen. Renders go to
 * build/font-scale/ for review.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class FontScaleTest(private val night: Boolean, private val qualifiers: String) {
    companion object {
        @JvmStatic
        @Parameters(name = "dark={0} {1}")
        fun variants() = listOf(false, true).flatMap { n -> listOf("w360dp-h640dp-xxhdpi", "w411dp-h869dp-420dpi").map { q -> arrayOf<Any>(n, q) } }

        /** A Prestige customer with a nine-digit balance, a 24-letter pocket and long names (QA's stress data, made up). */
        fun home(tier: String) = """{"customer": {"name": "Raka Pratama", "firstName": "Raka", "tier": "$tier", "phone": "081234567890"}, "unread": 3,
            "totalBalance": 834912398, "accounts": [{"id": "a1", "kind": "savings", "name": "ORVYN Everyday", "number": "4073979004", "balance": 696518116, "openedOn": "2024-03-03T02:00:00Z"}],
            "pockets": [{"id": "p1", "name": "Holiday in Labuan Bajo26", "balance": 107000, "image": "travel", "target": 5000000, "targetDate": null, "rule": null, "reached": false}],
            "relationshipManager": null,
            "recent": [${(1..5).joinToString(",") { """{"id": "t$it", "at": "2026-10-16T0$it:00:00Z", "amount": -150025000, "title": "PT Griya Properti Utama Sejahtera", "subtitle": "RTGS · Mandiri", "kind": "external_transfer", "status": "success", "fee": 25000}""" }}]}"""
    }

    @get:Rule val compose = createComposeRule()
    private val tag get() = "${qualifiers.substringBefore('-')}-${if (night) "dark" else "light"}"

    private fun render(name: String, content: @Composable () -> Unit) {
        // Never the dev server on this machine: a running one would answer 401 and change what screens show (Q-01-F1).
        id.orvyn.app.data.Api.url = "http://127.0.0.1:1/api"
        RuntimeEnvironment.setQualifiers(qualifiers); RuntimeEnvironment.setQualifiers(if (night) "+night" else "+notnight")
        compose.setContent {
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density, 2f)) { OrvynTheme(dark = night) { content() } }
        }
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        compose.onNode(isRoot()).captureRoboImage("build/font-scale/$name-$tag.png")
    }

    /** Fails when a node is missing or not fully inside the screen. */
    private fun inside(name: String, vararg checks: Pair<String, SemanticsMatcher>) {
        val root = compose.onNode(isRoot()).fetchSemanticsNode().boundsInRoot
        for ((label, m) in checks) {
            val nodes = compose.onAllNodes(m, useUnmergedTree = true).fetchSemanticsNodes()
            assertTrue("$name $tag: $label is not on screen", nodes.isNotEmpty())
            val b = nodes.first().boundsInRoot
            assertTrue("$name $tag: $label is cut ($b in $root)", b.top >= -0.5f && b.bottom <= root.bottom + 0.5f && b.left >= -0.5f && b.right <= root.right + 0.5f && b.height > 0f)
        }
    }

    private val keys = arrayOf("key 1" to hasContentDescription("1"), "key 0" to hasContentDescription("0"), "Delete" to hasContentDescription("Delete"))
    private fun store(tier: String = "everyday") = DeviceStore(RuntimeEnvironment.getApplication()).apply { lastHome = System.currentTimeMillis() to home(tier) }

    @Test fun unlock() {
        render("unlock") {
            PinScreen("Welcome back, Raka", "Enter your 6-digit PIN.", null, onComplete = {}, errorCount = 0, errorText = "Wrong PIN. 2 tries left.", onBiometric = {}, footer = {
                OrvynTextButton("Forgot PIN?", onClick = {})
                OrvynTextButton("Not you? Sign in with a password", onClick = {})
                Text("Demo · ORVYN Bank is a fictional bank")
            })
        }
        inside("unlock", *keys, "error" to hasText("Wrong PIN. 2 tries left."), "Fingerprint" to hasContentDescription("Use fingerprint"))
        // At large sizes the footer scrolls with the top part; it must exist, and the keypad never moves for it.
        assertTrue(compose.onAllNodes(hasText("Forgot PIN?"), true).fetchSemanticsNodes().isNotEmpty())
    }

    @Test fun createPin() {
        render("create-pin") { CreatePinScreen(onDone = {}) }
        inside("create-pin", *keys)
    }

    @Test fun transactionPin() {
        render("pin-transfer") { PinScreen("Enter your PIN", "To send Rp150,000,000 to PT Griya Properti Utama Sejahtera.", {}, onComplete = {}, errorCount = 0, errorText = null, busyText = "Sending Rp150,000,000…") }
        inside("pin-transfer", *keys, "busy" to hasText("Sending Rp150,000,000…"))
    }

    @Test fun transferAmount() {
        render("transfer-amount") {
            AmountScreen("How much?", null, {}, keypad = { AmountKeypad(150_000_000, {}) }, button = { OrvynButton("Send Rp150,000,000", onClick = {}) }) {
                RecipientHeader(Recipient("MANDIRI", "Mandiri", "5626290332", "PT Griya Properti Utama Sejahtera"))
                AmountDisplay(150_000_000, 696_518_116, null, onQuickPick = {}, quickAmounts = QUICK_AMOUNTS)
            }
        }
        inside("transfer-amount", *keys, "000" to hasContentDescription("Three zeros"), "button" to hasText("Send Rp150,000,000"))
    }

    @Test fun depositAmount() {
        render("deposit-amount") { OpenDepositFlow(onExit = {}) }
        inside("deposit-amount", *keys, "button" to hasText("Enter an amount"))
    }

    @Test fun transferSuccess() {
        render("success-transfer") {
            SuccessScreen("Sent", "Rp150,000,000", "to PT Griya Properti Utama Sejahtera", date = Instant.parse("2026-10-16T12:27:00Z"), details = {
                ReceiptLine("To", "Mandiri · 5626 2903 32"); ReceiptLine("Method", "RTGS · fee Rp25,000"); ReceiptLine("Note", "Down payment"); ReceiptLine("Reference", "CB2CC8CA1F52")
            }, onPrimary = {}, onShare = {}, textAction = { WhiteTextButton("Save as a recipient") {} })
        }
        inside("success-transfer", "Done" to hasText("Done"), "Share" to hasText("Share receipt"), "Save" to hasText("Save as a recipient"))
    }

    @Test fun scheduledSuccess() {
        render("success-scheduled") {
            SuccessScreen("Scheduled", "Rp150,000,000", "to PT Griya Properti Utama Sejahtera", date = Instant.parse("2026-10-16T12:27:00Z"), details = {
                ReceiptLine("To", "Mandiri · 5626 2903 32"); ReceiptLine("Method", "RTGS · fee Rp25,000"); ReceiptLine("Note", "Down payment")
                ReceiptLine("First send", "18 Oct 2026, 07:00 WIB"); ReceiptLine("Repeats", "Every month on the 18th")
            }, onPrimary = {})
        }
        inside("success-scheduled", "Done" to hasText("Done"))
    }

    @Test fun homeEveryday() {
        val s = store("everyday")
        render("home-everyday") { HomeScreen(s, onSeeAll = {}, onOpenTransaction = {}, onQuickAction = {}, onCopied = {}) }
        inside("home-everyday", "Transfer" to hasContentDescription("Transfer", substring = true), "Request" to hasContentDescription("Request", substring = true),
            "balance" to hasContentDescription("Rp834,912,398"))
    }

    @Test fun homePrestige() {
        val s = store("prestige")
        render("home-prestige") { HomeScreen(s, onSeeAll = {}, onOpenTransaction = {}, onQuickAction = {}, onCopied = {}) }
        inside("home-prestige", "balance" to hasContentDescription("Rp834,912,398"), "badge" to hasText("PRESTIGE"))
    }

    @Test fun signIn() {
        render("sign-in") { SignInScreen(DeviceStore(RuntimeEnvironment.getApplication()), onBack = {}, onSignedIn = {}) }
        inside("sign-in", "Phone label" to hasText("Phone number"))
        // Below the fold at 2.0 on 360dp: reachable by scrolling (Q-D1 AC4).
        compose.onNode(hasText("Sign in with a passkey")).performScrollTo()
        inside("sign-in", "Password label" to hasText("Password"), "passkey button, under Sign in" to hasText("Sign in with a passkey"))
    }

    @Test fun history() {
        val rows = JSONObject(home("everyday")).getJSONArray("recent").let { a -> List(a.length()) { parseTransaction(a.getJSONObject(it)) } }
        render("history-rows") { Column { rows.take(3).forEach { TransactionRow(it, withDay = false) {} } } }
        inside("history-rows", "amount" to hasContentDescription("minus Rp150,025,000"))
    }

    @Test fun accountDetail() {
        val s = store("everyday")
        render("account") { AccountScreen(s, onBack = {}, onOpenTransaction = {}) }
        // The offline banner may or may not be up yet (the load runs in the background), and at 360dp it pushes the buttons
        // below the fold. The details scroll as one list (design.md 10.1), so each button is checked after scrolling to it.
        compose.onNode(hasText("Share account details")).performScrollTo()
        inside("account", "Share" to hasText("Share account details"))
        compose.onNode(hasText("Copy number")).performScrollTo()
        inside("account", "Copy" to hasText("Copy number"))
    }
}

