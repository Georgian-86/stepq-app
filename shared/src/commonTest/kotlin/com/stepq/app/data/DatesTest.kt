package com.stepq.app.data

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class DatesTest {
    @Test fun weekStartsOnMonday() {
        assertEquals(LocalDate(2026, 11, 9), Dates.weekStart(LocalDate(2026, 11, 12)))
        assertEquals(LocalDate(2026, 11, 9), Dates.weekStart(LocalDate(2026, 11, 9)))
        assertEquals(LocalDate(2026, 11, 9), Dates.weekStart(LocalDate(2026, 11, 15)))
    }

    @Test fun postgresTimestamps() {
        val t = Instant.parse("2026-11-11T03:00:00Z")
        assertEquals(t, Dates.parseInstant("2026-11-11T03:00:00+00:00"))
        assertEquals(t, Dates.parseInstant("2026-11-11 08:30:00+05:30"))
        assertEquals(t, Dates.parseInstant("2026-11-11T08:30:00+0530"))
        assertEquals(t, Dates.parseInstant("2026-11-11T03:00:00+00"))
    }

    @Test fun progressCountsIndiaDays() {
        val days = listOf(
            DailySteps("u", "2026-11-10", 5000),
            DailySteps("u", "2026-11-11", 6000),
            DailySteps("u", "2026-11-12", 7000),
            DailySteps("u", "2026-11-20", 9000),
        )
        val joined = Instant.parse("2026-11-11T03:00:00Z")
        val ends = Instant.parse("2026-11-18T18:29:00Z")
        assertEquals(13000, ChallengesRepository.progress(days, joined, ends))
    }

    @Test fun pendingGoal() {
        val p = Profile(id = "me", displayName = "Riya", username = "riya", nextDailyGoal = 10000, nextGoalFrom = "2026-11-19")
        assertEquals(null, pendingGoalDue(p, LocalDate(2026, 11, 18)))
        assertEquals(10000, pendingGoalDue(p, LocalDate(2026, 11, 19)))
    }
}
