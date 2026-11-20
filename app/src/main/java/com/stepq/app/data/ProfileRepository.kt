package com.stepq.app.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** The pending goal to switch to on [today], if one is due. */
fun pendingGoalDue(p: Profile, today: java.time.LocalDate): Int? {
    val next = p.nextDailyGoal ?: return null
    val from = p.nextGoalFrom?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() } ?: return null
    return next.takeIf { !from.isAfter(today) }
}

/** The signed-in user's row in app.profiles. RLS limits writes to their own row. */
class ProfileRepository(private val client: SupabaseClient?) {

    private fun requireClient() = client ?: throw NotConfiguredException()

    fun currentUserId(): String? = client?.auth?.currentUserOrNull()?.id

    /** The name Google gave us, to prefill A06 for people who signed up with Google. */
    fun currentUserFullName(): String? = client?.auth?.currentUserOrNull()?.userMetadata
        ?.let { it["full_name"] ?: it["name"] }
        ?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
        ?.takeIf { it.isNotBlank() }

    suspend fun mine(): Result<Profile?> = runCatching {
        val c = requireClient()
        val id = currentUserId() ?: return@runCatching null
        val p = c.postgrest["profiles"].select { filter { eq("id", id) } }.decodeSingleOrNull<Profile>()
            ?: return@runCatching null
        // A goal set yesterday (or earlier) takes over from today.
        val due = pendingGoalDue(p, java.time.LocalDate.now())
        if (due != null) {
            runCatching {
                c.postgrest["profiles"].update({
                    set("daily_goal", due)
                    set("next_daily_goal", null as Int?)
                    set("next_goal_from", null as String?)
                }) { filter { eq("id", id) } }
            }.fold(onSuccess = { p.copy(dailyGoal = due, nextDailyGoal = null, nextGoalFrom = null) }, onFailure = { p })
        } else {
            p
        }
    }

    suspend fun usernameAvailable(username: String): Result<Boolean> = runCatching {
        requireClient().postgrest
            .rpc("username_available", buildJsonObject { put("name", username) })
            .decodeAs<Boolean>()
    }

    suspend fun create(profile: NewProfile): Result<Unit> = runCatching {
        requireClient().postgrest["profiles"].insert(profile)
    }

    /** A26: change the daily goal. The check constraint keeps it between 1,000 and 100,000. */
    /** A26 Edit profile. Birthdate isn't here: it's locked once set. */
    suspend fun updateProfile(displayName: String, username: String, avatar: AvatarConfig, gender: String?): Result<Unit> = runCatching {
        val id = currentUserId() ?: throw NotConfiguredException()
        requireClient().postgrest["profiles"].update({
            set("display_name", displayName.trim())
            set("username", username)
            set("avatar", kotlinx.serialization.json.Json.encodeToJsonElement(AvatarConfig.serializer(), avatar))
            set("gender", gender)
        }) { filter { eq("id", id) } }
    }

    /**
     * A26: change the daily goal from tomorrow, so today's score and streak are judged on the
     * goal the day started with. Picking today's goal again cancels a pending change.
     */
    suspend fun updateGoal(goal: Int, current: Int): Result<Unit> = runCatching {
        val id = currentUserId() ?: throw NotConfiguredException()
        val tomorrow = java.time.LocalDate.now().plusDays(1).toString()
        requireClient().postgrest["profiles"].update({
            if (goal == current) {
                set("next_daily_goal", null as Int?)
                set("next_goal_from", null as String?)
            } else {
                set("next_daily_goal", goal)
                set("next_goal_from", tomorrow)
            }
        }) { filter { eq("id", id) } }
    }

    /** All my saved days, newest first, for the streak, best day and total distance. */
    suspend fun myDays(): Result<List<DailySteps>> = runCatching {
        val id = currentUserId() ?: return@runCatching emptyList()
        requireClient().postgrest["daily_steps"].select {
            filter { eq("user_id", id) }
            order("day", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
        }.decodeList<DailySteps>()
    }

    /** My share streak and StepQ score; nulls until the Nov 20 migration is applied. */
    suspend fun streakAndScore(today: java.time.LocalDate): Pair<Int?, Int?> {
        val c = client ?: return null to null
        val id = currentUserId() ?: return null to null
        val db = c.postgrest
        return StreakScore.streak(db, today)?.streak to StreakScore.scores(db, listOf(id))?.let { it[id] ?: 0 }
    }

    /** A27: is the user on the StepQ Pro waitlist? */
    suspend fun onProWaitlist(): Result<Boolean> = runCatching {
        val id = currentUserId() ?: return@runCatching false
        requireClient().postgrest["pro_waitlist"].select { filter { eq("user_id", id) } }
            .decodeList<kotlinx.serialization.json.JsonObject>().isNotEmpty()
    }

    suspend fun joinProWaitlist(): Result<Unit> = runCatching {
        val id = currentUserId() ?: throw NotConfiguredException()
        requireClient().postgrest["pro_waitlist"].upsert(buildJsonObject { put("user_id", id) }) {
            onConflict = "user_id"
            ignoreDuplicates = true
        }
    }
}
