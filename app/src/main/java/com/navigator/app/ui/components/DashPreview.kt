package com.navigator.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.navigator.app.ble.BccuProtocol.TurnIcon
import com.navigator.app.ui.theme.KtmShapes
import com.navigator.app.ui.theme.KtmSpacing

/** The dash's own palette: it imitates the TFT's lettering, so it does not follow the app theme. */
private object DashPalette {
    val bg = Color(0xFF050607)
    val ring = Color(0xFF2A2D33)
    val text = Color(0xFFFFFFFF)
    val subtle = Color(0xFF9AA0A8)
}

/**
 * What the dash is showing, from what the BCCU protocol carries (turn icon, distance, road, ETA,
 * remaining, notification banner). It answers "what is my dash showing right now?"; it does not try
 * to look like a photo of the TFT. Text is supplied by the caller.
 */
sealed interface DashPreviewState {
    data class Turn(
        val time: String, val icon: TurnIcon?, val distance: String, val road: String,
        val eta: String, val remaining: String,
    ) : DashPreviewState

    data class Message(
        val time: String, val app: String, val text: String, val eta: String? = null, val remaining: String? = null,
    ) : DashPreviewState

    data class NotConnected(val title: String, val hint: String) : DashPreviewState
}

@Composable
fun DashPreview(state: DashPreviewState, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth()
            .semantics(mergeDescendants = true) {}
            .background(DashPalette.bg, KtmShapes.Card)
            .border(2.dp, DashPalette.ring, KtmShapes.Card)
            .padding(KtmSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(KtmSpacing.sm),
    ) {
        when (state) {
            is DashPreviewState.Turn -> {
                DashLine(state.time)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(KtmSpacing.md)) {
                    state.icon?.let { TurnGlyph(it, DashPalette.text, size = 56.dp) }
                    Column {
                        Text(state.distance, style = MaterialTheme.typography.displaySmall, color = DashPalette.text, maxLines = 1)
                        Text(state.road, style = MaterialTheme.typography.titleLarge, color = DashPalette.text, maxLines = 1)
                    }
                }
                Footer(state.eta, state.remaining)
            }
            is DashPreviewState.Message -> {
                DashLine(state.time)
                Text(state.app, style = MaterialTheme.typography.titleLarge, color = DashPalette.subtle, maxLines = 1)
                Text(state.text, style = MaterialTheme.typography.headlineSmall, color = DashPalette.text, maxLines = 2)
                if (state.eta != null && state.remaining != null) Footer(state.eta, state.remaining)
            }
            is DashPreviewState.NotConnected -> {
                Text("—", style = MaterialTheme.typography.displaySmall, color = DashPalette.subtle, modifier = Modifier.clearAndSetSemantics {})
                Text(state.title, style = MaterialTheme.typography.headlineSmall, color = DashPalette.text)
                Text(state.hint, style = MaterialTheme.typography.bodySmall, color = DashPalette.subtle)
            }
        }
    }
}

@Composable
private fun DashLine(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, color = DashPalette.subtle, maxLines = 1)
}

@Composable
private fun Footer(left: String, right: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(left, style = MaterialTheme.typography.titleLarge, color = DashPalette.subtle, maxLines = 1)
        Text(right, style = MaterialTheme.typography.titleLarge, color = DashPalette.subtle, maxLines = 1)
    }
}
