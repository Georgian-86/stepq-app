package com.stepq.app.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

enum class AuthState { Loading, SignedOut, SignedIn }

class NotConfiguredException : IllegalStateException(
    "This build has no Supabase key. Add SUPABASE_ANON_KEY to local.properties.",
)

/** Accounts through Supabase Auth: email + password, and Google. Apple needs OAuth set up first. */
class AuthRepository(private val client: SupabaseClient?) {

    val state: Flow<AuthState> = client?.auth?.sessionStatus?.map {
        when (it) {
            is SessionStatus.Initializing -> AuthState.Loading
            is SessionStatus.Authenticated -> AuthState.SignedIn
            else -> AuthState.SignedOut
        }
    } ?: flowOf(AuthState.SignedOut)

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

    suspend fun sendPasswordReset(email: String): Result<Unit> = runCatching {
        val c = client ?: throw NotConfiguredException()
        c.auth.resetPasswordForEmail(email.trim())
    }

    suspend fun logOut() {
        client?.auth?.signOut()
    }
}
