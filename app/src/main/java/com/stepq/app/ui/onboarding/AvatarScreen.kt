package com.stepq.app.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.stepq.app.data.AvatarConfig
import com.stepq.app.ui.components.AvatarPalette
import com.stepq.app.ui.components.AvatarPart
import com.stepq.app.ui.components.OnboardingFrame
import com.stepq.app.ui.components.StepqAvatar
import com.stepq.app.ui.components.StepqButton
import com.stepq.app.ui.components.StepqChip
import com.stepq.app.ui.components.StepqTextLink
import com.stepq.app.ui.components.get
import com.stepq.app.ui.components.with
import com.stepq.app.ui.theme.StepqColors

/** A08 Create your avatar: pick a colour for skin, hair, outfit and headband. */
@Composable
fun AvatarScreen(state: OnboardingViewModel, onBack: () -> Unit, onContinue: () -> Unit) {
    var part by rememberSaveable { mutableStateOf(AvatarPart.Skin) }

    OnboardingFrame(
        step = 4,
        title = "Create your avatar",
        subtitle = "It shows up on leaderboards, stories and streaks.",
        onBack = onBack,
        bottom = {
            StepqButton("Save avatar", onContinue)
            StepqTextLink("Skip for now", {
                state.avatar = AvatarConfig()
                onContinue()
            })
        },
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            StepqAvatar(state.avatar, 168.dp)
        }
        Spacer(Modifier.height(24.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
            AvatarPart.entries.forEach { p -> StepqChip(p.label, selected = part == p, onClick = { part = p }) }
        }
        Spacer(Modifier.height(24.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            AvatarPalette.options(part).forEachIndexed { i, color ->
                val selected = state.avatar.get(part) == i
                Box(
                    Modifier
                        .size(48.dp)
                        .then(if (selected) Modifier.border(2.dp, StepqColors.Ink, CircleShape) else Modifier)
                        .padding(4.dp)
                        .clip(CircleShape)
                        .background(color)
                        .border(1.dp, StepqColors.Line, CircleShape)
                        .clickable { state.avatar = state.avatar.with(part, i) }
                        .semantics {
                            contentDescription = "${part.label} option ${i + 1}"
                            this.selected = selected
                        },
                )
            }
        }
    }
}
