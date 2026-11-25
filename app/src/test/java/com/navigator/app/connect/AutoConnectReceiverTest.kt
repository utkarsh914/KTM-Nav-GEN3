package com.navigator.app.connect

import android.app.Application
import android.content.Context
import android.content.Intent
import com.navigator.app.settings.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class AutoConnectReceiverTest {
    private val ctx: Context get() = RuntimeEnvironment.getApplication()

    private fun boot() = AutoConnectReceiver().onReceive(ctx, Intent(Intent.ACTION_BOOT_COMPLETED))

    @Test fun afterAQuitNothingStartsUntilTheRiderOpensTheApp() {
        val settings = AppSettings(ctx)
        settings.bondedDeviceAddress = "AA:BB:CC:DD:EE:FF"
        settings.quitUntilOpen = true
        boot()
        assertNull("boot, Bluetooth and the bike all go through this receiver", shadowOf(ctx as Application).nextStartedService)
        settings.quitUntilOpen = false
        boot()
        assertNotNull(shadowOf(ctx as Application).nextStartedService)
    }

    @Test fun theStartHelperIsGatedToo() {
        val settings = AppSettings(ctx)
        settings.quitUntilOpen = true
        com.navigator.app.ble.BccuConnectionService.start(ctx, "AA:BB:CC:DD:EE:FF")
        assertNull(shadowOf(ctx as Application).nextStartedService)
        assertEquals(true, AppSettings(ctx).quitUntilOpen)
    }
}
