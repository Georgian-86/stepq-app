package com.stepq.app.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * The accent colours a theme swaps. Ink, Muted, Mango and Tomato stay the same in every theme so
 * text contrast and warnings never change.
 */
data class StepqPalette(
    val key: String,
    val label: String,
    val leaf: Color,
    val deep: Color,
    val mint: Color,
    val line: Color,
    val leafSoft: Color,
    val page: Color,
)

/** App themes. Leaf is everyone's; the rest come with StepQ Pro (feature change_theme). */
object StepqThemes {
    val Leaf = StepqPalette("leaf", "Leaf", Color(0xFF22C55E), Color(0xFF0A7F3F), Color(0xFFEEF6F0), Color(0xFFDCE7DF), Color(0xFFDDF7E6), Color(0xFFF6F9F6))
    val Ocean = StepqPalette("ocean", "Ocean", Color(0xFF38BDF8), Color(0xFF0B5E8A), Color(0xFFEAF5FB), Color(0xFFD3E4EE), Color(0xFFD7EEFA), Color(0xFFF5FAFD))
    val Grape = StepqPalette("grape", "Grape", Color(0xFFA78BFA), Color(0xFF5B3BA8), Color(0xFFF3F0FC), Color(0xFFE0DAF1), Color(0xFFE7E0FB), Color(0xFFF8F6FD))
    val Sunset = StepqPalette("sunset", "Sunset", Color(0xFFFB923C), Color(0xFFB8400C), Color(0xFFFFF2EA), Color(0xFFF2DFD3), Color(0xFFFFE3D1), Color(0xFFFFF8F3))
    val Gold = StepqPalette("gold", "Gold", Color(0xFFF5B83D), Color(0xFF8A5A00), Color(0xFFFFF8E6), Color(0xFFF0E3C4), Color(0xFFFFEFC4), Color(0xFFFFFBF2))

    val all = listOf(Leaf, Ocean, Grape, Sunset, Gold)

    fun byKey(key: String?): StepqPalette = all.firstOrNull { it.key == key } ?: Leaf
}

/** StepQ brand colours, matching the Figma variables and packages/tokens in the website repo. */
object StepqColors {
    /** The active theme. Compose state, so every screen recolours when it changes. */
    var palette by mutableStateOf(StepqThemes.Leaf)
        private set

    fun apply(themeKey: String?) {
        val next = StepqThemes.byKey(themeKey)
        if (next != palette) palette = next
    }

    val Leaf: Color get() = palette.leaf
    val Deep: Color get() = palette.deep
    val Mint: Color get() = palette.mint
    val Line: Color get() = palette.line
    val LeafSoft: Color get() = palette.leafSoft
    val Page: Color get() = palette.page

    val Mango = Color(0xFFFFB627)
    val Tomato = Color(0xFFF0533A)
    val Ink = Color(0xFF0E2A1A)
    val White = Color(0xFFFFFFFF)
    val Muted = Color(0xFF55695D)
    val MangoSoft = Color(0xFFFFF3D6)
    val TomatoSoft = Color(0xFFFDE6E1)
    val TomatoText = Color(0xFFB8321C)
}

/** StepQ Pro's "Kadam Gold": night-green cards, gold accents. Same in every theme. */
object ProColors {
    val Gold1 = Color(0xFFFFE29A)
    val Gold2 = Color(0xFFF5B83D)
    val Gold3 = Color(0xFFC98A1B)
    val GoldText = Color(0xFF8A5A00)
    val GoldOnNight = Color(0xFFF5D58A)
    val Night1 = Color(0xFF17452C)
    val Night2 = Color(0xFF0E2A1A)
    val Night3 = Color(0xFF0B2014)
    val OnNightMuted = Color(0xFFCFE3D6)
    val Champagne = Color(0xFFFFF8E6)
    val Champagne2 = Color(0xFFFFEFC4)

    val gold = Brush.linearGradient(listOf(Gold1, Gold2, Gold3))
    val goldFlat = Brush.horizontalGradient(listOf(Gold1, Gold2, Gold3))
    val night = Brush.linearGradient(listOf(Night1, Night2, Night3))
    val champagne = Brush.horizontalGradient(listOf(Champagne, Champagne2))
    val ring = Brush.sweepGradient(listOf(Gold1, Gold3, Gold2, Gold1))
}
