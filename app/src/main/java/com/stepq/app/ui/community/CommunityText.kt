package com.stepq.app.ui.community

import com.stepq.app.data.Post
import com.stepq.app.ui.onboarding.formatSteps
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Threads-style age: "now", "5m", "3h", "2d", then the date ("12 Nov", or "12 Nov 2025" in another year). */
fun timeAgo(createdAt: String, now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()): String {
    val at = runCatching { Instant.parse(createdAt) }
        .recoverCatching { java.time.OffsetDateTime.parse(createdAt).toInstant() }
        .getOrNull() ?: return ""
    val age = Duration.between(at, now)
    return when {
        age.seconds < 60 -> "now"
        age.toMinutes() < 60 -> "${age.toMinutes()}m"
        age.toHours() < 24 -> "${age.toHours()}h"
        age.toDays() < 7 -> "${age.toDays()}d"
        else -> {
            val date = at.atZone(zone)
            val pattern = if (date.year == now.atZone(zone).year) "d MMM" else "d MMM yyyy"
            date.format(DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH))
        }
    }
}

/** Counts next to the action icons: blank at 0, then 7, 1.2K, 3.4M. */
fun compactCount(n: Int): String = when {
    n <= 0 -> ""
    n < 1_000 -> n.toString()
    n < 1_000_000 -> trimDecimal(n / 1_000.0) + "K"
    else -> trimDecimal(n / 1_000_000.0) + "M"
}

private fun trimDecimal(v: Double): String =
    if (v >= 100) v.toInt().toString() else String.format(Locale.US, "%.1f", v).removeSuffix(".0")

/** What the share sheet sends for a post. */
fun postShareText(post: Post): String {
    val who = "${post.authorName} (@${post.authorUsername})"
    val text = when {
        post.isAchievement && post.steps != null -> {
            val walked = "$who walked ${formatSteps(post.steps)} steps" +
                (if (post.goalReached) " and hit their goal" else "") + " on StepQ"
            if (post.body.isBlank()) "$walked 🎉" else "$walked: “${post.body.trim()}”"
        }
        else -> "“${post.body.trim()}” by $who on StepQ"
    }
    return "$text\n\nWalk with us: https://stepq.in"
}

/** "1 reply", "4 replies", "" at 0. */
fun repliesLabel(n: Int): String = when (n) {
    0 -> ""
    1 -> "1 reply"
    else -> "${compactCount(n)} replies"
}

/** Ideas to start a post with, shown as chips in the composer. */
val postPrompts = listOf(
    "Hit my goal today! 🎉",
    "Morning walk done ☀️",
    "Who's walking this evening?",
    "New personal best 💪",
    "Rainy day, indoor laps 🌧️",
)
