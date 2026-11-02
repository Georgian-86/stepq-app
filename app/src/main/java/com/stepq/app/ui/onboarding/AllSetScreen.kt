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

/** A11 All set. */
@Composable
fun AllSetScreen(firstName: String, dailyGoal: Int, onStart: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(StepqColors.LeafSoft).safeDrawingPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        Image(painterResource(R.drawable.piko_cheer), contentDescription = "Piko cheering", Modifier.size(150.dp))
        Spacer(Modifier.height(20.dp))
        Text("You're all set, $firstName!", style = StepqType.Title, color = StepqColors.Ink, textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        Text(
            "Your goal is ${formatSteps(dailyGoal)} steps a day. Walk with friends, keep your streak alive and climb the weekly leaderboard.",
            style = StepqType.Body, color = StepqColors.Muted, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.weight(1.4f))
        StepqButton("Chalo, let's walk", onStart)
    }
}
