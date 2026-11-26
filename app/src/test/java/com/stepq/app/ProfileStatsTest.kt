package com.stepq.app

import com.stepq.app.data.DailySteps
import com.stepq.app.ui.profile.profileStats
import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileStatsTest {
    @Test fun scoreBestDayAndKm() {
        val days = listOf(
            DailySteps("u", "2026-11-12", 3000, distanceM = 2300),
            DailySteps("u", "2026-11-11", 9000, distanceM = 6900),
            DailySteps("u", "2026-11-10", 11830, distanceM = 9000),
            DailySteps("u", "2026-11-08", 8500, distanceM = 6500),
        )
        val s = profileStats(days, goal = 8000, score = 40)
        assertEquals(40, s.score)
        assertEquals(11830, s.bestDay)
        assertEquals(24, s.kmSoFar)
        // Before the migration: the score counts days at the goal.
        val old = profileStats(days, goal = 8000)
        assertEquals(3, old.score)
    }

    @Test fun shareToasts() {
        assertEquals("Shared 6,240 steps with Arjun. Streak started!", com.stepq.app.ui.home.shareToast(listOf("Arjun"), 6240, 1, firstToday = true))
        assertEquals("Shared 6,240 steps with Arjun. 4-day streak!", com.stepq.app.ui.home.shareToast(listOf("Arjun"), 6240, 4, firstToday = true))
        // Streaks are per friend: sharing with several names no single streak.
        assertEquals("Shared 6,240 steps with Arjun and Meera.", com.stepq.app.ui.home.shareToast(listOf("Arjun", "Meera"), 6240, 4, firstToday = true))
        assertEquals("Shared 100 steps with Arjun and 2 friends.", com.stepq.app.ui.home.shareToast(listOf("Arjun", "Meera", "Kabir"), 100, 4, firstToday = false))
    }

    @Test fun shareNotification() {
        val one = listOf(com.stepq.app.push.InboxShare(1, "Arjun Mehta", 9120, "2026-11-20T10:00:00Z"))
        assertEquals("Arjun shared 9,120 steps with you", com.stepq.app.push.shareMessage(one)!!.first)
        val two = one + com.stepq.app.push.InboxShare(2, "Meera Iyer", 5000, "2026-11-20T09:00:00Z")
        assertEquals("Arjun and 1 friend shared their steps with you", com.stepq.app.push.shareMessage(two)!!.first)
    }
}
