package com.navigator.app.backup

import android.content.Context
import com.navigator.app.ride.RidePoint
import com.navigator.app.ride.RideStore
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
// The plain Application: OpenDashApplication's launch work (orphan recovery, pruning) would race with these tests' files.
@Config(sdk = [36], application = android.app.Application::class)
class BackupRestoreLimitTest {
    private lateinit var ctx: Context
    private lateinit var store: RideStore
    private val prefs get() = ctx.getSharedPreferences("opendash_pairing", Context.MODE_PRIVATE)

    @Before fun setUp() {
        ctx = RuntimeEnvironment.getApplication()
        File(ctx.filesDir, "rides").deleteRecursively()
        ctx.getSharedPreferences("ride_store", Context.MODE_PRIVATE).edit().clear().commit()
        prefs.edit().clear().commit()
        store = RideStore(ctx)
        store.resetStaticsForTest()
    }

    @After fun tearDown() = store.resetStaticsForTest()

    private fun makeUnsavedRides(n: Int) = repeat(n) { i ->
        val pts = (0 until 60).map { RidePoint(12.0, 77.0 + it * 0.0003, 1_000_000L * (i + 1) + 1_000L * it, 40f) }
        val r = store.importRide("Ride $i", pts)!!
        store.setSaved(r.id, false)
    }

    private fun backupThenWipe(limit: Int): ByteArray {
        prefs.edit().putInt("ride_history_limit", limit).commit()
        val out = ByteArrayOutputStream()
        BackupManager(ctx).backup(out)
        store.deleteAll(store.list().map { it.id })
        return out.toByteArray()
    }

    @Test fun restoringRidesOnlyRaisesTheLimitSoTheNextPruneKeepsThem() {
        makeUnsavedRides(15)
        val file = backupThenWipe(limit = 10)
        val r = BackupManager(ctx).restore(ByteArrayInputStream(file), withRides = true, withPlaces = false, withSettings = false)
        assertEquals(15, r.ridesAdded)
        assertEquals(15, r.limitRaisedTo)
        assertEquals(15, prefs.getInt("ride_history_limit", 0))
        assertEquals(emptyList<String>(), store.pruneToLimit(prefs.getInt("ride_history_limit", 0)))
    }

    @Test fun restoringSettingsWithASmallerLimitStillKeepsTheRestoredRides() {
        makeUnsavedRides(15)
        val file = backupThenWipe(limit = 10)
        val r = BackupManager(ctx).restore(ByteArrayInputStream(file), withRides = true, withPlaces = false, withSettings = true)
        assertEquals(15, r.limitRaisedTo)
        assertEquals(15, prefs.getInt("ride_history_limit", 0))
    }

    @Test fun settingsAloneWithASmallerLimitNeverLeaveMoreRidesThanTheLimitKeeps() {
        makeUnsavedRides(15)
        val file = backupThenWipe(limit = 10)
        // The rides are already on the phone (nothing to add), the file lowers the limit, a restart follows.
        BackupManager(ctx).restore(ByteArrayInputStream(file), withRides = true, withPlaces = false, withSettings = false)
        prefs.edit().putInt("ride_history_limit", 50).commit()
        val again = ByteArrayOutputStream().also { BackupManager(ctx).backup(it) }.toByteArray()
        prefs.edit().putInt("ride_history_limit", 20).commit()
        val r = BackupManager(ctx).restore(ByteArrayInputStream(again), withRides = false, withPlaces = false, withSettings = true)
        assertEquals(0, r.ridesAdded)
        assertNull("50 covers 15 rides", r.limitRaisedTo)
        val low = backupWithLimit(10)
        val r2 = BackupManager(ctx).restore(ByteArrayInputStream(low), withRides = false, withPlaces = false, withSettings = true)
        assertEquals(15, r2.limitRaisedTo)
        assertEquals(15, prefs.getInt("ride_history_limit", 0))
    }

    private fun backupWithLimit(limit: Int): ByteArray {
        prefs.edit().putInt("ride_history_limit", limit).commit()
        return ByteArrayOutputStream().also { BackupManager(ctx).backup(it) }.toByteArray()
    }

    @Test fun noRaiseWhenTheLimitAlreadyCoversTheRides() {
        makeUnsavedRides(5)
        val file = backupThenWipe(limit = 50)
        val r = BackupManager(ctx).restore(ByteArrayInputStream(file), withRides = true, withPlaces = false, withSettings = false)
        assertNull(r.limitRaisedTo)
    }
}
