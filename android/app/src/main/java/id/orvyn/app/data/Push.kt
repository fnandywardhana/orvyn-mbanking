package id.orvyn.app.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import id.orvyn.app.MainActivity
import id.orvyn.app.R
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** Story S2.0. The push token is the only thing ORVYN gives Firebase; messages carry the same text as the Inbox. */
object Push {
    /** Where a tapped notification wants to go ("tx:<id>" or the Inbox), opened once the customer is signed in. */
    var pendingLink by mutableStateOf<String?>(null)

    /** Called after every sign-in: tells the server which phone to push to. Silent if Firebase isn't configured. */
    suspend fun register(context: Context) {
        if (FirebaseApp.getApps(context).isEmpty()) return
        val token = suspendCancellableCoroutine<String?> { c ->
            FirebaseMessaging.getInstance().token.addOnCompleteListener { c.resume(if (it.isSuccessful) it.result else null) }
        } ?: return
        runCatching { Api.registerPush(token) }
    }

    fun show(context: Context, title: String, body: String, link: String?) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(CHANNEL, "Account activity", NotificationManager.IMPORTANCE_HIGH))
        val open = PendingIntent.getActivity(context, link.hashCode(),
            Intent(context, MainActivity::class.java).putExtra(EXTRA_LINK, link ?: "inbox").addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        fun base() = NotificationCompat.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFF0032A0.toInt()).setContentIntent(open).setAutoCancel(true)
        val notification = base().setContentTitle(title).setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            // S2.0 AC3: the lock screen only says something happened; amounts and names show once unlocked.
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(base().setContentTitle("ORVYN").setContentText("New activity on your account").build())
            .build()
        NotificationManagerCompat.from(context).notify((link ?: title).hashCode(), notification)
    }

    const val EXTRA_LINK = "orvyn.link"
    private const val CHANNEL = "activity"
}

class PushService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        val d = message.data
        Push.show(this, d["title"] ?: return, d["body"] ?: "", d["link"]?.takeIf { it.isNotEmpty() })
    }

    // A new token is sent at the next sign-in (Push.register); without a session there is nobody to send it as.
    override fun onNewToken(token: String) {}
}
