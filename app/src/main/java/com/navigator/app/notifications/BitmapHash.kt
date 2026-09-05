package com.navigator.app.notifications

import android.graphics.Bitmap

/**
 * Content hash of a bitmap's pixels, used to match/label turn-icon bitmaps.
 *
 * Kept in one place because the value must be identical everywhere: the icon
 * cache ([IconHashCache]) and the TFLite classifier ([ManeuverClassifier]) look
 * icons up by this hash, calibration persists it, and the capture flow names PNG
 * files by it. Any drift between copies would silently break those lookups.
 */
internal fun bitmapPixelHash(bitmap: Bitmap): Int {
    val pixels = IntArray(bitmap.width * bitmap.height)
    bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    return pixels.contentHashCode()
}
