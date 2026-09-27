package com.navigator.app.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class BccuProtocolTextTest {

    @Test fun safePrefixNeverSplitsSurrogatePairs() {
        val s = "Hi there 😀😀😀😀" // each emoji = 2 UTF-16 units
        val p = BccuProtocol.safePrefix(s, 10)
        assertEquals("Hi there ", p)
        assertFalse(String(p.toByteArray(Charsets.UTF_8), Charsets.UTF_8).contains('?'))
        assertEquals("Hi there 😀", BccuProtocol.safePrefix(s, 11))
    }

    @Test fun safePrefixKeepsIndicClustersWhole() {
        // "ಕ್ಷ" = ಕ + ್ + ಷ : one conjunct; must not end on the virama.
        val s = "ಕ್ಷೇತ್ರ ರಸ್ತೆ"
        val p = BccuProtocol.safePrefix(s, 2)
        assertFalse("must not end on a virama: $p", p.endsWith("\u0CCD"))
    }

    @Test fun shortTextUnchanged() {
        assertEquals("NH 48", BccuProtocol.safePrefix("NH 48", 16))
    }

    @Test fun roadLabelEllipsisIsClusterSafe() {
        val payload = BccuProtocol.buildTurnRoadPayload("A".repeat(28) + "😀😀😀")
        val text = String(payload.copyOfRange(1, payload.size), Charsets.UTF_8)
        assertFalse(text.contains('?'))
        assert(text.endsWith("..."))
    }

    @Test fun graphemesSplitsByUserCharacter() {
        assertEquals(listOf("a", "😀", "b"), BccuProtocol.graphemes("a😀b"))
    }
}
