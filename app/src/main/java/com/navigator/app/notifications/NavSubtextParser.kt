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
        val text = asciiDigits(normalizeSpaces(subText))
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
        val isPm = PM_RE.containsMatchIn(lower) || containsToken(lower, amPm.second)
        val isAm = AM_RE.containsMatchIn(lower) || containsToken(lower, amPm.first)
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

    /** Whole-token match (not inside a longer word, e.g. "am" in "Amsterdam"). */
    private fun containsToken(lowerText: String, marker: String): Boolean {
        if (marker.isBlank()) return false
        return Regex("(?<!\\p{L})" + Regex.escape(marker.lowercase()) + "(?!\\p{L})").containsMatchIn(lowerText)
    }

    /** NBSP / narrow NBSP (U+202F, used by newer ICU before am/pm) and friends -> plain space. */
    fun normalizeSpaces(s: String): String =
        s.replace('\u00A0', ' ').replace('\u202F', ' ').replace('\u2009', ' ').replace('\u2007', ' ')

    /**
     * The title as a dash distance label, or null when it clearly isn't one. Deliberately
     * lenient about UNITS (localised ones like "110 मी" / "1 200 m" must still reach the dash):
     * it only has to start with a digit, carry no clock colon and fit the 8-char field's
     * neighbourhood. Latin-unit labels are compacted ("0.3 miles" -> "0.3 mi").
     */
    fun dashDistanceLabel(title: String): String? {
        val t = compactDistance(asciiDigits(normalizeSpaces(title))).trim()
        if (t.isEmpty()) return null
        if (isDistance(t)) return t
        return t.takeIf { it.first().isDigit() && ':' !in it && it.length <= 10 }
    }

    /** True when [text] is entirely a distance label such as "110 m" or "1.2 km". */
    fun isDistance(text: String): Boolean = DISTANCE_RE.matches(asciiDigits(normalizeSpaces(text)).trim())

    /**
     * Metres for a leading distance such as "110 m", "1.2 km", "1,200 m", "0.3 miles",
     * "500 yd"; null when the text doesn't START with a distance (a road name that happens
     * to contain "5 m" in the middle must not count).
     */
    fun distanceMeters(text: String?): Int? {
        if (text.isNullOrBlank()) return null
        val m = LEADING_DISTANCE_RE.find(asciiDigits(normalizeSpaces(text))) ?: return null
        val unit = m.groupValues[2].lowercase()
        val value = parseNumber(m.groupValues[1], unit) ?: return null
        return when {
            unit.startsWith("k") -> (value * 1000).toInt()
            unit.startsWith("mi") -> (value * 1609.344).toInt()
            unit.startsWith("y") -> (value * 0.9144).toInt()
            unit.startsWith("f") -> (value * 0.3048).toInt()
            else -> value.toInt()
        }
    }

    private fun parseNumber(raw: String, unit: String): Double? {
        val s = raw.trimEnd('.', ',')
        val hasComma = ',' in s
        val hasDot = '.' in s
        val normalized = when {
            hasComma && hasDot -> {
                // The LAST separator is the decimal one; the other is a thousands separator.
                val decimalIsDot = s.lastIndexOf('.') > s.lastIndexOf(',')
                if (decimalIsDot) s.replace(",", "") else s.replace(".", "").replace(',', '.')
            }
            hasComma -> if (THOUSANDS_COMMA_RE.matches(s) && !s.startsWith("0")) s.replace(",", "") else s.replace(',', '.')
            hasDot -> when {
                s.count { it == '.' } > 1 -> s.replace(".", "")
                // "1.200 m" = 1200 m, but "1.200 km" is 1.2 km.
                THOUSANDS_DOT_RE.matches(s) && !unit.startsWith("k") && !unit.startsWith("mi") -> s.replace(".", "")
                else -> s
            }
            else -> s
        }
        return normalized.toDoubleOrNull()
    }

    /** Map any Unicode decimal digit (Kannada, Devanagari, Arabic-Indic…) to ASCII. */
    fun asciiDigits(s: String): String = buildString(s.length) {
        for (c in s) append(if (Character.isDigit(c) && c !in '0'..'9') ('0' + Character.digit(c, 10)) else c)
    }

    fun localeAmPm(locale: Locale): Pair<String, String> = runCatching {
        val s = DateFormatSymbols.getInstance(locale).amPmStrings
        s[0] to s[1]
    }.getOrDefault("" to "")

    private val THOUSANDS_COMMA_RE = Regex("""\d{1,3}(,\d{3})+""")
    private val THOUSANDS_DOT_RE = Regex("""\d{1,3}\.\d{3}""")
    private val LEADING_DISTANCE_RE = Regex(
        """^\s*(\d[\d.,]*)\s*(km|kilomet\w*|miles?|mi|metres?|meters?|m|feet|foot|ft|yards?|yd)(?!\p{L})""",
        RegexOption.IGNORE_CASE,
    )
    private val TIME_RE = Regex("""(\d{1,2})[:.](\d{2})(?!\d)""")
    private val AM_RE = Regex("""\ba\.?\s?m\b\.?""")
    private val PM_RE = Regex("""\bp\.?\s?m\b\.?""")
    private val DISTANCE_RE = Regex(
        """\d+([.,]\d+)?\s*(km|mi|m|ft|yd|miles?|feet|yards?|kilometres?|kilometers?|metres?|meters?)\b\.?""",
        RegexOption.IGNORE_CASE,
    )
}
