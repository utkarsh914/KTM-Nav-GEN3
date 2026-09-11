package com.navigator.app.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationManager
import android.view.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import kotlin.math.cos
import kotlin.math.sin

/**
 * A Google-Maps-style "my location" marker driven by the device compass, used on
 * the non-navigating map surfaces (browse, route preview, ride replay).
 *
 * The app's map is a Navigation-SDK [com.google.android.libraries.navigation.NavigationView]
 * whose built-in puck is (a) small and (b) only rotates with GPS course, so it
 * won't spin when you turn a stationary phone the way the standalone Google Maps
 * app does. We instead draw our own marker: a large blue dot with a white ring
 * and a directional beam whose rotation follows the phone's orientation sensor.
 */

// ---- Compass heading -------------------------------------------------------

/**
 * Live device heading in degrees (0 = north, clockwise), from the rotation-vector
 * sensor (falling back to accelerometer + magnetometer). Smoothed to avoid jitter
 * and remapped for the current display rotation. Null until the first reading (or
 * when no suitable sensor exists). Registers on entry, unregisters on dispose.
 */
@Composable
internal fun rememberDeviceHeading(active: Boolean = true): Float? {
    val context = LocalContext.current
    var heading by remember { mutableStateOf<Float?>(null) }
    DisposableEffect(active) {
        if (!active) return@DisposableEffect onDispose { }
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val rotationSensor = sm?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val accelSensor = sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val magSensor = sm?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

        val rotationMatrix = FloatArray(9)
        val remapped = FloatArray(9)
        val orientation = FloatArray(3)
        // Fallback path buffers.
        var gravity: FloatArray? = null
        var geomagnetic: FloatArray? = null

        // Low-pass smoothing over the shortest-arc so 359°→1° doesn't spin the
        // beam the long way round.
        var smoothed = Float.NaN

        fun publish(rawDeg: Float) {
            val norm = ((rawDeg % 360f) + 360f) % 360f
            smoothed = if (smoothed.isNaN()) {
                norm
            } else {
                var delta = norm - smoothed
                if (delta > 180f) delta -= 360f
                if (delta < -180f) delta += 360f
                (smoothed + delta * SMOOTHING) .let { ((it % 360f) + 360f) % 360f }
            }
            heading = smoothed
        }

        fun displayRotationDegrees(): Int {
            @Suppress("DEPRECATION")
            val rot = (context.getSystemService(Context.WINDOW_SERVICE) as? android.view.WindowManager)
                ?.defaultDisplay?.rotation ?: Surface.ROTATION_0
            return when (rot) {
                Surface.ROTATION_90 -> 90
                Surface.ROTATION_180 -> 180
                Surface.ROTATION_270 -> 270
                else -> 0
            }
        }

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                when (event.sensor.type) {
                    Sensor.TYPE_ROTATION_VECTOR -> {
                        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                        SensorManager.remapCoordinateSystem(
                            rotationMatrix, SensorManager.AXIS_X, SensorManager.AXIS_Y, remapped,
                        )
                        SensorManager.getOrientation(remapped, orientation)
                        val azimuth = Math.toDegrees(orientation[0].toDouble()).toFloat()
                        publish(azimuth + displayRotationDegrees())
                    }
                    Sensor.TYPE_ACCELEROMETER -> {
                        gravity = event.values.clone()
                        computeFallback(gravity, geomagnetic, rotationMatrix, orientation, ::publish, ::displayRotationDegrees)
                    }
                    Sensor.TYPE_MAGNETIC_FIELD -> {
                        geomagnetic = event.values.clone()
                        computeFallback(gravity, geomagnetic, rotationMatrix, orientation, ::publish, ::displayRotationDegrees)
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        val registered = when {
            rotationSensor != null ->
                sm.registerListener(listener, rotationSensor, SensorManager.SENSOR_DELAY_UI)
            accelSensor != null && magSensor != null -> {
                sm.registerListener(listener, accelSensor, SensorManager.SENSOR_DELAY_UI)
                sm.registerListener(listener, magSensor, SensorManager.SENSOR_DELAY_UI)
                true
            }
            else -> false
        }

        onDispose {
            if (registered) runCatching { sm?.unregisterListener(listener) }
        }
    }
    return heading
}

private const val SMOOTHING = 0.15f

private fun computeFallback(
    gravity: FloatArray?,
    geomagnetic: FloatArray?,
    rotationMatrix: FloatArray,
    orientation: FloatArray,
    publish: (Float) -> Unit,
    displayRotationDegrees: () -> Int,
) {
    if (gravity == null || geomagnetic == null) return
    if (!SensorManager.getRotationMatrix(rotationMatrix, null, gravity, geomagnetic)) return
    SensorManager.getOrientation(rotationMatrix, orientation)
    val azimuth = Math.toDegrees(orientation[0].toDouble()).toFloat()
    publish(azimuth + displayRotationDegrees())
}

// ---- Live location ---------------------------------------------------------

/**
 * Latest device location for the user marker. Seeds with last-known and then
 * follows GPS updates. Null until a fix is available or when permission is
 * missing / [active] is false.
 */
@Composable
internal fun rememberDeviceLatLng(active: Boolean = true): LatLng? {
    val context = LocalContext.current
    var latLng by remember { mutableStateOf<LatLng?>(null) }
    DisposableEffect(active) {
        if (!active || !hasLocationPermission(context)) {
            return@DisposableEffect onDispose { }
        }
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        runCatching {
            val last = lm?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: lm?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            last?.let { latLng = LatLng(it.latitude, it.longitude) }
        }
        val listener = object : android.location.LocationListener {
            override fun onLocationChanged(loc: Location) {
                latLng = LatLng(loc.latitude, loc.longitude)
            }
            @Deprecated("Deprecated in API 29 but still abstract on older devices")
            override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }
        runCatching {
            lm?.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, listener)
        }
        onDispose { runCatching { lm?.removeUpdates(listener) } }
    }
    return latLng
}

// ---- Marker bitmap + manager ----------------------------------------------

/** Direction-indicator style for the user-location marker. */
internal enum class LocationMarkerStyle {
    /** A solid, detached arrowhead ahead of the dot (Google-Maps-like). */
    CHEVRON,

    /** A bold, detached cone/beam fanning ahead of the dot. */
    BEAM,
    ;

    companion object {
        const val ID_CHEVRON = "chevron"
        const val ID_BEAM = "beam"

        fun fromId(id: String?): LocationMarkerStyle =
            if (id == ID_BEAM) BEAM else CHEVRON
    }
}

/**
 * Owns a single "you are here" [Marker] on a map. The bitmap is heading-agnostic
 * (drawn pointing "up"/north) and the marker is rotated to the live compass
 * heading, so the icon is built once per [style].
 *
 * Resilient to external `map.clear()`: [update] re-adds the marker whenever the
 * cached handle is gone or the target map changed, so the dot reliably reappears
 * after any screen that clears the shared map (route preview, trip-finished…).
 */
internal class UserLocationMarker(
    private val context: Context,
    private val style: LocationMarkerStyle = LocationMarkerStyle.CHEVRON,
) {
    private var marker: Marker? = null
    private var descriptor: BitmapDescriptor? = null
    // The map the current marker belongs to; if a later update targets a different
    // GoogleMap (or the marker was cleared) we re-add rather than mutate a corpse.
    private var boundMap: GoogleMap? = null

    private fun descriptor(): BitmapDescriptor =
        descriptor ?: BitmapDescriptorFactory.fromBitmap(buildBitmap(context, style))
            .also { descriptor = it }

    /** Create/refresh the marker at [position] with [headingDeg] (null = no rotation). */
    fun update(map: GoogleMap, position: LatLng, headingDeg: Float?) {
        val existing = marker
        // A marker removed by map.clear() keeps a non-null handle but is dead; we
        // can't reliably query validity, so re-add whenever the map identity
        // changed. Same-map updates just mutate the live marker (cheap, no flicker).
        if (existing == null || boundMap !== map) {
            runCatching { existing?.remove() }
            marker = map.addMarker(
                MarkerOptions()
                    .position(position)
                    .icon(descriptor())
                    .anchor(0.5f, 0.5f)
                    .flat(true) // rotate with the map plane, not the screen
                    .rotation(headingDeg ?: 0f)
                    .zIndex(10f),
            )
            boundMap = map
        } else {
            existing.position = position
            if (headingDeg != null) existing.rotation = headingDeg
        }
    }

    /** Drop the marker AND its map binding, so the next [update] re-adds it (used
     *  after we know the map was cleared, or when hiding the marker entirely). */
    fun remove() {
        runCatching { marker?.remove() }
        marker = null
        boundMap = null
    }
}

/** Google-Maps blue. */
private const val MARKER_R = 0x1A
private const val MARKER_G = 0x73
private const val MARKER_B = 0xE8

/**
 * Draw the user-location marker into a bitmap, pointing up (north): a soft
 * translucent halo, a bold detached direction indicator ([style]), then a small
 * blue dot with a white ring. The dot is deliberately compact; the indicator is
 * high-contrast (white-outlined) and sits in a gap ahead of the dot so it's easy
 * to spot and clearly conveys facing direction.
 */
private fun buildBitmap(context: Context, style: LocationMarkerStyle): Bitmap {
    val density = context.resources.displayMetrics.density
    fun dp(v: Float) = v * density

    // Canvas large enough for the detached indicator not to clip.
    val sizePx = dp(84f).toInt()
    val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val cx = sizePx / 2f
    val cy = sizePx / 2f

    val blue = Color.rgb(MARKER_R, MARKER_G, MARKER_B)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    // Soft accuracy halo.
    paint.color = Color.argb(38, MARKER_R, MARKER_G, MARKER_B)
    canvas.drawCircle(cx, cy, dp(32f), paint)

    // ---- Direction indicator (detached, ahead of the dot / north) ----------
    val dotRadius = dp(6.5f)
    val gap = dp(6f) // clear gap between dot and indicator (detached, like Maps)

    when (style) {
        LocationMarkerStyle.CHEVRON -> drawChevron(canvas, cx, cy, dotRadius + gap, ::dp, blue)
        LocationMarkerStyle.BEAM -> drawBeam(canvas, cx, cy, dotRadius, ::dp, blue)
    }

    // ---- Dot: white ring + subtle shadow + blue fill -----------------------
    val ringRadius = dotRadius + dp(2.5f)
    paint.color = Color.WHITE
    paint.style = Paint.Style.FILL
    canvas.drawCircle(cx, cy, ringRadius, paint)
    paint.color = Color.argb(60, 0, 0, 0)
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = dp(1f)
    canvas.drawCircle(cx, cy, ringRadius, paint)
    paint.style = Paint.Style.FILL
    paint.color = blue
    canvas.drawCircle(cx, cy, dotRadius, paint)

    return bmp
}

/** Solid, high-contrast arrowhead pointing north, starting [startFromCenter] px
 *  above the dot centre (so it reads as a detached direction arrow). */
private fun drawChevron(
    canvas: Canvas,
    cx: Float,
    cy: Float,
    startFromCenter: Float,
    dp: (Float) -> Float,
    blue: Int,
) {
    val baseY = cy - startFromCenter          // arrow base (near the dot)
    val tipY = baseY - dp(18f)                // arrow tip (further north)
    val halfWidth = dp(11f)
    // A chunky arrowhead with a slight notch at the base for a crisp "arrow" look.
    val notchY = baseY - dp(5f)
    val arrow = Path().apply {
        moveTo(cx, tipY)                       // tip
        lineTo(cx + halfWidth, baseY)          // right base
        lineTo(cx, notchY)                     // inner notch
        lineTo(cx - halfWidth, baseY)          // left base
        close()
    }
    // White outline first (drawn slightly larger via stroke), then solid fill.
    val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = dp(3f)
        strokeJoin = Paint.Join.ROUND
    }
    canvas.drawPath(arrow, outline)
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = blue
        style = Paint.Style.FILL
    }
    canvas.drawPath(arrow, fill)
}

