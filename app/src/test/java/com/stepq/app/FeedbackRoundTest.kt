package com.stepq.app

import com.stepq.app.data.Contacts
import com.stepq.app.data.Profile
import com.stepq.app.data.QRun
import com.stepq.app.data.defaultHairStyle
import com.stepq.app.data.kx
import com.stepq.app.data.pendingGoalDue
import com.stepq.app.ui.qrun.LatLng
import com.stepq.app.ui.qrun.PriceFilter
import com.stepq.app.ui.qrun.QRunSort
import com.stepq.app.ui.qrun.listOrder
import com.stepq.app.ui.qrun.priceLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class FeedbackRoundTest {
    private val now = Instant.parse("2026-11-18T04:00:00Z")
    private val here = LatLng(19.06, 72.83)
    private fun run(id: String, lat: Double?, price: Int = 0, hours: Long = 24) = QRun(
        id = id, title = id, placeName = id, lat = lat, lng = lat?.let { 72.83 },
        startsAtRaw = now.plusSeconds(hours * 3600).toString(), createdAtRaw = now.toString(),
        createdBy = "x", hostName = "X", priceInr = price,
    )
    private val runs = listOf(run("far", 19.30), run("near", 19.07, price = 499), run("mid", 19.15), run("nowhere", null, hours = 2))

    @Test fun nearestFirstPutsUnlocatedLast() =
        assertEquals(listOf("near", "mid", "far", "nowhere"), listOrder(runs, here, now, QRunSort.Nearest).map { it.id })

    @Test fun farthestFirstPutsUnlocatedLast() =
        assertEquals(listOf("far", "mid", "near", "nowhere"), listOrder(runs, here, now, QRunSort.Farthest).map { it.id })

    @Test fun soonestIgnoresDistance() =
        assertEquals("nowhere", listOrder(runs, here, now, QRunSort.Soonest).first().id)

    @Test fun priceFilters() {
        assertEquals(listOf("near"), listOrder(runs, here, now, price = PriceFilter.Paid).map { it.id })
        assertEquals(3, listOrder(runs, here, now, price = PriceFilter.Free).size)
    }

    @Test fun priceLabels() {
        assertEquals("Free", priceLabel(0))
        assertEquals("₹1,499", priceLabel(1499))
    }

    @Test fun pendingGoalStartsOnItsDay() {
        val p = Profile(id = "me", displayName = "Riya", username = "riya", dailyGoal = 8000, nextDailyGoal = 10000, nextGoalFrom = "2026-11-19")
        assertNull(pendingGoalDue(p, LocalDate.of(2026, 11, 18).kx))
        assertEquals(10000, pendingGoalDue(p, LocalDate.of(2026, 11, 19).kx))
        assertEquals(10000, pendingGoalDue(p, LocalDate.of(2026, 11, 25).kx))
        assertNull(pendingGoalDue(p.copy(nextDailyGoal = null), LocalDate.of(2026, 11, 25).kx))
    }

    @Test fun hairStyleFollowsGender() {
        assertEquals(1, defaultHairStyle("female"))
        assertEquals(0, defaultHairStyle("male"))
        assertEquals(0, defaultHairStyle(null))
    }

    @Test fun contactEmailsAreHashedLikeThePostgresSide() {
        // select encode(sha256(convert_to('riya@test', 'UTF8')), 'hex') in Postgres
        assertEquals("03f26024327c0ec2552ad1d89ebdddef417cd6f9ca1b3f608ebb903218aaeb2d", Contacts.sha256Hex("riya@test"))
        assertEquals(Contacts.sha256Hex("riya@test"), Contacts.sha256Hex(Contacts.normalizeEmail("  Riya@Test ")))
    }

    @Test fun searchHighlightMarksWordStarts() {
        val h = com.stepq.app.ui.friends.highlight("Sara Arora", "ar")
        assertEquals(5, h.spanStyles.single().start)
        assertEquals(0, com.stepq.app.ui.friends.highlight("Arjun", "ar").spanStyles.single().start)
        assertEquals(0, com.stepq.app.ui.friends.highlight("Meera", "xy").spanStyles.size)
    }
}
