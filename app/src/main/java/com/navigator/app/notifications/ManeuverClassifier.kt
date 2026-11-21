package com.navigator.app.notifications

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import com.navigator.app.R
import com.navigator.app.ble.BccuProtocol
import com.navigator.app.logging.AppLogger
import com.navigator.app.nav.ktm.KtmManeuverMapping
import com.navigator.app.nav.model.DrivingSide
import java.nio.channels.FileChannel
import java.util.concurrent.ConcurrentHashMap
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * On-device turn-icon recogniser, ported from the approach used by the KTM
 * Gen-3 companion app (com.bikeconnect.app): instead of a fragile pixel
 * heuristic, a small TensorFlow Lite CNN looks at the nav app's maneuver-icon
 * bitmap and outputs the dash turn-icon code directly - so Google Maps' icons
 * (including every roundabout exit, which the heuristic could never tell apart)
 * are classified robustly.
 *
 * Pipeline (identical to the reference app so the bundled model behaves the
 * same): composite the icon onto a black background, scale to 96x96, feed RGB
 * floats [0,1], run the model, take the arg-max class; accept it only if its
 * confidence is >= [CONFIDENCE]. The 28 output classes are the dash turn-icon
 * binary codes (plus three named fall-backs), so a numeric label maps straight
 * to [BccuProtocol.TurnIcon.fromBinary].
 */
object ManeuverClassifier {

    private const val INPUT = 96
    private const val CONFIDENCE = 0.4f

    // Output class labels in model order. Loaded at runtime from
    // res/raw/maneuver_labels.txt so a retrained model + labels file drop in
    // with no code change; falls back to this embedded list (the order of the
    // currently-bundled model). A numeric entry is a dash TurnIcon binary code;
    // named entries are Google Maps icon-resource fall-backs.
    private val FALLBACK_LABELS = arrayOf(
        "10", "11", "12", "13", "2", "21", "27", "29", "3", "31", "33", "35", "37", "39",
        "4", "43", "45", "47", "49", "5", "51", "53", "55", "6", "7", "8",
        "merge", "roundabout_generic",
    )
    @Volatile private var labels: Array<String>? = null
    private fun labels(context: Context): Array<String> {
        labels?.let { return it }
        val loaded = try {
            val id = context.resources.getIdentifier("maneuver_labels", "raw", context.packageName)
            if (id != 0) {
                val lines = context.resources.openRawResource(id).bufferedReader().useLines { seq ->
                    seq.map { it.trim() }.filter { it.isNotEmpty() }.toList()
                }
                // '#' lines are headers, not classes.
                lines.filterNot { it.startsWith("#") }.toTypedArray().takeIf { it.isNotEmpty() }
            } else null
        } catch (e: Throwable) {
            AppLogger.log("Maneuver", "!! labels load failed, using embedded: $e"); null
        }
        return (loaded ?: FALLBACK_LABELS).also { labels = it }
    }

    // Named fall-backs whose left/right (or RH/LH) sense depends on the rider's
    // driving side rather than being fixed - resolved in [labelToIcon] via the
    // shared [KtmManeuverMapping] driving-side rule so it agrees with every other
    // path. The `_lhs`/no-suffix roundabout labels are the *model's* own two
    // classes (a generic roundabout drawn for LHT vs RHT), independent of the
    // rider setting, so those stay fixed.
    private val NAMED: Map<String, BccuProtocol.TurnIcon> = mapOf(
        "big_trans_directions_roundabout" to BccuProtocol.TurnIcon.RAB_SECT_8_RH,
        "big_trans_directions_roundabout_lhs" to BccuProtocol.TurnIcon.RAB_SECT_8_LH,
        // v2 models: a roundabout icon with no exit direction -> no arrow.
        "roundabout_generic" to BccuProtocol.TurnIcon.UNDEFINED,
    )

    @Volatile private var interpreter: Interpreter? = null
    @Volatile private var unavailable = false
    // Cache by bitmap-pixel hash: the same maneuver bitmap recurs many times per
    // ride, so we only run the model once per distinct icon.
    private val cache = ConcurrentHashMap<Int, BccuProtocol.TurnIcon>()
    // "No confident match" is cached too (same bitmap, same answer): the rejected icon
    // used to be re-run through the model - and re-logged - on every ~1 Hz update.
    private val noMatch = ConcurrentHashMap.newKeySet<Int>()

    private fun ensureModel(context: Context): Interpreter? {
        interpreter?.let { return it }
        if (unavailable) return null
        synchronized(this) {
            interpreter?.let { return it }
            return try {
                Interpreter(loadModel(context)).also { interpreter = it }
            } catch (e: Throwable) {
                AppLogger.log("Maneuver", "!! Failed to load TFLite model: $e")
                unavailable = true
                null
            }
        }
    }

