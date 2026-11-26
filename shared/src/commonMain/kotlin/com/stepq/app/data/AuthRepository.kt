package com.stepq.app.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.auth.providers.Apple
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.runningFold

enum class AuthState { Loading, SignedOut, SignedIn }


/**
 * Accounts through Supabase Auth: email + password, Google and Apple.
 * [beforeLogOut] lets a platform drop its push token first; [beforeDelete] removes files the
 * database can't reach (profile photos) before the account goes.
 */
class AuthRepository(
    private val client: SupabaseClient?,
    private val beforeLogOut: suspend () -> Unit = {},
    private val beforeDelete: suspend () -> Unit = { PlanRepository(client).deleteAllMyPhotos() },
) {

    val state: Flow<AuthState> = client?.auth?.sessionStatus?.let(::authStates) ?: flowOf(AuthState.SignedOut)

    suspend fun logIn(email: String, password: String): Result<Unit> = runCatching {
        val c = client ?: throw NotConfiguredException()
        c.auth.signInWith(Email) {
            this.email = email.trim()
            this.password = password
        }
    }

    /**
     * A05: creates the Supabase account. Returns true when the user is signed in straight away,
     * false when Supabase wants the email confirmed first (then they log in after confirming
     * and onboarding picks up where it left off).
     */
    suspend fun signUp(email: String, password: String): Result<Boolean> = runCatching {
        val c = client ?: throw NotConfiguredException()
        c.auth.signUpWith(Email, redirectUrl = StepqClient.AUTH_REDIRECT_URL) {
            this.email = email.trim()
            this.password = password
        }
        c.auth.currentSessionOrNull() != null
    }

    /** Signs in (or up) with a Google ID token. New users then go through onboarding. */
    suspend fun logInWithGoogle(idToken: String, rawNonce: String?): Result<Unit> = runCatching {
        val c = client ?: throw NotConfiguredException()
        c.auth.signInWith(IDToken) {
            this.idToken = idToken
            provider = Google
            nonce = rawNonce
        }
    }

    /**
     * Sign in with Apple through Supabase's web flow: opens Apple's page in the browser, which
     * returns to stepq://auth-callback. Needs the Apple provider turned on in Supabase.
     */
    suspend fun logInWithApple(): Result<Unit> = runCatching {
        val c = client ?: throw NotConfiguredException()
        c.auth.signInWith(Apple, redirectUrl = StepqClient.AUTH_REDIRECT_URL)
    }

    /**
     * iOS: native Sign in with Apple. [idToken] is the identity token from
     * ASAuthorizationAppleIDCredential and [rawNonce] the nonce whose SHA-256 went into the request.
     */
    suspend fun logInWithAppleIdToken(idToken: String, rawNonce: String): Result<Unit> = runCatching {
        val c = client ?: throw NotConfiguredException()
        c.auth.signInWith(IDToken) {
            this.idToken = idToken
            provider = Apple
            nonce = rawNonce
        }
    }

    /**
     * iOS: the Google sign-in page to open in an ASWebAuthenticationSession. It comes back to
     * stepq://auth-callback?code=…, which [handleAuthLink] turns into a session (PKCE).
     */
    suspend fun googleOAuthUrl(): String {
        val c = client ?: throw NotConfiguredException()
        return c.auth.getOAuthUrl(Google, redirectUrl = StepqClient.AUTH_REDIRECT_URL)
    }

    /**
     * A stepq://auth-callback link (email confirmation, password reset, change of email, or the
     * end of a web sign-in). Exchanges its one-time code for a session. Returns false when the
     * link carries no code (then there's nothing to do).
     */
    suspend fun handleAuthLink(url: String): Result<Boolean> = runCatching {
        val c = client ?: throw NotConfiguredException()
        PasswordRecovery.linkOpened(url, null)
        val code = Regex("[?&#]code=([^&#]+)").find(url)?.groupValues?.get(1) ?: return@runCatching false
        c.auth.exchangeCodeForSession(code)
        true
    }

    /** True when the signed-in account was made with Sign in with Apple. */
    fun usesApple(): Boolean = client?.auth?.currentUserOrNull()?.identities?.any { it.provider == "apple" } == true

    /** A03: the reset link opens the app, which then asks for a new password (see [PasswordRecovery]). */
    suspend fun sendPasswordReset(email: String): Result<Unit> = runCatching {
        val c = client ?: throw NotConfiguredException()
        c.auth.resetPasswordForEmail(email.trim(), redirectUrl = StepqClient.AUTH_REDIRECT_URL)
        PasswordRecovery.requested()
    }

    /** The signed-in account's email, or null. */
    fun email(): String? = client?.auth?.currentUserOrNull()?.email

    /** The email waiting to be confirmed after a change, if any. */
    fun pendingEmail(): String? = client?.auth?.currentUserOrNull()?.newEmail

    /** True when the account has a password (signed up with email), so it can be changed. */
    fun hasPassword(): Boolean {
        val user = client?.auth?.currentUserOrNull() ?: return false
        val providers = user.identities?.map { it.provider }.orEmpty()
        return providers.isEmpty() || "email" in providers
    }

    /** Sets a new password: from Profile, or after opening a reset link. */
    suspend fun updatePassword(newPassword: String): Result<Unit> = runCatching {
        val c = client ?: throw NotConfiguredException()
        c.auth.updateUser { password = newPassword }
        PasswordRecovery.done()
    }

    /** Supabase emails a confirmation link to the new address; the change applies once it's opened. */
    suspend fun updateEmail(newEmail: String): Result<Unit> = runCatching {
        val c = client ?: throw NotConfiguredException()
        c.auth.updateUser(redirectUrl = StepqClient.AUTH_REDIRECT_URL) { email = newEmail.trim() }
    }

    /** Deletes the account and everything in it (app.delete_my_account), then signs out. */
    suspend fun deleteAccount(): Result<Unit> = runCatching {
        val c = client ?: throw NotConfiguredException()
        // Profile photos live in Storage, which the database function can't reach: remove
        // them first so nothing of the account is left behind (DPDP s.8(7), s.12).
        beforeDelete()
        c.postgrest.rpc("delete_my_account")
        runCatching { c.auth.signOut() }
        runCatching { c.auth.clearSession() }
    }

    suspend fun logOut() {
        runCatching { beforeLogOut() }
        client?.auth?.signOut()
    }
}

