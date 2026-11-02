package com.stepq.app

import com.stepq.app.data.isValidEmail
import com.stepq.app.data.isValidPassword
import com.stepq.app.data.isValidUsername
import com.stepq.app.data.normalizeUsername
import com.stepq.app.data.parseBirthdate
import com.stepq.app.data.stepsToKm
import com.stepq.app.data.suggestUsernames
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
        val today = LocalDate.of(2026, 11, 2)
        assertEquals(LocalDate.of(2001, 3, 14), parseBirthdate("14032001", today))
        assertNull(parseBirthdate("31022001", today)) // no 31 Feb
        assertNull(parseBirthdate("1403200", today)) // incomplete
        assertNull(parseBirthdate("03112013", today)) // 12, under 13
        assertEquals(LocalDate.of(2013, 11, 2), parseBirthdate("02112013", today)) // 13 today
    }

    @Test fun goalDistance() {
        assertEquals(6.1, stepsToKm(8000), 0.05)
    }
}
