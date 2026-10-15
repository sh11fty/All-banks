@file:OptIn(ExperimentalMaterial3Api::class)

package com.sh11fty.allbanks.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardColors
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Design system: "Ink & Mint". Deep ink surfaces, a single mint accent, generous radii and
 * tabular figures so amounts line up. No dynamic colour, so the app keeps its own identity.
 */
object Brand {
    val Ink = Color(0xFF0E1526)
    val Ink2 = Color(0xFF1D2B4D)
    val Mint = Color(0xFF3DDBB5)
}

object Space {
    const val xs = 4
    const val sm = 8
    const val md = 12
    const val lg = 16
    const val xl = 24
}

private val LightScheme = lightColorScheme(
    primary = Color(0xFF0B6E5C), onPrimary = Color.White,
    primaryContainer = Color(0xFFD6F5EC), onPrimaryContainer = Color(0xFF00382C),
    secondary = Color(0xFF475569), onSecondary = Color.White,
    secondaryContainer = Color(0xFFE8ECF3), onSecondaryContainer = Color(0xFF1B2433),
    tertiary = Color(0xFF5B5BD6), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE6E6FB), onTertiaryContainer = Color(0xFF1E1E6B),
    background = Color(0xFFF5F6F8), onBackground = Color(0xFF0E1526),
    surface = Color.White, onSurface = Color(0xFF0E1526),
    surfaceVariant = Color(0xFFEDEFF3), onSurfaceVariant = Color(0xFF667085),
    outline = Color(0xFFCBD0DA), outlineVariant = Color(0xFFE4E7EC),
    error = Color(0xFFD92D3A), onError = Color.White,
    errorContainer = Color(0xFFFDE8EA), onErrorContainer = Color(0xFF7A111B),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFFAFBFC),
    surfaceContainer = Color(0xFFF1F3F6), surfaceContainerHigh = Color(0xFFEBEEF2),
    surfaceContainerHighest = Color(0xFFE4E7EC),
)

private val DarkScheme = darkColorScheme(
    primary = Brand.Mint, onPrimary = Color(0xFF00382C),
    primaryContainer = Color(0xFF0F4D40), onPrimaryContainer = Color(0xFFBFF5E6),
    secondary = Color(0xFFB4BED0), onSecondary = Color(0xFF1B2433),
    secondaryContainer = Color(0xFF222E4A), onSecondaryContainer = Color(0xFFDDE3F0),
    tertiary = Color(0xFF9A9AF5), onTertiary = Color(0xFF1E1E6B),
    tertiaryContainer = Color(0xFF2C2C6B), onTertiaryContainer = Color(0xFFE0E0FF),
    background = Color(0xFF0A0F1C), onBackground = Color(0xFFE8ECF4),
    surface = Color(0xFF121A2B), onSurface = Color(0xFFE8ECF4),
    surfaceVariant = Color(0xFF1B2540), onSurfaceVariant = Color(0xFF98A2B5),
    outline = Color(0xFF3A4562), outlineVariant = Color(0xFF232E48),
    error = Color(0xFFFF7A82), onError = Color(0xFF4A0A10),
    errorContainer = Color(0xFF5A1A20), onErrorContainer = Color(0xFFFFDADC),
    surfaceContainerLowest = Color(0xFF0A0F1C), surfaceContainerLow = Color(0xFF121A2B),
    surfaceContainer = Color(0xFF162036), surfaceContainerHigh = Color(0xFF1B2540),
    surfaceContainerHighest = Color(0xFF222E4A),
)

private val tnum = "tnum"

private val AppTypography = Typography().let { d ->
    d.copy(
        displayLarge = TextStyle(fontSize = 44.sp, lineHeight = 48.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-1.2).sp, fontFeatureSettings = tnum),
        displayMedium = TextStyle(fontSize = 36.sp, lineHeight = 40.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.8).sp, fontFeatureSettings = tnum),
        headlineLarge = TextStyle(fontSize = 32.sp, lineHeight = 38.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.6).sp, fontFeatureSettings = tnum),
        headlineMedium = TextStyle(fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.4).sp, fontFeatureSettings = tnum),
        headlineSmall = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
        titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.1).sp, fontFeatureSettings = tnum),
        titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = tnum),
        titleSmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = tnum),
        bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontFeatureSettings = tnum),
        bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontFeatureSettings = tnum),
        bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontFeatureSettings = tnum),
        labelLarge = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.1.sp),
        labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.2.sp),
        labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp),
    )
}

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkScheme else LightScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}

/** Positive money colour that stays readable in both themes. */
@Composable
fun positiveColour(): Color = if (isSystemInDarkTheme()) Color(0xFF4ADE9E) else Color(0xFF0A8F6A)

/** Standard card: white (or raised ink) surface with a hairline border and generous radius. */
@Composable
fun Card(
    modifier: Modifier = Modifier,
    colors: CardColors? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    androidx.compose.material3.Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        colors = colors ?: androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        content = content,
    )
}

/** Quiet, inset panel for secondary groupings inside sheets and cards. */
@Composable
fun OutlinedCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    androidx.compose.material3.Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        content = content,
    )
}
