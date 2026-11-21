package com.stepq.app.ui.components

import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.stepq.app.ui.theme.StepqColors
import com.stepq.app.ui.theme.StepqType
import kotlin.math.PI

/** One full walk cycle (a left step and a right step). */
const val WALK_CYCLE_MS = 640

/**
 * Where Piko is in its walk cycle, 0..1. It follows the frame clock rather than starting from 0,
 * so every walking Piko on screen steps in time, and moving from the launch screen to the
 * Log in / Sign up screen doesn't make Piko skip. 0 when animations are off.
 */
@Composable
fun rememberWalkCycle(): Float {
    val motion = LocalMotionEnabled.current
    var cycle by remember { mutableFloatStateOf(0f) }
    if (motion) {
        LaunchedEffect(Unit) {
            while (true) {
                withInfiniteAnimationFrameNanos { n -> cycle = ((n / 1_000_000L) % WALK_CYCLE_MS) / WALK_CYCLE_MS.toFloat() }
            }
        }
    }
    return if (motion) cycle else 0f
}

/** Colours for the path, on the green launch screen or on a white page. */
data class PathColors(val road: Color, val roadAlpha: Float, val edge: Color, val mark: Color)

val OnGreenPath = PathColors(road = Color.White, roadAlpha = 0.32f, edge = Color.White.copy(alpha = 0.55f), mark = StepqColors.Deep)
val OnWhitePath = PathColors(road = StepqColors.LeafSoft, roadAlpha = 1f, edge = StepqColors.Leaf.copy(alpha = 0.45f), mark = StepqColors.Deep)

/**
 * Piko walking towards you on a path that runs back to the horizon: the app's loader. The path's
 * markings and the grass beside it move away behind Piko, so it looks like Piko walks forward
 * forever.
 */
@Composable
fun WalkingScene(modifier: Modifier = Modifier, pikoSize: Dp = 170.dp, colors: PathColors = OnGreenPath, pikoBottom: Dp = pikoSize * 0.2f) {
    val cycle = rememberWalkCycle()
    val motion = LocalMotionEnabled.current
    Box(modifier, contentAlignment = Alignment.BottomCenter) {
        Canvas(Modifier.matchParentSize()) { drawPerspectivePath(cycle, colors) }
        WalkingPiko(
            phase = cycle * 2f * PI.toFloat(),
            stride = if (motion) 1f else 0f,
            modifier = Modifier.padding(bottom = pikoBottom).size(pikoSize),
            contentDescription = null,
        )
    }
}

/** A loading state: walking Piko in the middle of the page, with an optional line under it. */
@Composable
fun PikoLoader(modifier: Modifier = Modifier, label: String? = null, pikoSize: Dp = 110.dp) {
    Column(
        modifier.fillMaxWidth().semantics { contentDescription = label ?: "Loading" },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        WalkingScene(Modifier.width(pikoSize * 2.2f).height(pikoSize * 1.45f), pikoSize, OnWhitePath)
        if (label != null) {
            Spacer(Modifier.height(10.dp))
            Text(label, style = StepqType.Small, color = StepqColors.Muted)
        }
    }
}

/** Small walking Piko for tight spots, like a button while it waits. */
@Composable
fun PikoSpinner(size: Dp, modifier: Modifier = Modifier) {
    val cycle = rememberWalkCycle()
    val motion = LocalMotionEnabled.current
    WalkingPiko(
        phase = cycle * 2f * PI.toFloat(),
        stride = if (motion) 1f else 0f,
        modifier = modifier.size(size).semantics { contentDescription = "Loading" },
        contentDescription = null,
    )
}

/**
 * A path seen in perspective: wide at the bottom, narrowing to the horizon. [travel] (0..1) moves
 * the centre dashes and the grass tufts one gap further away.
 */
fun DrawScope.drawPerspectivePath(travel: Float, colors: PathColors = OnGreenPath) {
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
    val unit = w / 360f // sizes below are in dp for a 360dp-wide scene, scaled to this one
    clipRect(0f, horizon, w, h) {
        val road = Path().apply {
            moveTo(cx - farHalf, horizon); lineTo(cx + farHalf, horizon)
            lineTo(cx + nearHalf, h); lineTo(cx - nearHalf, h); close()
        }
        drawPath(
            road,
            Brush.verticalGradient(
                0f to colors.road.copy(alpha = 0f),
                0.45f to colors.road.copy(alpha = colors.roadAlpha * 0.7f),
                0.8f to colors.road.copy(alpha = colors.roadAlpha * 0.95f),
                1f to colors.road.copy(alpha = 0f),
                startY = horizon, endY = h,
            ),
        )
        for (side in listOf(-1f, 1f)) {
            var z = 1f
            while (z < 40f) {
                val z2 = z * 1.08f
                drawLine(
                    colors.edge.copy(alpha = colors.edge.alpha * fade(z)),
                    Offset(cx + side * half(z), y(z)),
                    Offset(cx + side * half(z2), y(z2)),
                    strokeWidth = (2.5f / z + 0.6f) * unit,
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
                colors.mark.copy(alpha = 0.45f * a),
                Offset(cx, y(z1)),
                Offset(cx, y(z2)),
                strokeWidth = (5f / z1).coerceIn(0.8f, 5f) * unit,
                cap = StrokeCap.Round,
            )
        }
        // Grass tufts on both sides, moving away with the path. The right side is offset by half
        // a gap so the two sides alternate.
        for (side in listOf(-1f, 1f)) for (i in -1..30) {
            val z = 1f + (i + travel + if (side > 0) 0.5f else 0f) * gap * 2f
            if (z < 0.7f) continue
            val a = fade(z)
            if (a <= 0f) continue
            val x = cx + side * (half(z) * 1.25f + 14f * unit / z)
            val base = y(z)
            val s = 16f * unit / z
            val c = colors.mark.copy(alpha = 0.6f * a)
            val sw = (3f / z).coerceAtLeast(0.6f) * unit
            drawLine(c, Offset(x, base), Offset(x - s * 0.45f, base - s), strokeWidth = sw, cap = StrokeCap.Round)
            drawLine(c, Offset(x, base), Offset(x, base - s * 1.2f), strokeWidth = sw, cap = StrokeCap.Round)
            drawLine(c, Offset(x, base), Offset(x + s * 0.45f, base - s), strokeWidth = sw, cap = StrokeCap.Round)
        }
    }
}
