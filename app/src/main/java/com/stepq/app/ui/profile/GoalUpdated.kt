package com.stepq.app.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.TrackChanges
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.stepq.app.data.GoalChange
import com.stepq.app.ui.components.StepqButton
import com.stepq.app.ui.onboarding.formatSteps
import com.stepq.app.ui.theme.StepqColors
import com.stepq.app.ui.theme.StepqType
import com.stepq.app.data.Dates
import com.stepq.app.data.goalUpdatedCopy
import kotlinx.datetime.LocalDate
import java.util.Locale

/** Shown after saving a new daily goal: it starts tomorrow, and today stays as it was. */
@Composable
fun GoalUpdatedDialog(change: GoalChange, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) { GoalUpdatedCard(change, onDismiss) }
}

@Composable
fun GoalUpdatedCard(change: GoalChange, onDismiss: () -> Unit, today: LocalDate = Dates.today()) {
    val copy = goalUpdatedCopy(change, today)
    val next = change.nextDailyGoal
    Column(
        Modifier
            .widthIn(max = 360.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(StepqColors.White)
            .padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(72.dp).clip(CircleShape).background(StepqColors.Mint), contentAlignment = Alignment.Center) {
            Box(Modifier.size(52.dp).clip(CircleShape).background(StepqColors.LeafSoft), contentAlignment = Alignment.Center) {
                Icon(
                    if (next != null) Icons.Outlined.EventAvailable else Icons.Outlined.TrackChanges,
                    contentDescription = null,
                    tint = StepqColors.Deep,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            copy.title,
            style = StepqType.Heading,
            color = StepqColors.Ink,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(8.dp))
        Text(copy.body, style = StepqType.Body, color = StepqColors.Muted, textAlign = TextAlign.Center)
        if (next != null) {
            Spacer(Modifier.height(20.dp))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GoalTile("Today", change.dailyGoal, highlighted = false, Modifier.weight(1f))
                Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null, tint = StepqColors.Muted, modifier = Modifier.size(18.dp))
                GoalTile(copy.startsLabel ?: "Tomorrow", next, highlighted = true, Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(24.dp))
        StepqButton("Got it", onDismiss, Modifier.fillMaxWidth())
    }
}

@Composable
private fun GoalTile(label: String, steps: Int, highlighted: Boolean, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (highlighted) StepqColors.Mint else StepqColors.Page)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label.uppercase(Locale.ENGLISH), style = StepqType.Label, color = if (highlighted) StepqColors.Deep else StepqColors.Muted)
        Spacer(Modifier.height(4.dp))
        Text(formatSteps(steps), style = StepqType.NumberM, color = if (highlighted) StepqColors.Deep else StepqColors.Ink)
        Text("steps", style = StepqType.Small, color = StepqColors.Muted)
    }
}

/** For screenshots: the popup over a dimmed screen, the way the dialog shows it. */
@Composable
fun GoalUpdatedPreview(change: GoalChange, today: LocalDate, behind: @Composable () -> Unit) {
    Box(Modifier) {
        behind()
        Box(
            Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.45f)).padding(24.dp),
            contentAlignment = Alignment.Center,
        ) { GoalUpdatedCard(change, onDismiss = {}, today = today) }
    }
}
