package com.navigator.app.ui.feature.diagnostics

enum class LogLevel { Info, Warn, Ok }

/** One log line split into its parts: "[12:30:01.123] [BLE] Connected" becomes time, tag and message. */
data class LogEntry(val time: String, val tag: String, val message: String, val level: LogLevel, val key: String = "")

object LogEntries {
    private val LINE = Regex("""^\[([^\]]*)]\s*\[([^\]]*)]\s?(.*)$""", RegexOption.DOT_MATCHES_ALL)
    private val WARN = Regex("""\bfail(ed|ure)?\b|\berror\b|exception|denied|timed? ?out|\bcrash\b""", RegexOption.IGNORE_CASE)
    private val QUOTED = Regex(""""[^"]*"|<\d+ chars>""")
    private val OK = Regex("""\bauthenticated\b|\bsuccess(ful)?\b|= WORKS\b""", RegexOption.IGNORE_CASE)

    /** A line that doesn't match the logger's format keeps all its text as the message. */
    fun parse(line: String): LogEntry {
        val m = LINE.matchEntire(line) ?: return LogEntry("", "", line, levelOf("", line))
        val tag = m.groupValues[2]
        val message = m.groupValues[3]
        return LogEntry(m.groupValues[1], tag, message, levelOf(tag, message))
    }

    /** The logger's own marker is a leading "!!"; words count only outside quoted user content (a road called "Error Street"). */
    fun levelOf(tag: String, message: String): LogLevel {
        val own = message.replace(QUOTED, "")
        return when {
            tag.equals("CRASH", ignoreCase = true) || message.startsWith("!!") || WARN.containsMatchIn(own) -> LogLevel.Warn
            OK.containsMatchIn(own) -> LogLevel.Ok
            else -> LogLevel.Info
        }
    }

    /** Subsystems A to Z for the filter chips: a fixed order, so chips don't move while a rider reaches for one. */
    fun tagsOf(entries: List<LogEntry>): List<String> = entries.map { it.tag }.filter { it.isNotEmpty() }.distinct().sorted()

    /** [tag] null shows every subsystem; [query] matches the tag or the message, ignoring case. */
    fun filter(entries: List<LogEntry>, tag: String?, query: String): List<LogEntry> {
        val q = query.trim()
        return entries.filter { e ->
            (tag == null || e.tag == tag) &&
                (q.isEmpty() || e.message.contains(q, ignoreCase = true) || e.tag.contains(q, ignoreCase = true))
        }
    }
}
