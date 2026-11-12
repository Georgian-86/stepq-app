package com.stepq.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.stepq.app.ui.theme.StepqColors
import com.stepq.app.ui.theme.StepqType

const val ONBOARDING_STEPS = 6

/** The six-segment progress bar at the top of A05–A10. */
@Composable
fun StepProgress(step: Int, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(ONBOARDING_STEPS) { i ->
            Box(
                Modifier
                    .weight(1f)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(if (i < step) StepqColors.Deep else StepqColors.Line),
            )
        }
    }
}

/**
 * Shared layout for the sign-up steps: back arrow, progress, title and subtitle, scrolling
 * content, and the bottom actions pinned above the keyboard.
 */
@Composable
fun OnboardingFrame(
    step: Int?,
    title: String,
    subtitle: String?,
    onBack: (() -> Unit)?,
    bottom: @Composable ColumnScope.() -> Unit,
    centered: Boolean = false,
    header: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier.fillMaxSize().background(StepqColors.White).safeDrawingPadding().imePadding().padding(horizontal = 24.dp),
    ) {
        Box(Modifier.height(56.dp), contentAlignment = Alignment.CenterStart) {
            if (onBack != null) {
                IconButton(onClick = onBack, modifier = Modifier.padding(start = 0.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBackIos, contentDescription = "Back", tint = StepqColors.Ink)
                }
            }
        }
        if (step != null) StepProgress(step)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()),
            horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
        ) {
            Spacer(Modifier.height(20.dp))
            header?.invoke()
            Text(
                title,
                style = StepqType.Title,
                color = StepqColors.Ink,
                textAlign = if (centered) androidx.compose.ui.text.style.TextAlign.Center else null,
            )
            if (subtitle != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    subtitle,
                    style = StepqType.Body,
                    color = StepqColors.Muted,
                    textAlign = if (centered) androidx.compose.ui.text.style.TextAlign.Center else null,
                )
            }
            Spacer(Modifier.height(20.dp))
            content()
            Spacer(Modifier.height(16.dp))
        }
        Column(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            bottom()
        }
    }
}

/** Pill choice chip: ink when selected, mint otherwise (A06 suggestions, A07 gender, A08 tabs). */
@Composable
fun StepqChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(CircleShape)
            .background(if (selected) StepqColors.Ink else StepqColors.Mint)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(text, style = StepqType.SmallStrong, color = if (selected) StepqColors.White else StepqColors.Ink)
    }
}

/** Small status line under a field: deep green tick, or tomato for a problem. */
@Composable
fun FieldHint(text: String, ok: Boolean, modifier: Modifier = Modifier) {
    Row(modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (ok) Icons.Filled.Check else Icons.Outlined.ErrorOutline,
            contentDescription = null,
            tint = if (ok) StepqColors.Deep else StepqColors.TomatoText,
            modifier = Modifier.padding(end = 6.dp).height(16.dp),
        )
        Text(text, style = StepqType.Small, color = if (ok) StepqColors.Deep else StepqColors.TomatoText)
    }
}

/** Label in the field style, for sections that aren't text fields ("GENDER", "OR PICK ONE"). */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = StepqType.Label, color = StepqColors.Muted, modifier = modifier)
}
