package id.orvyn.app

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import id.orvyn.app.data.DeviceStore
import id.orvyn.app.widget.BalanceWidget
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.ParameterizedRobolectricTestRunner.Parameters
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Story S2.18: hidden by default, shown on tap, nothing after sign-out; light and dark. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h869dp-420dpi")
class WidgetTest(private val night: Boolean) {
    companion object {
        @JvmStatic @Parameters(name = "dark={0}")
        fun variants() = listOf(arrayOf<Any>(false), arrayOf<Any>(true))
    }

    @Test fun hiddenThenShownThenGoneAfterSignOut() {
        RuntimeEnvironment.setQualifiers(if (night) "+night" else "+notnight")
        val context = RuntimeEnvironment.getApplication()
        val manager = shadowOf(AppWidgetManager.getInstance(context))
        val id = manager.createWidget(BalanceWidget::class.java, R.layout.widget_orvyn)
        val store = DeviceStore(context)
        store.lastHome = 0L to """{"totalBalance": 14030166}"""
        BalanceWidget.refresh(context)
        fun view(): View = manager.getViewFor(id)
        fun text(res: Int) = (view().findViewById<TextView>(res)).text.toString()

        assertEquals("Rp••••••", text(R.id.widget_balance))
        assertEquals("Balance hidden", text(R.id.widget_hint))
        assertEquals("Show balance", text(R.id.widget_show))
        BalanceWidget().onReceive(context, Intent(context, BalanceWidget::class.java).setAction("id.orvyn.app.widget.SHOW"))
        assertEquals("Rp14,030,166", text(R.id.widget_balance))
        assertEquals("As of 07:00 · hides in 10 seconds", text(R.id.widget_hint)) // saved at epoch 0 = 07:00 WIB
        snapshot(view(), "widget-shown")

        BalanceWidget().onReceive(context, Intent(context, BalanceWidget::class.java).setAction("id.orvyn.app.widget.HIDE"))
        assertEquals("Rp••••••", text(R.id.widget_balance))
        snapshot(view(), "widget-hidden")

        store.forgetDevice()
        BalanceWidget.refresh(context)
        assertEquals("Open ORVYN to see your balance", text(R.id.widget_hint))
    }

    private fun snapshot(v: View, name: String) {
        val frame = FrameLayout(RuntimeEnvironment.getApplication()).apply { setPadding(24, 24, 24, 24); setBackgroundColor(if (night) 0xFF000000.toInt() else 0xFFE8EAF0.toInt()) }
        (v.parent as? android.view.ViewGroup)?.removeView(v)
        frame.addView(v, FrameLayout.LayoutParams(900, 400))
        frame.measure(View.MeasureSpec.makeMeasureSpec(948, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(448, View.MeasureSpec.EXACTLY))
        frame.layout(0, 0, 948, 448)
        val bmp = android.graphics.Bitmap.createBitmap(948, 448, android.graphics.Bitmap.Config.ARGB_8888)
        frame.draw(android.graphics.Canvas(bmp))
        java.io.File("screenshots/$name-${if (night) "dark" else "light"}.png").outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }
}
