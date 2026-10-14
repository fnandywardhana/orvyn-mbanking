package id.orvyn.app

import android.os.Bundle
import android.os.SystemClock
import android.view.MotionEvent
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import id.orvyn.app.data.Api
import id.orvyn.app.data.DeviceStore
import id.orvyn.app.ui.OrvynApp
import id.orvyn.app.ui.SignOutSignal
import id.orvyn.app.ui.theme.OrvynTheme

// FragmentActivity because androidx.biometric's prompt needs one.
class MainActivity : FragmentActivity() {
    private var lastTouch = SystemClock.elapsedRealtime()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Api.onSignedOut = { runOnUiThread { SignOutSignal.count++ } }
        val store = DeviceStore(this)
        setContent { OrvynTheme { OrvynApp(store) } }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        lastTouch = SystemClock.elapsedRealtime()
        return super.dispatchTouchEvent(ev)
    }

    // Story S1.6: sign out after 5 idle minutes, including time spent in the background.
    override fun onResume() {
        super.onResume()
        if (Api.session != null && SystemClock.elapsedRealtime() - lastTouch > IDLE_MS) {
            Api.session = null
            SignOutSignal.count++
        }
        window.decorView.postDelayed(idleCheck, IDLE_CHECK_MS)
    }

    override fun onPause() {
        super.onPause()
        window.decorView.removeCallbacks(idleCheck)
    }

    private val idleCheck: Runnable = object : Runnable {
        override fun run() {
            if (Api.session != null && SystemClock.elapsedRealtime() - lastTouch > IDLE_MS) {
                Api.session = null
                SignOutSignal.count++
            }
            window.decorView.postDelayed(this, IDLE_CHECK_MS)
        }
    }

    private companion object {
        const val IDLE_MS = 5 * 60_000L
        const val IDLE_CHECK_MS = 15_000L
    }
}
