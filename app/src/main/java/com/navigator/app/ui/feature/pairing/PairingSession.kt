package com.navigator.app.ui.feature.pairing

import android.content.Context
import com.navigator.app.ble.BccuConnectionService
import com.navigator.app.data.settings.SettingsRepository

/** The side effects of pairing: choosing a bike starts the link, giving up puts things back as they were. */
interface PairingSession {
    fun begin(address: String, name: String?)

    /** The attempt ended without a bike: stop the link and restore the bike that was paired before, if any. */
    fun abandon()

    /** The handshake finished: keep the new bike. */
    fun commit()

    /** Debug builds only: pretend a bike is paired. */
    fun bondDebugDevice()
}

class AndroidPairingSession(context: Context, private val settings: SettingsRepository) : PairingSession {
    private val app = context.applicationContext
    private var previous: Pair<String?, String?>? = null

    override fun begin(address: String, name: String?) {
        val s = settings.legacy
        if (previous == null) previous = s.bondedDeviceAddress to s.bondedDeviceName
        s.bondedDeviceAddress = address
        s.bondedDeviceName = name
        settings.refresh()
        BccuConnectionService.start(app, address)
    }

    override fun abandon() {
        BccuConnectionService.stop(app)
        val before = previous
        previous = null
        if (before != null) {
            settings.legacy.bondedDeviceAddress = before.first
            settings.legacy.bondedDeviceName = before.second
        }
        settings.refresh()
        // The bike that was paired before gets its link back.
        before?.first?.let { BccuConnectionService.start(app, it) }
    }

    override fun commit() {
        previous = null
        // The date the pairing was confirmed, for the bike card.
        settings.legacy.bondedPairedMs = System.currentTimeMillis()
    }

    override fun bondDebugDevice() {
        settings.legacy.bondedDeviceAddress = "00:00:00:00:00:00"
        settings.legacy.bondedDeviceName = "Debug device (no real bike)"
        settings.legacy.bondedPairedMs = System.currentTimeMillis()
        settings.refresh()
    }
}
