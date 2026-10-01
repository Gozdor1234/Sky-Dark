package com.nate.skydark

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** Condition buckets used for the timeline bar and the plain-English summaries. */
enum class Sky(val label: String) {
    CLEAR("Clear"),
    PARTLY("Partly Cloudy"),
    MOSTLY("Mostly Cloudy"),
    OVERCAST("Overcast"),
    FOG("Foggy"),
    WINDY("Windy"),
    DRIZZLE("Drizzle"),
    LIGHT_RAIN("Light Rain"),
    RAIN("Rain"),
    HEAVY_RAIN("Heavy Rain"),
    LIGHT_SNOW("Light Snow"),
    SNOW("Snow"),
    HEAVY_SNOW("Heavy Snow"),
    SLEET("Sleet"),
    STORM("Thunderstorms");

    val isPrecip: Boolean get() = ordinal >= DRIZZLE.ordinal
}

// Intensity thresholds in mm/h (liquid equivalent). Used for both labels and graph gridlines.
const val LIGHT_MM = 0.5
const val MODERATE_MM = 2.5
const val HEAVY_MM = 7.6

/** Hourly: counts as precipitation when it's both measurable and reasonably likely. */
fun hourWet(mm: Double, prob: Double): Boolean =
    !mm.isNaN() && mm >= 0.1 && (prob.isNaN() || prob >= 0.3)

/** Minutely: same idea, slightly stricter on probability since there are 60 noisy points. */
fun minuteWet(m: Minute): Boolean =
    !m.mm.isNaN() && m.mm >= 0.1 && (m.prob.isNaN() || m.prob >= 0.35)

fun Hour.sky(): Sky {
    if (hourWet(mm, prob)) {
        if (icon == "thunderstorm") return Sky.STORM
        return when (type) {
            "snow" -> when {
                mm < 0.5 -> Sky.LIGHT_SNOW
                mm < 2.0 -> Sky.SNOW
                else -> Sky.HEAVY_SNOW
            }
            "sleet", "hail", "ice" -> Sky.SLEET
            else -> when {
                mm < 0.25 -> Sky.DRIZZLE
                mm < 1.0 -> Sky.LIGHT_RAIN
                mm < 4.0 -> Sky.RAIN
                else -> Sky.HEAVY_RAIN
            }
        }
    }
    if (icon == "fog") return Sky.FOG
    if (icon == "wind") return Sky.WINDY
    if (cloud.isNaN()) {
        return when (icon) {
            "clear-day", "clear-night" -> Sky.CLEAR
            "partly-cloudy-day", "partly-cloudy-night" -> Sky.PARTLY
            "cloudy" -> Sky.OVERCAST
            else -> Sky.MOSTLY
        }
    }
    return when {
        cloud < 0.2 -> Sky.CLEAR
        cloud < 0.5 -> Sky.PARTLY
        cloud < 0.85 -> Sky.MOSTLY
        else -> Sky.OVERCAST
    }
}

/** A run of consecutive hours with the same condition. end is exclusive. */
data class Segment(val start: Int, val end: Int, val sky: Sky) {
    val length: Int get() = end - start
}

fun segments(hours: List<Hour>): List<Segment> {
    val out = mutableListOf<Segment>()
    var start = 0
    for (i in 1..hours.size) {
        if (i == hours.size || hours[i].sky() != hours[start].sky()) {
            if (hours.isNotEmpty()) out += Segment(start, i, hours[start].sky())
            start = i
        }
    }
    return out
}

/** "Mostly cloudy until 3PM, then light rain." Single-hour blips are folded in for readability. */
fun headline(hours: List<Hour>, zone: ZoneId): String {
    if (hours.isEmpty()) return ""
    val merged = mutableListOf<Segment>()
    for (s in segments(hours)) {
        val last = merged.lastOrNull()
        when {
            last != null && last.sky == s.sky -> merged[merged.lastIndex] = Segment(last.start, s.end, last.sky)
            last != null && s.length == 1 && !s.sky.isPrecip -> merged[merged.lastIndex] = Segment(last.start, s.end, last.sky)
            else -> merged += s
        }
    }
    val first = merged[0]
    if (merged.size == 1) return "${first.sky.label} for the next ${hours.size} hours."
    val until = hourLabel(hours[first.end].time, zone)
    val then = merged[1].sky.label.lowercase(Locale.getDefault())
    return "${first.sky.label} until $until, then $then."
}

private fun precipNoun(type: String, peakMm: Double): String {
    val noun = when (type) {
        "snow" -> "snow"
        "sleet", "hail", "ice" -> "sleet"
        "rain" -> "rain"
        else -> "precipitation"
    }
    return when {
        peakMm < 0.25 && noun == "rain" -> "drizzle"
        peakMm < 0.25 && noun == "snow" -> "flurries"
        peakMm < 1.0 -> "light $noun"
        peakMm >= HEAVY_MM -> "heavy $noun"
        else -> noun
    }
}

