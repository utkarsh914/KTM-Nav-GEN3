package com.navigator.app.notifications

import com.navigator.app.ble.BccuProtocol.TurnIcon

/** Best-effort maneuver label for the Direction screen, derived from the turn icon. */
object ManeuverText {
    fun forIcon(icon: TurnIcon?): String? {
        val name = icon?.name ?: return null
        // Specific families first: U-turn/highway/roundabout names also contain
        // LEFT/RIGHT, so a plain LEFT/RIGHT check first would mislabel them as turns.
        return when {
            name.contains("UTURN") || name.contains("U_TURN") -> "Make a U-turn onto"
            name.startsWith("ENTER_HIGHWAY") -> "Merge onto"
            name.startsWith("LEAVE_HIGHWAY") -> "Take the exit onto"
            name.startsWith("HIGHWAY_KEEP_") -> if (name.endsWith("LEFT")) "Keep left onto" else "Keep right onto"
            name.startsWith("RAB_SECT") -> "At the roundabout, exit onto"
            name == "KEEP_MIDDLE" -> "Continue straight onto"
            name.startsWith("KEEP_") -> if (name.endsWith("LEFT")) "Keep left onto" else "Keep right onto"
            name.contains("LEFT") -> "Turn left onto"
            name.contains("RIGHT") -> "Turn right onto"
            name.contains("STRAIGHT") || name.contains("CONTINUE") -> "Continue onto"
            else -> null
        }
    }
}
