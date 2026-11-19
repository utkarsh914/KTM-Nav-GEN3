package com.navigator.app.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BikeScanningTest {
    @Test fun rssiBuckets() {
        assertEquals(SignalStrength.VERY_CLOSE, BikeScanning.signal(-60))
        assertEquals(SignalStrength.NEARBY, BikeScanning.signal(-61))
        assertEquals(SignalStrength.NEARBY, BikeScanning.signal(-80))
        assertEquals(SignalStrength.FAR, BikeScanning.signal(-81))
        assertEquals(SignalStrength.UNKNOWN, BikeScanning.signal(null))
        assertEquals("0 is what a bonded, unheard device reports", SignalStrength.UNKNOWN, BikeScanning.signal(0))
    }

    @Test fun likelyDashIsAKtmNameInAnyCase() {
        assertTrue(BikeScanning.isLikelyDash("KTM3638"))
        assertTrue(BikeScanning.isLikelyDash("my ktm"))
        assertFalse(BikeScanning.isLikelyDash("JBL Flip"))
        assertFalse(BikeScanning.isLikelyDash(null))
    }

    private fun bike(addr: String, name: String?, rssi: Int?) = ScannedBike(addr, name, rssi, pairedBefore = false)

    @Test fun mergeUpdatesTheSameAddressAndAddsNewOnes() {
        val bonded = listOf(bike("AA:01", "KTM3638", null))
        val heard = BikeScanning.merge(bonded, bike("aa:01", "KTM3638", -55))
        assertEquals(1, heard.size)
        assertEquals(-55, heard[0].rssi)
        assertEquals(2, BikeScanning.merge(heard, bike("BB:02", "Headset", -70)).size)
    }

    @Test fun aLaterReadingWithoutANameKeepsTheKnownName() {
        val merged = BikeScanning.merge(listOf(bike("A", "KTM3638", null)), bike("A", null, -50))
        assertEquals("KTM3638", merged[0].name)
        assertEquals(-50, merged[0].rssi)
    }

    @Test fun sortedPutsDashesFirstThenTheStrongestSignalThenBondedOnly() {
        val list = listOf(
            bike("1", "Headset", -40),
            bike("2", "KTM1", null),
            bike("3", "KTM2", -70),
            bike("4", "KTM3", -50),
        )
        assertEquals(listOf("4", "3", "2", "1"), BikeScanning.sorted(list).map { it.address })
    }

    @Test fun scannedBikeDerivesSignalAndDash() {
        val b = bike("1", "KTM9", -75)
        assertEquals(SignalStrength.NEARBY, b.signal)
        assertTrue(b.isLikelyDash)
    }
}
