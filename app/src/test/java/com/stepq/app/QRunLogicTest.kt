package com.stepq.app

import com.stepq.app.data.QRun
import com.stepq.app.ui.qrun.LatLng
import com.stepq.app.ui.qrun.cardWhen
import com.stepq.app.ui.qrun.directionsUrl
import com.stepq.app.ui.qrun.distanceKm
import com.stepq.app.ui.qrun.joinedMessage
import com.stepq.app.ui.qrun.kmLabel
import com.stepq.app.ui.qrun.listOrder
import com.stepq.app.ui.qrun.monthCells
import com.stepq.app.ui.qrun.pickable
import com.stepq.app.ui.qrun.plannerSplit
import com.stepq.app.ui.qrun.postProblem
import com.stepq.app.ui.qrun.postedAgo
import com.stepq.app.ui.qrun.sheetSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId

class QRunLogicTest {
    private val ist = ZoneId.of("Asia/Kolkata")
    private val now = Instant.parse("2026-10-17T04:00:00Z") // Sat 17 Oct, 9:30 AM IST

    private fun run(id: String, startsAt: String, lat: Double? = null, lng: Double? = null, joined: Boolean = false, by: String = "host") = QRun(
        id = id, title = "Morning run, easy pace", placeName = "Carter Road Promenade, Bandra West", lat = lat, lng = lng,
        startsAtRaw = startsAt, createdAtRaw = "2026-10-17T02:00:00Z", createdBy = by, hostName = "Arjun Mehta", going = 8, joined = joined,
    )

    @Test fun distances() {
        val bandra = LatLng(19.0596, 72.8295)
        val juhu = LatLng(19.0988, 72.8267)
        assertEquals(4.4, distanceKm(bandra, juhu), 0.2)
        assertEquals("650 m", kmLabel(0.66))
        assertEquals("1.2 km", kmLabel(1.24))
        assertEquals("24 km", kmLabel(23.6))
    }

    @Test fun labels() {
        assertEquals("2 hours ago", postedAgo(Instant.parse("2026-10-17T02:00:00Z"), now))
        assertEquals("1 minute ago", postedAgo(now.minusSeconds(90), now))
        assertEquals("Yesterday", postedAgo(now.minusSeconds(30 * 3600), now))
        val r = run("a", "2026-10-18T01:00:00Z")
        assertEquals("Sun, 18 Oct · 6:30 AM", cardWhen(r.startsAt, ist))
        assertEquals("Morning run, easy pace · Carter Road Promenade, Sun 18 Oct, 6:30 AM", sheetSummary(r, ist))
        assertEquals(
            "You're in! Morning run, easy pace at Carter Road Promenade, Sun 18 Oct at 6:30 AM. We'll remind you an hour before.",
            joinedMessage(r, ist, remind = true),
        )
    }

    @Test fun listIsNearestFirstAndHidesStartedOnes() {
        val here = LatLng(19.0596, 72.8295)
        val far = run("far", "2026-10-18T01:00:00Z", 19.0988, 72.8267)
        val near = run("near", "2026-10-22T12:30:00Z", 19.0600, 72.8300)
        val unknown = run("unknown", "2026-10-17T23:00:00Z")
        val started = run("started", "2026-10-17T03:00:00Z", 19.0600, 72.8300)
        assertEquals(listOf("near", "far", "unknown"), listOrder(listOf(far, near, unknown, started), here, now).map { it.id })
        assertEquals(listOf("unknown", "far", "near"), listOrder(listOf(far, near, unknown, started), null, now).map { it.id })
    }

    @Test fun plannerHasMineOnly() {
        val going = run("going", "2026-10-18T01:00:00Z", joined = true)
        val hosting = run("hosting", "2026-10-22T12:30:00Z", by = "me")
        val other = run("other", "2026-10-19T01:00:00Z")
        val past = run("past", "2026-10-10T01:00:00Z", joined = true)
        val (up, done) = plannerSplit(listOf(hosting, other, going, past), "me", now)
        assertEquals(listOf("going", "hosting"), up.map { it.id })
        assertEquals(listOf("past"), done.map { it.id })
    }

    @Test fun calendarStartsOnMonday() {
        val oct = monthCells(YearMonth.of(2026, 10)) // 1 Oct 2026 is a Thursday
        assertEquals(35, oct.size)
        assertEquals(listOf(null, null, null), oct.take(3))
        assertEquals(LocalDate.of(2026, 10, 1), oct[3])
        val today = LocalDate.of(2026, 10, 17)
        assertTrue(pickable(today, today))
        assertTrue(!pickable(today.minusDays(1), today))
        assertTrue(!pickable(today.plusDays(61), today))
    }

    @Test fun postNeedsAFutureTime() {
        val sat = LocalDate.of(2026, 10, 17)
        assertEquals("Pick a time at least 15 minutes from now.", postProblem("Run", "Carter Road", sat, LocalTime.of(6, 30), ist, now))
        assertNull(postProblem("Run", "Carter Road", sat.plusDays(1), LocalTime.of(6, 30), ist, now))
        assertEquals("Say where to meet.", postProblem("Run", " ", sat, LocalTime.NOON, ist, now))
        assertEquals("Pick a time.", postProblem("Run", "X", sat, null, ist, now))
    }

    @Test fun directionsUseCoordinatesWhenKnown() {
        assertEquals("https://www.google.com/maps/dir/?api=1&destination=19.06,72.83", directionsUrl(run("a", "2026-10-18T01:00:00Z", 19.06, 72.83)))
        assertEquals(
            "https://www.google.com/maps/dir/?api=1&destination=Carter+Road+Promenade%2C+Bandra+West",
            directionsUrl(run("a", "2026-10-18T01:00:00Z")),
        )
    }
}
