package com.stepq.app.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.stepq.app.R

val Fredoka = FontFamily(
    Font(R.font.fredoka_500, FontWeight.Medium),
    Font(R.font.fredoka_600, FontWeight.SemiBold),
)

val Nunito = FontFamily(
    Font(R.font.nunito_400, FontWeight.Normal),
    Font(R.font.nunito_700, FontWeight.Bold),
    Font(R.font.nunito_800, FontWeight.ExtraBold),
)

private fun style(family: FontFamily, weight: Int, size: Int, lineHeight: Int, tracking: Double = 0.0) =
    TextStyle(
        fontFamily = family,
        fontWeight = FontWeight(weight),
        fontSize = size.sp,
        lineHeight = lineHeight.sp,
        letterSpacing = if (tracking == 0.0) 0.sp else (tracking / size).em,
    )

/** The app type ramp from Figma ("App/…" styles). */
object StepqType {
    val Display = style(Fredoka, 600, 34, 40)
    val Title = style(Fredoka, 600, 28, 34)
    val Heading = style(Fredoka, 600, 20, 26)
    val Subheading = style(Fredoka, 500, 17, 22)
    val NumberXL = style(Fredoka, 600, 52, 56)
    val NumberL = style(Fredoka, 600, 30, 34, -0.5)
    val NumberM = style(Fredoka, 600, 18, 22)
    val Body = style(Nunito, 400, 15, 22)
    val BodyStrong = style(Nunito, 700, 15, 22)
    val Small = style(Nunito, 400, 13, 18)
    val SmallStrong = style(Nunito, 700, 13, 18)
    /** Shown upper-case, e.g. the EMAIL / PASSWORD field labels. */
    val Label = style(Nunito, 800, 11, 14, 1.2)
    val Button = style(Nunito, 700, 16, 22)
}
