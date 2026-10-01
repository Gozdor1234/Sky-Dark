package com.nate.skydark

import androidx.compose.foundation.isSystemInDarkTheme
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

private val Dark = darkColorScheme(
    primary = Color(0xFF86B4FF),
    onPrimary = Color(0xFF0B1F44),
    background = Color(0xFF0E1116),
    onBackground = Color(0xFFE5E8ED),
    surface = Color(0xFF171B22),
    onSurface = Color(0xFFE5E8ED),
    surfaceVariant = Color(0xFF232933),
    onSurfaceVariant = Color(0xFF9AA4B2),
    outlineVariant = Color(0xFF2E3541),
    error = Color(0xFFFF7B6E),
)

@Composable
fun SkyTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
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
