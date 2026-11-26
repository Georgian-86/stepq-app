package com.stepq.app.data

import kotlinx.datetime.LocalDate
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Shared rules: these run on the JVM for Android and natively for iOS. */
class ValidationTest {
    @Test fun emails() {
        assertTrue(isValidEmail("riya.sharma@gmail.com"))
        assertTrue(isValidEmail("  riya@stepq.in "))
        assertFalse(isValidEmail("riya"))
        assertFalse(isValidEmail("riya@gmail"))
        assertFalse(isValidEmail("ri ya@gmail.com"))
    }

    @Test fun passwords() {
        assertTrue(isValidPassword("walking8"))
        assertFalse(isValidPassword("walking"))
        assertFalse(isValidPassword("short1"))
    }

    @Test fun usernames() {
        assertEquals("riya.walks", normalizeUsername(" @Riya.Walks "))
        assertTrue(isValidUsername("riya_steps"))
        assertFalse(isValidUsername("ri"))
        assertFalse(isValidUsername("riya walks"))
        assertFalse(isValidUsername("a".repeat(21)))
        assertEquals(listOf("riyasharma", "riya_steps", "walkwithriya"), suggestUsernames("Riya Sharma"))
        assertEquals(emptyList<String>(), suggestUsernames("  "))
    }

    @Test fun birthdates() {
        val today = LocalDate(2026, 11, 2)
        assertEquals(LocalDate(2001, 3, 14), parseBirthdate("14032001", today))
        assertNull(parseBirthdate("31022001", today)) // no 31 Feb
        assertNull(parseBirthdate("1403200", today)) // incomplete
        assertNull(parseBirthdate("03112013", today)) // 12, under 13
        assertEquals(LocalDate(2013, 11, 2), parseBirthdate("02112013", today)) // 13 today
    }

    @Test fun siteErrors() {
        assertEquals("Use your parent's email, not your own.", siteErrorMessage("""{"error":{"code":"invalid-argument","message":"x","details":{"fields":{"parentEmail":"Use your parent's email, not your own."}}}}"""))
        assertEquals("Too many tries.", siteErrorMessage("""{"error":{"code":"resource-exhausted","message":"Too many tries."}}"""))
        assertEquals("Couldn't send the email. Check your connection and try again.", siteErrorMessage("<html>"))
    }

    @Test fun minors() {
        val today = LocalDate(2026, 11, 2)
        assertTrue(isMinor(LocalDate(2008, 11, 3), today)) // 17
        assertFalse(isMinor(LocalDate(2008, 11, 2), today)) // 18 today
        assertFalse(isMinor(null, today))
    }

    @Test fun goalDistance() {
        assertTrue(abs(stepsToKm(8000) - 6.1) < 0.05)
    }
}
