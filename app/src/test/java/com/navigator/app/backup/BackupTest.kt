package com.navigator.app.backup

import com.navigator.app.data.settings.SettingSpecs
import com.navigator.app.nav.destination.FavoritePlace
import com.navigator.app.nav.destination.FavoriteSlot
import com.navigator.app.nav.destination.PlacesMerge
import com.navigator.app.nav.destination.SavedPlace
import com.navigator.app.ride.RecordedRide
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BackupTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun ride(id: String) = RecordedRide(id, 0, 1, 1000, 100, destinationLabel = "R$id", notes = "n$id", saved = true)

    @Test fun archiveRoundTripKeepsRidesTracksPlacesAndPrefs() {
        val src = tmp.newFolder("src")
        val rides = listOf(ride("111"), ride("222"))
        File(src, "111.jsonl").writeText("{\"lat\":1}\n")
        val out = ByteArrayOutputStream()
        val written = BackupArchive.write(out, rides, { id -> File(src, "$id.jsonl") }, """{"recents":"[]"}""", """{"a":1}""", 123L)
        assertEquals(1, written) // ride 222 has no track file: still listed, no track entry
        val c = BackupArchive.read(ByteArrayInputStream(out.toByteArray()), tmp.newFolder("tmp"))!!
        assertEquals(listOf("111", "222"), c.rides.map { it.id })
        assertEquals("n111", c.rides[0].notes)
        assertTrue(c.rides[0].saved)
        assertEquals(setOf("111"), c.tracks.keys)
        assertEquals("{\"lat\":1}\n", c.tracks["111"]!!.readText())
        assertEquals("""{"recents":"[]"}""", c.placesJson)
        assertEquals("""{"a":1}""", c.prefsJson)
    }

    private fun zip(vararg entries: Pair<String, String>): ByteArray {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { z -> entries.forEach { (n, b) -> z.putNextEntry(ZipEntry(n)); z.write(b.toByteArray()); z.closeEntry() } }
        return bos.toByteArray()
    }

    @Test fun nonBackupsAreRejected() {
        val dir = tmp.newFolder("t")
        assertNull(BackupArchive.read(ByteArrayInputStream("not a zip".toByteArray()), dir))
        assertNull(BackupArchive.read(ByteArrayInputStream(zip("rides.json" to "[]")), dir)) // no manifest
        assertNull(BackupArchive.read(ByteArrayInputStream(zip("manifest.json" to """{"format":99}""")), dir))
    }

    @Test fun hostileEntryNamesAreNeverWritten() {
        val dir = tmp.newFolder("t")
        val data = zip(
            "manifest.json" to """{"format":1}""",
            "../../evil.txt" to "x",
            "tracks/../../evil.jsonl" to "x",
            "tracks/abc.jsonl" to "x",
            "tracks/123.jsonl" to "ok",
        )
        val c = BackupArchive.read(ByteArrayInputStream(data), dir)!!
        assertEquals(setOf("123"), c.tracks.keys)
        assertEquals(setOf("123.jsonl"), dir.list()!!.toSet())
        assertFalse(File(dir, "../evil.txt").exists())
    }

    @Test fun badRideEntriesAreSkipped() {
        val c = BackupArchive.read(
            ByteArrayInputStream(zip("manifest.json" to """{"format":1}""",
                "rides.json" to """[{"id":"5","startMs":1},{"id":"../x"},{"id":""},"junk",{"id":"5"}]""")),
            tmp.newFolder("t"),
        )!!
        assertEquals(listOf("5"), c.rides.map { it.id })
    }

    @Test fun oversizedTracksAreRefused() {
        // 51 MB of zeros compresses to almost nothing: the cap is on the UNCOMPRESSED size.
        val big = ByteArray((BackupArchive.MAX_TRACK_BYTES + 1024).toInt())
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { z ->
            z.putNextEntry(ZipEntry("manifest.json")); z.write("""{"format":1}""".toByteArray()); z.closeEntry()
            z.putNextEntry(ZipEntry("tracks/1.jsonl")); z.write(big); z.closeEntry()
        }
        assertNull(BackupArchive.read(ByteArrayInputStream(bos.toByteArray()), tmp.newFolder("t")))
    }

    // ---- prefs ----

    @Test fun pairingOnboardingAndUnknownKeysNeverLeaveThePhone() {
        val all = mapOf<String, Any?>(
            "theme_mode" to "dark", "bonded_device_address" to "AA:BB", "bonded_device_name" to "KTM",
            "paired_before_AA" to true, "session_keys_AA" to "deadbeef", "onboarding_complete" to true,
            "beep_volume_percent" to 40, "notification_source_apps" to setOf("a", "b"),
            "call_audio_device" to "AA:BB:CC", "something_else" to "x", "obj" to Any(),
        )
        val back = PrefsBackup.decode(PrefsBackup.encode(all))
        assertEquals(setOf("theme_mode", "beep_volume_percent", "notification_source_apps"), back.keys)
        assertEquals("dark", back["theme_mode"]); assertEquals(40, back["beep_volume_percent"])
        assertEquals(setOf("a", "b"), back["notification_source_apps"])
    }

    @Test fun aRestoreReplacesSoKeysTheFileLacksReturnToTheirDefaults() {
        val entries = PrefsBackup.decode(PrefsBackup.encode(mapOf<String, Any?>("theme_mode" to "dark")))
        val absent = PrefsBackup.absentFrom(entries)
        assertFalse("theme_mode" in absent)
        assertTrue("overspeed_limit_kmh" in absent)
        assertTrue("the pairing is never touched", absent.none { PrefsBackup.isExcluded(it) })
    }

    @Test fun aWrongTypedValueForAKnownKeyIsNeverRestored() {
        // The app's getters throw ClassCastException on a type mismatch, which would crash every launch.
        val json = """{"beep_volume_percent":{"t":"s","v":"loud"},"theme_mode":{"t":"i","v":3},
            "mirror_enabled":{"t":"s","v":"yes"},"notification_source_apps":{"t":"s","v":"x"},
            "live_updates_enabled":{"t":"b","v":false}}"""
        assertEquals(mapOf<String, Any>("live_updates_enabled" to false), PrefsBackup.decode(json))
    }

    @Test fun numbersAreClampedAndStringsCapped() {
        val json = """{"beep_volume_percent":{"t":"i","v":100000},"overspeed_limit_kmh":{"t":"i","v":-5},
            "speed_band_count":{"t":"i","v":2000000},"user_name":{"t":"s","v":"${"n".repeat(500)}"}}"""
        val back = PrefsBackup.decode(json)
        assertEquals(100, back["beep_volume_percent"])
        assertEquals(30, back["overspeed_limit_kmh"])
        assertEquals(20, back["speed_band_count"])
        assertEquals(SettingSpecs.MAX_USER_NAME, (back["user_name"] as String).length)
    }

    @Test fun hostileOrBrokenPrefsAreFilteredOnDecode() {
        val json = """{"bonded_device_address":{"t":"s","v":"X"},"onboarding_complete":{"t":"b","v":false},
            "ride_history_limit":{"t":"i","v":50},"bad":{"t":"i","v":"nope"},"weird":{"t":"zz","v":1},"str":"plain"}"""
        assertEquals(mapOf<String, Any>("ride_history_limit" to 50), PrefsBackup.decode(json))
        assertTrue(PrefsBackup.decode("garbage").isEmpty())
        assertTrue(PrefsBackup.decode(null).isEmpty())
    }

    @Test fun calibrationFamiliesAreStringsAndCapped() {
        val sb = StringBuilder("{")
        repeat(800) { sb.append("\"turncal_$it\":{\"t\":\"s\",\"v\":\"LEFT\"},") }
        sb.append("\"turncal_bad\":{\"t\":\"i\",\"v\":1}}")
        val back = PrefsBackup.decode(sb.toString())
        assertEquals(500, back.size)
        assertFalse("turncal_bad" in back.keys)
    }

    // ---- places ----

    private fun sp(n: Int) = SavedPlace(10.0 + n, 20.0 + n, "P$n")
    private fun fav(n: Int, slot: FavoriteSlot = FavoriteSlot.OTHER) = FavoritePlace(sp(n), slot)

    @Test fun favoritesMergeKeepsExistingAndFillsEmptySlotsOnly() {
        val existing = listOf(fav(1), fav(2, FavoriteSlot.HOME))
        val incoming = listOf(fav(1), fav(3), fav(4, FavoriteSlot.HOME), fav(5, FavoriteSlot.WORK))
        val m = PlacesMerge.favorites(existing, incoming)
        assertEquals(listOf(fav(1), fav(2, FavoriteSlot.HOME), fav(3), fav(5, FavoriteSlot.WORK)), m)
    }

    @Test fun recentsMergeDedupesCapsAndKeepsExistingFirst() {
        val m = PlacesMerge.recents(listOf(sp(1), sp(2)), listOf(sp(2), sp(3), sp(4)), 3)
        assertEquals(listOf(sp(1), sp(2), sp(3)), m)
        assertNotNull(m)
    }

    @Test fun peekCountsTracksWithoutExtractingThem() {
        val src = tmp.newFolder("peeksrc")
        File(src, "111.jsonl").writeText("{\"lat\":1}\n")
        val out = ByteArrayOutputStream()
        BackupArchive.write(out, listOf(ride("111"), ride("222")), { id -> File(src, "$id.jsonl") }, "{}", "{}", 1L)
        val dir = File(tmp.root, "peekdir")
        val c = BackupArchive.read(ByteArrayInputStream(out.toByteArray()), dir, withTracks = false)!!
        assertEquals(setOf("111"), c.trackIds)
        assertTrue(c.tracks.isEmpty())
        assertFalse(dir.exists()) // nothing was written
    }

    @Test fun aBackupFromBeforeTheFullGoogleUiWasRetiredStillRestoresTheRest() {
        assertEquals(
            mapOf<String, Any>("theme_mode" to "dark"),
            PrefsBackup.decode("""{"full_sdk_nav_ui":{"t":"b","v":true},"theme_mode":{"t":"s","v":"dark"}}"""),
        )
    }

    @Test fun retiredBrandKeyIsNeitherBackedUpNorRestored() {
        val all = mapOf("brand" to "husqvarna", "theme_mode" to "dark")
        assertEquals(mapOf<String, Any>("theme_mode" to "dark"), PrefsBackup.decode(PrefsBackup.encode(all)))
        assertEquals(
            mapOf<String, Any>("theme_mode" to "dark"),
            PrefsBackup.decode("""{"brand":{"t":"s","v":"husqvarna"},"theme_mode":{"t":"s","v":"dark"}}"""),
        )
    }
}
