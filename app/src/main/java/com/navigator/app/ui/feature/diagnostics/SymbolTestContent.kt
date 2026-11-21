package com.navigator.app.ui.feature.diagnostics

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.navigator.app.R
import com.navigator.app.ui.components.BadgeTone
import com.navigator.app.ui.components.ButtonSize
import com.navigator.app.ui.components.ButtonStyle
import com.navigator.app.ui.components.DashPalette
import com.navigator.app.ui.components.IconButtonStyle
import com.navigator.app.ui.components.KtmBadge
import com.navigator.app.ui.components.KtmButton
import com.navigator.app.ui.components.KtmIconButton
import com.navigator.app.ui.components.KtmModalSheet
import com.navigator.app.ui.components.KtmSegmented
import com.navigator.app.ui.components.KtmTopBar
import com.navigator.app.ui.components.ListRow
import com.navigator.app.ui.components.TurnIconRef
import com.navigator.app.ui.theme.KtmIcons
import com.navigator.app.ui.theme.KtmLayout
import com.navigator.app.ui.theme.KtmShapes
import com.navigator.app.ui.theme.KtmSpacing
import com.navigator.app.ui.theme.KtmTheme
import com.navigator.app.ui.theme.pageInsets

class SymbolTestActions(
    val onBack: () -> Unit,
    val onKind: (SymbolKind) -> Unit,
    val onInfo: (Boolean) -> Unit,
    val onSend: (SymbolRow) -> Unit,
    val onMark: (SymbolRow, SymbolResult) -> Unit,
)

/** DG2: a 48 dp Send per row, then big Works / Wrong buttons; turn and notification icons on a segmented switch. Stateless. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SymbolTestContent(ui: SymbolTestUi, actions: SymbolTestActions, modifier: Modifier = Modifier) {
    val c = KtmTheme.colors
    val turnLabel = if (ui.turnCount > 0) stringResource(R.string.symbols_turn_count, ui.turnCount) else stringResource(R.string.symbols_turn)
    val notificationLabel =
        if (ui.notificationCount > 0) stringResource(R.string.symbols_notification_count, ui.notificationCount) else stringResource(R.string.symbols_notification)
    Column(modifier.fillMaxSize().background(c.bg)) {
        KtmTopBar(title = stringResource(R.string.symbols_title), onBack = actions.onBack) {
            KtmIconButton(KtmIcons.Help, stringResource(R.string.symbols_info), { actions.onInfo(true) })
        }
        LazyColumn(
            contentPadding = PaddingValues(vertical = KtmSpacing.sm),
            modifier = Modifier.fillMaxSize().pageInsets(),
        ) {
            item {
                Text(
                    stringResource(R.string.symbols_intro), style = MaterialTheme.typography.bodyMedium, color = c.fg2,
                    modifier = Modifier.padding(horizontal = KtmSpacing.edge, vertical = KtmSpacing.sm),
                )
            }
            item {
                KtmSegmented(
                    SymbolKind.entries, ui.kind, actions.onKind,
                    { if (it == SymbolKind.TURN) turnLabel else notificationLabel },
                    Modifier.fillMaxWidth().padding(horizontal = KtmSpacing.edge),
                )
            }
            item {
                Text(
                    stringResource(R.string.symbols_summary, ui.works, ui.wrong, ui.rows.size),
                    style = MaterialTheme.typography.labelMedium, color = c.fg3,
                    modifier = Modifier.padding(horizontal = KtmSpacing.edge).padding(top = KtmSpacing.sm),
                )
            }
            // DG2: one divided list, not a card per symbol.
            itemsIndexed(ui.rows, key = { _, r -> r.key }) { i, row -> SymbolRowItem(row, actions, first = i == 0) }
        }
    }
    if (ui.showInfo) {
        KtmModalSheet(onDismiss = { actions.onInfo(false) }, title = stringResource(R.string.symbols_info_title)) {
            Text(
                stringResource(R.string.symbols_info_body), style = MaterialTheme.typography.bodyMedium, color = c.fg2,
                modifier = Modifier.padding(horizontal = KtmSpacing.edge, vertical = KtmSpacing.sm),
            )
        }
    }
}

@Composable
private fun SymbolRowItem(row: SymbolRow, actions: SymbolTestActions, first: Boolean) {
    val c = KtmTheme.colors
    val sendLabel = stringResource(R.string.symbols_send_named, row.name)
    Column(Modifier.fillMaxWidth().animateContentSize().then(if (row.expanded) Modifier.background(c.surf2) else Modifier)) {
        if (!first) HorizontalDivider(color = c.line)
        ListRow(
            title = row.name, supporting = if (row.expanded) stringResource(R.string.symbols_code_sent, row.code) else row.code,
            leading = {
                // The tile is the dash's own black, so the glyph reads as it will on the bike.
                Box(Modifier.size(KtmLayout.diagRowTile).background(DashPalette.bg, KtmShapes.Control), contentAlignment = Alignment.Center) {
                    row.turn?.let { TurnIconRef(icon = it, size = KtmLayout.diagGlyphSmall, color = DashPalette.text) }
                        ?: Icon(KtmIcons.Message, null, tint = DashPalette.text)
                }
            },
            trailing = {
                // Sent and waiting for a verdict: the Works / Wrong row below is the next step, not another Send.
                if (!row.expanded) KtmIconButton(KtmIcons.Dash, sendLabel, { actions.onSend(row) }, style = IconButtonStyle.Tonal)
                when (row.result) {
                    SymbolResult.WORKS -> KtmBadge(stringResource(R.string.symbols_works), tone = BadgeTone.Ok)
                    SymbolResult.WRONG -> KtmBadge(stringResource(R.string.symbols_wrong), tone = BadgeTone.Bad)
                    null -> {}
                }
            },
        )
        if (row.expanded) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = KtmSpacing.edge).padding(bottom = KtmSpacing.md),
                horizontalArrangement = Arrangement.spacedBy(KtmSpacing.sm),
            ) {
                KtmButton(stringResource(R.string.symbols_works), { actions.onMark(row, SymbolResult.WORKS) }, Modifier.weight(1f), ButtonStyle.Tonal, ButtonSize.Small, icon = KtmIcons.Done)
                KtmButton(stringResource(R.string.symbols_wrong), { actions.onMark(row, SymbolResult.WRONG) }, Modifier.weight(1f), ButtonStyle.DangerTonal, ButtonSize.Small, icon = KtmIcons.Close)
            }
        }
    }
}
