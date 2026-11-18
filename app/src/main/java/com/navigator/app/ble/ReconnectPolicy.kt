package com.navigator.app.ble

/**
 * Timing rules for the dash reconnect loop, kept free of Android types so they
 * can be unit-tested.
 */
object ReconnectPolicy {
    /**
     * How long a "hunting for the bike" scan may run before we stop waiting to
     * hear it advertise and fall back to a background connect to its saved
     * address. A dash that is already linked to the phone over classic Bluetooth
     * (media/calls) may not advertise over BLE at all, and Android quietly
     * downgrades scans that run for ~30 minutes, so an unbounded scan can hang
     * forever.
     */
    const val SCAN_WINDOW_MS = 45_000L

    /** A direct (foreground) connect attempt stuck this long is recycled. */
    const val STUCK_CONNECTING_MS = 45_000L

    /**
     * A background (autoConnect=true) request is meant to wait: field logs showed
     * it relinks on its own the instant the dash powers on, so recycling it often
     * only churns the radio while the bike is simply off. Recycle only as a rare
     * safety net for the "autoConnect silently never fires" quirk.
     */
    const val STUCK_BACKGROUND_MS = 600_000L

    fun stuckLimitMs(backgroundConnect: Boolean): Long =
        if (backgroundConnect) STUCK_BACKGROUND_MS else STUCK_CONNECTING_MS

    /**
     * An explicit "the bike is here" signal (ACL link to the saved MAC) should
     * cut a running scan short and connect directly, unless a link is already
     * up or being set up.
     */
    fun shouldPreemptScan(bikePresent: Boolean, scanning: Boolean, hasGatt: Boolean): Boolean =
        bikePresent && scanning && !hasGatt

    /**
     * A background connect request is parked (never reached STATE_CONNECTED) but
     * the bike's own link just came up: recycle it into a direct connect.
     */
    fun shouldPreemptBackgroundConnect(
        bikePresent: Boolean, backgroundConnect: Boolean, hasGatt: Boolean, linkUp: Boolean,
    ): Boolean = bikePresent && backgroundConnect && hasGatt && !linkUp
}
