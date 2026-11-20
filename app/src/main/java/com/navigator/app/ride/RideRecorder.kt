package com.navigator.app.ride

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Handler
import android.os.HandlerThread
import androidx.core.content.ContextCompat
import com.navigator.app.logging.AppLogger
import com.navigator.app.settings.AppSettings
import java.io.Writer

/**
 * Records the live GPS track of a ride to a per-ride NDJSON file, one flushed
 * line per fix (crash-safe — see [RideStore]). Owned by the app's foreground
 * service so it keeps recording with the screen off.
 *
 * Runs its own [HandlerThread] and delivers location callbacks onto it, so both
 * the fix handling and the disk append happen off the main thread. On [stop] the
 * ride is finalised into the index, or discarded if it never cleared the keep
 * cutoffs (distance AND duration).
 */
class RideRecorder(private val context: Context) {

    private val store = RideStore(context)
    private val settings = AppSettings(context)
    private val acc = RideMetrics.Accumulator()

    private var thread: HandlerThread? = null
    private var writer: Writer? = null
    private var activeId: String? = null
    private var started = false
    @Volatile private var paused = false
    // Set by resume(): the next accepted fix is flagged as the first one after a pause.
    @Volatile private var gapNext = false
    private var source = RideSource.NAV

    // Destination captured at start(), read at finalize.
    private var destLabel: String? = null
    private var destLat: Double? = null
    private var destLng: Double? = null

    val isRecording: Boolean get() = started

    private companion object {
        /** Fixes less accurate than this (metres) are dropped (generous: a phone in a tank bag is rarely sharp). */
        const val MAX_ACCURACY_M = 40f
    }

    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) = onFix(location)

        @Deprecated("Deprecated in API 29 but still called on older devices")
        override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    /** Begin recording a new ride toward the given (optional) destination. */
    /** @return true when recording is running (also if it already was). */
    fun start(destLabel: String?, destLat: Double?, destLng: Double?, source: String = RideSource.NAV): Boolean {
        if (started) return true
        this.source = source
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            AppLogger.log("Ride", "No location permission - recording skipped")
            return false
        }
        this.destLabel = destLabel
        this.destLat = destLat
        this.destLng = destLng
        val id = System.currentTimeMillis().toString()
        val ht = HandlerThread("ride-recorder").also { it.start() }
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        try {
            writer = store.openTrack(id, destLabel, destLat, destLng, source)
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, listener, ht.looper)
            thread = ht
            activeId = id
            started = true
            RideSession.started(source == RideSource.MANUAL, System.currentTimeMillis())
            AppLogger.log("Ride", "Recording started ($id) -> ${AppLogger.sensitive(destLabel)}")
            return true
        } catch (e: Exception) {
            AppLogger.log("Ride", "!! start failed: $e")
            runCatching { writer?.close() }
            writer = null
            store.discardTrack(id)
            ht.quitSafely()
            return false
        }
    }

    private fun onFix(location: Location) {
        val w = writer ?: return
        // A fix that was already queued when updates were removed must not leak into the pause.
        if (paused) return
        // Poor fixes (urban canyon, cold start) add phantom distance and speed spikes.
        if (location.hasAccuracy() && location.accuracy > MAX_ACCURACY_M) return
        // Mock providers are accepted in debug builds only, so mock-location testing still works.
        if (location.isMock && !com.navigator.app.BuildConfig.DEBUG) return
        val point = RidePoint(
            lat = location.latitude,
            lng = location.longitude,
            timeMs = if (location.time > 0L) location.time else System.currentTimeMillis(),
            speedKmh = if (location.hasSpeed()) location.speed * 3.6f else null,
            gapBefore = gapNext,
            altM = if (location.hasAltitude()) location.altitude.toFloat() else null,
        )
        if (!acc.add(point)) return // rejected as a glitch (time went backwards / impossible jump)
        gapNext = false
        runCatching { store.appendPoint(w, point) }
        RideSession.distance(acc.stats().distanceMeters)
    }

    /** Stop collecting fixes without ending the ride (the track keeps its file and state). */
    fun pause() {
        if (!started || paused) return
        paused = true
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        runCatching { lm.removeUpdates(listener) }
        thread?.let { ht -> Handler(ht.looper).post { runCatching { writer?.flush() } } }
        RideSession.paused(System.currentTimeMillis())
        AppLogger.log("Ride", "Recording paused")
    }

    fun resume() {
        if (!started || !paused) return
        val ht = thread ?: return
        // Permission may have been revoked while paused.
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            AppLogger.log("Ride", "Can't resume: location permission is off")
            return
        }
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        try {
            gapNext = true
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, listener, ht.looper)
            paused = false
            RideSession.resumed(System.currentTimeMillis())
            AppLogger.log("Ride", "Recording resumed")
        } catch (e: Exception) {
            gapNext = false
            AppLogger.log("Ride", "!! resume failed: $e")
        }
    }

    val isPaused: Boolean get() = paused

    /** Stop recording; finalise or discard on the recorder thread, then tear down. */
    fun stop() {
        if (!started) return
        started = false
        paused = false
        RideSession.ended()
        val id = activeId ?: return
        val ht = thread
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        runCatching { lm.removeUpdates(listener) }

        val finalize = Runnable { try {
            runCatching { writer?.flush(); writer?.close() }
            writer = null
            val stats = acc.stats()
            val minDist = settings.rideMinDistanceMeters
            val minDur = settings.rideMinDurationSeconds
            if (RideMetrics.meetsCutoff(stats.distanceMeters, stats.durationSeconds, minDist, minDur)) {
                val ride = RecordedRide(
                    id = id,
                    startMs = stats.startMs,
                    endMs = stats.endMs,
                    distanceMeters = stats.distanceMeters,
                    durationSeconds = stats.durationSeconds,
                    destinationLabel = destLabel,
                    destLat = destLat,
                    destLng = destLng,
                    avgSpeedKmh = stats.avgSpeedKmh,
                    movingSeconds = stats.movingSeconds,
                    avgOverallSpeedKmh = stats.avgOverallSpeedKmh,
                    elevGainM = stats.elevGainM,
                    elevLossM = stats.elevLossM,
                    maxSpeedKmh = stats.maxSpeedKmh,
                    pointCount = stats.pointCount,
                    source = source,
                )
                store.finalizeRide(ride)
                AppLogger.log("Ride", "Saved ride $id: ${stats.distanceMeters}m / ${stats.durationSeconds}s")
                // Enforce the history limit now that a new ride was added.
                runCatching { store.pruneToLimit(settings.rideHistoryLimit) }
                // Let the trip-finished screen surface this ride's route + stats.
                RideEvents.publish(RideFinish.Saved(ride))
            } else {
                store.discardTrack(id)
                AppLogger.log("Ride", "Discarded short ride $id (${stats.distanceMeters}m / ${stats.durationSeconds}s)")
                RideEvents.publish(RideFinish.Discarded)
            }
        } finally {
            store.markTrackClosed(id)
            ht?.quitSafely()
        } }
        // Run finalisation on the recorder thread so it serialises after any
        // in-flight fix; fall back to inline if the thread is already gone.
        if (ht != null) Handler(ht.looper).post(finalize) else finalize.run()
        thread = null
        activeId = null
    }
}
