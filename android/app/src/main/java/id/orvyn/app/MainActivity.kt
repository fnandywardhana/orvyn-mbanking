package id.orvyn.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import id.orvyn.app.ui.theme.OrvynTheme
import id.orvyn.app.ui.welcome.WelcomeScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OrvynTheme {
                WelcomeScreen(onSignIn = {}, onRegister = {}) // wired up in Fase 1 (S1.1, S1.5)
            }
        }
    }
}
