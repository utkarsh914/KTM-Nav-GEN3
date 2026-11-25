package com.navigator.app.ride

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import com.navigator.app.R
import com.navigator.app.app.appContainer

/**
 * The "Record a ride" shortcut (SY6): a no-UI trampoline that starts the recording service and finishes, so recording
 * begins from a pocket without the app opening. Without location permission it opens the app instead, where the
 * rider can allow it.
 */
class RecordRideShortcutActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = applicationContext
        // Starting a recording is the rider's own act: it ends a standing quit.
        com.navigator.app.settings.AppSettings(app).quitUntilOpen = false
        val setUp = appContainer.settings.snapshot.value.onboardingComplete
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!setUp) {
            // Nothing is set up yet: the app's own setup comes first.
            say(app, R.string.shortcut_record_needs_setup)
            openApp()
        } else if (RideSession.state.value.active) {
            say(app, R.string.shortcut_record_already)
        } else when (RecordStartPolicy.result(granted) { RideRecordingService.startManual(this) }) {
            RecordStartResult.Started -> {}
            RecordStartResult.NeedsLocation, RecordStartResult.Failed -> {
                // The app opens next; say why, so it doesn't look like the shortcut did nothing.
                say(app, if (granted) R.string.shortcut_record_failed else R.string.shortcut_record_needs_location)
                openApp()
            }
        }
        finish()
    }

    // The application context: this activity is finishing, and the toast outlives it.
    private fun say(app: Context, @StringRes text: Int) = Toast.makeText(app, text, Toast.LENGTH_LONG).show()

    private fun openApp() = startActivity(com.navigator.app.app.AppIntents.internal(this).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
