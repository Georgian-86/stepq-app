package com.stepq.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.stepq.app.ui.theme.StepqColors
import com.stepq.app.ui.theme.StepqType

enum class StepqButtonStyle(val container: Color, val content: Color, val border: Color?) {
    /** Deep green, the main call to action on most screens. */
    Primary(StepqColors.Deep, StepqColors.White, null),
    /** White on the green splash. */
    Light(StepqColors.White, StepqColors.Ink, null),
    /** Ink, e.g. "Sign up" on the splash and "Continue with Apple". */
    Dark(StepqColors.Ink, StepqColors.White, null),
    /** White with a line border, e.g. "Continue with Google". */
    Outline(StepqColors.White, StepqColors.Ink, StepqColors.Line),
}

/** The 52dp pill button used across the app. */
@Composable
fun StepqButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: StepqButtonStyle = StepqButtonStyle.Primary,
    enabled: Boolean = true,
    loading: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier.fillMaxWidth().height(52.dp),
        shape = CircleShape,
        border = style.border?.let { BorderStroke(1.5.dp, it) },
        contentPadding = PaddingValues(horizontal = 24.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = style.container,
            contentColor = style.content,
            disabledContainerColor = style.container.copy(alpha = 0.5f),
            disabledContentColor = style.content.copy(alpha = 0.8f),
        ),
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(20.dp), color = style.content, strokeWidth = 2.dp)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                if (leading != null) {
                    leading()
                    Spacer(Modifier.width(10.dp))
                }
                Text(text, style = StepqType.Button)
            }
        }
    }
}

/** Deep-green text link, e.g. "Forgot password?" and "New to StepQ? Sign up". */
@Composable
fun StepqTextLink(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(onClick = onClick, modifier = modifier) {
        Text(text, style = StepqType.SmallStrong.copy(fontSize = StepqType.BodyStrong.fontSize), color = StepqColors.Deep)
    }
}
