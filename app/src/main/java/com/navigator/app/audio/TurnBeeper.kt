package com.navigator.app.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.navigator.app.ble.BccuProtocol
import com.navigator.app.logging.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

/**
 * Stereo turn-approach beeper. One continuous "metronome" runs while a turn is
 * within [MAX_DISTANCE_M]: the gap between beeps maps linearly to the live
 * distance-to-turn, so the rider hears long, lazy beeps 400 m out that tighten
 * smoothly into a rapid pulse at the corner (".   .   .  . . ..."). Panned to
 * the turn's side. This replaced an earlier burst-per-distance-band design
 * whose restarts sounded like random bursts (R18 field test).
 *
 * Driven from AppNotificationListener's nav-guidance path, the one place both
 * the distance ("110 m") and the guessed direction are in scope together.
 */
object TurnBeeper {

    private const val SAMPLE_RATE = 44_100
    /** Mellow mid tone. The original 1400 Hz read as piercing on the R20 ride. */
    private const val TONE_HZ = 750.0
    private const val BEEP_MS = 110
    /** Only beep once the turn is within this many metres. */
    private const val MAX_DISTANCE_M = 400
    /** Beep gap at 0 m (rapid pulse) and at MAX_DISTANCE_M (lazy tick). */
    private const val MIN_GAP_MS = 160L
    private const val MAX_GAP_MS = 1600L
    /** Below this GPS speed the rider is waiting (signal, kill switch on) - not approaching the turn. */
    private const val STATIONARY_KMH = 4f
    /** Amplitude multiplier while stationary / engine off: still audible, no longer insistent. */
    private const val DUCK_FACTOR = 0.12

    private enum class Side { LEFT, RIGHT }

    /** Stop beeping if no guidance update arrives for this long (nav app killed mid-approach). */
    private const val STALE_GUIDANCE_MS = 20_000L

    /**
     * Swap which PCM channel gets the tone. Nominal Android interleaving is
     * [left, right], but the R18 field test heard the sides flipped on the
     * rider's headset - a toggle (Settings → Riding) beats baking either
     * assumption in, since a different headset may behave nominally.
     */
    @Volatile var swapChannels: Boolean = true

    /** Loudness 5..100 (%), pushed from settings on every guidance update. */
    @Volatile var volumePercent: Int = com.navigator.app.settings.AppSettings.DEFAULT_BEEP_VOLUME

    /**
     * Live input for the stationary duck, pushed by whoever owns it (SpeedMonitor
     * fixes). null = unknown - unknown never ducks, so a phone without GPS keeps
     * full beeps.
     */
    @Volatile var gpsSpeedKmh: Float? = null

