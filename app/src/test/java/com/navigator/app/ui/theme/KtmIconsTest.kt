package com.navigator.app.ui.theme

import androidx.compose.ui.graphics.vector.ImageVector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KtmIconsTest {
    @Test fun everyIconIsA24GridVectorWithShapes() {
        assertTrue(KtmIcons.all.size >= 66)
        for (icon in KtmIcons.all) {
            assertEquals(icon.name, 24f, icon.viewportWidth, 0f)
            assertEquals(icon.name, 24f, icon.viewportHeight, 0f)
            assertTrue(icon.name, icon.root.size > 0)
        }
    }

    @Test fun filledTwinsAndDashIconsHaveFilledShapes() {
        fun fills(i: ImageVector) = (i.root.toList().filterIsInstance<androidx.compose.ui.graphics.vector.VectorPath>()).any { it.fill != null }
        assertTrue(fills(KtmIcons.StarFilled) && fills(KtmIcons.BookmarkFilled) && fills(KtmIcons.Play))
        assertTrue(!fills(KtmIcons.Star) && !fills(KtmIcons.Bookmark) && !fills(KtmIcons.Search))
    }

    @Test fun namesAreUnique() {
        assertEquals(KtmIcons.all.size, KtmIcons.all.map { it.name }.toSet().size)
    }

    @Test fun designIconsExist() {
        val needed: List<ImageVector> = listOf(
            KtmIcons.Navigation, KtmIcons.Route, KtmIcons.Bookmark, KtmIcons.Settings, KtmIcons.Search, KtmIcons.Mic,
            KtmIcons.Home, KtmIcons.Work, KtmIcons.Pin, KtmIcons.PinPlus, KtmIcons.Crosshair, KtmIcons.Recenter,
            KtmIcons.Overview, KtmIcons.Bike, KtmIcons.Dash, KtmIcons.Bluetooth, KtmIcons.BluetoothOff, KtmIcons.Record,
            KtmIcons.Play, KtmIcons.Pause, KtmIcons.Stop, KtmIcons.Restart, KtmIcons.Star, KtmIcons.Delete,
            KtmIcons.Edit, KtmIcons.Share, KtmIcons.Fuel, KtmIcons.Food, KtmIcons.Coffee, KtmIcons.Parking,
            KtmIcons.Atm, KtmIcons.Hospital, KtmIcons.Pharmacy, KtmIcons.Ev, KtmIcons.Flag, KtmIcons.RideBack,
            KtmIcons.Trim, KtmIcons.Speed, KtmIcons.Elevation, KtmIcons.Sound, KtmIcons.Mute, KtmIcons.Message,
            KtmIcons.Bell, KtmIcons.Headset, KtmIcons.Palette, KtmIcons.Day, KtmIcons.Night, KtmIcons.Font,
            KtmIcons.Permission, KtmIcons.Battery, KtmIcons.Backup, KtmIcons.Diagnostics, KtmIcons.Offline,
            KtmIcons.Warning, KtmIcons.Info, KtmIcons.Done, KtmIcons.Sort, KtmIcons.Import, KtmIcons.Download,
            KtmIcons.More, KtmIcons.Reorder, KtmIcons.External, KtmIcons.Help, KtmIcons.Quit, KtmIcons.Merge,
            KtmIcons.StarFilled, KtmIcons.BookmarkFilled,
        )
        assertEquals(needed.size, needed.toSet().size)
    }
}
