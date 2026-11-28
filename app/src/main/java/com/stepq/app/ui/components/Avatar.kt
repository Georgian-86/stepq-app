package com.stepq.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.Dp
import com.stepq.app.data.AvatarArt
import com.stepq.app.data.AvatarConfig
import com.stepq.app.data.AvatarOval
import com.stepq.app.data.AvatarPoly
import com.stepq.app.data.AvatarRoundRect
import com.stepq.app.data.AvatarShape

/** Palettes for the A08 avatar builder, from the shared [AvatarArt]. Stored by index in app.profiles.avatar. */
object AvatarPalette {
    val skin = AvatarArt.skin.map { Color(it) }
    val hair = AvatarArt.hair.map { Color(it) }
    val outfit = AvatarArt.outfit.map { Color(it) }
    val headband = AvatarArt.headband.map { Color(it) }
}

/** One row of choices in the avatar builder: named shapes shown as small avatars, or colours. */
sealed class AvatarChoices(val label: String, val get: (AvatarConfig) -> Int, val set: (AvatarConfig, Int) -> AvatarConfig) {
    class Shapes(label: String, val names: List<String>, get: (AvatarConfig) -> Int, set: (AvatarConfig, Int) -> AvatarConfig) :
        AvatarChoices(label, get, set)

    class Colors(label: String, val colors: List<Color>, get: (AvatarConfig) -> Int, set: (AvatarConfig, Int) -> AvatarConfig) :
        AvatarChoices(label, get, set)
}

/** The builder's tabs and what each one changes. */
enum class AvatarPart(val label: String) {
    Body("Body"), Hair("Hair"), Skin("Skin"), Outfit("Outfit"), Headwear("Headwear"), Face("Face"), Extras("Extras");

    val choices: List<AvatarChoices>
        get() = when (this) {
            Body -> listOf(
                AvatarChoices.Shapes("Body", AvatarArt.bodies, { if (it.isFemale) 1 else 0 }) { c, i -> c.copy(body = i) },
            )
            Hair -> listOf(
                AvatarChoices.Shapes("Style", AvatarArt.hairStyles, { it.style }) { c, i -> c.copy(style = i) },
                AvatarChoices.Colors("Colour", AvatarPalette.hair, { it.hair }) { c, i -> c.copy(hair = i) },
            )
            Skin -> listOf(
                AvatarChoices.Colors("Skin tone", AvatarPalette.skin, { it.skin }) { c, i -> c.copy(skin = i) },
            )
            Outfit -> listOf(
                AvatarChoices.Shapes("Top", AvatarArt.tops, { it.top }) { c, i -> c.copy(top = i) },
                AvatarChoices.Colors("Colour", AvatarPalette.outfit, { it.outfit }) { c, i -> c.copy(outfit = i) },
            )
            Headwear -> listOf(
                AvatarChoices.Shapes("Headwear", AvatarArt.headwear, { it.headwear }) { c, i -> c.copy(headwear = i) },
                AvatarChoices.Colors("Colour", AvatarPalette.headband, { it.headband }) { c, i -> c.copy(headband = i) },
            )
            Face -> listOf(
                AvatarChoices.Shapes("Glasses", AvatarArt.eyewear, { it.eyewear }) { c, i -> c.copy(eyewear = i) },
                AvatarChoices.Shapes("Facial hair", AvatarArt.facialHair, { it.facialHair }) { c, i -> c.copy(facialHair = i) },
            )
            Extras -> listOf(
                AvatarChoices.Shapes("Extras", AvatarArt.extras, { it.extra }) { c, i -> c.copy(extra = i) },
            )
        }
}

/** The round avatar from A08, drawn from the shared [AvatarArt] shapes on a mint disc. */
@Composable
fun StepqAvatar(config: AvatarConfig, size: Dp, modifier: Modifier = Modifier) {
    val shapes = remember(config) { AvatarArt.shapes(config) }
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val disc = Path().apply { addOval(Rect(0f, 0f, w, w)) }
        shapes.forEach { shape ->
            if (shape.clipToDisc) clipPath(disc) { draw(shape, w) } else draw(shape, w)
        }
    }
}

private fun DrawScope.draw(shape: AvatarShape, w: Float) {
    val color = Color(shape.argb)
    when (shape) {
        is AvatarOval -> drawOval(
            color,
            topLeft = Offset((shape.cx - shape.rx) * w, (shape.cy - shape.ry) * w),
            size = Size(shape.rx * 2 * w, shape.ry * 2 * w),
            style = if (shape.stroke > 0f) Stroke(shape.stroke * w) else Fill,
        )
        is AvatarRoundRect -> drawRoundRect(
            color,
            topLeft = Offset(shape.x * w, shape.y * w),
            size = Size(shape.w * w, shape.h * w),
            cornerRadius = CornerRadius(shape.rx * w, shape.ry * w),
        )
        is AvatarPoly -> {
            val p = shape.points
            val path = Path().apply {
                moveTo(p[0] * w, p[1] * w)
                var i = 2
                while (i + 1 < p.size) {
                    lineTo(p[i] * w, p[i + 1] * w)
                    i += 2
                }
                if (shape.stroke <= 0f) close()
            }
            drawPath(
                path, color,
                style = if (shape.stroke > 0f) Stroke(shape.stroke * w, cap = StrokeCap.Round, join = StrokeJoin.Round) else Fill,
            )
        }
    }
}
