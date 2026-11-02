package com.stepq.app.data

import android.util.Log
import androidx.credentials.exceptions.GetCredentialException
import com.stepq.app.BuildConfig
import io.github.jan.supabase.auth.exception.AuthErrorCode
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.exceptions.HttpRequestException
import io.ktor.client.plugins.HttpRequestTimeoutException
import java.io.IOException

/**
 * Turns a sign-in failure into a message people can act on. The cause is always logged, and
 * debug builds add it under the message so a tester can tell us what went wrong.
 */
fun authErrorMessage(e: Throwable, fallback: String): String {
    Log.w("StepqAuth", "Auth failed", e)
    val friendly = when {
        e is NotConfiguredException || e is GoogleSignInNotConfigured -> e.message
        e is HttpRequestException || e is HttpRequestTimeoutException || e is IOException ->
            "Can't reach StepQ right now. Check your internet connection and try again."
        e is AuthRestException -> when (e.errorCode) {
            AuthErrorCode.InvalidCredentials -> "That email and password don't match. Try again or reset your password."
            AuthErrorCode.EmailNotConfirmed -> "Confirm your email first: open the link we sent you, then log in."
            AuthErrorCode.UserAlreadyExists, AuthErrorCode.EmailExists -> "That email already has an account. Log in instead."
            AuthErrorCode.SignupDisabled -> "Sign-ups are turned off right now."
            AuthErrorCode.EmailProviderDisabled -> "Email log-in is turned off right now. Use Google instead."
            AuthErrorCode.ProviderDisabled -> "Google sign-in isn't switched on for StepQ yet."
            AuthErrorCode.OverEmailSendRateLimit, AuthErrorCode.OverRequestRateLimit ->
                "Too many tries. Wait a minute and try again."
            AuthErrorCode.WeakPassword -> "Pick a stronger password: 8+ characters with a number."
            else -> null
        }
        e is GetCredentialException -> "Google couldn't sign you in on this phone."
        else -> null
    } ?: fallback
    if (!BuildConfig.DEBUG) return friendly
    val detail = when (e) {
        is AuthRestException -> "${e.errorCode?.value ?: e.error}: ${e.errorDescription}"
        is GetCredentialException -> "${e.type}: ${e.errorMessage}"
        else -> "${e::class.simpleName}: ${e.message?.lineSequence()?.firstOrNull()}"
    }
    return "$friendly\n($detail)"
}
