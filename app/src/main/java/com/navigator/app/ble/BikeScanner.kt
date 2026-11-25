package com.navigator.app.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.navigator.app.logging.AppLogger
import com.navigator.app.settings.AppSettings
import com.navigator.app.ui.OpenDashPermissions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class SignalStrength { VERY_CLOSE, NEARBY, FAR, UNKNOWN }

/** A bike (or any BLE device) found while pairing. [rssi] is null for a bonded device that was not heard. */
data class ScannedBike(
    val address: String,
    val name: String?,
    val rssi: Int?,
    val pairedBefore: Boolean,
) {
    val signal: SignalStrength get() = BikeScanning.signal(rssi)
    val isLikelyDash: Boolean get() = BikeScanning.isLikelyDash(name)
}

/** Pure rules for the pairing list. */
object BikeScanning {
    const val VERY_CLOSE_RSSI = -60
    const val NEARBY_RSSI = -80

    fun signal(rssi: Int?): SignalStrength = when {
        rssi == null || rssi == 0 -> SignalStrength.UNKNOWN
        rssi >= VERY_CLOSE_RSSI -> SignalStrength.VERY_CLOSE
        rssi >= NEARBY_RSSI -> SignalStrength.NEARBY
        else -> SignalStrength.FAR
    }

    fun isLikelyDash(name: String?): Boolean = name?.contains("KTM", ignoreCase = true) == true

    /** The list as shown: dashes first, then by signal (heard before merely bonded), then by name. */
    fun sorted(bikes: List<ScannedBike>): List<ScannedBike> =
        bikes.sortedWith(
            compareByDescending<ScannedBike> { it.isLikelyDash }
                .thenByDescending { it.rssi ?: Int.MIN_VALUE }
                .thenBy { it.name ?: it.address },
        )

    /** Add or refresh [found] (latest reading wins); a bonded entry without a reading is replaced by a heard one. */
    fun merge(list: List<ScannedBike>, found: ScannedBike): List<ScannedBike> {
        val i = list.indexOfFirst { it.address.equals(found.address, ignoreCase = true) }
        // A later reading may carry no name: keep the one we had.
        return if (i >= 0) list.toMutableList().also { it[i] = found.copy(name = found.name ?: list[i].name) } else list + found
    }
}

/** What the pairing screens need from a scan, so they can be tested without Bluetooth. */
interface ScanSource {
    val results: StateFlow<List<ScannedBike>>
    val scanning: StateFlow<Boolean>
    val scanError: StateFlow<Int?>
    fun start()
    fun stop()
}

/**
 * The pairing scan: BLE advertisements plus the phone's bonded devices (a bonded dash may be linked
 * over classic Bluetooth and not advertise at all, so it shows as "paired before" without a signal).
 * Main thread. One scan window of [WINDOW_MS], restartable.
 */
@SuppressLint("MissingPermission") // checked through OpenDashPermissions before every call
class BikeScanner(private val context: Context, private val settings: () -> AppSettings) : ScanSource {
    private val _results = MutableStateFlow<List<ScannedBike>>(emptyList())
    override val results: StateFlow<List<ScannedBike>> = _results.asStateFlow()

    private val _scanning = MutableStateFlow(false)
    override val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    /** The Bluetooth stack's error code when the scan could not run (for example scanning too often), else null. */
    private val _scanError = MutableStateFlow<Int?>(null)
    override val scanError: StateFlow<Int?> = _scanError.asStateFlow()

    private val handler = Handler(Looper.getMainLooper())
    private val stopRunnable = Runnable { stop() }

    // A low-latency scan reports every advertisement (several per second per device): the list is published at most every
    // THROTTLE_MS, so the screen does not recompose and re-sort on each one.
    private var latest: List<ScannedBike> = emptyList()
    private var lastPublishMs = 0L
    private val flushRunnable = Runnable { publish() }

    private fun publish() {
        handler.removeCallbacks(flushRunnable)
        lastPublishMs = SystemClock.uptimeMillis()
        _results.value = latest
    }

    private fun publishSoon() {
        val wait = lastPublishMs + THROTTLE_MS - SystemClock.uptimeMillis()
        if (wait <= 0) publish() else if (!handler.hasCallbacks(flushRunnable)) handler.postDelayed(flushRunnable, wait)
    }

    private val callback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val dev = result.device
            val name = result.scanRecord?.deviceName ?: runCatching { dev.name }.getOrNull()
            latest = BikeScanning.merge(latest, dev.toBike(result.rssi, name))
            publishSoon()
        }

        override fun onScanFailed(errorCode: Int) {
            AppLogger.log("Pairing", "!! scan failed: $errorCode")
            handler.removeCallbacks(stopRunnable)
            _scanning.value = false
            _scanError.value = errorCode
        }
    }

    override fun start() {
        if (!OpenDashPermissions.bluetoothGranted(context)) return
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
        val scanner = adapter?.takeIf { it.isEnabled }?.bluetoothLeScanner
        if (_scanning.value) stop()
        _scanError.value = null
        if (scanner == null) {
            latest = emptyList()
            publish()
            return
        }
        latest = runCatching {
            adapter.bondedDevices.orEmpty().map { it.toBike(null) }
        }.getOrElse {
            AppLogger.log("Pairing", "bondedDevices read denied: ${it.message}")
            emptyList()
        }
        publish()
        try {
            scanner.startScan(
                null,
                ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),
                callback,
            )
            _scanning.value = true
        } catch (e: SecurityException) {
            return
        }
        handler.postDelayed(stopRunnable, WINDOW_MS)
    }

    override fun stop() {
        handler.removeCallbacks(stopRunnable)
        if (handler.hasCallbacks(flushRunnable)) publish() // what was heard is not lost with the scan
        if (!_scanning.value) return
        _scanning.value = false
        runCatching { context.getSystemService(BluetoothManager::class.java)?.adapter?.bluetoothLeScanner?.stopScan(callback) }
            .onFailure { AppLogger.log("Pairing", "stopScan failed: ${it.message}") }
    }

    private fun BluetoothDevice.toBike(rssi: Int?, advertisedName: String? = null) = ScannedBike(
        address = address,
        name = advertisedName ?: runCatching { name }.getOrNull(),
        rssi = rssi,
        pairedBefore = settings().hasPairedBefore(address),
    )

    companion object {
        const val WINDOW_MS = 8_000L
        const val THROTTLE_MS = 250L
    }
}
