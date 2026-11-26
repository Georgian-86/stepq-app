package com.stepq.app.data

import com.russhwolf.settings.ExperimentalSettingsImplementation
import com.russhwolf.settings.KeychainSettings
import com.russhwolf.settings.NSUserDefaultsSettings
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import platform.Foundation.NSUserDefaults

/** An error with a message that can be shown as is. Swift sees it as an NSError. */
class StepqError(message: String) : Exception(message)

/** Stops a watch started from Swift. */
class Watch internal constructor(private val job: Job) {
    fun cancel() = job.cancel()
}

/** What a checked App Store purchase did to my plan (from the appstore-verify function). */
data class StoreResult(val plan: String, val expiresAt: String?)

/**
 * The iOS app's door into the shared data layer. Swift calls these as `async throws`
 * functions. Everything is the same Supabase project, rules and row-level security as Android;
 * only the session store differs (the Keychain here).
 *
 * Dates cross into Swift as ISO strings (yyyy-MM-dd), numbers as Int32, lists as arrays.
 */
@OptIn(ExperimentalSettingsImplementation::class)
class StepqApi(private val supabaseUrl: String, anonKey: String) {
    private val client = StepqClient.create(
        supabaseUrl,
        anonKey,
        // The session (access and refresh tokens) and the PKCE verifier live in the Keychain,
        // readable only by this app on this device.
        KeychainSettings("in.stepq.app.session"),
    )
    private val anon = anonKey
    private val scope: CoroutineScope = MainScope()
    private val http by lazy { HttpClient { expectSuccess = false } }

    private val profiles = ProfileRepository(client)
    private val plans = PlanRepository(client)
    private val auth = AuthRepository(client)
    private val home = HomeRepository(client, profiles)
    private val friends = FriendsRepository(client, profiles)
    private val community = CommunityRepository(client, profiles)
    private val challenges = ChallengesRepository(client, profiles)

    init {
        // Small flags that aren't secrets (when a password reset was asked for).
        PasswordRecovery.init(NSUserDefaultsSettings(NSUserDefaults.standardUserDefaults))
    }

    val isConfigured: Boolean get() = client != null

    // ------------------------------------------------------------------ Auth

    /** Calls [onChange] on the main thread with "loading", "signedOut" or "signedIn". */
    fun watchAuth(onChange: (String) -> Unit): Watch = Watch(
        scope.launch {
            auth.state.collect {
                onChange(
                    when (it) {
                        AuthState.Loading -> "loading"
                        AuthState.SignedOut -> "signedOut"
                        AuthState.SignedIn -> "signedIn"
                    },
                )
            }
        },
    )

    /** True while a password reset link is open and a new password hasn't been saved. */
    fun watchPasswordRecovery(onChange: (Boolean) -> Unit): Watch = Watch(
        scope.launch { PasswordRecovery.active.collect { onChange(it) } },
    )

    fun myId(): String? = profiles.currentUserId()
    fun email(): String? = auth.email()
    fun hasPassword(): Boolean = auth.hasPassword()
    fun usesApple(): Boolean = auth.usesApple()
    fun fullNameHint(): String? = profiles.currentUserFullName()

    @Throws(Throwable::class)
    suspend fun logIn(email: String, password: String) = authCall { auth.logIn(email, password) }

    /** True when signed in straight away; false when the email must be confirmed first. */
    @Throws(Throwable::class)
    suspend fun signUp(email: String, password: String): Boolean = authCall { auth.signUp(email, password) }

    @Throws(Throwable::class)
    suspend fun logInWithApple(idToken: String, rawNonce: String) = authCall { auth.logInWithAppleIdToken(idToken, rawNonce) }

    @Throws(Throwable::class)
    suspend fun googleSignInUrl(): String = try {
        auth.googleOAuthUrl()
    } catch (e: Throwable) {
        throw StepqError(friendlyAuthError(e) ?: "Couldn't open Google sign-in. Try again.")
    }

    /** A stepq://auth-callback link opened the app. True when it signed me in. */
    @Throws(Throwable::class)
    suspend fun openAuthLink(url: String): Boolean = authCall { auth.handleAuthLink(url) }

    @Throws(Throwable::class)
    suspend fun sendPasswordReset(email: String) = authCall { auth.sendPasswordReset(email) }

