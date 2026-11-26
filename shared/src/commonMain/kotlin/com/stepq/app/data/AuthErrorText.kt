package com.stepq.app.data

import io.github.jan.supabase.auth.exception.AuthErrorCode
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.exceptions.HttpRequestException
import io.ktor.client.plugins.HttpRequestTimeoutException

class NotConfiguredException : IllegalStateException(
    "This build has no Supabase key. Add SUPABASE_ANON_KEY to the build settings.",
)

/**
 * A sign-in failure as a message people can act on, or null when there's nothing more useful
 * to say than the caller's fallback. Platform errors (Credential Manager, AuthenticationServices)
 * are handled by each app before this.
 */
fun friendlyAuthError(e: Throwable): String? = when {
    e is NotConfiguredException -> e.message
    e is HttpRequestException || e is HttpRequestTimeoutException || e is kotlinx.io.IOException ->
        "Can't reach StepQ right now. Check your internet connection and try again."
    e is AuthRestException -> when (e.errorCode) {
        AuthErrorCode.InvalidCredentials -> "That email and password don't match. Try again or reset your password."
        AuthErrorCode.EmailNotConfirmed -> "Confirm your email first: open the link we sent you, then log in."
        AuthErrorCode.UserAlreadyExists, AuthErrorCode.EmailExists -> "That email already has an account. Log in instead."
        AuthErrorCode.SignupDisabled -> "Sign-ups are turned off right now."
        AuthErrorCode.EmailProviderDisabled -> "Email log-in is turned off right now. Use another way to sign in."
        AuthErrorCode.ProviderDisabled -> "That sign-in option isn't switched on for StepQ yet."
        AuthErrorCode.OverEmailSendRateLimit, AuthErrorCode.OverRequestRateLimit ->
            "Too many tries. Wait a minute and try again."
        AuthErrorCode.WeakPassword -> "Pick a stronger password: 8+ characters with a number."
        else -> null
    }
    else -> null
}

/** A short technical line for debug builds, so a tester can tell us what went wrong. */
fun authErrorDetail(e: Throwable): String = when (e) {
    is AuthRestException -> "${e.errorCode?.value ?: e.error}: ${e.errorDescription}"
    else -> "${e::class.simpleName}: ${e.message?.lineSequence()?.firstOrNull()}"
}
