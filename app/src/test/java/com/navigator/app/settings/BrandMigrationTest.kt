package com.navigator.app.settings

import android.content.Context
import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BrandMigrationTest {
    private lateinit var prefs: SharedPreferences
    private lateinit var settings: AppSettings

    @Before fun setUp() {
        val ctx: Context = RuntimeEnvironment.getApplication()
        prefs = ctx.getSharedPreferences("opendash_pairing", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        settings = AppSettings(ctx)
    }

    @Test fun husqvarnaWithoutAccentKeepsBlue() {
        prefs.edit().putString("brand", "husqvarna").commit()
        settings.migrateRetiredBrand()
        assertEquals("blue", settings.accentColorId)
        assertFalse(prefs.contains("brand"))
    }

    @Test fun husqvarnaWithAnAccentKeepsIt() {
        prefs.edit().putString("brand", "husqvarna").putString("accent_color", "teal").commit()
        settings.migrateRetiredBrand()
        assertEquals("teal", settings.accentColorId)
        assertFalse(prefs.contains("brand"))
    }

    @Test fun ktmJustDropsTheKey() {
        prefs.edit().putString("brand", "ktm").commit()
        settings.migrateRetiredBrand()
        assertNull(settings.accentColorId)
        assertFalse(prefs.contains("brand"))
    }

    @Test fun noBrandKeyIsANoOp() {
        settings.migrateRetiredBrand()
        assertNull(settings.accentColorId)
    }
}
