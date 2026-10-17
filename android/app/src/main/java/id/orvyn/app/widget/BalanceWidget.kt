package id.orvyn.app.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.widget.RemoteViews
import id.orvyn.app.MainActivity
import id.orvyn.app.R
import id.orvyn.app.data.DeviceStore
import id.orvyn.app.data.Push
import id.orvyn.app.ui.Money
import org.json.JSONObject

/**
 * Story S2.18: home screen widget. The balance stays hidden; "Balance" shows the last one ORVYN saw for 10 seconds.
 * It never fetches anything itself, and after a Profile sign-out there is nothing to show.
 */
class BalanceWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = render(context, reveal = false)

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            SHOW -> {
                render(context, reveal = true)
                // Inexact is fine: hiding a second late is harmless, and exact alarms need a permission ORVYN doesn't ask for.
                context.getSystemService(AlarmManager::class.java).set(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + 10_000, broadcast(context, HIDE))
            }
            HIDE -> render(context, reveal = false)
            else -> super.onReceive(context, intent)
        }
    }

    companion object {
        private const val SHOW = "id.orvyn.app.widget.SHOW"
        private const val HIDE = "id.orvyn.app.widget.HIDE"

        private fun broadcast(context: Context, action: String) = PendingIntent.getBroadcast(context, action.hashCode(),
            Intent(context, BalanceWidget::class.java).setAction(action), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        /** Called when the home screen data changes or the customer signs out, so the widget never shows stale state. */
        fun refresh(context: Context) = render(context, reveal = false)

        private fun render(context: Context, reveal: Boolean) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, BalanceWidget::class.java))
            if (ids.isEmpty()) return
            val total = DeviceStore(context).lastHome?.second?.let { runCatching { JSONObject(it).getLong("totalBalance") }.getOrNull() }
            val views = RemoteViews(context.packageName, R.layout.widget_orvyn).apply {
                when {
                    total == null -> { setTextViewText(R.id.widget_balance, "Rp••••••"); setTextViewText(R.id.widget_hint, "Open ORVYN to see your balance") }
                    reveal -> {
                        // Q-G11: when that balance was saved, not "last seen".
                        val at = DeviceStore(context).lastHome?.first?.let { id.orvyn.app.ui.formatTime(java.time.Instant.ofEpochMilli(it)) } ?: ""
                        setTextViewText(R.id.widget_balance, Money.format(total)); setTextViewText(R.id.widget_hint, "As of $at · hides in 10 seconds")
                    }
                    else -> { setTextViewText(R.id.widget_balance, "Rp••••••"); setTextViewText(R.id.widget_hint, "Balance hidden") }
                }
                setOnClickPendingIntent(R.id.widget_show, if (total == null) openApp(context, null) else broadcast(context, SHOW))
                setOnClickPendingIntent(R.id.widget_qris, openApp(context, "qris"))
            }
            manager.updateAppWidget(ids, views)
        }

        /** Opens ORVYN; with a link, the PIN screen comes first and then that screen (like a notification tap). */
        private fun openApp(context: Context, link: String?) = PendingIntent.getActivity(context, (link ?: "open").hashCode(),
            Intent(context, MainActivity::class.java).apply { link?.let { putExtra(Push.EXTRA_LINK, it) }; addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP) },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }
}
