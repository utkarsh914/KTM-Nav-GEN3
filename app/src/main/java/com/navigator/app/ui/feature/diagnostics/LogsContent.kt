package com.navigator.app.ui.feature.diagnostics

import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.navigator.app.R
import com.navigator.app.ui.components.EmptyState
import com.navigator.app.ui.components.KtmFilterChip
import com.navigator.app.ui.components.KtmIconButton
import com.navigator.app.ui.components.KtmSearchField
import com.navigator.app.ui.components.KtmTopBar
import com.navigator.app.ui.theme.KtmIcons
import com.navigator.app.ui.theme.KtmLayout
import com.navigator.app.ui.theme.KtmShapes
import com.navigator.app.ui.theme.KtmSpacing
import com.navigator.app.ui.theme.KtmTheme

class LogsActions(
    val onBack: () -> Unit,
    val onShare: () -> Unit,
    val onTag: (String?) -> Unit,
    val onQuery: (String) -> Unit,
)

/** DG1: timestamp split from message, warning and OK tints, a chip per subsystem, search and a Following live pill. Stateless. */
@Composable
fun LogsContent(ui: LogsUi, actions: LogsActions, modifier: Modifier = Modifier) {
    val c = KtmTheme.colors
    val listState = rememberLazyListState()
    var following by rememberSaveable { mutableStateOf(true) }

    // Scrolling by hand pauses following; reaching the end again resumes it.
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress to listState.canScrollForward }.collect { (moving, more) -> if (moving) following = !more }
    }
    LaunchedEffect(ui.entries.size, ui.entries.lastOrNull(), following) {
        if (following && ui.entries.isNotEmpty()) listState.scrollToItem(ui.entries.lastIndex)
    }

    Column(modifier.fillMaxSize().background(c.bg)) {
        KtmTopBar(title = stringResource(R.string.logs_title), onBack = actions.onBack) {
            KtmIconButton(KtmIcons.Share, stringResource(R.string.logs_share), actions.onShare)
        }
        KtmSearchField(
            ui.query, actions.onQuery, stringResource(R.string.logs_search),
            Modifier.fillMaxWidth().padding(horizontal = KtmSpacing.edge),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = KtmSpacing.edge), horizontalArrangement = Arrangement.spacedBy(KtmSpacing.sm),
            modifier = Modifier.padding(top = KtmSpacing.xs),
        ) {
            item { KtmFilterChip(stringResource(R.string.logs_all), ui.tag == null, { actions.onTag(null) }) }
            items(ui.tags, key = { it }) { tag -> KtmFilterChip(tag, ui.tag == tag, { actions.onTag(if (ui.tag == tag) null else tag) }) }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                ui.entries.isEmpty() -> EmptyState(
                    KtmIcons.Terminal,
                    stringResource(if (ui.filteredOut) R.string.logs_no_match_title else R.string.logs_empty_title),
                    stringResource(if (ui.filteredOut) R.string.logs_no_match else R.string.logs_empty),
                )
                else -> LazyColumn(
                    state = listState,
                    // Room under the pill, so the newest lines are never behind it.
                    contentPadding = PaddingValues(start = KtmSpacing.edge, end = KtmSpacing.edge, top = KtmSpacing.sm, bottom = KtmLayout.targetRest + KtmSpacing.xl),
                    modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)),
                ) {
                    itemsIndexed(ui.entries, key = { index, e -> e.key.ifEmpty { "#$index" } }) { _, e ->
                        LogRow(e, Modifier.animateItem(fadeInSpec = KtmTheme.motion.effectsFast(), placementSpec = null))
                    }
                }
            }
            FollowPill(
                following, { following = !following },
                Modifier.align(Alignment.BottomCenter).padding(bottom = KtmSpacing.lg)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)),
            )
        }
    }
}

@Composable
private fun LogRow(entry: LogEntry, modifier: Modifier = Modifier) {
    val c = KtmTheme.colors
    val tint = when (entry.level) {
        LogLevel.Warn -> c.warn
        LogLevel.Ok -> c.ok
        LogLevel.Info -> c.fg
    }
    val shape = KtmShapes.Badge
    Row(
        modifier.fillMaxWidth().padding(vertical = KtmLayout.hairline)
            .then(if (entry.level == LogLevel.Warn) Modifier.background(c.warnContainer, shape) else Modifier)
            .padding(horizontal = KtmSpacing.xs, vertical = KtmLayout.logRowPadding),
        horizontalArrangement = Arrangement.spacedBy(KtmSpacing.sm),
    ) {
        Text(entry.time, style = KtmTheme.type.mono.copy(fontWeight = FontWeight.Normal), color = c.fg3, maxLines = 1)
        Column(Modifier.weight(1f)) {
            if (entry.tag.isNotEmpty()) Text(entry.tag, style = MaterialTheme.typography.labelSmall, color = c.fg2)
            Text(entry.message, style = KtmTheme.type.mono, color = tint)
        }
    }
}

/** "Following live": tap to pause the auto-scroll, tap again to follow. */
@Composable
private fun FollowPill(following: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val c = KtmTheme.colors
    val a = KtmTheme.accent
    val label = stringResource(if (following) R.string.logs_following else R.string.logs_paused)
    Row(
        modifier.heightIn(min = KtmLayout.targetRest)
            .background(if (following) a.container else c.surf3, KtmShapes.Pill)
            .toggleable(value = following, role = Role.Switch, onValueChange = { onToggle() })
            .padding(horizontal = KtmSpacing.lg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(KtmSpacing.sm),
    ) {
        Icon(if (following) KtmIcons.Pause else KtmIcons.Play, null, tint = if (following) a.onContainer else c.fg, modifier = Modifier.size(KtmLayout.iconSmall))
        Text(label, style = MaterialTheme.typography.labelMedium, color = if (following) a.onContainer else c.fg)
    }
}
