package com.navigator.app.ui.shell

import android.content.Context
import android.content.Intent
import android.provider.Settings

/** Opens the system Bluetooth settings (to pair or forget the dash by hand). A phone without the screen does nothing. */
fun Context.openBluetoothSettings() {
    runCatching { startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
