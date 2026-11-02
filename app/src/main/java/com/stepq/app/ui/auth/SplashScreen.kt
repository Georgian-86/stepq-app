package com.stepq.app.ui.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stepq.app.R
import com.stepq.app.ui.components.StepqButton
import com.stepq.app.ui.components.StepqButtonStyle
import com.stepq.app.ui.theme.StepqColors
import com.stepq.app.ui.theme.StepqTheme
import com.stepq.app.ui.theme.StepqType

/** A01 Splash. */
@Composable
fun SplashScreen(onLogIn: () -> Unit, onSignUp: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(StepqColors.Leaf)
            .safeDrawingPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        Image(painterResource(R.drawable.piko_happy), contentDescription = "Piko", Modifier.size(190.dp))
        Spacer(Modifier.height(24.dp))
        Wordmark()
        Spacer(Modifier.height(12.dp))
        Text("Chalo, chalte hain.", style = StepqType.Heading.copy(fontSize = 18.sp), color = StepqColors.Ink)
        Spacer(Modifier.height(12.dp))
        Text("Come on, let's walk.", style = StepqType.Body, color = StepqColors.Ink)
        Spacer(Modifier.weight(1.2f))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            StepqButton("Log in", onLogIn, style = StepqButtonStyle.Light)
            StepqButton("Sign up", onSignUp, style = StepqButtonStyle.Dark)
        }
    }
}

@Composable
private fun Wordmark() {
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(color = StepqColors.Ink)) { append("step") }
            withStyle(SpanStyle(color = StepqColors.Deep)) { append("q") }
        },
        style = StepqType.Display.copy(fontSize = 52.sp, lineHeight = 56.sp),
    )
}

@Preview
@Composable
private fun SplashPreview() = StepqTheme { SplashScreen({}, {}) }
