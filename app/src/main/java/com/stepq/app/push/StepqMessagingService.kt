package com.stepq.app.push

import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.stepq.app.MainActivity
import com.stepq.app.R

/**
 * Receives FCM pushes: water reminders from friends (A14), QRun reminders (A21) and challenge
 * updates. The device token is stored against the signed-in user once the `app.push_tokens`
 * table exists (see docs/PLAN.md); until then it is only received.
 */
class StepqMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        // TODO(push): upsert into app.push_tokens for the signed-in user.
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.notification?.title ?: message.data["title"] ?: return
        val body = message.notification?.body ?: message.data["body"].orEmpty()
        val intent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, getString(R.string.notification_channel_default))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(intent)
            .build()
        val manager = NotificationManagerCompat.from(this)
        if (manager.areNotificationsEnabled()) {
            @Suppress("MissingPermission") // checked by areNotificationsEnabled()
            manager.notify(message.messageId.hashCode(), notification)
        }
    }
}
