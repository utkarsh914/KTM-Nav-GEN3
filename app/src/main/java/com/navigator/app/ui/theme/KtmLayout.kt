package com.navigator.app.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** The 4 dp grid: no other spacing values (design §3.3). */
object KtmSpacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    val xxxl = 48.dp

    /** Screen margin at rest. */
    val edge = lg

    /** Margin on riding screens: from the screen edge and between maneuver card, chips and trip bar. */
    val ridingEdge = sm
}

/** Four radii and the pill. */
object KtmShapes {
    val Badge = RoundedCornerShape(8.dp)
    val Control = RoundedCornerShape(12.dp)
    val Card = RoundedCornerShape(16.dp)
    val Sheet = RoundedCornerShape(28.dp)
    val Pill = CircleShape

    /** Bottom sheets round only their top corners. */
    val SheetTop = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

    /** Material's slots mapped onto the same four values. */
    val material = Shapes(
        extraSmall = Badge, small = Control, medium = Card, large = Sheet, extraLarge = Sheet,
    )
}

/** Four levels: flat inside screens, shadow only for things floating over the map. */
object KtmElevation {
    val flat = 0.dp
    val onMap = 2.dp
    val sheet = 6.dp
    val dialog = 12.dp
}

/** Touch targets and fixed measurements from the layout rules (design §3.3). */
object KtmLayout {
    // Targets
    val targetRest = 48.dp
    val targetPrimary = 56.dp
    val targetRiding = 64.dp

    // Row heights: one line, two lines, three lines, thumbnail or large leading
    val row1 = 56.dp
    val row2 = 64.dp
    val row3 = 80.dp
    val rowThumb = 72.dp

    /** An app's icon in a picker row. */
    val appIcon = 40.dp

    /** Accent swatch (the touch area is [targetRest]) and its selection ring. */
    val swatch = 40.dp
    val swatchRing = 2.dp

    /** The speed-colour legend bar. */
    val legendBar = 12.dp

    /** The custom-colour picker: shade panel, hue slider track, and the drag handle. */
    val colorPanel = 176.dp
    val hueTrack = 20.dp
    val colorHandle = 28.dp
    val colorHandleRing = 2.dp

    /** Settings that are not in use right now (still editable) are drawn at this opacity. */
    const val inactiveAlpha = 0.6f

    /** The replay's seek bar: track thickness, thumb, the hatched stop between merged parts, and the chart. */
    val seekTrack = 6.dp
    val seekThumb = 24.dp
    val seekGap = 10.dp
    val replayChart = 120.dp
    val chartAxisWidth = 36.dp
    val chartAxisHeight = 20.dp

    /** The trim range bar: handle size (the touch band is [targetRest]) and the track. */
    val trimHandle = 28.dp

    /** The replay sheet may grow to this share of the screen's height when expanded. */
    const val replaySheetMaxShare = 0.7f

    /** The joined-route preview of a merge. */
    val mergeMap = 200.dp

    /** The route line of the merge preview. */
    val routeStroke = 3.dp

    /** The route preview on a ride card. */
    val rideThumb = 64.dp

    /** Border of a flat card. */
    val hairline = 1.dp

    /** Dividers inset to the text. */
    val dividerInset = 72.dp

    /** Opacity of a control or list that is inert for now (no location, loading). */
    const val disabledAlpha = 0.5f
    val topBar = 64.dp

    // Landscape panel: 352 dp at 874 dp wide, 320 dp below 800 dp (content = portrait sheet width)
    val panelWide = 352.dp
    val panelNarrow = 320.dp
    val panelNarrowBelow = 800.dp
    val panelPadding = 12.dp
    val panelInset = 8.dp

    /** Riding toggle tile (Beeps, Messages). */
    val ridingToggle = 56.dp
    val ridingGlyph = 60.dp
    val ridingGlyphLand = 76.dp
    val mirrorGlyph = 96.dp

    /** Outline of an emphasised row (the paused switch row). */
    val outline = 2.dp
    val laneArrowLand = 30.dp
    val rowStop = 76.dp
    val laneArrow = 28.dp
    val laneCellWidth = 40.dp
    val laneCellHeight = 44.dp
    val progressLine = 4.dp
    val laneRecommendedMark = 3.dp
    val iconRiding = 28.dp

    /** Sheet anatomy: handle zone is 12 · 4 · 12, the title row 48. */
    val sheetHandleZone = 28.dp

    /** The pin of Pick on map; its tip is the picked point. */
    val mapPin = 48.dp

    /** A docked activity strip row. */
    val activityStrip = 64.dp
    val sheetTitleRow = 48.dp

    // Small icons and the gaps around them in pills, chips and rows.
    val iconSmall = 18.dp
    val iconInline = 20.dp
    val iconMedium = 24.dp
    val pillStartPadding = 14.dp

    /** The map's top controls sit this far below the status bar. */
    val mapControlTop = 10.dp
    val logRowPadding = 2.dp

    /** The open trip sheet never gets smaller than this, however tall the card above it is. */
    val sheetMinHeight = 240.dp

    /** How far the pin on the pick-on-map screen is lifted so its foot marks the centre. */
    val pinLift = 8.dp

    /** How far a pushed page slides in from the right. */
    val pushOffset = 30.dp

    // Diagnostics: glyph tiles and cells in the calibration and symbol-test tools.
    val diagGlyphSmall = 32.dp
    val diagGlyph = 40.dp
    val diagThumb = 44.dp
    val diagTile = 56.dp
    /** The corner radius a page takes on while it shrinks away in a back swipe (Android's own back preview). */
    val pageLeaveCorner = 28.dp
    /** The big turn glyph in a calibration cell (it fills the cell's tile). */
    val diagGlyphLarge = 56.dp
    /** The square behind a turn glyph in a diagnostics row. */
    val diagRowTile = 48.dp
    val diagCell = 64.dp
    val diagCellWidth = 80.dp
    val diagRowMin = 72.dp

    /** Panel width for a window [widthDp] wide. */
    fun panelWidth(widthDp: Float) = if (widthDp < panelNarrowBelow.value) panelNarrow else panelWide
}
