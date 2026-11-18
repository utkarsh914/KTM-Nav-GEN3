package com.navigator.app.ble

import java.util.ArrayDeque
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Serialises GATT operations - the Android BLE stack allows only one outstanding
 * GATT op at a time, so all reads/writes/descriptor-writes funnel through here.
 *
 * [enqueue] adds an op; a non-null `coalesceKey` marks a "latest value wins" write
 * (guidance labels, marquee frames, banner) so enqueueing drops any still-pending
 * op with the same key - a degraded link can never build an unbounded backlog of
 * stale frames. Control-plane ops (auth, CCCD, PRPC requests) pass a null key.
 *
 * The owner MUST call [finish] from every GATT completion callback to release the
 * next op, and [clear] on teardown. Thread-safe: ops are enqueued/finished on BLE
 * binder threads while teardown paths clear the queue from the main thread
 * (ArrayDeque itself is not thread-safe, hence the lock).
 *
 * Completion callbacks identify the characteristic they belong to: [finish] with a
 * uuid that differs from the in-flight op's target is a LATE completion of an op
 * that already timed out, and is ignored instead of releasing the op that
 * replaced it (which used to cascade into busy rejections and a dropped queue).
 *
 * Self-healing: an in-flight op is released after [opTimeoutMs] if its
 * completion callback never arrives (a known Android quirk on a link that stays
 * up), and an op that throws is released immediately - either used to leave the
 * queue busy forever, freezing every later dash write until a reconnect.
 */
internal class SerialGattQueue(
    private val opTimeoutMs: Long = 2_000L,
    /** Schedules [action] after [delayMs]; returns a canceller. Injectable for tests. */
    private val scheduler: (delayMs: Long, action: () -> Unit) -> (() -> Unit) = ::defaultSchedule,
    /** Called (outside the lock) when an op timed out or threw, e.g. to log/resync. */
    private val onOpFailed: (reason: String) -> Unit = {},
) {

    private class Op(val coalesceKey: java.util.UUID?, val target: java.util.UUID?, val run: () -> Unit)

    private val lock = Any()
    private val queue = ArrayDeque<Op>()
    private var busy = false
    /** Id of the op currently in flight (0 = none); guards timeout/throw releases. */
    private var inFlightId = 0L
    private var nextId = 0L
    private var cancelTimeout: (() -> Unit)? = null
    /** Characteristic the in-flight op targets (null = unknown / not matchable). */
    private var inFlightTarget: java.util.UUID? = null

    /**
     * @param target the characteristic this op reads/writes, used to recognise its
     * completion callback; defaults to [coalesceKey] (which is the target uuid for
     * display writes).
     */
    fun enqueue(coalesceKey: java.util.UUID? = null, target: java.util.UUID? = coalesceKey, op: () -> Unit) {
        synchronized(lock) {
            if (coalesceKey != null) {
                queue.removeAll { it.coalesceKey == coalesceKey }
            }
            queue.addLast(Op(coalesceKey, target, op))
        }
        runNext()
    }

    private fun runNext() {
        val (next, id) = synchronized(lock) {
            if (busy) return
            val op = queue.pollFirst() ?: return
            busy = true
            val id = ++nextId
            inFlightId = id
            inFlightTarget = op.target
            cancelTimeout = scheduler(opTimeoutMs) { release(id, "timed out after ${opTimeoutMs}ms") }
            op to id
        }
        try {
            next.run()
        } catch (e: Throwable) {
            release(id, "threw: $e")
        }
    }

    /** Release op [id] if it is still the one in flight (timeout / exception path). */
    private fun release(id: Long, reason: String) {
        val released = synchronized(lock) {
            if (!busy || inFlightId != id) return
            releaseLocked()
            true
        }
        if (released) {
            onOpFailed(reason)
            runNext()
        }
    }

    private fun releaseLocked() {
        busy = false
        inFlightId = 0
        inFlightTarget = null
        cancelTimeout?.invoke()
        cancelTimeout = null
    }

    /**
     * Release the queue so the next op can run. Call from each GATT completion callback,
     * passing the characteristic uuid the callback is for ([completedUuid]); calls made
     * from inside an op that failed synchronously pass null and always release.
     * Returns false when the completion was ignored as a late one.
     */
    fun finish(completedUuid: java.util.UUID? = null): Boolean {
        synchronized(lock) {
            val target = inFlightTarget
            if (completedUuid != null && target != null && completedUuid != target) return false
            releaseLocked()
        }
        runNext()
        return true
    }

    /** Drop all pending ops and reset the busy flag (teardown / reconnect). */
    fun clear() {
        synchronized(lock) {
            queue.clear()
            releaseLocked()
        }
    }

    private companion object {
        private val timer = Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, "gatt-op-timeout").apply { isDaemon = true }
        }

        fun defaultSchedule(delayMs: Long, action: () -> Unit): () -> Unit {
            val f = timer.schedule(action, delayMs, TimeUnit.MILLISECONDS)
            return { f.cancel(false) }
        }
    }
}
