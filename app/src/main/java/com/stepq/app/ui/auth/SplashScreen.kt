package com.stepq.app.ui.auth

import androidx.compose.foundation.Image
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.drawscope.clipRect
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
        // Piko walks towards you in the middle of the screen, endlessly, while the path behind
        // it moves away to the horizon, so it looks like Piko keeps walking forward. The
        // wordmark rises in, the taglines follow and the buttons slide up.
        val motion = com.stepq.app.ui.components.LocalMotionEnabled.current
        val walk = androidx.compose.animation.core.rememberInfiniteTransition(label = "walk")
        val cycle by walk.animateFloat(
            0f, 1f,
            androidx.compose.animation.core.infiniteRepeatable(tween(STEP_MS, easing = androidx.compose.animation.core.LinearEasing)),
            label = "step",
        )
        val phase = if (motion) cycle * 2f * Math.PI.toFloat() else 0f
        Spacer(Modifier.weight(1f))
        Box(Modifier.fillMaxWidth().height(270.dp), contentAlignment = Alignment.BottomCenter) {
            // The path runs from under Piko's feet back to the horizon. Its markings move away
            // into the distance by one gap per step, so it loops seamlessly.
            androidx.compose.foundation.Canvas(Modifier.matchParentSize()) {
                drawPerspectivePath(if (motion) cycle else 0f)
            }
            com.stepq.app.ui.components.WalkingPiko(
                phase = phase,
                stride = if (motion) 1f else 0f,
                modifier = Modifier.padding(bottom = 34.dp).size(170.dp),
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

/**
 * A path seen in perspective: wide at the bottom, narrowing to the horizon. [travel] (0..1) moves
 * the centre dashes and the grass tufts one gap further away.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPerspectivePath(travel: Float) {
    val w = size.width
    val h = size.height
    val cx = w / 2
    val horizon = h * 0.30f
    val nearHalf = w * 0.50f
    val farHalf = w * 0.015f
    // Depth z: 1 at the bottom edge, growing into the distance. Screen y falls off as 1/z.
    fun y(z: Float) = horizon + (h - horizon) / z
    fun half(z: Float) = farHalf + (nearHalf - farHalf) / z
    // Fades out at the horizon and towards the bottom edge.
    fun fade(z: Float) = ((y(z) - horizon) / (h * 0.35f)).coerceIn(0f, 1f) * ((h - y(z)) / (h * 0.15f)).coerceIn(0f, 1f)
    clipRect(0f, horizon, w, h) { drawRoad(cx, horizon, h, farHalf, nearHalf, travel, ::y, ::half, ::fade) }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRoad(
    cx: Float, horizon: Float, h: Float, farHalf: Float, nearHalf: Float, travel: Float,
    y: (Float) -> Float, half: (Float) -> Float, fade: (Float) -> Float,
) {
    val road = androidx.compose.ui.graphics.Path().apply {
        moveTo(cx - farHalf, horizon); lineTo(cx + farHalf, horizon)
        lineTo(cx + nearHalf, h); lineTo(cx - nearHalf, h); close()
    }
    drawPath(
        road,
        androidx.compose.ui.graphics.Brush.verticalGradient(
            0f to StepqColors.White.copy(alpha = 0f),
            0.45f to StepqColors.White.copy(alpha = 0.22f),
            0.8f to StepqColors.White.copy(alpha = 0.3f),
            1f to StepqColors.White.copy(alpha = 0f),
            startY = horizon, endY = h,
        ),
    )
    // Edges.
    for (side in listOf(-1f, 1f)) {
        var z = 1f
        while (z < 40f) {
            val z2 = z * 1.08f
            drawLine(
                StepqColors.White.copy(alpha = 0.55f * fade(z)),
                androidx.compose.ui.geometry.Offset(cx + side * half(z), y(z)),
                androidx.compose.ui.geometry.Offset(cx + side * half(z2), y(z2)),
                strokeWidth = (2.5f / z + 0.6f) * density,
            )
            z = z2
        }
    }
    val gap = 0.32f
    // Centre dashes, moving away.
    for (i in -1..40) {
        val z1 = 1f + (i + travel) * gap
        if (z1 < 0.6f) continue
        val z2 = z1 + gap * 0.5f
        val a = fade(z2)
        if (a <= 0f) continue
        drawLine(
            StepqColors.Deep.copy(alpha = 0.45f * a),
            androidx.compose.ui.geometry.Offset(cx, y(z1)),
            androidx.compose.ui.geometry.Offset(cx, y(z2)),
            strokeWidth = (5f / z1).coerceIn(0.8f, 5f) * density,
            cap = androidx.compose.ui.graphics.StrokeCap.Round,
        )
    }
    // Grass tufts on both sides, moving away with the path. The right side is offset by half a
    // gap so the two sides alternate.
    for (side in listOf(-1f, 1f)) for (i in -1..30) {
        val z = 1f + (i + travel + if (side > 0) 0.5f else 0f) * gap * 2f
        if (z < 0.7f) continue
        val a = fade(z)
        if (a <= 0f) continue
        val x = cx + side * (half(z) * 1.25f + 14f * density / z)
        val base = y(z)
        val s = 16f * density / z
        val c = StepqColors.Deep.copy(alpha = 0.6f * a)
        val sw = (3f / z).coerceAtLeast(0.6f) * density
        drawLine(c, androidx.compose.ui.geometry.Offset(x, base), androidx.compose.ui.geometry.Offset(x - s * 0.45f, base - s), strokeWidth = sw, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        drawLine(c, androidx.compose.ui.geometry.Offset(x, base), androidx.compose.ui.geometry.Offset(x, base - s * 1.2f), strokeWidth = sw, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        drawLine(c, androidx.compose.ui.geometry.Offset(x, base), androidx.compose.ui.geometry.Offset(x + s * 0.45f, base - s), strokeWidth = sw, cap = androidx.compose.ui.graphics.StrokeCap.Round)
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
