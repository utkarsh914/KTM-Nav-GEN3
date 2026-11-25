package com.navigator.app.data.settings

import android.content.Context
import com.navigator.app.nav.model.TravelMode
import com.navigator.app.ride.AvgSpeedMode
import com.navigator.app.settings.AppSettings
import com.navigator.app.settings.DrivingSidePreference
import com.navigator.app.ui.theme.NavThemeMode
import com.navigator.app.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SettingsRepositoryTest {
    private lateinit var ctx: Context
    private lateinit var repo: SettingsRepository

    @Before fun setUp() {
        ctx = RuntimeEnvironment.getApplication()
        ctx.getSharedPreferences("opendash_pairing", Context.MODE_PRIVATE).edit().clear().commit()
        repo = SettingsRepository(AppSettings(ctx))
    }

    @Test fun defaultsMatchTheStore() {
        val s = repo.snapshot.value
        assertEquals(NavEngine.IN_APP, s.engine)
        assertNull(s.accentColorId)
        assertEquals(ThemeMode.SYSTEM, s.themeMode)
        assertTrue(s.turnBeepEnabled)
        assertEquals(AppSettings.DEFAULT_BEEP_VOLUME, s.beepVolumePercent)
        assertEquals(AppSettings.DEFAULT_OVERSPEED_LIMIT_KMH, s.overspeedLimitKmh)
        assertFalse(s.isPaired)
        assertFalse(s.onboardingComplete)
    }

    @Test fun engineInUseIsTheStoredChoiceOnlyWhenTheInAppEngineCanRun() {
        assertEquals(NavEngine.IN_APP, repo.engineInUse())
        assertEquals(NavEngine.MIRROR, SettingsRepository(AppSettings(ctx)) { false }.engineInUse())
        assertEquals(NavEngine.MIRROR, NavEngine.inUse(NavEngine.MIRROR, available = true))
        assertEquals(NavEngine.MIRROR, NavEngine.inUse(NavEngine.IN_APP, available = false))
    }

    @Test fun updateEmitsNewSnapshot() {
        repo.update { themeMode = ThemeMode.LIGHT; googleNavEnabled = false; userName = "Asha" }
        val s = repo.snapshot.value
        assertEquals(ThemeMode.LIGHT, s.themeMode)
        assertEquals(NavEngine.MIRROR, s.engine)
        assertEquals("Asha", s.userName)
    }

    @Test fun updatesClampThroughTheStore() {
        repo.update { overspeedLimitKmh = 999; rideHistoryLimit = 1 }
        assertEquals(AppSettings.OVERSPEED_LIMIT_MAX, repo.snapshot.value.overspeedLimitKmh)
        assertEquals(AppSettings.RIDE_HISTORY_LIMIT_MIN, repo.snapshot.value.rideHistoryLimit)
    }

    @Test fun writesFromOtherAppSettingsInstancesAreObserved() {
        AppSettings(ctx).bondedDeviceAddress = "AA:BB:CC:DD:EE:FF"
        AppSettings(ctx).marqueeEnabled = false
        val s = repo.snapshot.value
        assertTrue(s.isPaired)
        assertFalse(s.marqueeEnabled)
    }

    @Test fun accentCanBeSetAndClearedBackToKtmOrange() {
        repo.update { accentColorId = "teal" }
        assertEquals("teal", repo.snapshot.value.accentColorId)
        repo.update { accentColorId = null }
        assertNull(repo.snapshot.value.accentColorId)
    }

    @Test fun refreshPicksUpWholesaleChanges() {
        ctx.getSharedPreferences("opendash_pairing", Context.MODE_PRIVATE).edit()
            .putBoolean("onboarding_complete", true).commit()
        repo.refresh()
        assertTrue(repo.snapshot.value.onboardingComplete)
    }

    @Test fun snapshotMapsEveryGroupOfSettings() {
        repo.update {
            navThemeMode = NavThemeMode.APP
            avgSpeedMode = AvgSpeedMode.OVERALL
            drivingSidePreference = DrivingSidePreference.LEFT
            vehicleMode = TravelMode.DRIVING
            notificationSourceApps = setOf("a.b")
            navAppOverride = "com.waze"
            preferredCallAudioDeviceAddress = "11:22"
            speedBandCount = 7
        }
        val s = repo.snapshot.value
        assertEquals(NavThemeMode.APP, s.ridingScreens)
        assertEquals(AvgSpeedMode.OVERALL, s.avgSpeedMode)
        assertEquals(DrivingSidePreference.LEFT, s.drivingSide)
        assertEquals(TravelMode.DRIVING, s.vehicleMode)
        assertEquals(setOf("a.b"), s.notificationSourceApps)
        assertEquals("com.waze", s.navAppOverride)
        assertEquals("11:22", s.callAudioDeviceAddress)
        assertEquals(7, s.speedBandCount)
    }
}
