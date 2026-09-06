package com.navigator.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * Lucide-style stroke icons reproduced 1:1 from the KTM handoff's inline
 * SVGs (stroke-width ~1.7, round caps/joins, 24x24 viewport). Drawn as
 * outline paths so [androidx.compose.material3.Icon] can tint them freely;
 * the baked stroke color is a placeholder that the Icon tint overrides.
 */
object OpenDashIcons {

    private fun lucide(name: String, strokeWidth: Float, vararg paths: String): ImageVector {
        val builder = ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        )
        for (d in paths) {
            builder.addPath(
                pathData = PathParser().parsePathString(d).toNodes(),
                fill = null,
                stroke = SolidColor(Color.White),
                strokeLineWidth = strokeWidth,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
        return builder.build()
    }

    /** Solid (filled) variant of a lucide glyph — fill tints via [androidx.compose.material3.Icon]. */
    private fun lucideFilled(name: String, vararg paths: String): ImageVector {
        val builder = ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        )
        for (d in paths) {
            builder.addPath(
                pathData = PathParser().parsePathString(d).toNodes(),
                fill = SolidColor(Color.White),
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
        return builder.build()
    }

    // Circles expressed as path arcs so they can share the lucide() builder.
    private fun circle(cx: Float, cy: Float, r: Float): String =
        "M${cx - r},$cy a$r,$r 0 1,0 ${2 * r},0 a$r,$r 0 1,0 ${-2 * r},0 Z"

    val Bike: ImageVector by lazy {
        lucide(
            "Bike", 1.6f,
            circle(5.5f, 17.5f, 3.5f),
            circle(18.5f, 17.5f, 3.5f),
            "M15 17.5h-6l-3-6 4-1 3 4",
            "M12 6.5h3l1.5 4",
        )
    }

    val Bell: ImageVector by lazy {
        lucide(
            "Bell", 1.7f,
            "M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9",
            "M10.3 21a1.94 1.94 0 0 0 3.4 0",
        )
    }

    val Navigation: ImageVector by lazy {
        lucide("Navigation", 1.7f, "M3 11 L22 2 L13 21 L11 13 Z")
    }

    val House: ImageVector by lazy {
        lucide(
            "House", 1.7f,
            "M3 9.5 12 3l9 6.5",
            "M5 10v10h14V10",
            "M9 20v-6h6v6",
        )
    }

    /** Merge/turn arrow used on the Direction card and the bike dash. */
    val TurnArrow: ImageVector by lazy {
        lucide(
            "TurnArrow", 1.8f,
            "M9 20V10a4 4 0 0 1 4-4h5",
            "M15 3l4 3-4 3",
        )
    }

    /** Clean lucide "settings-2" used as the touch-only entry to Settings. */
    val Settings: ImageVector by lazy {
        lucide(
            "Settings", 1.7f,
            "M20 7h-9",
            "M14 17H5",
            circle(17f, 7f, 3f),
            circle(7f, 17f, 3f),
        )
    }

    /** Back chevron (‹) for screen headers. */
    val ChevronLeft: ImageVector by lazy {
        lucide("ChevronLeft", 1.9f, "M15 18l-6-6 6-6")
    }

    val ChevronRight: ImageVector by lazy {
        lucide("ChevronRight", 1.9f, "M9 18l6-6-6-6")
    }

    /** Down chevron — used to "minimise" the active-navigation panel to the map. */
    val ChevronDown: ImageVector by lazy {
        lucide("ChevronDown", 1.9f, "M6 9l6 6 6-6")
    }

    val Bluetooth: ImageVector by lazy {
        lucide("Bluetooth", 1.8f, "M7 7l10 10-5 5V2l5 5L7 17")
    }

    val Check: ImageVector by lazy {
        lucide("Check", 2.2f, "M20 6L9 17l-5-5")
    }

    /** Magnifier for the destination search bar. */
    val Search: ImageVector by lazy {
        lucide(
            "Search", 1.8f,
            circle(11f, 11f, 7f),
            "M21 21l-4.35-4.35",
        )
    }

    /** Bookmark (save a place as a favorite). */
    val Bookmark: ImageVector by lazy {
        lucide("Bookmark", 1.8f, "M19 21l-7-5-7 5V5a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2z")
    }

    /** Filled bookmark — "saved" state. */
    val BookmarkFilled: ImageVector by lazy {
        lucideFilled("BookmarkFilled", "M19 21l-7-5-7 5V5a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2z")
    }

    /** Briefcase (Work favorite). */
    val Briefcase: ImageVector by lazy {
        lucide(
            "Briefcase", 1.7f,
            "M4 8h16a1 1 0 0 1 1 1v10a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V9a1 1 0 0 1 1-1z",
            "M9 8V6a2 2 0 0 1 2-2h2a2 2 0 0 1 2 2v2",
        )
    }

    /** Clock (recent locations). */
    val Clock: ImageVector by lazy {
        lucide("Clock", 1.7f, circle(12f, 12f, 9f), "M12 7v5l3 2")
    }

    /** X / close. */
    val Close: ImageVector by lazy {
        lucide("Close", 1.9f, "M18 6L6 18", "M6 6l12 12")
    }

    /** North needle for the map compass (rotate the whole Icon by the map bearing). */
    val Compass: ImageVector by lazy {
        lucideFilled("Compass", "M12 2 L16 21 L12 17 L8 21 Z")
    }

    /** Crosshair / "recenter on me" control for the map. */
    val LocateFixed: ImageVector by lazy {
        lucide(
            "LocateFixed", 1.7f,
            "M12 2v3",
            "M12 19v3",
            "M2 12h3",
            "M19 12h3",
            circle(12f, 12f, 7f),
            circle(12f, 12f, 3f),
        )
    }
}