/**
 * What the app shows for each Supabase session status. Only a real sign-out (the user tapped
 * Log out, or the server refused the session) counts as signed out:
 * - RefreshFailure means the token expired while the phone was offline. The session is still
 *   stored and Supabase keeps retrying, so the user stays in the app.
 * - Supabase drops back to Initializing every time the app goes to the background and reloads
 *   the session when it returns. After the first load that is not a reason to leave the
 *   current screen, so the last state is kept.
 */
fun authStates(statuses: Flow<SessionStatus>): Flow<AuthState> =
    statuses
        .map {
            when (it) {
                is SessionStatus.Initializing -> AuthState.Loading
                is SessionStatus.Authenticated, is SessionStatus.RefreshFailure -> AuthState.SignedIn
                is SessionStatus.NotAuthenticated -> AuthState.SignedOut
            }
        }
        .runningFold(AuthState.Loading) { previous, next ->
            if (next == AuthState.Loading && previous != AuthState.Loading) previous else next
        }
        .distinctUntilChanged()

/**
 * A password reset link was opened: the app shows "Set a new password" until the user saves one.
 * The link signs the user in first, so the new password can be saved with [AuthRepository.updatePassword].
 */
object PasswordRecovery {
    private val _active = kotlinx.coroutines.flow.MutableStateFlow(false)
    val active: kotlinx.coroutines.flow.StateFlow<Boolean> = _active

    /**
     * When a reset was last requested on this phone, to recognise the link: with PKCE the link
     * carries only `?code=`, no type. Kept on disk, because the phone often stops the app while
     * the person is in their email app.
     */
    private var requestedAt: Long = 0
    private var settings: com.russhwolf.settings.Settings? = null
    private const val KEY = "reset_requested_at"

    /** Called once at start-up with a small on-device store (not the session store). */
    fun init(settings: com.russhwolf.settings.Settings) {
        this.settings = settings
        requestedAt = settings.getLong(KEY, 0L)
    }

    fun requested() {
        requestedAt = nowMillis()
        settings?.putLong(KEY, requestedAt)
    }

    /** Called with what the opened link said about itself. */
    fun linkOpened(url: String?, sessionType: String?) {
        val recent = nowMillis() - requestedAt < 2 * 60 * 60 * 1000
        if (isRecovery(url, sessionType) || (recent && url?.contains("code=") == true)) _active.value = true
    }

    fun done() {
        _active.value = false
        requestedAt = 0
        settings?.remove(KEY)
    }

    fun isRecovery(url: String?, sessionType: String?): Boolean =
        sessionType == "recovery" || url?.contains("type=recovery") == true
}
