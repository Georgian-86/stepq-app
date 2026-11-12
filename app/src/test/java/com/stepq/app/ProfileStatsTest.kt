package com.stepq.app

import com.stepq.app.data.DailySteps
import com.stepq.app.ui.profile.profileStats
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class ProfileStatsTest {
    @Test fun streakBestDayAndKm() {
        val today = LocalDate.of(2026, 11, 12)
        val days = listOf(
            DailySteps("u", "2026-11-12", 3000, distanceM = 2300),
            DailySteps("u", "2026-11-11", 9000, distanceM = 6900),
            DailySteps("u", "2026-11-10", 11830, distanceM = 9000),
            DailySteps("u", "2026-11-08", 8500, distanceM = 6500),
        )
        val s = profileStats(days, goal = 8000, today = today)
        assertEquals(2, s.streak)
        assertEquals(11830, s.bestDay)
        assertEquals(24, s.kmSoFar)
    }
}
