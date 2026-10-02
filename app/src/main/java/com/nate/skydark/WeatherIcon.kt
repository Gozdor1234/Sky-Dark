package com.nate.skydark

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Condition glyphs drawn in code (no image assets), keyed by Pirate Weather's icon names.
 * [bg] is the color behind the icon; it cuts a small gap where a cloud overlaps the sun or moon.
 */
@Composable
fun WeatherIcon(
    icon: String,
    modifier: Modifier = Modifier,
    bg: Color = MaterialTheme.colorScheme.surface,
) {
    val dark = LocalLook.current.dark
    Canvas(modifier) { drawWeatherIcon(icon, dark, bg) }
}

/** The weather glyph itself, usable from any DrawScope (the app screens and the home-screen widget). */
fun DrawScope.drawWeatherIcon(icon: String, dark: Boolean, bg: Color) {
    // Full-color glyphs: soft gray-blue clouds, warm moon, plus the sun/rain/snow/bolt accents.
    val tint = if (dark) Color(0xFFC3CCD8) else Color(0xFF8E9BAD)
    val moonColor = Color(0xFFF1D27A)
    val fogColor = if (dark) Color(0xFFA9B3C0) else Color(0xFF9AA5B3)
    val windColor = Color(0xFF5FAFC4)
    run {
        val s = size.minDimension
        translate((size.width - s) / 2f, (size.height - s) / 2f) {
            when (icon) {
                "clear-day" -> sun(Offset(s * 0.5f, s * 0.5f), s * 0.2f)
                "clear-night" -> moon(Offset(s * 0.5f, s * 0.5f), s * 0.3f, moonColor)
                "partly-cloudy-day" -> {
                    sun(Offset(s * 0.36f, s * 0.36f), s * 0.14f)
                    cloud(Offset(s * 0.56f, s * 0.64f), s * 0.64f, tint, bg)
                }
                "partly-cloudy-night" -> {
                    moon(Offset(s * 0.38f, s * 0.34f), s * 0.2f, moonColor)
                    cloud(Offset(s * 0.56f, s * 0.64f), s * 0.64f, tint, bg)
                }
                "cloudy" -> cloud(Offset(s * 0.5f, s * 0.56f), s * 0.82f, tint, null)
                "fog" -> {
                    val w = s * 0.07f
                    line(fogColor, s * 0.2f, s * 0.36f, s * 0.8f, s * 0.36f, w)
                    line(fogColor, s * 0.14f, s * 0.52f, s * 0.72f, s * 0.52f, w)
                    line(fogColor, s * 0.28f, s * 0.68f, s * 0.86f, s * 0.68f, w)
                }
                "wind" -> {
                    val w = s * 0.07f
                    line(windColor, s * 0.14f, s * 0.34f, s * 0.7f, s * 0.34f, w)
                    line(windColor, s * 0.1f, s * 0.5f, s * 0.86f, s * 0.5f, w)
                    line(windColor, s * 0.2f, s * 0.66f, s * 0.62f, s * 0.66f, w)
                }
                "rain", "drizzle" -> {
                    cloud(Offset(s * 0.5f, s * 0.42f), s * 0.78f, tint, null)
                    drops(s, SkyColors.rain)
                }
                "snow" -> {
                    cloud(Offset(s * 0.5f, s * 0.42f), s * 0.78f, tint, null)
                    flakes(s, SkyColors.snow)
                }
                "sleet", "hail" -> {
                    cloud(Offset(s * 0.5f, s * 0.42f), s * 0.78f, tint, null)
                    line(SkyColors.rain, s * 0.36f, s * 0.68f, s * 0.31f, s * 0.86f, s * 0.06f)
                    drawCircle(SkyColors.snow, s * 0.05f, Offset(s * 0.52f, s * 0.78f))
                    line(SkyColors.rain, s * 0.68f, s * 0.68f, s * 0.63f, s * 0.86f, s * 0.06f)
                }
                "thunderstorm" -> {
                    cloud(Offset(s * 0.5f, s * 0.42f), s * 0.78f, tint, null)
                    val bolt = Path().apply {
                        moveTo(s * 0.54f, s * 0.6f)
                        lineTo(s * 0.4f, s * 0.78f)
                        lineTo(s * 0.5f, s * 0.78f)
                        lineTo(s * 0.44f, s * 0.95f)
                        lineTo(s * 0.62f, s * 0.72f)
                        lineTo(s * 0.52f, s * 0.72f)
                        lineTo(s * 0.58f, s * 0.6f)
                        close()
                    }
                    drawPath(bolt, SkyColors.bolt)
                }
                else -> cloud(Offset(s * 0.5f, s * 0.56f), s * 0.82f, tint, null)
            }
        }
    }
}

private fun DrawScope.line(c: Color, x0: Float, y0: Float, x1: Float, y1: Float, w: Float) =
    drawLine(c, Offset(x0, y0), Offset(x1, y1), strokeWidth = w, cap = StrokeCap.Round)

private fun DrawScope.sun(c: Offset, r: Float) {
    drawCircle(SkyColors.sun, r, c)
    for (k in 0 until 8) {
        val a = k * PI / 4
        val dx = cos(a).toFloat()
        val dy = sin(a).toFloat()
        drawLine(
            SkyColors.sun,
            Offset(c.x + dx * r * 1.45f, c.y + dy * r * 1.45f),
            Offset(c.x + dx * r * 1.95f, c.y + dy * r * 1.95f),
            strokeWidth = r * 0.3f,
            cap = StrokeCap.Round,
        )
    }
}

private fun DrawScope.moon(c: Offset, r: Float, color: Color) {
    val disc = Path().apply { addOval(Rect(c, r)) }
    val bite = Path().apply { addOval(Rect(Offset(c.x + r * 0.55f, c.y - r * 0.35f), r * 0.85f)) }
    val crescent = Path()
    crescent.op(disc, bite, PathOperation.Difference)
    drawPath(crescent, color)
}

/** Cloud of width [w] centered at [c]. When [halo] is set, a slightly larger cloud in that color is drawn first. */
private fun DrawScope.cloud(c: Offset, w: Float, color: Color, halo: Color?) {
    if (halo != null) cloudShape(c, w * 1.16f, halo)
    cloudShape(c, w, color)
}

private fun DrawScope.cloudShape(c: Offset, w: Float, color: Color) {
    val baseTop = c.y - w * 0.06f
    val baseH = w * 0.28f
    drawRoundRect(
        color,
        topLeft = Offset(c.x - w * 0.5f, baseTop),
        size = Size(w, baseH),
        cornerRadius = CornerRadius(baseH / 2f, baseH / 2f),
    )
    drawCircle(color, w * 0.19f, Offset(c.x - w * 0.2f, c.y + w * 0.02f))
    drawCircle(color, w * 0.26f, Offset(c.x + w * 0.06f, c.y - w * 0.06f))
}

private fun DrawScope.drops(s: Float, c: Color) {
    for (k in 0 until 3) {
        val x = s * (0.32f + 0.18f * k)
        line(c, x, s * 0.7f, x - s * 0.05f, s * 0.88f, s * 0.06f)
    }
}

private fun DrawScope.flakes(s: Float, c: Color) {
    val r = s * 0.045f
    listOf(0.32f to 0.72f, 0.5f to 0.8f, 0.68f to 0.72f, 0.41f to 0.9f, 0.59f to 0.9f).forEach { (x, y) ->
        drawCircle(c, r, Offset(s * x, s * y))
    }
}
