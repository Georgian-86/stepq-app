package com.stepq.app.push

import android.app.PendingIntent
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.stepq.app.MainActivity
import com.stepq.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.URL

/**
 * Receives FCM pushes from stepq.in: the admin's notifications and the automatic nudges
 * (morning, water, evening, streak, goal, comeback). They arrive as data messages, so this
 * builds every notification itself: its channel, the full text when expanded, an optional
 * picture, and the tab a tap opens.
 */
class StepqMessagingService : FirebaseMessagingService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        scope.launch { PushTokens.register(token) }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val push = PushMessage.from(message.data, message.notification?.title, message.notification?.body) ?: return
        // onMessageReceived runs on a background thread with ~20 s to finish: room to fetch a picture.
        val picture = push.image?.let(::download)
        val intent = PendingIntent.getActivity(
            this,
            push.route.hashCode(),
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(PushRoute.EXTRA, push.route),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = NotificationCompat.Builder(this, push.channel)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setColor(getColor(R.color.notification_accent))
            .setContentTitle(push.title)
            .setContentText(push.body)
            .setAutoCancel(true)
            .setContentIntent(intent)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
        if (picture != null) {
            builder.setLargeIcon(picture)
                .setStyle(NotificationCompat.BigPictureStyle().bigPicture(picture).bigLargeIcon(null as Bitmap?))
        } else {
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(push.body))
        }
        val manager = NotificationManagerCompat.from(this)
        if (manager.areNotificationsEnabled()) {
            // One slot per kind: a new evening nudge replaces an unread one instead of piling up.
            @Suppress("MissingPermission") // checked by areNotificationsEnabled()
            manager.notify(if (push.kind == "campaign") message.messageId.hashCode() else push.kind.hashCode(), builder.build())
        }
    }

    private fun download(url: String): Bitmap? = runCatching {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 5_000
        c.readTimeout = 5_000
        c.inputStream.use { BitmapFactory.decodeStream(it) }
    }.getOrNull()
}
