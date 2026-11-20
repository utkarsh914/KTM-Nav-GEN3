package com.navigator.app.ride

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.core.content.ContextCompat

/**
 * The "Record a ride" shortcut (SY6): a no-UI trampoline that starts the recording service and finishes, so recording
 * begins from a pocket without the app opening. Without location permission it opens the app instead, where the
 * rider can allow it.
 */
class RecordRideShortcutActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val alreadyRecording = RideSession.state.value.active
        when (RecordStartPolicy.result(granted) { alreadyRecording || RideRecordingService.startManual(this) }) {
            RecordStartResult.Started -> {}
            RecordStartResult.NeedsLocation, RecordStartResult.Failed ->
                startActivity(Intent(this, com.navigator.app.ui.MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        finish()
    }
}
