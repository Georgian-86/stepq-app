package com.stepq.app.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A08 avatar choices, stored as indexes into the palettes in AvatarPalette. */
@Serializable
data class AvatarConfig(
    val skin: Int = 2,
    val hair: Int = 0,
    val outfit: Int = 0,
    val headband: Int = 0,
    /** Hair style: 0 short, 1 long, 2 bun. Picked from gender at sign-up, changeable after. */
    val style: Int = 0,
)

/** The hair style that matches a gender: long for female, short for everyone else. */
fun defaultHairStyle(gender: String?): Int = if (gender == "female") 1 else 0

enum class Gender(val value: String, val label: String) {
    Female("female", "Female"),
    Male("male", "Male"),
    NonBinary("non_binary", "Non-binary"),
    PreferNot("prefer_not", "Prefer not to say"),
}

/** A row of app.profiles, in the columns the app may write. */
@Serializable
data class NewProfile(
    val id: String,
    @SerialName("display_name") val displayName: String,
    val username: String,
    val gender: String? = null,
    /** ISO date, yyyy-MM-dd. */
    val birthdate: String? = null,
    val avatar: AvatarConfig = AvatarConfig(),
    @SerialName("daily_goal") val dailyGoal: Int = 8000,
)

@Serializable
data class Profile(
    val id: String,
    @SerialName("display_name") val displayName: String,
    val username: String,
    val gender: String? = null,
    val birthdate: String? = null,
    val avatar: AvatarConfig = AvatarConfig(),
    @SerialName("daily_goal") val dailyGoal: Int = 8000,
    /** A goal change waiting for the next day: today's goal never moves mid-day. */
    @SerialName("next_daily_goal") val nextDailyGoal: Int? = null,
    /** ISO date the pending goal starts, yyyy-MM-dd. */
    @SerialName("next_goal_from") val nextGoalFrom: String? = null,
    val plan: String = "free",
    /** ISO country code for the country rankings, e.g. "IN". */
    val country: String? = null,
)
