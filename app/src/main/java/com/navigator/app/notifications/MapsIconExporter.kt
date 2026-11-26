package com.navigator.app.notifications

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Drawable
import androidx.core.graphics.drawable.DrawableCompat
import com.navigator.app.logging.AppLogger
import java.io.File
import java.io.FileOutputStream

/**
 * DEBUG-ONLY dataset builder for training an independent maneuver-icon model.
 *
 * Google Maps ships its turn-by-turn icons as *named* drawables
 * (maneuver_turn_sharp_left, maneuver_roundabout_enter_and_exit_cw_slight_right,
 * ...). This renders each one (from the installed Maps package's own resources)
 * to a PNG named by its label, giving a perfectly-labeled training set with zero
 * hand-labeling. Pull files/maneuver_dataset/ off the device and train on it.
 *
 * Triggered by: adb shell am broadcast -a com.navigator.app.EXPORT_MANEUVERS
 * Never runs in release builds (guarded by BuildConfig.DEBUG at the call site).
 */
object MapsIconExporter {

    private const val MAPS_PKG = NavNotificationFilter.GOOGLE_MAPS
    private const val RENDER = 128 // export at 128px; training script scales to 96

    val MANEUVER_NAMES: List<String> = listOf(
        "maneuver_depart",
        "maneuver_destination",
        "maneuver_destination_left",
        "maneuver_destination_right",
        "maneuver_destination_straight",
        "maneuver_fork_left",
        "maneuver_fork_right",
        "maneuver_keep_left",
        "maneuver_keep_right",
        "maneuver_merge",
        "maneuver_merge_left",
        "maneuver_merge_right",
        "maneuver_name_change",
        "maneuver_off_ramp_keep_left",
        "maneuver_off_ramp_keep_right",
        "maneuver_off_ramp_normal_left",
        "maneuver_off_ramp_normal_right",
        "maneuver_off_ramp_sharp_left",
        "maneuver_off_ramp_sharp_right",
        "maneuver_off_ramp_slight_left",
        "maneuver_off_ramp_slight_right",
        "maneuver_off_ramp_u_turn_left",
        "maneuver_off_ramp_u_turn_right",
        "maneuver_on_ramp_keep_left",
        "maneuver_on_ramp_keep_right",
        "maneuver_on_ramp_normal_left",
        "maneuver_on_ramp_normal_right",
        "maneuver_on_ramp_sharp_left",
        "maneuver_on_ramp_sharp_right",
        "maneuver_on_ramp_slight_left",
        "maneuver_on_ramp_slight_right",
        "maneuver_on_ramp_u_turn_left",
        "maneuver_on_ramp_u_turn_right",
        "maneuver_roundabout_enter_and_exit_ccw",
        "maneuver_roundabout_enter_and_exit_ccw_normal_left",
        "maneuver_roundabout_enter_and_exit_ccw_normal_right",
        "maneuver_roundabout_enter_and_exit_ccw_sharp_left",
        "maneuver_roundabout_enter_and_exit_ccw_sharp_right",
        "maneuver_roundabout_enter_and_exit_ccw_slight_left",
        "maneuver_roundabout_enter_and_exit_ccw_slight_right",
        "maneuver_roundabout_enter_and_exit_ccw_straight",
        "maneuver_roundabout_enter_and_exit_ccw_u_turn",
        "maneuver_roundabout_enter_and_exit_cw",
        "maneuver_roundabout_enter_and_exit_cw_normal_left",
        "maneuver_roundabout_enter_and_exit_cw_normal_right",
        "maneuver_roundabout_enter_and_exit_cw_sharp_left",
        "maneuver_roundabout_enter_and_exit_cw_sharp_right",
        "maneuver_roundabout_enter_and_exit_cw_slight_left",
        "maneuver_roundabout_enter_and_exit_cw_slight_right",
        "maneuver_roundabout_enter_and_exit_cw_straight",
        "maneuver_roundabout_enter_and_exit_cw_u_turn",
        "maneuver_roundabout_enter_ccw",
        "maneuver_roundabout_enter_cw",
        "maneuver_roundabout_exit_ccw",
        "maneuver_roundabout_exit_cw",
        "maneuver_straight",
        "maneuver_turn_normal_left",
        "maneuver_turn_normal_right",
        "maneuver_turn_sharp_left",
        "maneuver_turn_sharp_right",
        "maneuver_turn_slight_left",
        "maneuver_turn_slight_right",
        "maneuver_u_turn_left",
        "maneuver_u_turn_right",
    )

    fun exportAll(context: Context): String {
        val outDir = File(context.filesDir, "maneuver_dataset").apply { mkdirs() }
        val mapsRes = try {
            context.createPackageContext(MAPS_PKG, 0).resources
        } catch (e: Exception) {
            AppLogger.log("Dataset", "!! Google Maps not installed / no resources: $e")
            return "Maps not available"
        }
        var ok = 0; var miss = 0
        for (name in MANEUVER_NAMES) {
            try {
                val id = mapsRes.getIdentifier(name, "drawable", MAPS_PKG)
                if (id == 0) { miss++; continue }
                @Suppress("DEPRECATION")
                val d: Drawable? = mapsRes.getDrawable(id, null)
                if (d == null) { miss++; continue }
                // Tint white to match how the icon actually appears in the nav
                // notification (white glyph on transparent) - that's what the
                // classifier sees at runtime, so the training data must match.
                val tinted = DrawableCompat.wrap(d.mutate())
                DrawableCompat.setTint(tinted, Color.WHITE)
                val bmp = Bitmap.createBitmap(RENDER, RENDER, Bitmap.Config.ARGB_8888)
                val c = Canvas(bmp)
                tinted.setBounds(0, 0, RENDER, RENDER)
                tinted.draw(c)
                FileOutputStream(File(outDir, "$name.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bmp.recycle()
                ok++
            } catch (e: Exception) {
                AppLogger.log("Dataset", "!! export $name failed: $e"); miss++
            }
        }
        val msg = "Exported $ok maneuver icons ($miss missing) to ${outDir.absolutePath}"
        AppLogger.log("Dataset", msg)
        return msg
    }
}
