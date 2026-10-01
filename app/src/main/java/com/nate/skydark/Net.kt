package com.nate.skydark

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** A non-2xx HTTP reply, kept separate so callers can explain 401/429 etc. in plain words. */
class HttpError(val code: Int, host: String) : IOException("HTTP $code from $host")

/** Minimal HTTP + JSON helpers. Uses Android's built-in org.json, no extra libraries. */
object Net {
    suspend fun getJson(url: String): JSONObject = withContext(Dispatchers.IO) {
        val u = URL(url)
        val conn = u.openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 20_000
        conn.setRequestProperty("Accept", "application/json")
        conn.setRequestProperty("User-Agent", "SkyDark/1.0 (personal Android app)")
        try {
            val code = conn.responseCode
            if (code !in 200..299) throw HttpError(code, u.host)
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            JSONObject(text)
        } finally {
            conn.disconnect()
        }
    }
}

fun JSONArray?.objects(): List<JSONObject> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { optJSONObject(it) }
}

/** Missing, null, or Pirate Weather's -999 "no data" marker all become NaN. */
fun JSONObject.num(key: String): Double {
    if (isNull(key)) return Double.NaN
    val v = optDouble(key, Double.NaN)
    return if (v <= -999.0) Double.NaN else v
}

fun JSONObject.text(key: String): String = if (isNull(key)) "" else optString(key, "")
