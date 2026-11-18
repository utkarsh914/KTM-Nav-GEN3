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

    // --- distanceMeters (turn beeper) ---

    @Test fun distanceMetersUnitsAndSeparators() {
        assertEquals(110, NavSubtextParser.distanceMeters("110 m"))
        assertEquals(1200, NavSubtextParser.distanceMeters("1.2 km"))
        assertEquals(1200, NavSubtextParser.distanceMeters("1,2 km"))        // decimal comma
        assertEquals(1200, NavSubtextParser.distanceMeters("1,200 m"))       // thousands comma
        assertEquals(1200, NavSubtextParser.distanceMeters("1.200 m"))       // thousands dot
        assertEquals(482, NavSubtextParser.distanceMeters("0.3 mi"))
        assertEquals(482, NavSubtextParser.distanceMeters("0.3 miles"))
        assertEquals(457, NavSubtextParser.distanceMeters("500 yd"))
        assertEquals(30, NavSubtextParser.distanceMeters("100 ft"))
        assertEquals(350, NavSubtextParser.distanceMeters("\u0969\u096B\u0966 m")) // Devanagari digits
    }

    @Test fun distanceMetersMustLeadTheText() {
        assertEquals(null, NavSubtextParser.distanceMeters("Turn left onto 5 m Rd"))
        assertEquals(null, NavSubtextParser.distanceMeters("Main St"))
        assertEquals(null, NavSubtextParser.distanceMeters(null))
        assertEquals(null, NavSubtextParser.distanceMeters("5 minutes"))
    }

    @Test fun isDistanceOnlyForWholeDistanceLabels() {
        assertEquals(true, NavSubtextParser.isDistance("110 m"))
        assertEquals(true, NavSubtextParser.isDistance("1.2 km"))
        assertEquals(false, NavSubtextParser.isDistance("Turn left"))
        assertEquals(false, NavSubtextParser.isDistance("110 m then left"))
    }

    // --- NBSP / narrow NBSP and AM/PM token boundaries ---

    @Test fun narrowNoBreakSpaceBeforeMeridiem() {
        assertEquals("14:12" to "9.7 km", parse("22 min\u00A0·\u00A09.7\u00A0km · 2:12\u202FPM ETA"))
        assertEquals("02:12", parse("2:12\u202Fam").first)
    }

    @Test fun meridiemMarkerMustBeAWholeToken() {
        val ams = "AM" to "PM"
        // "am" inside "Amsterdam" must not flip a 12-hour clock.
        assertEquals("12:30", parse("12:30 Amsterdam", ams).first)
    }

    @Test fun dashDistanceLabelIsLenientAboutLocalisedUnitsButNotAboutText() {
        assertEquals("0.3 mi", NavSubtextParser.dashDistanceLabel("0.3 miles"))
        assertEquals("110 m", NavSubtextParser.dashDistanceLabel("110\u00A0m"))
        assertEquals("110 मी", NavSubtextParser.dashDistanceLabel("110 मी"))      // localised unit still reaches the dash
        assertEquals("1 200 m", NavSubtextParser.dashDistanceLabel("1 200 m"))
        assertEquals(null, NavSubtextParser.dashDistanceLabel("Turn left"))
        assertEquals(null, NavSubtextParser.dashDistanceLabel("2:12 pm"))
        assertEquals(null, NavSubtextParser.dashDistanceLabel(""))
    }

    @Test fun leadingZeroCommaIsADecimalNotThousands() {
        assertEquals(500, NavSubtextParser.distanceMeters("0,5 km"))
        assertEquals(500, NavSubtextParser.distanceMeters("0,500 km"))
    }
}
