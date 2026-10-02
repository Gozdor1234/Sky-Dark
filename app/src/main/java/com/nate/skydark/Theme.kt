package com.nate.skydark

import android.graphics.Color as AndroidColor
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

private val Light = lightColorScheme(
    primary = Color(0xFF2E6BD1),
    onPrimary = Color.White,
    background = Color(0xFFF3F5F8),
    onBackground = Color(0xFF181C22),
    surface = Color.White,
    onSurface = Color(0xFF181C22),
    surfaceVariant = Color(0xFFE8ECF2),
    onSurfaceVariant = Color(0xFF5B6472),
    outlineVariant = Color(0xFFD5DBE4),
    error = Color(0xFFC0392B),
)

// Dark mode: slate gray-blue (same family as Scoreology's dark theme) rather than near-black.
private val Dark = darkColorScheme(
    primary = Color(0xFF8AB4F8),
    onPrimary = Color(0xFF0B1F44),
    background = Color(0xFF1B2230),
    onBackground = Color(0xFFE8ECF2),
    surface = Color(0xFF232C3B),
    onSurface = Color(0xFFE8ECF2),
    surfaceVariant = Color(0xFF354154),
    onSurfaceVariant = Color(0xFFBFC8D6),
    outline = Color(0xFF8A94A6),
    outlineVariant = Color(0xFF3F4A5C),
    error = Color(0xFFFF8A7E),
)

enum class ThemeMode(val key: String, val label: String) {
    SYSTEM("system", "System"),
    LIGHT("light", "Light"),
    DARK("dark", "Dark"),
    AMOLED("amoled", "AMOLED");

    companion object {
        fun from(key: String?): ThemeMode = entries.firstOrNull { it.key == key } ?: SYSTEM
    }
}

/** What the current look resolved to, for code that can't read it from the color scheme (the radar page). */
@Immutable
data class Look(val dark: Boolean, val amoled: Boolean, val modern: Boolean)

val LocalLook = staticCompositionLocalOf { Look(dark = false, amoled = false, modern = false) }

/** Pure black page so OLED pixels switch off; cards stay just visible against it. */
private fun ColorScheme.toAmoled(): ColorScheme = copy(
    background = Color.Black,
    surface = Color(0xFF0D0D0D),
    surfaceVariant = Color(0xFF1A1A1A),
    outlineVariant = Color(0xFF242424),
)

@Composable
fun SkyTheme(mode: ThemeMode, modern: Boolean, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK, ThemeMode.AMOLED -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val amoled = mode == ThemeMode.AMOLED
    var scheme = if (dark) Dark else Light
    if (amoled) scheme = scheme.toAmoled()
    // Glass panels need the page and the panel to share one tone so shadows and highlights read.
    if (modern) {
        val page = when {
            amoled -> Color.Black
            dark -> scheme.background
            else -> ModernLightBackground
        }
        scheme = scheme.copy(background = page, surface = page)
    }

    // Status and navigation bar icons follow the app's own mode, not just the phone's.
    val activity = LocalContext.current as? ComponentActivity
    LaunchedEffect(dark, activity) {
        activity?.enableEdgeToEdge(
            statusBarStyle = if (dark) SystemBarStyle.dark(AndroidColor.TRANSPARENT)
            else SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
            navigationBarStyle = if (dark) SystemBarStyle.dark(AndroidColor.TRANSPARENT)
            else SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
        )
    }

    CompositionLocalProvider(
        LocalLook provides Look(dark, amoled, modern),
        LocalModern provides modern,
    ) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

object SkyColors {
    val sun = Color(0xFFF2B33D)
    val rain = Color(0xFF3F88D4)
    val snow = Color(0xFFA48FE3)
    val bolt = Color(0xFFF5C542)

    /** Fill for each condition on the timeline bar. */
    fun of(sky: Sky): Color = when (sky) {
        Sky.CLEAR -> Color(0x00000000)
        Sky.PARTLY -> Color(0xFFC9D0DA)
        Sky.MOSTLY -> Color(0xFF9DA6B3)
        Sky.OVERCAST -> Color(0xFF747E8C)
        Sky.FOG -> Color(0xFFB4BBA9)
        Sky.WINDY -> Color(0xFFA9C7C2)
        Sky.DRIZZLE -> Color(0xFFA6CCF0)
        Sky.LIGHT_RAIN -> Color(0xFF74AEE8)
        Sky.RAIN -> Color(0xFF3F88D4)
        Sky.HEAVY_RAIN -> Color(0xFF1F5BAA)
        Sky.LIGHT_SNOW -> Color(0xFFD9D0F5)
        Sky.SNOW -> Color(0xFFB8A6EB)
        Sky.HEAVY_SNOW -> Color(0xFF8D74DA)
        Sky.SLEET -> Color(0xFF8CCBC4)
        Sky.STORM -> Color(0xFF5A4CB3)
    }

    fun textOn(bg: Color, fallback: Color): Color = when {
        bg.alpha < 0.3f -> fallback
        bg.luminance() > 0.45f -> Color(0xFF1A1F27)
        else -> Color.White
    }

    private val stops = listOf(
        -10.0 to Color(0xFF6A5AD6),
        20.0 to Color(0xFF4A8EE0),
        45.0 to Color(0xFF3DBBAE),
        62.0 to Color(0xFF9BCB4E),
        72.0 to Color(0xFFEDC444),
        84.0 to Color(0xFFF08B39),
        98.0 to Color(0xFFDF4440),
    )

    /** Temperature color, keyed in °F so both unit systems get the same palette. */
    fun temp(f: Double): Color {
        if (f.isNaN()) return Color.Gray
        if (f <= stops.first().first) return stops.first().second
        for (i in 1 until stops.size) {
            val (t1, c1) = stops[i]
            val (t0, c0) = stops[i - 1]
            if (f <= t1) return lerp(c0, c1, ((f - t0) / (t1 - t0)).toFloat())
        }
        return stops.last().second
    }
}
