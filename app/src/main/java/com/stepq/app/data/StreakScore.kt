package com.stepq.app.data

import io.github.jan.supabase.postgrest.Postgrest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.LocalDate

/** A row of app.friend_streaks(): days in a row I shared my steps with one friend. */
@Serializable
data class FriendStreak(
    @SerialName("friend_id") val friendId: String,
    val streak: Int = 0,
    @SerialName("shared_today") val sharedToday: Boolean = false,
)

@Serializable
private data class UserScore(@SerialName("user_id") val userId: String, val score: Int = 0)

/**
 * Streaks and StepQ score:
 * - Streaks are per friend (Nov 24 rule): days in a row on which I shared my step count with
 *   that friend. There is no overall streak; several can be going on the same day.
 * - StepQ score: one point for every day I reached that day's goal.
 * Both live in Postgres (migrations 20261120000000 and 20261125000000); calls return null
 * until they're applied.
 */
object StreakScore {
    /** My streak with each friend, by friend id. */
    suspend fun friendStreaks(db: Postgrest, today: LocalDate): Map<String, FriendStreak>? = runCatching {
        db.rpc("friend_streaks", buildJsonObject { put("p_today", today.toString()) })
            .decodeList<FriendStreak>().associateBy { it.friendId }
    }.getOrNull()

    /** StepQ scores by user id, for people I can see (me, friends, group mates). */
    suspend fun scores(db: Postgrest, ids: Collection<String>): Map<String, Int>? = runCatching {
        if (ids.isEmpty()) return@runCatching emptyMap()
        db.rpc("stepq_scores", buildJsonObject { put("ids", JsonArray(ids.map { JsonPrimitive(it) })) })
            .decodeList<UserScore>().associate { it.userId to it.score }
    }.getOrNull()

    /** Shares [steps] for [day] with [friendIds]. Returns how many friends got it. */
    suspend fun share(db: Postgrest, friendIds: Collection<String>, steps: Int, day: LocalDate): Int =
        db.rpc(
            "share_steps",
            buildJsonObject {
                put("friends", JsonArray(friendIds.map { JsonPrimitive(it) }))
                put("steps", steps)
                put("p_day", day.toString())
            },
        ).decodeAs<Int>()
}

/** Score fallback before the migration: days at or above the current goal. */
fun goalDays(days: List<DailySteps>, goal: Int): Int = days.count { it.steps >= goal }
