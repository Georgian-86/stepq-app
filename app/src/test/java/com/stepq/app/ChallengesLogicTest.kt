package com.stepq.app

import com.stepq.app.data.ChallengesRepository
import com.stepq.app.data.DailySteps
import com.stepq.app.ui.challenges.endsIn
import com.stepq.app.ui.challenges.periodEnd
import com.stepq.app.ui.challenges.periodLabel
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class ChallengesLogicTest {
    @Test fun endsInLabels() {
        val now = Instant.parse("2026-11-12T10:00:00Z")
        assertEquals("Ends in 1d 14h", endsIn(now.plusSeconds((24 + 14) * 3600L + 60), now))
        assertEquals("Ends in 3h", endsIn(now.plusSeconds(3 * 3600L + 5), now))
        assertEquals("Ended", endsIn(now.minusSeconds(1), now))
    }

    @Test fun weekRunsMondayToSundayIndiaTime() {
        val thu = LocalDate.of(2026, 11, 12)
        assertEquals(LocalDate.of(2026, 11, 9), ChallengesRepository.weekStart(thu))
        assertEquals(Instant.parse("2026-11-15T18:30:00Z"), periodEnd(thu, monthly = false))
        assertEquals("Mon 9 Nov – Sun 15 Nov · ranked by total steps", periodLabel(thu, monthly = false))
        assertEquals("1 Nov – 30 Nov · ranked by total steps", periodLabel(thu, monthly = true))
    }

    @Test fun progressCountsFromTheDayYouJoin() {
        val days = listOf(
            DailySteps("u", "2026-11-10", 5000),
            DailySteps("u", "2026-11-11", 6000),
            DailySteps("u", "2026-11-12", 7000),
            DailySteps("u", "2026-11-20", 9000),
        )
        val joined = Instant.parse("2026-11-11T03:00:00Z")
        val ends = Instant.parse("2026-11-18T18:29:00Z")
        assertEquals(13000, ChallengesRepository.progress(days, joined, ends))
        assertEquals(joined, ChallengesRepository.parseInstant("2026-11-11T03:00:00+00:00"))
    }

    @Test fun tiesSharePlaces() {
        fun r(name: String, steps: Int) = com.stepq.app.data.Ranked(com.stepq.app.data.PersonSummary(name, name, name), steps, false)
        val ranked = ChallengesRepository.withRanks(listOf(r("c", 500), r("a", 900), r("b", 900), r("d", 100)))
        assertEquals(listOf("a" to 1, "b" to 1, "c" to 3, "d" to 4), ranked.map { it.person.id to it.rank })
    }

    @Test fun countryLabels() {
        assertEquals("India", com.stepq.app.ui.challenges.countryName("IN"))
        assertEquals("Everyone in India · 1,240 walkers", com.stepq.app.ui.challenges.rankingScope("IN", 1240))
        assertEquals("Everyone in your country · 1 walker", com.stepq.app.ui.challenges.rankingScope(null, 1))
    }
}
