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
)

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
    val plan: String = "free",
)
