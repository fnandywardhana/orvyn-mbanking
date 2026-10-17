package id.orvyn.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import id.orvyn.app.R

// Tokens mirror design-system/MASTER.md. Components read colors from MaterialTheme.colorScheme or OrvynTheme.colors, never hex.

object Brand {
    val Nerazzurro = Color(0xFF0032A0)
    val Nero = Color(0xFF0A0B10)
    val Giallo = Color(0xFFF5C400)
    val GialloInk = Color(0xFF7A5E00)
    val HeroMid = Color(0xFF0A1650)
    val HeroTextMuted = Color(0xFFC9D4F5)
    val Hero = Brush.linearGradient(listOf(Nerazzurro, HeroMid, Nero))
}

@Immutable
data class OrvynColors(
    val textSecondary: Color,
    val textTertiary: Color,
    val success: Color,
    val successContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val chartBarMuted: Color, // single-series charts: the bars that aren't selected (design.md 4.2)
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF0032A0), onPrimary = Color.White,
    primaryContainer = Color(0xFFE5ECFA), onPrimaryContainer = Color(0xFF001A5C),
    background = Color(0xFFF4F6FB), onBackground = Color(0xFF0B1020),
    surface = Color.White, onSurface = Color(0xFF0B1020),
    surfaceVariant = Color(0xFFEDF1F8), onSurfaceVariant = Color(0xFF4D5872),
    outline = Color(0xFF858FA3), outlineVariant = Color(0xFFDCE2EE),
    error = Color(0xFFC62828), onError = Color.White,
    errorContainer = Color(0xFFFCE8E8), onErrorContainer = Color(0xFFC62828),
    inverseSurface = Color(0xFF0A0B10), inverseOnSurface = Color.White, inversePrimary = Color(0xFF7FA2FF),
    scrim = Color.Black,
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF7FA2FF), onPrimary = Color(0xFF001A5C),
    primaryContainer = Color(0xFF0032A0), onPrimaryContainer = Color(0xFFDCE5FF),
    background = Color(0xFF070A12), onBackground = Color(0xFFE8ECF6),
    surface = Color(0xFF0F1422), onSurface = Color(0xFFE8ECF6),
    surfaceVariant = Color(0xFF161D2E), onSurfaceVariant = Color(0xFFA3ADC2),
    outline = Color(0xFF5D6884), outlineVariant = Color(0xFF212A3E),
    error = Color(0xFFFF8A80), onError = Color(0xFF3A1416),
    errorContainer = Color(0xFF3A1416), onErrorContainer = Color(0xFFFF8A80),
    inverseSurface = Color(0xFFE8ECF6), inverseOnSurface = Color(0xFF0B1020), inversePrimary = Color(0xFF0032A0),
    scrim = Color.Black,
)

private val LightExtra = OrvynColors(
    textSecondary = Color(0xFF4D5872), textTertiary = Color(0xFF616D86), // 4.81:1 on background (Q-E2)
    success = Color(0xFF0E7A47), successContainer = Color(0xFFE3F4EA),
    warning = Color(0xFF9A5B00), warningContainer = Color(0xFFFFF1D6),
    chartBarMuted = Color(0xFF7088C4),
)

private val DarkExtra = OrvynColors(
    textSecondary = Color(0xFFA3ADC2), textTertiary = Color(0xFF7C879E),
    success = Color(0xFF4CC78A), successContainer = Color(0xFF0F2A1E),
    warning = Color(0xFFF0B44C), warningContainer = Color(0xFF33240A),
    chartBarMuted = Color(0xFF55689C),
)

@OptIn(ExperimentalTextApi::class)
private fun jakarta(weight: Int) = Font(
    R.font.plus_jakarta_sans,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val Jakarta = FontFamily(jakarta(400), jakarta(500), jakarta(600), jakarta(700), jakarta(800))

private fun style(size: Int, line: Int, weight: Int, tracking: Double = 0.0) = TextStyle(
    fontFamily = Jakarta, fontSize = size.sp, lineHeight = line.sp,
    fontWeight = FontWeight(weight), letterSpacing = tracking.em, fontFeatureSettings = "tnum",
)

// All 15 Material 3 roles, so no text and no Material component (date picker, dialog, snackbar, segmented button) falls back to Roboto (Q-E2).
private val OrvynTypography = Typography(
    displayLarge = style(44, 52, 800),
    displayMedium = style(40, 48, 800),
    displaySmall = style(34, 40, 800, -0.005),
    headlineLarge = style(28, 34, 700),
    headlineMedium = style(24, 30, 700),
    headlineSmall = style(22, 28, 700),
    titleLarge = style(20, 26, 600),
    titleMedium = style(16, 22, 600),
    titleSmall = style(14, 20, 600),
    bodyLarge = style(16, 24, 400),
    bodyMedium = style(14, 20, 400),
    bodySmall = style(12, 16, 400), // only for captions under charts
    labelLarge = style(15, 20, 600),
    labelMedium = style(12, 16, 600),
    labelSmall = style(11, 16, 700, 0.08),
)

object OrvynType {
    val displayBalance = OrvynTypography.displaySmall
}

private val LocalOrvynColors = staticCompositionLocalOf { LightExtra }

object OrvynTheme {
    val colors: OrvynColors @Composable get() = LocalOrvynColors.current
}

@Composable
fun OrvynTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val scheme: ColorScheme = if (dark) DarkScheme else LightScheme
    CompositionLocalProvider(LocalOrvynColors provides if (dark) DarkExtra else LightExtra) {
        MaterialTheme(colorScheme = scheme, typography = OrvynTypography) {
            // Text without an explicit color follows the theme instead of defaulting to black.
            CompositionLocalProvider(LocalContentColor provides scheme.onBackground, content = content)
        }
    }
}
