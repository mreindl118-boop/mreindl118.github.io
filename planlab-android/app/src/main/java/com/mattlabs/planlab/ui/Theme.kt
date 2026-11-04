package com.mattlabs.planlab.ui

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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** planLAB's tokens: the same palette as the web page, plus one color per kind of task. */
@Immutable
data class Tokens(
    val bg: Color, val surface: Color, val sunk: Color, val fg: Color, val muted: Color, val line: Color,
    val accent: Color, val accentSoft: Color, val ok: Color, val okSoft: Color, val warn: Color, val warnSoft: Color, val bad: Color,
    val eat: Color, val body: Color, val piano: Color, val voice: Color, val home: Color, val dark: Boolean,
) {
    fun kind(k: String): Color = when (k) { "meals" -> eat; "body", "move", "mind" -> body; "piano" -> piano; "voice" -> voice; "clean", "home", "bugs" -> home; else -> accent }
    fun score(s: Int): Color = when { s >= 80 -> ok; s >= 65 -> accent; s >= 50 -> warn; else -> bad }
}

val LightTokens = Tokens(
    bg = Color(0xFFF2F5F9), surface = Color.White, sunk = Color(0xFFE8EDF3), fg = Color(0xFF0E1620), muted = Color(0xFF5B6979), line = Color(0xFFD9E1EA),
    accent = Color(0xFF2F7DE1), accentSoft = Color(0xFFE2EFFC), ok = Color(0xFF17875A), okSoft = Color(0xFFDCF2E7), warn = Color(0xFFB4690E), warnSoft = Color(0xFFFBEFD9), bad = Color(0xFFC2410C),
    eat = Color(0xFFE08A1E), body = Color(0xFF12A383), piano = Color(0xFF6E56CF), voice = Color(0xFFE5484D), home = Color(0xFF0E8FB0), dark = false,
)
val DarkTokens = Tokens(
    bg = Color(0xFF0B1016), surface = Color(0xFF141B23), sunk = Color(0xFF1B242E), fg = Color(0xFFE8EDF3), muted = Color(0xFF93A2B2), line = Color(0xFF25303D),
    accent = Color(0xFF69B4F4), accentSoft = Color(0xFF15314A), ok = Color(0xFF4CC38A), okSoft = Color(0xFF173327), warn = Color(0xFFE0B25A), warnSoft = Color(0xFF3A2C12), bad = Color(0xFFFF8A4C),
    eat = Color(0xFFF2A94A), body = Color(0xFF3FD1AE), piano = Color(0xFF9E8CFC), voice = Color(0xFFFF7A7F), home = Color(0xFF4FC7E3), dark = true,
)

val LocalTokens = staticCompositionLocalOf { LightTokens }

private val Round = FontFamily.SansSerif
private val Type = Typography(
    displaySmall = TextStyle(fontFamily = Round, fontWeight = FontWeight.ExtraBold, fontSize = 34.sp, lineHeight = 38.sp, letterSpacing = (-0.5).sp),
    headlineSmall = TextStyle(fontFamily = Round, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = Round, fontWeight = FontWeight.Bold, fontSize = 17.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 0.6.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 19.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 15.sp),
)

@Composable
fun PlanTheme(content: @Composable () -> Unit) {
    val t = if (isSystemInDarkTheme()) DarkTokens else LightTokens
    val scheme = if (t.dark) darkColorScheme(primary = t.accent, onPrimary = Color(0xFF06121D), background = t.bg, surface = t.surface, onSurface = t.fg, onBackground = t.fg, surfaceVariant = t.sunk, outline = t.line)
    else lightColorScheme(primary = t.accent, onPrimary = Color.White, background = t.bg, surface = t.surface, onSurface = t.fg, onBackground = t.fg, surfaceVariant = t.sunk, outline = t.line)
    CompositionLocalProvider(LocalTokens provides t) {
        MaterialTheme(colorScheme = scheme, typography = Type, content = content)
    }
}
