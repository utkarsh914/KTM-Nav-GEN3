package com.navigator.app.ui.map

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.graphics.createBitmap
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory

/** Colours (ARGB) for what the planning map draws itself: pins, flag and route time labels. */
data class PreviewPinStyle(val accent: Int, val onAccent: Int, val surface: Int, val onSurface: Int, val outline: Int)

/**
 * The map's own markers, drawn as bitmaps so they match the app's colours (PL3): numbered stop pins, the
 * destination flag and the time label of each route (PL2).
 */
internal class MapPins(context: Context, private val pin: PreviewPinStyle) {
    private val density = context.resources.displayMetrics.density

    private fun dp(v: Float) = v * density

    private fun bitmap(w: Float, h: Float, draw: Canvas.() -> Unit): BitmapDescriptor {
        val bmp = createBitmap(dp(w).toInt().coerceAtLeast(1), dp(h).toInt().coerceAtLeast(1))
        Canvas(bmp).draw()
        return BitmapDescriptorFactory.fromBitmap(bmp)
    }

    private fun paint(color: Int, fill: Boolean = true, stroke: Float = 0f) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        this.style = if (fill) Paint.Style.FILL else Paint.Style.STROKE
        strokeWidth = stroke
    }

    /** A filled circle with the stop's number, ringed in the surface colour so it reads on any map. */
    fun numbered(number: Int): BitmapDescriptor = bitmap(32f, 32f) {
        val c = dp(16f)
        drawCircle(c, c, dp(15f), paint(pin.surface))
        drawCircle(c, c, dp(12.5f), paint(pin.accent))
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = pin.onAccent; textSize = dp(14f); typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER
        }
        drawText(number.toString(), c, c - (text.descent() + text.ascent()) / 2f, text)
    }

    /** The destination: a flag on a pole; the pole's foot is the point. */
    fun flag(): BitmapDescriptor = bitmap(36f, 44f) {
        val pole = dp(8f)
        drawRoundRect(RectF(pole - dp(1.5f), dp(3f), pole + dp(1.5f), dp(43f)), dp(1.5f), dp(1.5f), paint(pin.surface))
        drawRoundRect(RectF(pole - dp(0.75f), dp(4f), pole + dp(0.75f), dp(42f)), dp(1f), dp(1f), paint(pin.onSurface))
        val cloth = Path().apply {
            moveTo(pole, dp(4f)); lineTo(dp(33f), dp(10f)); lineTo(pole, dp(22f)); close()
        }
        drawPath(cloth, paint(pin.surface, stroke = dp(3f)).apply { strokeJoin = Paint.Join.ROUND })
        drawPath(cloth, paint(pin.accent))
    }

    /** A rounded label ("42 min") sitting on a route; the selected route's is filled with the accent. */
    fun callout(text: String, selected: Boolean): BitmapDescriptor {
        val t = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (selected) pin.onAccent else pin.onSurface
            textSize = dp(14f); typeface = Typeface.DEFAULT_BOLD
        }
        val w = t.measureText(text) + dp(20f)
        val h = dp(30f)
        return bitmap(w / density, h / density) {
            val r = RectF(dp(1f), dp(1f), w - dp(1f), h - dp(1f))
            drawRoundRect(r, h / 2, h / 2, paint(if (selected) pin.accent else pin.surface))
            drawRoundRect(r, h / 2, h / 2, paint(pin.outline, fill = false, stroke = dp(1.5f)))
            drawText(text, dp(10f), h / 2 - (t.descent() + t.ascent()) / 2f, t)
        }
    }
}

/**
 * The two ends of a recorded ride: a white-ringed disc with a play triangle where it began and a stop square where it
 * ended. Fixed colours (green and near-black) because a ride is drawn in its own speed colours, not the accent.
 */
internal class RideEndpointPins(context: Context) {
    private val density = context.resources.displayMetrics.density

    private fun dp(v: Float) = v * density

    private fun disc(fill: Int, glyph: Canvas.(c: Float, paint: Paint) -> Unit): BitmapDescriptor {
        val size = dp(30f).toInt()
        val bmp = createBitmap(size, size)
        val canvas = Canvas(bmp)
        val c = size / 2f
        canvas.drawCircle(c, c, dp(14f), Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() })
        canvas.drawCircle(c, c, dp(11.5f), Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fill })
        canvas.glyph(c, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() })
        return BitmapDescriptorFactory.fromBitmap(bmp)
    }

    val start: BitmapDescriptor by lazy {
        disc(0xFF2E9E5B.toInt()) { c, paint ->
            drawPath(Path().apply { moveTo(c - dp(3f), c - dp(5f)); lineTo(c + dp(5.5f), c); lineTo(c - dp(3f), c + dp(5f)); close() }, paint)
        }
    }

    val end: BitmapDescriptor by lazy {
        disc(0xFF1B1D21.toInt()) { c, paint -> drawRoundRect(RectF(c - dp(4.5f), c - dp(4.5f), c + dp(4.5f), c + dp(4.5f)), dp(1f), dp(1f), paint) }
    }
}
