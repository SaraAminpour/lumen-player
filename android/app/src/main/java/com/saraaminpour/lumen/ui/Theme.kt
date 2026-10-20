package com.saraaminpour.lumen.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.saraaminpour.lumen.R
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** Same palette as the desktop app (Theme.qml). */
object C {
    val bg = Color(0xFF07080C)
    val glass = Color(0xF0101219)
    val glassSolid = Color(0xFF12141B)
    val glassBorder = Color(0x17FFFFFF)
    val card = Color(0x0BFFFFFF)
    val cardHover = Color(0x14FFFFFF)
    val hover = Color(0x1AFFFFFF)
    val text = Color(0xFFF4F5FA)
    val textDim = Color(0xA3F5F7FF)
    val textFaint = Color(0x66F5F7FF)
    val danger = Color(0xFFF43F5E)
    val warn = Color(0xFFF59E0B)
    val good = Color(0xFF34D399)
    val scrim = Color(0x99000000)
}

data class Accent(val name: String, val a: Color, val b: Color) {
    val brush get() = Brush.horizontalGradient(listOf(a, b))
    val light get() = Color(
        (a.red + (1 - a.red) * 0.25f), (a.green + (1 - a.green) * 0.25f), (a.blue + (1 - a.blue) * 0.25f), 1f,
    )
    val onAccent get() = if (name == "Mono") Color(0xFF0B0C10) else Color.White
}

val Accents = listOf(
    Accent("Aurora", Color(0xFF8B5CF6), Color(0xFF22D3EE)),
    Accent("Ocean", Color(0xFF2563EB), Color(0xFF2DD4BF)),
    Accent("Sunset", Color(0xFFF97316), Color(0xFFEC4899)),
    Accent("Mint", Color(0xFF10B981), Color(0xFFA3E635)),
    Accent("Rose", Color(0xFFE11D48), Color(0xFFF59E0B)),
    Accent("Mono", Color(0xFFE5E7EB), Color(0xFF9CA3AF)),
)

val LocalAccent = staticCompositionLocalOf { Accents[0] }

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun inter(w: Int) = Font(
    R.font.inter, FontWeight(w),
    variationSettings = FontVariation.Settings(FontVariation.weight(w)),
)

val Inter = FontFamily(inter(400), inter(500), inter(600), inter(700), inter(800))

@Composable
fun LumenTheme(accentIndex: Int, content: @Composable () -> Unit) {
    val accent = Accents[accentIndex.coerceIn(0, Accents.lastIndex)]
    val base = TextStyle(fontFamily = Inter, color = C.text)
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = accent.light, onPrimary = accent.onAccent, secondary = accent.b,
            background = C.bg, surface = C.glassSolid, onSurface = C.text, onBackground = C.text,
        ),
        typography = Typography(
            bodyLarge = base.copy(fontSize = 15.sp), bodyMedium = base.copy(fontSize = 14.sp),
            bodySmall = base.copy(fontSize = 12.sp), labelLarge = base.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
            labelMedium = base.copy(fontSize = 12.sp), labelSmall = base.copy(fontSize = 11.sp),
            titleLarge = base.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold),
            titleMedium = base.copy(fontSize = 17.sp, fontWeight = FontWeight.Bold),
            titleSmall = base.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
            headlineLarge = base.copy(fontSize = 40.sp, fontWeight = FontWeight.ExtraBold),
        ),
    ) {
        CompositionLocalProvider(LocalAccent provides accent, content = content)
    }
}

fun fmtTime(seconds: Double): String {
    val s = if (seconds.isNaN() || seconds < 0) 0 else seconds.toLong()
    val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, sec) else String.format(Locale.US, "%d:%02d", m, sec)
}

fun fmtDelay(v: Double): String {
    val ms = (v * 1000).roundToInt()
    val sign = if (ms > 0) "+" else if (ms < 0) "−" else "±"
    return sign + String.format(Locale.US, "%.3f s", abs(ms) / 1000.0)
}

fun fmtBytes(b: Long): String {
    if (b <= 0) return "—"
    val u = listOf("B", "KB", "MB", "GB", "TB"); var v = b.toDouble(); var i = 0
    while (v >= 1024 && i < u.lastIndex) { v /= 1024; i++ }
    return String.format(Locale.US, if (i == 0) "%.0f %s" else "%.1f %s", v, u[i])
}
