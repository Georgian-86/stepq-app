package com.stepq.app.ui.auth

import androidx.compose.foundation.Image
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
        // Intro (reference reel 1): Piko walks in from the left with a bouncy step and lands,
        // then the wordmark rises in, the taglines follow and the buttons slide up.
        val motion = com.stepq.app.ui.components.LocalMotionEnabled.current
        val walk = remember { Animatable(if (motion) 0f else 1f) }
        val land = remember { Animatable(if (motion) 0f else 1f) }
        LaunchedEffect(Unit) {
            if (!motion) return@LaunchedEffect
            walk.animateTo(1f, tween(1100, easing = FastOutSlowInEasing))
            land.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = 500f))
        }
        val density = LocalDensity.current
        Spacer(Modifier.weight(1f))
        Image(
            painterResource(R.drawable.piko_happy), contentDescription = "Piko",
            Modifier.size(190.dp).graphicsLayer {
                val t = walk.value
                translationX = -(1f - t) * with(density) { 420.dp.toPx() }
                // Steps: a quick hop and a lean while walking, settling as it arrives.
                val step = kotlin.math.abs(kotlin.math.sin(t * 6f * Math.PI.toFloat()))
                translationY = -step * (1f - t) * with(density) { 26.dp.toPx() }
                rotationZ = kotlin.math.sin(t * 6f * Math.PI.toFloat()) * 8f * (1f - t)
                // Landing squash.
                val squash = (1f - land.value) * 0.12f
                scaleY = 1f - squash
                scaleX = 1f + squash * 0.6f
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
            },
        )
        Spacer(Modifier.height(24.dp))
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
