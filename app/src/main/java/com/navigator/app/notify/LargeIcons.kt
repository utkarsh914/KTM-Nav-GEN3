package com.navigator.app.notify

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.LruCache
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.createBitmap
import com.navigator.app.ble.BccuProtocol.TurnIcon
import com.navigator.app.ui.components.TurnIconArt

/**
 * The large icon of the guidance notification: the dash's own turn glyph, or the speed while
 * overspeeding. Drawn with the platform canvas because a notification can't take a composable.
 */
object LargeIcons {
    private const val SIZE = 128
    private const val CACHED_TURNS = 24
    private const val CACHED_SPEEDS = 40
    private val DASH_BG = Color.rgb(0x1F, 0x21, 0x25)
    private val ALERT_BG = Color.rgb(0xC6, 0x28, 0x28)

    // The same few glyphs and speeds come round on every notification update: draw each once.
    private val turns = LruCache<TurnIcon, Bitmap>(CACHED_TURNS)
    private val speeds = LruCache<Int, Bitmap>(CACHED_SPEEDS)

    fun turn(context: Context, icon: TurnIcon): Bitmap? = turns.get(icon) ?: drawTurn(context, icon)?.also { turns.put(icon, it) }

    fun speed(kmh: Int): Bitmap = speeds.get(kmh) ?: drawSpeed(kmh).also { speeds.put(kmh, it) }

    private fun drawTurn(context: Context, icon: TurnIcon): Bitmap? = runCatching {
        val drawable = TurnIconArt.drawableFor(icon)?.let { AppCompatResources.getDrawable(context, it) } ?: return null
        val bitmap = createBitmap(SIZE, SIZE)
        val canvas = Canvas(bitmap)
        canvas.drawCircle(SIZE / 2f, SIZE / 2f, SIZE / 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = DASH_BG })
        val inset = SIZE / 5
        drawable.mutate().setTint(Color.WHITE)
        drawable.setBounds(inset, inset, SIZE - inset, SIZE - inset)
        drawable.draw(canvas)
        bitmap
    }.getOrNull()

    private fun drawSpeed(kmh: Int): Bitmap {
        val bitmap = createBitmap(SIZE, SIZE)
        val canvas = Canvas(bitmap)
        canvas.drawCircle(SIZE / 2f, SIZE / 2f, SIZE / 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ALERT_BG })
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            textSize = if (kmh >= 100) SIZE * 0.42f else SIZE * 0.52f
        }
        canvas.drawText(kmh.toString(), SIZE / 2f, SIZE / 2f - (text.descent() + text.ascent()) / 2f, text)
        return bitmap
    }
}
