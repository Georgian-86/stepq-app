package com.stepq.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.stepq.app.BuildConfig
import com.stepq.app.data.AuthRepository
import com.stepq.app.data.GoogleSignIn
import com.stepq.app.data.GoogleSignInCancelled
import com.stepq.app.data.NotConfiguredException
import com.stepq.app.data.authErrorMessage
import kotlinx.coroutines.launch

/**
 * "Continue with Google" (A02 and A05). On success the auth state flips to signed in and
 * navigation moves on: to Home, or to onboarding for a new account.
 */
@Composable
fun GoogleSignInButton(auth: AuthRepository?, onError: (String?) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    StepqButton("Continue with Google", {
        busy = true
        onError(null)
        scope.launch {
            val result = runCatching {
                val token = GoogleSignIn.getIdToken(context)
                (auth ?: throw NotConfiguredException()).logInWithGoogle(token).getOrThrow()
            }
            busy = false
            result.onFailure {
                onError(
                    when (it) {
                        // A real "back" press stays silent; debug builds show what Google said,
                        // because a set-up mismatch also arrives as a cancellation.
                        is GoogleSignInCancelled -> if (BuildConfig.DEBUG && it.detail != null) {
                            "Google sign-in was cancelled.\n(${it.detail})"
                        } else {
                            null
                        }
                        is IllegalStateException -> it.message
                        else -> authErrorMessage(it, "Couldn't sign in with Google. Try again, or use your email.")
                    },
                )
            }
        }
    }, style = StepqButtonStyle.Outline, loading = busy, modifier = modifier)
}
