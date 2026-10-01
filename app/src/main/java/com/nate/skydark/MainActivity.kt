package com.nate.skydark

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LifecycleResumeEffect

class MainActivity : ComponentActivity() {
    private val vm: WeatherViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SkyTheme { App(vm) }
        }
    }
}

private enum class Screen { FORECAST, LOCATIONS, SETTINGS }

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
        Box(Modifier.fillMaxSize().systemBarsPadding()) {
            when (screen) {
                Screen.FORECAST -> ForecastScreen(
                    vm,
                    onLocations = { screen = Screen.LOCATIONS },
                    onSettings = { screen = Screen.SETTINGS },
                    onRequestPermission = requestPermission,
                )
                Screen.LOCATIONS -> LocationsScreen(
                    vm,
                    onBack = { screen = Screen.FORECAST },
                    onRequestPermission = requestPermission,
                )
                Screen.SETTINGS -> SettingsScreen(vm, onBack = { screen = Screen.FORECAST })
            }
        }
    }
}
