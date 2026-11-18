package com.navigator.app.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacySettingsMigrationTest {

    @Test fun plainValuesWinAndOnlyMissingKeysAreCopied() {
        val legacy = mapOf<String, Any?>("a" to true, "b" to "old", "c" to 3)
        val plan = LegacySettingsMigration.planCopy(legacy, existingKeys = setOf("b"))
        assertEquals(mapOf<String, Any>("a" to true, "c" to 3), plan)
    }

    @Test fun allStorableTypesSurviveAndNullsAndOddTypesAreSkipped() {
        val legacy = mapOf<String, Any?>(
            "bool" to false, "int" to 1, "long" to 2L, "float" to 1.5f, "str" to "x",
            "set" to setOf("p", "q"), "nul" to null, "badset" to setOf(1, 2), "obj" to Any(),
        )
        val plan = LegacySettingsMigration.planCopy(legacy, emptySet())
        assertEquals(setOf("bool", "int", "long", "float", "str", "set"), plan.keys)
        assertEquals(setOf("p", "q"), plan["set"])
    }

    @Test fun planIsEmptyWhenNothingToMigrate() {
        assertTrue(LegacySettingsMigration.planCopy(emptyMap(), setOf("x")).isEmpty())
    }

    // ---- runMigration ---------------------------------------------------------

    private fun ok(vararg e: Pair<String, Any?>) = LegacySettingsMigration.LegacyRead(mapOf(*e), ok = true)
    private fun failed() = LegacySettingsMigration.LegacyRead(emptyMap(), ok = false)
    private fun done(p: FakePrefs) = p.getBoolean(LegacySettingsMigration.KEY_DONE, false)

    @Test fun copiesAndMarksDone() {
        val plain = FakePrefs(mapOf("theme" to "dark"))
        LegacySettingsMigration.runMigration(plain, log = {}) {
            ok("onboarding_complete" to true, "user_name" to "Rider", "theme" to "legacy",
                "apps" to setOf("a", "b"), "vol" to 0.5f)
        }
        assertTrue(done(plain))
        assertEquals(true, plain.getBoolean("onboarding_complete", false))
        assertEquals("Rider", plain.getString("user_name", null))
        assertEquals("dark", plain.getString("theme", null)) // plain wins
        assertEquals(setOf("a", "b"), plain.getStringSet("apps", null))
        assertEquals(0.5f, plain.getFloat("vol", 0f), 0f)
        assertEquals(4, plain.getInt(LegacySettingsMigration.KEY_COUNT, -1))
    }

    @Test fun emptyLegacyIsDoneWithZeroCount() {
        val plain = FakePrefs()
        LegacySettingsMigration.runMigration(plain, log = {}) { ok() }
        assertTrue(done(plain))
        assertEquals(0, plain.getInt(LegacySettingsMigration.KEY_COUNT, -1))
    }

    @Test fun unreadableLegacyIsRetriedAndOnlyGivenUpAfterManyAttempts() {
        val plain = FakePrefs()
        repeat(LegacySettingsMigration.MAX_ATTEMPTS - 1) {
            LegacySettingsMigration.runMigration(plain, log = {}) { failed() }
            assertFalse(done(plain))
        }
        assertEquals(LegacySettingsMigration.MAX_ATTEMPTS - 1, plain.getInt(LegacySettingsMigration.KEY_ATTEMPTS, 0))
        LegacySettingsMigration.runMigration(plain, log = {}) { failed() }
        assertTrue(done(plain))
    }

    @Test fun aSourceThatThrowsCountsAsFailure() {
        val plain = FakePrefs()
        LegacySettingsMigration.runMigration(plain, log = {}) { throw IllegalStateException("keystore") }
        assertFalse(done(plain))
    }

    @Test fun failedWriteLeavesItNotDoneSoItRetries() {
        val plain = FakePrefs(failCommits = true)
        LegacySettingsMigration.runMigration(plain, log = {}) { ok("user_name" to "Rider") }
        assertFalse(done(plain))
        plain.failCommits = false
        LegacySettingsMigration.runMigration(plain, log = {}) { ok("user_name" to "Rider") }
        assertTrue(done(plain))
        assertEquals("Rider", plain.getString("user_name", null))
    }

    @Test fun laterRecoveryCopiesWhatEarlierFailureMissed() {
        val plain = FakePrefs()
        LegacySettingsMigration.runMigration(plain, log = {}) { failed() }
        LegacySettingsMigration.runMigration(plain, log = {}) { ok("user_name" to "Rider") }
        assertTrue(done(plain))
        assertEquals("Rider", plain.getString("user_name", null))
    }

    @Test fun summaryReflectsState() {
        val plain = FakePrefs()
        assertTrue(LegacySettingsMigration.summary(plain).startsWith("pending"))
        LegacySettingsMigration.runMigration(plain, log = {}) { ok("a" to 1) }
        assertEquals("migrated (1 keys)", LegacySettingsMigration.summary(plain))
    }
}
