package com.stepq.app.ui.auth

import androidx.compose.foundation.Image
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import com.stepq.app.ui.components.appear
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
        // Intro (reference reel 1): Piko walks in from the left, step by step, then keeps
        // walking on the spot while the ground slides past, so it looks like Piko is out for a
        // walk. The wordmark rises in, the taglines follow and the buttons slide up.
        val motion = com.stepq.app.ui.components.LocalMotionEnabled.current
        val enter = remember { Animatable(if (motion) 0f else 1f) }
        LaunchedEffect(Unit) {
            if (motion) enter.animateTo(1f, tween(1500, easing = androidx.compose.animation.core.LinearOutSlowInEasing))
        }
        val cycle = if (motion) {
            androidx.compose.animation.core.rememberInfiniteTransition(label = "walk").animateFloat(
                0f, 1f,
                androidx.compose.animation.core.infiniteRepeatable(tween(620, easing = androidx.compose.animation.core.LinearEasing)),
                label = "step",
            ).value
        } else {
            0f
        }
        val phase = cycle * 2f * Math.PI.toFloat()
        val density = LocalDensity.current
        Spacer(Modifier.weight(1f))
        Box(Modifier.size(width = 260.dp, height = 200.dp), contentAlignment = Alignment.BottomCenter) {
            // The ground: dashes that slide left once Piko is walking on the spot.
            if (motion) {
                androidx.compose.foundation.Canvas(Modifier.size(width = 260.dp, height = 14.dp)) {
                    val gap = 34.dp.toPx()
                    val dash = 16.dp.toPx()
                    val shift = (cycle * gap) * enter.value
                    var x = -shift
                    val fade = enter.value
                    while (x < size.width) {
                        val edge = (kotlin.math.min(x, size.width - x) / (size.width * 0.3f)).coerceIn(0f, 1f)
                        drawLine(
                            StepqColors.Deep.copy(alpha = 0.35f * fade * edge),
                            androidx.compose.ui.geometry.Offset(x, size.height / 2),
                            androidx.compose.ui.geometry.Offset(x + dash, size.height / 2),
                            strokeWidth = 3.dp.toPx(),
                            cap = androidx.compose.ui.graphics.StrokeCap.Round,
                        )
                        x += gap
                    }
                }
            }
            com.stepq.app.ui.components.WalkingPiko(
                phase = phase,
                stride = if (motion) 1f else 0f,
                modifier = Modifier.size(190.dp).padding(bottom = 4.dp).graphicsLayer {
                    translationX = -(1f - enter.value) * with(density) { 420.dp.toPx() }
                    // Up on each step: twice per cycle.
                    translationY = -kotlin.math.abs(kotlin.math.cos(phase)) * with(density) { 5.dp.toPx() }
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