    @Throws(Throwable::class)
    suspend fun updatePassword(password: String) = authCall { auth.updatePassword(password) }

    @Throws(Throwable::class)
    suspend fun updateEmail(email: String) = authCall { auth.updateEmail(email) }

    @Throws(Throwable::class)
    suspend fun logOut() {
        runCatching { auth.logOut() }
    }

    /**
     * Deletes the account and everything in it. For Sign in with Apple accounts, pass the
     * authorization code from a fresh Apple sign-in: the server uses it to revoke StepQ's
     * Apple tokens (Apple requires this), before the account is removed.
     */
    @Throws(Throwable::class)
    suspend fun deleteAccount(appleAuthorizationCode: String?) {
        if (appleAuthorizationCode != null) {
            // Best effort: deleting the account must never fail because of Apple.
            runCatching { callFunction("apple-revoke", buildJsonObject { put("authorizationCode", appleAuthorizationCode) }) }
                .onFailure { logWarn("StepqAuth", "Couldn't revoke the Apple token", it) }
        }
        call("Couldn't delete your account. Check your connection and try again.") { auth.deleteAccount() }
    }

    // ------------------------------------------------------------------ Profile and onboarding

    @Throws(Throwable::class)
    suspend fun myProfile(): Profile? = call { profiles.mine() }

    @Throws(Throwable::class)
    suspend fun usernameAvailable(username: String): Boolean = call { profiles.usernameAvailable(username) }

    @Throws(Throwable::class)
    suspend fun createProfile(
        displayName: String,
        username: String,
        gender: String?,
        birthdate: String?,
        avatar: AvatarConfig,
        dailyGoal: Int,
    ) {
        val me = myId() ?: throw StepqError("Log in again first.")
        call("Couldn't save your profile. Try again.") {
            profiles.create(NewProfile(me, displayName.trim(), username, gender, birthdate, avatar, dailyGoal))
        }
    }

    @Throws(Throwable::class)
    suspend fun updateProfile(displayName: String, username: String, avatar: AvatarConfig, gender: String?) =
        call { profiles.updateProfile(displayName, username, avatar, gender) }

    @Throws(Throwable::class)
    suspend fun updateGoal(goal: Int, current: Int) = call { profiles.updateGoal(goal, current) }

    @Throws(Throwable::class)
    suspend fun recordConsent(purpose: String, version: String, granted: Boolean) =
        call { profiles.recordConsent(purpose, version, granted) }

    /** Null when it can't be checked (offline, older database): let the person in. */
    suspend fun hasConsent(version: String, purpose: String): Boolean? = profiles.hasConsent(version, purpose).getOrNull()

    /** "not_needed", "none", "pending", "granted", "declined" or "withdrawn". */
    suspend fun parentalConsent(): String = profiles.parentalConsent().getOrDefault("not_needed")

    @Throws(Throwable::class)
    suspend fun requestParentalConsent(parentName: String, parentEmail: String) =
        call { profiles.requestParentalConsent(parentName, parentEmail) }

    @Throws(Throwable::class)
    suspend fun exportMyData(): String = call { profiles.exportMyData() }

    @Throws(Throwable::class)
    suspend fun myBlocks(): List<BlockedUser> = call { profiles.myBlocks() }

    @Throws(Throwable::class)
    suspend fun unblock(userId: String) = call { profiles.unblock(userId) }

    @Throws(Throwable::class)
    suspend fun myDays(): List<DailySteps> = call { profiles.myDays() }

    suspend fun myScore(): Int? = profiles.score()

    // ------------------------------------------------------------------ Home and steps

    @Throws(Throwable::class)
    suspend fun loadHome(today: String): HomeData? = call { home.load(LocalDate.parse(today)) }

    /** Saves days read from Apple Health. Only my own rows (RLS). */
    @Throws(Throwable::class)
    suspend fun saveDays(days: List<DailySteps>) = call { home.saveDays(days) }

    /** Shares today's steps with these friends; returns how many got it. */
    @Throws(Throwable::class)
    suspend fun shareSteps(friendIds: List<String>, steps: Int, day: String): Int =
        call { home.shareSteps(friendIds, steps, LocalDate.parse(day)) }.first

    // ------------------------------------------------------------------ Friends

