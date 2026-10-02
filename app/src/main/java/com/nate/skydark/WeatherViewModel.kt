package com.nate.skydark

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class WeatherViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = Prefs(app)

    var apiKey by mutableStateOf(prefs.apiKey); private set
    var units by mutableStateOf(prefs.units); private set
    var places by mutableStateOf(prefs.places); private set
    var selected by mutableStateOf(prefs.selected); private set
    var gpsPlace by mutableStateOf(prefs.gpsPlace); private set

    var themeMode by mutableStateOf(prefs.themeMode); private set
    var modern by mutableStateOf(prefs.modern); private set

    var forecast by mutableStateOf<Forecast?>(null); private set
    var loading by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var needsPermission by mutableStateOf(false); private set

    var searchResults by mutableStateOf<List<Place>>(emptyList()); private set
    var searching by mutableStateOf(false); private set
    var searchError by mutableStateOf<String?>(null); private set

    private var job: Job? = null
    private var loadToken = 0
    private var searchJob: Job? = null

    init {
        if (places.none { it.id == selected } && selected != GPS) selected = GPS
        needsPermission = selected == GPS && !Locate.hasPermission(app)
        loadCache()
        currentCoords?.let { RadarWarmup.warm(it.first, it.second) }
    }

    val placeName: String
        get() = if (selected == GPS) gpsPlace?.name ?: "Current Location"
        else places.firstOrNull { it.id == selected }?.name ?: "Unknown place"

    /** Coordinates of the selected place (last GPS fix for "Current Location"), if known. */
    val currentCoords: Pair<Double, Double>?
        get() = if (selected == GPS) gpsPlace?.let { it.lat to it.lon }
        else places.firstOrNull { it.id == selected }?.let { it.lat to it.lon }

    fun changeThemeMode(m: ThemeMode) {
        prefs.themeMode = m
        themeMode = m
        SkyWidgets.render(getApplication<Application>())
    }

    fun changeModern(on: Boolean) {
        prefs.modern = on
        modern = on
        SkyWidgets.render(getApplication<Application>())
    }

    fun saveApiKey(key: String) {
        val k = key.trim()
        prefs.apiKey = k
        apiKey = k
        error = null
        refresh(force = true)
    }

    fun changeUnits(u: Units) {
        if (u == units) return
        prefs.units = u
        units = u
        forecast = null
        loadCache()
        refresh(force = true)
    }

    fun select(id: String) {
        prefs.selected = id
        SkyWidgets.render(getApplication<Application>())
        selected = id
        forecast = null
        error = null
        needsPermission = id == GPS && !Locate.hasPermission(getApplication<Application>())
        loadCache()
        refresh(force = false)
    }

    fun addPlace(p: Place) {
        places = places.filterNot { it.id == p.id } + p
        prefs.places = places
        select(p.id)
    }

    fun removePlace(id: String) {
        places = places.filterNot { it.id == id }
        prefs.places = places
        Units.entries.forEach { prefs.dropCache("$id|${it.code}") }
        if (selected == id) select(GPS)
    }

    fun permissionGranted() {
        needsPermission = false
        if (selected == GPS) refresh(force = true)
    }

    fun search(query: String) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            searching = true
            searchError = null
            try {
                searchResults = Locate.search(query)
                if (searchResults.isEmpty()) searchError = "No places found."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                searchError = "Search failed. Check your connection."
            } finally {
                searching = false
            }
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        searchResults = emptyList()
        searchError = null
        searching = false
    }

    private fun cacheKey() = "$selected|${units.code}"

    private fun loadCache() {
        val (time, json) = prefs.cache(cacheKey()) ?: return
        forecast = runCatching { PirateWeather.parse(JSONObject(json), units, time) }.getOrNull()
    }

    /** Fetches a new forecast unless the one on screen is under 10 minutes old (force skips that check). */
    fun refresh(force: Boolean) {
        if (apiKey.isBlank()) return
        val f = forecast
        if (!force && f != null && System.currentTimeMillis() - f.fetchedAt < 10 * 60_000) return
        if (!force && job?.isActive == true) return
        job?.cancel()
        val key = cacheKey()
        val u = units
        val token = ++loadToken
        loading = true
        job = viewModelScope.launch {
            try {
                val coords = coordinates() ?: return@launch
                val now = System.currentTimeMillis()
                val json = Net.getJson(PirateWeather.url(apiKey, coords.first, coords.second, u))
                val parsed = withContext(Dispatchers.Default) { PirateWeather.parse(json, u, now) }
                forecast = parsed
                error = null
                RadarWarmup.warm(coords.first, coords.second)
                prefs.putCache(key, now, json.toString())
                SkyWidgets.render(getApplication<Application>())
            } catch (e: CancellationException) {
                throw e
            } catch (e: HttpError) {
                error = when (e.code) {
                    400 -> "Pirate Weather didn't accept that location."
                    401, 403 -> "Pirate Weather rejected the API key. Check it in Settings."
                    429 -> "API call limit reached. Try again later."
                    else -> "Forecast server error (${e.code}). Try again shortly."
                }
            } catch (e: Exception) {
                error = "Couldn't load the forecast. Check your connection."
            } finally {
                if (token == loadToken) loading = false
            }
        }
    }

    private suspend fun coordinates(): Pair<Double, Double>? {
        if (selected != GPS) {
            val p = places.firstOrNull { it.id == selected } ?: return null
            return p.lat to p.lon
        }
        val ctx = getApplication<Application>()
        if (!Locate.hasPermission(ctx)) {
            needsPermission = true
            return gpsPlace?.let { it.lat to it.lon }
        }
        needsPermission = false
        val loc = Locate.current(ctx)
        if (loc == null) {
            val old = gpsPlace
            if (old != null) return old.lat to old.lon
            error = "Couldn't get your location. Make sure location is turned on, or add a place."
            return null
        }
        val name = Locate.placeName(ctx, loc.latitude, loc.longitude) ?: gpsPlace?.name ?: "Current Location"
        val p = Place(GPS, name, "", loc.latitude, loc.longitude)
        gpsPlace = p
        prefs.gpsPlace = p
        return p.lat to p.lon
    }
}
