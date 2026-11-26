package com.stepq.app.data

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

/** Dates the way both apps count them. */
object Dates {
    /** India time: the day Postgres uses for limits (water reminders, challenge progress). */
    val IST: TimeZone = TimeZone.of("Asia/Kolkata")

    /** Today on this phone's clock and time zone: the day steps are saved under. */
    fun today(zone: TimeZone = TimeZone.currentSystemDefault()): LocalDate =
        Clock.System.now().toLocalDateTime(zone).date

    fun todayIst(): LocalDate = today(IST)

    fun startOfDay(day: LocalDate, zone: TimeZone = IST): Instant = day.atStartOfDayIn(zone)

    fun LocalDate.plusDays(n: Int): LocalDate = plus(DatePeriod(days = n))
    fun LocalDate.minusDays(n: Int): LocalDate = minus(DatePeriod(days = n))
    fun LocalDate.minusYears(n: Int): LocalDate = minus(DatePeriod(years = n))
    fun LocalDate.firstOfMonth(): LocalDate = LocalDate(year, month, 1)

    /** The Monday on or before [day]. */
    fun weekStart(day: LocalDate): LocalDate = day.minusDays(day.dayOfWeek.ordinal - DayOfWeek.MONDAY.ordinal)

    fun localDate(instant: Instant, zone: TimeZone = IST): LocalDate = instant.toLocalDateTime(zone).date

    /** Postgres timestamps come as ISO with an offset, sometimes without the colon or seconds. */
    fun parseInstant(s: String): Instant = runCatching { Instant.parse(s) }
        .getOrElse { Instant.parse(normalizeOffset(s)) }

    private fun normalizeOffset(s: String): String {
        var t = s.trim().replace(' ', 'T')
        // "+05:30" is fine; "+0530" and "+05" are not ISO enough for Instant.parse.
        val m = Regex("([+-])(\\d{2})(\\d{2})?$").find(t)
        if (m != null && !t.endsWith("Z")) {
            val (sign, hh, mm) = m.destructured
            t = t.substring(0, m.range.first) + "$sign$hh:${mm.ifEmpty { "00" }}"
        }
        return t
    }

    fun parseDateOrNull(s: String?): LocalDate? = s?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
}
