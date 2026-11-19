// GENERATED from overhaul/design/redesign.html (symbols i-*): Lucide geometry, 24 dp grid, 2 dp round stroke.
// Edit the icon source and regenerate rather than hand-editing paths.
package com.navigator.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** One shape of an icon: stroked by default; [fill] fills it too, [stroke] = false drops the outline. */
internal class IconPart(
    val d: String,
    val fill: Boolean = false,
    val stroke: Boolean = true,
    val color: Long? = null,
    val alpha: Float = 1f,
)

/**
 * The one icon set (design §3.4). Every icon is black on a 24 dp viewport; tint with `Icon(tint = ...)`.
 * A selected state is shown by its container, except [Star] and [Bookmark], which have filled twins.
 * The official dash turn glyphs stay separate (`dash_*` drawables).
 */
object KtmIcons {
    private fun build(name: String, parts: List<IconPart>): ImageVector {
        val b = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        for (p in parts) {
            val tone = if (p.color != null) Color(p.color) else Color.Black
            b.addPath(
                pathData = addPathNodes(p.d),
                fill = if (p.fill || p.color != null) SolidColor(tone) else null,
                fillAlpha = p.alpha,
                stroke = if (p.stroke) SolidColor(Color.Black) else null,
                strokeAlpha = p.alpha,
                strokeLineWidth = if (p.stroke) 2f else 0f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
        return b.build()
    }
    val Search: ImageVector by lazy {
        build("Search", listOf(
            IconPart("M4 11a7 7 0 1 0 14 0a7 7 0 1 0 -14 0Z"),
            IconPart("m20 20-3.6-3.6"),
        ))
    }
    val Mic: ImageVector by lazy {
        build("Mic", listOf(
            IconPart("M12 2h0a3 3 0 0 1 3 3v6a3 3 0 0 1 -3 3h-0a3 3 0 0 1 -3 -3v-6a3 3 0 0 1 3 -3Z"),
            IconPart("M19 10v1a7 7 0 0 1-14 0v-1"),
            IconPart("M12 18v4"),
        ))
    }
    val Close: ImageVector by lazy {
        build("Close", listOf(
            IconPart("M18 6 6 18M6 6l12 12"),
        ))
    }
    val ChevronLeft: ImageVector by lazy {
        build("ChevronLeft", listOf(
            IconPart("m15 18-6-6 6-6"),
        ))
    }
    val ChevronRight: ImageVector by lazy {
        build("ChevronRight", listOf(
            IconPart("m9 18 6-6-6-6"),
        ))
    }
    val ChevronDown: ImageVector by lazy {
        build("ChevronDown", listOf(
            IconPart("m6 9 6 6 6-6"),
        ))
    }
    val ChevronUp: ImageVector by lazy {
        build("ChevronUp", listOf(
            IconPart("m18 15-6-6-6 6"),
        ))
    }
    val ArrowLeft: ImageVector by lazy {
        build("ArrowLeft", listOf(
            IconPart("M19 12H5M12 19l-7-7 7-7"),
        ))
    }
    val Home: ImageVector by lazy {
        build("Home", listOf(
            IconPart("M3 10.5 12 3l9 7.5"),
            IconPart("M5 9.5V21h14V9.5"),
            IconPart("M9.5 21v-6h5v6"),
        ))
    }
    val Work: ImageVector by lazy {
        build("Work", listOf(
            IconPart("M5 7h14a2 2 0 0 1 2 2v9a2 2 0 0 1 -2 2h-14a2 2 0 0 1 -2 -2v-9a2 2 0 0 1 2 -2Z"),
            IconPart("M8 7V5a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"),
            IconPart("M3 13h18"),
        ))
    }
    val Bookmark: ImageVector by lazy {
        build("Bookmark", listOf(
            IconPart("M19 21l-7-4.5L5 21V5a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2z"),
        ))
    }
    val BookmarkFilled: ImageVector by lazy {
        build("BookmarkFilled", listOf(
            IconPart("M19 21l-7-4.5L5 21V5a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2z", fill = true),
        ))
    }
    val Pin: ImageVector by lazy {
        build("Pin", listOf(
            IconPart("M20 10c0 6-8 12-8 12S4 16 4 10a8 8 0 0 1 16 0z"),
            IconPart("M9 10a3 3 0 1 0 6 0a3 3 0 1 0 -6 0Z"),
        ))
    }
    val PinPlus: ImageVector by lazy {
        build("PinPlus", listOf(
            IconPart("M20 10c0 6-8 12-8 12S4 16 4 10a8 8 0 0 1 16 0z"),
            IconPart("M12 7v6M9 10h6"),
        ))
    }
    val PinOff: ImageVector by lazy {
        build("PinOff", listOf(
            IconPart("M5.4 5.4A8 8 0 0 0 4 10c0 6 8 12 8 12s2.4-1.8 4.6-4.6M18.9 13.9A12 12 0 0 0 20 10a8 8 0 0 0-11.6-7.1"),
            IconPart("m2 2 20 20"),
        ))
    }
    val Navigation: ImageVector by lazy {
        build("Navigation", listOf(
            IconPart("M3 11 22 2l-9 19-2-8z"),
        ))
    }
    val NavigationUp: ImageVector by lazy {
        build("NavigationUp", listOf(
            IconPart("M12 2 19 21 12 17 5 21z"),
        ))
    }
    val Recenter: ImageVector by lazy {
        build("Recenter", listOf(
            IconPart("M2 12h3M19 12h3M12 2v3M12 19v3"),
            IconPart("M5 12a7 7 0 1 0 14 0a7 7 0 1 0 -14 0Z"),
            IconPart("M9 12a3 3 0 1 0 6 0a3 3 0 1 0 -6 0Z"),
        ))
    }
    val Crosshair: ImageVector by lazy {
        build("Crosshair", listOf(
            IconPart("M3 12a9 9 0 1 0 18 0a9 9 0 1 0 -18 0Z"),
            IconPart("M22 12h-5M7 12H2M12 7V2M12 22v-5"),
        ))
    }
    val Bike: ImageVector by lazy {
        build("Bike", listOf(
            IconPart("M2 17a3.5 3.5 0 1 0 7 0a3.5 3.5 0 1 0 -7 0Z"),
            IconPart("M15 17a3.5 3.5 0 1 0 7 0a3.5 3.5 0 1 0 -7 0Z"),
            IconPart("M15 17h-6l-3-6 4-1 3 4"),
            IconPart("M12 6h3l1.5 4.5"),
        ))
    }
    val Bluetooth: ImageVector by lazy {
        build("Bluetooth", listOf(
            IconPart("m7 7 10 10-5 5V2l5 5L7 17"),
        ))
    }
    val BluetoothOff: ImageVector by lazy {
        build("BluetoothOff", listOf(
            IconPart("m17 17-5 5V12l-5 5M2 2l20 20M14.5 9.5 17 7l-5-5v4.5"),
        ))
    }
    val Bell: ImageVector by lazy {
        build("Bell", listOf(
            IconPart("M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9"),
            IconPart("M10.3 21a1.94 1.94 0 0 0 3.4 0"),
        ))
    }
    val Settings: ImageVector by lazy {
        build("Settings", listOf(
            IconPart("M9 12a3 3 0 1 0 6 0a3 3 0 1 0 -6 0Z"),
            IconPart("M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 1 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 1 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 1 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 1 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z"),
        ))
    }
    val Route: ImageVector by lazy {
        build("Route", listOf(
            IconPart("M3 19a3 3 0 1 0 6 0a3 3 0 1 0 -6 0Z"),
            IconPart("M9 19h8.5a3.5 3.5 0 0 0 0-7h-11a3.5 3.5 0 0 1 0-7H15"),
            IconPart("M15 5a3 3 0 1 0 6 0a3 3 0 1 0 -6 0Z"),
        ))
    }
    val History: ImageVector by lazy {
        build("History", listOf(
            IconPart("M3 12a9 9 0 1 0 3-6.7L3 8"),
            IconPart("M3 3v5h5"),
            IconPart("M12 7v5l4 2"),
        ))
    }
    val Clock: ImageVector by lazy {
        build("Clock", listOf(
            IconPart("M3 12a9 9 0 1 0 18 0a9 9 0 1 0 -18 0Z"),
            IconPart("M12 7v5l3 2"),
        ))
    }
    val Star: ImageVector by lazy {
        build("Star", listOf(
            IconPart("m12 2.5 2.9 6.1 6.6.9-4.8 4.6 1.2 6.6L12 17.6l-5.9 3.1 1.2-6.6-4.8-4.6 6.6-.9z"),
        ))
    }
    val StarFilled: ImageVector by lazy {
        build("StarFilled", listOf(
            IconPart("m12 2.5 2.9 6.1 6.6.9-4.8 4.6 1.2 6.6L12 17.6l-5.9 3.1 1.2-6.6-4.8-4.6 6.6-.9z", fill = true),
        ))
    }
    val Delete: ImageVector by lazy {
        build("Delete", listOf(
            IconPart("M3 6h18"),
            IconPart("M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6"),
            IconPart("M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"),
            IconPart("M10 11v6M14 11v6"),
        ))
    }
    val Edit: ImageVector by lazy {
        build("Edit", listOf(
            IconPart("M17 3a2.8 2.8 0 1 1 4 4L7.5 20.5 2 22l1.5-5.5z"),
        ))
    }
    val Share: ImageVector by lazy {
        build("Share", listOf(
            IconPart("M15 5a3 3 0 1 0 6 0a3 3 0 1 0 -6 0Z"),
            IconPart("M3 12a3 3 0 1 0 6 0a3 3 0 1 0 -6 0Z"),
            IconPart("M15 19a3 3 0 1 0 6 0a3 3 0 1 0 -6 0Z"),
            IconPart("m8.6 13.5 6.8 4M15.4 6.5l-6.8 4"),
        ))
    }
    val Play: ImageVector by lazy {
        build("Play", listOf(
            IconPart("M7 4.5v15l12-7.5z", fill = true),
        ))
    }
    val Pause: ImageVector by lazy {
        build("Pause", listOf(
            IconPart("M7 4.5h2a1 1 0 0 1 1 1v13a1 1 0 0 1 -1 1h-2a1 1 0 0 1 -1 -1v-13a1 1 0 0 1 1 -1Z", fill = true, stroke = false),
            IconPart("M15 4.5h2a1 1 0 0 1 1 1v13a1 1 0 0 1 -1 1h-2a1 1 0 0 1 -1 -1v-13a1 1 0 0 1 1 -1Z", fill = true, stroke = false),
        ))
    }
    val Stop: ImageVector by lazy {
        build("Stop", listOf(
            IconPart("M7.5 5.5h9a2 2 0 0 1 2 2v9a2 2 0 0 1 -2 2h-9a2 2 0 0 1 -2 -2v-9a2 2 0 0 1 2 -2Z", fill = true, stroke = false),
        ))
    }
    val Restart: ImageVector by lazy {
        build("Restart", listOf(
            IconPart("M3 12a9 9 0 1 0 3-6.7L3 8"),
            IconPart("M3 3v5h5"),
        ))
    }
    val Add: ImageVector by lazy {
        build("Add", listOf(
            IconPart("M12 5v14M5 12h14"),
        ))
    }
    val Remove: ImageVector by lazy {
        build("Remove", listOf(
            IconPart("M5 12h14"),
        ))
    }
    val Reorder: ImageVector by lazy {
        build("Reorder", listOf(
            IconPart("M7.4 6a1.6 1.6 0 1 0 3.2 0a1.6 1.6 0 1 0 -3.2 0Z", fill = true, stroke = false),
            IconPart("M13.4 6a1.6 1.6 0 1 0 3.2 0a1.6 1.6 0 1 0 -3.2 0Z", fill = true, stroke = false),
            IconPart("M7.4 12a1.6 1.6 0 1 0 3.2 0a1.6 1.6 0 1 0 -3.2 0Z", fill = true, stroke = false),
            IconPart("M13.4 12a1.6 1.6 0 1 0 3.2 0a1.6 1.6 0 1 0 -3.2 0Z", fill = true, stroke = false),
            IconPart("M7.4 18a1.6 1.6 0 1 0 3.2 0a1.6 1.6 0 1 0 -3.2 0Z", fill = true, stroke = false),
            IconPart("M13.4 18a1.6 1.6 0 1 0 3.2 0a1.6 1.6 0 1 0 -3.2 0Z", fill = true, stroke = false),
        ))
    }
    val Done: ImageVector by lazy {
        build("Done", listOf(
            IconPart("M20 6 9 17l-5-5"),
        ))
    }
    val DoneCircle: ImageVector by lazy {
        build("DoneCircle", listOf(
            IconPart("M2 12a10 10 0 1 0 20 0a10 10 0 1 0 -20 0Z"),
            IconPart("m8 12 3 3 5-6"),
        ))
    }
    val Circle: ImageVector by lazy {
        build("Circle", listOf(
            IconPart("M2 12a10 10 0 1 0 20 0a10 10 0 1 0 -20 0Z"),
        ))
    }
    val Warning: ImageVector by lazy {
        build("Warning", listOf(
            IconPart("M10.3 3.9 1.8 18a2 2 0 0 0 1.7 3h17a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0z"),
            IconPart("M12 9v4M12 17h.01"),
        ))
    }
    val Info: ImageVector by lazy {
        build("Info", listOf(
            IconPart("M2 12a10 10 0 1 0 20 0a10 10 0 1 0 -20 0Z"),
            IconPart("M12 16v-4M12 8h.01"),
        ))
    }
    val Offline: ImageVector by lazy {
        build("Offline", listOf(
            IconPart("m2 2 20 20"),
            IconPart("M8.5 16.5a5 5 0 0 1 7 0"),
            IconPart("M2 8.8a15 15 0 0 1 4.2-2.6"),
            IconPart("M10.7 5.1A15 15 0 0 1 22 8.8"),
            IconPart("M5 12.6a10 10 0 0 1 5.2-2.6"),
            IconPart("M16.8 11.1a10 10 0 0 1 2.2 1.5"),
            IconPart("M12 20h.01"),
        ))
    }
    val Sound: ImageVector by lazy {
        build("Sound", listOf(
            IconPart("M11 5 6 9H2v6h4l5 4z"),
            IconPart("M15.5 8.5a5 5 0 0 1 0 7"),
            IconPart("M19 5a10 10 0 0 1 0 14"),
        ))
    }
    val Mute: ImageVector by lazy {
        build("Mute", listOf(
            IconPart("M11 5 6 9H2v6h4l5 4z"),
            IconPart("m22 9-6 6M16 9l6 6"),
        ))
    }
    val Speed: ImageVector by lazy {
        build("Speed", listOf(
            IconPart("m12 14 4-4"),
            IconPart("M3.3 19a10 10 0 1 1 17.4 0"),
        ))
    }
    val Elevation: ImageVector by lazy {
        build("Elevation", listOf(
            IconPart("m8 3 4 8 5-5 5 15H2z"),
        ))
    }
    val Sort: ImageVector by lazy {
        build("Sort", listOf(
            IconPart("m21 16-4 4-4-4M17 20V4M3 8l4-4 4 4M7 4v16"),
        ))
    }
    val Download: ImageVector by lazy {
        build("Download", listOf(
            IconPart("M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"),
            IconPart("m7 10 5 5 5-5M12 15V3"),
        ))
    }
    val Upload: ImageVector by lazy {
        build("Upload", listOf(
            IconPart("M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"),
            IconPart("m17 8-5-5-5 5M12 3v12"),
        ))
    }
    val Import: ImageVector by lazy {
        build("Import", listOf(
            IconPart("M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"),
            IconPart("M14 2v6h6M12 18v-6M9 15l3-3 3 3"),
        ))
    }
    val More: ImageVector by lazy {
        build("More", listOf(
            IconPart("M10 5a2 2 0 1 0 4 0a2 2 0 1 0 -4 0Z", fill = true, stroke = false),
            IconPart("M10 12a2 2 0 1 0 4 0a2 2 0 1 0 -4 0Z", fill = true, stroke = false),
            IconPart("M10 19a2 2 0 1 0 4 0a2 2 0 1 0 -4 0Z", fill = true, stroke = false),
        ))
    }
    val Flag: ImageVector by lazy {
        build("Flag", listOf(
            IconPart("M4 15s1-1 4-1 5 2 8 2 4-1 4-1V3s-1 1-4 1-5-2-8-2-4 1-4 1z"),
            IconPart("M4 22v-7"),
        ))
    }
    val Fuel: ImageVector by lazy {
        build("Fuel", listOf(
            IconPart("M3 22h12M4 9h10M14 22V4a2 2 0 0 0-2-2H6a2 2 0 0 0-2 2v18"),
            IconPart("M14 13h2a2 2 0 0 1 2 2v2a2 2 0 0 0 4 0V9.8a2 2 0 0 0-.6-1.4L18 5"),
        ))
    }
    val Food: ImageVector by lazy {
        build("Food", listOf(
            IconPart("M3 2v7c0 1.1.9 2 2 2h4a2 2 0 0 0 2-2V2M7 2v20M21 15V2a5 5 0 0 0-5 5v6c0 1.1.9 2 2 2h3zm0 0v7"),
        ))
    }
    val Coffee: ImageVector by lazy {
        build("Coffee", listOf(
            IconPart("M17 8h1a4 4 0 1 1 0 8h-1"),
            IconPart("M3 8h14v9a4 4 0 0 1-4 4H7a4 4 0 0 1-4-4z"),
            IconPart("M6 2v2M10 2v2M14 2v2"),
        ))
    }
    val Parking: ImageVector by lazy {
        build("Parking", listOf(
            IconPart("M5 3h14a2 2 0 0 1 2 2v14a2 2 0 0 1 -2 2h-14a2 2 0 0 1 -2 -2v-14a2 2 0 0 1 2 -2Z"),
            IconPart("M9 17V7h4a3 3 0 0 1 0 6H9"),
        ))
    }
    val Atm: ImageVector by lazy {
        build("Atm", listOf(
            IconPart("M4 6h16a2 2 0 0 1 2 2v8a2 2 0 0 1 -2 2h-16a2 2 0 0 1 -2 -2v-8a2 2 0 0 1 2 -2Z"),
            IconPart("M10 12a2 2 0 1 0 4 0a2 2 0 1 0 -4 0Z"),
            IconPart("M6 12h.01M18 12h.01"),
        ))
    }
    val Hospital: ImageVector by lazy {
        build("Hospital", listOf(
            IconPart("M5 3h14a2 2 0 0 1 2 2v14a2 2 0 0 1 -2 2h-14a2 2 0 0 1 -2 -2v-14a2 2 0 0 1 2 -2Z"),
            IconPart("M12 8v8M8 12h8"),
        ))
    }
    val Pharmacy: ImageVector by lazy {
        build("Pharmacy", listOf(
            IconPart("m10.5 20.5 10-10a4.95 4.95 0 1 0-7-7l-10 10a4.95 4.95 0 1 0 7 7z"),
            IconPart("m8.5 8.5 7 7"),
        ))
    }
    val Ev: ImageVector by lazy {
        build("Ev", listOf(
            IconPart("M13 2 3 14h9l-1 8 10-12h-9z"),
        ))
    }
    val Day: ImageVector by lazy {
        build("Day", listOf(
            IconPart("M8 12a4 4 0 1 0 8 0a4 4 0 1 0 -8 0Z"),
            IconPart("M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4"),
        ))
    }
    val Night: ImageVector by lazy {
        build("Night", listOf(
            IconPart("M12 3a6 6 0 0 0 9 9 9 9 0 1 1-9-9z"),
        ))
    }
    val Palette: ImageVector by lazy {
        build("Palette", listOf(
            IconPart("M12.3 6.5a1.2 1.2 0 1 0 2.4 0a1.2 1.2 0 1 0 -2.4 0Z"),
            IconPart("M16.3 10.5a1.2 1.2 0 1 0 2.4 0a1.2 1.2 0 1 0 -2.4 0Z"),
            IconPart("M7.3 7.5a1.2 1.2 0 1 0 2.4 0a1.2 1.2 0 1 0 -2.4 0Z"),
            IconPart("M5.3 12.5a1.2 1.2 0 1 0 2.4 0a1.2 1.2 0 1 0 -2.4 0Z"),
            IconPart("M12 2a10 10 0 0 0 0 20c.9 0 1.6-.7 1.6-1.6 0-.4-.2-.8-.4-1.1-.3-.3-.4-.7-.4-1.1 0-.9.7-1.6 1.6-1.6H16a6 6 0 0 0 6-6c0-4.4-4.5-8.6-10-8.6z"),
        ))
    }
    val Font: ImageVector by lazy {
        build("Font", listOf(
            IconPart("M4 7V4h16v3M9 20h6M12 4v16"),
        ))
    }
    val Permission: ImageVector by lazy {
        build("Permission", listOf(
            IconPart("M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"),
            IconPart("m9 12 2 2 4-4"),
        ))
    }
    val Battery: ImageVector by lazy {
        build("Battery", listOf(
            IconPart("M4 7h12a2 2 0 0 1 2 2v6a2 2 0 0 1 -2 2h-12a2 2 0 0 1 -2 -2v-6a2 2 0 0 1 2 -2Z"),
            IconPart("M22 11v2M6 11v2M10 11v2"),
        ))
    }
    val Phone: ImageVector by lazy {
        build("Phone", listOf(
            IconPart("M7 2h10a2 2 0 0 1 2 2v16a2 2 0 0 1 -2 2h-10a2 2 0 0 1 -2 -2v-16a2 2 0 0 1 2 -2Z"),
            IconPart("M12 18h.01"),
        ))
    }
    val Message: ImageVector by lazy {
        build("Message", listOf(
            IconPart("M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"),
        ))
    }
    val Diagnostics: ImageVector by lazy {
        build("Diagnostics", listOf(
            IconPart("M22 12h-4l-3 9L9 3l-3 9H2"),
        ))
    }
    val Terminal: ImageVector by lazy {
        build("Terminal", listOf(
            IconPart("m4 17 6-6-6-6M12 19h8"),
        ))
    }
    val Refresh: ImageVector by lazy {
        build("Refresh", listOf(
            IconPart("M21 12a9 9 0 0 1-15 6.7L3 16"),
            IconPart("M3 21v-5h5"),
            IconPart("M3 12a9 9 0 0 1 15-6.7L21 8"),
            IconPart("M21 3v5h-5"),
        ))
    }
    val External: ImageVector by lazy {
        build("External", listOf(
            IconPart("M15 3h6v6M10 14 21 3M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6"),
        ))
    }
    val Map: ImageVector by lazy {
        build("Map", listOf(
            IconPart("M9 4 3 6v14l6-2 6 2 6-2V4l-6 2z"),
            IconPart("M9 4v14M15 6v14"),
        ))
    }
    val Record: ImageVector by lazy {
        build("Record", listOf(
            IconPart("M3 12a9 9 0 1 0 18 0a9 9 0 1 0 -18 0Z"),
            IconPart("M8 12a4 4 0 1 0 8 0a4 4 0 1 0 -8 0Z", fill = true, stroke = false),
        ))
    }
    val User: ImageVector by lazy {
        build("User", listOf(
            IconPart("M8 8a4 4 0 1 0 8 0a4 4 0 1 0 -8 0Z"),
            IconPart("M4 21a8 8 0 0 1 16 0"),
        ))
    }
    val Help: ImageVector by lazy {
        build("Help", listOf(
            IconPart("M2 12a10 10 0 1 0 20 0a10 10 0 1 0 -20 0Z"),
            IconPart("M9.1 9a3 3 0 0 1 5.8 1c0 2-3 3-3 3M12 17h.01"),
        ))
    }
    val Quit: ImageVector by lazy {
        build("Quit", listOf(
            IconPart("M12 2v10"),
            IconPart("M18.4 6.6a9 9 0 1 1-12.8 0"),
        ))
    }
    val Dash: ImageVector by lazy {
        build("Dash", listOf(
            IconPart("M5 5h14a3 3 0 0 1 3 3v8a3 3 0 0 1 -3 3h-14a3 3 0 0 1 -3 -3v-8a3 3 0 0 1 3 -3Z"),
            IconPart("M12 15.5V9M9.3 11.6 12 9l2.7 2.6"),
        ))
    }
    val Swap: ImageVector by lazy {
        build("Swap", listOf(
            IconPart("M8 3 4 7l4 4M4 7h16M16 21l4-4-4-4M20 17H4"),
        ))
    }
    val RideBack: ImageVector by lazy {
        build("RideBack", listOf(
            IconPart("M9 14 4 9l5-5"),
            IconPart("M4 9h10.5a5.5 5.5 0 0 1 0 11H11"),
        ))
    }
    val Trim: ImageVector by lazy {
        build("Trim", listOf(
            IconPart("M3 6a3 3 0 1 0 6 0a3 3 0 1 0 -6 0Z"),
            IconPart("M3 18a3 3 0 1 0 6 0a3 3 0 1 0 -6 0Z"),
            IconPart("M20 4 8.1 15.9M14.5 14.5 20 20M8.1 8.1 12 12"),
        ))
    }
    val Headset: ImageVector by lazy {
        build("Headset", listOf(
            IconPart("M3 14h3a2 2 0 0 1 2 2v3a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-7a9 9 0 0 1 18 0v7a2 2 0 0 1-2 2h-1a2 2 0 0 1-2-2v-3a2 2 0 0 1 2-2h3"),
        ))
    }
    val Backup: ImageVector by lazy {
        build("Backup", listOf(
            IconPart("M3 3h18a1 1 0 0 1 1 1v3a1 1 0 0 1 -1 1h-18a1 1 0 0 1 -1 -1v-3a1 1 0 0 1 1 -1Z"),
            IconPart("M4 8v11a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8M10 12h4"),
        ))
    }
    val List: ImageVector by lazy {
        build("List", listOf(
            IconPart("M8 6h13M8 12h13M8 18h13M3 6h.01M3 12h.01M3 18h.01"),
        ))
    }
    val SelectAll: ImageVector by lazy {
        build("SelectAll", listOf(
            IconPart("M6 3h12a3 3 0 0 1 3 3v12a3 3 0 0 1 -3 3h-12a3 3 0 0 1 -3 -3v-12a3 3 0 0 1 3 -3Z"),
            IconPart("m8 12 3 3 5-6"),
        ))
    }
    val Eye: ImageVector by lazy {
        build("Eye", listOf(
            IconPart("M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12z"),
            IconPart("M9 12a3 3 0 1 0 6 0a3 3 0 1 0 -6 0Z"),
        ))
    }
    val Lock: ImageVector by lazy {
        build("Lock", listOf(
            IconPart("M6 11h12a2 2 0 0 1 2 2v6a2 2 0 0 1 -2 2h-12a2 2 0 0 1 -2 -2v-6a2 2 0 0 1 2 -2Z"),
            IconPart("M8 11V7a4 4 0 0 1 8 0v4"),
        ))
    }
    val TextSize: ImageVector by lazy {
        build("TextSize", listOf(
            IconPart("M4 6h16M4 12h10M4 18h13"),
        ))
    }
    val Wand: ImageVector by lazy {
        build("Wand", listOf(
            IconPart("m15 4 5 5M3 21l11-11"),
            IconPart("M19 2v3M17.5 3.5h3M6 3v3M4.5 4.5h3"),
        ))
    }
    val Signal: ImageVector by lazy {
        build("Signal", listOf(
            IconPart("M2 20h.01M7 20v-4M12 20v-8M17 20V8M22 4v16"),
        ))
    }
    val Overview: ImageVector by lazy {
        build("Overview", listOf(
            IconPart("M9 4 3 6v14l6-2 6 2 6-2V4l-6 2z"),
            IconPart("M9 4v14M15 6v14"),
        ))
    }
    /** Carries its own colours (red north needle): draw it with `Image`, not `Icon`, which would tint it flat. */
    val CompassNorth: ImageVector by lazy {
        build("CompassNorth", listOf(
            IconPart("M12 2.5 16 12h-8z", fill = true, stroke = false, color = 0xFFE5362B),
            IconPart("M12 21.5 8 12h8z", fill = true, stroke = false, alpha = 0.55f),
        ))
    }
    val Steps: ImageVector by lazy {
        build("Steps", listOf(
            IconPart("M3 6a2 2 0 1 0 4 0a2 2 0 1 0 -4 0Z"),
            IconPart("M3 18a2 2 0 1 0 4 0a2 2 0 1 0 -4 0Z"),
            IconPart("M5 8v8M10 6h10M10 18h10M10 12h7"),
        ))
    }
    val Timer: ImageVector by lazy {
        build("Timer", listOf(
            IconPart("M4 13a8 8 0 1 0 16 0a8 8 0 1 0 -16 0Z"),
            IconPart("M12 9v4l2 2M10 2h4"),
        ))
    }
    val Merge: ImageVector by lazy {
        build("Merge", listOf(
            IconPart("m8 6 4-4 4 4"),
            IconPart("M12 2v10.3a4 4 0 0 1-1.172 2.872L4 22"),
            IconPart("m20 22-5-5"),
        ))
    }
    val Ruler: ImageVector by lazy {
        build("Ruler", listOf(
            IconPart("M21.3 15.3a2.4 2.4 0 0 1 0 3.4l-2.6 2.6a2.4 2.4 0 0 1-3.4 0L2.7 8.7a2.4 2.4 0 0 1 0-3.4l2.6-2.6a2.4 2.4 0 0 1 3.4 0z"),
            IconPart("m14.5 12.5 2-2M11.5 9.5l2-2M8.5 6.5l2-2M17.5 15.5l2-2"),
        ))
    }

    /** Every icon, for the token gallery and the icon-set test. */
    val all: List<ImageVector> by lazy {
        listOf(Search, Mic, Close, ChevronLeft, ChevronRight, ChevronDown, ChevronUp, ArrowLeft, Home, Work, Bookmark, BookmarkFilled, Pin, PinPlus, PinOff, Navigation, NavigationUp, Recenter, Crosshair, Bike, Bluetooth, BluetoothOff, Bell, Settings, Route, History, Clock, Star, StarFilled, Delete, Edit, Share, Play, Pause, Stop, Restart, Add, Remove, Reorder, Done, DoneCircle, Circle, Warning, Info, Offline, Sound, Mute, Speed, Elevation, Sort, Download, Upload, Import, More, Flag, Fuel, Food, Coffee, Parking, Atm, Hospital, Pharmacy, Ev, Day, Night, Palette, Font, Permission, Battery, Phone, Message, Diagnostics, Terminal, Refresh, External, Map, Record, User, Help, Quit, Dash, Swap, RideBack, Trim, Headset, Backup, List, SelectAll, Eye, Lock, TextSize, Wand, Signal, Overview, CompassNorth, Steps, Timer, Merge, Ruler)
    }
}