    /**
     * Memory-map the model from the APK (stored uncompressed, see noCompress in build.gradle);
     * falls back to copying it into a direct buffer if the resource can't be mapped.
     */
    private fun loadModel(context: Context): ByteBuffer {
        runCatching {
            // The fd is owned by the descriptor: close it exactly once (createInputStream's
            // channel shares it, and the mapping stays valid after it is closed).
            context.resources.openRawResourceFd(R.raw.maneuver_model).use { afd ->
                return afd.createInputStream().channel.map(
                    FileChannel.MapMode.READ_ONLY, afd.startOffset, afd.declaredLength,
                )
            }
        }.onFailure { AppLogger.log("Maneuver", "model mmap unavailable (${it.javaClass.simpleName}); copying") }
        val bytes = context.resources.openRawResource(R.raw.maneuver_model).use { it.readBytes() }
        val buf = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder())
        buf.put(bytes); buf.rewind()
        return buf
    }

    /** Classify a nav maneuver bitmap into a dash TurnIcon, or null if the model isn't confident. */
    fun classify(
        context: Context,
        bitmap: Bitmap,
        drivingSide: DrivingSide = DrivingSide.UNKNOWN,
    ): BccuProtocol.TurnIcon? {
        // Cache key includes driving side: the same "merge" bitmap resolves to a
        // different lane per driving side, so they can't share a cache entry.
        val hash = bitmapPixelHash(bitmap) * 31 + drivingSide.ordinal
        cache[hash]?.let { return it }
        if (hash in noMatch) return null
        val model = ensureModel(context) ?: return null
        val lbls = labels(context)
        var inferenceFailed = false
        val result = try {
            val input = preprocess(bitmap)
            val output = Array(1) { FloatArray(lbls.size) }
            model.run(input, output)
            val scores = output[0]
            var best = 0
            for (i in scores.indices) if (scores[i] > scores[best]) best = i
            if (scores[best] < CONFIDENCE) {
                AppLogger.log("Maneuver", "No confident match (best=${lbls[best]} @ ${"%.2f".format(scores[best])})")
                null
            } else {
                labelToIcon(lbls[best], drivingSide)?.also {
                    AppLogger.log("Maneuver", "Model -> ${lbls[best]} = ${it.name} (${"%.2f".format(scores[best])})")
                }
            }
        } catch (e: Throwable) {
            AppLogger.log("Maneuver", "!! inference failed: $e")
            inferenceFailed = true
            null
        }
        if (result != null) cache[hash] = result else if (!inferenceFailed) noMatch.add(hash)
        return result
    }

    internal fun labelToIcon(label: String, drivingSide: DrivingSide): BccuProtocol.TurnIcon? {
        val n = label.toIntOrNull()
        if (n != null) {
            // Maps draws merge left/right with the same bitmap, so a model's 14/15
            // output carries no real direction: treat it as the generic "merge".
            if (n == BccuProtocol.TurnIcon.ENTER_HIGHWAY_LEFT_LANE.binary ||
                n == BccuProtocol.TurnIcon.ENTER_HIGHWAY_RIGHT_LANE.binary
            ) {
                return KtmManeuverMapping.enterHighwayIcon(
                    rampOnLeft = KtmManeuverMapping.rampOnLeftFor(drivingSide),
                )
            }
            return BccuProtocol.TurnIcon.fromBinary(n)
        }
        // "merge" has no left/right in the label - the ramp is on the driving
        // side (left-hand traffic joins from the left), consistent with the text path.
        if (label == "merge") {
            return KtmManeuverMapping.enterHighwayIcon(
                rampOnLeft = KtmManeuverMapping.rampOnLeftFor(drivingSide),
            )
        }
        return NAMED[label]
    }

    /** Composite onto black, scale to 96x96, emit RGB floats [0,1] - matches the reference app byte-for-byte. */
    private fun preprocess(bitmap: Bitmap): ByteBuffer {
        val onBlack = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(onBlack)
        canvas.drawColor(Color.BLACK)
        canvas.drawBitmap(bitmap, 0f, 0f, null)
        val scaled = Bitmap.createScaledBitmap(onBlack, INPUT, INPUT, true)
        val buf = ByteBuffer.allocateDirect(INPUT * INPUT * 3 * 4).order(ByteOrder.nativeOrder())
        for (y in 0 until INPUT) {
            for (x in 0 until INPUT) {
                val p = scaled.getPixel(x, y)
                buf.putFloat(((p shr 16) and 0xFF) / 255f)
                buf.putFloat(((p shr 8) and 0xFF) / 255f)
                buf.putFloat((p and 0xFF) / 255f)
            }
        }
        buf.rewind()
        if (scaled != onBlack) scaled.recycle()
        onBlack.recycle()
        return buf
    }

}
