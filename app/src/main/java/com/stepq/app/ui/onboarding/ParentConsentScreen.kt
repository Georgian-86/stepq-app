package com.stepq.app.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MarkEmailRead
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.stepq.app.data.isValidEmail
import com.stepq.app.ui.components.OnboardingFrame
import com.stepq.app.ui.components.StepqButton
import com.stepq.app.ui.components.StepqButtonStyle
import com.stepq.app.ui.components.StepqTextLink
import com.stepq.app.ui.components.UnderlineField
import com.stepq.app.ui.theme.StepqColors
import com.stepq.app.ui.theme.StepqDimens
import com.stepq.app.ui.theme.StepqType

/**
 * For 13 to 17 year olds: ask a parent or guardian to agree (DPDP Act s.9). Until they do, the
 * database refuses the account's steps, friends and posts, so this is the only screen they see.
 *
 * [status] is app.my_parental_consent(): "none", "pending", "declined" or "withdrawn".
 */
@Composable
fun ParentConsentScreen(
    status: String,
    sentTo: String?,
    busy: Boolean,
    error: String?,
    onSend: (name: String, email: String) -> Unit,
    onCheckAgain: () -> Unit,
    onLogOut: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var editing by rememberSaveable { mutableStateOf(false) }
    val waiting = status == "pending" && !editing
    val declined = status == "declined" || status == "withdrawn"

    OnboardingFrame(
        step = null,
        title = when {
            declined -> "Your parent said no"
            waiting -> "Waiting for your parent"
            else -> "Ask a parent to agree"
        },
        subtitle = when {
            declined -> "StepQ can't keep your data without a parent's consent. Ask them again, or log out. Accounts without consent are deleted after 7 days."
            waiting -> "We emailed ${sentTo ?: "your parent"} a link. Once they agree, you can start walking with StepQ."
            else -> "You're under 18, so Indian law needs a parent or guardian to agree before StepQ can keep your steps and profile. We'll email them a link."
        },
        onBack = null,
        bottom = {
            if (waiting) {
                StepqButton("I've asked them, check again", onCheckAgain, loading = busy)
                StepqTextLink("Send to a different email", { editing = true })
            } else {
                StepqButton(
                    "Email my parent",
                    { onSend(name, email); editing = false },
                    enabled = name.trim().length >= 2 && isValidEmail(email),
                    loading = busy,
                )
            }
            StepqButton("Log out", onLogOut, style = StepqButtonStyle.Outline, modifier = Modifier.padding(top = 8.dp))
        },
    ) {
        if (waiting) {
            Row(
                Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(StepqDimens.RadiusM))
                    .background(StepqColors.Mint).padding(16.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(Icons.Outlined.MarkEmailRead, contentDescription = null, tint = StepqColors.Deep, modifier = Modifier.size(20.dp))
                Text(
                    "The email comes from StepQ. If it isn't there, ask them to check spam. The link works once.",
                    style = StepqType.Small, color = StepqColors.Ink, modifier = Modifier.padding(start = 12.dp),
                )
            }
        } else {
            UnderlineField(
                "Parent or guardian's name", name, { name = it },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
            )
            Spacer(Modifier.height(20.dp))
            UnderlineField(
                "Their email", email, { email = it },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "Only use the email of a parent or legal guardian who is 18 or older. They'll see what StepQ collects and can say yes or no.",
                style = StepqType.Small, color = StepqColors.Muted,
            )
        }
        error?.let { Text(it, style = StepqType.Small, color = StepqColors.TomatoText, modifier = Modifier.padding(top = 12.dp)) }
    }
}
