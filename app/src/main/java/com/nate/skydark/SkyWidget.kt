package com.nate.skydark

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import android.widget.RemoteViews
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * One-row home-screen widget: icon, current temperature, place and condition, the next-few-hours
 * line, and today's high/low. Styled like the app (theme mode and Modern glass look from Settings).
 * Tapping it opens the app.
 */
class SkyWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        SkyWidgets.render(context)
        SkyWidgets.schedule(context)
    }

    override fun onEnabled(context: Context) = SkyWidgets.schedule(context)

    override fun onDisabled(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(SkyWidgets.WORK)
    }
}

object SkyWidgets {
    const val WORK = "sky_widget_refresh"

    private fun ids(ctx: Context): IntArray =
        AppWidgetManager.getInstance(ctx).getAppWidgetIds(ComponentName(ctx, SkyWidget::class.java))

    /** Background refresh every 30 minutes while a widget is on the home screen. */
    fun schedule(ctx: Context) {
        if (ids(ctx).isEmpty()) return
        val req = PeriodicWorkRequestBuilder<WidgetRefreshWorker>(30, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, req)
    }

    /** Redraws every widget from the forecast the app last saved. Cheap; safe to call often. */
    fun render(ctx: Context) {
        val all = ids(ctx)
        if (all.isEmpty()) return
        val views = build(ctx)
        AppWidgetManager.getInstance(ctx).updateAppWidget(all, views)
    }

    private fun build(ctx: Context): RemoteViews {
        val prefs = Prefs(ctx)
        val dark = when (prefs.themeMode) {
            ThemeMode.LIGHT -> false
            ThemeMode.DARK, ThemeMode.AMOLED -> true
            ThemeMode.SYSTEM -> (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        }
        val amoled = prefs.themeMode == ThemeMode.AMOLED
        val glass = prefs.modern
        val bgRes = when {
            glass && amoled -> R.drawable.widget_bg_glass_amoled
            glass && dark -> R.drawable.widget_bg_glass_dark
            glass -> R.drawable.widget_bg_glass_light
            amoled -> R.drawable.widget_bg_amoled
            dark -> R.drawable.widget_bg_dark
            else -> R.drawable.widget_bg_light
        }
        val text = if (dark) 0xFFE5E8ED.toInt() else 0xFF181C22.toInt()
        val sub = if (dark) 0xFF9AA4B2.toInt() else 0xFF5B6472.toInt()
        val bgColor = when {
            amoled -> Color.Black
            dark -> Color(0xFF171B22)
            else -> Color(0xFFF3F5F8)
        }

        val v = RemoteViews(ctx.packageName, R.layout.widget_sky)
        v.setInt(R.id.w_root, "setBackgroundResource", bgRes)
        listOf(R.id.w_temp, R.id.w_title, R.id.w_high).forEach { v.setTextColor(it, text) }
        listOf(R.id.w_line, R.id.w_low).forEach { v.setTextColor(it, sub) }

        val open = Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        v.setOnClickPendingIntent(
            R.id.w_root,
            PendingIntent.getActivity(ctx, 0, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE),
        )

        val place = if (prefs.selected == GPS) prefs.gpsPlace?.name ?: "Current Location"
        else prefs.places.firstOrNull { it.id == prefs.selected }?.name ?: "Sky Dark"
        val cached = prefs.cache("${prefs.selected}|${prefs.units.code}")
        val f = cached?.let { (time, json) ->
            runCatching { PirateWeather.parse(JSONObject(json), prefs.units, time) }.getOrNull()
        }
        if (f == null) {
            v.setTextViewText(R.id.w_temp, "--°")
            v.setTextViewText(R.id.w_title, place)
            v.setTextViewText(
                R.id.w_line,
                if (prefs.apiKey.isBlank()) "Open Sky Dark to add your API key" else "Open Sky Dark to load the forecast",
            )
            v.setTextViewText(R.id.w_high, "")
            v.setTextViewText(R.id.w_low, "")
            return v
        }

        val c = f.current
        val today = f.daily.firstOrNull()
        val condition = c.summary.ifBlank { c.sky().label }
        // Rain line if any is coming in the next 3 hours, otherwise the 24-hour outlook.
        val minutes = precipOutlook(f.minutely, f.hourly, OUTLOOK_MIN)
        val rain = precipSummary(minutes, f.hourly, f.zone)
        val line = if (rain.startsWith("No precipitation") || rain.startsWith("Dry for")) headline(f.upcoming(24), f.zone) else rain

        v.setImageViewBitmap(R.id.w_icon, iconBitmap(ctx, c.icon, dark, bgColor))
        v.setTextViewText(R.id.w_temp, deg(c.temp))
        v.setTextViewText(R.id.w_title, "$place · $condition")
        v.setTextViewText(R.id.w_line, line)
        v.setTextViewText(R.id.w_high, if (today != null) "H ${deg(today.high)}" else "")
        v.setTextViewText(R.id.w_low, if (today != null) "L ${deg(today.low)}" else "")
        return v
    }

    /** The app's own weather glyph, drawn into a bitmap (widgets can't run Compose). */
    private fun iconBitmap(ctx: Context, icon: String, dark: Boolean, bg: Color): Bitmap {
        val dm = ctx.resources.displayMetrics
        val px = (40 * dm.density).toInt().coerceAtLeast(48)
        val img = ImageBitmap(px, px)
        CanvasDrawScope().draw(Density(dm.density), LayoutDirection.Ltr, Canvas(img), Size(px.toFloat(), px.toFloat())) {
            drawWeatherIcon(icon, dark, bg)
        }
        return img.asAndroidBitmap()
    }
}

/** Fetches a fresh forecast for the selected place in the background, then redraws the widget. */
class WidgetRefreshWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val prefs = Prefs(applicationContext)
        if (prefs.apiKey.isBlank()) return Result.success()
        // Background location needs an extra permission, so "Current Location" uses the last known spot.
        val p = if (prefs.selected == GPS) prefs.gpsPlace else prefs.places.firstOrNull { it.id == prefs.selected }
        if (p != null) {
            val key = "${prefs.selected}|${prefs.units.code}"
            val last = prefs.cache(key)?.first ?: 0L
            if (System.currentTimeMillis() - last > 10 * 60_000) {
                runCatching {
                    val now = System.currentTimeMillis()
                    val json = Net.getJson(PirateWeather.url(prefs.apiKey, p.lat, p.lon, prefs.units))
                    prefs.putCache(key, now, json.toString())
                }
            }
        }
        SkyWidgets.render(applicationContext)
        return Result.success()
    }
}
