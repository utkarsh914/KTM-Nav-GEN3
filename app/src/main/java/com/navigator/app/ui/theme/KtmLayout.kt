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

    /** Dividers inset to the text. */
    val dividerInset = 72.dp
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
    val rowStop = 76.dp
    val laneArrow = 28.dp
    val laneCellWidth = 40.dp
    val laneCellHeight = 44.dp
    val progressLine = 4.dp
    val laneRecommendedMark = 3.dp
    val iconRiding = 28.dp

    /** Sheet anatomy: handle zone is 12 · 4 · 12, the title row 48. */
    val sheetHandleZone = 28.dp

    /** The landscape side panel of the Navigate sheet (352 dp, the portrait sheet's content width). */
    val sidePanel = 352.dp

    /** The pin of Pick on map; its tip is the picked point. */
    val mapPin = 48.dp

    /** A docked activity strip row. */
    val activityStrip = 64.dp
    val sheetTitleRow = 48.dp

    /** Panel width for a window [widthDp] wide. */
    fun panelWidth(widthDp: Float) = if (widthDp < panelNarrowBelow.value) panelNarrow else panelWide
}
