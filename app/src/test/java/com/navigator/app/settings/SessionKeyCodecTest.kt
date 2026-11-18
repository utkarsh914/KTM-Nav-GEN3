package com.navigator.app.settings

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SessionKeyCodecTest {
    private fun pool() = List(16) { i -> ByteArray(16) { j -> (i * 16 + j).toByte() } }

    @Test fun roundTrips() {
        val decoded = SessionKeyCodec.decode(SessionKeyCodec.encode(pool()))!!
        assertEquals(16, decoded.size)
        pool().forEachIndexed { i, k -> assertArrayEquals(k, decoded[i]) }
    }

    @Test fun rejectsMalformedPools() {
        val good = SessionKeyCodec.encode(pool())
        assertNull(SessionKeyCodec.decode(null))
        assertNull(SessionKeyCodec.decode(""))
        assertNull(SessionKeyCodec.decode(good.substringBeforeLast(",")))          // 15 keys
        assertNull(SessionKeyCodec.decode(good + ",00"))                            // 17 / short key
        assertNull(SessionKeyCodec.decode(good.dropLast(1)))                        // truncated key
        assertNull(SessionKeyCodec.decode(good.replaceFirst("00", "zz")))           // not hex
    }

    @Test fun matchesLegacyStorageFormat() {
        // Same lowercase-hex, comma-separated layout the old storeSessionKeys wrote.
        val legacy = pool().joinToString(",") { k -> k.joinToString("") { "%02x".format(it) } }
        assertEquals(legacy, SessionKeyCodec.encode(pool()))
    }
}
