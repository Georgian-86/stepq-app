package com.stepq.app.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A row of app.daily_steps. `day` is an ISO date in the phone's local time zone. */
@Serializable
data class DailySteps(
    @SerialName("user_id") val userId: String,
    val day: String,
    val steps: Int = 0,
    @SerialName("distance_m") val distanceM: Int = 0,
    @SerialName("active_minutes") val activeMinutes: Int = 0,
    val calories: Int = 0,
)

@Serializable
data class Friendship(
    val requester: String,
    val addressee: String,
    val status: String,
)

/** Public fields of someone the user can see (themselves, friends, group mates). */
@Serializable
data class PersonSummary(
    val id: String,
    @SerialName("display_name") val displayName: String,
    val username: String,
    val avatar: AvatarConfig = AvatarConfig(),
)
