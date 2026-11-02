package com.stepq.app

import com.stepq.app.data.isValidEmail
import com.stepq.app.data.isValidPassword
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
}
