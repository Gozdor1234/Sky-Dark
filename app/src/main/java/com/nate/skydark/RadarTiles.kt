package com.nate.skydark

import android.util.LruCache
import android.webkit.WebResourceResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.tan

/**
 * Map tiles the radar page asks for under the app's own origin, fetched here and handed back.
 * Same-origin responses sidestep cross-site rules in the WebView. Finished (re-painted) radar tiles
 * are kept in memory so reopening the tab, or a tile warmed up in advance, shows instantly.
 */
object TileProxy {
    private val upstreams = listOf(
        "/iem/" to "https://mesonet.agron.iastate.edu/cache/tile.py/1.0.0/",
        "/iemv/" to "https://mesonet.agron.iastate.edu/cache/tile.py/1.0.0/",
        "/esri/" to "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/",
    )
    private val headers = mapOf("Access-Control-Allow-Origin" to "*")

    private class Entry(val bytes: ByteArray, val type: String, val time: Long)

    // ~40 MB of finished tiles.
    private val memory = object : LruCache<String, Entry>(40 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Entry) = value.bytes.size
    }

    /** "Latest" and forecast tiles change as new data arrives; dated observed tiles never do. */
    private fun maxAgeMs(path: String): Long = when {
        path.contains("-N0Q-0/") -> 4 * 60_000L
        path.contains("hrrr::") -> 30 * 60_000L
        else -> 6 * 60 * 60_000L
    }

    fun handle(path: String): WebResourceResponse? {
        if (upstreams.none { path.startsWith(it.first) }) return null
        val e = fetch(path) ?: return empty(404)
        return WebResourceResponse(e.type, null, 200, "OK", headers, ByteArrayInputStream(e.bytes))
    }

    /** Returns the finished tile, from memory if fresh, otherwise downloaded (and re-painted for radar). */
    private fun fetch(path: String): Entry? {
        memory.get(path)?.let { if (System.currentTimeMillis() - it.time < maxAgeMs(path)) return it }
        val (prefix, base) = upstreams.first { path.startsWith(it.first) }
        return try {
            val conn = URL(base + path.removePrefix(prefix)).openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000
            conn.useCaches = true
            conn.setRequestProperty("User-Agent", "SkyDark/1.0 (personal Android weather app)")
            try {
                if (conn.responseCode != 200) return null
                val bytes = conn.inputStream.use { it.readBytes() }
                val type = conn.contentType?.substringBefore(';')?.trim() ?: "image/png"
                val entry = if ((prefix == "/iem/" || prefix == "/iemv/") && type == "image/png") {
                    // Radar tiles get re-rendered in the app's palette with the grid smoothed out.
                    val parts = path.split('/')
                    val zoom = parts.getOrNull(parts.size - 3)?.toIntOrNull() ?: 7
                    val styled = runCatching { RadarPaint.repaint(bytes, zoom, model = path.contains("hrrr::"), gray = prefix == "/iemv/") }.getOrNull()
                    if (styled != null) Entry(styled, "image/png", System.currentTimeMillis())
                    else Entry(bytes, type, System.currentTimeMillis())
                } else {
                    Entry(bytes, type, System.currentTimeMillis())
                }
                memory.put(path, entry)
                entry
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            null
        }
    }

    // Android rejects 3xx codes here, so anything unusual becomes a plain 404.
    private fun empty(code: Int): WebResourceResponse {
        val safe = if (code in 400..599) code else 404
        return WebResourceResponse("text/plain", null, safe, "Unavailable", headers, ByteArrayInputStream(ByteArray(0)))
    }

    /** Download + re-paint ahead of time (no-op if already fresh in memory). */
    fun warm(path: String) {
        fetch(path)
    }
}

/**
 * Gets the radar ready before the tab is opened: the newest HRRR run time, and the latest observed
 * radar tiles around the selected place at the zoom the map opens at.
 */
object RadarWarmup {
    private const val HRRR_STATUS = "https://mesonet.agron.iastate.edu/data/gis/images/4326/hrrr/refd_1080.json"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Main-thread scope that outlives a single screen (the radar WebView is kept between visits). */
    val main = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    @Volatile private var hrrrInit: String? = null
    @Volatile private var hrrrFetchedAt = 0L
    @Volatile private var lastWarm: Triple<Double, Double, Long>? = null

    /** Newest HRRR run time (ISO, UTC), cached for 10 minutes. Null if the status file can't be read. */
    suspend fun hrrrInit(): String? {
        val cached = hrrrInit
        if (cached != null && System.currentTimeMillis() - hrrrFetchedAt < 10 * 60_000) return cached
        val fresh = runCatching { Net.getJson(HRRR_STATUS).text("model_init_utc").ifBlank { null } }.getOrNull()
        if (fresh != null) {
            hrrrInit = fresh
            hrrrFetchedAt = System.currentTimeMillis()
        }
        return fresh ?: cached
    }

    /**
     * Matches radar.html: it opens at map zoom 6.3 and builds each frame from intensity tiles at
     * round(zoom + 1.5) = zoom 8.
     */
    fun warm(lat: Double, lon: Double) {
        val last = lastWarm
        val now = System.currentTimeMillis()
        if (last != null && last.first == lat && last.second == lon && now - last.third < 3 * 60_000) return
        lastWarm = Triple(lat, lon, now)
        scope.launch {
            launch { hrrrInit() }
            val z = 8
            val n = 1 shl z
            val cx = floor((lon + 180.0) / 360.0 * n).toInt()
            val latRad = lat * PI / 180.0
            val cy = floor((1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * n).toInt()
            val gate = Semaphore(6)
            (cy - 3..cy + 3).flatMap { y -> (cx - 2..cx + 2).map { x -> x to y } }
                .filter { (x, y) -> y in 0 until n }
                .map { (x, y) ->
                    val wx = ((x % n) + n) % n
                    async { gate.withPermit { TileProxy.warm("/iemv/ridge::USCOMP-N0Q-0/$z/$wx/$y.png") } }
                }
                .awaitAll()
        }
    }
}
