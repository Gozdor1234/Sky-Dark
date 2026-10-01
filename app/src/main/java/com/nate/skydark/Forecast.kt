package com.nate.skydark

import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

enum class Units(
    val code: String,
    val label: String,
    val windUnit: String,
    val distUnit: String,
    val celsius: Boolean,
) {
    US("us", "US (°F, mph)", "mph", "mi", false),
    METRIC("ca", "Metric (°C, km/h)", "km/h", "km", true),
    UK("uk", "UK (°C, mph)", "mph", "mi", true);

    /** Pirate Weather reports precipitation intensity in in/h for "us", mm/h otherwise. */
    val toMm: Double get() = if (this == US) 25.4 else 1.0

    companion object {
        fun from(code: String?): Units = entries.firstOrNull { it.code == code } ?: US
    }
}

data class Minute(val time: Long, val mm: Double, val prob: Double, val type: String)

/** One hour of forecast. Also used for "currently", which has the same fields. mm = mm/h. */
data class Hour(
    val time: Long,
    val icon: String,
    val summary: String,
    val mm: Double,
    val prob: Double,
    val type: String,
    val temp: Double,
    val feels: Double,
    val dewPoint: Double,
    val humidity: Double,
    val pressure: Double,
    val wind: Double,
    val gust: Double,
    val bearing: Double,
    val cloud: Double,
    val uv: Double,
    val visibility: Double,
    val stormDist: Double,
    val stormBearing: Double,
)

data class Day(
    val time: Long,
    val icon: String,
    val summary: String,
    val sunrise: Long,
    val sunset: Long,
    val prob: Double,
    val type: String,
    val high: Double,
    val low: Double,
    val wind: Double,
    val uv: Double,
    val humidity: Double,
)

data class Alert(
    val title: String,
    val severity: String,
    val time: Long,
    val expires: Long,
    val description: String,
    val uri: String,
)

data class Forecast(
    val zone: ZoneId,
    val units: Units,
    val current: Hour,
    val minutely: List<Minute>,
    val hourly: List<Hour>,
    val daily: List<Day>,
    val alerts: List<Alert>,
    val fetchedAt: Long,
) {
    /** Hourly entries from the current hour onward. */
    fun upcoming(count: Int): List<Hour> {
        val now = System.currentTimeMillis() / 1000
        return hourly.filter { it.time + 3600 > now }.take(count)
    }

    fun localDate(epochSec: Long): LocalDate = Instant.ofEpochSecond(epochSec).atZone(zone).toLocalDate()

    fun hoursOn(day: Day): List<Hour> {
        val date = localDate(day.time)
        return hourly.filter { localDate(it.time) == date }
    }
}

object PirateWeather {
    const val SIGNUP_URL = "https://pirate-weather.apiable.io"

    fun url(key: String, lat: Double, lon: Double, units: Units): String =
        "https://api.pirateweather.net/forecast/${key.trim()}/$lat,$lon" +
            "?units=${units.code}&extend=hourly&version=2"

    fun parse(root: JSONObject, units: Units, fetchedAt: Long): Forecast {
        val k = units.toMm
        val zone: ZoneId = runCatching { ZoneId.of(root.text("timezone")) }.getOrElse {
            val off = root.optDouble("offset", 0.0)
            ZoneOffset.ofTotalSeconds((off * 3600).toInt())
        }

        fun hour(o: JSONObject) = Hour(
            time = o.optLong("time"),
            icon = o.text("icon"),
            summary = o.text("summary"),
            mm = o.num("precipIntensity") * k,
            prob = o.num("precipProbability"),
            type = o.text("precipType"),
            temp = o.num("temperature"),
            feels = o.num("apparentTemperature"),
            dewPoint = o.num("dewPoint"),
            humidity = o.num("humidity"),
            pressure = o.num("pressure"),
            wind = o.num("windSpeed"),
            gust = o.num("windGust"),
            bearing = o.num("windBearing"),
            cloud = o.num("cloudCover"),
            uv = o.num("uvIndex"),
            visibility = o.num("visibility"),
            stormDist = o.num("nearestStormDistance"),
            stormBearing = o.num("nearestStormBearing"),
        )

        val current = hour(root.optJSONObject("currently") ?: JSONObject())
        val minutely = root.optJSONObject("minutely")?.optJSONArray("data").objects().map {
            Minute(it.optLong("time"), it.num("precipIntensity") * k, it.num("precipProbability"), it.text("precipType"))
        }
        val hourly = root.optJSONObject("hourly")?.optJSONArray("data").objects().map { hour(it) }
        val daily = root.optJSONObject("daily")?.optJSONArray("data").objects().map {
            Day(
                time = it.optLong("time"),
                icon = it.text("icon"),
                summary = it.text("summary"),
                sunrise = it.optLong("sunriseTime"),
                sunset = it.optLong("sunsetTime"),
                prob = it.num("precipProbability"),
                type = it.text("precipType"),
                high = it.num("temperatureHigh").takeUnless { v -> v.isNaN() } ?: it.num("temperatureMax"),
                low = it.num("temperatureLow").takeUnless { v -> v.isNaN() } ?: it.num("temperatureMin"),
                wind = it.num("windSpeed"),
                uv = it.num("uvIndex"),
                humidity = it.num("humidity"),
            )
        }
        val alerts = root.optJSONArray("alerts").objects().map {
            Alert(it.text("title"), it.text("severity"), it.optLong("time"), it.optLong("expires"), it.text("description"), it.text("uri"))
        }
        return Forecast(zone, units, current, minutely, hourly, daily, alerts, fetchedAt)
    }
}
