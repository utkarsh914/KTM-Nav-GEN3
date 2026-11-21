package com.navigator.app.ui.feature.diagnostics

import android.graphics.BitmapFactory
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.navigator.app.R
import com.navigator.app.ble.BccuProtocol.TurnIcon
import com.navigator.app.ui.components.ButtonSize
import com.navigator.app.ui.components.ButtonStyle
import com.navigator.app.ui.components.DashPalette
import com.navigator.app.ui.components.EmptyState
import com.navigator.app.ui.components.KtmButton
import com.navigator.app.ui.components.KtmCard
import com.navigator.app.ui.components.KtmTopBar
import com.navigator.app.ui.components.TurnIconNames
import com.navigator.app.ui.components.TurnIconRef
import com.navigator.app.ui.theme.KtmIcons
import com.navigator.app.ui.theme.KtmLayout
import com.navigator.app.ui.theme.KtmShapes
import com.navigator.app.ui.theme.KtmSpacing
import com.navigator.app.ui.theme.KtmTheme
import com.navigator.app.ui.theme.pageInsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CalibrationActions(
    val onBack: () -> Unit,
    val onToggle: (Int) -> Unit,
    val onAssign: (Int, TurnIcon) -> Unit,
    val onClear: (Int) -> Unit,
)

/** DG3: "Seen N times" instead of a hash, 64 dp candidate tiles, and a row per icon that stays collapsed until opened. Stateless. */
@Composable
fun CalibrationContent(ui: CalibrationUi, actions: CalibrationActions, modifier: Modifier = Modifier) {
    val c = KtmTheme.colors
    Column(modifier.fillMaxSize().background(c.bg)) {
        KtmTopBar(title = stringResource(R.string.calib_title), onBack = actions.onBack)
        if (!ui.loaded) return@Column
        if (ui.rows.isEmpty()) {
            EmptyState(KtmIcons.Wand, stringResource(R.string.calib_empty_title), stringResource(R.string.calib_empty))
            return@Column
        }
        LazyColumn(
            contentPadding = PaddingValues(horizontal = KtmSpacing.edge, vertical = KtmSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(KtmSpacing.sm),
            modifier = Modifier.fillMaxSize().pageInsets(),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(KtmSpacing.xs)) {
                    Text(stringResource(R.string.calib_intro), style = MaterialTheme.typography.bodyMedium, color = c.fg2)
                    Text(stringResource(R.string.calib_legend), style = MaterialTheme.typography.bodyMedium, color = c.fg2)
                }
            }
            items(ui.rows, key = { it.hash }) { row -> CalibrationCard(row, ui, actions) }
        }
    }
}

@Composable
private fun CalibrationCard(row: CalibrationRow, ui: CalibrationUi, actions: CalibrationActions) {
    val c = KtmTheme.colors
    val a = KtmTheme.accent
    val stateText = stringResource(if (row.expanded) R.string.calib_expanded else R.string.calib_collapsed)
    // A file decode: off the main thread, the tile shows its "?" until it is ready.
    val image by produceState<ImageBitmap?>(null, row.path) {
        value = withContext(Dispatchers.IO) { runCatching { BitmapFactory.decodeFile(row.path)?.asImageBitmap() }.getOrNull() }
    }
    KtmCard(
        Modifier.fillMaxWidth().animateContentSize().then(if (row.expanded) Modifier.border(KtmLayout.selectedOutline, a.fill, KtmShapes.Card) else Modifier),
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = KtmLayout.diagRowMin)
                .clickable(role = Role.Button, onClick = { actions.onToggle(row.hash) })
                .semantics { stateDescription = stateText }
                .padding(KtmSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(KtmSpacing.md),
        ) {
            // DG3: Google Maps' icon on white, as it arrives, and the dash's symbol on the dash's black.
            Box(Modifier.size(KtmLayout.diagTile).background(DashPalette.text, KtmShapes.Control), contentAlignment = Alignment.Center) {
                val bitmap = image
                if (bitmap != null) Image(bitmap, contentDescription = stringResource(R.string.calib_captured), modifier = Modifier.size(KtmLayout.diagThumb))
                else Text("?", style = MaterialTheme.typography.titleMedium, color = DashPalette.subtle)
            }
            row.assigned?.let {
                Icon(KtmIcons.ChevronRight, null, tint = c.fg3, modifier = Modifier.size(KtmLayout.iconSmall))
                Box(Modifier.size(KtmLayout.diagTile).background(DashPalette.bg, KtmShapes.Control), contentAlignment = Alignment.Center) {
                    TurnIconRef(icon = it, size = KtmLayout.diagGlyph, color = a.fill)
                }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    row.assigned?.let { TurnIconNames.human(it.name) } ?: stringResource(R.string.calib_unmatched),
                    style = MaterialTheme.typography.titleSmall, color = if (row.assigned != null) a.text else c.fg, maxLines = 1,
                )
                Text(
                    if (row.seen > 0) pluralStringResource(R.plurals.calib_seen, row.seen, row.seen) else stringResource(R.string.calib_seen_before),
                    style = MaterialTheme.typography.bodySmall, color = c.fg3,
                )
            }
            Icon(if (row.expanded) KtmIcons.ChevronUp else KtmIcons.ChevronDown, null, tint = c.fg2)
        }
        if (row.expanded) {
            Text(
                stringResource(R.string.calib_pick), style = MaterialTheme.typography.labelMedium, color = c.fg2,
                modifier = Modifier.padding(horizontal = KtmSpacing.md),
            )
            LazyRow(
                contentPadding = PaddingValues(KtmSpacing.md), horizontalArrangement = Arrangement.spacedBy(KtmSpacing.sm),
            ) {
                items(ui.candidates, key = { it.name }) { icon ->
                    CandidateTile(icon, selected = row.assigned == icon, working = icon in ui.working) { actions.onAssign(row.hash, icon) }
                }
            }
            if (row.assigned != null) {
                KtmButton(
                    stringResource(R.string.calib_clear), { actions.onClear(row.hash) },
                    Modifier.padding(start = KtmSpacing.md, end = KtmSpacing.md, bottom = KtmSpacing.md),
                    style = ButtonStyle.Text, size = ButtonSize.Small,
                )
            }
        }
    }
}

@Composable
private fun CandidateTile(icon: TurnIcon, selected: Boolean, working: Boolean, onClick: () -> Unit) {
    val c = KtmTheme.colors
    val a = KtmTheme.accent
    val name = TurnIconNames.human(icon.name)
    val description = if (working) stringResource(R.string.calib_working_named, name) else name
    Column(
        Modifier.width(KtmLayout.diagCellWidth)
            .background(if (selected) a.fill else c.surf2, KtmShapes.Control)
            .border(if (working) KtmLayout.outline else KtmLayout.hairline, if (working) c.ok else c.line2, KtmShapes.Control)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics(mergeDescendants = true) { this.selected = selected; contentDescription = description }
            .padding(horizontal = KtmSpacing.xs, vertical = KtmSpacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(KtmLayout.diagCell), contentAlignment = Alignment.Center) {
            TurnIconRef(icon = icon, size = KtmLayout.diagGlyphLarge, color = if (selected) a.onFill else c.fg)
        }
        Text(
            (if (working) "\u2605 " else "") + name, style = MaterialTheme.typography.labelSmall,
            color = if (selected) a.onFill else c.fg2, maxLines = 3, textAlign = TextAlign.Center,
        )
    }
}
