package com.navigator.app.notifications

import org.junit.Assert.assertEquals
import org.junit.Test

class NavSubtextParserTest {

    private val en = "AM" to "PM"
    private fun parse(s: String, amPm: Pair<String, String> = en) = NavSubtextParser.parseEtaAndRemaining(s, amPm)

    @Test fun englishSubtitle() {
        assertEquals("02:12" to "9.7 km", parse("22 min · 9.7 km · 2:12 am ETA"))
        assertEquals("14:12" to "9.7 km", parse("22 min · 9.7 km · 2:12 pm ETA"))
        assertEquals("00:05" to null, parse("3 min · 12:05 AM ETA"))
        assertEquals("17:17" to "35 km", parse("1 hr 16 min · 35 km · 17:17 ETA"))
    }

    @Test fun localeMeridiemBeforeOrAfter() {
        val kn = "ಪೂರ್ವಾಹ್ನ" to "ಅಪರಾಹ್ನ"
        assertEquals("14:12", parse("22 ನಿಮಿಷ · 9.7 km · ಅಪರಾಹ್ನ 2:12", kn).first)
        assertEquals("14:12", parse("22 ನಿಮಿಷ · 9.7 km · 2:12 ಅಪರಾಹ್ನ", kn).first)
        assertEquals("09:30", parse("9:30 ಪೂರ್ವಾಹ್ನ", kn).first)
    }

    @Test fun nonAsciiDigitsNormalised() {
        assertEquals("14:05", parse("೧೪:೦೫").first)
    }

    @Test fun imperialRemainingDistance() {
        assertEquals("0.3 mi", parse("5 min · 0.3 miles · 2:12 pm").second)
        assertEquals("500 ft", parse("1 min · 500 ft · 2:12 pm").second)
    }

    @Test fun distanceNeverReadAsDottedTime() {
        assertEquals(null to "12.50 km", parse("20 min · 12.50 km"))
    }

    @Test fun compactDistanceFitsEightChars() {
        assertEquals("0.3 mi", NavSubtextParser.compactDistance("0.3 miles"))
        assertEquals("200 ft", NavSubtextParser.compactDistance("200 feet"))
        assertEquals("110 m", NavSubtextParser.compactDistance("110 m"))
    }
}
