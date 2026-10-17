package id.orvyn.app

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import id.orvyn.app.ui.BottomBar
import id.orvyn.app.ui.Tab
import id.orvyn.app.ui.components.LocalSnacks
import id.orvyn.app.ui.components.OrvynSnackbarHost
import id.orvyn.app.ui.components.Snacks
import id.orvyn.app.ui.components.rememberSnacks
import id.orvyn.app.ui.components.OrvynTextField
import id.orvyn.app.ui.components.closeKeyboard
import id.orvyn.app.ui.components.doneAction
import id.orvyn.app.data.Api
import id.orvyn.app.ui.components.spokenCard
import id.orvyn.app.ui.insights.CategoryScreen
import java.net.ServerSocket
import id.orvyn.app.data.AppConfig
import id.orvyn.app.ui.requests.belowMinimum
import id.orvyn.app.ui.requests.quickAmountsFrom
import id.orvyn.app.ui.requests.sendSharesButton
import id.orvyn.app.ui.requests.tooSmallForFriends
import id.orvyn.app.ui.theme.OrvynTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Hotfix 0.9.1 (docs/features/2026-10-17-hotfix-091): the app side of H91-1, H91-2, H91-4 and H91-5. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h640dp-xxhdpi")
class Hotfix091Test {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun set(content: @androidx.compose.runtime.Composable () -> Unit) = compose.setContent { OrvynTheme(dark = false) { content() } }

    // ---- H91-1: one minimum (design.md 2.2 to 2.4)

    @Test fun `H91-1 AC1-2 amount buttons say the minimum, and no quick amount sits under it`() {
        assertEquals(10_000L, AppConfig.minTransfer) // until /config says otherwise
        assertEquals("Enter an amount", belowMinimum(0, 10_000))
        assertEquals("Minimum Rp10,000", belowMinimum(1, 10_000))
        assertEquals("Minimum Rp10,000", belowMinimum(9_999, 10_000))
        assertNull(belowMinimum(10_000, 10_000))
        assertEquals(listOf(50_000L, 100_000L, 250_000L, 500_000L), quickAmountsFrom(10_000))
        assertEquals(listOf(250_000L, 500_000L), quickAmountsFrom(200_000))
    }

    @Test fun `H91-1 AC3 a share under the minimum turns Send off until it's changed`() {
        assertEquals("Minimum Rp10,000 a share" to false, sendSharesButton(listOf(7_500), 15_000, 10_000))
        assertEquals("Send 1 request" to true, sendSharesButton(listOf(10_000), 15_000, 10_000))
        assertEquals("Send 2 requests" to false, sendSharesButton(listOf(10_000, 10_000), 15_000, 10_000)) // over the bill (0.9.0)
        assertEquals("Send 2 requests" to true, sendSharesButton(listOf(10_000, 10_000), 30_000, 10_000))
    }

    @Test fun `H91-1 AC4 a bill too small for everyone says so`() {
        assertEquals("This bill is too small for 2 friends at Rp10,000 each. Go back and pick fewer friends.", tooSmallForFriends(15_000, 2, 10_000))
        assertNull(tooSmallForFriends(15_000, 1, 10_000))
        assertNull(tooSmallForFriends(20_000, 2, 10_000))
    }

    // ---- H91-2: category detail with savings and cards (design.md 3)

    private var server: ServerSocket? = null
    private val realUrl = Api.url
    @After fun restore() { server?.close(); Api.url = realUrl }

