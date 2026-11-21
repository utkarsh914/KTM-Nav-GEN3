package com.navigator.app.ride

import android.content.Context
import org.robolectric.RuntimeEnvironment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.After
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

    private fun mergedOf(r: MergeResult): MergeResult.Merged = r as? MergeResult.Merged ?: error("the merge was refused: $r")

    @Before fun setUp() {
        ctx = RuntimeEnvironment.getApplication()
        File(ctx.filesDir, "rides").deleteRecursively()
        ctx.getSharedPreferences("ride_store", Context.MODE_PRIVATE).edit().clear().commit()
        store = RideStore(ctx)
        store.resetStaticsForTest()
    }

    @After fun tearDown() = store.resetStaticsForTest()

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

    @Test fun trimRefusesWhenTheTrackIsNotTheOneTheIndicesWereChosenOn() {
        val r = importRide(30)
        assertNull("the page saw 40 fixes", store.trimRide(r.id, 5, 20, expectedCount = 40))
        assertEquals(30, store.points(r.id).size)
        assertEquals(16, store.trimRide(r.id, 5, 20, expectedCount = 30)!!.pointCount)
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

    // ---- B12: source, soft delete, merge ----

    @Test fun importedRidesAreSourcedGpxAndOldIndexEntriesAreInferred() {
        val r = importRide()
        assertEquals(RideSource.GPX, r.source)
        store.finalizeRide(RecordedRide("1000", 1000, 61_000, 500, 60, destLat = 1.0, destLng = 2.0))
        assertEquals(RideSource.NAV, store.get("1000")!!.source)
    }

    @Test fun anOrphanKeepsItsSourceFromTheMetaLine() {
        val id = "5000000"
        store.trackFile(id).writeText(
            RideJson.metaLine(null, null, null, RideSource.MIRROR) + "\n" +
                pts(60, 5_000_000L).joinToString("\n") { RideJson.pointLine(it) } + "\n",
        )
        store.recoverOrphans(10, 10)
        assertEquals(RideSource.MIRROR, (store.get(id) ?: error("the orphan was not recovered; recovered=${store.list().map { it.id }}")).source)
    }

    @Test fun softDeleteHidesRestoreBringsBackAndPurgeRemoves() {
        val a = importRide(startMs = 1_000_000L); val b = importRide(startMs = 2_000_000L)
        val before = store.changes.value
        assertEquals(listOf(a.id), store.softDelete(listOf(a.id), nowMs = 100))
        assertTrue(store.changes.value > before)
        assertEquals(listOf(b.id), store.list().map { it.id })
        assertNull(store.get(a.id))
        assertTrue("the track waits for the undo", store.trackFile(a.id).exists())
        assertEquals(listOf(a.id), store.listDeleted().map { it.id })
        assertEquals(listOf(a.id), store.restore(listOf(a.id)))
        assertEquals(2, store.list().size)
        store.softDelete(listOf(a.id), nowMs = 100)
        assertTrue(store.purgeDeleted(olderThanMs = 100).isEmpty())
        assertEquals(listOf(a.id), store.purgeDeleted(olderThanMs = 101))
        assertFalse(store.trackFile(a.id).exists())
        assertTrue(store.restore(listOf(a.id)).isEmpty())
    }

    @Test fun aDeletedRideIsNotAnOrphanAndIgnoredByPruneAndDuplicates() {
        val a = importRide(startMs = 1_000_000L)
        store.setSaved(a.id, false)
        store.softDelete(listOf(a.id))
        store.recoverOrphans(10, 10)
        assertEquals("not recovered as a second ride", 0, store.list().size)
        assertEquals(1, store.listDeleted().size)
        assertNull(store.findDuplicate(pts(60, 1_000_000L)))
        val b = importRide(startMs = 2_000_000L); store.setSaved(b.id, false)
        val c = importRide(startMs = 3_000_000L); store.setSaved(c.id, false)
        assertEquals("only the live unsaved rides count", listOf(b.id), store.pruneToLimit(1))
        assertEquals(1, store.listDeleted().size)
    }

    @Test fun aMergeThatDiedBeforeTheIndexIsRolledBack() {
        val a = importRide(startMs = 1_000_000L); val b = importRide(startMs = 2_000_000L)
        val id = "7000000"
        store.trackFile(id).writeText(
            RideJson.metaLine("x", null, null, RideSource.MERGED) + "\n" +
                (pts(60, 1_000_000L) + pts(60, 2_000_000L)).joinToString("\n") { RideJson.pointLine(it) } + "\n",
        )
        store.recoverOrphans(10, 10)
        assertFalse(store.trackFile(id).exists())
        assertEquals(setOf(a.id, b.id), store.list().map { it.id }.toSet())
    }

    @Test fun purgeByIdsOnlyRemovesDeletedRides() {
        val a = importRide(startMs = 1_000_000L); val b = importRide(startMs = 2_000_000L)
        store.softDelete(listOf(a.id))
        assertEquals(listOf(a.id), store.purge(listOf(a.id, b.id)))
        assertFalse(store.trackFile(a.id).exists())
        assertTrue(store.trackFile(b.id).exists())
    }

    @Test fun anActiveRideCannotBeSoftDeleted() {
        val r = importRide()
        store.setActiveForTest(r.id)
        assertTrue(store.softDelete(listOf(r.id)).isEmpty())
    }

    @Test fun backupLeavesDeletedRidesOut() {
        val a = importRide(startMs = 1_000_000L); importRide(startMs = 2_000_000L)
        store.softDelete(listOf(a.id))
        val out = java.io.ByteArrayOutputStream()
        val summary = com.navigator.app.backup.BackupManager(ctx).backup(out)
        assertEquals(1, summary.rides)
    }

    private fun merge(vararg ids: String) = store.mergeRides(ids.toList())

    @Test fun mergeJoinsStatsKeepsTheGapAndSoftDeletesTheOriginals() {
        val a = store.importRide("First", pts(60, 1_000_000L))!!.also { store.updateMeta(it.id, "First", "n1") }
        val b = store.importRide("Second", pts(60, 2_000_000L, lng0 = 77.1))!!.also { store.updateMeta(it.id, "Second", "n2"); store.setSaved(it.id, true) }
        val m = mergedOf(merge(b.id, a.id))
        assertEquals("First", m.ride.destinationLabel)
        assertEquals("n1\n\nn2", m.ride.notes)
        assertEquals(RideSource.MERGED, m.ride.source)
        assertTrue("saved when any was", m.ride.saved)
        assertEquals(120, m.ride.pointCount)
        val joined = store.points(m.ride.id)
        assertEquals(listOf(61), joined.withIndex().filter { it.value.gapBefore }.map { it.index }.map { it + 1 })
        assertTrue("the seam is not riding distance", m.ride.distanceMeters < a.distanceMeters + b.distanceMeters + 100)
        assertEquals(listOf(m.ride.id), store.list().map { it.id })
        assertEquals(setOf(a.id, b.id), store.listDeleted().map { it.id }.toSet())
        assertEquals(RideSource.MERGED, RideJson.parseMeta(store.trackFile(m.ride.id).readLines().first())!!.getString("src"))
    }

    @Test fun mergeRefusesOverlapActiveAndTooFewAndLeavesEverythingAlone() {
        val a = importRide(startMs = 1_000_000L)
        val b = importRide(startMs = 1_030_000L)
        assertEquals(MergeResult.Refused(MergeRefusal.Overlap), merge(a.id, b.id))
        assertEquals(MergeResult.Refused(MergeRefusal.TooFew), merge(a.id))
        val c = importRide(startMs = 9_000_000L)
        store.setActiveForTest(c.id)
        assertEquals(MergeResult.Refused(MergeRefusal.Active), merge(a.id, c.id))
        assertEquals(3, store.list().size)
    }

    @Test fun undoMergeBringsTheOriginalsBackAndDropsTheMergedRide() {
        val a = importRide(startMs = 1_000_000L); val b = importRide(startMs = 2_000_000L)
        val m = mergedOf(merge(a.id, b.id))
        assertTrue(store.undoMerge(m.token))
        assertEquals(setOf(a.id, b.id), store.list().map { it.id }.toSet())
        assertFalse(store.trackFile(m.ride.id).exists())
        assertTrue("a second undo has nothing to undo", !store.undoMerge(m.token))
    }

    @Test fun undoMergeFailsOnceAnOriginalIsPurged() {
        val a = importRide(startMs = 1_000_000L); val b = importRide(startMs = 2_000_000L)
        val m = mergedOf(merge(a.id, b.id))
        store.deleteAll(listOf(a.id))
        assertFalse(store.undoMerge(m.token))
        assertEquals(listOf(m.ride.id), store.list().map { it.id })
    }

    @Test fun gpxExportOfAMergedRideHasOneSegmentPerPart() {
        val a = importRide(startMs = 1_000_000L); val b = importRide(startMs = 2_000_000L)
        val m = mergedOf(merge(a.id, b.id))
        val xml = Gpx.write(m.ride.destinationLabel, store.points(m.ride.id))
        assertEquals(2, Regex("<trkseg>").findAll(xml).count())
    }

    @Test fun thumbnailCacheIsLruAndKeyedByPointCount() {
        var reads = 0
        val cache = com.navigator.app.data.rides.RideThumbnailCache(store, capacity = 2, load = { reads++; pts(100) })
        cache.get("a", 100); cache.get("a", 100)
        assertEquals(1, reads)
        cache.get("a", 50)
        assertEquals("a trimmed ride is read again", 2, reads)
        cache.get("b", 1)
        assertEquals(2, cache.size())
        cache.get("a", 100)
        assertEquals("the least recently used was evicted", 4, reads)
    }

    @Test fun aCachedThumbnailCanBePeekedWithoutReading() {
        var reads = 0
        val cache = com.navigator.app.data.rides.RideThumbnailCache(store, load = { reads++; pts(100) })
        assertNull(cache.peek("a", 100))
        val first = cache.get("a", 100)
        assertEquals(first, cache.peek("a", 100))
        assertNull("a trimmed ride has a different key", cache.peek("a", 50))
        assertEquals(1, reads)
    }
}
