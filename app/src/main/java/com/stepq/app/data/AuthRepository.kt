package com.stepq.app.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
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

class NotConfiguredException : IllegalStateException(
    "This build has no Supabase key. Add SUPABASE_ANON_KEY to local.properties.",
)

/** Accounts through Supabase Auth: email + password, and Google. Apple needs OAuth set up first. */
class AuthRepository(private val client: SupabaseClient?) {

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
        c.auth.signUpWith(Email, redirectUrl = Supabase.AUTH_REDIRECT_URL) {
            this.email = email.trim()
            this.password = password
        }
        c.auth.currentSessionOrNull() != null
    }

    /** Signs in (or up) with a Google ID token. New users then go through onboarding. */
    suspend fun logInWithGoogle(token: GoogleIdToken): Result<Unit> = runCatching {
        val c = client ?: throw NotConfiguredException()
        c.auth.signInWith(IDToken) {
            idToken = token.idToken
            provider = Google
            nonce = token.rawNonce
        }
    }

    /**
     * Sign in with Apple through Supabase's web flow: opens Apple's page in the browser, which
     * returns to stepq://auth-callback. Needs the Apple provider turned on in Supabase.
     */
    suspend fun logInWithApple(): Result<Unit> = runCatching {
        val c = client ?: throw NotConfiguredException()
        c.auth.signInWith(io.github.jan.supabase.auth.providers.Apple, redirectUrl = Supabase.AUTH_REDIRECT_URL)
    }

    /** A03: the reset link opens the app, which then asks for a new password (see [PasswordRecovery]). */
    suspend fun sendPasswordReset(email: String): Result<Unit> = runCatching {
        val c = client ?: throw NotConfiguredException()
        c.auth.resetPasswordForEmail(email.trim(), redirectUrl = Supabase.AUTH_REDIRECT_URL)
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
        c.auth.updateUser(redirectUrl = Supabase.AUTH_REDIRECT_URL) { email = newEmail.trim() }
    }

    /** Deletes the account and everything in it (app.delete_my_account), then signs out. */
    suspend fun deleteAccount(): Result<Unit> = runCatching {
        val c = client ?: throw NotConfiguredException()
        // Profile photos live in Storage, which the database function can't reach: remove
        // them first so nothing of the account is left behind (DPDP s.8(7), s.12).
        Pro.deleteAllMyPhotos()
        c.postgrest.rpc("delete_my_account")
        runCatching { c.auth.signOut() }
        runCatching { c.auth.clearSession() }
        Unit
    }

    suspend fun logOut() {
        com.stepq.app.push.PushTokens.unregister()
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
     * carries only `?code=`, no type. Kept on disk, because Android often stops the app while
     * the person is in their email app.
     */
    @Volatile private var requestedAt: Long = 0
    private var prefs: android.content.SharedPreferences? = null
    private const val KEY = "reset_requested_at"

    /** Called once at start-up. */
    fun init(context: android.content.Context) {
        prefs = context.applicationContext.getSharedPreferences("stepq_auth_flags", android.content.Context.MODE_PRIVATE)
        requestedAt = prefs?.getLong(KEY, 0L) ?: 0L
    }

    fun requested() {
        requestedAt = System.currentTimeMillis()
        prefs?.edit()?.putLong(KEY, requestedAt)?.apply()
    }

    /** Called with what the opened link said about itself. */
    fun linkOpened(url: String?, sessionType: String?) {
        val recent = System.currentTimeMillis() - requestedAt < 2 * 60 * 60 * 1000
        if (isRecovery(url, sessionType) || (recent && url?.contains("code=") == true)) _active.value = true
    }

    fun done() {
        _active.value = false
        requestedAt = 0
        prefs?.edit()?.remove(KEY)?.apply()
    }

    fun isRecovery(url: String?, sessionType: String?): Boolean =
        sessionType == "recovery" || url?.contains("type=recovery") == true
}
