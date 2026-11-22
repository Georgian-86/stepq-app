package com.stepq.app.health

import android.content.Context
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Which StepQ accounts have counted steps on this phone, and from when.
 *
 * The phone's step counter and Health Connect belong to the phone, not to a StepQ account. If
 * someone logs out and a different account logs in, the steps already walked on this phone
 * were walked by the first person, so the new account starts at 0 from the moment it first
 * signs in here. The first account ever used on the phone keeps everything, as before.
 */
class StepAccounts(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("step_accounts", Context.MODE_PRIVATE)

    /** When [userId] started counting on this phone, in epoch ms. 0 means all steps count. */
    @Synchronized
    fun since(userId: String, nowMs: Long = System.currentTimeMillis()): Long {
        val known = prefs.all.mapNotNull { (k, v) -> (v as? Long)?.let { k to it } }.toMap()
        val claimed = claim(known, userId, nowMs)
        if (userId !in known) prefs.edit().putLong(userId, claimed.getValue(userId)).apply()
        return claimed.getValue(userId)
    }

    companion object {
        /** Adds [userId] to [known]: the first account on the phone gets 0, later ones [nowMs]. */
        fun claim(known: Map<String, Long>, userId: String, nowMs: Long): Map<String, Long> =
            if (userId in known) known else known + (userId to if (known.isEmpty()) 0L else nowMs)
    }
}

/**
 * The Health Connect ranges to read for the last [days] days when the account only counts
 * from [sinceMs]: whole days from [wholeDaysFrom], plus [partial] for the day it started
 * (from the moment it started to midnight). Days before that are left out, so they read as 0.
 */
data class HealthWindows(
    val wholeDaysFrom: LocalDateTime?,
    val end: LocalDateTime,
    val partial: Pair<LocalDateTime, LocalDateTime>?,
) {
    companion object {
        fun of(today: LocalDate, days: Int, sinceMs: Long, zone: ZoneId = ZoneId.systemDefault()): HealthWindows {
            val start = today.minusDays((days - 1).toLong()).atStartOfDay()
            val end = today.plusDays(1).atStartOfDay()
            val since = Instant.ofEpochMilli(sinceMs).atZone(zone).toLocalDateTime()
            if (!since.isAfter(start)) return HealthWindows(start, end, null)
            if (!since.isBefore(end)) return HealthWindows(null, end, null)
            val nextMidnight = since.toLocalDate().plusDays(1).atStartOfDay()
            return HealthWindows(
                wholeDaysFrom = nextMidnight.takeIf { it.isBefore(end) },
                end = end,
                partial = since to nextMidnight,
            )
        }
    }
}
