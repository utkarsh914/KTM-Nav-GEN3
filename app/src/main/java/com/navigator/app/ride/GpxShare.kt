package com.navigator.app.ride

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.navigator.app.R
import com.navigator.app.logging.AppLogger
import java.io.File

/** Share a ride as a GPX file through the system share sheet. */
object GpxShare {

    private const val AUTHORITY = "com.navigator.app.fileprovider"

    /**
     * Writes the GPX (blocking disk work: call off the main thread) and returns the chooser
     * intent, or null when the ride has no readable track.
     */
    fun buildIntent(context: Context, ride: RecordedRide, store: RideStore): Intent? {
        val points = store.points(ride.id)
        if (points.size < 2) return null
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        // Keep the folder from growing, but never pull a file out from under a share still in
        // flight: only exports older than an hour go.
        val cutoff = System.currentTimeMillis() - 60 * 60 * 1000L
        dir.listFiles()?.forEach { if (it.lastModified() < cutoff) runCatching { it.delete() } }
        val file = File(dir, Gpx.fileName(ride.destinationLabel, ride.startMs))
        return try {
            file.writeText(Gpx.write(ride.destinationLabel ?: context.getString(R.string.gpx_default_name), points), Charsets.UTF_8)
            val uri = FileProvider.getUriForFile(context, AUTHORITY, file)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "application/gpx+xml"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, ride.destinationLabel ?: context.getString(R.string.gpx_default_name))
                clipData = ClipData.newRawUri("", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            Intent.createChooser(send, context.getString(R.string.gpx_share_title)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (e: Exception) {
            AppLogger.log("Ride", "!! GPX export failed: ${e.javaClass.simpleName}")
            null
        }
    }
}
