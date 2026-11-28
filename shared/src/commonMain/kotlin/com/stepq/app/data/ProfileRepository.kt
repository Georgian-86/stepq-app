package com.stepq.app.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import com.stepq.app.data.Dates.plusDays
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** The pending goal to switch to on [today], if one is due. */
fun pendingGoalDue(p: Profile, today: LocalDate): Int? {
    val next = p.nextDailyGoal ?: return null
    val from = Dates.parseDateOrNull(p.nextGoalFrom) ?: return null
    return next.takeIf { from <= today }
}

/** What a goal change saved: today's goal, and the new one with the day it starts (or none). */
@kotlinx.serialization.Serializable
data class GoalChange(
    @kotlinx.serialization.SerialName("daily_goal") val dailyGoal: Int,
    @kotlinx.serialization.SerialName("next_daily_goal") val nextDailyGoal: Int? = null,
    @kotlinx.serialization.SerialName("next_goal_from") val nextGoalFrom: String? = null,
)

/** The change the app saves itself when Postgres can't decide: from tomorrow on the phone. */
fun goalChange(goal: Int, current: Int, today: LocalDate): GoalChange =
    if (goal == current) GoalChange(current)
    else GoalChange(current, goal, today.plusDays(1).toString())

/** The signed-in user's row in app.profiles. RLS limits writes to their own row. */
class ProfileRepository(private val client: SupabaseClient?) {

