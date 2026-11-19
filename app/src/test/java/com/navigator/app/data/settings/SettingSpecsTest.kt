package com.navigator.app.data.settings

import com.navigator.app.settings.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingSpecsTest {
    private val history = SettingSpecs.rideHistoryLimit

    @Test fun validateAcceptsInRangeAndTrims() {
        assertEquals(SpecValidation.Valid(50), history.validate(" 50 "))
        assertEquals(SpecValidation.Valid(10), history.validate("10"))
        assertEquals(SpecValidation.Valid(1000), history.validate("1000"))
    }

    @Test fun validateRejectsOutOfRangeAndText() {
        assertEquals(SpecValidation.TooLow(10), history.validate("9"))
        assertEquals(SpecValidation.TooHigh(1000), history.validate("1001"))
        assertEquals(SpecValidation.NotANumber, history.validate(""))
        assertEquals(SpecValidation.NotANumber, history.validate("12.5"))
        assertEquals(SpecValidation.NotANumber, history.validate("abc"))
    }

    @Test fun stepsSnapToTheGridAndStayInRange() {
        assertEquals(110, history.stepUp(100))
        assertEquals(110, history.stepUp(105))
        assertEquals(90, history.stepDown(100))
        assertEquals(100, history.stepDown(105))
        assertEquals(1000, history.stepUp(1000))
        assertEquals(10, history.stepDown(10))
        assertEquals(10, history.stepDown(15))
    }

    @Test fun durationStepFloorsAtItsMinimum() {
        val d = SettingSpecs.rideMinDuration
        assertEquals(30, d.stepUp(10))
        assertEquals(10, d.stepDown(30))
        assertEquals(3600, d.stepUp(3600))
    }

    @Test fun clampMatchesAppSettingsLimits() {
        assertEquals(AppSettings.OVERSPEED_LIMIT_MAX, SettingSpecs.overspeedLimit.clamp(500))
        assertEquals(AppSettings.OVERSPEED_LIMIT_MIN, SettingSpecs.overspeedLimit.clamp(0))
        assertEquals(AppSettings.SPEED_BAND_STEP_MIN, SettingSpecs.speedBandStep.clamp(-3))
    }

    @Test fun everyDefaultIsInsideItsRange() {
        listOf(
            SettingSpecs.overspeedLimit, SettingSpecs.rideMinDistance, SettingSpecs.rideMinDuration,
            SettingSpecs.rideHistoryLimit, SettingSpecs.speedBandBase, SettingSpecs.speedBandStep,
            SettingSpecs.speedBandCount,
        ).forEach { assertTrue(it.default in it.min..it.max) }
    }

    @Test fun beepVolumeSnapsToNearestStep() {
        assertEquals(20, SettingSpecs.nearestBeepVolumeStep(5))
        assertEquals(35, SettingSpecs.nearestBeepVolumeStep(40))
        assertEquals(60, SettingSpecs.nearestBeepVolumeStep(55))
        assertEquals(100, SettingSpecs.nearestBeepVolumeStep(100))
    }

    @Test fun validateReadsHugeNumbersAsTooHighAndRejectsSignsAndUnicode() {
        assertEquals(SpecValidation.TooHigh(1000), history.validate("99999999999"))
        assertEquals(SpecValidation.NotANumber, history.validate("+50"))
        assertEquals(SpecValidation.NotANumber, history.validate("-5"))
        assertEquals(SpecValidation.NotANumber, history.validate("٥٠"))
        assertEquals(SpecValidation.NotANumber, history.validate("9".repeat(40)))
    }

    @Test fun steppingIsSafeAtIntExtremes() {
        assertEquals(1000, history.stepUp(Int.MAX_VALUE))
        assertEquals(10, history.stepDown(Int.MIN_VALUE))
        assertEquals(20, history.stepUp(-5))
    }

    @Test fun everySpecStopsAtItsBounds() {
        listOf(
            SettingSpecs.overspeedLimit, SettingSpecs.rideMinDistance, SettingSpecs.rideMinDuration,
            SettingSpecs.rideHistoryLimit, SettingSpecs.speedBandBase, SettingSpecs.speedBandStep,
            SettingSpecs.speedBandCount,
        ).forEach {
            assertEquals(it.max, it.stepUp(it.max))
            assertEquals(it.min, it.stepDown(it.min))
        }
    }
}
