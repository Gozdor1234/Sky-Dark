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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import kotlin.math.exp
import kotlin.math.ln

@OptIn(ExperimentalMaterial3Api::class)
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
    var pulled by remember { mutableStateOf(false) }
    LaunchedEffect(vm.loading) { if (!vm.loading) pulled = false }
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
            IconButton(onClick = onSettings) {
                Icon(Icons.Default.Settings, contentDescription = "Settings")
            }
        }
        // Thin bar for background refreshes; a pull shows its own spinner instead.
        if (vm.loading && !pulled) {
            LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp))
        } else {
            Spacer(Modifier.height(2.dp))
        }

        PullToRefreshBox(
            isRefreshing = pulled && vm.loading,
            onRefresh = {
                pulled = true
                vm.refresh(force = true)
                if (!vm.loading) pulled = false   // nothing to fetch (e.g. no API key yet)
            },
            modifier = Modifier.fillMaxSize(),
        ) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(if (LocalModern.current) 16.dp else 12.dp),
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
                item { SunCard(f) }
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
}

/** Big current temperature: Google Sans Flex (SIL OFL), rounded, SemiBold, digits only. */
private val TempFont = FontFamily(Font(R.font.temp_display, FontWeight.SemiBold))

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
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            WeatherIcon(c.icon, Modifier.size(78.dp), bg = cs.background)
            Spacer(Modifier.width(14.dp))
            Column {
                Text(deg(c.temp), fontSize = 68.sp, lineHeight = 70.sp, fontFamily = TempFont, fontWeight = FontWeight.SemiBold, letterSpacing = (-1).sp)
                Text("Feels ${deg(c.feels)}", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = cs.onSurfaceVariant)
            }
        }
        Text(c.summary.ifBlank { c.sky().label }, fontSize = 18.sp, fontWeight = FontWeight.Medium)
        if (today != null) {
            Text("High ${deg(today.high)}   Low ${deg(today.low)}", fontSize = 13.sp, color = cs.onSurfaceVariant)
        }
        val line = headline(f.upcoming(24), f.zone)
        if (line.isNotBlank()) {
            Text(line, fontSize = 15.sp, modifier = Modifier.padding(top = 4.dp))
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
        SectionTitle("Next 3 hours")
        // Summary and graph read the same minutes, so the words always match the picture.
        val minutes = remember(f) { precipOutlook(f.minutely, f.hourly, OUTLOOK_MIN) }
        Text(precipSummary(minutes, f.hourly, f.zone), fontSize = 17.sp, fontWeight = FontWeight.Medium)
        if (minutes.size >= 2) {
            PrecipGraph(minutes, Modifier.fillMaxWidth().height(100.dp))
            AxisLabels(listOf("Now", "30min", "1hr", "1.5hr", "2hr", "2.5hr", "3hr"))
        }
    }
}

internal const val OUTLOOK_MIN = 180

/** Labels spread evenly under the graph: first flush left, last flush right, the rest centered on their tick. */
@Composable
private fun AxisLabels(labels: List<String>) {
    val cs = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()
    val style = TextStyle(fontSize = 11.sp)
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxWidth().height(16.dp)) {
        val n = labels.size - 1
        labels.forEachIndexed { i, label ->
            val textW = with(density) { measurer.measure(label, style).size.width.toDp() }
            val x = when (i) {
                0 -> 0.dp
                n -> maxWidth - textW
                else -> maxWidth * (i.toFloat() / n) - textW / 2
            }
            Text(label, style = style, color = cs.onSurfaceVariant, modifier = Modifier.offset(x = x))
        }
    }
}

/**
 * One value per minute for the next [count] minutes. The first hour comes from Pirate Weather's
 * minute-by-minute data; everything is topped up with the hourly forecast, spread across the minutes
 * (linear between hour midpoints). Taking the larger of the two keeps light hourly rain visible even
 * when the minute data reads zero.
 */
internal fun precipOutlook(m: List<Minute>, hours: List<Hour>, count: Int): List<Minute> {
    val start = m.firstOrNull()?.time ?: hours.firstOrNull()?.time?.coerceAtLeast(System.currentTimeMillis() / 1000)
        ?: return emptyList()
    if (hours.isEmpty()) return m
    val mids = hours.map { it.time + 1800 }
    fun mm(h: Hour) = if (h.mm.isNaN()) 0.0 else h.mm
    fun pr(h: Hour) = if (h.prob.isNaN()) 0.0 else h.prob
    fun hourly(t: Long): Minute {
        val k = mids.indexOfFirst { it >= t }
        return when (k) {
            -1 -> hours.last().let { Minute(t, mm(it), pr(it), it.type) }
            0 -> hours[0].let { Minute(t, mm(it), pr(it), it.type) }
            else -> {
                val a = hours[k - 1]
                val b = hours[k]
                val f = (t - mids[k - 1]).toDouble() / (mids[k] - mids[k - 1]).coerceAtLeast(1)
                Minute(t, mm(a) + (mm(b) - mm(a)) * f, pr(a) + (pr(b) - pr(a)) * f, if (f < 0.5) a.type else b.type)
            }
        }
    }
    return (0..count).map { i ->
        val t = start + i * 60L
        val h = hourly(t)
        val own = m.getOrNull(i)
        val ownMm = own?.mm?.takeUnless { it.isNaN() } ?: -1.0
        if (own != null && ownMm >= h.mm) own else h.copy(type = own?.type?.ifBlank { h.type } ?: h.type)
    }
}

