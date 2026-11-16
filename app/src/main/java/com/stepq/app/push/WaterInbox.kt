package com.stepq.app.push

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.stepq.app.MainActivity
import com.stepq.app.R
import com.stepq.app.data.Supabase
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.util.concurrent.TimeUnit

@Serializable
data class InboxReminder(val id: Long, @SerialName("from_name") val fromName: String, @SerialName("sent_at") val sentAt: String)

/** The notification text for reminders that arrived since the last check, or null for none. */
fun inboxMessage(reminders: List<InboxReminder>): Pair<String, String>? {
    if (reminders.isEmpty()) return null
    val names = reminders.map { it.fromName }.distinct()
    val title = when (names.size) {
        1 -> "${names[0]} reminded you to drink water"
        2 -> "${names[0]} and ${names[1]} reminded you to drink water"
        else -> "${names[0]} and ${names.size - 1} friends reminded you to drink water"
    }
    return title to "Time for a glass. Stay fresh for your next walk."
}

/**
 * Water reminders from friends (A14). Until push is set up, the app checks for new ones about
 * every 15 minutes in the background (and when it opens) and shows them as a notification.
 */
object WaterInbox {
    private const val WORK = "water-inbox"
    private const val PREFS = "stepq"
    private const val SEEN = "water_inbox_seen"

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<WaterInboxWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        runCatching {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }

    suspend fun check(context: Context) {
        val client = Supabase.client ?: return
        client.auth.awaitInitialization()
        if (client.auth.currentSessionOrNull() == null) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val seen = prefs.getString(SEEN, null)
        if (seen == null) {
            // First run: start from now rather than replaying old reminders.
            prefs.edit().putString(SEEN, Instant.now().toString()).apply()
            return
        }
        val reminders = client.postgrest
            .rpc("water_inbox", buildJsonObject { put("since", seen) })
            .decodeList<InboxReminder>()
        val (title, body) = inboxMessage(reminders) ?: return
        prefs.edit().putString(SEEN, reminders.maxOf { it.sentAt }).apply()
        notify(context, title, body)
    }

    private fun notify(context: Context, title: String, body: String) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        val intent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, context.getString(R.string.notification_channel_default))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(intent)
            .build()
        @Suppress("MissingPermission") // checked by areNotificationsEnabled()
        manager.notify(WORK.hashCode(), notification)
    }
}

class WaterInboxWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = runCatching { WaterInbox.check(applicationContext) }
        .fold({ Result.success() }, { Result.retry() })
}
