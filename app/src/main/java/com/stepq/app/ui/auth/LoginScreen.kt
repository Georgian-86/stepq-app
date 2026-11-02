package com.stepq.app.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.stepq.app.data.AuthRepository
import com.stepq.app.data.NotConfiguredException
import com.stepq.app.data.isValidEmail
import com.stepq.app.ui.components.GoogleSignInButton
import com.stepq.app.ui.components.StepqButton
import com.stepq.app.ui.components.StepqButtonStyle
import com.stepq.app.ui.components.StepqTextLink
import com.stepq.app.ui.components.UnderlineField
import com.stepq.app.ui.theme.StepqColors
import com.stepq.app.ui.theme.StepqTheme
import com.stepq.app.ui.theme.StepqType
import kotlinx.coroutines.launch

/**
 * A02 Log in: email + password or Google. Apple is shown as in the design but still needs
 * an Apple Services ID before it can work.
 */
@Composable
fun LoginScreen(
    auth: AuthRepository?,
    onBack: () -> Unit,
    onForgotPassword: () -> Unit,
    onSignUp: () -> Unit,
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun submit() {
        if (!isValidEmail(email)) { error = "Enter the email you signed up with."; return }
        if (password.isEmpty()) { error = "Enter your password."; return }
        busy = true
        error = null
        scope.launch {
            val result = auth?.logIn(email, password) ?: Result.failure(NotConfiguredException())
            busy = false
            // On success the auth state flips to SignedIn and navigation moves on by itself.
            result.onFailure {
                error = when (it) {
                    is NotConfiguredException -> it.message
                    else -> "That email and password don't match. Try again or reset your password."
                }
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(StepqColors.White)
            .safeDrawingPadding()
            .imePadding()
            .padding(horizontal = 24.dp),
    ) {
        IconButton(onClick = onBack, modifier = Modifier.padding(top = 8.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBackIos, contentDescription = "Back", tint = StepqColors.Ink)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(12.dp))
            Text("Welcome back", style = StepqType.Title, color = StepqColors.Ink)
            Spacer(Modifier.height(8.dp))
            Text("Log in with your email, or use Google or Apple.", style = StepqType.Body, color = StepqColors.Muted)
            Spacer(Modifier.height(24.dp))
            UnderlineField(
                "Email", email, { email = it },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            )
            Spacer(Modifier.height(20.dp))
            UnderlineField(
                "Password", password, { password = it },
                password = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            )
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                StepqTextLink("Forgot password?", onForgotPassword)
            }
            error?.let {
                Text(it, style = StepqType.Small, color = StepqColors.TomatoText, modifier = Modifier.padding(vertical = 4.dp))
            }
            Spacer(Modifier.height(16.dp))
            OrDivider()
            Spacer(Modifier.height(16.dp))
            val soon = { error = "Apple sign-in is coming soon. Use Google or your email for now." }
            GoogleSignInButton(auth, onError = { error = it })
            Spacer(Modifier.height(12.dp))
            StepqButton("Continue with Apple", soon, style = StepqButtonStyle.Dark)
            Spacer(Modifier.height(24.dp))
        }
        StepqButton("Log in", ::submit, loading = busy)
        Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
            StepqTextLink("New to StepQ? Sign up", onSignUp)
        }
    }
}

@Composable
private fun OrDivider() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        HorizontalDivider(Modifier.weight(1f), color = StepqColors.Line)
        Text("or", style = StepqType.Small, color = StepqColors.Muted)
        HorizontalDivider(Modifier.weight(1f), color = StepqColors.Line)
    }
}

@Preview(showBackground = true)
@Composable
private fun LoginPreview() = StepqTheme { LoginScreen(null, {}, {}, {}) }
