package com.stepq.app.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

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
        c.postgrest["profiles"].select { filter { eq("id", id) } }.decodeSingleOrNull<Profile>()
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
    suspend fun updateGoal(goal: Int): Result<Unit> = runCatching {
        val id = currentUserId() ?: throw NotConfiguredException()
        requireClient().postgrest["profiles"].update({ set("daily_goal", goal) }) { filter { eq("id", id) } }
    }

    /** All my saved days, newest first, for the streak, best day and total distance. */
    suspend fun myDays(): Result<List<DailySteps>> = runCatching {
        val id = currentUserId() ?: return@runCatching emptyList()
        requireClient().postgrest["daily_steps"].select {
            filter { eq("user_id", id) }
            order("day", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
        }.decodeList<DailySteps>()
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