    /** Answers every request with [json], like the server's category endpoint. Plain sockets: no test libraries. */
    private fun serve(json: String) {
        val socket = ServerSocket(0).also { server = it }
        Thread {
            while (!socket.isClosed) runCatching {
                socket.accept().use { c ->
                    val input = c.getInputStream().bufferedReader()
                    while (input.readLine()?.isNotEmpty() == true) Unit // request line and headers
                    val body = json.toByteArray()
                    c.getOutputStream().apply {
                        write("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray()); write(body); flush()
                    }
                }
            }
        }.apply { isDaemon = true }.start()
        Api.url = "http://127.0.0.1:${socket.localPort}/api"
    }

    @Test fun `H91-2 AC2 a masked card number is read as card ending`() {
        assertEquals("ORVYN Voyage card ending 4821 · Thu, 15 Oct, 20:12", spokenCard("ORVYN Voyage ·••• 4821 · Thu, 15 Oct, 20:12"))
        assertEquals("Rp850,000 at Tokopedia with your ORVYN Voyage card ending 4821.", spokenCard("Rp850,000 at Tokopedia with your ORVYN Voyage ·••• 4821."))
        assertEquals("QRIS · Sun, 11 Oct, 11:40", spokenCard("QRIS · Sun, 11 Oct, 11:40"))
    }

    @Test fun `H91-2 AC1-3 the detail lists card and savings rows, and a card row opens on the card side`() {
        serve("""{"items":[
          {"id":"j1","at":"2026-10-15T13:12:00Z","amount":-850000,"title":"Tokopedia","subtitle":"ORVYN Voyage ·••• 4821","kind":"card_spend","status":"success","accountId":"card-acc"},
          {"id":"j2","at":"2026-10-11T04:40:00Z","amount":-240000,"title":"Minimarket Sejahtera","subtitle":"QRIS","kind":"qris_payment","status":"success","accountId":"sav-acc"}]}""")
        var opened: Pair<String, String?>? = null
        set { CategoryScreen("shopping", onBack = {}, onOpen = { id, account -> opened = id to account }) }
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("Tokopedia")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("This month, from ORVYN Everyday and your cards.").assertExists()
        compose.onNodeWithText("Minimarket Sejahtera").assertExists()
        assertTrue(compose.onAllNodes(hasContentDescription("card ending 4821", substring = true)).fetchSemanticsNodes().isNotEmpty())
        compose.onNodeWithText("Tokopedia").performClick()
        assertEquals("j1" to "card-acc", opened)
    }

    @Test fun `H91-2 AC5 offline shows the error state with Try again, not a cached list`() {
        Api.url = "http://127.0.0.1:1/api"
        set { CategoryScreen("shopping", onBack = {}, onOpen = { _, _ -> }) }
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("Try again")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Couldn't load these").assertExists()
        compose.onNodeWithText("You're offline. Check your connection and try again.").assertExists()
    }

    // ---- H91-4: Done runs the primary action only when it's on; Search only closes the keyboard (MASTER Text field)

    @Test fun `H91-4 Done runs the button when it is enabled, and Search never runs anything`() {
        var ran = 0
        var number by mutableStateOf("12345")
        set {
            Column {
                OrvynTextField("Account number", number, { number = it }, imeAction = ImeAction.Done, onImeAction = doneAction(number.length >= 10) { ran++ })
                OrvynTextField("Find a bank", "", {}, imeAction = ImeAction.Search, onImeAction = closeKeyboard())
            }
        }
        val fields = compose.onAllNodes(hasSetTextAction())
        fields[0].performImeAction()
        compose.runOnIdle { assertEquals("too short: Done only closes the keyboard", 0, ran) }
        number = "1234567890"
        compose.waitForIdle()
        fields[0].performImeAction()
        compose.runOnIdle { assertEquals("valid: Done is the same as tapping Check account", 1, ran) }
        fields[1].performImeAction()
        compose.runOnIdle { assertEquals(1, ran) }
    }

    // ---- H91-5: the snackbar sits 12dp above the raised QRIS button (design.md 5)

    private fun snackClearOfQris(fontScale: Float) {
        var snacks: Snacks? = null
        var px = 0f
        set {
            val d = LocalDensity.current
            px = d.density
            CompositionLocalProvider(LocalDensity provides Density(d.density, fontScale)) {
                val s = rememberSnacks().also { snacks = it }
                CompositionLocalProvider(LocalSnacks provides s) {
                    Box(Modifier.fillMaxSize()) {
                        Box(Modifier.align(Alignment.BottomCenter)) { BottomBar(Tab.Home) {} }
                        OrvynSnackbarHost(s)
                    }
                }
            }
        }
        compose.runOnIdle { snacks!!.show("You're offline. Try again when you're connected.", error = true) }
        compose.waitForIdle()
        val snack = compose.onNodeWithTag("snackbar", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val qrisTop = compose.onNodeWithTag("qris", useUnmergedTree = true).fetchSemanticsNode().positionInRoot.y
        assertEquals("12dp between the snackbar and the top of QRIS at font ${fontScale}x", 12f, (qrisTop - snack.bottom) / px, 1f)
    }

    @Test fun `H91-5 AC1 the snackbar clears the raised QRIS button`() = snackClearOfQris(1f)
    @Test fun `H91-5 AC3 the snackbar clears the raised QRIS button at 200 percent`() = snackClearOfQris(2f)
}
