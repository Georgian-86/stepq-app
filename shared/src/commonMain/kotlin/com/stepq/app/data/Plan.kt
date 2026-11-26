package com.stepq.app.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.storage.storage
import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** One row of app.plan_features as app.my_plan() returns it. null values mean unlimited. */
@Serializable
data class PlanFeature(
    val key: String,
    val label: String,
    val kind: String,
    val unit: String? = null,
    val free: Int? = null,
    val pro: Int? = null,
    /** My plan's value. */
    val mine: Int? = null,
) {
    val isSwitch get() = kind == "switch"
}

@Serializable
data class PlanUsage(
    @SerialName("community_posts") val communityPosts: Int = 0,
    @SerialName("streak_restores") val streakRestores: Int = 0,
    @SerialName("water_reminders") val waterReminders: Int = 0,
)

/** app.my_plan(): what my plan includes and what I've used this day / month. */
@Serializable
data class MyPlan(
    val plan: String = "free",
    @SerialName("is_pro") val isPro: Boolean = false,
    @SerialName("pro_since") val proSince: String? = null,
    @SerialName("qrun_host") val qrunHost: Boolean = false,
    @SerialName("qrun_host_requested_at") val qrunHostRequestedAt: String? = null,
    val theme: String = "leaf",
    @SerialName("photo_path") val photoPath: String? = null,
    @SerialName("bff_id") val bffId: String? = null,
    val features: List<PlanFeature> = emptyList(),
    val usage: PlanUsage = PlanUsage(),
    @SerialName("month_resets_at") val monthResetsAt: String? = null,
) {
    fun feature(key: String): PlanFeature? = features.firstOrNull { it.key == key }

    /** Is the switch on (or the limit above 0 / unlimited) on my plan? Unknown keys are off. */
    fun has(key: String): Boolean = feature(key)?.let { it.mine == null || it.mine > 0 } ?: false

    /** My limit for [key]; null = unlimited. */
    fun limit(key: String): Int? = feature(key)?.mine

    /** How many I have left of [key] this period, or null when unlimited. */
    fun left(key: String, used: Int): Int? = limit(key)?.let { (it - used).coerceAtLeast(0) }

    val postsLeft get() = left(F.COMMUNITY_POSTS, usage.communityPosts)
    val restoresLeft get() = left(F.STREAK_RESTORES, usage.streakRestores)
    val remindersLeft get() = left(F.WATER_REMINDERS, usage.waterReminders)

    /** Can I post a QRun right now? Pro (plan_qrun) and an admin's OK. */
    val canPostQRun get() = has(F.PLAN_QRUN) && qrunHost

    /** The theme to show: mine if my plan allows themes, otherwise the default. */
    val activeTheme get() = if (has(F.CHANGE_THEME)) theme else "leaf"

    /** Feature keys, as in app.plan_features. */
    object F {
        const val UPLOAD_PHOTO = "upload_photo"
        const val CHANGE_THEME = "change_theme"
        const val PLAN_QRUN = "plan_qrun"
        const val STREAK_RESTORES = "streak_restores"
        const val REWARD_WEEKLY = "reward_weekly"
        const val REWARD_MONTHLY = "reward_monthly"
        const val REWARD_YEARLY = "reward_yearly"
        const val ALL_CHALLENGES = "all_challenges"
        const val ANALYSIS_DAYS = "analysis_days"
        const val WATER_REMINDERS = "water_reminders"
        const val PROFILE_BADGE = "profile_badge"
        const val PIN_BFF = "pin_bff"
        const val COMMUNITY_POSTS = "community_posts"
        const val POST_APPROVAL = "post_approval"
        const val UPGRADE_POPUP_DAYS = "upgrade_popup_days"
    }

    companion object {
        /** What Free gets when app.my_plan() isn't there yet (before the Nov 26 migration). */
        val FREE_DEFAULTS = MyPlan(
            features = listOf(
                PlanFeature(F.UPLOAD_PHOTO, "Upload a profile photo", "switch", free = 0, pro = 1, mine = 0),
                PlanFeature(F.CHANGE_THEME, "Change the app theme", "switch", free = 0, pro = 1, mine = 0),
                PlanFeature(F.PLAN_QRUN, "Plan a QRun (once an admin approves)", "switch", free = 0, pro = 1, mine = 0),
                PlanFeature(F.STREAK_RESTORES, "Streak restores", "limit", "a month", 2, 8, 2),
                PlanFeature(F.REWARD_WEEKLY, "Weekly reward eligible", "switch", free = 1, pro = 0, mine = 1),
                PlanFeature(F.REWARD_MONTHLY, "Monthly reward eligible", "switch", free = 0, pro = 1, mine = 0),
                PlanFeature(F.REWARD_YEARLY, "Yearly reward eligible", "switch", free = 0, pro = 1, mine = 0),
                PlanFeature(F.ALL_CHALLENGES, "Join every challenge (incl. Pro-only)", "switch", free = 0, pro = 1, mine = 0),
                PlanFeature(F.ANALYSIS_DAYS, "Step history in Insights", "limit", "days", 365, null, 365),
                PlanFeature(F.WATER_REMINDERS, "Water reminders", "limit", "a day", 3, null, 3),
                PlanFeature(F.PROFILE_BADGE, "Pro badge on profile", "switch", free = 0, pro = 1, mine = 0),
                PlanFeature(F.PIN_BFF, "Pin one BFF", "switch", free = 0, pro = 1, mine = 0),
                PlanFeature(F.COMMUNITY_POSTS, "Community posts", "limit", "a month", 0, null, 0),
                PlanFeature(F.POST_APPROVAL, "New Community posts wait for admin approval", "switch", free = 1, pro = 0, mine = 1),
            ),
        )
    }
}

