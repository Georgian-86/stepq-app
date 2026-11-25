package com.stepq.app.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.ByteArrayOutputStream
import java.time.LocalDate

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
 * StepQ Pro, app-wide: my plan (limits, usage, theme), everyone's Pro badges and photos, and the
 * Pro-only actions. Postgres enforces every limit; this only mirrors it so screens can explain
 * them before someone runs into one.
 */
object Pro {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client get() = Supabase.client

    private val _plan = MutableStateFlow<MyPlan?>(null)
    /** null until loaded (or signed out). */
    val plan: StateFlow<MyPlan?> = _plan.asStateFlow()

    /** Badges by user id, filled in as screens ask for them. */
    val badges = mutableStateMapOf<String, PlanBadge>()
    private val wanted = mutableSetOf<String>()
    private val asked = mutableSetOf<String>()
    private val lock = Mutex()
    private var flushing = false

    const val BUCKET = "avatars"

    fun myId(): String? = client?.auth?.currentUserOrNull()?.id

    /** Reloads my plan. Falls back to the Free defaults before the migration is applied. */
    suspend fun refresh(): MyPlan? {
        val c = client ?: return null
        if (myId() == null) {
            _plan.value = null
            return null
        }
        val loaded = runCatching { c.postgrest.rpc("my_plan").decodeAs<MyPlan?>() }
            .getOrElse { MyPlan.FREE_DEFAULTS }
            ?: MyPlan.FREE_DEFAULTS
        _plan.value = loaded
        com.stepq.app.ui.theme.StepqColors.apply(loaded.activeTheme)
        myId()?.let { id ->
            badges[id] = PlanBadge(id, loaded.isPro && loaded.has(MyPlan.F.PROFILE_BADGE), loaded.photoPath?.takeIf { loaded.has(MyPlan.F.UPLOAD_PHOTO) })
        }
        return loaded
    }

    fun refreshAsync() = scope.launch { refresh() }

    /** Screenshot tests and previews: show [plan] and these badges without a server. */
    @androidx.annotation.VisibleForTesting
    fun preview(plan: MyPlan?, badges: Map<String, PlanBadge> = emptyMap()) {
        _plan.value = plan
        this.badges.clear()
        this.badges.putAll(badges)
        asked.clear()
        asked.addAll(badges.keys)
    }

    /** Forget everything on sign-out. */
    fun clear() {
        _plan.value = null
        badges.clear()
        asked.clear()
        photos.evictAll()
        com.stepq.app.ui.theme.StepqColors.apply("leaf")
    }

    /** Ask for [id]'s badge; requests are batched. */
    fun want(id: String) {
        if (id.isBlank() || id in asked || client == null) return
        scope.launch {
            lock.withLock {
                if (id in asked) return@launch
                wanted += id
                if (flushing) return@launch
                flushing = true
            }
            delay(60)
            val ids = lock.withLock {
                val now = wanted.toList()
                wanted.clear()
                asked += now
                flushing = false
                now
            }
            if (ids.isEmpty()) return@launch
            runCatching {
                client!!.postgrest.rpc(
                    "people_badges",
                    buildJsonObject { put("ids", JsonArray(ids.map { JsonPrimitive(it) })) },
                ).decodeList<PlanBadge>()
            }.onSuccess { list -> withContext(Dispatchers.Main) { list.forEach { badges[it.id] = it } } }
                .onFailure { lock.withLock { asked -= ids.toSet() } }
        }
    }

    /** Forget [id]'s badge so the next look fetches it again (after a photo change). */
    fun forget(id: String) {
        asked -= id
        badges.remove(id)
    }

    // ---------------------------------------------------------------- Photos
    private val photos = LruCache<String, ImageBitmap>(40)

    /** A profile photo from the private bucket (RLS: only people who can see the profile). */
    suspend fun photo(path: String): ImageBitmap? {
        photos.get(path)?.let { return it }
        val c = client ?: return null
        return withContext(Dispatchers.IO) {
            runCatching {
                val bytes = c.storage.from(BUCKET).downloadAuthenticated(path)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            }.getOrNull()?.also { photos.put(path, it) }
        }
    }

