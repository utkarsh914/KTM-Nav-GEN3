package com.navigator.app.ble

import android.app.Activity
import android.bluetooth.le.ScanFilter
import android.companion.AssociationInfo
import android.companion.AssociationRequest
import android.companion.BluetoothLeDeviceFilter
import android.companion.CompanionDeviceManager
import android.companion.ObservingDevicePresenceRequest
import android.content.Context
import android.content.IntentSender
import androidx.core.content.ContextCompat
import com.navigator.app.logging.AppLogger
import com.navigator.app.settings.AppSettings

/** RF4b spike (debug builds only): associate with the bonded bike and start observing its presence. */
object BikePresenceSpike {
    fun start(activity: Activity, launch: (IntentSender) -> Unit) {
        val address = AppSettings(activity).bondedDeviceAddress
        if (address == null) { AppLogger.log("Presence", "no bonded bike"); return }
        val cdm = activity.getSystemService(CompanionDeviceManager::class.java)
        cdm.myAssociations.firstOrNull { it.deviceMacAddress?.toString().equals(address, ignoreCase = true) }?.let {
            AppLogger.log("Presence", "already associated (${it.id}); observing")
            observe(cdm, it.id)
            return
        }
        val filter = BluetoothLeDeviceFilter.Builder().setScanFilter(ScanFilter.Builder().setDeviceAddress(address).build()).build()
        val request = AssociationRequest.Builder().addDeviceFilter(filter).setSingleDevice(true).build()
        cdm.associate(request, ContextCompat.getMainExecutor(activity), object : CompanionDeviceManager.Callback() {
            override fun onAssociationPending(intentSender: IntentSender) {
                AppLogger.log("Presence", "association pending: showing the system dialog")
                launch(intentSender)
            }

            override fun onAssociationCreated(associationInfo: AssociationInfo) {
                AppLogger.log("Presence", "associated (${associationInfo.id})")
                observe(cdm, associationInfo.id)
            }

            override fun onFailure(error: CharSequence?) {
                AppLogger.log("Presence", "!! association failed: $error")
            }
        })
    }

    private fun observe(cdm: CompanionDeviceManager, id: Int) {
        runCatching { cdm.startObservingDevicePresence(ObservingDevicePresenceRequest.Builder().setAssociationId(id).build()) }
            .onSuccess { AppLogger.log("Presence", "observing association $id: switch the ignition on and off with the app closed") }
            .onFailure { AppLogger.log("Presence", "!! cannot observe: ${it.javaClass.simpleName}: ${it.message}") }
    }

    fun stop(context: Context) {
        val cdm = context.getSystemService(CompanionDeviceManager::class.java)
        cdm.myAssociations.forEach {
            runCatching { cdm.stopObservingDevicePresence(ObservingDevicePresenceRequest.Builder().setAssociationId(it.id).build()) }
            runCatching { cdm.disassociate(it.id) }
        }
        AppLogger.log("Presence", "stopped and removed ${cdm.myAssociations.size} association(s)")
    }
}
