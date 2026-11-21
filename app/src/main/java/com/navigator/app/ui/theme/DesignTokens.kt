package com.navigator.app.ui.theme

import java.time.LocalTime

/** How the app chooses between the light and dark neutral palettes. */
enum class ThemeMode(val id: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    companion object {
        fun fromId(id: String?): ThemeMode = entries.firstOrNull { it.id == id } ?: SYSTEM
    }
}

/**
 * How riding surfaces choose light/dark, independent of [ThemeMode]: follow the app theme, or
 * switch by day and night. Shown to the rider as "Riding screens".
 */
enum class NavThemeMode(val id: String) {
    APP("app"),
    DAY_NIGHT("day_night");

    companion object {
        fun fromId(id: String?): NavThemeMode = entries.firstOrNull { it.id == id } ?: DAY_NIGHT
    }
}

/** True when the local wall-clock time is "night" for nav theming (18:00–06:00). */
fun isNightTime(hourOfDay: Int = LocalTime.now().hour): Boolean =
    hourOfDay < 6 || hourOfDay >= 18
