package com.stepq.app.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.stepq.app.data.ProfileRepository
import com.stepq.app.data.isValidUsername
import com.stepq.app.data.normalizeUsername
import com.stepq.app.data.suggestUsernames
import com.stepq.app.ui.components.FieldHint
import com.stepq.app.ui.components.OnboardingFrame
import com.stepq.app.ui.components.SectionLabel
import com.stepq.app.ui.components.StepqButton
import com.stepq.app.ui.components.StepqChip
import com.stepq.app.ui.components.UnderlineField
import kotlinx.coroutines.delay

private enum class UsernameStatus { Empty, Invalid, Checking, Available, Taken, Offline }

/** A06 Sign up · Name: full name and a unique username, checked as you type. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NameScreen(state: OnboardingViewModel, profiles: ProfileRepository?, onBack: () -> Unit, onContinue: () -> Unit) {
    var status by remember { mutableStateOf(UsernameStatus.Empty) }

    // People who signed up with Google already have a name; start from it.
    LaunchedEffect(Unit) {
        if (state.fullName.isBlank()) profiles?.currentUserFullName()?.let { state.fullName = it.take(40) }
    }

    LaunchedEffect(state.username) {
        val name = state.username
        status = when {
            name.isEmpty() -> UsernameStatus.Empty
            !isValidUsername(name) -> UsernameStatus.Invalid
            else -> UsernameStatus.Checking
        }
        if (status != UsernameStatus.Checking) return@LaunchedEffect
        delay(400) // wait for typing to pause
        status = profiles?.usernameAvailable(name)?.fold(
            onSuccess = { if (it) UsernameStatus.Available else UsernameStatus.Taken },
            onFailure = { UsernameStatus.Offline },
        ) ?: UsernameStatus.Offline
    }

    OnboardingFrame(
        step = 2,
        title = "What should Piko call you?",
        subtitle = "This is how friends find you on leaderboards.",
        onBack = onBack,
        bottom = {
            StepqButton(
                "Continue", onContinue,
                enabled = state.fullName.isNotBlank() && status == UsernameStatus.Available,
            )
        },
    ) {
        UnderlineField(
            "Full name", state.fullName, { state.fullName = it.take(40) },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
        )
        Spacer(Modifier.height(20.dp))
        UnderlineField(
            "Username", if (state.username.isEmpty()) "" else "@${state.username}",
            { state.username = normalizeUsername(it).take(20) },
        )
        when (status) {
            UsernameStatus.Available -> FieldHint("Available. You can change it later.", ok = true)
            UsernameStatus.Taken -> FieldHint("Taken. Try another or pick one below.", ok = false)
            UsernameStatus.Invalid -> FieldHint("3–20 letters, numbers, dots or underscores.", ok = false)
            UsernameStatus.Offline -> FieldHint("Couldn't check right now. Try again.", ok = false)
            UsernameStatus.Checking, UsernameStatus.Empty -> Unit
        }
        val suggestions = suggestUsernames(state.fullName)
        if (suggestions.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))
            SectionLabel("Or pick one")
            Spacer(Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                suggestions.forEach { s ->
                    StepqChip("@$s", selected = state.username == s, onClick = { state.username = s })
                }
            }
        }
    }
}
