package com.stepq.app.data

private val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

fun isValidEmail(email: String): Boolean = EMAIL.matches(email.trim())

/** A05 Sign up: at least 8 characters with a number, as on the sign-up screen. */
fun isValidPassword(password: String): Boolean =
    password.length >= 8 && password.any { it.isDigit() }
