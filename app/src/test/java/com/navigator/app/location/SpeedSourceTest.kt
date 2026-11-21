package com.navigator.app.location

import android.Manifest
import android.app.Application
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SpeedSourceTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private fun fix(metresPerSecond: Float) = Location(LocationManager.GPS_PROVIDER).apply {
        latitude = 12.97; longitude = 77.59; speed = metresPerSecond; time = System.currentTimeMillis()
        elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
    }

    @Test fun reportsKmhWhileHeldAndNothingOnceReleased() {
        shadowOf(context as Application).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val source = SpeedSource(context)
        source.acquire()
        shadowOf(lm).simulateLocation(fix(10f))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(36f, source.speedKmh.value!!, 0.01f)
        source.release()
        assertNull(source.speedKmh.value)
    }

    @Test fun staysOnUntilTheLastHolderReleases() {
        shadowOf(context as Application).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val source = SpeedSource(context)
        source.acquire(); source.acquire()
        source.release()
        shadowOf(lm).simulateLocation(fix(5f))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(18f, source.speedKmh.value!!, 0.01f)
        source.release()
        assertNull(source.speedKmh.value)
    }

    @Test fun withoutPermissionItStaysSilent() {
        val source = SpeedSource(context)
        source.acquire()
        assertNull(source.speedKmh.value)
        source.release()
    }
}
