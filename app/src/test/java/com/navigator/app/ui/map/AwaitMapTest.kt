package com.navigator.app.ui.map

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AwaitMapTest {
    @Test fun aMapDeliveredLaterIsTakenByThePageThatAskedForIt() = runTest {
        var deliver: ((String) -> Unit)? = null
        val taken = mutableListOf<String>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) { taken += awaitDelivery<String> { deliver = it } }
        // The SDK posts it to the main thread even when it has it already: the asking coroutine is still waiting then.
        assertTrue(job.isActive)
        deliver!!("map")
        runCurrent()
        assertEquals(listOf("map"), taken)
        assertFalse(job.isActive)
    }

    @Test fun aPageThatStoppedBeingActiveTakesNothingAndALateDeliveryIsIgnored() = runTest {
        var deliver: ((String) -> Unit)? = null
        val taken = mutableListOf<String>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) { taken += awaitDelivery<String> { deliver = it } }
        job.cancel()
        runCurrent()
        deliver!!("map") // must neither throw nor resume
        runCurrent()
        assertTrue(taken.isEmpty())
    }
}
