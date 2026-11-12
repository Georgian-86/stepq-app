package com.stepq.app.ui.qrun

import com.stepq.app.data.QRun
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

data class LatLng(val lat: Double, val lng: Double)

/** Straight-line distance in km. */
fun distanceKm(a: LatLng, b: LatLng): Double {
    val r = 6371.0
    val dLat = Math.toRadians(b.lat - a.lat)
    val dLng = Math.toRadians(b.lng - a.lng)
    val h = sin(dLat / 2).pow(2) + cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLng / 2).pow(2)
    return 2 * r * asin(sqrt(h))
}

fun QRun.distanceFrom(here: LatLng?): Double? {
    if (here == null || lat == null || lng == null) return null
    return distanceKm(here, LatLng(lat, lng))
}

/** "650 m", "1.2 km", "24 km". */
fun kmLabel(km: Double): String = when {
    km < 1 -> "${((km * 1000) / 50).roundToInt() * 50} m"
    km < 10 -> String.format(Locale.US, "%.1f km", km)
    else -> "${km.roundToInt()} km"
}

/** "2 hours ago", "Yesterday". */
fun postedAgo(at: Instant, now: Instant): String {
    val d = Duration.between(at, now)
    return when {
        d.toMinutes() < 1 -> "Just now"
        d.toMinutes() < 60 -> plural(d.toMinutes(), "minute") + " ago"
        d.toHours() < 24 -> plural(d.toHours(), "hour") + " ago"
        d.toDays() < 2 -> "Yesterday"
        else -> plural(d.toDays(), "day") + " ago"
    }
}

private fun plural(n: Long, word: String) = "$n $word${if (n == 1L) "" else "s"}"

private val cardDate = DateTimeFormatter.ofPattern("EEE, d MMM · h:mm a", Locale.ENGLISH)
private val plannerDate = DateTimeFormatter.ofPattern("EEE d MMM · h:mm a", Locale.ENGLISH)
private val sheetDate = DateTimeFormatter.ofPattern("EEE d MMM, h:mm a", Locale.ENGLISH)
private val timeOnly = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)

/** A18 card: "Sun, 18 Oct · 6:30 AM". */
fun cardWhen(at: Instant, zone: ZoneId): String = cardDate.format(at.atZone(zone))

/** A21 card: "Sun 18 Oct · 6:30 AM". */
fun plannerWhen(at: Instant, zone: ZoneId): String = plannerDate.format(at.atZone(zone))

fun timeLabel(t: LocalTime): String = timeOnly.format(t)

/** A20: "Morning run, easy pace · Carter Road, Sun 18 Oct, 6:30 AM". */
fun sheetSummary(run: QRun, zone: ZoneId): String =
    "${run.title} · ${shortPlace(run.placeName)}, ${sheetDate.format(run.startsAt.atZone(zone))}"

/** The first part of an address: "Carter Road Promenade, Bandra West" → "Carter Road Promenade". */
fun shortPlace(place: String): String = place.substringBefore(',').trim()

/** A21 banner: "You're in! Morning run at Carter Road, Sun 18 Oct at 6:30 AM." */
fun joinedMessage(run: QRun, zone: ZoneId, remind: Boolean): String {
    val z = run.startsAt.atZone(zone)
    val day = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH).format(z)
    return "You're in! ${run.title} at ${shortPlace(run.placeName)}, $day at ${timeOnly.format(z)}." +
        if (remind) " We'll remind you an hour before." else " Add it to your calendar so you don't miss it."
}

fun goingLabel(n: Int) = "$n going"

/**
 * A18 list: QRuns that haven't started, nearest first when we know where you are (those without
 * a location go last), soonest first otherwise.
 */
fun listOrder(runs: List<QRun>, here: LatLng?, now: Instant): List<QRun> {
    val open = runs.filter { it.startsAt.isAfter(now) }
    if (here == null) return open.sortedBy { it.startsAt }
    return open.sortedWith(compareBy<QRun>({ it.distanceFrom(here) ?: Double.MAX_VALUE }, { it.startsAt }))
}

/** A21 tabs: the QRuns I host or joined, upcoming soonest first and past latest first. */
fun plannerSplit(runs: List<QRun>, myId: String, now: Instant): Pair<List<QRun>, List<QRun>> {
    val mine = runs.filter { it.joined || it.createdBy == myId }
    val upcoming = mine.filter { it.startsAt.plus(Duration.ofHours(2)).isAfter(now) }.sortedBy { it.startsAt }
    val past = (mine - upcoming.toSet()).sortedByDescending { it.startsAt }
    return upcoming to past
}

/** A19 calendar: the month's days in Monday-first weeks, null for the blanks. */
fun monthCells(month: YearMonth): List<LocalDate?> {
    val lead = month.atDay(1).dayOfWeek.value - 1
    val days = (1..month.lengthOfMonth()).map { month.atDay(it) }
    val cells = List<LocalDate?>(lead) { null } + days
    return cells + List((7 - cells.size % 7) % 7) { null }
}

/** QRuns can be posted up to this many days ahead. */
const val QRUN_MAX_DAYS_AHEAD = 60L

fun pickable(day: LocalDate, today: LocalDate) = !day.isBefore(today) && !day.isAfter(today.plusDays(QRUN_MAX_DAYS_AHEAD))

/** What's wrong with the A19 form, or null when it can be posted. */
fun postProblem(title: String, place: String, date: LocalDate?, time: LocalTime?, zone: ZoneId, now: Instant): String? = when {
    title.isBlank() -> "Give your QRun a name, like \"Morning run, easy pace\"."
    title.trim().length > 60 -> "Keep the name under 60 characters."
    place.isBlank() -> "Say where to meet."
    place.trim().length > 120 -> "Keep the place under 120 characters."
    date == null -> "Pick a date."
    time == null -> "Pick a time."
    !date.atTime(time).atZone(zone).toInstant().isAfter(now.plusSeconds(15 * 60)) -> "Pick a time at least 15 minutes from now."
    else -> null
}

/** Opens the maps app with directions; works with or without Google Maps installed. */
fun directionsUrl(run: QRun): String {
    val dest = if (run.lat != null && run.lng != null) "${run.lat},${run.lng}" else java.net.URLEncoder.encode(run.placeName, "UTF-8")
    return "https://www.google.com/maps/dir/?api=1&destination=$dest"
}
