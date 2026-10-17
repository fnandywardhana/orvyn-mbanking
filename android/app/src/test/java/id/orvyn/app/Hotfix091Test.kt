package id.orvyn.app

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import id.orvyn.app.data.AppConfig
import id.orvyn.app.ui.requests.belowMinimum
import id.orvyn.app.ui.requests.quickAmountsFrom
import id.orvyn.app.ui.requests.sendSharesButton
import id.orvyn.app.ui.requests.tooSmallForFriends
import id.orvyn.app.ui.theme.OrvynTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}
