package com.navigator.app.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NotificationChannelsTest {
    private val ctx: Context get() = RuntimeEnvironment.getApplication()
    private val nm: NotificationManager get() = ctx.getSystemService(NotificationManager::class.java)

    @Test fun oneStatusChannelReplacesTheOldOnesAndTheSeparateAlertsStay() {
        NotificationChannels.RETIRED.forEach { nm.createNotificationChannel(NotificationChannel(it, it, NotificationManager.IMPORTANCE_MIN)) }
        listOf(NotificationChannels.CONNECTION_ALERT, NotificationChannels.OVERSPEED, NotificationChannels.RIDE_SAVED).forEach { NotificationChannels.ensure(ctx, it) }
        NotificationChannels.deleteRetired(ctx)
        NotificationChannels.RETIRED.forEach { assertNull(it, nm.getNotificationChannel(it)) }
        assertEquals(NotificationManager.IMPORTANCE_LOW, nm.getNotificationChannel(NotificationChannels.STATUS).importance)
        listOf(NotificationChannels.CONNECTION_ALERT, NotificationChannels.OVERSPEED, NotificationChannels.RIDE_SAVED).forEach { assertNotNull(it, nm.getNotificationChannel(it)) }
    }
}
