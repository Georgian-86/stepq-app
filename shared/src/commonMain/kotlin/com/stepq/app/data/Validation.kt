package com.stepq.app.data

import com.stepq.app.data.Dates.minusYears
import kotlinx.datetime.LocalDate

private val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
private val USERNAME = Regex("^[a-z0-9._]{3,20}$")

fun isValidEmail(email: String): Boolean = EMAIL.matches(email.trim())

/** A05 Sign up: at least 8 characters with a number. */
fun isValidPassword(password: String): Boolean =
    password.length >= 8 && password.any { it.isDigit() }

/** Same rule as the app.profiles check constraint. */
fun isValidUsername(username: String): Boolean = USERNAME.matches(username)

/** "@Riya.Walks " → "riya.walks". */
fun normalizeUsername(input: String): String = input.trim().removePrefix("@").lowercase()

/** A06 "or pick one": a few usernames built from the full name. */
fun suggestUsernames(fullName: String): List<String> {
    val parts = fullName.lowercase().split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }
    if (parts.isEmpty()) return emptyList()
    val first = parts.first()
    val joined = parts.joinToString("")
    return listOf(joined, "${first}_steps", "walkwith$first")
        .map { it.take(20) }
        .filter(::isValidUsername)
        .distinct()
}

/**
 * StepQ is for 13 and older (app.check_min_age enforces it too). India's DPDP Act treats anyone
 * under [ADULT_AGE] as a child, whose data may only be processed with verifiable parental
 * consent, so 13 to 17 year olds wait for a parent to agree on stepq.in before using the app.
 */
const val MIN_AGE = 13
const val ADULT_AGE = 18

/** True when someone born on [birthdate] is under 18 on [today]. */
fun isMinor(birthdate: LocalDate?, today: LocalDate = Dates.today()): Boolean =
    birthdate != null && birthdate > today.minusYears(ADULT_AGE)

/**
 * A07 birthdate typed as DD / MM / YYYY (8 digits). Returns null unless it is a real date, in
 * the past, and the person is at least [MIN_AGE].
 */
fun parseBirthdate(digits: String, today: LocalDate = Dates.today()): LocalDate? {
    if (digits.length != 8 || !digits.all(Char::isDigit)) return null
    val date = runCatching {
        LocalDate(digits.substring(4).toInt(), digits.substring(2, 4).toInt(), digits.substring(0, 2).toInt())
    }.getOrNull() ?: return null
    if (date > today.minusYears(MIN_AGE) || date < LocalDate(1900, 1, 1)) return null
    return date
}

/** A09: "about 6.1 km" for 8,000 steps, at 0.762 m a step. */
fun stepsToKm(steps: Int): Double = steps * 0.000762
