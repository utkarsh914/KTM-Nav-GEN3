package com.navigator.app.ble

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class DashTextTest {

    // A stand-in for ICU: maps the few characters used below.
    private val fake: (String) -> String = { s ->
        s.replace("ಮ", "ma").replace("ೈ", "i").replace("ಸ", "sa").replace("ೂ", "u").replace("ರ", "ra")
            .replace("र", "r").replace("ो", "o").replace("ड", "d")
    }

    @Before fun setUp() { DashText.enabled = true; DashText.transliterate = fake }
    @After fun tearDown() { DashText.enabled = true; DashText.transliterate = null }

    @Test fun latinTextIsUntouched() {
        assertEquals("MG Road, Indiranagar", DashText.forDash("MG Road, Indiranagar"))
        assertEquals("Caf\u00e9 \u00dcber 12\u00b0", DashText.forDash("Caf\u00e9 \u00dcber 12\u00b0")) // Latin-1 is fine
        assertEquals("", DashText.forDash(""))
    }

    @Test fun nonLatinRoadNamesAreTransliterated() {
        assertEquals("masaura", DashText.forDash("\u0cae\u0cb8\u0cc2\u0cb0"))
    }

    @Test fun mixedTextKeepsItsLatinPartAndWhitespaceIsCollapsed() {
        assertEquals("NH 48 masa", DashText.forDash("NH 48   \u0cae\u0cb8"))
    }

    @Test fun whenNothingUsableComesOutARoadRefIsUsedElseNothing() {
        DashText.transliterate = { "\u0cae\u0cb8" } // a "transliterator" that failed to produce Latin
        assertEquals("NH 48", DashText.forDash("\u0cae\u0cb8 NH-48 \u0cb0"))
        assertEquals("", DashText.forDash("\u0cae\u0cb8"))
    }

    @Test fun offOrMissingTransliteratorPassesTextThrough() {
        DashText.enabled = false
        assertEquals("\u0cae\u0cb8", DashText.forDash("\u0cae\u0cb8"))
        DashText.enabled = true
        DashText.transliterate = null
        assertEquals("\u0cae\u0cb8", DashText.forDash("\u0cae\u0cb8"))
    }

    @Test fun aThrowingTransliteratorNeverCrashesTheDashWrite() {
        DashText.transliterate = { throw IllegalStateException("icu") }
        assertEquals("\u0cae\u0cb8", DashText.forDash("\u0cae\u0cb8")) // sent as before, not hidden
    }
}
