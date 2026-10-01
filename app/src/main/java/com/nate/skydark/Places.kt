package com.nate.skydark

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Locale
import kotlin.coroutines.resume

const val GPS = "gps"

data class Place(val id: String, val name: String, val region: String, val lat: Double, val lon: Double) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("name", name).put("region", region).put("lat", lat).put("lon", lon)

    companion object {
        fun fromJson(o: JSONObject) = Place(
            o.text("id"), o.text("name"), o.text("region"), o.optDouble("lat"), o.optDouble("lon"),
        )
    }
}

/** Everything the app remembers, in plain SharedPreferences. */
class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("skydark", Context.MODE_PRIVATE)

    var apiKey: String
        get() = sp.getString("api_key", "") ?: ""
        set(v) = sp.edit().putString("api_key", v).apply()

    var units: Units
        get() = Units.from(sp.getString("units", null))
        set(v) = sp.edit().putString("units", v.code).apply()

    var themeMode: ThemeMode
        get() = ThemeMode.from(sp.getString("theme_mode", null))
        set(v) = sp.edit().putString("theme_mode", v.key).apply()

    /** Glass panels with soft raised/pressed shadows. Off = standard flat look. */
    var modern: Boolean
        get() = sp.getBoolean("modern_style", false)
        set(v) = sp.edit().putBoolean("modern_style", v).apply()

    var selected: String
        get() = sp.getString("selected", GPS) ?: GPS
        set(v) = sp.edit().putString("selected", v).apply()

    var places: List<Place>
        get() = runCatching {
            JSONArray(sp.getString("places", "[]")).objects().map { Place.fromJson(it) }
        }.getOrDefault(emptyList())
        set(v) = sp.edit().putString("places", JSONArray(v.map { it.toJson() }).toString()).apply()

    /** Last GPS fix and its place name, so the app opens instantly before a new fix arrives. */
    var gpsPlace: Place?
        get() = sp.getString("gps_place", null)?.let { runCatching { Place.fromJson(JSONObject(it)) }.getOrNull() }
        set(v) = sp.edit().putString("gps_place", v?.toJson()?.toString()).apply()

    fun cache(key: String): Pair<Long, String>? {
        val json = sp.getString("cache_json_$key", null) ?: return null
        return sp.getLong("cache_time_$key", 0L) to json
    }

    fun putCache(key: String, time: Long, json: String) {
        sp.edit().putLong("cache_time_$key", time).putString("cache_json_$key", json).apply()
    }

    fun dropCache(key: String) {
        sp.edit().remove("cache_time_$key").remove("cache_json_$key").apply()
    }
}

object Locate {
    fun hasPermission(ctx: Context): Boolean =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** A recent cached fix if there is one, otherwise a fresh one (Android 11+), else the best stale fix. */
    @SuppressLint("MissingPermission")
    suspend fun current(ctx: Context): Location? {
        if (!hasPermission(ctx)) return null
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            .filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
        val last = providers.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
        if (last != null && System.currentTimeMillis() - last.time < 15 * 60_000) return last

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val provider = providers.firstOrNull { it != LocationManager.PASSIVE_PROVIDER }
            if (provider != null) {
                val fresh = withTimeoutOrNull(12_000) {
                    suspendCancellableCoroutine<Location?> { cont ->
                        val signal = CancellationSignal()
                        cont.invokeOnCancellation { signal.cancel() }
                        try {
                            lm.getCurrentLocation(provider, signal, ContextCompat.getMainExecutor(ctx)) { loc ->
                                if (cont.isActive) cont.resume(loc)
                            }
                        } catch (e: Exception) {
                            if (cont.isActive) cont.resume(null)
                        }
                    }
                }
                if (fresh != null) return fresh
            }
        }
        return last
    }

    /** Town name for a coordinate using the phone's built-in geocoder, or null if unavailable. */
    @Suppress("DEPRECATION")
    suspend fun placeName(ctx: Context, lat: Double, lon: Double): String? = withContext(Dispatchers.IO) {
        if (!Geocoder.isPresent()) return@withContext null
        runCatching {
            val a = Geocoder(ctx, Locale.getDefault()).getFromLocation(lat, lon, 1)?.firstOrNull()
            a?.locality ?: a?.subAdminArea ?: a?.adminArea
        }.getOrNull()
    }

    /** City search via Open-Meteo's free geocoding API (GeoNames data, no key needed). */
    suspend fun search(query: String): List<Place> {
        val q = query.trim()
        if (q.length < 2) return emptyList()
        val url = "https://geocoding-api.open-meteo.com/v1/search?name=" +
            URLEncoder.encode(q, "UTF-8") + "&count=10&language=en&format=json"
        val root = Net.getJson(url)
        return root.optJSONArray("results").objects().map {
            val region = listOf(it.text("admin1"), it.text("country")).filter { s -> s.isNotBlank() }.joinToString(", ")
            Place("om:" + it.optLong("id"), it.text("name"), region, it.optDouble("latitude"), it.optDouble("longitude"))
        }
    }
}
