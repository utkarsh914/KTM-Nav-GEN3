package com.navigator.app.ble

import java.util.ArrayDeque

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
 */
internal class SerialGattQueue {

    private class Op(val coalesceKey: java.util.UUID?, val run: () -> Unit)

    private val lock = Any()
    private val queue = ArrayDeque<Op>()
    private var busy = false

    fun enqueue(coalesceKey: java.util.UUID? = null, op: () -> Unit) {
        synchronized(lock) {
            if (coalesceKey != null) {
                queue.removeAll { it.coalesceKey == coalesceKey }
            }
            queue.addLast(Op(coalesceKey, op))
        }
        runNext()
    }

    private fun runNext() {
        val next = synchronized(lock) {
            if (busy) return
            val op = queue.pollFirst() ?: return
            busy = true
            op
        }
        next.run()
    }

    /** Release the queue so the next op can run. Call from each GATT completion callback. */
    fun finish() {
        synchronized(lock) { busy = false }
        runNext()
    }

    /** Drop all pending ops and reset the busy flag (teardown / reconnect). */
    fun clear() {
        synchronized(lock) {
            queue.clear()
            busy = false
        }
    }
}
