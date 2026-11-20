package com.stepq.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.stepq.app.R
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

private val Ink = Color(0xFF0E2A1A)
private val Shoe = Color(0xFFFF7A3D)

/** Where one foot is in the walk cycle: [depth] is +1 nearest the viewer and -1 furthest back. */
internal data class FootPose(val depth: Float, val lift: Float)

/**
 * One leg's pose at [t] (0..1) of its own cycle. The first half is the swing: the foot lifts and
 * comes forward, towards the viewer. The second half is the stance: the foot is on the ground and
 * slides back under Piko as the ground moves away behind it.
 */
internal fun footPose(t: Float): FootPose {
    val u = ((t % 1f) + 1f) % 1f
    return if (u < 0.5f) {
        val s = u / 0.5f
        val eased = s * s * (3 - 2 * s)
        FootPose(depth = -1f + 2f * eased, lift = sin(PI.toFloat() * s))
    } else {
        FootPose(depth = 1f - 2f * (u - 0.5f) / 0.5f, lift = 0f)
    }
}

/**
 * Piko walking towards the viewer, seen from the front. [phase] is the step cycle in radians: one
 * left step and one right step per 2π. Each foot lifts and steps forward in turn, then slides back
 * while it carries Piko; the body bobs up over the standing leg and shifts its weight a little.
 * [stride] 0 is Piko standing still.
 */
@Composable
fun WalkingPiko(phase: Float, stride: Float, modifier: Modifier = Modifier, contentDescription: String? = "Piko") {
    val body = rememberVectorPainter(ImageVector.vectorResource(R.drawable.piko_body))
    val m = if (contentDescription != null) modifier.semantics { this.contentDescription = contentDescription } else modifier
    Canvas(m) {
        val u = size.width / 120f // the art is drawn on a 120×120 grid
        val t = phase / (2f * PI.toFloat())
        val left = footPose(t).scaled(stride)
        val right = footPose(t + 0.5f).scaled(stride)
        val sw = sin(phase) * stride
        // Up over the standing leg, down when both feet are on the ground.
        val bob = -abs(sw) * 2.6f
        // Weight moves onto the standing leg: towards the right while the left foot swings.
        val sway = sw * 1.4f
        val tilt = sw * 2.5f

        // Shadow, a little smaller when Piko is up.
        val shadowW = 58f - abs(sw) * 4f
        drawOval(Ink.copy(alpha = 0.12f), Offset((62f - shadowW / 2) * u, 107.5f * u), Size(shadowW * u, 6f * u))

        // Legs first, so the body covers the hips. The nearer foot is drawn last.
        val legs = listOf(Leg(51f, left, -1), Leg(73f, right, 1)).sortedBy { it.pose.depth }
        for (leg in legs) drawLeg(leg, u, hipDx = sway, hipDy = bob)

        // Arms swing against the legs: the right arm comes forward with the left foot.
        drawArm(shoulder = Offset(34f + sway, 63f + bob), side = -1, forward = -sw, u = u)
        drawArm(shoulder = Offset(91f + sway, 67f + bob), side = 1, forward = sw, u = u)

        translate(sway * u, bob * u) {
            rotate(tilt, pivot = Offset(62f * u, 84f * u)) {
                with(body) { draw(size) }
            }
        }
    }
}

private fun FootPose.scaled(stride: Float) = FootPose(depth * stride, lift * stride)

private data class Leg(val hipX: Float, val pose: FootPose, val side: Int)

private fun DrawScope.drawLeg(leg: Leg, u: Float, hipDx: Float, hipDy: Float) {
    val (depth, lift) = leg.pose
    // A nearer foot sits lower on screen and looks bigger; a lifted one rises.
    val footScale = 1f + depth * 0.13f + lift * 0.06f
    val footX = leg.hipX + leg.side * depth * 1.2f
    val footY = 104f + depth * 2.6f - lift * 8f
    val hip = Offset(leg.hipX + hipDx, 80f + hipDy)
    // The knee bends outward when the foot is up.
    val knee = Offset((hip.x + footX) / 2 + leg.side * lift * 3.5f, (hip.y + footY) / 2)
    val path = Path().apply {
        moveTo(hip.x * u, hip.y * u)
        quadraticTo(knee.x * u, knee.y * u, footX * u, (footY - 4f) * u)
    }
    drawPath(path, Ink, style = Stroke(width = 4.2f * u, cap = StrokeCap.Round))
    // The shoe, seen from the front: an orange cap with a white sole and laces.
    scale(footScale, pivot = Offset(footX * u, footY * u)) {
        val w = 15f * u
        val h = 9f * u
        val topLeft = Offset(footX * u - w / 2, (footY - 5f) * u)
        drawRoundRect(Shoe, topLeft, Size(w, h), CornerRadius(4.5f * u))
        drawRoundRect(Ink, topLeft, Size(w, h), CornerRadius(4.5f * u), style = Stroke(2.4f * u))
        drawLine(Color.White, Offset(topLeft.x + 2.4f * u, topLeft.y + h - 2.3f * u), Offset(topLeft.x + w - 2.4f * u, topLeft.y + h - 2.3f * u), strokeWidth = 1.8f * u, cap = StrokeCap.Round)
        drawLine(Color.White, Offset(footX * u - 2.4f * u, topLeft.y + 2.4f * u), Offset(footX * u - 1.4f * u, topLeft.y + 4.2f * u), strokeWidth = 1.2f * u, cap = StrokeCap.Round)
        drawLine(Color.White, Offset(footX * u + 1.4f * u, topLeft.y + 2.4f * u), Offset(footX * u + 2.4f * u, topLeft.y + 4.2f * u), strokeWidth = 1.2f * u, cap = StrokeCap.Round)
    }
}

private fun DrawScope.drawArm(shoulder: Offset, side: Int, forward: Float, u: Float) {
    // Seen from the front, an arm swinging forward drops a little and its hand comes nearer
    // (bigger); swinging back, the hand rises and tucks in behind.
    val hand = Offset(shoulder.x + side * (9f - forward * 1.5f), shoulder.y + 13f + forward * 2.5f)
    val elbow = Offset(shoulder.x + side * 8f, shoulder.y + 6f)
    val path = Path().apply {
        moveTo(shoulder.x * u, shoulder.y * u)
        quadraticTo(elbow.x * u, elbow.y * u, hand.x * u, hand.y * u)
    }
    drawPath(path, Ink, style = Stroke(width = 4.2f * u, cap = StrokeCap.Round))
    drawCircle(Ink, radius = (3.6f + forward * 0.6f) * u, center = Offset(hand.x * u, hand.y * u))
}
