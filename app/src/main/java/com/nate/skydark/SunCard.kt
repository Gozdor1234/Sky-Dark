package com.nate.skydark

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import kotlin.math.PI
import kotlin.math.cos

private val SunLine = Color(0xFFE08A1E)
private val NightLineLight = Color(0xFF4A5BB0)
private val NightLineDark = Color(0xFF8C9BE8)

/**
 * Today's sun path over 24 hours (midnight to midnight): a curve that rises above the horizon line
 * at sunrise and drops below it at sunset, with a marker for where the sun is right now.
 * Shown only when today's sunrise and sunset are known (not in polar day or night).
 */
@Composable
fun SunCard(f: Forecast) {
    val today = f.daily.firstOrNull() ?: return
    val rise = today.sunrise
    val set = today.sunset
    if (rise <= 0 || set <= rise) return

    val cs = MaterialTheme.colorScheme
    val dark = LocalLook.current.dark
    val nightLine = if (dark) NightLineDark else NightLineLight
    val nightFill = nightLine.copy(alpha = if (dark) 0.14f else 0.10f)

    // The local day the sun times belong to.
    val dayStart = Instant.ofEpochSecond(rise).atZone(f.zone).toLocalDate().atStartOfDay(f.zone).toEpochSecond()
    val daySecs = 24 * 3600.0
    val noon = (rise + set) / 2.0
    // Elevation stand-in: a cosine centered on solar noon, shifted so it is exactly 0 at sunrise and sunset.
    val zero = cos(2 * PI * (rise - noon) / daySecs)
    fun elev(t: Double) = cos(2 * PI * (t - noon) / daySecs) - zero
    val top = 1 - zero
    val bottom = -1 - zero
    val nowSec = (System.currentTimeMillis() / 1000).coerceIn(dayStart, dayStart + 86_399)

    val daylight = set - rise
    val dh = daylight / 3600
    val dm = (daylight % 3600) / 60

    Panel {
        SectionTitle("Sunrise & sunset")
        BoxWithConstraints(Modifier.fillMaxWidth().height(172.dp)) {
            val w = maxWidth
            val h = maxHeight
            // Horizon sits 58% down; the curve uses the space above and below it in proportion.
            val horizonFrac = 0.58f
            fun xFrac(t: Long) = ((t - dayStart) / daySecs).toFloat().coerceIn(0f, 1f)

            Canvas(Modifier.fillMaxWidth().height(h)) {
                val cw = size.width
                val ch = size.height
                val hy = ch * horizonFrac
                val upPx = hy - 10.dp.toPx()
                val downPx = ch - hy - 6.dp.toPx()
                fun y(e: Double): Float =
                    if (e >= 0) hy - (e / top * upPx).toFloat() else hy + (e / bottom * downPx).toFloat()
                fun x(t: Double): Float = ((t - dayStart) / daySecs).toFloat() * cw

                val steps = 160
                val curve = Path()
                for (i in 0..steps) {
                    val t = dayStart + daySecs * i / steps
                    val px = x(t)
                    val py = y(elev(t))
                    if (i == 0) curve.moveTo(px, py) else curve.lineTo(px, py)
                }

                // Night band below the horizon
                drawRect(nightFill, topLeft = Offset(0f, hy), size = androidx.compose.ui.geometry.Size(cw, ch - hy))
                // Soft glow under the daytime arc
                val glow = Path().apply {
                    addPath(curve)
                    lineTo(cw, hy); lineTo(0f, hy); close()
                }
                clipRect(left = x(rise.toDouble()), right = x(set.toDouble()), top = 0f, bottom = hy) {
                    drawPath(glow, Brush.verticalGradient(listOf(SunLine.copy(alpha = 0.22f), SunLine.copy(alpha = 0.02f)), startY = 0f, endY = hy))
                }
                // Horizon line
                drawLine(cs.outlineVariant, Offset(0f, hy), Offset(cw, hy), strokeWidth = 1.dp.toPx())
                // Curve: amber above the horizon, blue below
                val stroke = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                clipRect(0f, 0f, cw, hy) { drawPath(curve, SunLine, style = stroke) }
                clipRect(0f, hy, cw, ch) { drawPath(curve, nightLine, style = stroke) }
                // Sunrise and sunset points
                listOf(rise, set).forEach { t -> drawCircle(SunLine, 4.5.dp.toPx(), Offset(x(t.toDouble()), hy)) }
                // Where the sun is now
                val nx = x(nowSec.toDouble())
                val ny = y(elev(nowSec.toDouble()))
                val up = elev(nowSec.toDouble()) >= 0
                drawCircle(cs.surface, 10.dp.toPx(), Offset(nx, ny))
                if (up) {
                    drawCircle(SkyColors.sun, 7.5.dp.toPx(), Offset(nx, ny))
                } else {
                    drawCircle(nightLine, 8.dp.toPx(), Offset(nx, ny))
                    drawCircle(cs.surface, 5.dp.toPx(), Offset(nx + 2.5.dp.toPx(), ny - 1.5.dp.toPx()))
                }
            }

            // Sunrise and sunset times under their points, day length centered below them
            val labelY = h * horizonFrac + 6.dp
            val lw = 76.dp
            Text(
                clockLabel(rise, f.zone),
                modifier = Modifier.offset(x = (w * xFrac(rise) - lw / 2).coerceIn(0.dp, w - lw), y = labelY).width(lw),
                fontSize = 13.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center, color = cs.onSurface,
            )
            Text(
                "${dh}h ${dm}m of daylight",
                modifier = Modifier.offset(y = labelY + 22.dp).fillMaxWidth(),
                fontSize = 13.sp, textAlign = TextAlign.Center, color = cs.onSurfaceVariant,
            )
            Text(
                clockLabel(set, f.zone),
                modifier = Modifier.offset(x = (w * xFrac(set) - lw / 2).coerceIn(0.dp, w - lw), y = labelY).width(lw),
                fontSize = 13.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center, color = cs.onSurface,
            )
        }
    }
}
