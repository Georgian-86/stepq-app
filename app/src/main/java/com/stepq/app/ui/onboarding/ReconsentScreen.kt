package com.stepq.app.ui.onboarding

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.stepq.app.ui.components.ConsentCheckbox
import com.stepq.app.ui.components.OnboardingFrame
import com.stepq.app.ui.components.StepqButton
import com.stepq.app.ui.components.StepqButtonStyle
import com.stepq.app.ui.components.legalText
import com.stepq.app.ui.theme.StepqColors
import com.stepq.app.ui.theme.StepqType

/**
 * Shown once to anyone signed in who hasn't agreed to the current Terms and Privacy policy
 * (Legal.POLICY_VERSION): people who signed up before consent was logged, and everyone after
 * a policy change that needs agreeing to again (DPDP Act s.5 and s.6). The box starts unticked.
 */
@Composable
fun ReconsentScreen(
    busy: Boolean,
    error: String?,
    onAgree: () -> Unit,
    onLogOut: () -> Unit,
) {
    var agreed by rememberSaveable { mutableStateOf(false) }
    OnboardingFrame(
        step = null,
        title = "We've updated our terms",
        subtitle = "Please read our Terms of use and Privacy policy. They say what StepQ collects, why, who can see it, how long we keep it and your rights.",
        onBack = null,
        bottom = {
            StepqButton("Agree and continue", onAgree, enabled = agreed, loading = busy)
            StepqButton("Log out", onLogOut, style = StepqButtonStyle.Outline, modifier = Modifier.padding(top = 8.dp))
        },
    ) {
        ConsentCheckbox(
            checked = agreed,
            onChange = { agreed = it },
            text = legalText("I have read and agree to StepQ's ", "."),
        )
        Text(
            "Don't agree? Log out, or delete your account from Profile → Account after agreeing. You can withdraw consent any time in Profile → Privacy and data.",
            style = StepqType.Small,
            color = StepqColors.Muted,
            modifier = Modifier.padding(top = 16.dp),
        )
        error?.let { Text(it, style = StepqType.Small, color = StepqColors.TomatoText, modifier = Modifier.padding(top = 12.dp)) }
    }
}
