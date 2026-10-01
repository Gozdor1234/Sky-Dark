package com.nate.skydark

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.ZoneId
import kotlin.math.roundToInt

/** What the right-hand column of the timeline shows. */
enum class Metric(val label: String) {
    TEMP("Temp"),
    FEELS("Feels Like"),
    PRECIP("Precip"),
    WIND("Wind"),
    HUMIDITY("Humidity"),
    UV("UV Index");

    fun value(h: Hour): Double = when (this) {
        TEMP -> h.temp
        FEELS -> h.feels
        PRECIP -> h.prob
        WIND -> h.wind
        HUMIDITY -> h.humidity
        UV -> h.uv
    }

    fun format(v: Double, u: Units): String = if (v.isNaN()) "--" else when (this) {
        TEMP, FEELS -> "${v.roundToInt()}°"
        PRECIP, HUMIDITY -> "${(v * 100).roundToInt()}%"
        WIND -> "${v.roundToInt()} ${u.windUnit}"
        UV -> "${v.roundToInt()}"
    }

    /** Fixed scales where they make sense, otherwise stretch to the data shown. */
    fun range(values: List<Double>): Pair<Double, Double> {
        val ok = values.filter { !it.isNaN() }
        return when (this) {
            PRECIP, HUMIDITY -> 0.0 to 1.0
            UV -> 0.0 to maxOf(11.0, ok.maxOrNull() ?: 0.0)
            WIND -> 0.0 to maxOf(10.0, ok.maxOrNull() ?: 0.0)
            else -> (ok.minOrNull() ?: 0.0) to (ok.maxOrNull() ?: 1.0)
        }
    }
}

@Composable
fun MetricChips(selected: Metric, onSelect: (Metric) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Metric.entries.forEach { m ->
            val on = m == selected
            val cs = MaterialTheme.colorScheme
            Box(
                Modifier
                    .clip(CircleShape)
                    .background(if (on) cs.primary else cs.surfaceVariant)
                    .clickable { onSelect(m) }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
            ) {
                Text(
                    m.label,
                    fontSize = 13.sp,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (on) cs.onPrimary else cs.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Vertical hour-by-hour timeline: time labels, a condition bar split into colored runs,
 * and value pills whose horizontal position tracks the value (warmer/wetter sits further right).
 */
@Composable
fun Timeline(hours: List<Hour>, zone: ZoneId, units: Units, metric: Metric, startsNow: Boolean) {
    if (hours.isEmpty()) {
        Text("Hourly detail isn't available for this day.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val cs = MaterialTheme.colorScheme
    val rowH = 30.dp
    val segs = segments(hours)
    val values = hours.map { metric.value(it) }
    val (lo, hi) = metric.range(values)

    Row(Modifier.fillMaxWidth().height(rowH * hours.size)) {
        // Hour labels, every other hour
        Box(Modifier.width(48.dp).fillMaxHeight()) {
            hours.forEachIndexed { i, h ->
                if (i % 2 == 0) {
                    Box(Modifier.offset(y = rowH * i).height(rowH), contentAlignment = Alignment.CenterStart) {
                        Text(
                            if (i == 0 && startsNow) "Now" else hourLabel(h.time, zone),
                            fontSize = 12.sp,
                            color = cs.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        // Condition bar
        Box(Modifier.width(118.dp).fillMaxHeight()) {
            segs.forEach { s ->
                val fill = SkyColors.of(s.sky)
                val shape = RoundedCornerShape(6.dp)
                Box(
                    Modifier
                        .offset(y = rowH * s.start)
                        .height(rowH * s.length)
                        .fillMaxWidth()
                        .padding(vertical = 1.dp)
                        .clip(shape)
                        .background(fill)
                        .then(if (s.sky == Sky.CLEAR) Modifier.border(1.dp, cs.outlineVariant, shape) else Modifier),
                ) {
                    Text(
                        s.sky.label,
                        modifier = Modifier.padding(start = 8.dp, top = 6.dp, end = 4.dp),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = SkyColors.textOn(fill, cs.onSurfaceVariant),
                    )
                }
            }
        }

        // Value pills
        BoxWithConstraints(Modifier.weight(1f).fillMaxHeight().padding(start = 10.dp)) {
            val pillW = 62.dp
            val track = (maxWidth - pillW).coerceAtLeast(0.dp)
            hours.forEachIndexed { i, h ->
                if (i % 2 == 0) {
                    val v = values[i]
                    val frac = if (hi > lo && !v.isNaN()) ((v - lo) / (hi - lo)).toFloat().coerceIn(0f, 1f) else 0f
                    val tint = if (metric == Metric.TEMP || metric == Metric.FEELS) {
                        SkyColors.temp(toFahrenheit(v, units)).copy(alpha = 0.28f)
                    } else {
                        cs.surfaceVariant
                    }
                    Box(
                        Modifier.offset(x = track * frac, y = rowH * i).width(pillW).height(rowH),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier
                                .clip(CircleShape)
                                .background(tint)
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                        ) {
                            Text(
                                metric.format(v, units),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                color = cs.onSurface,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Week-row temperature span: one shared scale across the week so days compare at a glance. */
@Composable
fun RangeBar(low: Double, high: Double, weekLow: Double, weekHigh: Double, units: Units, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    BoxWithConstraints(modifier.height(28.dp)) {
        val labelW = 38.dp
        val track = (maxWidth - labelW * 2).coerceAtLeast(0.dp)
        val span = (weekHigh - weekLow).takeIf { it > 0 } ?: 1.0
        val l = if (low.isNaN()) 0f else ((low - weekLow) / span).toFloat().coerceIn(0f, 1f)
        val r = if (high.isNaN()) 1f else ((high - weekLow) / span).toFloat().coerceIn(l, 1f)
        val barW = (track * (r - l)).coerceAtLeast(8.dp)
        val lowColor = SkyColors.temp(toFahrenheit(low, units))
        val highColor = SkyColors.temp(toFahrenheit(high, units))

        Box(
            Modifier
                .offset(x = labelW + track * l)
                .align(Alignment.CenterStart)
                .width(barW)
                .height(6.dp)
                .clip(CircleShape)
                .background(androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(lowColor, highColor))),
        )
        Box(Modifier.offset(x = track * l).width(labelW).fillMaxHeight(), contentAlignment = Alignment.CenterEnd) {
            Text(deg(low), modifier = Modifier.padding(end = 6.dp), fontSize = 14.sp, color = cs.onSurfaceVariant)
        }
        Box(Modifier.offset(x = labelW + track * l + barW).width(labelW).fillMaxHeight(), contentAlignment = Alignment.CenterStart) {
            Text(deg(high), modifier = Modifier.padding(start = 6.dp), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = cs.onSurface)
        }
    }
}

@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) { content() }
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text.uppercase(),
        fontSize = 12.sp,
        letterSpacing = 1.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
fun SmallIcon(icon: String, size: Int = 26) {
    WeatherIcon(icon, Modifier.size(size.dp))
}
