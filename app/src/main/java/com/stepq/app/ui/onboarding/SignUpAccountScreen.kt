package com.stepq.app.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.stepq.app.data.AuthRepository
import com.stepq.app.data.NotConfiguredException
import com.stepq.app.data.authErrorMessage
import com.stepq.app.data.isValidEmail
import com.stepq.app.data.isValidPassword
import com.stepq.app.ui.components.FieldHint
import com.stepq.app.ui.components.OnboardingFrame
import com.stepq.app.ui.components.GoogleSignInButton
import com.stepq.app.ui.components.StepqButton
import com.stepq.app.ui.components.StepqButtonStyle
import com.stepq.app.ui.components.StepqTextLink
import com.stepq.app.ui.components.UnderlineField
import com.stepq.app.ui.theme.StepqColors
import com.stepq.app.ui.theme.StepqType
import kotlinx.coroutines.launch

/**
 * A05 Sign up · Account. On success either the user is signed in (navigation moves on to
 * A06 by itself) or Supabase wants the email confirmed first ([onNeedsConfirmation]).
 */
@Composable
fun SignUpAccountScreen(
    auth: AuthRepository?,
    onBack: () -> Unit,
    onLogIn: () -> Unit,
    onNeedsConfirmation: (email: String) -> Unit,
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val passwordOk = isValidPassword(password)

    fun submit() {
        if (!isValidEmail(email)) { error = "Enter a valid email."; return }
        if (!passwordOk) { error = "Use 8+ characters with a number."; return }
        if (confirm != password) { error = "The passwords don't match."; return }
        busy = true
        error = null
        scope.launch {
            val result = auth?.signUp(email, password) ?: Result.failure(NotConfiguredException())
            busy = false
            result
                .onSuccess { signedIn -> if (!signedIn) onNeedsConfirmation(email.trim()) }
                .onFailure {
                    error = authErrorMessage(it, "Couldn\'t create your account. Try again in a minute.")
                }
        }
    }

    OnboardingFrame(
        step = 1,
        title = "Take your first step",
        subtitle = "Create your StepQ account.",
        onBack = onBack,
        bottom = {
            Text(
                com.stepq.app.ui.components.legalText("StepQ is for people 13 and older (under 18s need a parent's OK). Read our ", " before you create an account."),
                style = StepqType.Small, color = StepqColors.Muted, textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 10.dp),
            )
            StepqButton("Continue", ::submit, loading = busy)
            StepqTextLink("Already have an account? Log in", onLogIn)
        },
    ) {
        GoogleSignInButton(auth, onError = { error = it })
        Spacer(Modifier.height(12.dp))
        com.stepq.app.ui.components.AppleSignInButton(auth, onError = { error = it })
        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HorizontalDivider(Modifier.weight(1f), color = StepqColors.Line)
            Text("or", style = StepqType.Small, color = StepqColors.Muted)
            HorizontalDivider(Modifier.weight(1f), color = StepqColors.Line)
        }
        Spacer(Modifier.height(20.dp))
        UnderlineField(
            "Email", email, { email = it },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
        )
        Spacer(Modifier.height(20.dp))
        UnderlineField(
            "Create a password", password, { password = it }, password = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
        )
        FieldHint("8+ characters with a number", ok = passwordOk || password.isEmpty())
        Spacer(Modifier.height(20.dp))
        UnderlineField(
            "Confirm password", confirm, { confirm = it }, password = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
        )
        if (confirm.isNotEmpty()) {
            if (confirm == password) FieldHint("Passwords match", ok = true) else FieldHint("The passwords don't match.", ok = false)
        }
        error?.let { Text(it, style = StepqType.Small, color = StepqColors.TomatoText, modifier = Modifier.padding(top = 12.dp)) }
    }
}
