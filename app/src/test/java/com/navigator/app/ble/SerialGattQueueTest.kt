package com.navigator.app.ble

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.UUID

class SerialGattQueueTest {

    /** Manual scheduler: timeouts fire only when the test says so. */
    private val pending = mutableListOf<Pair<() -> Unit, BooleanArray>>()
    private val failures = mutableListOf<String>()
    private fun fireTimeouts() {
        val due = pending.toList(); pending.clear()
        due.forEach { (action, cancelled) -> if (!cancelled[0]) action() }
    }

    private val q = SerialGattQueue(
        opTimeoutMs = 2_000,
        scheduler = { _, action ->
            val cancelled = booleanArrayOf(false)
            pending += action to cancelled
            ({ cancelled[0] = true })
        },
        onOpFailed = { failures += it },
    )

    @Test fun opsRunOneAtATime() {
        val ran = mutableListOf<Int>()
        q.enqueue { ran += 1 }
        q.enqueue { ran += 2 }
        assertEquals(listOf(1), ran)
        q.finish()
        assertEquals(listOf(1, 2), ran)
    }

    @Test fun lostCallbackIsReleasedByTimeout() {
        val ran = mutableListOf<Int>()
        q.enqueue { ran += 1 } // completion never arrives
        q.enqueue { ran += 2 }
        fireTimeouts()
        assertEquals(listOf(1, 2), ran)
        assertEquals(1, failures.size)
    }

    @Test fun throwingOpDoesNotWedgeTheQueue() {
        val ran = mutableListOf<Int>()
        q.enqueue { throw SecurityException("boom") }
        q.enqueue { ran += 2 }
        assertEquals(listOf(2), ran)
        assertEquals(1, failures.size)
    }

    @Test fun timeoutOfAFinishedOpIsIgnored() {
        val ran = mutableListOf<Int>()
        q.enqueue { ran += 1 }
        q.finish()          // op 1 completed normally (its timer cancelled)
        q.enqueue { ran += 2 }
        fireTimeouts()      // only op 2's timer is live
        assertEquals(listOf(1, 2), ran)
        assertEquals(1, failures.size)
    }

    @Test fun coalescingDropsStalePendingWrite() {
        val ran = mutableListOf<String>()
        val key = UUID.randomUUID()
        q.enqueue { ran += "busy" }
        q.enqueue(key) { ran += "old" }
        q.enqueue(key) { ran += "new" }
        q.finish()
        assertEquals(listOf("busy", "new"), ran)
    }
}
