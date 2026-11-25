package com.navigator.app.nav.providers

import java.util.concurrent.atomic.AtomicLong

/**
 * Tells a pending start whether it is still wanted. `getNavigator` answers later (it may show the terms dialog): if the
 * rider went Back, replaced the trip or quit meanwhile, that answer must not bring guidance up with no screen for it.
 */
class StartRequests {
    private val latest = AtomicLong(0)

    /** A new start: every earlier one is stale from now on. */
    fun begin(): Long = latest.incrementAndGet()

    /** The pending start (if any) is cancelled. */
    fun invalidate() { latest.incrementAndGet() }

    fun isCurrent(id: Long): Boolean = latest.get() == id
}
