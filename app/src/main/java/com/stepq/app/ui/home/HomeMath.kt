package com.stepq.app.ui.home

import com.stepq.app.data.DailySteps
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.roundToInt

/** Piko's mood on Home (Figma A12 notes): Nudge until the goal, Cheer at the moment, then Happy. */
enum class PikoMood { Nudge, Cheer, Happy }

fun greeting(now: LocalTime): String = when (now.hour) {
    in 5..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    else -> "Good evening"
}

/**
 * Provisional StepQ score: percent of today's goal, capped at 100. The Figma shows other
 * numbers once the goal is passed (81, 84), so the real formula is still to be decided.
 */
fun stepqScore(steps: Int, goal: Int): Int =
    if (goal <= 0) 0 else (steps * 100.0 / goal).roundToInt().coerceIn(0, 100)

/** Monday-to-Sunday steps for the week containing [today]; days after today are null. */
fun weekBars(days: List<DailySteps>, today: LocalDate): List<Int?> {
    val byDay = days.associate { LocalDate.parse(it.day) to it.steps }
    val monday = today.with(DayOfWeek.MONDAY)
    return (0..6).map { i ->
        val d = monday.plusDays(i.toLong())
        if (d.isAfter(today)) null else byDay[d] ?: 0
    }
}

/** Average over the days of this week so far (today included). */
fun weekAverage(bars: List<Int?>): Int {
    val known = bars.filterNotNull()
    return if (known.isEmpty()) 0 else known.average().roundToInt()
}

/** [streak]: days in a row I've shared my steps with this friend (0 for me, or no streak). */
data class FriendRow(
    val rank: Int,
    val name: String,
    val avatar: com.stepq.app.data.AvatarConfig,
    val steps: Int,
    val isMe: Boolean,
    val streak: Int = 0,
)

/**
 * A12 "Friends today": the top three by steps, always including the user at their real rank.
 * [streaks] lines up with [friends]: my streak with each of them.
 */
fun friendsToday(
    me: Pair<String, com.stepq.app.data.AvatarConfig>,
    mySteps: Int,
    friends: List<Triple<String, com.stepq.app.data.AvatarConfig, Int>>,
    size: Int = 3,
    streaks: List<Int> = emptyList(),
): List<FriendRow> {
    val all = (friends.mapIndexed { i, f -> FriendRow(0, f.first, f.second, f.third, false, streaks.getOrElse(i) { 0 }) } +
        FriendRow(0, "You", me.second, mySteps, true))
        .sortedWith(compareByDescending<FriendRow> { it.steps }.thenBy { !it.isMe })
        .mapIndexed { i, r -> r.copy(rank = i + 1) }
    val top = all.take(size)
    if (top.any { it.isMe }) return top
    return all.take(size - 1) + all.first { it.isMe }
}

/** Metres under 1 km (so a short walk doesn't read "0.0 km"), then km with one decimal. */
fun distanceLabel(km: Double): Pair<String, String> =
    if (km < 1.0) "${(km * 1000).roundToInt()}" to "m"
    else String.format(java.util.Locale.US, "%.1f", km) to "km"

/**
 * [days] with each day in [local] replaced by the local row. The phone's own counter is the
 * truth for its days; the server copy can lag if a save failed or hasn't happened yet.
 */
fun overlayDays(days: List<DailySteps>, local: List<DailySteps>): List<DailySteps> =
    (local + days.filter { d -> local.none { it.day == d.day } }).sortedByDescending { it.day }

/** Minutes of walking for a step count, at about 100 steps a minute; any walking counts as 1. */
fun estimatedActiveMinutes(steps: Int): Int =
    if (steps <= 0) 0 else ((steps + STEPS_PER_ACTIVE_MINUTE - 1) / STEPS_PER_ACTIVE_MINUTE).coerceAtMost(1440)

private const val STEPS_PER_ACTIVE_MINUTE = 100

/**
 * "Shared 6,240 steps with Arjun. 4-day streak!" Streaks are per friend, so the streak is only
 * named when sharing with one friend, and only on the first share to them that day.
 */
fun shareToast(names: List<String>, steps: Int, streak: Int, firstToday: Boolean): String {
    val who = when (names.size) {
        0 -> "your friends"
        1 -> names[0]
        2 -> "${names[0]} and ${names[1]}"
        else -> "${names[0]} and ${names.size - 1} friends"
    }
    val tail = when {
        !firstToday || names.size != 1 -> ""
        streak <= 1 -> " Streak started!"
        else -> " $streak-day streak!"
    }
    return "Shared ${com.stepq.app.ui.onboarding.formatSteps(steps)} steps with $who.$tail"
}
