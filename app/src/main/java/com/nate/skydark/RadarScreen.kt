package com.nate.skydark

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL

/** Newest HRRR run that the Iowa Environmental Mesonet has finished rendering (all 18 forecast hours). */
private const val HRRR_STATUS = "https://mesonet.agron.iastate.edu/data/gis/images/4326/hrrr/refd_1080.json"
private const val ASSET_HOST = "appassets.androidplatform.net"

/**
 * Map tiles the radar page asks for under our own origin, fetched here and handed back to the page.
 * Same-origin responses sidestep cross-site rules in the WebView; HttpResponseCache (installed in
 * MainActivity) keeps repeat views fast.
 */
private object TileProxy {
    private val upstreams = listOf(
        "/iem/" to "https://mesonet.agron.iastate.edu/cache/tile.py/1.0.0/",
        "/esri/" to "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/",
    )
    private val headers = mapOf("Access-Control-Allow-Origin" to "*")

    fun handle(path: String): WebResourceResponse? {
        val (prefix, base) = upstreams.firstOrNull { path.startsWith(it.first) } ?: return null
        return try {
            val conn = URL(base + path.removePrefix(prefix)).openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000
            conn.useCaches = true
            conn.setRequestProperty("User-Agent", "SkyDark/1.0 (personal Android weather app)")
            try {
                val code = conn.responseCode
                if (code != 200) return empty(code)
                val bytes = conn.inputStream.use { it.readBytes() }
                val type = conn.contentType?.substringBefore(';')?.trim() ?: "image/png"
                if (prefix == "/iem/" && type == "image/png") {
                    // Radar tiles get re-rendered in the app's palette with the grid smoothed out.
                    val parts = path.split('/')
                    val zoom = parts.getOrNull(parts.size - 3)?.toIntOrNull() ?: 7
                    val styled = runCatching { RadarPaint.repaint(bytes, zoom, model = path.contains("hrrr::")) }.getOrNull()
                    if (styled != null) {
                        return WebResourceResponse("image/png", null, 200, "OK", headers, ByteArrayInputStream(styled))
                    }
                }
                WebResourceResponse(type, null, 200, "OK", headers, ByteArrayInputStream(bytes))
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            empty(502)
        }
    }

    // Android rejects 3xx codes here, so anything unusual becomes a plain 404.
    private fun empty(code: Int): WebResourceResponse {
        val safe = if (code in 400..599) code else 404
        return WebResourceResponse("text/plain", null, safe, "Unavailable", headers, ByteArrayInputStream(ByteArray(0)))
    }
}

/**
 * Radar map: a MapLibre page bundled in assets (radar.html) inside a WebView. Observed radar is the
 * NWS NEXRAD composite; forecast radar is NOAA's HRRR model reflectivity. Both cover the US only.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun RadarScreen(vm: WeatherViewModel) {
    val cs = MaterialTheme.colorScheme
    val dark = isSystemInDarkTheme()
    val coords = vm.currentCoords
    val scope = rememberCoroutineScope()
    val bg = cs.background.toArgb()

    Column(Modifier.fillMaxSize()) {
        Text(
            vm.placeName,
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 10.dp),
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (coords == null) {
            Box(Modifier.fillMaxSize().padding(24.dp)) {
                Text(
                    "Pick a location on the Forecast tab first, then come back here for radar.",
                    color = cs.onSurfaceVariant,
                )
            }
            return@Column
        }
        val (lat, lon) = coords
        key(lat, lon, dark) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        // Without explicit MATCH_PARENT the page sees a zero/unbounded viewport and the map never draws.
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                        setBackgroundColor(bg)
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        // Serve the bundled page from a real https origin so map requests behave normally.
                        val assets = WebViewAssetLoader.Builder()
                            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(ctx))
                            .build()
                        webViewClient = object : WebViewClient() {
                            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                                val url = request.url
                                if (url.host != ASSET_HOST) return null
                                return assets.shouldInterceptRequest(url) ?: TileProxy.handle(url.encodedPath ?: "")
                            }

                            override fun onPageFinished(view: WebView, url: String?) {
                                view.evaluateJavascript("init($lat, $lon, $dark)", null)
                                scope.launch {
                                    val init = try {
                                        Net.getJson(HRRR_STATUS).text("model_init_utc").ifBlank { null }
                                    } catch (e: Exception) {
                                        null
                                    }
                                    val arg = if (init == null) "null" else JSONObject.quote(init)
                                    view.evaluateJavascript("setTimeline($arg)", null)
                                }
                            }
                        }
                        loadUrl("https://$ASSET_HOST/assets/radar.html")
                    }
                },
                onRelease = { it.destroy() },
            )
        }
    }
}