private fun String.cap() = replaceFirstChar { it.titlecase(Locale.getDefault()) }

/** A trace the model thinks is plausible, even if not likely: shown as "possible". */
private fun minutePossible(m: Minute): Boolean =
    !m.mm.isNaN() && m.mm >= 0.02 && (m.prob.isNaN() || m.prob >= 0.15)

/** "12 min", "1 hr", "1 hr 35 min" (rounded to 5 min past the first hour). */
private fun inTime(min: Int): String {
    if (min < 60) return "$min min"
    val r = (min + 2) / 5 * 5
    val h = r / 60
    val mm = r % 60
    return if (mm == 0) "$h hr" else "$h hr $mm min"
}

/**
 * Plain-English line for the precipitation outlook, e.g. "Light rain starting in 12 min, stopping
 * 1 hr 25 min later." Tiers: likely precipitation, then possible (low-chance traces), then a look
 * past the window at the next few hours.
 */
fun precipSummary(m: List<Minute>, hours: List<Hour>, zone: ZoneId): String {
    if (m.isEmpty()) return laterLine(hours, zone, 0) ?: "Precipitation forecast isn't available here."
    val window = if (m.size > 70) "the next ${(m.size - 1) / 60} hours" else "the hour"
    fun typeOf(range: IntRange) = range.map { m[it].type }.firstOrNull { it.isNotBlank() } ?: "rain"
    fun peak(range: IntRange) = range.maxOf { m[it].mm.takeUnless { v -> v.isNaN() } ?: 0.0 }

    fun describe(flags: List<Boolean>, prefix: String): String? {
        if (flags[0]) {
            val stop = flags.indexOfFirst { !it }
            val span = 0 until (if (stop == -1) m.size else stop)
            val noun = (prefix + precipNoun(typeOf(span), peak(span))).cap()
            return if (stop == -1) "$noun for $window." else "$noun stopping in ${inTime(stop)}."
        }
        val start = flags.indexOfFirst { it }
        if (start == -1) return null
        val stopRel = flags.drop(start).indexOfFirst { !it }
        val span = start until (if (stopRel == -1) m.size else start + stopRel)
        val noun = (prefix + precipNoun(typeOf(span), peak(span))).cap()
        return if (stopRel == -1) "$noun starting in ${inTime(start)}."
        else "$noun starting in ${inTime(start)}, stopping ${inTime(stopRel)} later."
    }

    describe(m.map { minuteWet(it) }, "")?.let { return it }
    describe(m.map { minutePossible(it) }, "possible ")?.let { return it }
    val later = laterLine(hours, zone, m.last().time)
    return if (later != null) "Dry for $window. $later" else "No precipitation for $window."
}

/** "Drizzle likely around 1AM." from the few hourly entries after [after] (epoch s), or null if they're dry. */
private fun laterLine(hours: List<Hour>, zone: ZoneId, after: Long): String? {
    val from = maxOf(after, System.currentTimeMillis() / 1000)
    val next = hours.filter { it.time > from }.take(4)
    val h = next.firstOrNull { hourWet(it.mm, it.prob) } ?: return null
    val noun = precipNoun(h.type.ifBlank { "rain" }, h.mm).cap()
    return "$noun likely around ${hourLabel(h.time, zone)}."
}

// ---- Formatting ----

private val hourFmt = DateTimeFormatter.ofPattern("ha", Locale.getDefault())
private val clockFmt = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
private val dowFmt = DateTimeFormatter.ofPattern("EEE", Locale.getDefault())
private val longDayFmt = DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.getDefault())

fun hourLabel(t: Long, zone: ZoneId): String = hourFmt.format(Instant.ofEpochSecond(t).atZone(zone))
fun clockLabel(t: Long, zone: ZoneId): String =
    if (t <= 0) "--" else clockFmt.format(Instant.ofEpochSecond(t).atZone(zone))
fun dowLabel(t: Long, zone: ZoneId): String = dowFmt.format(Instant.ofEpochSecond(t).atZone(zone))
fun longDayLabel(t: Long, zone: ZoneId): String = longDayFmt.format(Instant.ofEpochSecond(t).atZone(zone))

fun deg(v: Double): String = if (v.isNaN()) "--" else "${v.roundToInt()}°"
fun pct(v: Double): String = if (v.isNaN()) "--" else "${(v * 100).roundToInt()}%"

fun compass(bearing: Double): String {
    if (bearing.isNaN()) return ""
    val dirs = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
    return dirs[((bearing % 360 + 360) % 360 / 45.0).roundToInt() % 8]
}

fun toFahrenheit(v: Double, units: Units): Double = if (units.celsius) v * 9 / 5 + 32 else v
