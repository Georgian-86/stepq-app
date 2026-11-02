package com.stepq.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.stepq.app.data.AvatarConfig
import com.stepq.app.ui.theme.StepqColors

/** Palettes for the A08 avatar builder. Stored by index in app.profiles.avatar. */
object AvatarPalette {
    val skin = listOf(Color(0xFFF6D3BC), Color(0xFFE2AC82), Color(0xFFC98642), Color(0xFF8E5426), Color(0xFF5A3418))
    val hair = listOf(Color(0xFF2B1A10), Color(0xFF5B3A1E), Color(0xFFA0522D), Color(0xFF8C8C8C), Color(0xFFE7C26A))
    val outfit = listOf(StepqColors.Deep, StepqColors.Leaf, StepqColors.Tomato, Color(0xFF3B6FD8), StepqColors.Ink)
    val headband = listOf(StepqColors.Mango, StepqColors.Tomato, StepqColors.Leaf, Color(0xFF3B6FD8), StepqColors.White)

    fun options(part: AvatarPart): List<Color> = when (part) {
        AvatarPart.Skin -> skin
        AvatarPart.Hair -> hair
        AvatarPart.Outfit -> outfit
        AvatarPart.Headband -> headband
    }
}

enum class AvatarPart(val label: String) { Skin("Skin"), Hair("Hair"), Outfit("Outfit"), Headband("Headband") }

fun AvatarConfig.get(part: AvatarPart): Int = when (part) {
    AvatarPart.Skin -> skin
    AvatarPart.Hair -> hair
    AvatarPart.Outfit -> outfit
    AvatarPart.Headband -> headband
}

fun AvatarConfig.with(part: AvatarPart, index: Int): AvatarConfig = when (part) {
    AvatarPart.Skin -> copy(skin = index)
    AvatarPart.Hair -> copy(hair = index)
    AvatarPart.Outfit -> copy(outfit = index)
    AvatarPart.Headband -> copy(headband = index)
}

/** The round avatar from A08: head with hair and headband over a shirt, on a mint disc. */
@Composable
fun StepqAvatar(config: AvatarConfig, size: Dp, modifier: Modifier = Modifier) {
    val skin = AvatarPalette.skin.getOrElse(config.skin) { AvatarPalette.skin[2] }
    val hair = AvatarPalette.hair.getOrElse(config.hair) { AvatarPalette.hair[0] }
    val outfit = AvatarPalette.outfit.getOrElse(config.outfit) { AvatarPalette.outfit[0] }
    val band = AvatarPalette.headband.getOrElse(config.headband) { AvatarPalette.headband[0] }
    Canvas(modifier.size(size)) {
        val w = this.size.width
        drawCircle(StepqColors.Mint)
        // Clip the shirt to the disc by drawing it as a large circle low in the frame.
        val clip = androidx.compose.ui.graphics.Path().apply { addOval(androidx.compose.ui.geometry.Rect(0f, 0f, w, w)) }
        clipPath(clip) {
            drawCircle(outfit, radius = w * 0.36f, center = Offset(w / 2, w * 1.02f))
        }
        // Neck and head.
        drawRect(skin, topLeft = Offset(w * 0.44f, w * 0.52f), size = Size(w * 0.12f, w * 0.16f))
        drawCircle(skin, radius = w * 0.2f, center = Offset(w / 2, w * 0.42f))
        // Hair on top, then the headband across it.
        drawArc(hair, 180f, 180f, useCenter = true, topLeft = Offset(w * 0.3f, w * 0.22f), size = Size(w * 0.4f, w * 0.3f))
        drawRoundRect(
            band, topLeft = Offset(w * 0.28f, w * 0.32f), size = Size(w * 0.44f, w * 0.06f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.03f),
        )
        // Eyes and smile.
        val ink = StepqColors.Ink
        drawCircle(ink, radius = w * 0.018f, center = Offset(w * 0.43f, w * 0.45f))
        drawCircle(ink, radius = w * 0.018f, center = Offset(w * 0.57f, w * 0.45f))
        drawArc(
            ink, 20f, 140f, useCenter = false, topLeft = Offset(w * 0.43f, w * 0.44f), size = Size(w * 0.14f, w * 0.1f),
            style = Stroke(width = w * 0.018f, cap = StrokeCap.Round),
        )
    }
}

