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
 * Days in a row the goal was met, counting back from today if today is already met, or from
 * yesterday otherwise (so the streak doesn't drop to 0 every morning).
 */
fun streak(days: List<DailySteps>, goal: Int, today: LocalDate): Int {
    val met = days.filter { it.steps >= goal }.map { LocalDate.parse(it.day) }.toSet()
    var day = if (today in met) today else today.minusDays(1)
    var count = 0
    while (day in met) {
        count++
        day = day.minusDays(1)
    }
    return count
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

data class FriendRow(val rank: Int, val name: String, val avatar: com.stepq.app.data.AvatarConfig, val steps: Int, val isMe: Boolean)

/** A12 "Friends today": the top three by steps, always including the user at their real rank. */
fun friendsToday(
    me: Pair<String, com.stepq.app.data.AvatarConfig>,
    mySteps: Int,
    friends: List<Triple<String, com.stepq.app.data.AvatarConfig, Int>>,
    size: Int = 3,
): List<FriendRow> {
    val all = (friends.map { Triple(it.first, it.second, it.third) to false } + (Triple(me.first, me.second, mySteps) to true))
        .sortedWith(compareByDescending<Pair<Triple<String, com.stepq.app.data.AvatarConfig, Int>, Boolean>> { it.first.third }.thenBy { !it.second })
        .mapIndexed { i, (p, isMe) -> FriendRow(i + 1, if (isMe) "You" else p.first, p.second, p.third, isMe) }
    val top = all.take(size)
    if (top.any { it.isMe }) return top
    return all.take(size - 1) + all.first { it.isMe }
}
