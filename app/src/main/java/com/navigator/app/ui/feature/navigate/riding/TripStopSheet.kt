package com.navigator.app.ui.feature.navigate.riding

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.navigator.app.R
import com.navigator.app.nav.destination.CategoryResult
import com.navigator.app.nav.destination.PlaceCategory
import com.navigator.app.nav.ktm.DistanceFormatter
import com.navigator.app.nav.model.DistanceUnits
import com.navigator.app.ui.components.ButtonSize
import com.navigator.app.ui.components.ButtonStyle
import com.navigator.app.ui.components.KtmButton
import com.navigator.app.ui.components.KtmFilterChip
import com.navigator.app.ui.components.KtmModalSheet
import com.navigator.app.ui.components.ListDivider
import com.navigator.app.ui.components.Skeleton
import com.navigator.app.ui.components.fullBleed
import com.navigator.app.ui.feature.navigate.search.labelRes
import com.navigator.app.ui.theme.KtmIcons
import com.navigator.app.ui.theme.KtmLayout
import com.navigator.app.ui.theme.KtmShapes
import com.navigator.app.ui.theme.KtmSpacing
import com.navigator.app.ui.theme.KtmTheme
import com.navigator.app.ui.theme.rememberHaptics

data class TripStopUi(
    val left: Int,
    val online: Boolean,
    val category: PlaceCategory?,
    val loading: Boolean,
    val failed: Boolean,
    val results: List<CategoryResult>,
    val destination: String? = null,
)

class TripStopActions(
    val onCategory: (PlaceCategory) -> Unit,
    val onAdd: (CategoryResult) -> Unit,
    val onSearchByName: () -> Unit,
    val onRetry: () -> Unit,
    val onDismiss: () -> Unit,
)

/**
 * SE4: add a stop to the running trip. Category first, then 76 dp rows with a separate 56 dp Add button, so a
 * scroll never adds one. The sheet says how many stops are left up front; offline the Add buttons are disabled
 * with "Needs internet". Free-text search is one tap away (the full search). On success the host closes the sheet
 * and a riding chip confirms.
 */
@OptIn(ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun TripStopSheet(ui: TripStopUi, actions: TripStopActions) {
    val c = KtmTheme.colors
    val canAdd = TripStopRules.canAdd(ui.left, ui.online)
    KtmModalSheet(onDismiss = actions.onDismiss, title = stringResource(R.string.trip_stop_title), scrollable = true, onClose = actions.onDismiss) {
        val left = when {
            ui.left == 0 -> stringResource(R.string.trip_stop_full)
            else -> pluralStringResource(R.plurals.trip_stop_left, ui.left, ui.left, TripStopRules.MAX)
        }
        Text(
            ui.destination?.takeIf { it.isNotBlank() }?.let { stringResource(R.string.trip_stop_subtitle, it, left) } ?: left,
            style = MaterialTheme.typography.bodyMedium, color = c.fg2,
        )
        if (!ui.online) {
            Text(stringResource(R.string.trip_stop_offline), style = MaterialTheme.typography.labelMedium, color = c.warn)
        }
        Spacer(Modifier.height(KtmSpacing.md))
        SearchPill(actions.onSearchByName)
        Spacer(Modifier.height(KtmSpacing.md))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(KtmSpacing.sm), verticalArrangement = Arrangement.spacedBy(KtmSpacing.sm)) {
            PlaceCategory.entries.forEach { category ->
                KtmFilterChip(stringResource(category.labelRes()), selected = ui.category == category, onClick = { actions.onCategory(category) }, riding = true)
            }
        }
        Spacer(Modifier.height(KtmSpacing.xs))
        when {
            ui.category == null -> Hint(R.string.trip_stop_pick_category)
            ui.loading -> repeat(3) { Skeleton(Modifier.fillMaxWidth().height(KtmLayout.rowStop).padding(vertical = KtmSpacing.xs), KtmShapes.Control) }
            ui.failed -> {
                Hint(R.string.trip_stop_failed)
                KtmButton(stringResource(R.string.start_failed_retry), actions.onRetry, Modifier.fillMaxWidth(), style = ButtonStyle.Tonal)
            }
            ui.results.isEmpty() -> Hint(R.string.trip_stop_none)
            else -> ui.results.forEachIndexed { i, result -> ResultRow(result, canAdd, divided = i > 0) { actions.onAdd(result) } }
        }
    }
}

/** SE4: the field that opens the full search (free text, voice), 56 dp for a glove. */
@Composable
private fun SearchPill(onClick: () -> Unit) {
    val c = KtmTheme.colors
    val haptics = rememberHaptics()
    Row(
        Modifier.fillMaxWidth().heightIn(min = KtmLayout.targetPrimary).clip(KtmShapes.Pill).background(c.surf2)
            .clickable(role = Role.Button) { haptics.tap(); onClick() }.padding(horizontal = KtmSpacing.lg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(KtmSpacing.md),
    ) {
        Icon(KtmIcons.Search, null, tint = c.fg2)
        Text(stringResource(R.string.trip_stop_search_name), style = MaterialTheme.typography.bodyLarge, color = c.fg2, maxLines = 1)
    }
}

@Composable
private fun Hint(@androidx.annotation.StringRes res: Int) {
    Text(
        stringResource(res), style = MaterialTheme.typography.bodyMedium, color = KtmTheme.colors.fg2,
        modifier = Modifier.padding(vertical = KtmSpacing.md).semantics { liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite },
    )
}

@Composable
private fun ResultRow(result: CategoryResult, canAdd: Boolean, divided: Boolean, onAdd: () -> Unit) {
    val c = KtmTheme.colors
    val distance = result.distanceMeters?.let { DistanceFormatter.format(it, DistanceUnits.METRIC) }
    val supporting = listOfNotNull(distance, result.address).joinToString(" · ")
    val addDescription = stringResource(R.string.trip_stop_add_description, result.name)
    Column(Modifier.fillMaxWidth().fullBleed()) {
    if (divided) ListDivider(inset = KtmSpacing.lg)
    Row(
        Modifier.fillMaxWidth().heightIn(min = KtmLayout.rowStop).padding(horizontal = KtmSpacing.lg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(KtmSpacing.md),
    ) {
        Column(Modifier.weight(1f)) {
            Text(result.name, style = MaterialTheme.typography.titleMedium, color = c.fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (supporting.isNotEmpty()) {
                Text(supporting, style = MaterialTheme.typography.bodySmall, color = c.fg2, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        KtmButton(
            stringResource(R.string.trip_stop_add), onAdd, Modifier.semantics { contentDescription = addDescription },
            style = ButtonStyle.Tonal, size = ButtonSize.Rest, enabled = canAdd, icon = KtmIcons.Add,
        )
    }
    }
}
