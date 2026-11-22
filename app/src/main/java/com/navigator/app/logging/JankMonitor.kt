package com.navigator.app.logging

import android.app.Activity
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalView
import androidx.metrics.performance.JankStats
import androidx.metrics.performance.PerformanceMetricsState
import com.navigator.app.BuildConfig

/**
 * Logs frames that missed their deadline, with the route and stage they happened on, to [AppLogger]
 * ("Jank") so they show up in Settings > Logs. On in debug and benchmark builds only
 * ([BuildConfig.JANK_LOG]); release pays nothing.
 */
object JankMonitor {

    private const val TAG = "Jank"
    private const val MIN_GAP_MS = 100L

    private var stats: JankStats? = null
    private var lastLogMs = 0L

    fun attach(activity: Activity) {
        if (!BuildConfig.JANK_LOG) return
        stats?.isTrackingEnabled = false
        stats = JankStats.createAndTrack(activity.window) { frame ->
            if (!frame.isJank) return@createAndTrack
            val now = System.currentTimeMillis()
            if (now - lastLogMs < MIN_GAP_MS) return@createAndTrack
            lastLogMs = now
            val where = frame.states.joinToString(" ") { "${it.key}=${it.value}" }
            AppLogger.log(TAG, "${frame.frameDurationUiNanos / 1_000_000} ms ui, $where")
        }
    }

    fun detach() {
        stats?.isTrackingEnabled = false
        stats = null
    }

    /** Names what the screen is showing (route, stage) so a janky frame says where it happened. */
    @Composable
    fun Tag(key: String, value: String) {
        if (!BuildConfig.JANK_LOG) return
        val view = LocalView.current
        LaunchedEffect(view, key, value) { put(view, key, value) }
    }

    private fun put(view: View, key: String, value: String) {
        PerformanceMetricsState.getHolderForHierarchy(view).state?.putState(key, value)
    }
}
