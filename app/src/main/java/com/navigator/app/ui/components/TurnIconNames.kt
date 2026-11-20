package com.navigator.app.ui.components

import com.navigator.app.ble.BccuProtocol.TurnIcon

/** Plain-words names for dash symbols, for screen readers and the diagnostics screens. */
object TurnIconNames {
    /** "LIGHT_LEFT" becomes "Light left"; roundabout sections read as exits. */
    fun human(enumName: String): String {
        Regex("RAB_SECT_(\\d+)_(RH|LH)").matchEntire(enumName)?.let { return "Roundabout exit ${it.groupValues[1]} (${it.groupValues[2]})" }
        return enumName.lowercase().replace("uturn", "u-turn").replace('_', ' ').replaceFirstChar { it.uppercase() }
    }

    fun human(icon: TurnIcon): String = human(icon.name)
}
