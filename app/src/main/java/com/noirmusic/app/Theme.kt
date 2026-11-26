package com.noirmusic.app

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val AccentChoices = listOf(
    "Violet" to Color(0xFFB879FF),
    "Blue" to Color(0xFF6EA8FF),
    "Rose" to Color(0xFFFF7AB6),
    "Mint" to Color(0xFF4FE3B0),
    "Amber" to Color(0xFFFFB454),
    "Crimson" to Color(0xFFFF5C6C)
)

@Immutable
class NoirColors(
    val accent: Color, val accentSoft: Color, val onAccent: Color,
    val bg: Color, val panel: Color, val panel2: Color, val muted: Color, val text: Color
)

fun buildColors(accentIndex: Int, amoled: Boolean, dynamic: Color? = null): NoirColors {
    val a = dynamic ?: AccentChoices[accentIndex.coerceIn(0, AccentChoices.lastIndex)].second
    return NoirColors(
        accent = a,
        accentSoft = lerp(a, Color.White, .35f),
        onAccent = Color(0xFF120A1C),
        bg = if (amoled) Color.Black else Color(0xFF0B0A12),
        panel = if (amoled) Color(0xFF111016) else Color(0xFF17131F),
        panel2 = if (amoled) Color(0xFF1B1923) else Color(0xFF231C30),
        muted = Color(0xFF9A91AA),
        text = Color(0xFFF7F4FB)
    )
}

val LocalNoir = staticCompositionLocalOf { buildColors(0, false) }

val N: NoirColors
    @Composable @ReadOnlyComposable get() = LocalNoir.current

@Composable
fun NoirTheme(accentIndex: Int, amoled: Boolean, content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val dyn = if (Build.VERSION.SDK_INT >= 31 && accentIndex == AccentChoices.size) dynamicDarkColorScheme(ctx).primary else null
    val c = remember(accentIndex, amoled, dyn) { buildColors(accentIndex, amoled, dyn) }
    val base = Typography()
    val typography = base.copy(
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-.2).sp),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        bodyMedium = base.bodyMedium.copy(fontSize = 14.sp)
    )
    CompositionLocalProvider(LocalNoir provides c) {
        MaterialTheme(
            colorScheme = darkColorScheme(
                primary = c.accent, onPrimary = c.onAccent, secondary = c.accentSoft,
                background = c.bg, onBackground = c.text, surface = c.panel, onSurface = c.text,
                surfaceVariant = c.panel2, onSurfaceVariant = c.muted,
                surfaceContainer = c.panel, surfaceContainerHigh = c.panel2, surfaceContainerHighest = c.panel2,
                surfaceContainerLow = c.panel, surfaceContainerLowest = c.bg, outline = Color(0xFF3A3047)
            ),
            typography = typography,
            content = content
        )
    }
}
