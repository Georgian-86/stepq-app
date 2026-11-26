package com.stepq.app.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import io.github.jan.supabase.auth.auth
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
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import kotlinx.datetime.toKotlinLocalDate

/**
 * StepQ Pro, app-wide: my plan (limits, usage, theme), everyone's Pro badges and photos, and the
 * Pro-only actions. Postgres enforces every limit; this only mirrors it so screens can explain
 * them before someone runs into one.
 */
object Pro {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client get() = Supabase.client
    private val repo by lazy { PlanRepository(Supabase.client) }

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
        val loaded = repo.myPlan() ?: MyPlan.FREE_DEFAULTS
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
            runCatching { repo.badges(ids) }.onSuccess { list -> withContext(Dispatchers.Main) { list.forEach { badges[it.id] = it } } }
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
                val bytes = repo.photoBytes(path)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            }.getOrNull()?.also { photos.put(path, it) }
        }
    }

    /** Uploads a new profile photo (shrunk to 512 px JPEG) and puts it on my profile. */
    suspend fun uploadPhoto(raw: ByteArray): Result<Unit> = runCatching {
        val me = myId() ?: throw NotConfiguredException()
        val jpeg = withContext(Dispatchers.Default) { shrink(raw) }
        repo.uploadPhoto(jpeg, _plan.value?.photoPath).getOrThrow()
        forget(me)
        refresh()
    }

    suspend fun removePhoto(): Result<Unit> = runCatching {
        val me = myId() ?: throw NotConfiguredException()
        repo.removePhoto(_plan.value?.photoPath).getOrThrow()
        forget(me)
        refresh()
    }

    /**
     * Before deleting the account: removes every file in my folder of the photo bucket,
     * including any left over from an earlier failed delete. Best effort: an account with no
     * photos, or a project without the bucket yet, just has nothing to remove.
     */
    suspend fun deleteAllMyPhotos() {
        repo.deleteAllMyPhotos()
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
    suspend fun setTheme(key: String): Result<Unit> = repo.setTheme(key).onSuccess {
        _plan.value = _plan.value?.copy(theme = key)
        com.stepq.app.ui.theme.StepqColors.apply(_plan.value?.activeTheme ?: "leaf")
    }

    suspend fun setBff(friendId: String?): Result<Unit> = repo.setBff(friendId).onSuccess {
        _plan.value = _plan.value?.copy(bffId = friendId)
    }

    suspend fun requestQRunHost(): Result<Unit> = repo.requestQRunHost().onSuccess { refresh() }

    // ---------------------------------------------------------------- Streaks and history
    suspend fun lostStreaks(today: LocalDate): List<LostStreak> = repo.lostStreaks(today.toKotlinLocalDate())

    /** Restores yesterday's streak with [friendId]. Returns the restores left (null = unlimited). */
    suspend fun restoreStreak(friendId: String, today: LocalDate): Result<Int?> =
        repo.restoreStreak(friendId, today.toKotlinLocalDate()).onSuccess { refresh() }

    /** My steps for the last [days] days, capped by my plan on the server. */
    suspend fun history(days: Int): Result<List<HistoryDay>> = repo.history(days)
}
