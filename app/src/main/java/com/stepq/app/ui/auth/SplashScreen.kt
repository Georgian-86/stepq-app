package com.stepq.app.ui.auth

import androidx.compose.foundation.Image
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.stepq.app.ui.components.appear
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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

/**
 * A01 Splash, and the launch screen. Every time the app opens it shows this green screen with
 * Piko walking towards you while the app gets ready. Signed-out people then get Log in and Sign up
 * ([showButtons]); signed-in people go straight to Home and never see the buttons.
 *
 * [intro] plays the wordmark and taglines rising in. It's off when this screen follows the
 * launch screen, which already played it, so the text doesn't blink.
 */
@Composable
fun SplashScreen(onLogIn: () -> Unit, onSignUp: () -> Unit, showButtons: Boolean = true, intro: Boolean = true) {
    Column(
        Modifier
            .fillMaxSize()
            .background(StepqColors.Leaf)
            .safeDrawingPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        fun Modifier.introIn(delayMs: Int, offset: androidx.compose.ui.unit.Dp = 16.dp) =
            if (intro) appear(0, offset = offset, delayMs = delayMs) else this
        Spacer(Modifier.weight(1f))
        com.stepq.app.ui.components.WalkingScene(
            Modifier.fillMaxWidth().height(270.dp).semantics { contentDescription = "Piko walking" },
            pikoSize = 170.dp,
            pikoBottom = 34.dp,
        )
        Spacer(Modifier.height(14.dp))
        Box(Modifier.introIn(900, 24.dp)) { Wordmark() }
        Spacer(Modifier.height(12.dp))
        Text("Chalo, chalte hain.", style = StepqType.Heading.copy(fontSize = 18.sp), color = StepqColors.Ink, modifier = Modifier.introIn(1150))
        Spacer(Modifier.height(12.dp))
        Text("Come on, let's walk.", style = StepqType.Body, color = StepqColors.Ink, modifier = Modifier.introIn(1300))
        Spacer(Modifier.weight(1.2f))
        // The buttons keep their space on the launch screen, so nothing moves when they slide in.
        Box(Modifier.height(112.dp)) {
            if (showButtons) {
                Column(Modifier.appear(0, offset = 40.dp, delayMs = if (intro) 1450 else 0), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    StepqButton("Log in", onLogIn, style = StepqButtonStyle.Light)
                    StepqButton("Sign up", onSignUp, style = StepqButtonStyle.Dark)
                }
            }
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