/** What others see of someone's plan: the Pro badge and, for friends, their photo. */
@Serializable
data class PlanBadge(val id: String, val pro: Boolean = false, @SerialName("photo_path") val photoPath: String? = null)

/** A row of app.lost_streaks(): a streak with a friend that ended yesterday. */
@Serializable
data class LostStreak(@SerialName("friend_id") val friendId: String, val streak: Int)

/** A row of app.step_history(). */
@Serializable
data class HistoryDay(val day: String, val steps: Int = 0, val goal: Int? = null, @SerialName("distance_m") val distanceM: Int = 0)

/** A Postgres "you've hit your plan's limit" error: the feature key from its hint, if any. */
fun planLimitKey(e: Throwable): String? {
    val text = listOfNotNull((e as? io.github.jan.supabase.postgrest.exception.PostgrestRestException)?.hint, e.message).joinToString("\n")
    return Regex("plan_limit:([a-z_]+)").find(text)?.groupValues?.get(1)
}


/**
 * StepQ Pro on the server: my plan (limits, usage, theme), badges and photos, and the Pro-only
 * actions. Postgres enforces every limit; the apps only mirror it to explain them up front.
 * Both apps keep their own in-memory state around this (Android: `Pro`, iOS: `ProStore`).
 */
class PlanRepository(private val client: SupabaseClient?) {
    private fun c() = client ?: throw NotConfiguredException()

    fun myId(): String? = client?.auth?.currentUserOrNull()?.id

    /** My plan; the Free defaults before the Nov 26 migration is applied. Null when signed out. */
    suspend fun myPlan(): MyPlan? {
        val c = client ?: return null
        if (myId() == null) return null
        return runCatching { c.postgrest.rpc("my_plan").decodeAs<MyPlan?>() }.getOrNull() ?: MyPlan.FREE_DEFAULTS
    }

    suspend fun badges(ids: List<String>): List<PlanBadge> {
        if (ids.isEmpty()) return emptyList()
        return c().postgrest.rpc(
            "people_badges",
            buildJsonObject { put("ids", JsonArray(ids.map { JsonPrimitive(it) })) },
        ).decodeList<PlanBadge>()
    }

    /** A profile photo's bytes from the private bucket (RLS: only people who can see the profile). */
    suspend fun photoBytes(path: String): ByteArray = c().storage.from(BUCKET).downloadAuthenticated(path)

    /** Uploads an already shrunk JPEG as my new photo and removes the old one ([oldPath]). */
    suspend fun uploadPhoto(jpeg: ByteArray, oldPath: String?): Result<Unit> = runCatching {
        val c = c()
        val me = myId() ?: throw NotConfiguredException()
        val path = "$me/photo-${nowMillis()}.jpg"
        c.storage.from(BUCKET).upload(path, jpeg) { upsert = true }
        c.postgrest["profiles"].update({ set("photo_path", path) }) { filter { eq("id", me) } }
        oldPath?.let { runCatching { c.storage.from(BUCKET).delete(it) } }
    }

    suspend fun removePhoto(oldPath: String?): Result<Unit> = runCatching {
        val c = c()
        val me = myId() ?: throw NotConfiguredException()
        c.postgrest["profiles"].update({ set("photo_path", null as String?) }) { filter { eq("id", me) } }
        oldPath?.let { runCatching { c.storage.from(BUCKET).delete(it) } }
    }

    /**
     * Before deleting the account: removes every file in my folder of the photo bucket,
     * including any left over from an earlier failed delete. Best effort.
     */
    suspend fun deleteAllMyPhotos() {
        val c = client ?: return
        val me = myId() ?: return
        runCatching {
            val bucket = c.storage.from(BUCKET)
            val paths = bucket.list(me).map { "$me/${it.name}" }
            if (paths.isNotEmpty()) bucket.delete(paths)
        }.onFailure { logWarn("StepqPro", "Couldn't remove profile photos", it) }
    }

    suspend fun setTheme(key: String): Result<Unit> = runCatching {
        val me = myId() ?: throw NotConfiguredException()
        c().postgrest["profiles"].update({ set("theme", key) }) { filter { eq("id", me) } }
    }

    suspend fun setBff(friendId: String?): Result<Unit> = runCatching {
        val me = myId() ?: throw NotConfiguredException()
        c().postgrest["profiles"].update({ set("bff_id", friendId) }) { filter { eq("id", me) } }
    }

    suspend fun requestQRunHost(): Result<Unit> = runCatching {
        c().postgrest.rpc("request_qrun_host")
    }

    suspend fun lostStreaks(today: LocalDate): List<LostStreak> = runCatching {
        (client ?: return emptyList()).postgrest
            .rpc("lost_streaks", buildJsonObject { put("p_today", today.toString()) })
            .decodeList<LostStreak>()
    }.getOrDefault(emptyList())

    /** Restores yesterday's streak with [friendId]. Returns the restores left (null = unlimited). */
    suspend fun restoreStreak(friendId: String, today: LocalDate): Result<Int?> = runCatching {
        c().postgrest.rpc(
            "restore_streak",
            buildJsonObject {
                put("p_friend", friendId)
                put("p_today", today.toString())
            },
        ).decodeAs<Int?>()
    }

    /** My steps for the last [days] days, capped by my plan on the server. */
    suspend fun history(days: Int): Result<List<HistoryDay>> = runCatching {
        c().postgrest.rpc("step_history", buildJsonObject { put("p_days", days) }).decodeList<HistoryDay>()
    }

    companion object {
        const val BUCKET = "avatars"
    }
}
