package com.navigator.app.ble

/**
 * Free text for the dash (road names, secondary maneuver text, banner messages) in a script its font
 * can show. The dash font is believed to be Latin-only (BACKLOG): a Kannada / Devanagari / Arabic road
 * name would arrive as blanks or boxes, so it is transliterated to plain Latin first. Labels that
 * carry localised UNITS (turn distance, remaining distance, ETA) are deliberately NOT touched:
 * "110 मी" must never become "110 mi". Setting "Latin text on dash" turns this off.
 *
 * The transliterator (ICU) is injected at app start so the logic stays JVM-testable; without one,
 * text passes through unchanged.
 */
object DashText {

    @Volatile var enabled: Boolean = true
    @Volatile var transliterate: ((String) -> String)? = null

    // Latin letters incl. extended, punctuation, digits: what the dash is known to handle.
    private fun isDashSafe(c: Char): Boolean =
        c.code <= 0x024F || c.code in 0x2000..0x206F || c.isWhitespace()

    private val ROAD_REF = Regex("""\b(NH|SH|AH|MDR|ODR|VR)[- ]?\d{1,4}[A-Z]?\b""", RegexOption.IGNORE_CASE)

    /** [text] unchanged when it is already dash-safe (or the feature is off / unavailable). */
    fun forDash(text: String): String {
        val t = transliterate
        if (!enabled || t == null || text.all { isDashSafe(it) }) return text
        // A failing transliterator must not hide the road name: send it as before.
        val raw = runCatching { t(text) }.getOrNull() ?: return text
        val latin = raw
            .filter { isDashSafe(it) && (it == '\n' || it.code >= 0x20) }
            .replace(Regex("\\s+"), " ").trim()
        if (latin.count { it.isLetterOrDigit() } >= 2) return latin
        // Nothing usable came out: fall back to a road reference ("NH 48") if the text had one.
        return ROAD_REF.find(text)?.value?.uppercase()?.replace(Regex("[- ]"), " ") ?: ""
    }

    /** ICU "Any-Latin; Latin-ASCII" when the platform has it, else null (pass-through). */
    fun icuTransliterator(): ((String) -> String)? = runCatching {
        val tr = android.icu.text.Transliterator.getInstance("Any-Latin; Latin-ASCII")
        val f: (String) -> String = { s -> tr.transliterate(s) }
        f
    }.getOrNull()
}
