package com.stepq.app

import com.stepq.app.data.PasswordRecovery
import com.stepq.app.push.InboxReminder
import com.stepq.app.push.inboxMessage
import com.stepq.app.ui.profile.authErrorMessage
import com.stepq.app.ui.profile.editProfileProblem
import com.stepq.app.ui.profile.newPasswordProblem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountLogicTest {
    @Test fun editProfileChecks() {
        assertNull(editProfileProblem("Riya", "riya.walks"))
        assertEquals("Enter your name.", editProfileProblem("  ", "riya.walks"))
        assertTrue(editProfileProblem("Riya", "ri")!!.startsWith("Usernames"))
    }

    @Test fun newPasswordChecks() {
        assertNull(newPasswordProblem("walking123", ""))
        assertNull(newPasswordProblem("walking123", "walking123"))
        assertEquals("The passwords don't match.", newPasswordProblem("walking123", "walking12"))
        assertEquals("Use at least 8 characters with a number.", newPasswordProblem("walking", "walking"))
    }

    @Test fun friendlyAuthErrors() {
        assertEquals("Pick a password you haven't used here before.", authErrorMessage(Exception("same_password: New password should be different from the old password.")))
        assertEquals("That email already has a StepQ account.", authErrorMessage(Exception("email_exists")))
        assertTrue(authErrorMessage(Exception("timeout")).startsWith("Something went wrong"))
    }

    @Test fun recoveryLinks() {
        assertTrue(PasswordRecovery.isRecovery("stepq://auth-callback#access_token=x&type=recovery", null))
        assertTrue(PasswordRecovery.isRecovery("stepq://auth-callback?code=abc", "recovery"))
        assertFalse(PasswordRecovery.isRecovery("stepq://auth-callback#access_token=x&type=signup", null))
    }

    @Test fun inboxText() {
        assertNull(inboxMessage(emptyList()))
        val r = { n: String -> InboxReminder(1, n, "2026-11-16T10:00:00+00:00") }
        assertEquals("Arjun reminded you to drink water", inboxMessage(listOf(r("Arjun"), r("Arjun")))!!.first)
        assertEquals("Arjun and Meera reminded you to drink water", inboxMessage(listOf(r("Arjun"), r("Meera")))!!.first)
        assertEquals("Arjun and 2 friends reminded you to drink water", inboxMessage(listOf(r("Arjun"), r("Meera"), r("Kabir")))!!.first)
    }
}
