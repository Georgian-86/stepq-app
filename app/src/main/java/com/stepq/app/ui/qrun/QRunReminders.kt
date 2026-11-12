package com.stepq.app.ui.qrun

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.stepq.app.MainActivity
import com.stepq.app.R
import com.stepq.app.data.QRun
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/**
 * A21: a local reminder an hour before each QRun I'm going to or hosting. Kept in step with the
 * feed every time the QRun list loads, which also brings reminders back after a reboot.
 */
object QRunReminders {
    private const val PREFS = "qrun_reminders"
    private const val KEY = "scheduled"

    fun sync(context: Context, runs: List<QRun>, myId: String, now: Instant = Instant.now()) {
        val want = runs.filter { (it.joined || it.createdBy == myId) && it.startsAt.minus(Duration.ofHours(1)).isAfter(now) }
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val had = prefs.getStringSet(KEY, emptySet()).orEmpty()
        (had - want.map { it.id }.toSet()).forEach { cancel(context, it) }
        want.forEach { schedule(context, it) }
        prefs.edit().putStringSet(KEY, want.map { it.id }.toSet()).apply()
    }

    fun schedule(context: Context, run: QRun) {
        val at = run.startsAt.minus(Duration.ofHours(1))
        if (!at.isAfter(Instant.now())) return
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        // Inexact within 10 minutes, so no exact-alarm permission is needed.
        am.setWindow(AlarmManager.RTC_WAKEUP, at.toEpochMilli() - 5 * 60_000, 10 * 60_000, pending(context, run.id, run))
    }

    fun cancel(context: Context, id: String) {
        context.getSystemService(AlarmManager::class.java)?.cancel(pending(context, id, null))
    }

    private fun pending(context: Context, id: String, run: QRun?): PendingIntent {
        val intent = Intent(context, QRunReminderReceiver::class.java).setAction("com.stepq.app.QRUN_REMINDER/$id")
        if (run != null) {
            intent.putExtra("title", run.title)
                .putExtra("place", shortPlace(run.placeName))
                .putExtra("time", timeLabel(run.startsAt.atZone(ZoneId.systemDefault()).toLocalTime()))
        }
        return PendingIntent.getBroadcast(context, id.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }
}

class QRunReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra("title") ?: return
        val body = "${intent.getStringExtra("place").orEmpty()} at ${intent.getStringExtra("time").orEmpty()}. Have a good walk!"
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, context.getString(R.string.notification_channel_default))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("$title starts in an hour")
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        val manager = NotificationManagerCompat.from(context)
        if (manager.areNotificationsEnabled()) {
            @Suppress("MissingPermission") // checked by areNotificationsEnabled()
            manager.notify(intent.action.hashCode(), n)
        }
    }
}
