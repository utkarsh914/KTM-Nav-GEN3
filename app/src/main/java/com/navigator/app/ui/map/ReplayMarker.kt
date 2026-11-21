package com.navigator.app.ui.map

import android.content.Context
import android.graphics.Color
import androidx.core.graphics.createBitmap
import android.graphics.Canvas
import android.graphics.Paint
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory

private const val SIZE_DP = 32f
private const val RING_DP = 5f

/** The replay's playback marker: an open ring in [ringColor], so the start and end markers stay visible under it. */
internal fun replayMarkerIcon(context: Context, ringColor: Int): BitmapDescriptor {
    val density = context.resources.displayMetrics.density
    val size = (SIZE_DP * density).toInt()
    val ring = RING_DP * density
    val bitmap = createBitmap(size, size)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val centre = size / 2f
    // A white halo keeps the ring readable on any map colour; the centre stays open over the start marker.
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = ring + 2 * density
    paint.color = Color.WHITE
    canvas.drawCircle(centre, centre, centre - ring, paint)
    paint.strokeWidth = ring
    paint.color = ringColor
    canvas.drawCircle(centre, centre, centre - ring, paint)
    return BitmapDescriptorFactory.fromBitmap(bitmap)
}
