package com.navigator.app.nav.providers

import java.util.concurrent.atomic.AtomicLong

/**
 * Generation counter for in-app trips. Every callback that can outlive the trip
 * that created it (route results, arrival, NavInfo frames) captures the
 * generation at creation and drops itself once [invalidate]/[next] moved on,
 * so a stale result can never restart, resume or overwrite a newer trip.
 */
class TripGeneration {
    private val gen = AtomicLong(0)

    /** Start a new trip; everything captured earlier becomes stale. */
    fun next(): Long = gen.incrementAndGet()

    /** End the current trip without starting another. */
    fun invalidate() { gen.incrementAndGet() }

    fun current(): Long = gen.get()
    fun isCurrent(token: Long): Boolean = gen.get() == token
}

/** What to do with the outcome of a `setDestinations` request. */
enum class RouteResultAction {
    /** Route computed: start guidance. */
    START,
    /** Superseded by a newer request. Terminal for this request: never retry. */
    IGNORE_CANCELED,
    /** Failed and a retry/fallback is still available. */
    RETRY,
    /** Failed and nothing is left to try. */
    FAIL,
}

object RouteResultPolicy {
    /**
     * @param ok the SDK reported RouteStatus.OK
     * @param canceled the SDK reported RouteStatus.ROUTE_CANCELED ("canceled in
     *   favour of a newer request"); retrying it would cancel the newer trip and
     *   bill every call.
     * @param canRetry a token retry or travel-mode fallback is still available
     */
    fun decide(ok: Boolean, canceled: Boolean, canRetry: Boolean): RouteResultAction = when {
        ok -> RouteResultAction.START
        canceled -> RouteResultAction.IGNORE_CANCELED
        canRetry -> RouteResultAction.RETRY
        else -> RouteResultAction.FAIL
    }
}

/** Why an in-app trip could not start; drives the route-failure card. */
enum class StartFailure(val message: String) {
    ROUTE("Couldn't calculate a route."),
    NAVIGATOR_UNAVAILABLE("Navigation isn't ready. Check internet, Play services and the Terms prompt."),
    UNAVAILABLE("In-app navigation isn't available on this device."),
}
