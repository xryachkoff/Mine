package metrovoice.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import metrovoice.app.R

/**
 * Design tokens. Soft neutral canvas, white cards, two pastel tints for emphasis, one ink colour for
 * text and primary actions. Line colours come from the city data, never from here.
 */
@Immutable
data class Palette(
    val canvas: Color,
    val card: Color,
    val ink: Color,
    /** Secondary text. At least 4.5:1 on canvas and card. */
    val muted: Color,
    val outline: Color,
    /** Calm emphasis: "you are at a station", selected things. */
    val tintBlue: Color,
    /** Warm emphasis: status that needs a glance, onboarding. */
    val tintCream: Color,
    val onInk: Color,
    val danger: Color,
    val dark: Boolean,
)

private val LightPalette = Palette(
    canvas = Color(0xFFF7F7F7),
    card = Color(0xFFFFFFFF),
    ink = Color(0xFF1C1A1A),
    muted = Color(0xFF6B6B6B),
    outline = Color(0xFFE4E4E4),
    tintBlue = Color(0xFFD0EDF6),
    tintCream = Color(0xFFFDF6E4),
    onInk = Color(0xFFFFFFFF),
    danger = Color(0xFFB3261E),
    dark = false,
)

// The reference is light only; the metro is not. Same roles, tints pulled down to sit on a dark canvas.
private val DarkPalette = Palette(
    canvas = Color(0xFF121212),
    card = Color(0xFF1E1E1E),
    ink = Color(0xFFF2F2F2),
    muted = Color(0xFFA9A9A9),
    outline = Color(0xFF343434),
    tintBlue = Color(0xFF1C3B46),
    tintCream = Color(0xFF3A3423),
    onInk = Color(0xFF1C1A1A),
    danger = Color(0xFFF2B8B5),
    dark = true,
)

val LocalPalette = staticCompositionLocalOf { LightPalette }

/** Type scale: 12 / 14 / 16 / 20 / 28 / 36 sp. Nothing else. */
val Manrope = FontFamily(
    Font(R.font.manrope_regular, FontWeight.Normal),
    Font(R.font.manrope_medium, FontWeight.Medium),
    Font(R.font.manrope_semibold, FontWeight.SemiBold),
    Font(R.font.manrope_bold, FontWeight.Bold),
)

object Radius {
    val card = 24.dp
    val tile = 20.dp
    val control = 16.dp
}

private fun Typography.withFamily(family: FontFamily) = Typography(
    displayLarge = displayLarge.copy(fontFamily = family), displayMedium = displayMedium.copy(fontFamily = family),
    displaySmall = displaySmall.copy(fontFamily = family), headlineLarge = headlineLarge.copy(fontFamily = family),
    headlineMedium = headlineMedium.copy(fontFamily = family), headlineSmall = headlineSmall.copy(fontFamily = family),
    titleLarge = titleLarge.copy(fontFamily = family), titleMedium = titleMedium.copy(fontFamily = family),
    titleSmall = titleSmall.copy(fontFamily = family), bodyLarge = bodyLarge.copy(fontFamily = family),
    bodyMedium = bodyMedium.copy(fontFamily = family), bodySmall = bodySmall.copy(fontFamily = family),
    labelLarge = labelLarge.copy(fontFamily = family), labelMedium = labelMedium.copy(fontFamily = family),
    labelSmall = labelSmall.copy(fontFamily = family),
)

@Composable
fun MetroTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val p = if (dark) DarkPalette else LightPalette
    // Material components (dialogs, switches, text buttons) take their colours from the same tokens.
    val scheme = if (dark) darkColorScheme(
        primary = p.ink, onPrimary = p.onInk, background = p.canvas, onBackground = p.ink,
        surface = p.canvas, onSurface = p.ink, surfaceVariant = p.card, onSurfaceVariant = p.muted,
        surfaceContainer = p.card, surfaceContainerHigh = p.card, surfaceContainerHighest = p.outline,
        outline = p.muted, outlineVariant = p.outline, primaryContainer = p.tintBlue, onPrimaryContainer = p.ink,
        secondaryContainer = p.tintBlue, onSecondaryContainer = p.ink, error = p.danger,
    ) else lightColorScheme(
        primary = p.ink, onPrimary = p.onInk, background = p.canvas, onBackground = p.ink,
        surface = p.canvas, onSurface = p.ink, surfaceVariant = p.card, onSurfaceVariant = p.muted,
        surfaceContainer = p.card, surfaceContainerHigh = p.card, surfaceContainerHighest = p.outline,
        outline = p.muted, outlineVariant = p.outline, primaryContainer = p.tintBlue, onPrimaryContainer = p.ink,
        secondaryContainer = p.tintBlue, onSecondaryContainer = p.ink, error = p.danger,
    )
    CompositionLocalProvider(LocalPalette provides p) {
        MaterialTheme(colorScheme = scheme, typography = Typography().withFamily(Manrope), content = content)
    }
}
