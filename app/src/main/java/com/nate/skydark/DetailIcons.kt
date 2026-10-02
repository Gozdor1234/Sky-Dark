package com.nate.skydark

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Small glyphs for the "Right now" details, drawn in code in the same palette as the weather icons. */
enum class DetailIcon { WIND, GUSTS, HUMIDITY, DEW, UV, VISIBILITY, PRESSURE, CLOUD, SUNRISE, SUNSET, STORM }

private val Teal = Color(0xFF5FAFC4)
private val TealDeep = Color(0xFF3A8FA6)
private val Violet = Color(0xFF7E6BC9)
private val Orange = Color(0xFFE08A1E)

@Composable
fun DetailGlyph(icon: DetailIcon, modifier: Modifier = Modifier) {
    val dark = LocalLook.current.dark
    val cloud = if (dark) Color(0xFFC3CCD8) else Color(0xFF8E9BAD)
    val slate = if (dark) Color(0xFFAAB4C2) else Color(0xFF6E7B8D)
    Canvas(modifier) {
        val s = size.minDimension
        val w = s * 0.085f
        when (icon) {
            DetailIcon.WIND -> {
                windLine(s, 0.30f, 0.62f, Teal, w)
                windLine(s, 0.50f, 0.80f, Teal, w)
                line(Teal, s * 0.14f, s * 0.70f, s * 0.50f, s * 0.70f, w)
            }
            DetailIcon.GUSTS -> {
                windLine(s, 0.28f, 0.70f, TealDeep, w)
                windLine(s, 0.50f, 0.86f, TealDeep, w)
                line(TealDeep, s * 0.08f, s * 0.72f, s * 0.30f, s * 0.72f, w)
                line(TealDeep, s * 0.40f, s * 0.72f, s * 0.62f, s * 0.72f, w)
            }
            DetailIcon.HUMIDITY -> drawPath(drop(s, s * 0.5f, 0.12f, 0.88f), SkyColors.rain)
            DetailIcon.DEW -> {
                drawPath(drop(s, s * 0.5f, 0.10f, 0.72f), SkyColors.rain, style = Stroke(w, cap = StrokeCap.Round))
                line(SkyColors.rain, s * 0.18f, s * 0.88f, s * 0.82f, s * 0.88f, w)
            }
            DetailIcon.UV -> sun(Offset(s * 0.5f, s * 0.5f), s * 0.18f, SkyColors.sun, w)
            DetailIcon.VISIBILITY -> {
                val eye = Path().apply {
                    moveTo(s * 0.08f, s * 0.5f)
                    quadraticBezierTo(s * 0.5f, s * 0.08f, s * 0.92f, s * 0.5f)
                    quadraticBezierTo(s * 0.5f, s * 0.92f, s * 0.08f, s * 0.5f)
                    close()
                }
                drawPath(eye, slate, style = Stroke(w))
                drawCircle(slate, s * 0.13f, Offset(s * 0.5f, s * 0.5f))
            }
            DetailIcon.PRESSURE -> {
                drawArc(Violet, 150f, 240f, false, Offset(s * 0.12f, s * 0.12f), Size(s * 0.76f, s * 0.76f), style = Stroke(w, cap = StrokeCap.Round))
                val a = -50.0 * PI / 180
                line(Violet, s * 0.5f, s * 0.5f, s * 0.5f + cos(a).toFloat() * s * 0.26f, s * 0.5f + sin(a).toFloat() * s * 0.26f, w)
                drawCircle(Violet, s * 0.07f, Offset(s * 0.5f, s * 0.5f))
            }
            DetailIcon.CLOUD -> {
                val base = Rect(s * 0.10f, s * 0.48f, s * 0.90f, s * 0.80f)
                drawRoundRect(cloud, base.topLeft, base.size, androidx.compose.ui.geometry.CornerRadius(base.height / 2))
                drawCircle(cloud, s * 0.17f, Offset(s * 0.36f, s * 0.50f))
                drawCircle(cloud, s * 0.23f, Offset(s * 0.58f, s * 0.42f))
            }
            DetailIcon.SUNRISE, DetailIcon.SUNSET -> {
                val c = if (icon == DetailIcon.SUNRISE) SkyColors.sun else Orange
                drawArc(c, 180f, 180f, true, Offset(s * 0.28f, s * 0.50f), Size(s * 0.44f, s * 0.44f))
                line(c, s * 0.08f, s * 0.74f, s * 0.92f, s * 0.74f, w)
                // Arrow up for sunrise, down for sunset
                val up = icon == DetailIcon.SUNRISE
                val tipY = if (up) s * 0.06f else s * 0.40f
                val tailY = if (up) s * 0.40f else s * 0.06f
                line(c, s * 0.5f, tailY, s * 0.5f, tipY, w)
                val back = if (up) tipY + s * 0.12f else tipY - s * 0.12f
                line(c, s * 0.5f, tipY, s * 0.38f, back, w)
                line(c, s * 0.5f, tipY, s * 0.62f, back, w)
            }
            DetailIcon.STORM -> {
                val bolt = Path().apply {
                    moveTo(s * 0.58f, s * 0.06f)
                    lineTo(s * 0.22f, s * 0.56f)
                    lineTo(s * 0.48f, s * 0.56f)
                    lineTo(s * 0.38f, s * 0.94f)
                    lineTo(s * 0.80f, s * 0.40f)
                    lineTo(s * 0.54f, s * 0.40f)
                    lineTo(s * 0.66f, s * 0.06f)
                    close()
                }
                drawPath(bolt, SkyColors.bolt)
            }
        }
    }
}

private fun DrawScope.line(c: Color, x0: Float, y0: Float, x1: Float, y1: Float, w: Float) =
    drawLine(c, Offset(x0, y0), Offset(x1, y1), strokeWidth = w, cap = StrokeCap.Round)

/** A wind stroke at height [y] (fraction) running to [end] (fraction) and curling up at the end. */
private fun DrawScope.windLine(s: Float, y: Float, end: Float, c: Color, w: Float) {
    val r = s * 0.11f
    val p = Path().apply {
        moveTo(s * 0.10f, s * y)
        lineTo(s * end - r, s * y)
        // curl: half circle up and back
        arcTo(Rect(Offset(s * end - r * 2f, s * y - r * 2f), Size(r * 2f, r * 2f)), 90f, -270f, false)
    }
    drawPath(p, c, style = Stroke(w, cap = StrokeCap.Round))
}

private fun drop(s: Float, cx: Float, top: Float, bottom: Float): Path {
    val r = (bottom - top) * s * 0.34f
    val cy = bottom * s - r
    return Path().apply {
        moveTo(cx, top * s)
        cubicTo(cx + r * 0.35f, top * s + r * 0.9f, cx + r, cy - r * 0.45f, cx + r, cy)
        arcTo(Rect(Offset(cx, cy), r), 0f, 180f, false)
        cubicTo(cx - r, cy - r * 0.45f, cx - r * 0.35f, top * s + r * 0.9f, cx, top * s)
        close()
    }
}

private fun DrawScope.sun(c: Offset, r: Float, color: Color, w: Float) {
    drawCircle(color, r, c)
    for (k in 0 until 8) {
        val a = k * PI / 4
        val dx = cos(a).toFloat()
        val dy = sin(a).toFloat()
        drawLine(color, Offset(c.x + dx * r * 1.55f, c.y + dy * r * 1.55f), Offset(c.x + dx * r * 2.2f, c.y + dy * r * 2.2f), strokeWidth = w, cap = StrokeCap.Round)
    }
}
