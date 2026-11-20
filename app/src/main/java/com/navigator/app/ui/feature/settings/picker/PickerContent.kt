package com.navigator.app.ui.feature.settings.picker

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.navigator.app.R
import com.navigator.app.ui.components.KtmCheckIndicator
import com.navigator.app.ui.components.KtmIconButton
import com.navigator.app.ui.components.KtmRadioRow
import com.navigator.app.ui.components.KtmSearchField
import com.navigator.app.ui.components.KtmTopBar
import com.navigator.app.ui.components.ListRow
import com.navigator.app.ui.components.SectionHeader
import com.navigator.app.ui.components.SkeletonRow
import com.navigator.app.ui.theme.KtmIcons
import com.navigator.app.ui.theme.KtmSpacing
import com.navigator.app.ui.theme.KtmTheme
import com.navigator.app.ui.theme.rememberHaptics

class AppPickerActions(val onBack: () -> Unit, val onQuery: (String) -> Unit, val onToggle: (String) -> Unit)

/** ST11: a full-screen picker with search and app icons; 56 dp rows with the checkbox on the right. Stateless. */
@Composable
fun AppPickerContent(ui: AppPickerUi, actions: AppPickerActions, modifier: Modifier = Modifier) {
    val c = KtmTheme.colors
    val haptics = rememberHaptics()
    Column(modifier.fillMaxSize().background(c.bg)) {
        KtmTopBar(title = stringResource(R.string.picker_apps_title), onBack = actions.onBack)
        KtmSearchField(
            ui.query, actions.onQuery, stringResource(R.string.picker_search),
            Modifier.padding(horizontal = KtmSpacing.edge, vertical = KtmSpacing.sm),
            trailing = {
                if (ui.query.isNotEmpty()) KtmIconButton(KtmIcons.Close, stringResource(R.string.picker_search_clear), { actions.onQuery("") })
            },
        )
        Text(
            stringResource(R.string.picker_apps_hint), style = MaterialTheme.typography.bodySmall, color = c.fg2,
            modifier = Modifier.padding(horizontal = KtmSpacing.edge).padding(bottom = KtmSpacing.xs),
        )
        when {
            ui.loading -> Column { repeat(5) { SkeletonRow(announce = it == 0) } }
            ui.sections.isEmpty() -> Text(
                stringResource(R.string.picker_none_found), style = MaterialTheme.typography.bodyMedium, color = c.fg2,
                modifier = Modifier.padding(KtmSpacing.edge),
            )
            else -> LazyColumn(Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))) {
                ui.sections.forEachIndexed { index, section ->
                    section.title?.let { title -> item(key = "h$index") { SectionHeader(title.resolve()) } }
                    items(section.apps, key = { "$index-${it.packageName}" }) { app ->
                        val checked = app.packageName in ui.selected
                        ListRow(
                            title = app.label,
                            leading = { AppIcon(app.packageName) },
                            modifier = Modifier.toggleable(checked, role = Role.Checkbox, onValueChange = { haptics.toggle(it); actions.onToggle(app.packageName) }),
                            trailing = { KtmCheckIndicator(checked) },
                        )
                    }
                }
            }
        }
    }
}

class AudioPickerActions(val onBack: () -> Unit, val onChoose: (String?) -> Unit)

/** DL8: choose one device (or none) from a short list. Stateless. */
@Composable
fun AudioPickerContent(ui: AudioPickerUi, actions: AudioPickerActions, modifier: Modifier = Modifier) {
    val c = KtmTheme.colors
    Column(modifier.fillMaxSize().background(c.bg)) {
        KtmTopBar(title = stringResource(R.string.picker_audio_title), onBack = actions.onBack)
        if (ui.loading) Column { repeat(3) { SkeletonRow(announce = it == 0) } }
        else Column(Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))) {
            KtmRadioRow(stringResource(R.string.picker_audio_none), selected = ui.selected == null, onSelect = { actions.onChoose(null) })
            // A saved choice that can't be listed now stays visible and selected: it is still in force.
            if (ui.selectedUnavailable) {
                KtmRadioRow(stringResource(R.string.picker_audio_unavailable), selected = true, onSelect = { actions.onChoose(ui.selected) })
            }
            ui.devices.forEach { KtmRadioRow(it.name, selected = it.address == ui.selected, onSelect = { actions.onChoose(it.address) }) }
            if (ui.devices.isEmpty()) {
                Text(
                    stringResource(if (ui.permissionDenied) R.string.picker_audio_permission else R.string.picker_audio_empty),
                    style = MaterialTheme.typography.bodyMedium, color = c.fg2, modifier = Modifier.padding(KtmSpacing.edge),
                )
            }
        }
    }
}
