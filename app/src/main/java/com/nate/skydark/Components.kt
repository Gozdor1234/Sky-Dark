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
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.text.font.FontStyle
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
    ChipRow {
        Metric.entries.forEach { m -> Chip(m.label, m == selected) { onSelect(m) } }
    }
}

/** Horizontally scrolling row of chips, with room for Modern-style shadows. */
@Composable
fun ChipRow(content: @Composable () -> Unit) {
    val modern = LocalModern.current
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = if (modern) 6.dp else 0.dp, horizontal = if (modern) 4.dp else 0.dp),
        horizontalArrangement = Arrangement.spacedBy(if (modern) 10.dp else 8.dp),
    ) { content() }
}

/** Pill toggle. Standard: filled accent when on. Modern: raised glass pill, pressed in (accent text) when on. */
@Composable
fun Chip(label: String, on: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    if (LocalModern.current) {
        val p = modernPalette
        Box(
            Modifier
                .then(if (on) Modifier.neuInset(p, 18.dp) else Modifier.neuRaised(p, 18.dp, 3.dp, 6.dp))
                .clip(CircleShape)
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            Text(
                label,
                fontSize = 13.sp,
                fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                color = if (on) cs.primary else cs.onSurfaceVariant,
            )
        }
    } else {
        Box(
            Modifier
                .clip(CircleShape)
                .background(if (on) cs.primary else cs.surfaceVariant)
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 7.dp),
        ) {
            Text(
                label,
                fontSize = 13.sp,
                fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                color = if (on) cs.onPrimary else cs.onSurfaceVariant,
            )
        }
    }
}

/**
 * Hour-by-hour timeline. A slim condition bar runs down the left edge (one colored slice per hour),
 * and each row covers two hours: time, the condition where it changes, and a value pill whose
 * horizontal position tracks the value (warmer, wetter, windier sits further right).
 */
@Composable
fun Timeline(hours: List<Hour>, zone: ZoneId, units: Units, metric: Metric, startsNow: Boolean) {
    if (hours.isEmpty()) {
        Text("Hourly detail isn't available for this day.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val cs = MaterialTheme.colorScheme
    val hourH = 22.dp
    val rowCount = (hours.size + 1) / 2
    val skies = hours.map { it.sky() }
    val starts = segments(hours).associateBy { it.start }
    val values = hours.map { metric.value(it) }
    val (lo, hi) = metric.range(values)

    Row(Modifier.fillMaxWidth().height(hourH * (rowCount * 2))) {
        // Slim condition bar: track color shows through for clear hours.
        Box(
            Modifier
                .width(8.dp)
                .height(hourH * hours.size)
                .clip(RoundedCornerShape(4.dp))
                .background(cs.surfaceVariant),
        ) {
            skies.forEachIndexed { i, sky ->
                val fill = SkyColors.of(sky)
                if (fill.alpha > 0f) {
                    Box(Modifier.offset(y = hourH * i).height(hourH).fillMaxWidth().background(fill))
                }
            }
        }
        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            for (r in 0 until rowCount) {
                val i = r * 2
                val h = hours[i]
                // Name the condition on the row where it begins (either hour of the pair).
                val label = starts[i]?.sky?.label ?: starts[i + 1]?.sky?.label
                Row(Modifier.fillMaxWidth().height(hourH * 2), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (i == 0 && startsNow) "Now" else hourLabel(h.time, zone),
                        modifier = Modifier.width(50.dp),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = cs.onSurface,
                    )
                    Box(Modifier.width(96.dp)) {
                        if (label != null) {
                            Text(
                                label,
                                fontSize = 12.sp,
                                fontStyle = FontStyle.Italic,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = cs.onSurfaceVariant,
                            )
                        }
                    }
                    BoxWithConstraints(Modifier.weight(1f).fillMaxHeight()) {
                        val pillW = 58.dp
                        val track = (maxWidth - pillW).coerceAtLeast(0.dp)
                        val v = values[i]
                        val frac = if (hi > lo && !v.isNaN()) ((v - lo) / (hi - lo)).toFloat().coerceIn(0f, 1f) else 0f
                        val tint = if (metric == Metric.TEMP || metric == Metric.FEELS) {
                            SkyColors.temp(toFahrenheit(v, units)).copy(alpha = 0.25f)
                        } else {
                            cs.surfaceVariant
                        }
                        // Thin guide line leading to the pill
                        Box(
                            Modifier
                                .align(Alignment.CenterStart)
                                .width(track * frac + 10.dp)
                                .height(1.dp)
                                .background(cs.outlineVariant),
                        )
                        Box(
                            Modifier.offset(x = track * frac).width(pillW).fillMaxHeight(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                Modifier
                                    .clip(CircleShape)
                                    .background(tint)
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                            ) {
                                Text(
                                    metric.format(v, units),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
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
    val shape = RoundedCornerShape(18.dp)
    val surface = if (LocalModern.current) {
        // Raised glass panel with soft shadows (Modern style)
        Modifier.neuRaised(modernPalette, 18.dp).clip(shape)
    } else {
        Modifier.clip(shape).background(MaterialTheme.colorScheme.surface)
    }
    Column(
        modifier.fillMaxWidth().then(surface).padding(16.dp),
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
