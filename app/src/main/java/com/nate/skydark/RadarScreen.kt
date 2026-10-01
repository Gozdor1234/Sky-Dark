package com.nate.skydark

import android.annotation.SuppressLint
import android.view.ViewGroup
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
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

private const val RAINVIEWER_MAPS = "https://api.rainviewer.com/public/weather-maps.json"

/**
 * Radar map: a Leaflet page bundled in assets (radar.html) inside a WebView. The app fetches the
 * frame list from RainViewer and hands it to the page; the page draws the base map and animates frames.
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
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView, url: String?) {
                                view.evaluateJavascript("init($lat, $lon, $dark)", null)
                                scope.launch {
                                    val js = try {
                                        val root = Net.getJson(RAINVIEWER_MAPS)
                                        val host = root.text("host").ifBlank { "https://tilecache.rainviewer.com" }
                                        val past = root.optJSONObject("radar")?.optJSONArray("past") ?: JSONArray()
                                        "setFrames(${JSONObject.quote(host)}, $past)"
                                    } catch (e: Exception) {
                                        "showError(${JSONObject.quote("Couldn't load radar. Check your connection.")})"
                                    }
                                    view.evaluateJavascript(js, null)
                                }
                            }
                        }
                        loadUrl("file:///android_asset/radar.html")
                    }
                },
                onRelease = { it.destroy() },
            )
        }
    }
}
