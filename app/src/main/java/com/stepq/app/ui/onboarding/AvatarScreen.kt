package com.stepq.app.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import com.stepq.app.ui.components.AvatarChoices
import com.stepq.app.ui.components.AvatarPart
import com.stepq.app.ui.components.OnboardingFrame
import com.stepq.app.ui.components.StepqAvatar
import com.stepq.app.ui.components.StepqButton
import com.stepq.app.ui.components.StepqChip
import com.stepq.app.ui.components.StepqTextLink
import com.stepq.app.ui.theme.StepqColors
import com.stepq.app.ui.theme.StepqType
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text

/** A08 Create your avatar: figure, hair, skin, outfit, headwear, face and extras. */
@Composable
fun AvatarScreen(state: OnboardingViewModel, onBack: () -> Unit, onContinue: () -> Unit) {

    OnboardingFrame(
        step = 4,
        title = "Create your avatar",
        subtitle = "It shows up on leaderboards, stories and streaks.",
        onBack = onBack,
        bottom = {
            StepqButton("Save avatar", onContinue)
            StepqTextLink("Skip for now", {
                state.avatar = com.stepq.app.data.defaultAvatar(state.gender?.value)
                onContinue()
            })
        },
    ) {
        AvatarPicker(state.avatar, 168.dp) { state.avatar = it }
    }
}

/**
 * The A08 avatar builder: preview, part tabs, then the choices for that part: shapes shown as
 * small avatars and colours as swatches. Also used by Edit profile.
 */
@Composable
fun AvatarPicker(avatar: AvatarConfig, size: androidx.compose.ui.unit.Dp, onChange: (AvatarConfig) -> Unit) {
    var part by rememberSaveable { mutableStateOf(AvatarPart.Body) }
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            StepqAvatar(avatar, size)
        }
        Spacer(Modifier.height(20.dp))
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AvatarPart.entries.forEach { p -> StepqChip(p.label, selected = part == p, onClick = { part = p }) }
        }
        part.choices.forEach { row ->
            Spacer(Modifier.height(18.dp))
            Text(row.label, style = StepqType.SmallStrong, color = StepqColors.Muted, modifier = Modifier.padding(bottom = 8.dp))
            when (row) {
                is AvatarChoices.Shapes -> ShapeChoices(avatar, row, onChange)
                is AvatarChoices.Colors -> ColorChoices(avatar, row, onChange)
            }
        }
    }
}

@Composable
private fun ShapeChoices(avatar: AvatarConfig, row: AvatarChoices.Shapes, onChange: (AvatarConfig) -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        row.names.forEachIndexed { i, label ->
            val selected = row.get(avatar) == i
            Column(
                Modifier
                    .clip(shape)
                    .border(if (selected) 2.dp else 1.dp, if (selected) StepqColors.Ink else StepqColors.Line, shape)
                    .clickable { onChange(row.set(avatar, i)) }
                    .padding(horizontal = 8.dp, vertical = 6.dp)
                    .semantics {
                        contentDescription = "${row.label}: $label"
                        this.selected = selected
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                StepqAvatar(row.set(avatar, i), 52.dp)
                Text(label, style = StepqType.Small, color = StepqColors.Ink)
            }
        }
    }
}

@Composable
private fun ColorChoices(avatar: AvatarConfig, row: AvatarChoices.Colors, onChange: (AvatarConfig) -> Unit) {
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        row.colors.forEachIndexed { i, color ->
            val selected = row.get(avatar) == i
            Box(
                Modifier
                    .size(40.dp)
                    .then(if (selected) Modifier.border(2.dp, StepqColors.Ink, CircleShape) else Modifier)
                    .padding(4.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(1.dp, StepqColors.Line, CircleShape)
                    .clickable { onChange(row.set(avatar, i)) }
                    .semantics {
                        contentDescription = "${row.label} option ${i + 1}"
                        this.selected = selected
                    },
            )
        }
    }
}
