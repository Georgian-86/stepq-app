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

@Serializable
data class InboxShare(
    val id: Long,
    @SerialName("from_name") val fromName: String,
    val steps: Int,
    @SerialName("shared_at") val sharedAt: String,
)

/** "Arjun shared 9,120 steps with you", for shares that arrived since the last check. */
/** One row of app.friend_requests(), for the notification. */
@kotlinx.serialization.Serializable
data class InboxRequest(
    val id: String,
    @kotlinx.serialization.SerialName("display_name") val displayName: String,
    @kotlinx.serialization.SerialName("created_at") val createdAt: String,
)

/** "Meera wants to be your friend", or a count when several arrive at once. */
fun requestMessage(requests: List<InboxRequest>): Pair<String, String>? {
    if (requests.isEmpty()) return null
    val first = requests.maxBy { it.createdAt }.displayName.substringBefore(' ')
    val title = if (requests.size == 1) "$first wants to be your friend" else "$first and ${requests.size - 1} more want to be your friends"
    return title to "Open Friends to accept."
}

fun shareMessage(shares: List<InboxShare>): Pair<String, String>? {
    if (shares.isEmpty()) return null
    val latest = shares.maxBy { it.sharedAt }
    val first = latest.fromName.substringBefore(' ')
    val others = shares.map { it.fromName }.distinct().size - 1
    val title = if (others == 0) "$first shared ${com.stepq.app.ui.onboarding.formatSteps(latest.steps)} steps with you"
    else "$first and ${if (others == 1) "1 friend" else "$others friends"} shared their steps with you"
    return title to "Share yours back to keep your streak with them going."
}

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
    private const val SHARES_SEEN = "share_inbox_seen"
    private const val REQUESTS_SEEN = "friend_requests_seen"

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
        checkShares(context, client, prefs)
        checkFriendRequests(context, client, prefs)
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

    /** Friends sharing their steps (the streak). Needs the Nov 20 migration; skipped without it. */
    private suspend fun checkShares(context: Context, client: io.github.jan.supabase.SupabaseClient, prefs: android.content.SharedPreferences) {
        val seen = prefs.getString(SHARES_SEEN, null)
        if (seen == null) {
            prefs.edit().putString(SHARES_SEEN, Instant.now().toString()).apply()
            return
        }
        val shares = runCatching {
            client.postgrest.rpc("step_share_inbox", buildJsonObject { put("since", seen) }).decodeList<InboxShare>()
        }.getOrNull() ?: return
        val (title, body) = shareMessage(shares) ?: return
        prefs.edit().putString(SHARES_SEEN, shares.maxOf { it.sharedAt }).apply()
        notify(context, title, body, id = SHARES_SEEN.hashCode())
    }

    /** New friend requests waiting for me. Needs migration 20261112000000; skipped without it. */
    private suspend fun checkFriendRequests(context: Context, client: io.github.jan.supabase.SupabaseClient, prefs: android.content.SharedPreferences) {
        val seen = prefs.getString(REQUESTS_SEEN, null)
        val requests = runCatching { client.postgrest.rpc("friend_requests").decodeList<InboxRequest>() }.getOrNull() ?: return
        if (seen == null) {
            // First run: only tell them about requests that arrive from now on.
            prefs.edit().putString(REQUESTS_SEEN, Instant.now().toString()).apply()
            return
        }
        val since = runCatching { Instant.parse(seen) }.getOrDefault(Instant.EPOCH)
        val fresh = requests.filter { com.stepq.app.data.parseJavaInstant(it.createdAt).isAfter(since) }
        val (title, body) = requestMessage(fresh) ?: return
        prefs.edit().putString(REQUESTS_SEEN, fresh.maxOf { com.stepq.app.data.parseJavaInstant(it.createdAt) }.toString()).apply()
        notify(context, title, body, id = REQUESTS_SEEN.hashCode())
    }

    private fun notify(context: Context, title: String, body: String, id: Int = WORK.hashCode()) {
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
        manager.notify(id, notification)
    }
}

class WaterInboxWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = runCatching { WaterInbox.check(applicationContext) }
        .fold({ Result.success() }, { Result.retry() })
}
