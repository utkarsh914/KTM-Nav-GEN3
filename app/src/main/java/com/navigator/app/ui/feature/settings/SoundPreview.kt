package com.navigator.app.ui.feature.settings

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import com.navigator.app.audio.TurnBeeper

/** The "hear it before you ride" previews: the real beep at the chosen volume and side order, and the real alert tone. */
object SoundPreview {
    fun beeps(volumePercent: Int, swap: Boolean) {
        TurnBeeper.volumePercent = volumePercent
        TurnBeeper.swapChannels = swap
        TurnBeeper.preview()
    }

    /** Matches the live alert: alarm stream, full volume. */
    fun speedAlert() {
        runCatching {
            val tone = ToneGenerator(AudioManager.STREAM_ALARM, 100)
            tone.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, TONE_MS)
            Handler(Looper.getMainLooper()).postDelayed({ runCatching { tone.release() } }, TONE_MS + RELEASE_SLACK_MS)
        }
    }

    private const val TONE_MS = 400
    private const val RELEASE_SLACK_MS = 400L
}
