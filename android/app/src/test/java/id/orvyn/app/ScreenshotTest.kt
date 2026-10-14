package id.orvyn.app

import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.captureRoboImage
import id.orvyn.app.ui.theme.OrvynTheme
import id.orvyn.app.ui.welcome.WelcomeScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.ParameterizedRobolectricTestRunner.Parameters
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Story S0.5: every screen rendered in light and dark at the Galaxy Note10+ size (411×869dp, 420dpi). English only. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h869dp-420dpi")
class ScreenshotTest(private val night: Boolean) {
    companion object {
        @JvmStatic
        @Parameters(name = "dark={0}")
        fun variants() = listOf(arrayOf<Any>(false), arrayOf<Any>(true))
    }

    @get:Rule val compose = createComposeRule()

    private fun capture(name: String, content: @androidx.compose.runtime.Composable () -> Unit) {
        RuntimeEnvironment.setQualifiers(if (night) "+night" else "+notnight")
        compose.setContent { OrvynTheme(dark = night) { content() } }
        compose.onRoot().captureRoboImage("screenshots/$name-${if (night) "dark" else "light"}.png")
    }

    @Test fun welcome() = capture("welcome") { WelcomeScreen(onSignIn = {}, onRegister = {}) }
}

private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onRoot() =
    onNode(androidx.compose.ui.test.isRoot())
