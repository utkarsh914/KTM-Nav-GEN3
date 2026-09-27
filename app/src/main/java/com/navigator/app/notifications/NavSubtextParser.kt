package com.navigator.app.notifications

import java.text.DateFormatSymbols
import java.util.Locale

/**
 * Pure parsing of a nav app's notification strings (JVM-testable):
 * - [parseEtaAndRemaining]: the subtitle line, e.g. "22 min · 9.7 km · 2:12 pm ETA".
 * - [compactDistance]: squeeze a distance label into the dash's 8-char field.
 */
object NavSubtextParser {

    /**
     * ETA as plain zero-padded 24h "HH:MM" (the firmware renders only a strict
     * numeric time) and the remaining-distance segment, or null for either.
     *
     * [amPm] are the meridiem markers to recognise besides English am/pm -
     * the device locale's (e.g. Kannada "ಪೂರ್ವಾಹ್ನ"/"ಅಪರಾಹ್ನ"), which may come
     * before or after the time. Non-ASCII digits are normalised first.
     */
    fun parseEtaAndRemaining(
        subText: String,
        amPm: Pair<String, String> = localeAmPm(Locale.getDefault()),
    ): Pair<String?, String?> {
        if (subText.isBlank()) return null to null
        val text = asciiDigits(subText)
        // Separators: middle dot, bullet, hyphen, en/em dashes, pipe.
        val segments = text.split("·", "•", " - ", "–", "—", "|").map { it.trim() }.filter { it.isNotEmpty() }
        var eta: String? = null
        var distance: String? = null
        for (segment in segments) {
            val isDistance = DISTANCE_RE.matches(segment)
            // Never read a distance ("12.50 km") as a dotted time.
            if (eta == null && !isDistance) eta = parseTime(segment, amPm)
            if (distance == null && isDistance) distance = compactDistance(segment)
        }
        return eta to distance
    }

    private fun parseTime(segment: String, amPm: Pair<String, String>): String? {
        val m = TIME_RE.find(segment) ?: return null
        var hour = m.groupValues[1].toInt()
        val minute = m.groupValues[2]
        if (hour > 23 || minute.toInt() > 59) return null
        val lower = segment.lowercase()
        val isPm = PM_RE.containsMatchIn(lower) || (amPm.second.isNotBlank() && lower.contains(amPm.second.lowercase()))
        val isAm = AM_RE.containsMatchIn(lower) || (amPm.first.isNotBlank() && lower.contains(amPm.first.lowercase()))
        if (isPm && hour < 12) hour += 12
        if (isAm && !isPm && hour == 12) hour = 0
        return "%02d:%s".format(Locale.ROOT, hour, minute)
    }

    /**
     * Shorten unit words so a distance fits the dash's 8-char field without an
     * ellipsis ("0.3 miles" became "0.3 m...", which reads as metres).
     */
    fun compactDistance(text: String): String =
        text.trim()
            .replace(Regex("""\s*\b(miles|mile)\b""", RegexOption.IGNORE_CASE), " mi")
            .replace(Regex("""\s*\b(feet|foot)\b""", RegexOption.IGNORE_CASE), " ft")
            .replace(Regex("""\s*\b(yards|yard|yds)\b""", RegexOption.IGNORE_CASE), " yd")
            .replace(Regex("""\s*\b(kilometres|kilometers|kilometre|kilometer)\b""", RegexOption.IGNORE_CASE), " km")
            .replace(Regex("""\s*\b(metres|meters|metre|meter)\b""", RegexOption.IGNORE_CASE), " m")

    /** Map any Unicode decimal digit (Kannada, Devanagari, Arabic-Indic…) to ASCII. */
    fun asciiDigits(s: String): String = buildString(s.length) {
        for (c in s) append(if (Character.isDigit(c) && c !in '0'..'9') ('0' + Character.digit(c, 10)) else c)
    }

    fun localeAmPm(locale: Locale): Pair<String, String> = runCatching {
        val s = DateFormatSymbols.getInstance(locale).amPmStrings
        s[0] to s[1]
    }.getOrDefault("" to "")

    private val TIME_RE = Regex("""(\d{1,2})[:.](\d{2})(?!\d)""")
    private val AM_RE = Regex("""\ba\.?\s?m\b\.?""")
    private val PM_RE = Regex("""\bp\.?\s?m\b\.?""")
    private val DISTANCE_RE = Regex(
        """\d+([.,]\d+)?\s*(km|mi|m|ft|yd|miles?|feet|yards?|kilometres?|kilometers?|metres?|meters?)\b\.?""",
        RegexOption.IGNORE_CASE,
    )
}