    @Throws(Throwable::class)
    suspend fun loadFriends(): FriendsData? = call { friends.load() }

    @Throws(Throwable::class)
    suspend fun searchPeople(query: String): List<Suggestion> = call { friends.searchPeople(query) }

    @Throws(Throwable::class)
    suspend fun peopleYouMayKnow(): List<Suggestion> = call { friends.peopleYouMayKnow() }

    @Throws(Throwable::class)
    suspend fun sendFriendRequest(toId: String) = call { friends.sendRequest(myIdOrThrow(), toId) }

    @Throws(Throwable::class)
    suspend fun acceptFriend(requesterId: String) = call { friends.accept(myIdOrThrow(), requesterId) }

    @Throws(Throwable::class)
    suspend fun removeFriend(otherId: String) = call { friends.remove(myIdOrThrow(), otherId) }

    @Throws(Throwable::class)
    suspend fun sendWaterReminder(toId: String) = call { friends.sendWaterReminder(myIdOrThrow(), toId) }

    @Throws(Throwable::class)
    suspend fun friendStreaks(today: String): Map<String, FriendStreak> =
        StreakScore.friendStreaks(db(), LocalDate.parse(today)).orEmpty()

    // ------------------------------------------------------------------ Community

    /** [scope] is "everyone", "friends" or "mine". */
    @Throws(Throwable::class)
    suspend fun feed(scope: String, before: String?): List<Post> =
        call { community.feed(FeedScope.entries.first { it.value == scope }, before) }

    @Throws(Throwable::class)
    suspend fun thread(postId: String): List<Post> = call { community.thread(postId) }

    /** Posts a thought, a reply, or (with [shareDay]) my steps that day. */
    @Throws(Throwable::class)
    suspend fun createPost(body: String, parentId: String?, shareDay: String?): String =
        call("Couldn't post that. Try again.") { community.create(body, parentId, shareDay?.let(LocalDate::parse)) }

    @Throws(Throwable::class)
    suspend fun setLiked(postId: String, liked: Boolean) = call { community.setLiked(postId, liked) }

    @Throws(Throwable::class)
    suspend fun setReposted(postId: String, reposted: Boolean) = call { community.setReposted(postId, reposted) }

    @Throws(Throwable::class)
    suspend fun deletePost(postId: String) = call { community.delete(postId) }

    @Throws(Throwable::class)
    suspend fun reportPost(postId: String, reason: String) = call { community.report(postId, reason) }

    @Throws(Throwable::class)
    suspend fun blockUser(userId: String) = call { community.block(userId) }

    suspend fun myStepsToday(): Int? = community.myStepsToday()

    // ------------------------------------------------------------------ Challenges

    @Throws(Throwable::class)
    suspend fun loadChallenges(country: String?): ChallengesData? = call { challenges.load(country) }

    @Throws(Throwable::class)
    suspend fun joinChallenge(challengeId: String) = call { challenges.join(myIdOrThrow(), challengeId) }

    @Throws(Throwable::class)
    suspend fun leaveChallenge(challengeId: String) = call { challenges.leave(myIdOrThrow(), challengeId) }

    // ------------------------------------------------------------------ StepQ Pro

    suspend fun myPlan(): MyPlan? = plans.myPlan()

    @Throws(Throwable::class)
    suspend fun history(days: Int): List<HistoryDay> = call { plans.history(days) }

    suspend fun lostStreaks(today: String): List<LostStreak> = plans.lostStreaks(LocalDate.parse(today))

    /** Returns the restores left, or null when unlimited. */
    @Throws(Throwable::class)
    suspend fun restoreStreak(friendId: String, today: String): Int? = call { plans.restoreStreak(friendId, LocalDate.parse(today)) }

    @Throws(Throwable::class)
    suspend fun setTheme(key: String) = call { plans.setTheme(key) }

    @Throws(Throwable::class)
    suspend fun setBff(friendId: String?) = call { plans.setBff(friendId) }

