package com.nate.skydark

import android.annotation.SuppressLint
import android.content.Context
import android.content.MutableContextWrapper
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
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

private const val ASSET_HOST = "appassets.androidplatform.net"

/**
 * Radar map: a MapLibre page bundled in assets (radar.html) inside a WebView. Observed radar is the
 * NWS NEXRAD composite; forecast radar is NOAA's HRRR model reflectivity. Both cover the US only.
 */
@Composable
fun RadarScreen(vm: WeatherViewModel) {
    val cs = MaterialTheme.colorScheme
    val look = LocalLook.current
    val dark = look.dark
    val amoled = look.amoled
    val glass = look.modern
    val coords = vm.currentCoords
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
        val viewKey = "$lat,$lon,$dark,$amoled,$glass"
        key(viewKey) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx -> RadarWebHolder.obtain(ctx, viewKey, bg, lat, lon, dark, amoled, glass) },
                // Keep the page alive between visits so reopening the tab is instant; just pause playback.
                onRelease = { it.evaluateJavascript("stop()", null) },
            )
        }
    }
}

/**
 * One radar WebView kept across visits to the tab. Reopening reuses it, so the map and every frame
 * that already loaded show instantly. It's rebuilt when the place or look changes, and reloaded
 * when it's more than 5 minutes old so the frames stay current.
 */
@SuppressLint("SetJavaScriptEnabled")
private object RadarWebHolder {
    private var view: WebView? = null
    private var key = ""
    private var loadedAt = 0L
    private const val MAX_AGE_MS = 5 * 60_000L
    private const val PAGE = "https://$ASSET_HOST/assets/radar.html"

    fun obtain(
        ctx: Context, newKey: String, bg: Int,
        lat: Double, lon: Double, dark: Boolean, amoled: Boolean, glass: Boolean,
    ): WebView {
        val existing = view
        if (existing != null && key == newKey) {
            (existing.context as? MutableContextWrapper)?.baseContext = ctx
            (existing.parent as? ViewGroup)?.removeView(existing)
            if (System.currentTimeMillis() - loadedAt > MAX_AGE_MS) {
                loadedAt = System.currentTimeMillis()
                existing.loadUrl(PAGE)
            }
            return existing
        }
        existing?.let {
            (it.parent as? ViewGroup)?.removeView(it)
            it.destroy()
        }
        // MutableContextWrapper lets a kept WebView be re-attached to a new Activity without leaking the old one.
        val web = WebView(MutableContextWrapper(ctx)).apply {
            // Without explicit MATCH_PARENT the page sees a zero/unbounded viewport and the map never draws.
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundColor(bg)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            // Serve the bundled page from a real https origin so map requests behave normally.
            val assets = WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(ctx.applicationContext))
                .build()
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                    val url = request.url
                    if (url.host != ASSET_HOST) return null
                    return assets.shouldInterceptRequest(url) ?: TileProxy.handle(url.encodedPath ?: "")
                }

                override fun onPageFinished(view: WebView, url: String?) {
                    view.evaluateJavascript("init($lat, $lon, $dark, $amoled, $glass)", null)
                    RadarWarmup.main.launch {
                        val init = RadarWarmup.hrrrInit()
                        val arg = if (init == null) "null" else JSONObject.quote(init)
                        view.evaluateJavascript("setForecast($arg)", null)
                    }
                }
            }
            loadUrl(PAGE)
        }
        view = web
        key = newKey
        loadedAt = System.currentTimeMillis()
        return web
    }
}

