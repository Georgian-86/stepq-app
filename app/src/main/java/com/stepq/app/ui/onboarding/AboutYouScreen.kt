package com.stepq.app.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.stepq.app.data.Gender
import com.stepq.app.data.parseBirthdate
import com.stepq.app.ui.components.FieldHint
import com.stepq.app.ui.components.OnboardingFrame
import com.stepq.app.ui.components.SectionLabel
import com.stepq.app.ui.components.StepqButton
import com.stepq.app.ui.components.StepqChip
import com.stepq.app.ui.components.UnderlineField
import com.stepq.app.ui.theme.StepqColors
import com.stepq.app.ui.theme.StepqDimens
import com.stepq.app.ui.theme.StepqType
import java.time.format.DateTimeFormatter

/** A07 Sign up · About you: gender and birthdate (DD / MM / YYYY). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AboutYouScreen(state: OnboardingViewModel, onBack: () -> Unit, onContinue: () -> Unit) {
    var digits by rememberSaveable {
        mutableStateOf(state.birthdate?.format(DateTimeFormatter.ofPattern("ddMMyyyy")).orEmpty())
    }
    val parsed = parseBirthdate(digits)

    OnboardingFrame(
        step = 3,
        title = "A bit about you",
        subtitle = "This helps us set fair leaderboards and age-right goals.",
        onBack = onBack,
        bottom = {
            StepqButton("Continue", {
                state.birthdate = parsed
                onContinue()
            }, enabled = state.gender != null && parsed != null)
        },
    ) {
        SectionLabel("Gender")
        Spacer(Modifier.height(12.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Gender.entries.forEach { g ->
                StepqChip(g.label, selected = state.gender == g, onClick = { state.gender = g })
            }
        }
        Spacer(Modifier.height(24.dp))
        UnderlineField(
            "Birthdate", digits, { digits = it.filter(Char::isDigit).take(8) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            visualTransformation = DateMask,
            placeholder = "DD / MM / YYYY",
        )
        if (digits.length == 8 && parsed == null) {
            FieldHint("Enter a real date. You need to be 13 or older.", ok = false)
        }
        Spacer(Modifier.height(20.dp))
        Row(
            Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(StepqDimens.RadiusM))
                .background(StepqColors.MangoSoft).padding(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(Icons.Outlined.Lock, contentDescription = null, tint = StepqColors.Ink, modifier = Modifier.size(18.dp))
            Text(
                "Your birthdate can't be changed later, so check it before you continue.",
                style = StepqType.Small, color = StepqColors.Ink, modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}

/** Shows "14032001" as "14 / 03 / 2001". */
private object DateMask : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val d = text.text
        val out = buildString {
            d.forEachIndexed { i, c ->
                if (i == 2 || i == 4) append(" / ")
                append(c)
            }
        }
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int) = offset + (if (offset > 2) 3 else 0) + (if (offset > 4) 3 else 0)
            override fun transformedToOriginal(offset: Int) = when {
                offset <= 2 -> offset
                offset <= 7 -> (offset - 3).coerceIn(2, 4)
                else -> (offset - 6).coerceAtMost(d.length)
            }.coerceAtMost(d.length)
        }
        return TransformedText(AnnotatedString(out), mapping)
    }
}
