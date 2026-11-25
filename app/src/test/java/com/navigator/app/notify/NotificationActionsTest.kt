package com.navigator.app.notify

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.navigator.app.app.AppIntents
import com.navigator.app.settings.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NotificationActionsTest {
    private val ctx: Context get() = RuntimeEnvironment.getApplication()

    private fun intentOf(pending: PendingIntent): Intent = shadowOf(pending).savedIntent

    @Test fun everyIntentWeSendOurselvesGoesThroughTheInternalEntry() {
        val intent = intentOf(NotificationActions.open(ctx, 3, AppIntents.REQUEST_QUIT))
        assertEquals(AppIntents.INTERNAL_ENTRY, intent.component!!.className)
        assertTrue(AppIntents.isTrusted(intent))
        assertFalse("an intent for MainActivity itself is another app's", AppIntents.isTrusted(Intent(ctx, com.navigator.app.ui.MainActivity::class.java)))
    }

    @Test fun aRequestButtonOpensTheAppOnThatRequestWithoutTheNavScreen() {
        val intent = intentOf(NotificationActions.open(ctx, 3, AppIntents.REQUEST_REPAIR))
        assertEquals(AppIntents.REQUEST_REPAIR, intent.getStringExtra(AppIntents.EXTRA_REQUEST))
        assertFalse(intent.getBooleanExtra(AppIntents.EXTRA_OPEN_NAV, true))
    }

    @Test fun aPlainOpenGoesToTheNavScreenAndCarriesARideId() {
        val plain = intentOf(NotificationActions.open(ctx, 0))
        assertTrue(plain.getBooleanExtra(AppIntents.EXTRA_OPEN_NAV, false))
        assertNull(plain.getStringExtra(AppIntents.EXTRA_REQUEST))
        val ride = intentOf(NotificationActions.open(ctx, 9, rideId = "42", openNav = false))
        assertEquals("42", ride.getStringExtra(AppIntents.EXTRA_RIDE_ID))
        assertFalse(ride.getBooleanExtra(AppIntents.EXTRA_OPEN_NAV, true))
    }

    @Test fun differentCodesGiveDifferentPendingIntentsForTheSameRequest() {
        // Extras don't tell PendingIntents apart: the request code does, so Re-pair and Quit need their own.
        val a = NotificationActions.open(ctx, 6, AppIntents.REQUEST_REPAIR)
        val b = NotificationActions.open(ctx, 7, AppIntents.REQUEST_REPAIR)
        assertNotEquals(shadowOf(a).requestCode, shadowOf(b).requestCode)
        assertEquals(6, shadowOf(a).requestCode)
    }

    @Test fun theBeepButtonFlipsTheSettingAndOtherActionsAreIgnored() {
        val settings = AppSettings(ctx)
        settings.turnBeepEnabled = true
        NotificationActionReceiver().onReceive(ctx, Intent(NotificationActions.ACTION_TOGGLE_BEEPS))
        assertFalse(AppSettings(ctx).turnBeepEnabled)
        NotificationActionReceiver().onReceive(ctx, Intent(NotificationActions.ACTION_TOGGLE_BEEPS))
        assertTrue(AppSettings(ctx).turnBeepEnabled)
        NotificationActionReceiver().onReceive(ctx, Intent("something.else"))
        assertTrue(AppSettings(ctx).turnBeepEnabled)
    }

    @Test fun everyRequestCodeIsDistinct() {
        val codes = NotificationRequestCodes::class.java.declaredFields
            .filter { java.lang.reflect.Modifier.isStatic(it.modifiers) && it.type == Int::class.javaPrimitiveType && !it.name.startsWith("$") }
            .onEach { it.isAccessible = true }.map { it.getInt(null) }
        assertTrue(codes.size >= 10)
        assertEquals("two buttons with one code would replace each other's intent", codes.size, codes.toSet().size)
    }
}