/** Bold, mostly-opaque cone fanning north, detached from the dot with a gap. */
private fun drawBeam(
    canvas: Canvas,
    cx: Float,
    cy: Float,
    dotRadius: Float,
    dp: (Float) -> Float,
    blue: Int,
) {
    val innerR = dotRadius + dp(5f)  // start beyond the dot (detached)
    val length = dp(26f)
    val outerR = innerR + length
    val halfAngle = Math.toRadians(24.0)
    val sinA = sin(halfAngle).toFloat()
    val cosA = cos(halfAngle).toFloat()

    val innerLeftX = cx - sinA * innerR
    val innerRightX = cx + sinA * innerR
    val innerY = cy - cosA * innerR
    val outerLeftX = cx - sinA * outerR
    val outerRightX = cx + sinA * outerR
    val outerY = cy - cosA * outerR
    val tipX = cx
    val tipY = cy - outerR

    val cone = Path().apply {
        moveTo(innerLeftX, innerY)
        lineTo(outerLeftX, outerY)
        lineTo(tipX, tipY)
        lineTo(outerRightX, outerY)
        lineTo(innerRightX, innerY)
        close()
    }
    // Bold gradient: strong near the dot, fading toward the tip, but far more
    // opaque than the old beam so it's easy to spot.
    val beamPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = android.graphics.RadialGradient(
            cx, cy, outerR,
            intArrayOf(
                Color.argb(235, MARKER_R, MARKER_G, MARKER_B),
                Color.argb(110, MARKER_R, MARKER_G, MARKER_B),
            ),
            floatArrayOf(innerR / outerR, 1f),
            android.graphics.Shader.TileMode.CLAMP,
        )
    }
    canvas.drawPath(cone, beamPaint)
    // Thin white edge outline for contrast on any map background.
    val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(200, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = dp(1.5f)
        strokeJoin = Paint.Join.ROUND
    }
    canvas.drawPath(cone, edge)
}
