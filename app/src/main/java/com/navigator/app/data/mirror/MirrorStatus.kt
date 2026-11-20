package com.navigator.app.data.mirror

import com.navigator.app.ble.BccuProtocol.TurnIcon
import com.navigator.app.notifications.MirrorHints
import com.navigator.app.notifications.NotificationRepository

/** What the mirror screen reads, gathered in one place so the mapping is a pure function. */
data class MirrorInputs(
    val accessGranted: Boolean,
    val listenerConnected: Boolean,
    val batteryUnrestricted: Boolean,
    val paused: Boolean,
    val guidance: NotificationRepository.NavGuidance?,
    val navText: String?,
    val navPackage: String?,
    val lastUpdateMs: Long,
    val nowMs: Long,
)

enum class MirrorSetupKind { Access, Listener, Battery }

/** One line of the setup checklist (MR1). Battery is a recommendation: it never blocks Ready. */
data class MirrorSetupRow(val kind: MirrorSetupKind, val done: Boolean, val required: Boolean)

/** How long ago guidance last arrived, for the header ("Live · 2 s ago") and the stale banner. */
sealed interface Freshness {
    data object JustNow : Freshness
    data class Seconds(val n: Int) : Freshness
    data class Minutes(val n: Int) : Freshness

    companion object {
        fun of(ageMs: Long): Freshness {
            val s = (ageMs / 1000).coerceAtLeast(0).toInt()
            return when {
                s < 3 -> JustNow
                s < 60 -> Seconds(s)
                else -> Minutes(s / 60)
            }
        }
    }
}

/** The turn as the dash gets it: a measured distance, or a text instruction in its place. */
data class MirrorTurn(
    val icon: TurnIcon?,
    /** "400 m", or null when the nav app gave a sentence instead of a distance. */
    val distance: String?,
    /** The instruction line: the maneuver, or the sentence that replaces the distance. */
    val instruction: String?,
    val road: String?,
    val eta: String?,
    val remaining: String?,
) {
    companion object {
        private val DISTANCE = Regex(
            """^\s*[\d.,]+\s*(m|km|ft|mi|meters?|kilomet[re]*s?|feet|miles?)\b""", RegexOption.IGNORE_CASE,
        )

        fun looksLikeDistance(s: String?): Boolean = s != null && DISTANCE.containsMatchIn(s)

        fun from(g: NotificationRepository.NavGuidance?, navText: String?): MirrorTurn {
            val first = g?.distance?.takeIf { it.isNotBlank() }
            val maneuver = g?.maneuver?.takeIf { it.isNotBlank() }
            val road = g?.road?.takeIf { it.isNotBlank() }
            val measured = looksLikeDistance(first)
            val instruction = if (measured) maneuver else (first ?: navText?.takeIf { it.isNotBlank() })
            return MirrorTurn(
                icon = g?.turnIcon,
                distance = if (measured) first else null,
                instruction = instruction,
                road = road?.takeUnless { !measured && instruction != null && instruction.contains(it, ignoreCase = true) },
                eta = g?.eta?.takeIf { it.isNotBlank() },
                remaining = g?.remaining?.takeIf { it.isNotBlank() },
            )
        }
    }
}

sealed interface MirrorStatus {
    /** Something required is missing: the checklist (all rows, done ones green). */
    data class Setup(val rows: List<MirrorSetupRow>) : MirrorStatus

    /** Set up, no guidance yet (or paused, which clears it). [rows] are shown on tap ("Ready" line); the battery tip is among them. */
    data class Ready(val rows: List<MirrorSetupRow>, val paused: Boolean = false) : MirrorStatus

    /** Guidance is coming through. [stale]: nothing for 30 s while sending. */
    data class Live(val turn: MirrorTurn, val freshness: Freshness, val paused: Boolean, val stale: Boolean) : MirrorStatus
}

object MirrorStatusMapper {
    fun map(i: MirrorInputs): MirrorStatus {
        val rows = listOf(
            MirrorSetupRow(MirrorSetupKind.Access, i.accessGranted, required = true),
            MirrorSetupRow(MirrorSetupKind.Listener, i.accessGranted && i.listenerConnected, required = true),
            MirrorSetupRow(MirrorSetupKind.Battery, i.batteryUnrestricted, required = false),
        )
        if (rows.any { it.required && !it.done }) return MirrorStatus.Setup(rows)
        val hasNav = i.navPackage != null && (i.guidance != null || i.navText != null)
        // Pausing clears what Google Maps sent, so a paused screen with nothing yet still offers the switch to resume.
        if (!hasNav) return MirrorStatus.Ready(rows, i.paused)
        val age = if (i.lastUpdateMs > 0L) (i.nowMs - i.lastUpdateMs).coerceAtLeast(0L) else 0L
        val stale = MirrorHints.compute(
            accessGranted = true, listenerConnected = true, paused = i.paused, hasNav = true, ageMs = age,
            ignoringBatteryOptimizations = true,
        ).contains(MirrorHints.Kind.STALE)
        return MirrorStatus.Live(MirrorTurn.from(i.guidance, i.navText), Freshness.of(age), i.paused, stale)
    }
}
