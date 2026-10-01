package com.nate.skydark

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Re-renders a radar tile in the app's own style:
 * 1. Reads each pixel's NWS-scale color back into a reflectivity value (dBZ).
 * 2. Softens the grid with a blur sized to the data's native cell (~1 km NEXRAD, ~3 km HRRR).
 * 3. Paints it with Sky Dark's palette, fading light returns to translucent and dropping clutter.
 */
object RadarPaint {
    // Standard NWS reflectivity colors, 5 dBZ apart starting at 5 dBZ. Tile colors sit on or between these.
    private val ref = intArrayOf(
        0x04E9E7, 0x019FF4, 0x0300F4, 0x02FD02, 0x01C501, 0x008E00, 0xFDF802, 0xE5BC00,
        0xFD9500, 0xFD0000, 0xD40000, 0xBC0000, 0xF800FD, 0x9854C6,
    )
    private const val MAX_DIST_SQ = 45f * 45f
    private const val MIN_DBZ = 8f

    // Sky Dark palette: (dBZ, RGB, alpha). Soft teal for light rain through blue and navy, red, then gold.
    private val stops = listOf(
        Triple(8f, 0xA6DFD6, 0.30f),
        Triple(15f, 0x86D4C9, 0.42f),
        Triple(22f, 0x5FB8D6, 0.58f),
        Triple(30f, 0x3A8FD9, 0.72f),
        Triple(38f, 0x2B4FB8, 0.84f),
        Triple(46f, 0xC23B5A, 0.90f),
        Triple(54f, 0xE0452F, 0.95f),
        Triple(62f, 0xF5C242, 1.00f),
    )

    private val dbzCache = ConcurrentHashMap<Int, Float>()

    /** Reflectivity for an opaque RGB; off-scale grays (clutter) come back as 0. */
    private fun dbz(rgb: Int): Float = dbzCache.getOrPut(rgb) {
        val r = (rgb shr 16 and 255).toFloat()
        val g = (rgb shr 8 and 255).toFloat()
        val b = (rgb and 255).toFloat()
        var best = Float.MAX_VALUE
        var bestDbz = 0f
        for (i in 0 until ref.size - 1) {
            val ar = (ref[i] shr 16 and 255).toFloat(); val ag = (ref[i] shr 8 and 255).toFloat(); val ab = (ref[i] and 255).toFloat()
            val dr = (ref[i + 1] shr 16 and 255) - ar; val dg = (ref[i + 1] shr 8 and 255) - ag; val db = (ref[i + 1] and 255) - ab
            val len = dr * dr + dg * dg + db * db
            val t = if (len == 0f) 0f else (((r - ar) * dr + (g - ag) * dg + (b - ab) * db) / len).coerceIn(0f, 1f)
            val er = r - (ar + t * dr); val eg = g - (ag + t * dg); val eb = b - (ab + t * db)
            val d = er * er + eg * eg + eb * eb
            if (d < best) {
                best = d
                bestDbz = 5f + 5f * (i + t)
            }
        }
        when {
            best <= MAX_DIST_SQ -> bestDbz
            // Off-scale bluish/lavender tints mark very light returns (drizzle, flurries).
            b > r + 20f && b > g -> 10f
            else -> 0f // gray clutter or anything else off the scale
        }
    }

    private fun color(v: Float): Int {
        if (v < MIN_DBZ - 3f) return 0
        if (v < MIN_DBZ) {
            // Soft edge just below the cutoff
            val (_, rgb, a) = stops.first()
            val alpha = a * (v - (MIN_DBZ - 3f)) / 3f
            return argb(alpha, rgb)
        }
        for (i in 0 until stops.size - 1) {
            val (d0, c0, a0) = stops[i]
            val (d1, c1, a1) = stops[i + 1]
            if (v <= d1) {
                val t = (v - d0) / (d1 - d0)
                return argb(a0 + (a1 - a0) * t, mix(c0, c1, t))
            }
        }
        val (_, rgb, a) = stops.last()
        return argb(a, rgb)
    }

    private fun mix(c0: Int, c1: Int, t: Float): Int {
        fun ch(s: Int) = ((c0 shr s and 255) + ((c1 shr s and 255) - (c0 shr s and 255)) * t).roundToInt().coerceIn(0, 255)
        return (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    private fun argb(alpha: Float, rgb: Int): Int = ((alpha.coerceIn(0f, 1f) * 255).roundToInt() shl 24) or (rgb and 0xFFFFFF)

    /** Two box-blur passes in each direction (close to a Gaussian), clamping at the tile edge. */
    private fun blur(src: FloatArray, w: Int, h: Int, r: Int): FloatArray {
        if (r <= 0) return src
        var a = src
        var b = FloatArray(w * h)
        val span = 2 * r + 1
        repeat(2) {
            for (y in 0 until h) {
                val row = y * w
                var sum = 0f
                for (k in -r..r) sum += a[row + k.coerceIn(0, w - 1)]
                for (x in 0 until w) {
                    b[row + x] = sum / span
                    sum += a[row + (x + r + 1).coerceAtMost(w - 1)] - a[row + (x - r).coerceAtLeast(0)]
                }
            }
            var t = a; a = b; b = t
            for (x in 0 until w) {
                var sum = 0f
                for (k in -r..r) sum += a[k.coerceIn(0, h - 1) * w + x]
                for (y in 0 until h) {
                    b[y * w + x] = sum / span
                    sum += a[(y + r + 1).coerceAtMost(h - 1) * w + x] - a[(y - r).coerceAtLeast(0) * w + x]
                }
            }
            t = a; a = b; b = t
        }
        return a
    }

    /**
     * @param zoom tile zoom level, used to size the blur to the data's native resolution
     * @param model true for HRRR forecast tiles (~3 km cells), false for NEXRAD (~1 km)
     * @return PNG bytes, or null if the tile couldn't be decoded (caller passes the original through)
     */
    fun repaint(png: ByteArray, zoom: Int, model: Boolean): ByteArray? {
        val src = BitmapFactory.decodeByteArray(png, 0, png.size) ?: return null
        val w = src.width
        val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        src.recycle()

        val v = FloatArray(w * h)
        for (i in px.indices) {
            val c = px[i]
            v[i] = if ((c ushr 24) < 128) 0f else dbz(c and 0xFFFFFF)
        }
        // Native cell size in tile pixels at this zoom (tile ~ 31,000 km / 2^z wide at mid-latitudes over 256 px).
        val kmPerPx = 120.0 / (1 shl zoom.coerceIn(0, 20))
        val cellPx = (if (model) 3.0 else 1.0) / kmPerPx
        val radius = max(1, (cellPx * 0.6).roundToInt()).coerceAtMost(12)
        val smooth = blur(v, w, h, radius)

        for (i in px.indices) px[i] = color(smooth[i])
        val out = Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
        val bytes = ByteArrayOutputStream(64 * 1024)
        out.compress(Bitmap.CompressFormat.PNG, 100, bytes)
        out.recycle()
        return bytes.toByteArray()
    }
}
