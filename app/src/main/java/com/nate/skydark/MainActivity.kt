package com.nate.skydark

import android.Manifest
import android.net.http.HttpResponseCache
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import java.io.File

class MainActivity : ComponentActivity() {
    private val vm: WeatherViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Disk cache for map/radar tiles fetched through the radar tab's tile proxy.
        if (HttpResponseCache.getInstalled() == null) {
            runCatching { HttpResponseCache.install(File(cacheDir, "http"), 50L * 1024 * 1024) }
        }
        enableEdgeToEdge()
        setContent {
            SkyTheme(vm.themeMode, vm.modern) { App(vm) }
        }
    }
}

private enum class Screen { FORECAST, RADAR, LOCATIONS, SETTINGS }

@Composable
private fun App(vm: WeatherViewModel) {
    var screen by rememberSaveable { mutableStateOf(Screen.FORECAST) }
    val askLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.any { it }) vm.permissionGranted()
    }
    val requestPermission = {
        askLocation.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
    }

    // Refresh whenever the app comes back to the foreground (skipped if the data is under 10 min old).
    LifecycleResumeEffect(Unit) {
        vm.refresh(force = false)
        onPauseOrDispose { }
    }

    BackHandler(enabled = screen != Screen.FORECAST) { screen = Screen.FORECAST }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            Box(Modifier.weight(1f)) {
                when (screen) {
                    Screen.FORECAST -> ForecastScreen(
                        vm,
                        onLocations = { screen = Screen.LOCATIONS },
                        onSettings = { screen = Screen.SETTINGS },
                        onRequestPermission = requestPermission,
                    )
                    Screen.RADAR -> RadarScreen(vm)
                    Screen.LOCATIONS -> LocationsScreen(
                        vm,
                        onBack = { screen = Screen.FORECAST },
                        onRequestPermission = requestPermission,
                    )
                    Screen.SETTINGS -> SettingsScreen(vm, onBack = { screen = Screen.FORECAST })
                }
            }
            if (screen == Screen.FORECAST || screen == Screen.RADAR) {
                BottomBar(screen) { screen = it }
            }
        }
    }
}

@Composable
private fun BottomBar(current: Screen, onSelect: (Screen) -> Unit) {
    val tabs = listOf(
        Triple(Screen.FORECAST, "Forecast", R.drawable.ic_nav_forecast),
        Triple(Screen.RADAR, "Radar", R.drawable.ic_nav_radar),
    )
    if (LocalModern.current) {
        // Floating raised glass bar with the selected tab pressed in.
        val p = modernPalette
        val cs = MaterialTheme.colorScheme
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 10.dp)
                .height(68.dp)
                .neuRaised(p, 26.dp)
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEach { (tab, label, icon) ->
                val sel = current == tab
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .then(if (sel) Modifier.neuInset(p, 18.dp) else Modifier)
                        .clip(RoundedCornerShape(18.dp))
                        .clickable { onSelect(tab) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    val tint = if (sel) cs.primary else cs.onSurfaceVariant
                    Icon(painterResource(icon), contentDescription = null, tint = tint)
                    Text(label, fontSize = 12.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium, color = tint)
                }
            }
        }
    } else {
        NavigationBar(
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            windowInsets = WindowInsets(0, 0, 0, 0),
        ) {
            tabs.forEach { (tab, label, icon) ->
                NavigationBarItem(
                    selected = current == tab,
                    onClick = { onSelect(tab) },
                    icon = { Icon(painterResource(icon), contentDescription = null) },
                    label = { Text(label) },
                )
            }
        }
    }
}
