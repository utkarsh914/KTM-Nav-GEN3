package com.navigator.app.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.navigator.app.ui.theme.BarlowCondensed
import com.navigator.app.ui.theme.JetBrainsMono
import com.navigator.app.ui.theme.Ktm


/** Pieces of the old ride list still used by the replay screen (replaced in B14). */
/** Text style that drops the built-in glyph padding so the stat strip can pack
 *  the value + label tightly together. (Shared with the replay info card.) */
internal val TightTextStyle = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))

@Composable
internal fun StatColumn(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value, color = Ktm.TextPrimary, fontFamily = JetBrainsMono, fontWeight = FontWeight.Medium,
            fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TightTextStyle,
        )
        Text(
            label, color = Ktm.Dim, fontFamily = BarlowCondensed, fontWeight = FontWeight.Bold,
            fontSize = 9.sp, letterSpacing = 1.5.sp, style = TightTextStyle,
            maxLines = 1, softWrap = false,
        )
    }
}

@Composable
internal fun StatDivider() {
    Box(Modifier.height(26.dp).width(1.dp).background(Ktm.RowDivider))
}

/**
 * Save/pin toggle star with a subtle "pop" on tap: the icon dips then springs
 * back to full size. Shared by the history list and the replay screen.
 */
@Composable
internal fun SaveStar(saved: Boolean, boxSize: Dp, iconSize: Dp, onToggle: () -> Unit) {
    val scale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    val haptics = com.navigator.app.ui.theme.rememberHaptics()
    Box(
        modifier = Modifier
            .size(boxSize)
            .clip(RoundedCornerShape(Ktm.RadiusButton))
            .clickable {
                haptics.toggle(!saved)
                scope.launch {
                    scale.snapTo(0.6f)
                    scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow))
                }
                onToggle()
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (saved) Icons.Filled.Star else Icons.Outlined.StarBorder,
            if (saved) "Saved - tap to unsave" else "Save permanently",
            tint = if (saved) Ktm.Orange else Ktm.Dim,
            modifier = Modifier
                .size(iconSize)
                .graphicsLayer { scaleX = scale.value; scaleY = scale.value },
        )
    }
}

/** e.g. "Sun 6 Sep, 2:32 PM" (date + 12-hour time). */
internal fun formatRideDate(ms: Long): String {
    if (ms <= 0L) return ""
    val dt = java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneId.systemDefault())
    return dt.format(java.time.format.DateTimeFormatter.ofPattern("EEE d MMM, h:mm a"))
}

/** Compact duration for the stat strip: "22m" / "1h 12m". */
internal fun formatDurationShort(seconds: Int): String {
    val mins = (seconds + 59) / 60
    return if (mins < 60) "${mins}m" else "${mins / 60}h ${mins % 60}m"
}
