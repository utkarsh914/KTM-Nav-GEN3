package com.navigator.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.navigator.app.R
import com.navigator.app.ble.BccuProtocol.TurnIcon
import com.navigator.app.ui.theme.KtmElevation
import com.navigator.app.ui.theme.KtmIcons
import com.navigator.app.ui.theme.KtmLayout
import com.navigator.app.ui.theme.KtmShapes
import com.navigator.app.ui.theme.KtmSpacing
import com.navigator.app.ui.theme.KtmTheme
import com.navigator.app.ui.theme.RidingDay
import com.navigator.app.ui.theme.rememberHaptics

/** What the strip above the tab bar is showing. Text comes from the caller (it is built from live state). */
sealed interface ActivityStripState {
    val title: String
    val detail: String

    /** A trip running in the background; the next turn glyph leads. */
    data class Trip(override val title: String, override val detail: String, val glyph: TurnIcon? = null) : ActivityStripState

    /** A recording with no trip. Paused shows Resume. */
    data class Recording(override val title: String, override val detail: String, val paused: Boolean) : ActivityStripState

    /** Mirroring another app's guidance to the dash. */
    data class Mirroring(override val title: String, override val detail: String) : ActivityStripState
}

/**
 * One 64 dp surface that docks above the tab bar on every tab while something runs. The whole strip
 * opens the activity; its own controls are 48 dp. A trip offers Resume; a recording offers
 * pause/resume and stop; mirroring has no control.
 */
@Composable
fun ActivityStrip(
    state: ActivityStripState,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    onResume: (() -> Unit)? = null,
    /** Trip only: add a stop without going back to the riding screen first (SE4). */
    onAddStop: (() -> Unit)? = null,
    onTogglePause: (() -> Unit)? = null,
    onStop: (() -> Unit)? = null,
) {
    val c = KtmTheme.colors
    val haptics = rememberHaptics()
    Row(
        modifier.fillMaxWidth()
            .shadow(KtmElevation.onMap, KtmShapes.Card)
            .heightIn(min = KtmLayout.activityStrip)
            .clip(KtmShapes.Card)
            .background(c.surf3)
            .clickable(role = Role.Button) { haptics.tap(); onOpen() }
            .padding(start = KtmSpacing.md, end = KtmSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(KtmSpacing.md),
    ) {
        // MO6: a tinted tile says what is running before the words do.
        // Guidance green on purpose, in both themes: it means "a trip is running" wherever it is seen.
        val tile = when (state) {
            is ActivityStripState.Trip -> RidingDay.guidance
            is ActivityStripState.Recording -> if (state.paused) c.warnContainer else c.badContainer
            is ActivityStripState.Mirroring -> DashPalette.bg
        }
        Box(
            Modifier.size(KtmLayout.activityStripGlyph).background(tile, KtmShapes.Control)
                // Black on a dark strip has no edge of its own.
                .then(if (state is ActivityStripState.Mirroring) Modifier.border(KtmLayout.hairline, c.line2, KtmShapes.Control) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            when (state) {
                is ActivityStripState.Trip ->
                    if (state.glyph != null) TurnGlyph(state.glyph, RidingDay.onGuidance, size = KtmLayout.activityStripIcon)
                    else Icon(KtmIcons.Navigation, null, tint = RidingDay.onGuidance, modifier = Modifier.size(KtmLayout.activityStripIcon))
                is ActivityStripState.Recording ->
                    Icon(if (state.paused) KtmIcons.Pause else KtmIcons.Record, null, tint = if (state.paused) c.warn else c.bad, modifier = Modifier.size(KtmLayout.activityStripIcon))
                is ActivityStripState.Mirroring ->
                    Icon(KtmIcons.Dash, null, tint = KtmTheme.accent.fill, modifier = Modifier.size(KtmLayout.activityStripIcon))
            }
        }
        Column(Modifier.weight(1f)) {
            Text(state.title, style = MaterialTheme.typography.titleLarge, color = c.fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(state.detail, style = MaterialTheme.typography.bodySmall, color = c.fg2, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        // A control without a callback is not drawn, so a missing wiring is visible, not a dead button.
        Row(horizontalArrangement = Arrangement.spacedBy(KtmSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
            when (state) {
                is ActivityStripState.Trip -> {
                    if (onAddStop != null) {
                        KtmIconButton(KtmIcons.PinPlus, stringResource(R.string.trip_add_stop), onAddStop)
                    }
                    if (onResume != null) {
                        KtmButton(stringResource(R.string.activity_strip_resume), onResume, style = ButtonStyle.Primary, size = ButtonSize.Small)
                    }
                }
                is ActivityStripState.Recording -> {
                    if (onTogglePause != null) {
                        if (state.paused) {
                            KtmButton(stringResource(R.string.activity_strip_resume), onTogglePause, style = ButtonStyle.Tonal, size = ButtonSize.Small)
                        } else {
                            KtmIconButton(KtmIcons.Pause, stringResource(R.string.activity_strip_pause_recording), onTogglePause)
                        }
                    }
                    if (onStop != null) {
                        KtmIconButton(KtmIcons.Stop, stringResource(R.string.activity_strip_stop_recording), onStop, tint = c.bad)
                    }
                }
                is ActivityStripState.Mirroring -> Icon(KtmIcons.ChevronRight, null, tint = c.fg2)
            }
        }
    }
}
