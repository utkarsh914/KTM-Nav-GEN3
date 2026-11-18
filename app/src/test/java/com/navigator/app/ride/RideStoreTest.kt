package com.navigator.app.ride

import android.content.Context
import org.robolectric.RuntimeEnvironment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RideStoreTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var ctx: Context
    private lateinit var store: RideStore

    @Before fun setUp() {
        ctx = RuntimeEnvironment.getApplication()
        File(ctx.filesDir, "rides").deleteRecursively()
        ctx.getSharedPreferences("ride_store", Context.MODE_PRIVATE).edit().clear().commit()
        store = RideStore(ctx)
        store.setActiveForTest(null)
    }

    private fun pts(n: Int, startMs: Long = 1_000_000L, lng0: Double = 77.0) =
        (0 until n).map { RidePoint(12.0, lng0 + it * 0.0003, startMs + 1_000L * it, 40f) }

    private fun importRide(n: Int = 60, startMs: Long = 1_000_000L): RecordedRide =
        store.importRide("Ride $startMs", pts(n, startMs))!!

    // ---- import / list / delete ----

    @Test fun importedRideIsListedWithRecomputedStats() {
        val r = importRide(60)
        assertEquals(1, store.list().size)
        assertEquals(60, store.points(r.id).size)
        assertEquals(60, store.get(r.id)!!.pointCount)
        assertTrue(r.distanceMeters > 0)
    }

    @Test fun deleteRemovesIndexTrackAndSavedFlag() {
        val r = importRide()
        store.setSaved(r.id, true)
        store.delete(r.id)
        assertTrue(store.list().isEmpty())
        assertFalse(store.trackFile(r.id).exists())
    }

    @Test fun pruneKeepsSavedRidesAndTheNewestUnsaved() {
        val a = importRide(startMs = 1_000_000L)
        val b = importRide(startMs = 2_000_000L)
        val c = importRide(startMs = 3_000_000L)
        // (imported rides start out saved)
        store.setSaved(b.id, false); store.setSaved(c.id, false)
        val pruned = store.pruneToLimit(1)
        assertEquals(listOf(b.id), pruned)
        assertEquals(setOf(a.id, c.id), store.list().map { it.id }.toSet())
    }

    // ---- rename / notes ----

    @Test fun updateMetaCleansAndStoresNameAndNotes() {
        val r = importRide()
        val u = store.updateMeta(r.id, "  Sunday \n ride ", " twisties ")!!
        assertEquals("Sunday ride", u.destinationLabel)
        assertEquals("twisties", store.get(r.id)!!.notes)
        assertNull(store.updateMeta(r.id, "  ", "")!!.destinationLabel)
        assertNull(store.updateMeta("999", "x", "y"))
    }

    // ---- trim ----

    @Test fun trimRewritesTheTrackAndRecomputesStats() {
        val r = importRide(60)
        val t = store.trimRide(r.id, 10, 29)!!
        assertEquals(20, store.points(r.id).size)
        assertEquals(20, t.pointCount)
        assertTrue(t.distanceMeters < r.distanceMeters)
        assertEquals(1, store.list().size)
        assertFalse(File(store.trackFile(r.id).parentFile, "${r.id}.jsonl.tmp").exists())
    }

    @Test fun trimKeepsSavedFlagLabelAndNotes() {
        val r = importRide(60)
        store.setSaved(r.id, true)
        store.updateMeta(r.id, "Keep me", "note")
        val t = store.trimRide(r.id, 0, 30)!!
        assertTrue(t.saved)
        assertEquals("Keep me", t.destinationLabel)
        assertEquals("note", t.notes)
    }

    @Test fun trimRefusesInvalidRangesAndMissingRides() {
        val r = importRide(30)
        assertNull(store.trimRide(r.id, 5, 5))
        assertNull(store.trimRide(r.id, 0, 99))
        assertNull(store.trimRide("12345", 0, 5))
        assertEquals(30, store.points(r.id).size) // untouched
    }

    @Test fun trimRefusesARideStillBeingRecorded() {
        val r = importRide(30)
        store.setActiveForTest(r.id)
        assertNull(store.trimRide(r.id, 0, 10))
        assertEquals(30, store.points(r.id).size)
    }

    @Test fun trimRefusesWhenTheTrackLostLinesComparedToTheIndex() {
        val r = importRide(30)
        // Simulate a damaged track: fewer parseable lines than the index promises.
        val f = store.trackFile(r.id)
        f.writeText(f.readLines().take(10).joinToString("\n") + "\n")
        assertNull(store.trimRide(r.id, 0, 5))
    }

    // ---- backup import ----

    private fun trackCopy(id: String): File {
        val f = File(tmp.root, "$id.jsonl")
        store.trackFile(id).copyTo(f, overwrite = true)
        return f
    }

    @Test fun importBackupAddsOnlyUnknownRidesWithATrackAndRecomputesStats() {
        val have = importRide(40, 1_000_000L)
        val extra = importRide(50, 5_000_000L)
        val haveFile = trackCopy(have.id)
        val extraFile = trackCopy(extra.id)
        store.delete(extra.id)
        // A lying index entry (wrong stats, dirty label) plus one without a track and one duplicate.
        val lying = extra.copy(distanceMeters = 999_999, pointCount = 7, destinationLabel = "  x\ny  ", saved = true)
        val noTrack = extra.copy(id = "777")
        val added = store.importBackup(listOf(have, lying, noTrack), mapOf(have.id to haveFile, extra.id to extraFile))
        assertEquals(1, added)
        val r = store.get(extra.id)!!
        assertEquals(50, r.pointCount)
        assertTrue(r.distanceMeters < 999_999)
        assertEquals("x y", r.destinationLabel)
        assertTrue(r.saved)
        assertNull(store.get("777"))
        assertEquals(2, store.list().size)
        assertEquals(50, store.points(extra.id).size)
    }

    @Test fun importBackupSkipsTracksWithTooFewPoints() {
        val bad = File(tmp.root, "55.jsonl").apply { writeText("not json\n") }
        val r = RecordedRide("55", 1, 2, 100, 10)
        assertEquals(0, store.importBackup(listOf(r), mapOf("55" to bad)))
        assertTrue(store.list().isEmpty())
    }

    // ---- crash recovery ----

    @Test fun orphanTrackBecomesARideWhenItClearsTheCutoffs() {
        val w = store.openTrack("4242", "Dest", 12.1, 77.1)
        pts(80).forEach { store.appendPoint(w, it) }
        w.close()
        store.markTrackClosed("4242")
        store.recoverOrphans(minDistanceMeters = 100, minDurationSeconds = 10)
        val r = store.get("4242")
        assertNotNull(r)
        assertEquals("Dest", r!!.destinationLabel)
        assertEquals(80, r.pointCount)
    }

    @Test fun shortOrphanIsDiscardedAndAnActiveOneIsLeftAlone() {
        val short = store.openTrack("1", null, null, null)
        pts(3).forEach { store.appendPoint(short, it) }
        short.close(); store.markTrackClosed("1")
        val live = store.openTrack("2", null, null, null) // still being recorded
        pts(80).forEach { store.appendPoint(live, it) }
        live.flush()
        store.recoverOrphans(minDistanceMeters = 100, minDurationSeconds = 10)
        assertFalse(store.trackFile("1").exists())
        assertTrue(store.trackFile("2").exists())
        assertNull(store.get("2"))
        live.close()
    }

    @Test fun leftoverTrimTempFilesAreSweptOnRecovery() {
        val tmpFile = File(store.trackFile("9").parentFile, "9.jsonl.tmp").apply { writeText("x") }
        store.recoverOrphans(100, 10)
        assertFalse(tmpFile.exists())
    }

    // ---- duplicates ----

    @Test fun findDuplicateMatchesSameStartAndDistance() {
        val p = pts(60, 7_000_000L)
        store.importRide("A", p)
        assertNotNull(store.findDuplicate(p))
        assertNull(store.findDuplicate(pts(60, 9_000_000L)))
        assertNull(store.findDuplicate(pts(60, 7_000_000L, lng0 = 80.0).take(1)))
    }
}
