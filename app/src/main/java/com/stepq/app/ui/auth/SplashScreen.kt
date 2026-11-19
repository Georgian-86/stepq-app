package com.stepq.app.ui.auth

import androidx.compose.foundation.Image
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.graphicsLayer
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

private const val STEP_MS = 640
private const val CROSS_MS = 6400

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
        // Intro (reference reel 1): Piko walks across the screen, left to right, step by
        // step, and keeps going: off the right edge and back in from the left. The wordmark
        // rises in, the taglines follow and the buttons slide up.
        val motion = com.stepq.app.ui.components.LocalMotionEnabled.current
        val walk = androidx.compose.animation.core.rememberInfiniteTransition(label = "walk")
        val cycle by walk.animateFloat(
            0f, 1f,
            androidx.compose.animation.core.infiniteRepeatable(tween(STEP_MS, easing = androidx.compose.animation.core.LinearEasing)),
            label = "step",
        )
        // Starts just off the left edge; one crossing takes CROSS_MS.
        val across by walk.animateFloat(
            0f, 1f,
            androidx.compose.animation.core.infiniteRepeatable(tween(CROSS_MS, easing = androidx.compose.animation.core.LinearEasing)),
            label = "across",
        )
        val phase = if (motion) cycle * 2f * Math.PI.toFloat() else 0f
        Spacer(Modifier.weight(1f))
        androidx.compose.foundation.layout.BoxWithConstraints(
            Modifier.fillMaxWidth().height(200.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            val track = maxWidth + 24.dp * 2 + 190.dp
            // A faint path for Piko to walk along.
            androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(10.dp)) {
                val gap = 30.dp.toPx()
                var x = 0f
                while (x < size.width) {
                    drawLine(
                        StepqColors.Deep.copy(alpha = 0.25f),
                        androidx.compose.ui.geometry.Offset(x, size.height / 2),
                        androidx.compose.ui.geometry.Offset(x + 14.dp.toPx(), size.height / 2),
                        strokeWidth = 3.dp.toPx(),
                        cap = androidx.compose.ui.graphics.StrokeCap.Round,
                    )
                    x += gap
                }
            }
            com.stepq.app.ui.components.WalkingPiko(
                phase = phase,
                stride = if (motion) 1f else 0f,
                modifier = Modifier.size(190.dp).padding(bottom = 4.dp).graphicsLayer {
                    if (motion) {
                        // From fully off the left edge (past the screen padding) to fully off the right.
                        translationX = (across - 0.5f) * track.toPx()
                        // A small lift on each step, twice per cycle.
                        translationY = -kotlin.math.abs(kotlin.math.cos(phase)) * 3.dp.toPx()
                    }
                },
            )
        }
        Spacer(Modifier.height(14.dp))
        Box(Modifier.appear(index = 0, offset = 24.dp, delayMs = 900)) { Wordmark() }
        Spacer(Modifier.height(12.dp))
        Text("Chalo, chalte hain.", style = StepqType.Heading.copy(fontSize = 18.sp), color = StepqColors.Ink, modifier = Modifier.appear(0, delayMs = 1150))
        Spacer(Modifier.height(12.dp))
        Text("Come on, let's walk.", style = StepqType.Body, color = StepqColors.Ink, modifier = Modifier.appear(0, delayMs = 1300))
        Spacer(Modifier.weight(1.2f))
        Column(Modifier.appear(0, offset = 40.dp, delayMs = 1450), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
