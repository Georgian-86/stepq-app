package com.stepq.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

/** Radius and spacing tokens from Figma. */
object StepqDimens {
    val RadiusS = 12.dp
    val RadiusM = 16.dp
    val RadiusL = 24.dp
    val SpaceXS = 4.dp
    val SpaceS = 8.dp
    val SpaceM = 16.dp
    val SpaceL = 24.dp
    val SpaceXL = 32.dp
}

private val colors = lightColorScheme(
    primary = StepqColors.Deep,
    onPrimary = StepqColors.White,
    secondary = StepqColors.Leaf,
    onSecondary = StepqColors.Ink,
    tertiary = StepqColors.Mango,
    onTertiary = StepqColors.Ink,
    background = StepqColors.White,
    onBackground = StepqColors.Ink,
    surface = StepqColors.White,
    onSurface = StepqColors.Ink,
    surfaceVariant = StepqColors.Mint,
    onSurfaceVariant = StepqColors.Muted,
    outline = StepqColors.Line,
    outlineVariant = StepqColors.Line,
    error = StepqColors.Tomato,
    onError = StepqColors.White,
    errorContainer = StepqColors.TomatoSoft,
    onErrorContainer = StepqColors.TomatoText,
)

private val typography = Typography(
    displaySmall = StepqType.Display,
    headlineMedium = StepqType.Title,
    titleLarge = StepqType.Heading,
    titleMedium = StepqType.Subheading,
    bodyLarge = StepqType.Body,
    bodyMedium = StepqType.Body,
    bodySmall = StepqType.Small,
    labelLarge = StepqType.Button,
    labelMedium = StepqType.SmallStrong,
    labelSmall = StepqType.Label,
)

private val shapes = Shapes(
    small = RoundedCornerShape(StepqDimens.RadiusS),
    medium = RoundedCornerShape(StepqDimens.RadiusM),
    large = RoundedCornerShape(StepqDimens.RadiusL),
)

@Composable
fun StepqTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, typography = typography, shapes = shapes, content = content)
}
