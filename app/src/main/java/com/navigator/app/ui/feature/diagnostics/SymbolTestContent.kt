package com.navigator.app.ui.feature.diagnostics

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.navigator.app.R
import com.navigator.app.ui.components.BadgeTone
import com.navigator.app.ui.components.ButtonSize
import com.navigator.app.ui.components.ButtonStyle
import com.navigator.app.ui.components.KtmBadge
import com.navigator.app.ui.components.KtmButton
import com.navigator.app.ui.components.KtmCard
import com.navigator.app.ui.components.KtmIconButton
import com.navigator.app.ui.components.KtmModalSheet
import com.navigator.app.ui.components.KtmSegmented
import com.navigator.app.ui.components.KtmTopBar
import com.navigator.app.ui.components.TurnIconRef
import com.navigator.app.ui.theme.KtmIcons
import com.navigator.app.ui.theme.KtmLayout
import com.navigator.app.ui.theme.KtmShapes
import com.navigator.app.ui.theme.KtmSpacing
import com.navigator.app.ui.theme.KtmTheme

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
    val turnLabel = stringResource(R.string.symbols_turn)
    val notificationLabel = stringResource(R.string.symbols_notification)
    Column(modifier.fillMaxSize().background(c.bg)) {
        KtmTopBar(title = stringResource(R.string.symbols_title), onBack = actions.onBack) {
            KtmIconButton(KtmIcons.Help, stringResource(R.string.symbols_info), { actions.onInfo(true) })
        }
        LazyColumn(
            contentPadding = PaddingValues(horizontal = KtmSpacing.edge, vertical = KtmSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(KtmSpacing.sm),
            modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)),
        ) {
            item {
                Text(stringResource(R.string.symbols_intro), style = MaterialTheme.typography.bodyMedium, color = c.fg2)
            }
            item {
                KtmSegmented(
                    SymbolKind.entries, ui.kind, actions.onKind,
                    { if (it == SymbolKind.TURN) turnLabel else notificationLabel },
                    Modifier.fillMaxWidth(),
                )
            }
            item {
                Text(
                    stringResource(R.string.symbols_summary, ui.works, ui.wrong, ui.rows.size),
                    style = MaterialTheme.typography.labelMedium, color = c.fg3,
                )
            }
            items(ui.rows, key = { it.key }) { row -> SymbolRowCard(row, actions) }
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
private fun SymbolRowCard(row: SymbolRow, actions: SymbolTestActions) {
    val c = KtmTheme.colors
    KtmCard(Modifier.fillMaxWidth().animateContentSize()) {
        Row(
            Modifier.fillMaxWidth().padding(KtmSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(KtmSpacing.md),
        ) {
            row.turn?.let {
                Box(Modifier.size(KtmLayout.targetRest).background(c.surf3, KtmShapes.Control), contentAlignment = Alignment.Center) {
                    TurnIconRef(icon = it, size = KtmLayout.diagGlyphSmall, color = c.fg)
                }
            }
            Column(Modifier.weight(1f)) {
                Text(row.name, style = MaterialTheme.typography.titleSmall, color = c.fg, maxLines = 1)
                Text(row.code, style = KtmTheme.type.mono, color = c.fg3, maxLines = 1)
            }
            when (row.result) {
                SymbolResult.WORKS -> KtmBadge(stringResource(R.string.symbols_works), tone = BadgeTone.Ok)
                SymbolResult.WRONG -> KtmBadge(stringResource(R.string.symbols_wrong), tone = BadgeTone.Bad)
                null -> {}
            }
            val sendLabel = stringResource(R.string.symbols_send_named, row.name)
            KtmButton(
                stringResource(R.string.symbols_send), { actions.onSend(row) },
                Modifier.semantics { contentDescription = sendLabel }, ButtonStyle.Tonal, ButtonSize.Small,
            )
        }
        if (row.expanded) {
            Column(Modifier.padding(start = KtmSpacing.md, end = KtmSpacing.md, bottom = KtmSpacing.md), verticalArrangement = Arrangement.spacedBy(KtmSpacing.sm)) {
                Text(stringResource(R.string.symbols_question), style = MaterialTheme.typography.bodyMedium, color = c.fg2)
                Row(horizontalArrangement = Arrangement.spacedBy(KtmSpacing.md)) {
                    KtmButton(stringResource(R.string.symbols_works), { actions.onMark(row, SymbolResult.WORKS) }, Modifier.weight(1f), ButtonStyle.Primary, ButtonSize.Rest, icon = KtmIcons.Done)
                    KtmButton(stringResource(R.string.symbols_wrong), { actions.onMark(row, SymbolResult.WRONG) }, Modifier.weight(1f), ButtonStyle.DangerTonal, ButtonSize.Rest, icon = KtmIcons.Close)
                }
            }
        }
    }
}
