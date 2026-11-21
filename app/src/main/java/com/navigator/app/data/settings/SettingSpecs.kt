package com.navigator.app.data.settings

import com.navigator.app.settings.AppSettings

enum class SettingUnit { KMH, METERS, SECONDS, COUNT, PERCENT }

/** Outcome of validating text typed into an edit-value field. */
sealed interface SpecValidation {
    data class Valid(val value: Int) : SpecValidation
    data object NotANumber : SpecValidation
    data class TooLow(val min: Int) : SpecValidation
    data class TooHigh(val max: Int) : SpecValidation
}

/**
 * Range, step and default of one numeric setting. The one place steppers, the edit-value sheet and
 * hint text read their limits from. The bounds are [AppSettings]'s own constants, so what the UI
 * accepts is exactly what the store keeps.
 */
data class IntSpec(
    val min: Int,
    val max: Int,
    val step: Int,
    val default: Int,
    val unit: SettingUnit,
) {
    init {
        require(min <= max && step > 0 && default in min..max)
    }

    fun clamp(value: Int): Int = value.coerceIn(min, max)

    /** Next multiple of [step] above [value], capped at [max]. */
    fun stepUp(value: Int): Int = clamp((Math.floorDiv(clamp(value), step) + 1) * step)

    /** Previous multiple of [step] below [value], floored at [min]. */
    fun stepDown(value: Int): Int = clamp(Math.floorDiv(clamp(value) - 1, step) * step)

    fun validate(text: String): SpecValidation {
        val t = text.trim()
        // Digits only: no sign, no non-ASCII digits. Parse as Long so "99999999999" reads as too high.
        if (t.isEmpty() || t.length > 18 || !t.all { it in '0'..'9' }) return SpecValidation.NotANumber
        val n = t.toLong()
        return when {
            n < min -> SpecValidation.TooLow(min)
            n > max -> SpecValidation.TooHigh(max)
            else -> SpecValidation.Valid(n.toInt())
        }
    }
}

object SettingSpecs {
    /** The longest name the rider can give themselves (the dash greeting); onboarding, Settings and a restore all cap at this. */
    const val MAX_USER_NAME = 30

    val overspeedLimit = IntSpec(
        AppSettings.OVERSPEED_LIMIT_MIN, AppSettings.OVERSPEED_LIMIT_MAX, 5,
        AppSettings.DEFAULT_OVERSPEED_LIMIT_KMH, SettingUnit.KMH,
    )
    val rideMinDistance = IntSpec(
        AppSettings.RIDE_MIN_DISTANCE_MIN_M, AppSettings.RIDE_MIN_DISTANCE_MAX_M, 50,
        AppSettings.DEFAULT_RIDE_MIN_DISTANCE_M, SettingUnit.METERS,
    )
    val rideMinDuration = IntSpec(
        AppSettings.RIDE_MIN_DURATION_MIN_S, AppSettings.RIDE_MIN_DURATION_MAX_S, 30,
        AppSettings.DEFAULT_RIDE_MIN_DURATION_S, SettingUnit.SECONDS,
    )
    val rideHistoryLimit = IntSpec(
        AppSettings.RIDE_HISTORY_LIMIT_MIN, AppSettings.RIDE_HISTORY_LIMIT_MAX, 10,
        AppSettings.DEFAULT_RIDE_HISTORY_LIMIT, SettingUnit.COUNT,
    )
    val speedBandBase = IntSpec(
        AppSettings.SPEED_BAND_BASE_MIN, AppSettings.SPEED_BAND_BASE_MAX, 5,
        AppSettings.DEFAULT_SPEED_BAND_BASE_KMH, SettingUnit.KMH,
    )
    val speedBandStep = IntSpec(
        AppSettings.SPEED_BAND_STEP_MIN, AppSettings.SPEED_BAND_STEP_MAX, 5,
        AppSettings.DEFAULT_SPEED_BAND_STEP_KMH, SettingUnit.KMH,
    )
    val speedBandCount = IntSpec(
        AppSettings.SPEED_BAND_COUNT_MIN, AppSettings.SPEED_BAND_COUNT_MAX, 1,
        AppSettings.DEFAULT_SPEED_BAND_COUNT, SettingUnit.COUNT,
    )

    /** Beep volume is picked from a short list rather than stepped. */
    val beepVolumeSteps: List<Int> = listOf(20, 35, 60, 100)

    /** The listed step closest to a stored volume (older versions allowed any 5..100). */
    fun nearestBeepVolumeStep(percent: Int): Int =
        beepVolumeSteps.minBy { kotlin.math.abs(it - percent) }
}
