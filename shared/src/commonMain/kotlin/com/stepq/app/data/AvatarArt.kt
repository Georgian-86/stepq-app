package com.stepq.app.data

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * One shape of an avatar drawing. Sizes are fractions of the avatar's width (0 to 1), with y
 * growing downwards. Android and iOS draw the same list, so both apps show the same avatar.
 */
abstract class AvatarShape {
    /** Colour as 0xAARRGGBB. */
    abstract val argb: Long

    /** Clip the shape to the round disc (used for the shirt, which runs off the bottom). */
    abstract val clipToDisc: Boolean
}

/** An ellipse, filled, or outlined when [stroke] is above 0. */
class AvatarOval(
    val cx: Float,
    val cy: Float,
    val rx: Float,
    val ry: Float,
    override val argb: Long,
    val stroke: Float = 0f,
    override val clipToDisc: Boolean = false,
) : AvatarShape()

/** A filled rectangle with rounded corners. */
class AvatarRoundRect(
    val x: Float,
    val y: Float,
    val w: Float,
    val h: Float,
    val rx: Float,
    val ry: Float,
    override val argb: Long,
    override val clipToDisc: Boolean = false,
) : AvatarShape()

/**
 * Points as x0, y0, x1, y1, …: a filled polygon, or a line [stroke] wide with round caps when
 * [stroke] is above 0.
 */
class AvatarPoly(
    val points: FloatArray,
    override val argb: Long,
    val stroke: Float = 0f,
    override val clipToDisc: Boolean = false,
) : AvatarShape()

/** The avatar builder's options, palettes and drawing, shared by Android and iOS. */
object AvatarArt {
    val bodies = listOf("Male", "Female")
    val hairStyles = listOf("Short", "Long", "Bun", "Ponytail", "Curly", "Buzz")
    val tops = listOf("Tee", "Hoodie", "Jersey", "Kurta")
    val headwear = listOf("Headband", "Cap", "None")
    val eyewear = listOf("None", "Glasses", "Shades")
    val facialHair = listOf("None", "Stubble", "Beard", "Moustache")
    val extras = listOf("None", "Earrings", "Bindi", "Both")

    // Palettes keep their first five colours in place: saved avatars store the index.
    val skin: List<Long> = listOf(0xFFF6D3BC, 0xFFE2AC82, 0xFFC98642, 0xFF8E5426, 0xFF5A3418, 0xFF3B2213)
    val hair: List<Long> = listOf(0xFF2B1A10, 0xFF5B3A1E, 0xFFA0522D, 0xFF8C8C8C, 0xFFE7C26A, 0xFFB33A2B, 0xFF3B6FD8, 0xFFE77FA8)
    val outfit: List<Long> = listOf(0xFF0A7F3F, 0xFF22C55E, 0xFFF0533A, 0xFF3B6FD8, 0xFF0E2A1A, 0xFF7C5CD6, 0xFFE5739B, 0xFFFFB627)
    val headband: List<Long> = listOf(0xFFFFB627, 0xFFF0533A, 0xFF22C55E, 0xFF3B6FD8, 0xFFFFFFFF, 0xFF7C5CD6, 0xFFE5739B, 0xFF0E2A1A)

    private const val MINT = 0xFFEEF6F0
    private const val INK = 0xFF0E2A1A
    private const val WHITE = 0xFFFFFFFF
    private const val GOLD = 0xFFFFB627
    private const val BINDI = 0xFFD7263D
    private const val BLUSH = 0x55F0533AL

    private fun pick(list: List<Long>, i: Int, fallback: Int) = list.getOrElse(i) { list[fallback] }

    /** Mixes [argb] towards black by [amount] (0 to 1), keeping its alpha. */
    private fun darken(argb: Long, amount: Float): Long {
        fun ch(shift: Int) = (((argb shr shift) and 0xFF) * (1 - amount)).toLong().coerceIn(0, 255) shl shift
        return (argb and 0xFF000000) or ch(16) or ch(8) or ch(0)
    }

    private fun withAlpha(argb: Long, alpha: Int) = (alpha.toLong() shl 24) or (argb and 0xFFFFFF)

    /** Points along an ellipse, angles in degrees clockwise from 3 o'clock. */
    private fun arc(cx: Float, cy: Float, rx: Float, ry: Float, from: Float, to: Float, steps: Int = 24): FloatArray {
        val out = FloatArray((steps + 1) * 2)
        for (i in 0..steps) {
            val a = (from + (to - from) * i / steps) * PI.toFloat() / 180f
            out[i * 2] = cx + rx * cos(a)
            out[i * 2 + 1] = cy + ry * sin(a)
        }
        return out
    }