    /** Uploads a new profile photo (shrunk to 512 px JPEG) and puts it on my profile. */
    suspend fun uploadPhoto(raw: ByteArray): Result<Unit> = runCatching {
        val c = client ?: throw NotConfiguredException()
        val me = myId() ?: throw NotConfiguredException()
        val jpeg = withContext(Dispatchers.Default) { shrink(raw) }
        val path = "$me/photo-${System.currentTimeMillis()}.jpg"
        c.storage.from(BUCKET).upload(path, jpeg) { upsert = true }
        val old = _plan.value?.photoPath
        c.postgrest["profiles"].update({ set("photo_path", path) }) { filter { eq("id", me) } }
        old?.let { runCatching { c.storage.from(BUCKET).delete(it) } }
        forget(me)
        refresh()
    }

    suspend fun removePhoto(): Result<Unit> = runCatching {
        val c = client ?: throw NotConfiguredException()
        val me = myId() ?: throw NotConfiguredException()
        val old = _plan.value?.photoPath
        c.postgrest["profiles"].update({ set("photo_path", null as String?) }) { filter { eq("id", me) } }
        old?.let { runCatching { c.storage.from(BUCKET).delete(it) } }
        forget(me)
        refresh()
    }

    /**
     * Before deleting the account: removes every file in my folder of the photo bucket,
     * including any left over from an earlier failed delete. Best effort: an account with no
     * photos, or a project without the bucket yet, just has nothing to remove.
     */
    suspend fun deleteAllMyPhotos() {
        val c = client ?: return
        val me = myId() ?: return
        runCatching {
            val bucket = c.storage.from(BUCKET)
            val paths = bucket.list(me).map { "$me/${it.name}" }
            if (paths.isNotEmpty()) bucket.delete(paths)
        }.onFailure { android.util.Log.w("StepqPro", "Couldn't remove profile photos", it) }
        photos.evictAll()
    }

    private fun shrink(raw: ByteArray): ByteArray {
        val bmp = BitmapFactory.decodeByteArray(raw, 0, raw.size) ?: throw IllegalArgumentException("Not an image")
        val side = minOf(bmp.width, bmp.height)
        val square = Bitmap.createBitmap(bmp, (bmp.width - side) / 2, (bmp.height - side) / 2, side, side)
        val scaled = if (side > 512) Bitmap.createScaledBitmap(square, 512, 512, true) else square
        return ByteArrayOutputStream().use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, 85, out)
            out.toByteArray()
        }
    }

    // ---------------------------------------------------------------- Settings
    suspend fun setTheme(key: String): Result<Unit> = runCatching {
        val c = client ?: throw NotConfiguredException()
        val me = myId() ?: throw NotConfiguredException()
        c.postgrest["profiles"].update({ set("theme", key) }) { filter { eq("id", me) } }
        _plan.value = _plan.value?.copy(theme = key)
        com.stepq.app.ui.theme.StepqColors.apply(_plan.value?.activeTheme ?: "leaf")
    }

    suspend fun setBff(friendId: String?): Result<Unit> = runCatching {
        val c = client ?: throw NotConfiguredException()
        val me = myId() ?: throw NotConfiguredException()
        c.postgrest["profiles"].update({ set("bff_id", friendId) }) { filter { eq("id", me) } }
        _plan.value = _plan.value?.copy(bffId = friendId)
    }

    suspend fun requestQRunHost(): Result<Unit> = runCatching {
        (client ?: throw NotConfiguredException()).postgrest.rpc("request_qrun_host")
        refresh()
    }

    // ---------------------------------------------------------------- Streaks and history
    suspend fun lostStreaks(today: LocalDate): List<LostStreak> = runCatching {
        (client ?: return emptyList()).postgrest
            .rpc("lost_streaks", buildJsonObject { put("p_today", today.toString()) })
            .decodeList<LostStreak>()
    }.getOrDefault(emptyList())

    /** Restores yesterday's streak with [friendId]. Returns the restores left (null = unlimited). */
    suspend fun restoreStreak(friendId: String, today: LocalDate): Result<Int?> = runCatching {
        val left = (client ?: throw NotConfiguredException()).postgrest.rpc(
            "restore_streak",
            buildJsonObject {
                put("p_friend", friendId)
                put("p_today", today.toString())
            },
        ).decodeAs<Int?>()
        refresh()
        left
    }

    /** My steps for the last [days] days, capped by my plan on the server. */
    suspend fun history(days: Int): Result<List<HistoryDay>> = runCatching {
        (client ?: throw NotConfiguredException()).postgrest
            .rpc("step_history", buildJsonObject { put("p_days", days) })
            .decodeList<HistoryDay>()
    }
}
