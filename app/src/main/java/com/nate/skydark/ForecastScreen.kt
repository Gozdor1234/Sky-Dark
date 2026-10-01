package com.nate.skydark

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import kotlin.math.sqrt

@Composable
fun ForecastScreen(
    vm: WeatherViewModel,
    onLocations: () -> Unit,
    onSettings: () -> Unit,
    onRequestPermission: () -> Unit,
) {
    val f = vm.forecast
    var metric by rememberSaveable { mutableStateOf(Metric.TEMP) }
    var expandedDay by rememberSaveable { mutableIntStateOf(-1) }
    val cs = MaterialTheme.colorScheme

    Column(Modifier.fillMaxSize()) {
        // Top bar: place name (tap to switch), last update, refresh, settings
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).clickable(onClick = onLocations).padding(vertical = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        vm.placeName,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Change location", tint = cs.onSurfaceVariant)
                }
                if (f != null) Text(updatedLabel(f.fetchedAt), fontSize = 12.sp, color = cs.onSurfaceVariant)
            }
            IconButton(onClick = { vm.refresh(force = true) }) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh")
            }
            IconButton(onClick = onSettings) {
                Icon(Icons.Default.Settings, contentDescription = "Settings")
            }
        }
        if (vm.loading) {
            LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp))
        } else {
            Spacer(Modifier.height(2.dp))
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (vm.apiKey.isBlank()) {
                item {
                    Notice(
                        "Add your free Pirate Weather API key to start getting forecasts.",
                        "Open Settings",
                        onSettings,
                    )
                }
            } else if (vm.needsPermission && vm.selected == GPS) {
                item {
                    Notice(
                        "Allow location access to see the weather where you are, or tap the place name above to add a city.",
                        "Allow location",
                        onRequestPermission,
                    )
                }
            }
            vm.error?.let { msg ->
                item { Notice(msg, "Try again") { vm.refresh(force = true) } }
            }
            if (f != null) {
                item { Hero(f) }
                items(f.alerts) { AlertCard(it, f) }
                item { NextHour(f) }
                item {
                    Panel {
                        SectionTitle("Next 24 hours")
                        MetricChips(metric) { metric = it }
                        Spacer(Modifier.height(2.dp))
                        Timeline(f.upcoming(24), f.zone, f.units, metric, startsNow = true)
                    }
                }
                item {
                    Week(f, expandedDay, metric, onToggle = { expandedDay = if (expandedDay == it) -1 else it })
                }
                item { Details(f) }
                item { Credits() }
            } else if (vm.loading && vm.error == null) {
                item {
                    Text(
                        "Loading forecast...",
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        color = cs.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun updatedLabel(t: Long): String {
    val mins = ((System.currentTimeMillis() - t) / 60_000).toInt()
    return when {
        mins < 1 -> "Updated just now"
        mins < 60 -> "Updated $mins min ago"
        mins < 60 * 24 -> "Updated ${mins / 60} hr ago"
        else -> "Updated ${mins / (60 * 24)} days ago"
    }
}

@Composable
private fun Notice(text: String, action: String, onAction: () -> Unit) {
    Panel {
        Text(text, fontSize = 15.sp)
        TextButton(onClick = onAction, contentPadding = PaddingValues(0.dp)) { Text(action, fontWeight = FontWeight.SemiBold) }
    }
}

@Composable
private fun Hero(f: Forecast) {
    val c = f.current
    val today = f.daily.firstOrNull()
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            WeatherIcon(c.icon, Modifier.size(78.dp), bg = cs.background)
            Spacer(Modifier.width(14.dp))
            Column {
                Text(deg(c.temp), fontSize = 64.sp, lineHeight = 64.sp, fontWeight = FontWeight.Light)
                Text("Feels ${deg(c.feels)}", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = cs.onSurfaceVariant)
            }
        }
        Text(c.summary.ifBlank { c.sky().label }, fontSize = 18.sp, fontWeight = FontWeight.Medium)
        if (today != null) {
            Text("High ${deg(today.high)}   Low ${deg(today.low)}", fontSize = 13.sp, color = cs.onSurfaceVariant)
        }
        val line = headline(f.upcoming(24), f.zone)
        if (line.isNotBlank()) {
            Text(line, fontSize = 15.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun AlertCard(a: Alert, f: Forecast) {
    var open by remember { mutableStateOf(false) }
    val uri = LocalUriHandler.current
    val cs = MaterialTheme.colorScheme
    Panel(Modifier.clickable { open = !open }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Warning, contentDescription = null, tint = cs.error, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(a.title, fontWeight = FontWeight.SemiBold)
                if (a.expires > 0) Text("Until ${clockLabel(a.expires, f.zone)}, ${dowLabel(a.expires, f.zone)}", fontSize = 12.sp, color = cs.onSurfaceVariant)
            }
        }
        AnimatedVisibility(open) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(a.description.trim(), fontSize = 13.sp)
                if (a.uri.isNotBlank()) {
                    TextButton(onClick = { runCatching { uri.openUri(a.uri) } }, contentPadding = PaddingValues(0.dp)) {
                        Text("Full alert")
                    }
                }
            }
        }
    }
}

@Composable
private fun NextHour(f: Forecast) {
    val cs = MaterialTheme.colorScheme
    Panel {
        SectionTitle("Next hour")
        Text(nextHourSummary(f.minutely), fontSize = 17.sp, fontWeight = FontWeight.Medium)
        if (f.minutely.size >= 2) {
            PrecipGraph(f.minutely, Modifier.fillMaxWidth().height(100.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf("Now", "10m", "20m", "30m", "40m", "50m", "60m").forEach {
                    Text(it, fontSize = 11.sp, color = cs.onSurfaceVariant)
                }
            }
        }
    }
}

/** Precip scale: square root so light rain is visible but heavy rain still tops out. */
private fun precipScale(mm: Double): Float = sqrt(mm.coerceIn(0.0, 10.0) / 10.0).toFloat()

@Composable
private fun PrecipGraph(m: List<Minute>, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    val grid = cs.outlineVariant
    val fill = SkyColors.rain
    BoxWithConstraints(modifier) {
        val h = maxHeight
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val ht = size.height
            val dash = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
            listOf(LIGHT_MM, MODERATE_MM, HEAVY_MM).forEach { lvl ->
                val y = ht - precipScale(lvl) * ht
                drawLine(grid, Offset(0f, y), Offset(w, y), strokeWidth = 1.dp.toPx(), pathEffect = dash)
            }
            drawLine(grid, Offset(0f, ht), Offset(w, ht), strokeWidth = 1.dp.toPx())
            val path = Path().apply {
                moveTo(0f, ht)
                m.forEachIndexed { i, p ->
                    val mm = if (p.mm.isNaN()) 0.0 else p.mm
                    val pr = if (p.prob.isNaN()) 1.0 else p.prob.coerceIn(0.0, 1.0)
                    val x = w * i / (m.size - 1)
                    lineTo(x, ht - precipScale(mm * pr) * ht)
                }
                lineTo(w, ht)
                close()
            }
            drawPath(path, fill.copy(alpha = 0.85f))
        }
        listOf("Light" to LIGHT_MM, "Med" to MODERATE_MM, "Heavy" to HEAVY_MM).forEach { (label, lvl) ->
            Text(
                label,
                fontSize = 10.sp,
                color = cs.onSurfaceVariant,
                modifier = Modifier.offset(y = h * (1f - precipScale(lvl)) - 14.dp),
            )
        }
    }
}

@Composable
private fun Week(f: Forecast, expanded: Int, metric: Metric, onToggle: (Int) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val lows = f.daily.map { it.low }.filter { !it.isNaN() }
    val highs = f.daily.map { it.high }.filter { !it.isNaN() }
    val weekLow = lows.minOrNull() ?: 0.0
    val weekHigh = highs.maxOrNull() ?: 1.0
    Panel {
        SectionTitle("Next ${f.daily.size} days")
        Column {
            f.daily.forEachIndexed { i, d ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { onToggle(i) }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (i == 0) "Today" else dowLabel(d.time, f.zone),
                        modifier = Modifier.width(56.dp),
                        fontWeight = FontWeight.Medium,
                    )
                    SmallIcon(d.icon, 26)
                    Box(Modifier.width(44.dp), contentAlignment = Alignment.Center) {
                        if (!d.prob.isNaN() && d.prob >= 0.2) {
                            Text(pct(d.prob), fontSize = 12.sp, color = SkyColors.rain, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    RangeBar(d.low, d.high, weekLow, weekHigh, f.units, Modifier.weight(1f))
                }
                AnimatedVisibility(expanded == i) {
                    DayDetail(f, d, metric)
                }
                if (i < f.daily.lastIndex) HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.6f))
            }
        }
    }
}

@Composable
private fun DayDetail(f: Forecast, d: Day, metric: Metric) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(bottom = 12.dp, top = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(longDayLabel(d.time, f.zone), fontWeight = FontWeight.SemiBold)
        if (d.summary.isNotBlank()) Text(d.summary, fontSize = 14.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Stat("Sunrise", clockLabel(d.sunrise, f.zone))
            Stat("Sunset", clockLabel(d.sunset, f.zone))
            Stat("Wind", if (d.wind.isNaN()) "--" else "${d.wind.roundToInt()} ${f.units.windUnit}")
            Stat("UV", if (d.uv.isNaN()) "--" else "${d.uv.roundToInt()}")
        }
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(cs.background).padding(10.dp)) {
            Timeline(f.hoursOn(d), f.zone, f.units, metric, startsNow = false)
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column {
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun Details(f: Forecast) {
    val c = f.current
    val u = f.units
    val today = f.daily.firstOrNull()
    val wind = if (c.wind.isNaN()) "--" else "${c.wind.roundToInt()} ${u.windUnit} ${compass(c.bearing)}".trim()
    val cells = buildList {
        add("Wind" to wind)
        if (!c.gust.isNaN()) add("Gusts" to "${c.gust.roundToInt()} ${u.windUnit}")
        add("Humidity" to pct(c.humidity))
        add("Dew Point" to deg(c.dewPoint))
        add("UV Index" to (if (c.uv.isNaN()) "--" else "${c.uv.roundToInt()}"))
        add("Visibility" to (if (c.visibility.isNaN()) "--" else "${c.visibility.roundToInt()} ${u.distUnit}"))
        add("Pressure" to (if (c.pressure.isNaN()) "--" else "${c.pressure.roundToInt()} hPa"))
        add("Cloud Cover" to pct(c.cloud))
        if (today != null) {
            add("Sunrise" to clockLabel(today.sunrise, f.zone))
            add("Sunset" to clockLabel(today.sunset, f.zone))
        }
        if (!c.stormDist.isNaN() && c.stormDist > 0) {
            add("Nearest Storm" to "${c.stormDist.roundToInt()} ${u.distUnit} ${compass(c.stormBearing)}".trim())
        }
    }
    Panel {
        SectionTitle("Right now")
        cells.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { (label, value) ->
                    Column(Modifier.weight(1f).padding(vertical = 4.dp)) {
                        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(value, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Credits() {
    Text(
        "Forecasts from Pirate Weather. Radar from RainViewer. Place search from Open-Meteo (GeoNames data).",
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        fontSize = 11.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