    private fun circle(cx: Float, cy: Float, r: Float, argb: Long) = AvatarOval(cx, cy, r, r, argb)

    /** The avatar as shapes to draw in order, back to front, on a disc as wide as the avatar. */
    fun shapes(config: AvatarConfig): List<AvatarShape> {
        val female = config.isFemale
        val skin = pick(skin, config.skin, 2)
        val hair = pick(hair, config.hair, 0)
        val outfit = pick(outfit, config.outfit, 0)
        val band = pick(headband, config.headband, 0)
        val out = mutableListOf<AvatarShape>()

        out += AvatarOval(0.5f, 0.5f, 0.5f, 0.5f, MINT)

        // Shirt: a large ellipse low in the frame, narrower at the shoulders for the female figure.
        if (female) {
            out += AvatarOval(0.5f, 1.04f, 0.31f, 0.36f, outfit, clipToDisc = true)
        } else {
            out += AvatarOval(0.5f, 1.02f, 0.36f, 0.36f, outfit, clipToDisc = true)
        }
        val trim = darken(outfit, 0.22f)
        when (config.top) {
            1 -> { // Hoodie: hood bunched around the neck.
                out += AvatarOval(0.5f, 0.685f, 0.15f, 0.05f, trim, clipToDisc = true)
            }
            3 -> { // Kurta: band collar and a buttoned placket.
                out += AvatarRoundRect(0.49f, 0.69f, 0.02f, 0.13f, 0.01f, 0.01f, trim, clipToDisc = true)
                out += circle(0.5f, 0.725f, 0.009f, GOLD)
                out += circle(0.5f, 0.765f, 0.009f, GOLD)
            }
        }

        if (config.top == 0 && female) out += AvatarOval(0.5f, 0.68f, 0.07f, 0.03f, skin, clipToDisc = true) // scoop neck

        // Hair that falls behind the head.
        when (config.style) {
            0 -> if (female) out += AvatarRoundRect(0.27f, 0.25f, 0.46f, 0.34f, 0.16f, 0.14f, hair) // bob
            1 -> out += AvatarRoundRect(0.23f, 0.24f, 0.54f, 0.52f, 0.22f, 0.18f, hair)
            2 -> out += circle(0.5f, 0.19f, 0.09f, hair)
            3 -> {
                out += AvatarOval(0.7f, 0.5f, 0.065f, 0.15f, hair)
                out += circle(0.66f, 0.35f, 0.06f, hair)
            }
            4 -> {
                for (i in 0..9) {
                    val a = (150f + 24f * i) * PI.toFloat() / 180f
                    out += circle(0.5f + 0.2f * cos(a), 0.41f + 0.19f * sin(a), 0.07f, hair)
                }
            }
        }

        // Neck and head.
        out += AvatarRoundRect(0.44f, 0.52f, 0.12f, 0.16f, 0f, 0f, skin)
        when (config.top) {
            1 -> { // Hoodie drawstrings.
                out += AvatarPoly(floatArrayOf(0.465f, 0.72f, 0.465f, 0.8f), WHITE, stroke = 0.012f, clipToDisc = true)
                out += AvatarPoly(floatArrayOf(0.535f, 0.72f, 0.535f, 0.8f), WHITE, stroke = 0.012f, clipToDisc = true)
            }
            2 -> { // Jersey: a white V-neck.
                out += AvatarPoly(floatArrayOf(0.42f, 0.665f, 0.58f, 0.665f, 0.5f, 0.77f), WHITE, clipToDisc = true)
                out += AvatarPoly(floatArrayOf(0.45f, 0.665f, 0.55f, 0.665f, 0.5f, 0.735f), skin, clipToDisc = true)
            }
            3 -> out += AvatarRoundRect(0.435f, 0.655f, 0.13f, 0.035f, 0.015f, 0.015f, trim, clipToDisc = true)
        }
        out += AvatarOval(0.5f, 0.42f, if (female) 0.19f else 0.2f, 0.2f, skin)

        // Facial hair, under the face.
        val jaw = arc(0.5f, 0.42f, 0.2f, 0.2f, 20f, 160f)
        when (config.facialHair) {
            1 -> out += AvatarPoly(jaw, withAlpha(hair, 0x59))
            2 -> {
                out += AvatarPoly(jaw, hair)
                out += AvatarOval(0.5f, 0.51f, 0.08f, 0.045f, skin)
            }
        }

        // Face: eyes, lashes and blush for the female figure, and a smile.
        out += circle(0.43f, 0.45f, 0.018f, INK)
        out += circle(0.57f, 0.45f, 0.018f, INK)
        if (female) {
            out += AvatarPoly(floatArrayOf(0.414f, 0.446f, 0.397f, 0.438f), INK, stroke = 0.009f)
            out += AvatarPoly(floatArrayOf(0.586f, 0.446f, 0.603f, 0.438f), INK, stroke = 0.009f)
            out += AvatarOval(0.38f, 0.5f, 0.032f, 0.02f, BLUSH)
            out += AvatarOval(0.62f, 0.5f, 0.032f, 0.02f, BLUSH)
        }
        out += AvatarPoly(arc(0.5f, 0.49f, 0.07f, 0.05f, 20f, 160f), INK, stroke = 0.018f)
        if (config.facialHair == 3) {
            out += AvatarOval(0.465f, 0.49f, 0.04f, 0.015f, hair)
            out += AvatarOval(0.535f, 0.49f, 0.04f, 0.015f, hair)
        }

        // Hair on top of the head.
        when (config.style) {
            5 -> out += AvatarPoly(arc(0.5f, 0.35f, 0.195f, 0.13f, 180f, 360f), hair)
            4 -> {
                out += AvatarPoly(arc(0.5f, 0.37f, 0.2f, 0.15f, 180f, 360f), hair)
                for (i in 0..6) {
                    val a = (195f + 25f * i) * PI.toFloat() / 180f
                    out += circle(0.5f + 0.18f * cos(a), 0.37f + 0.14f * sin(a), 0.05f, hair)
                }
            }
            else -> out += AvatarPoly(arc(0.5f, 0.37f, 0.2f, 0.15f, 180f, 360f), hair)
        }
        // Locks that frame the face for long hair and the bob.
        if (config.style == 1 || (config.style == 0 && female)) {
            out += AvatarRoundRect(0.295f, 0.34f, 0.055f, if (config.style == 1) 0.24f else 0.2f, 0.027f, 0.027f, hair)
            out += AvatarRoundRect(0.65f, 0.34f, 0.055f, if (config.style == 1) 0.24f else 0.2f, 0.027f, 0.027f, hair)
        }

        // Earrings and bindi.
        if (config.extra == 1 || config.extra == 3) {
            out += circle(if (female) 0.322f else 0.31f, 0.52f, 0.02f, GOLD)
            out += circle(if (female) 0.678f else 0.69f, 0.52f, 0.02f, GOLD)
        }
        if (config.extra == 2 || config.extra == 3) out += circle(0.5f, 0.398f, 0.013f, BINDI)

        // Glasses or shades.
        when (config.eyewear) {
            1 -> {
                out += AvatarOval(0.43f, 0.45f, 0.048f, 0.044f, INK, stroke = 0.012f)
                out += AvatarOval(0.57f, 0.45f, 0.048f, 0.044f, INK, stroke = 0.012f)
                out += AvatarPoly(floatArrayOf(0.478f, 0.448f, 0.522f, 0.448f), INK, stroke = 0.012f)
            }
            2 -> {
                out += AvatarRoundRect(0.375f, 0.423f, 0.105f, 0.06f, 0.025f, 0.025f, INK)
                out += AvatarRoundRect(0.52f, 0.423f, 0.105f, 0.06f, 0.025f, 0.025f, INK)
                out += AvatarPoly(floatArrayOf(0.475f, 0.44f, 0.525f, 0.44f), INK, stroke = 0.014f)
            }
        }

        // Headband or cap.
        when (config.headwear) {
            0 -> out += AvatarRoundRect(0.28f, 0.32f, 0.44f, 0.06f, 0.03f, 0.03f, band)
            1 -> {
                out += AvatarPoly(arc(0.5f, 0.355f, 0.21f, 0.16f, 180f, 360f), band)
                out += AvatarRoundRect(0.29f, 0.335f, 0.42f, 0.035f, 0.017f, 0.017f, darken(band, 0.15f))
                out += AvatarRoundRect(0.5f, 0.335f, 0.3f, 0.04f, 0.02f, 0.02f, darken(band, 0.15f))
            }
        }
        return out
    }
}