    /** For the few calls that go to stepq.in rather than Supabase. */
    private val siteHttp by lazy { HttpClient { expectSuccess = false } }

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
        // A goal change that has started takes over from today. Postgres moves it into
        // daily_goal (sync_my_goal); before that migration, the app writes it itself.
        val today = Dates.today()
        val due = pendingGoalDue(p, today) ?: return@runCatching p
        runCatching {
            c.postgrest.rpc("sync_my_goal", buildJsonObject { put("p_today", today.toString()) })
        }.recoverCatching {
            c.postgrest["profiles"].update({
                set("daily_goal", due)
                set("next_daily_goal", null as Int?)
                set("next_goal_from", null as String?)
            }) { filter { eq("id", id) } }
        }
        p.copy(dailyGoal = due, nextDailyGoal = null, nextGoalFrom = null)
    }

    suspend fun usernameAvailable(username: String): Result<Boolean> = runCatching {
        requireClient().postgrest
            .rpc("username_available", buildJsonObject { put("name", username) })
            .decodeAs<Boolean>()
    }

    suspend fun create(profile: NewProfile): Result<Unit> = runCatching {
        requireClient().postgrest["profiles"].insert(profile)
    }

    /** Logs that I agreed to (or withdrew) a purpose at a policy version, in app.consents. */
    suspend fun recordConsent(purpose: String, version: String, granted: Boolean = true): Result<Unit> = runCatching {
        requireClient().postgrest.rpc(
            "record_consent",
            buildJsonObject {
                put("p_purpose", purpose)
                put("p_version", version)
                put("p_granted", granted)
            },
        )
    }

    /**
     * True when I have agreed to the Terms and Privacy policy at [version] and not withdrawn.
     * Fails (and the caller lets me in) when offline or before the consent log exists.
     */
    suspend fun hasConsent(version: String, purpose: String = "terms_privacy"): Result<Boolean> = runCatching {
        val me = currentUserId() ?: throw IllegalStateException("Not signed in")
        requireClient().postgrest["consents"].select(io.github.jan.supabase.postgrest.query.Columns.list("id")) {
            filter {
                eq("user_id", me)
                eq("purpose", purpose)
                eq("version", version)
                exact("withdrawn_at", null)
            }
            limit(1)
        }.data.trim().let { it != "[]" && it.isNotEmpty() }
    }

    /** Everything StepQ holds about me, as JSON (app.export_my_data). */
    suspend fun exportMyData(): Result<String> = runCatching {
        requireClient().postgrest.rpc("export_my_data").data
    }

    /**
     * Where my parental consent stands: "not_needed" (18+), "none" (no parent asked yet),
     * "pending", "granted", "declined" or "withdrawn". Before the migration is live: not_needed.
     */
    suspend fun parentalConsent(): Result<String> = runCatching {
        requireClient().postgrest.rpc("my_parental_consent").decodeAs<String>()
    }

    /**
     * Asks stepq.in to email [parentEmail] a consent link. The site checks my session with
     * Supabase Auth, so only my own account can ask. Fails with the site's message.
     */
    suspend fun requestParentalConsent(parentName: String, parentEmail: String): Result<Unit> = runCatching {
        val token = requireClient().auth.currentSessionOrNull()?.accessToken ?: throw IllegalStateException("Log in again first.")
        val body = buildJsonObject {
            put("accessToken", token)
            put("parentName", parentName.trim())
            put("parentEmail", parentEmail.trim())
        }.toString()
        val response = siteHttp.post("${StepqClient.SITE_URL}/api/requestParentConsent") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        if (!response.status.isSuccess()) throw IllegalStateException(siteErrorMessage(response.bodyAsText()))
    }

    /** People I've blocked, newest first. */
    suspend fun myBlocks(): Result<List<BlockedUser>> = runCatching {
        requireClient().postgrest.rpc("my_blocks").decodeList<BlockedUser>()
    }

    suspend fun unblock(userId: String): Result<Unit> = runCatching {
        val me = currentUserId() ?: throw IllegalStateException("Not signed in")
        requireClient().postgrest["user_blocks"].delete { filter { eq("blocker", me); eq("blocked", userId) } }
    }

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
     * Postgres decides the start day (set_daily_goal) and never lets it be today; the result
     * is what it saved.
     */
    suspend fun updateGoal(goal: Int, current: Int): Result<GoalChange> = runCatching {
        val id = currentUserId() ?: throw NotConfiguredException()
        val c = requireClient()
        val today = Dates.today()
        runCatching {
            c.postgrest.rpc("set_daily_goal", buildJsonObject {
                put("p_goal", goal)
                put("p_today", today.toString())
            }).decodeList<GoalChange>().single()
        }.getOrElse {
            // Before the Nov 28 migration: save tomorrow's goal on the profile.
            c.postgrest["profiles"].update({
                if (goal == current) {
                    set("next_daily_goal", null as Int?)
                    set("next_goal_from", null as String?)
                } else {
                    set("next_daily_goal", goal)
                    set("next_goal_from", today.plusDays(1).toString())
                }
            }) { filter { eq("id", id) } }
            goalChange(goal, current, today)
        }
    }

    /** All my saved days, newest first, for the best day and total distance. */
    suspend fun myDays(): Result<List<DailySteps>> = runCatching {
        val id = currentUserId() ?: return@runCatching emptyList()
        requireClient().postgrest["daily_steps"].select {
            filter { eq("user_id", id) }
            order("day", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
        }.decodeList<DailySteps>()
    }

    /** My StepQ score; null until the Nov 20 migration is applied. Streaks are per friend, so none here. */
    suspend fun score(): Int? {
        val c = client ?: return null
        val id = currentUserId() ?: return null
        return StreakScore.scores(c.postgrest, listOf(id))?.let { it[id] ?: 0 }
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

/** The message in a stepq.in API error (`{"error":{"message":…}}`), or a generic one. */
fun siteErrorMessage(body: String): String = runCatching {
    val error = kotlinx.serialization.json.Json.parseToJsonElement(body)
        .let { (it as kotlinx.serialization.json.JsonObject)["error"] as kotlinx.serialization.json.JsonObject }
    val fields = error["details"]?.let { (it as kotlinx.serialization.json.JsonObject)["fields"] as? kotlinx.serialization.json.JsonObject }
    val first = fields?.values?.firstOrNull()?.let { (it as kotlinx.serialization.json.JsonPrimitive).content }
    first ?: (error["message"] as kotlinx.serialization.json.JsonPrimitive).content
}.getOrNull()?.takeIf { it.isNotBlank() } ?: "Couldn't send the email. Check your connection and try again."
