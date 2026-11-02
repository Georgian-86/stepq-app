package com.stepq.app.ui.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.stepq.app.R
import com.stepq.app.data.AuthRepository
import com.stepq.app.data.NotConfiguredException
import com.stepq.app.data.isValidEmail
import com.stepq.app.ui.components.StepqButton
import com.stepq.app.ui.components.StepqTextLink
import com.stepq.app.ui.components.UnderlineField
import com.stepq.app.ui.theme.StepqColors
import com.stepq.app.ui.theme.StepqType
import kotlinx.coroutines.launch

/** A03 Forgot password, then A04 Reset link sent. */
@Composable
fun ForgotPasswordScreen(auth: AuthRepository?, onBack: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var sentTo by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun send() {
        if (!isValidEmail(email)) { error = "Enter the email you signed up with."; return }
        busy = true
        error = null
        scope.launch {
            val result = auth?.sendPasswordReset(email) ?: Result.failure(NotConfiguredException())
            busy = false
            result
                .onSuccess { sentTo = email.trim() }
                .onFailure { error = (it as? NotConfiguredException)?.message ?: "Couldn't send the link. Try again in a minute." }
        }
    }

    Column(
        Modifier.fillMaxSize().background(StepqColors.White).safeDrawingPadding().imePadding().padding(horizontal = 24.dp),
    ) {
        IconButton(onClick = onBack, modifier = Modifier.padding(top = 8.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBackIos, contentDescription = "Back", tint = StepqColors.Ink)
        }
        val sent = sentTo
        if (sent == null) {
            Column(Modifier.weight(1f)) {
                Image(painterResource(R.drawable.piko_nudge), contentDescription = null, Modifier.size(64.dp))
                Spacer(Modifier.height(12.dp))
                Text("Forgot your password?", style = StepqType.Title, color = StepqColors.Ink)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Enter the email you signed up with. We'll send you a link to pick a new one.",
                    style = StepqType.Body, color = StepqColors.Muted,
                )
                Spacer(Modifier.height(24.dp))
                UnderlineField("Email", email, { email = it }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
                error?.let { Text(it, style = StepqType.Small, color = StepqColors.TomatoText, modifier = Modifier.padding(top = 8.dp)) }
            }
            StepqButton("Send reset link", ::send, loading = busy, modifier = Modifier.padding(bottom = 24.dp))
        } else {
            Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.weight(1f))
                Image(painterResource(R.drawable.piko_cheer), contentDescription = null, Modifier.size(120.dp))
                Spacer(Modifier.height(16.dp))
                Text("Check your inbox", style = StepqType.Title, color = StepqColors.Ink)
                Spacer(Modifier.height(8.dp))
                Text(
                    "We sent a reset link to $sent. It works for 30 minutes.",
                    style = StepqType.Body, color = StepqColors.Muted, textAlign = TextAlign.Center,
                )
                Spacer(Modifier.weight(1.4f))
            }
            StepqButton("Back to log in", onBack)
            Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                StepqTextLink("Didn't get it? Resend link", ::send)
            }
        }
    }
}
