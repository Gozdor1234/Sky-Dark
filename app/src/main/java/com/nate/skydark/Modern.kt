package com.nate.skydark

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * "Modern" style: frosted-glass surfaces with soft neumorphic (raised / pressed-in) shadows,
 * the same look as Scoreology. Toggled in Settings > Appearance; off = the flat standard look.
 */

/** True when the Modern style is on. */
val LocalModern = staticCompositionLocalOf { false }

@Immutable
data class ModernPalette(
    /** Solid page color the shadows are cast from. */
    val base: Color,
    /** Translucent glass fill on raised surfaces. */
    val glass: Color,
    /** Fill for pressed-in wells. */
    val well: Color,
    /** Thin highlight edge around glass. */
    val edge: Color,
    /** Bottom-right shadow. */
    val shade: Color,
    /** Top-left highlight. */
    val light: Color,
    /** Faint dark hairline so edges stay visible on every side, even on a near-white page. */
    val outline: Color = Color.Transparent,
    /** Soft all-around shadow that outlines the left and top edges. */
    val ambient: Color = Color.Transparent,
)

val modernPalette: ModernPalette
    @Composable @ReadOnlyComposable get() {
        val bg = MaterialTheme.colorScheme.background
        return if (bg.luminance() < 0.3f) {
            ModernPalette(
                base = bg,
                glass = Color.White.copy(alpha = 0.06f),
                well = Color.Black.copy(alpha = 0.18f),
                edge = Color.White.copy(alpha = 0.10f),
                shade = Color.Black.copy(alpha = 0.60f),
                light = Color.White.copy(alpha = 0.05f),
            )
        } else {
            ModernPalette(
                base = bg,
                glass = Color.White.copy(alpha = 0.42f),
                well = Color.White.copy(alpha = 0.16f),
                edge = Color.White.copy(alpha = 0.80f),
                shade = Color(0xFF1E3A64).copy(alpha = 0.22f),
                light = Color.White.copy(alpha = 0.90f),
                outline = Color(0xFF1E3A64).copy(alpha = 0.12f),
                ambient = Color(0xFF1E3A64).copy(alpha = 0.10f),
            )
        }
    }

/** Light page color used by Modern in light mode (unless a custom background is set). */
val ModernLightBackground = Color(0xFFE6ECF3)

/** Raised glass surface: soft shadow bottom-right, glow top-left, translucent fill, bright edge. */
fun Modifier.neuRaised(p: ModernPalette, radius: Dp, distance: Dp = 5.dp, blur: Dp = 10.dp): Modifier = this.drawBehind {
    val r = radius.toPx()
    val d = distance.toPx()
    val b = blur.toPx()
    drawIntoCanvas { c ->
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        paint.color = p.base.toArgb()
        paint.setShadowLayer(b, d, d, p.shade.toArgb())
        c.nativeCanvas.drawRoundRect(0f, 0f, size.width, size.height, r, r, paint)
        paint.setShadowLayer(b, -d, -d, p.light.toArgb())
        c.nativeCanvas.drawRoundRect(0f, 0f, size.width, size.height, r, r, paint)
        if (p.ambient.alpha > 0f) {
            paint.setShadowLayer(b * 0.6f, 0f, 0f, p.ambient.toArgb())
            c.nativeCanvas.drawRoundRect(0f, 0f, size.width, size.height, r, r, paint)
        }
    }
    val cr = androidx.compose.ui.geometry.CornerRadius(r)
    drawRoundRect(p.glass, cornerRadius = cr)
    // Dark hairline outside, bright edge just inside: a crisp border on all four sides.
    if (p.outline.alpha > 0f) drawRoundRect(p.outline, cornerRadius = cr, style = Stroke(1.dp.toPx()))
    val inset = 1.dp.toPx()
    drawRoundRect(
        p.edge,
        topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
        size = androidx.compose.ui.geometry.Size(size.width - 2 * inset, size.height - 2 * inset),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius((r - inset).coerceAtLeast(0f)),
        style = Stroke(1.dp.toPx()),
    )
}

/** Pressed-in well: inner shadow top-left, inner glow bottom-right. */
fun Modifier.neuInset(p: ModernPalette, radius: Dp, distance: Dp = 3.dp, blur: Dp = 7.dp): Modifier = this.drawBehind {
    val r = radius.toPx()
    val d = distance.toPx()
    val b = blur.toPx()
    drawRoundRect(p.well, cornerRadius = androidx.compose.ui.geometry.CornerRadius(r))
    drawIntoCanvas { c ->
        val nc = c.nativeCanvas
        val clip = android.graphics.Path().apply {
            addRoundRect(0f, 0f, size.width, size.height, r, r, android.graphics.Path.Direction.CW)
        }
        nc.save()
        nc.clipPath(clip)
        // A frame just outside the shape casts its shadow inward; the clip keeps only that shadow.
        val frame = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = b * 2
        }
        frame.color = p.shade.toArgb()
        frame.setShadowLayer(b, d, d, p.shade.toArgb())
        nc.drawRoundRect(-b, -b, size.width + b, size.height + b, r + b, r + b, frame)
        frame.color = p.light.toArgb()
        frame.setShadowLayer(b, -d, -d, p.light.toArgb())
        nc.drawRoundRect(-b, -b, size.width + b, size.height + b, r + b, r + b, frame)
        nc.restore()
    }
}


/** Pressed-in well in Modern (no-op otherwise). */
@Composable
fun Modifier.modernWell(radius: Dp = 14.dp): Modifier =
    if (LocalModern.current) this.neuInset(modernPalette, radius) else this
