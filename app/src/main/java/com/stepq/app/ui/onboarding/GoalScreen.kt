package com.stepq.app.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stepq.app.data.ProfileRepository
import com.stepq.app.data.stepsToKm
import com.stepq.app.ui.components.OnboardingFrame
import com.stepq.app.ui.components.StepqButton
import com.stepq.app.ui.theme.StepqColors
import com.stepq.app.ui.theme.StepqDimens
import com.stepq.app.ui.theme.StepqType
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

private val presets = listOf(5000 to "Getting started", 8000 to "Active", 10000 to "Very active", 12000 to "Athlete")
private const val STEP = 500
private val GOAL_RANGE = 1000..100000

fun formatSteps(steps: Int): String = NumberFormat.getIntegerInstance(Locale.US).format(steps)

/** A09 Pick your daily step goal. Continue saves the whole profile. */
@Composable
fun GoalScreen(state: OnboardingViewModel, profiles: ProfileRepository?, onBack: () -> Unit, onSaved: () -> Unit) {
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    OnboardingFrame(
        step = 5,
        title = "Pick your daily step goal",
        subtitle = "This is your StepQ score target. Piko cheers, and rewards you, every time you hit it.",
        onBack = onBack,
        bottom = {
            error?.let { Text(it, style = StepqType.Small, color = StepqColors.TomatoText, modifier = Modifier.padding(bottom = 8.dp)) }
            Text("You can change this any time", style = StepqType.Small, color = StepqColors.Muted, modifier = Modifier.padding(bottom = 10.dp))
            StepqButton("Continue", {
                busy = true
                error = null
                scope.launch {
                    val result = profiles?.let { state.save(it) } ?: Result.failure(IllegalStateException())
                    busy = false
                    result
                        .onSuccess { onSaved() }
                        .onFailure {
                            error = if (it.message.orEmpty().contains("profiles_username_key")) {
                                "Someone just took that username. Go back and pick another."
                            } else {
                                "Couldn't save your profile. Check your connection and try again."
                            }
                        }
                }
            }, loading = busy)
        },
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            RoundButton(Icons.Filled.Remove, "Fewer steps", enabled = state.dailyGoal > GOAL_RANGE.first) {
                state.dailyGoal = (state.dailyGoal - STEP).coerceIn(GOAL_RANGE)
            }
            Text(
                formatSteps(state.dailyGoal),
                style = StepqType.NumberXL.copy(fontSize = 48.sp),
                color = StepqColors.Ink,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            RoundButton(Icons.Filled.Add, "More steps", enabled = state.dailyGoal < GOAL_RANGE.last) {
                state.dailyGoal = (state.dailyGoal + STEP).coerceIn(GOAL_RANGE)
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "steps a day · about ${"%.1f".format(Locale.US, stepsToKm(state.dailyGoal))} km",
            style = StepqType.Small, color = StepqColors.Muted, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(24.dp))
        presets.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { (steps, label) ->
                    val selected = state.dailyGoal == steps
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(StepqDimens.RadiusM))
                            .background(if (selected) StepqColors.Deep else StepqColors.Mint)
                            .clickable { state.dailyGoal = steps }
                            .padding(vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(formatSteps(steps), style = StepqType.BodyStrong, color = if (selected) StepqColors.White else StepqColors.Ink)
                        Text(label, style = StepqType.Small, color = if (selected) StepqColors.White else StepqColors.Muted)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun RoundButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    FilledIconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(44.dp),
        shape = CircleShape,
        colors = IconButtonDefaults.filledIconButtonColors(containerColor = StepqColors.Mint, contentColor = StepqColors.Ink),
    ) { Icon(icon, contentDescription = label) }
}