/**
 * Softens the minute-by-minute series for drawing: a Gaussian blur (about 8 minutes wide) evens out
 * jumps between data sources and steps between hours, then every 3rd minute is kept so the curve
 * flows instead of zig-zagging. Values never go below zero.
 */
private fun smoothSeries(v: List<Double>): List<Double> {
    if (v.size < 3) return v
    val sigma = 4.0
    val r = 10
    val weights = (-r..r).map { exp(-(it * it) / (2 * sigma * sigma)) }
    val blurred = v.indices.map { i ->
        var sum = 0.0
        var wsum = 0.0
        for (k in -r..r) {
            val j = (i + k).coerceIn(0, v.lastIndex)
            sum += v[j] * weights[k + r]
            wsum += weights[k + r]
        }
        (sum / wsum).coerceAtLeast(0.0)
    }
    val step = 3
    val idx = (0 until blurred.size step step).toMutableList()
    if (idx.last() != blurred.lastIndex) idx += blurred.lastIndex
    return idx.map { blurred[it] }
}

/** Precip scale: logarithmic so even drizzle (0.05 mm/h) shows, while heavy rain still tops out. */
private fun precipScale(mm: Double): Float =
    (ln(1.0 + mm.coerceIn(0.0, 10.0) / 0.05) / ln(1.0 + 10.0 / 0.05)).toFloat()

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
            // Two layers: pale = how much would fall if it happens, solid = weighted by its chance.
            fun area(weighted: Boolean): Path {
                val ys = smoothSeries(m.map { p ->
                    val mm = if (p.mm.isNaN()) 0.0 else p.mm
                    val pr = if (p.prob.isNaN()) 1.0 else p.prob.coerceIn(0.0, 1.0)
                    precipScale(if (weighted) mm * pr else mm).toDouble()
                })
                val pts = ys.mapIndexed { i, v -> Offset(w * i / (ys.size - 1), (ht - v * ht).toFloat().coerceAtMost(ht)) }
                return Path().apply {
                    moveTo(0f, ht)
                    lineTo(pts[0].x, pts[0].y)
                    // Curve through the midpoints between samples, using each sample as the control point.
                    for (i in 1 until pts.size) {
                        val a = pts[i - 1]
                        val b = pts[i]
                        quadraticBezierTo(a.x, a.y, (a.x + b.x) / 2f, (a.y + b.y) / 2f)
                    }
                    lineTo(pts.last().x, pts.last().y)
                    lineTo(w, ht)
                    close()
                }
            }
            drawPath(area(weighted = false), fill.copy(alpha = 0.28f))
            drawPath(area(weighted = true), fill.copy(alpha = 0.85f))
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
        val well = if (LocalModern.current) Modifier.neuInset(modernPalette, 12.dp) else Modifier.clip(RoundedCornerShape(12.dp)).background(cs.background)
        Box(Modifier.fillMaxWidth().then(well).padding(10.dp)) {
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
        add(Triple("Wind", wind, DetailIcon.WIND))
        if (!c.gust.isNaN()) add(Triple("Gusts", "${c.gust.roundToInt()} ${u.windUnit}", DetailIcon.GUSTS))
        add(Triple("Humidity", pct(c.humidity), DetailIcon.HUMIDITY))
        add(Triple("Dew Point", deg(c.dewPoint), DetailIcon.DEW))
        add(Triple("UV Index", if (c.uv.isNaN()) "--" else "${c.uv.roundToInt()}", DetailIcon.UV))
        add(Triple("Visibility", if (c.visibility.isNaN()) "--" else "${c.visibility.roundToInt()} ${u.distUnit}", DetailIcon.VISIBILITY))
        add(Triple("Pressure", if (c.pressure.isNaN()) "--" else "${c.pressure.roundToInt()} hPa", DetailIcon.PRESSURE))
        add(Triple("Cloud Cover", pct(c.cloud), DetailIcon.CLOUD))
        if (today != null) {
            add(Triple("Sunrise", clockLabel(today.sunrise, f.zone), DetailIcon.SUNRISE))
            add(Triple("Sunset", clockLabel(today.sunset, f.zone), DetailIcon.SUNSET))
        }
        if (!c.stormDist.isNaN() && c.stormDist > 0) {
            add(Triple("Nearest Storm", "${c.stormDist.roundToInt()} ${u.distUnit} ${compass(c.stormBearing)}".trim(), DetailIcon.STORM))
        }
    }
    Panel {
        SectionTitle("Right now")
        cells.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { (label, value, icon) ->
                    Row(Modifier.weight(1f).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        DetailGlyph(icon, Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(value, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                        }
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
        "Forecasts from Pirate Weather. Radar from NWS NEXRAD and NOAA HRRR via Iowa Environmental Mesonet. Place search from Open-Meteo (GeoNames data).",
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        fontSize = 11.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
