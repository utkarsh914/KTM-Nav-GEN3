package com.navigator.app.ui.feature.settings

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothManager
import android.content.Context
import com.navigator.app.ui.OpenDashPermissions
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/** A paired Bluetooth audio device a call can be sent to. */
data class AudioDevice(val address: String, val name: String)

/** The devices, or why there are none to show: Android only lists them once nearby-devices access is allowed. */
data class AudioDeviceList(val devices: List<AudioDevice> = emptyList(), val permissionDenied: Boolean = false)

/** Where the picker gets its devices from. */
fun interface AudioDevices {
    suspend fun bonded(): AudioDeviceList
}

/** The paired audio devices, or none when Android won't say (no nearby-devices permission, Bluetooth off). Slow binder IPC: off the main thread. */
class AndroidAudioDevices(context: Context, private val io: CoroutineDispatcher = Dispatchers.IO) : AudioDevices {
    private val app = context.applicationContext

    // The permission is checked on the line above; lint can't see through the helper.
    @SuppressLint("MissingPermission")
    override suspend fun bonded(): AudioDeviceList = kotlinx.coroutines.withContext(io) {
        if (!OpenDashPermissions.isGranted(app, Manifest.permission.BLUETOOTH_CONNECT)) return@withContext AudioDeviceList(permissionDenied = true)
        val devices = runCatching {
            app.getSystemService(BluetoothManager::class.java)?.adapter?.bondedDevices.orEmpty()
                .filter { it.bluetoothClass?.hasService(BluetoothClass.Service.AUDIO) == true }
                .map { AudioDevice(it.address, it.name ?: it.address) }
                .sortedBy { it.name.lowercase() }
        }.getOrDefault(emptyList())
        AudioDeviceList(devices)
    }
}
