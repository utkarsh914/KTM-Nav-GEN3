package com.navigator.app.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class SystemBarIconsTest {
    @Test fun theAppThemeOwnsTheBarsUntilARidingSurfaceOverridesThem() {
        val riding = Any()
        assertEquals(false, SystemBarIcons.resolve(appDark = false))
        SystemBarIcons.push(riding, dark = true)
        try {
            assertEquals("riding night wins over a light app", true, SystemBarIcons.resolve(appDark = false))
            assertEquals("and over repeated app-theme applies", true, SystemBarIcons.resolve(appDark = false))
            SystemBarIcons.push(riding, dark = false)
            assertEquals("a day change updates the same override", false, SystemBarIcons.resolve(appDark = true))
        } finally {
            SystemBarIcons.pop(riding)
        }
        assertEquals("leaving riding hands the bars back", true, SystemBarIcons.resolve(appDark = true))
    }

    @Test fun theNewestOverrideWinsAndPoppingOneKeepsTheOther() {
        val a = Any(); val b = Any()
        SystemBarIcons.push(a, true); SystemBarIcons.push(b, false)
        assertEquals(false, SystemBarIcons.resolve(true))
        SystemBarIcons.pop(b)
        assertEquals(true, SystemBarIcons.resolve(false))
        SystemBarIcons.pop(a)
        assertEquals(false, SystemBarIcons.resolve(false))
    }
}
