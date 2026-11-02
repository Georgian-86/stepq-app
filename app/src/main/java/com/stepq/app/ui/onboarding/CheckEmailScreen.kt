package com.stepq.app.ui.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.stepq.app.R
import com.stepq.app.ui.components.StepqButton
import com.stepq.app.ui.theme.StepqColors
import com.stepq.app.ui.theme.StepqType

/** After A05 when Supabase requires email confirmation. Same look as A04. */
@Composable
fun CheckEmailScreen(email: String, onLogIn: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(StepqColors.White).safeDrawingPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        Image(painterResource(R.drawable.piko_cheer), contentDescription = null, Modifier.size(120.dp))
        Spacer(Modifier.height(16.dp))
        Text("Confirm your email", style = StepqType.Title, color = StepqColors.Ink)
        Spacer(Modifier.height(8.dp))
        Text(
            "We sent a link to $email. Tap it, then log in here and Piko will finish setting you up.",
            style = StepqType.Body, color = StepqColors.Muted, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.weight(1.4f))
        StepqButton("Go to log in", onLogIn)
    }
}