    /**
     * Sends a StoreKit 2 transaction (its JWS) to the appstore-verify function, which checks
     * Apple's signature and turns Pro on (or off) for my account. The app never decides that
     * itself.
     */
    @Throws(Throwable::class)
    suspend fun verifyAppStorePurchase(signedTransaction: String): StoreResult {
        val json = callFunction("appstore-verify", buildJsonObject { put("signedTransaction", signedTransaction) })
        return StoreResult(
            plan = json["plan"]?.jsonPrimitive?.content ?: "free",
            expiresAt = json["expiresAt"]?.jsonPrimitive?.takeIf { it.isString }?.content,
        )
    }

    // ------------------------------------------------------------------ Shared rules for Swift

    fun isValidEmail(email: String): Boolean = com.stepq.app.data.isValidEmail(email)
    fun isValidPassword(password: String): Boolean = com.stepq.app.data.isValidPassword(password)
    fun isValidUsername(username: String): Boolean = com.stepq.app.data.isValidUsername(username)
    fun normalizeUsername(input: String): String = com.stepq.app.data.normalizeUsername(input)
    fun suggestUsernames(fullName: String): List<String> = com.stepq.app.data.suggestUsernames(fullName)

    /** DDMMYYYY → ISO date, or null when not a real date or under 13. */
    fun parseBirthdate(digits: String): String? = com.stepq.app.data.parseBirthdate(digits)?.toString()

    fun isMinor(birthdateIso: String?): Boolean = com.stepq.app.data.isMinor(Dates.parseDateOrNull(birthdateIso))

    fun today(): String = Dates.today().toString()

    fun defaultHairStyle(gender: String?): Int = com.stepq.app.data.defaultHairStyle(gender)

    fun newDay(userId: String, day: String, steps: Int, distanceM: Int, activeMinutes: Int, calories: Int) =
        DailySteps(userId, day, steps, distanceM, activeMinutes, calories)

    fun avatar(skin: Int, hair: Int, outfit: Int, headband: Int, style: Int) = AvatarConfig(skin, hair, outfit, headband, style)

    // ------------------------------------------------------------------ Internals

    private fun db() = (client ?: throw StepqError(NotConfiguredException().message!!)).postgrest

    private fun myIdOrThrow(): String = myId() ?: throw StepqError("Log in again first.")

    private suspend fun <T> authCall(block: suspend () -> Result<T>): T =
        block().getOrElse { throw StepqError(friendlyAuthError(it) ?: "Something went wrong. Try again.") }

    private suspend fun <T> call(fallback: String = "Something went wrong. Check your connection and try again.", block: suspend () -> Result<T>): T =
        block().getOrElse { throw StepqError(userMessage(it, fallback)) }

    /**
     * Calls a Supabase Edge Function as me. Only my session's access token goes with it; the
     * functions hold any server secrets (never the app).
     */
    private suspend fun callFunction(name: String, body: JsonObject): JsonObject {
        val c = client ?: throw StepqError(NotConfiguredException().message!!)
        val token = c.auth.currentSessionOrNull()?.accessToken ?: throw StepqError("Log in again first.")
        val response = try {
            http.post("$supabaseUrl/functions/v1/$name") {
                header("Authorization", "Bearer $token")
                header("apikey", anon)
                contentType(ContentType.Application.Json)
                setBody(body.toString())
            }
        } catch (e: Throwable) {
            throw StepqError("Can't reach StepQ right now. Check your internet connection and try again.")
        }
        val text = response.bodyAsText()
        val json = runCatching { Json.parseToJsonElement(text) as JsonObject }.getOrElse { JsonObject(emptyMap()) }
        if (!response.status.isSuccess()) {
            throw StepqError((json["error"] as? JsonPrimitive)?.content ?: "Something went wrong. Try again.")
        }
        return json
    }
}

/**
 * A message for the person, from a Postgres or network error. StepQ's database functions raise
 * messages written for people (e.g. "Keep it under 500 characters."), so those are shown as is.
 */
internal fun userMessage(e: Throwable, fallback: String): String {
    if (e is StepqError) return e.message ?: fallback
    friendlyAuthError(e)?.let { return it }
    val pg = e as? io.github.jan.supabase.postgrest.exception.PostgrestRestException ?: return fallback
    if (pg.hint?.contains("content_filter") == true) {
        return "That has words StepQ doesn't allow in the community. Please change it and try again."
    }
    return pg.error.takeIf { it.isNotBlank() && it.length < 200 && !it.contains("violates") && !it.contains("relation") } ?: fallback
}
