package com.stepq.app.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A08 avatar choices, stored as indexes into the option lists in [AvatarArt]. Fields added later
 * default to the look avatars had before they existed, so older saved avatars draw the same.
 */
@Serializable
data class AvatarConfig(
    val skin: Int = 2,
    val hair: Int = 0,
    val outfit: Int = 0,
    /** Colour of the headwear (headband or cap). Named for the headband, which came first. */
    val headband: Int = 0,
    /** Hair style, see [AvatarArt.hairStyles]: 0 short, 1 long, 2 bun, 3 ponytail, 4 curly, 5 buzz. */
    val style: Int = 0,
    /** 0 male, 1 female, -1 not picked yet (avatars saved before this existed); see [isFemale]. */
    val body: Int = -1,
    /** Top, see [AvatarArt.tops]: 0 tee, 1 hoodie, 2 jersey, 3 kurta. */
    val top: Int = 0,
    /** See [AvatarArt.headwear]: 0 headband, 1 cap, 2 none. */
    val headwear: Int = 0,
    /** See [AvatarArt.eyewear]: 0 none, 1 glasses, 2 shades. */
    val eyewear: Int = 0,
    /** See [AvatarArt.facialHair]: 0 none, 1 stubble, 2 beard, 3 moustache. */
    @SerialName("facial_hair") val facialHair: Int = 0,
    /** See [AvatarArt.extras]: 0 none, 1 earrings, 2 bindi, 3 both. */
    val extra: Int = 0,
) {
    /**
     * Whether to draw the female figure. Avatars saved before the choice existed got long hair
     * or a bun from a female gender at sign-up, so those count as female.
     */
    val isFemale: Boolean get() = if (body >= 0) body == 1 else style == 1 || style == 2

    /** The figure and hair that match a gender, keeping every other choice. */
    fun forGender(gender: String?): AvatarConfig = copy(style = defaultHairStyle(gender), body = defaultBody(gender))
}

/** The hair style that matches a gender: long for female, short for everyone else. */
fun defaultHairStyle(gender: String?): Int = if (gender == "female") 1 else 0

/** The figure that matches a gender: female, male, or not picked for other answers. */
fun defaultBody(gender: String?): Int = when (gender) {
    "female" -> 1
    "male" -> 0
    else -> -1
}

/** A fresh avatar for someone who picked [gender] at sign-up. */
fun defaultAvatar(gender: String?): AvatarConfig = AvatarConfig().forGender(gender)

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

/** Someone I've blocked in the community (app.my_blocks). */
@Serializable
data class BlockedUser(
    @SerialName("user_id") val userId: String,
    @SerialName("display_name") val displayName: String,
    val username: String,
)
