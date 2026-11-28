package com.stepq.app

import com.stepq.app.data.GoalChange
import com.stepq.app.data.goalChange
import com.stepq.app.data.goalUpdatedCopy
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GoalUpdatedCopyTest {
    private val today = LocalDate(2026, 11, 28)

    @Test fun newGoalStartsTomorrow() {
        val copy = goalUpdatedCopy(GoalChange(8000, 10000, "2026-11-29"), today)
        assertEquals("Step goal updated", copy.title)
        assertTrue(copy.body.startsWith("Your new goal of 10,000 steps starts tomorrow."))
        assertTrue("Today's goal stays at 8,000 steps" in copy.body)
        assertEquals("Tomorrow", copy.startsLabel)
    }

    @Test fun laterStartDayIsNamed() {
        val copy = goalUpdatedCopy(GoalChange(8000, 6000, "2026-11-30"), today)
        assertTrue(copy.body.startsWith("Your new goal of 6,000 steps starts on Mon 30 Nov."))
        assertEquals("Mon 30 Nov", copy.startsLabel)
    }

    @Test fun keepingTodaysGoal() {
        val copy = goalUpdatedCopy(GoalChange(8000), today)
        assertEquals("Daily goal unchanged", copy.title)
        assertNull(copy.startsLabel)
    }

    @Test fun groupsSteps() {
        assertEquals("100,000", com.stepq.app.data.groupedSteps(100000))
        assertEquals("8,000", com.stepq.app.data.groupedSteps(8000))
        assertEquals("500", com.stepq.app.data.groupedSteps(500))
    }

    @Test fun localFallbackIsAlwaysTomorrow() {
        val k = LocalDate(2026, 11, 28)
        assertEquals(GoalChange(8000, 5000, "2026-11-29"), goalChange(5000, 8000, k))
        assertEquals(GoalChange(8000), goalChange(8000, 8000, k))
    }
}
