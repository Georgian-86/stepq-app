package com.stepq.app.data

import com.stepq.app.data.Dates.plusDays
import kotlinx.datetime.LocalDate

/** The words on the "Step goal updated" popup, the same on Android and iOS. */
data class GoalUpdatedCopy(val title: String, val body: String, val startsLabel: String?)

/** 10000 → "10,000". */
fun groupedSteps(n: Int): String {
    val digits = kotlin.math.abs(n).toString()
    val grouped = digits.reversed().chunked(3).joinToString(",").reversed()
    return if (n < 0) "-$grouped" else grouped
}

/** 2026-11-30 → "Mon 30 Nov". */
fun shortDay(d: LocalDate): String {
    val day = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")[d.dayOfWeek.ordinal]
    val month = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")[d.month.ordinal]
    return "$day ${d.day} $month"
}

/** What to say after [change] was saved, on the phone's [today]. */
fun goalUpdatedCopy(change: GoalChange, today: LocalDate = Dates.today()): GoalUpdatedCopy {
    val next = change.nextDailyGoal
        ?: return GoalUpdatedCopy(
            title = "Daily goal unchanged",
            body = "You're keeping your goal of ${groupedSteps(change.dailyGoal)} steps a day. Any change you'd planned for tomorrow is cancelled.",
            startsLabel = null,
        )
    val from = Dates.parseDateOrNull(change.nextGoalFrom)
    val tomorrow = from == null || from == today.plusDays(1)
    val starts = if (tomorrow) "tomorrow" else "on ${shortDay(from!!)}"
    return GoalUpdatedCopy(
        title = "Step goal updated",
        body = "Your new goal of ${groupedSteps(next)} steps starts $starts. " +
            "Today's goal stays at ${groupedSteps(change.dailyGoal)} steps, so today counts just as it started.",
        startsLabel = if (tomorrow) "Tomorrow" else shortDay(from!!),
    )
}
