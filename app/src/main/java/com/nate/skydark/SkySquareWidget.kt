package com.nate.skydark

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews
import androidx.compose.ui.graphics.Color

/** Square forecast widget; its background sky follows the current conditions. Tapping opens the app. */
class SkySquareWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        SkyWidgets.render(context)
        SkyWidgets.schedule(context)
    }

    override fun onEnabled(context: Context) = SkyWidgets.schedule(context)

    override fun onDisabled(context: Context) = SkyWidgets.stopIfUnused(context)
}

object SkySquare {
    private val dayIds = listOf(
        intArrayOf(R.id.d0_name, R.id.d0_icon, R.id.d0_lo, R.id.d0_hi),
        intArrayOf(R.id.d1_name, R.id.d1_icon, R.id.d1_lo, R.id.d1_hi),
        intArrayOf(R.id.d2_name, R.id.d2_icon, R.id.d2_lo, R.id.d2_hi),
        intArrayOf(R.id.d3_name, R.id.d3_icon, R.id.d3_lo, R.id.d3_hi),
    )

    fun build(ctx: Context): RemoteViews {
        val prefs = Prefs(ctx)
        val v = RemoteViews(ctx.packageName, R.layout.widget_sky_square)
        v.setOnClickPendingIntent(R.id.s_root, SkyWidgets.openApp(ctx))
        v.setTextViewText(R.id.s_place, SkyWidgets.placeName(prefs))

        val f = SkyWidgets.savedForecast(prefs)
        if (f == null) {
            v.setImageViewResource(R.id.s_bg, R.drawable.sky_cloudy_day)
            v.setTextViewText(R.id.s_temp, "--°")
            v.setTextViewText(R.id.s_cond, if (prefs.apiKey.isBlank()) "Open Sky Dark to add your API key" else "Open Sky Dark to load the forecast")
            return v
        }

        val c = f.current
        val today = f.daily.firstOrNull()
        v.setImageViewResource(R.id.s_bg, skyFor(f))
        // The user's widget opacity setting applies here too, when they've set one.
        prefs.widgetOpacity.takeIf { it in 0..100 }?.let { v.setInt(R.id.s_bg, "setImageAlpha", it * 255 / 100) }

        v.setTextViewText(R.id.s_temp, deg(c.temp))
        v.setImageViewBitmap(R.id.s_icon, SkyWidgets.iconBitmap(ctx, c.icon, true, Color.Transparent, 34))
        v.setTextViewText(R.id.s_hi, if (today != null) deg(today.high) else "")
        v.setTextViewText(R.id.s_lo, if (today != null) deg(today.low) else "")
        v.setTextViewText(R.id.s_cond, c.summary.ifBlank { c.sky().label })

        // The next four days (tomorrow onward)
        val days = f.daily.drop(1).take(4)
        dayIds.forEachIndexed { i, ids ->
            val d = days.getOrNull(i)
            v.setTextViewText(ids[0], d?.let { dowLabel(it.time, f.zone) } ?: "")
            if (d != null) v.setImageViewBitmap(ids[1], SkyWidgets.iconBitmap(ctx, d.icon, true, Color.Transparent, 24))
            v.setTextViewText(ids[2], d?.let { deg(it.low) } ?: "")
            v.setTextViewText(ids[3], d?.let { deg(it.high) } ?: "")
        }
        return v
    }

    /** Background sky for what it's doing outside right now, day or night. */
    private fun skyFor(f: Forecast): Int {
        val c = f.current
        val today = f.daily.firstOrNull()
        val now = System.currentTimeMillis() / 1000
        val night = when {
            c.icon.endsWith("night") -> true
            c.icon.endsWith("day") -> false
            today != null && today.sunrise > 0 && today.sunset > 0 -> now < today.sunrise || now > today.sunset
            else -> false
        }
        return when (c.sky()) {
            Sky.STORM -> R.drawable.sky_storm
            Sky.DRIZZLE, Sky.LIGHT_RAIN, Sky.RAIN, Sky.HEAVY_RAIN, Sky.SLEET -> R.drawable.sky_rain
            Sky.LIGHT_SNOW, Sky.SNOW, Sky.HEAVY_SNOW -> R.drawable.sky_snow
            Sky.FOG -> R.drawable.sky_fog
            Sky.MOSTLY, Sky.OVERCAST -> if (night) R.drawable.sky_cloudy_night else R.drawable.sky_cloudy_day
            Sky.PARTLY -> if (night) R.drawable.sky_partly_night else R.drawable.sky_partly_day
            Sky.CLEAR, Sky.WINDY -> if (night) R.drawable.sky_clear_night else R.drawable.sky_clear_day
        }
    }
}
