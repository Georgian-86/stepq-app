package com.stepq.app

import com.stepq.app.health.HealthWindows
import com.stepq.app.health.StepAccounts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class StepAccountsTest {
    @Test fun firstAccountOnThePhoneKeepsEverything() {
        assertEquals(mapOf("a" to 0L), StepAccounts.claim(emptyMap(), "a", 123L))
    }

    @Test fun laterAccountsCountFromWhenTheyFirstSignedIn() {
        val known = mapOf("a" to 0L)
        assertEquals(mapOf("a" to 0L, "b" to 500L), StepAccounts.claim(known, "b", 500L))
        // Signing in again doesn't move the start.
        assertEquals(known, StepAccounts.claim(known, "a", 900L))
    }

    private val today = LocalDate.of(2026, 11, 22)
    private val utc = ZoneOffset.UTC

    @Test fun firstAccountReadsTheWholeWeek() {
        val w = HealthWindows.of(today, 7, 0L, utc)
        assertEquals(LocalDate.of(2026, 11, 16).atStartOfDay(), w.wholeDaysFrom)
        assertNull(w.partial)
    }

    @Test fun accountThatStartedTodayOnlyReadsFromThatMoment() {
        val since = today.atTime(14, 30).toInstant(utc).toEpochMilli()
        val w = HealthWindows.of(today, 7, since, utc)
        assertNull(w.wholeDaysFrom)
        assertEquals(today.atTime(14, 30) to today.plusDays(1).atStartOfDay(), w.partial)
    }

    @Test fun accountThatStartedEarlierThisWeekReadsFullDaysAfterThat() {
        val since = today.minusDays(2).atTime(9, 0).toInstant(utc).toEpochMilli()
        val w = HealthWindows.of(today, 7, since, utc)
        assertEquals(today.minusDays(1).atStartOfDay(), w.wholeDaysFrom)
        assertEquals(today.minusDays(2).atTime(9, 0), w.partial?.first)
    }
}
