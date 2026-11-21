package com.stepq.app.ui.components

import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.stepq.app.ui.theme.StepqColors
import com.stepq.app.ui.theme.StepqType

/**
 * The form field from the log-in and sign-up screens: an upper-case label, plain text, and an
 * underline that turns deep green while focused.
 */
@Composable
fun UnderlineField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    password: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation? = null,
    placeholder: String? = null,
    /** Fixed text before the value that you can't delete, e.g. "@" for usernames. */
    prefix: String? = null,
) {
    var focused by remember { mutableStateOf(false) }
    var shown by remember { mutableStateOf(false) }
    Column(modifier.fillMaxWidth()) {
        Text(label.uppercase(), style = StepqType.Label, color = StepqColors.Muted)
        Spacer(Modifier.height(10.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = StepqType.Body.copy(color = StepqColors.Ink),
            cursorBrush = SolidColor(StepqColors.Deep),
            visualTransformation = visualTransformation
                ?: if (password && !shown) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
                .onFocusChanged { focused = it.isFocused },
            decorationBox = { inner ->
                androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    if (prefix != null) Text(prefix, style = StepqType.Body, color = StepqColors.Muted)
                    androidx.compose.foundation.layout.Box(Modifier.weight(1f)) {
                        if (value.isEmpty() && placeholder != null) {
                            Text(placeholder, style = StepqType.Body, color = StepqColors.Muted.copy(alpha = 0.6f))
                        }
                        inner()
                    }
                    if (password) {
                        // Show / hide the password (eye icon).
                        androidx.compose.material3.Icon(
                            if (shown) androidx.compose.material.icons.Icons.Outlined.VisibilityOff else androidx.compose.material.icons.Icons.Outlined.Visibility,
                            contentDescription = if (shown) "Hide password" else "Show password",
                            tint = StepqColors.Muted,
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .size(22.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .clickable(role = androidx.compose.ui.semantics.Role.Button) { shown = !shown },
                        )
                    }
                }
            },
        )
        HorizontalDivider(
            thickness = if (focused) 2.dp else 1.dp,
            color = if (focused) StepqColors.Deep else StepqColors.Line,
        )
    }
}
