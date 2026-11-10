package com.stepq.app

import com.stepq.app.data.AvatarConfig
import com.stepq.app.data.DailySteps
import com.stepq.app.ui.home.friendsToday
import com.stepq.app.ui.home.greeting
import com.stepq.app.ui.home.stepqScore
import com.stepq.app.ui.home.streak
import com.stepq.app.ui.home.weekAverage
import com.stepq.app.ui.home.weekBars
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class HomeMathTest {
    private val today = LocalDate.of(2026, 10, 17) // a Saturday
    private fun day(daysAgo: Long, steps: Int) = DailySteps("me", today.minusDays(daysAgo).toString(), steps)

    @Test fun streakCountsFromYesterdayUntilTodayIsMet() {
        val days = listOf(day(0, 3000), day(1, 9000), day(2, 8000), day(3, 7000))
        assertEquals(2, streak(days, 8000, today))
        assertEquals(3, streak(days.map { if (it.day == today.toString()) it.copy(steps = 8000) else it }, 8000, today))
        assertEquals(0, streak(emptyList(), 8000, today))
    }

    @Test fun scoreIsPercentOfGoalCapped() {
        assertEquals(78, stepqScore(6240, 8000))
        assertEquals(100, stepqScore(9450, 8000))
        assertEquals(0, stepqScore(100, 0))
    }

    @Test fun weekRunsMondayToSundayWithFutureDaysEmpty() {
        val bars = weekBars(listOf(day(0, 6240), day(5, 7000)), today)
        assertEquals(listOf(7000, 0, 0, 0, 0, 6240, null), bars)
        assertEquals(2207, weekAverage(bars))
    }

    @Test fun friendsTodayAlwaysIncludesMe() {
        val a = AvatarConfig()
        val friends = listOf(Triple("Arjun", a, 9120), Triple("Meera", a, 7480), Triple("Kabir", a, 6500))
        val rows = friendsToday("You" to a, 6240, friends)
        assertEquals(listOf("Arjun", "Meera", "You"), rows.map { it.name })
        assertEquals(4, rows.last().rank)
        assertEquals(listOf("You"), friendsToday("You" to a, 10, emptyList()).map { it.name })
    }

    @Test fun greetings() {
        assertEquals("Good morning", greeting(LocalTime.of(7, 0)))
        assertEquals("Good afternoon", greeting(LocalTime.of(13, 0)))
        assertEquals("Good evening", greeting(LocalTime.of(21, 0)))
    }

    @Test fun distanceUsesMetresUnderOneKm() {
        org.junit.Assert.assertEquals("38" to "m", com.stepq.app.ui.home.distanceLabel(0.0381))
        org.junit.Assert.assertEquals("1.2" to "km", com.stepq.app.ui.home.distanceLabel(1.24))
    }

    @Test fun phoneDaysWinOverStaleServerCopy() {
        val server = listOf(DailySteps("u", "2026-11-10", steps = 29), DailySteps("u", "2026-11-09", steps = 50))
        val local = listOf(DailySteps("u", "2026-11-10", steps = 412))
        val merged = com.stepq.app.ui.home.overlayDays(server, local)
        assertEquals(listOf("2026-11-10" to 412, "2026-11-09" to 50), merged.map { it.day to it.steps })
    }

    @Test fun activeMinutesCountAnyWalking() {
        val f = { s: Int -> com.stepq.app.ui.home.estimatedActiveMinutes(s) }
        assertEquals(0, f(0))
        assertEquals(1, f(29))
        assertEquals(1, f(100))
        assertEquals(2, f(101))
        assertEquals(80, f(8000))
    }
}
