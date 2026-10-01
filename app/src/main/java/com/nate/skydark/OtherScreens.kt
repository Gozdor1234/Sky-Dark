package com.nate.skydark

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SubBar(title: String, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }
        Text(title, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun PlaceRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = if (selected) cs.primary else cs.onSurfaceVariant)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
            if (subtitle.isNotBlank()) Text(subtitle, fontSize = 13.sp, color = cs.onSurfaceVariant)
        }
        if (selected) Icon(Icons.Default.Check, contentDescription = "Selected", tint = cs.primary)
        if (onDelete != null) {
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Remove $title", tint = cs.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun LocationsScreen(vm: WeatherViewModel, onBack: () -> Unit, onRequestPermission: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val keyboard = LocalSoftwareKeyboardController.current
    val ctx = LocalContext.current
    fun runSearch() {
        keyboard?.hide()
        vm.search(query)
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        SubBar("Locations") {
            vm.clearSearch()
            onBack()
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search for a city") },
                    singleLine = true,
                    trailingIcon = {
                        IconButton(onClick = { runSearch() }) { Icon(Icons.Default.Search, contentDescription = "Search") }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { runSearch() }),
                )
            }
            if (vm.searching) item { LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 8.dp)) }
            vm.searchError?.let { msg ->
                item { Text(msg, modifier = Modifier.padding(8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            items(vm.searchResults, key = { "r" + it.id }) { p ->
                PlaceRow(Icons.Default.Add, p.name, p.region, selected = false, onClick = {
                    vm.addPlace(p)
                    vm.clearSearch()
                    query = ""
                    onBack()
                })
            }
            item {
                Column(Modifier.padding(top = 12.dp, bottom = 4.dp)) { SectionTitle("Your places") }
            }
            item {
                val gps = vm.gpsPlace
                PlaceRow(
                    Icons.Default.LocationOn,
                    "Current Location",
                    gps?.name ?: "Uses your phone's location",
                    selected = vm.selected == GPS,
                    onClick = {
                        vm.select(GPS)
                        if (!Locate.hasPermission(ctx)) onRequestPermission()
                        onBack()
                    },
                )
            }
            items(vm.places, key = { it.id }) { p ->
                Column {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                PlaceRow(
                    Icons.Default.Place,
                    p.name,
                    p.region,
                    selected = vm.selected == p.id,
                    onClick = {
                        vm.select(p.id)
                        onBack()
                    },
                    onDelete = { vm.removePlace(p.id) },
                )
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(vm: WeatherViewModel, onBack: () -> Unit) {
    var key by rememberSaveable { mutableStateOf(vm.apiKey) }
    val uri = LocalUriHandler.current
    val keyboard = LocalSoftwareKeyboardController.current
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current

    Column(Modifier.fillMaxSize().imePadding()) {
        SubBar("Settings", onBack)
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Panel {
                    SectionTitle("Pirate Weather API key")
                    Text(
                        "Forecasts come from Pirate Weather. Their free plan covers personal use; sign up, " +
                            "subscribe to the free tier, and paste your key here.",
                        fontSize = 14.sp,
                    )
                    Text(
                        "Get a free key",
                        color = cs.primary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable { runCatching { uri.openUri(PirateWeather.SIGNUP_URL) } },
                    )
                    OutlinedTextField(
                        value = key,
                        onValueChange = { key = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("API key") },
                        singleLine = true,
                    )
                    Button(
                        onClick = {
                            keyboard?.hide()
                            vm.saveApiKey(key)
                            onBack()
                        },
                        enabled = key.trim() != vm.apiKey && key.isNotBlank(),
                    ) { Text("Save key") }
                }
            }
            item {
                Panel {
                    SectionTitle("Units")
                    Units.entries.forEach { u ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { vm.changeUnits(u) },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = vm.units == u, onClick = { vm.changeUnits(u) })
                            Text(u.label, fontSize = 15.sp)
                        }
                    }
                }
            }
            item {
                Panel {
                    SectionTitle("About")
                    Text("Sky Dark ${appVersion(ctx)}", fontWeight = FontWeight.SemiBold)
                    Text("Built by ChickenMyBobbers. No ads, no accounts, no tracking.", fontSize = 14.sp)
                    Text(
                        "Forecast data: Pirate Weather (built on NOAA and other public weather models). " +
                            "City search: Open-Meteo geocoding, GeoNames data.",
                        fontSize = 13.sp,
                        color = cs.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Suppress("DEPRECATION")
private fun appVersion(ctx: Context): String =
    runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: ""
