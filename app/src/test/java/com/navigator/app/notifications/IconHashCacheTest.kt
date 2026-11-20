package com.navigator.app.notifications

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class IconHashCacheTest {
    private lateinit var ctx: Context

    @Before fun setUp() {
        ctx = RuntimeEnvironment.getApplication()
        ctx.getSharedPreferences("opendash_icon_seen", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun icon(color: Int) = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }

    @Test fun anUnknownIconIsCountedOncePerSightingNotOncePerRefresh() {
        val a = icon(Color.RED)
        val b = icon(Color.BLUE)
        repeat(5) { IconHashCache.lookup(a, ctx) }
        val hashA = bitmapPixelHash(a)
        assertEquals(1, IconHashCache.seenCount(ctx, hashA))
        IconHashCache.lookup(b, ctx)
        IconHashCache.lookup(a, ctx)
        assertEquals(2, IconHashCache.seenCount(ctx, hashA))
        assertEquals(1, IconHashCache.seenCount(ctx, bitmapPixelHash(b)))
    }
}
