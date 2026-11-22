package com.navigator.app.ui.map

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SettledTest {

    @Test fun firstValueArrivesAtOnce() = runTest {
        val src = MutableStateFlow(MapInsets(top = 5))
        val got = mutableListOf<MapInsets>()
        backgroundScope.launch { src.settled(60).toList(got) }
        runCurrent()
        assertEquals(listOf(MapInsets(top = 5)), got)
    }

    @Test fun aBurstOfChangesAppliesOnlyTheLastOnce() = runTest {
        val src = MutableStateFlow(0)
        val got = mutableListOf<Int>()
        backgroundScope.launch { src.settled(60).toList(got) }
        runCurrent()
        for (v in 1..30) { src.value = v; advanceTimeBy(16) }
        advanceTimeBy(100)
        assertEquals(listOf(0, 30), got)
    }

    @Test fun anEqualValueIsNeverAppliedAgain() = runTest {
        val src = MutableStateFlow(7)
        val got = mutableListOf<Int>()
        backgroundScope.launch { src.settled(60).toList(got) }
        runCurrent()
        src.value = 8; advanceTimeBy(10); src.value = 7; advanceTimeBy(200)
        assertEquals(listOf(7), got)
    }

    @Test fun aLaterValueWaitsForTheSettleTime() = runTest {
        val src = MutableStateFlow(1)
        val got = mutableListOf<Int>()
        backgroundScope.launch { src.settled(60).toList(got) }
        runCurrent()
        src.value = 2
        advanceTimeBy(59); runCurrent()
        assertEquals(listOf(1), got)
        advanceTimeBy(2); runCurrent()
        assertEquals(listOf(1, 2), got)
    }
}
