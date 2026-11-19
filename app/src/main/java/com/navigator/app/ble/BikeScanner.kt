package com.navigator.app.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import com.navigator.app.logging.AppLogger
import com.navigator.app.ui.OpenDashPermissions
import com.navigator.app.settings.AppSettings
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

/**
 * The pairing scan: BLE advertisements plus the phone's bonded devices (a bonded dash may be linked
 * over classic Bluetooth and not advertise at all, so it shows as "paired before" without a signal).
 * Main thread. One scan window of [WINDOW_MS], restartable.
 */
@SuppressLint("MissingPermission") // checked through OpenDashPermissions before every call
class BikeScanner(private val context: Context, private val settings: () -> AppSettings) {
    private val _results = MutableStateFlow<List<ScannedBike>>(emptyList())
    val results: StateFlow<List<ScannedBike>> = _results.asStateFlow()

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    /** Set when the scan could not start for lack of the Nearby devices permission. */
    private val _permissionMissing = MutableStateFlow(false)
    val permissionMissing: StateFlow<Boolean> = _permissionMissing.asStateFlow()

    /** The Bluetooth stack's error code when the scan could not run (for example scanning too often), else null. */
    private val _scanError = MutableStateFlow<Int?>(null)
    val scanError: StateFlow<Int?> = _scanError.asStateFlow()

    /** True when the adapter is off, so an empty list means "turn Bluetooth on", not "nothing found". */
    private val _bluetoothOff = MutableStateFlow(false)
    val bluetoothOff: StateFlow<Boolean> = _bluetoothOff.asStateFlow()

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val stopRunnable = Runnable { stop() }

    private val callback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val dev = result.device
            val name = result.scanRecord?.deviceName ?: runCatching { dev.name }.getOrNull()
            _results.value = BikeScanning.merge(_results.value, dev.toBike(result.rssi, name))
        }

        override fun onScanFailed(errorCode: Int) {
            AppLogger.log("Pairing", "!! scan failed: $errorCode")
            handler.removeCallbacks(stopRunnable)
            _scanning.value = false
            _scanError.value = errorCode
        }
    }

    fun start() {
        if (!OpenDashPermissions.bluetoothGranted(context)) {
            _permissionMissing.value = true
            return
        }
        _permissionMissing.value = false
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
        val scanner = adapter?.takeIf { it.isEnabled }?.bluetoothLeScanner
        _bluetoothOff.value = scanner == null
        if (_scanning.value) stop()
        _scanError.value = null
        if (scanner == null) {
            _results.value = emptyList()
            return
        }
        _results.value = runCatching {
            adapter.bondedDevices.orEmpty().map { it.toBike(null) }
        }.getOrElse {
            AppLogger.log("Pairing", "bondedDevices read denied: ${it.message}")
            emptyList()
        }
        try {
            scanner.startScan(
                null,
                android.bluetooth.le.ScanSettings.Builder().setScanMode(android.bluetooth.le.ScanSettings.SCAN_MODE_LOW_LATENCY).build(),
                callback,
            )
            _scanning.value = true
        } catch (e: SecurityException) {
            _permissionMissing.value = true
            return
        }
        handler.postDelayed(stopRunnable, WINDOW_MS)
    }

    fun stop() {
        handler.removeCallbacks(stopRunnable)
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
    }
}
