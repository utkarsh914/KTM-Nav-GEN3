package com.navigator.app.ble

import android.companion.AssociationInfo
import android.companion.CompanionDeviceService
import android.companion.DevicePresenceEvent
import com.navigator.app.logging.AppLogger

/** RF4b spike: logs when the system sees the bike's Bluetooth LE advertisement appear and disappear, app closed or not. */
class BikePresenceService : CompanionDeviceService() {
    override fun onDevicePresenceEvent(event: DevicePresenceEvent) {
        AppLogger.init(this)
        val name = when (event.event) {
            DevicePresenceEvent.EVENT_BLE_APPEARED -> "BLE_APPEARED"
            DevicePresenceEvent.EVENT_BLE_DISAPPEARED -> "BLE_DISAPPEARED"
            DevicePresenceEvent.EVENT_BT_CONNECTED -> "BT_CONNECTED"
            DevicePresenceEvent.EVENT_BT_DISCONNECTED -> "BT_DISCONNECTED"
            else -> "event ${event.event}"
        }
        AppLogger.log("Presence", "$name (association ${event.associationId})")
    }

    // Older callbacks some builds still use; same information.
    override fun onDeviceAppeared(associationInfo: AssociationInfo) {
        AppLogger.init(this)
        AppLogger.log("Presence", "onDeviceAppeared (association ${associationInfo.id})")
    }

    override fun onDeviceDisappeared(associationInfo: AssociationInfo) {
        AppLogger.init(this)
        AppLogger.log("Presence", "onDeviceDisappeared (association ${associationInfo.id})")
    }
}
