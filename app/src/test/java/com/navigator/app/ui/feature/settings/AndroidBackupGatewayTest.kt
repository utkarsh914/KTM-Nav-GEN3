package com.navigator.app.ui.feature.settings

import android.content.Context
import android.net.Uri
import com.navigator.app.ride.RideStore
import com.navigator.app.ride.RidePoint
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AndroidBackupGatewayTest {
    private lateinit var ctx: Context
    private lateinit var gateway: AndroidBackupGateway

    @Before fun setUp() {
        ctx = RuntimeEnvironment.getApplication()
        File(ctx.filesDir, "rides").deleteRecursively()
        ctx.getSharedPreferences("ride_store", Context.MODE_PRIVATE).edit().clear().commit()
        RideStore(ctx).resetStaticsForTest()
        gateway = AndroidBackupGateway(ctx)
    }

    private fun fileUri(name: String) = Uri.fromFile(File(ctx.cacheDir, name).also { it.delete() })

    @Test fun aBackupIsWrittenAndReadBackThroughTheGateway() = runBlocking {
        val store = RideStore(ctx)
        store.importRide("Loop", (0 until 5).map { RidePoint(12.0, 77.0 + it * 0.0003, 1_000_000L + 1_000L * it, 40f) })!!
        val uri = fileUri("b.zip")
        val summary = gateway.backup(uri)
        assertTrue(summary.ok)
        assertEquals(1, summary.rides)
        assertTrue(File(uri.path!!).length() > 0)
        val preview = gateway.inspect(uri)
        assertNotNull(preview)
        assertEquals(1, preview!!.ridesInFile)
    }

    @Test fun aFileThatIsNotABackupIsRefusedNotCrashed() = runBlocking {
        val file = File(ctx.cacheDir, "junk.zip").also { it.writeText("not a zip") }
        val uri = Uri.fromFile(file)
        assertNull(gateway.inspect(uri))
        assertFalse(gateway.restore(uri, rides = true, places = true, settings = true).ok)
    }

    @Test fun aMissingFileIsAFailedRestoreAndInspect() = runBlocking {
        val uri = Uri.fromFile(File(ctx.cacheDir, "missing.zip").also { it.delete() })
        assertNull(gateway.inspect(uri))
        assertFalse(gateway.restore(uri, true, true, true).ok)
    }
}
