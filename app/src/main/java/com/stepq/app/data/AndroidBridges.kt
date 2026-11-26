package com.stepq.app.data

import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toKotlinLocalDate
import kotlin.time.toJavaInstant
import java.time.Instant as JInstant
import java.time.LocalDate as JLocalDate

/*
 * The data layer lives in the shared Kotlin Multiplatform module, with kotlinx-datetime dates.
 * The Android screens still use java.time; these convert at the edge.
 */

/** java.time view of a shared date. */
val kotlinx.datetime.LocalDate.java: JLocalDate get() = toJavaLocalDate()

/** Shared (kotlinx-datetime) view of a java.time date. */
val JLocalDate.kx: kotlinx.datetime.LocalDate get() = toKotlinLocalDate()

/** java.time view of a shared instant. */
val kotlin.time.Instant.java: JInstant get() = toJavaInstant()

/** A Postgres timestamp as java.time. */
fun parseJavaInstant(s: String): JInstant = Dates.parseInstant(s).toJavaInstant()

/** A07 birthdate for the Android screens. */
fun parseBirthdateJava(digits: String, today: JLocalDate = JLocalDate.now()): JLocalDate? =
    parseBirthdate(digits, today.kx)?.java

/** India time as a java.time zone, for the Android screens (shared code uses Dates.IST). */
val ANDROID_IST: java.time.ZoneId = java.time.ZoneId.of("Asia/Kolkata")
