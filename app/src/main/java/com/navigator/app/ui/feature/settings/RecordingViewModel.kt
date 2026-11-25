package com.navigator.app.ui.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.navigator.app.app.AppContainer
import com.navigator.app.data.settings.IntSpec
import com.navigator.app.data.settings.SettingSpecs
import com.navigator.app.data.settings.SettingsRepository
import com.navigator.app.data.settings.SettingsSnapshot
import com.navigator.app.logging.AppLogger
import com.navigator.app.ride.AvgSpeedMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The numbers on the Recording page, each with its own range. */
enum class RecordingField(val spec: IntSpec) {
    MIN_DISTANCE(SettingSpecs.rideMinDistance),
    MIN_DURATION(SettingSpecs.rideMinDuration),
    HISTORY(SettingSpecs.rideHistoryLimit),
    BAND_BASE(SettingSpecs.speedBandBase),
    BAND_STEP(SettingSpecs.speedBandStep),
    BAND_COUNT(SettingSpecs.speedBandCount),
}

data class RecordingUi(
    val recording: Boolean,
    val avgMode: AvgSpeedMode,
    val minDistanceM: Int,
    val minDurationS: Int,
    val history: Int,
    val bandBase: Int,
    val bandStep: Int,
    val bandCount: Int,
) {
    fun valueOf(field: RecordingField): Int = when (field) {
        RecordingField.MIN_DISTANCE -> minDistanceM
        RecordingField.MIN_DURATION -> minDurationS
        RecordingField.HISTORY -> history
        RecordingField.BAND_BASE -> bandBase
        RecordingField.BAND_STEP -> bandStep
        RecordingField.BAND_COUNT -> bandCount
    }
}

/** ST5: auto-record, which average to show, what to keep, and the track's speed colours. */
class RecordingViewModel(
    private val settings: SettingsRepository,
    /** Hides the oldest rides beyond the new limit with an Undo (new limit, previous limit); runs in the app scope. */
    private val removeOverLimit: (limit: Int, previous: Int) -> Unit = { _, _ -> },
) : ViewModel() {
    private val limitAtOpen = settings.snapshot.value.rideHistoryLimit

    private fun build(s: SettingsSnapshot) = RecordingUi(
        s.rideRecordingEnabled, s.avgSpeedMode, s.rideMinDistanceMeters, s.rideMinDurationSeconds, s.rideHistoryLimit,
        s.speedBandBaseKmh, s.speedBandStepKmh, s.speedBandCount,
    )

    val ui: StateFlow<RecordingUi> = settings.snapshot.map(::build)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), build(settings.snapshot.value))

    fun setRecording(on: Boolean) = settings.update { rideRecordingEnabled = on }
    fun setAvgMode(mode: AvgSpeedMode) = settings.update { avgSpeedMode = mode }

    fun set(field: RecordingField, value: Int) {
        val v = field.spec.clamp(value)
        settings.update {
            when (field) {
                RecordingField.MIN_DISTANCE -> rideMinDistanceMeters = v
                RecordingField.MIN_DURATION -> rideMinDurationSeconds = v
                RecordingField.HISTORY -> rideHistoryLimit = v
                RecordingField.BAND_BASE -> speedBandBaseKmh = v
                RecordingField.BAND_STEP -> speedBandStepKmh = v
                RecordingField.BAND_COUNT -> speedBandCount = v
            }
        }
    }

    /**
     * Lowering "Rides to keep" removes the extra rides (with Undo) once when the page is left: every stepper tap
     * saves at once, and removing per tap would hide rides while the rider is still stepping down.
     */
    /** Safe to call twice (page left, then cleared): once the extra rides are hidden nothing is left over the limit. */
    fun commit() {
        val limit = settings.snapshot.value.rideHistoryLimit
        if (limit < limitAtOpen) removeOverLimit(limit, limitAtOpen)
    }

    override fun onCleared() = commit()

    companion object {
        fun factory(container: AppContainer) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = RecordingViewModel(
                container.settings,
                removeOverLimit = { limit, previous ->
                    container.appScope.launch {
                        try {
                            container.rides.removeOverLimit(limit, previous)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            AppLogger.log("Ride", "!! removing rides over the limit failed: ${e.javaClass.simpleName}")
                        }
                    }
                },
            ) as T
        }
    }
}