    /** A beep is ducked when the bike is demonstrably waiting, not riding toward the turn. */
    private fun duckedNow(): Boolean {
        val speed = gpsSpeedKmh
        return speed != null && speed < STATIONARY_KMH
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var patternJob: Job? = null
    @Volatile private var currentMeters: Int = Int.MAX_VALUE
    @Volatile private var currentSide: Side? = null
    @Volatile private var lastGuidanceAtMs: Long = 0L

    /**
     * Called on every nav-notification update. [distanceText] is the nav app's
     * own distance line (e.g. "110 m", "1.2 km"); [icon] the guessed maneuver.
     * Updates the live distance the running metronome reads its gap from;
     * starts/stops/re-sides the metronome as the maneuver comes and goes.
     */
    fun onGuidance(distanceText: String?, icon: BccuProtocol.TurnIcon?) {
        val side = sideOf(icon)
        val meters = parseMeters(distanceText)
        if (side == null || meters == null || meters > MAX_DISTANCE_M) {
            stopPattern()
            return
        }
        currentMeters = meters
        lastGuidanceAtMs = System.currentTimeMillis()
        if (patternJob?.isActive != true || side != currentSide) {
            currentSide = side
            startPattern(side)
        }
    }

    /** Navigation ended (debounced in the service) - stop and re-arm for the next route. */
    fun reset() = stopPattern()

    /**
     * Settings preview: left, right, left at the configured volume and channel
     * swap - never ducked, so the rider hears the true riding loudness even
     * while parked (where the stationary duck would otherwise kick in).
     */
    fun preview() {
        scope.launch {
            playBeep(Side.LEFT, ducked = false)
            delay(500)
            playBeep(Side.RIGHT, ducked = false)
            delay(500)
            playBeep(Side.LEFT, ducked = false)
        }
    }

    private fun stopPattern() {
        patternJob?.cancel()
        patternJob = null
        currentSide = null
        currentMeters = Int.MAX_VALUE
    }

    private fun startPattern(side: Side) {
        patternJob?.cancel()
        patternJob = scope.launch {
            AppLogger.log("Beep", "Turn metronome started ($side)")
            try {
                while (isActive) {
                    if (System.currentTimeMillis() - lastGuidanceAtMs > STALE_GUIDANCE_MS) {
                        AppLogger.log("Beep", "No guidance update for ${STALE_GUIDANCE_MS / 1000}s - stopping metronome")
                        break
                    }
                    playBeep(side)
                    delay(gapForMeters(currentMeters))
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e // normal stop (new maneuver / nav ended), not a failure
            } catch (e: Exception) {
                AppLogger.log("Beep", "!! beep pattern failed: $e")
            }
        }
    }

    /** Linear map: MAX_DISTANCE_M -> MAX_GAP_MS, 0 m -> MIN_GAP_MS. */
    private fun gapForMeters(meters: Int): Long {
        val m = meters.coerceIn(0, MAX_DISTANCE_M)
        return MIN_GAP_MS + ((MAX_GAP_MS - MIN_GAP_MS) * m / MAX_DISTANCE_M)
    }

    private fun sideOf(icon: BccuProtocol.TurnIcon?): Side? {
        val name = icon?.name ?: return null
        return when {
            name.contains("LEFT") -> Side.LEFT
            name.contains("RIGHT") -> Side.RIGHT
            else -> null
        }
    }

    /** "110 m" -> 110, "1.2 km" -> 1200; null when the text isn't a distance. */
    private fun parseMeters(text: String?): Int? {
        if (text.isNullOrBlank()) return null
        val m = Regex("""(\d+(?:[.,]\d+)?)\s*(km|mi|m|ft)""", RegexOption.IGNORE_CASE).find(text) ?: return null
        val value = m.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
        return when (m.groupValues[2].lowercase()) {
            "km" -> (value * 1000).toInt()
            "mi" -> (value * 1609).toInt()
            "ft" -> (value * 0.3048).toInt()
            else -> value.toInt()
        }
    }

    private fun playBeep(side: Side, ducked: Boolean = duckedNow()) {
        // Volume: user setting, cut way down while the bike is provably waiting
        // (stationary per GPS, or engine off per the vibration monitor) - the
        // R20 ride found full-rate beeping at a signal with the kill switch on
        // maddening. The metronome keeps ticking so the cue survives; it just
        // drops to a background hum until the bike moves again.
        val amplitude = (volumePercent.coerceIn(5, 100) / 100.0) *
            (if (ducked) DUCK_FACTOR else 1.0)
        val frames = SAMPLE_RATE * BEEP_MS / 1000
        val buf = ShortArray(frames * 2) // interleaved stereo
        for (i in 0 until frames) {
            // Full raised-cosine envelope: a soft swell-and-fade "boop" instead
            // of the old hard-edged burst (only the first/last 200 samples were
            // faded, which kept it sounding sharp).
            val env = 0.5 * (1.0 - kotlin.math.cos(2.0 * PI * i / (frames - 1)))
            val s = (sin(2.0 * PI * TONE_HZ * i / SAMPLE_RATE) * env * Short.MAX_VALUE * amplitude)
                .toInt().toShort()
            val leftIndex = if (swapChannels) i * 2 + 1 else i * 2
            val rightIndex = if (swapChannels) i * 2 else i * 2 + 1
            if (side == Side.LEFT) buf[leftIndex] = s else buf[rightIndex] = s
        }
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(buf.size * 2)
            .build()
        track.write(buf, 0, buf.size)
        track.play()
        // MODE_STATIC keeps playing after release is scheduled; free it once done.
        scope.launch { delay(BEEP_MS + 60L); runCatching { track.release() } }
    }
}
