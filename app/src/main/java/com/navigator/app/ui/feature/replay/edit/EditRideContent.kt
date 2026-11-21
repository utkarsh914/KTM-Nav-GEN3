package com.navigator.app.ui.feature.replay.edit

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.navigator.app.R
import com.navigator.app.ui.components.EmptyState
import com.navigator.app.ui.components.KtmSwitchRow
import com.navigator.app.ui.components.KtmTextField
import com.navigator.app.ui.components.KtmTopBar
import com.navigator.app.ui.components.ListDivider
import com.navigator.app.ui.components.ListRow
import com.navigator.app.ui.components.SkeletonRow
import com.navigator.app.ui.components.fullBleed
import com.navigator.app.ui.theme.KtmIcons
import com.navigator.app.ui.theme.KtmLayout
import com.navigator.app.ui.theme.KtmSpacing
import com.navigator.app.ui.theme.KtmTheme
import com.navigator.app.ui.theme.pageInsets

class EditRideActions(
    val onBack: () -> Unit,
    val onName: (String) -> Unit,
    val onNotes: (String) -> Unit,
    val onTrim: () -> Unit,
    val onToggleStar: (Boolean) -> Unit,
    val onDelete: () -> Unit,
)

/** RP4: name, notes, then Trim, Keep forever and Delete. Changes save as they are made, so there is no Save button. Stateless. */
@Composable
fun EditRideContent(ui: EditUi, actions: EditRideActions, modifier: Modifier = Modifier) {
    val c = KtmTheme.colors
    Column(modifier.fillMaxSize().background(c.bg)) {
        KtmTopBar(title = stringResource(R.string.edit_ride_title), onBack = actions.onBack)
        val motionSpecs = KtmTheme.motion
        AnimatedContent(targetState = ui, modifier = Modifier.pageInsets(), contentKey = { it::class }, transitionSpec = { fadeIn(motionSpecs.effectsDefault()) togetherWith fadeOut(motionSpecs.effectsFast()) using SizeTransform { _, _ -> snap() } }, label = "loading") { shown ->
            when (shown) {
                EditUi.Loading -> Column { repeat(2) { SkeletonRow(announce = it == 0) } }
                EditUi.Gone -> EmptyState(KtmIcons.Warning, stringResource(R.string.edit_ride_gone_title), stringResource(R.string.edit_ride_gone_message))
                is EditUi.Ready -> Form(shown, actions)
            }
        }
    }
}

@Composable
private fun Form(ui: EditUi.Ready, actions: EditRideActions) {
    val c = KtmTheme.colors
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = KtmSpacing.edge),
        verticalArrangement = Arrangement.spacedBy(KtmSpacing.md),
    ) {
        KtmTextField(ui.name, actions.onName, stringResource(R.string.edit_ride_name), Modifier.fillMaxWidth())
        KtmTextField(
            ui.notes, actions.onNotes, stringResource(R.string.edit_ride_notes),
            Modifier.fillMaxWidth().heightIn(min = KtmLayout.notesMin), singleLine = false,
        )
        Text(stringResource(R.string.edit_ride_saved_hint), style = MaterialTheme.typography.bodySmall, color = c.fg2)
        ListRow(
            modifier = Modifier.fullBleed(),
            title = stringResource(R.string.edit_ride_trim), supporting = stringResource(R.string.edit_ride_trim_supporting),
            leadingIcon = KtmIcons.Trim, onClick = actions.onTrim,
            trailing = { Icon(KtmIcons.ChevronRight, null, tint = c.fg2) },
        )
        ListDivider(Modifier.fullBleed())
        KtmSwitchRow(
            modifier = Modifier.fullBleed(),
            title = stringResource(R.string.edit_ride_star), checked = ui.saved, onCheckedChange = actions.onToggleStar,
            supporting = stringResource(R.string.edit_ride_star_supporting), supportingLines = 3, leadingIcon = KtmIcons.Star,
        )
        // RP4: Delete is the last row of the list, in the danger colour.
        ListDivider(Modifier.fullBleed())
        ListRow(
            title = stringResource(R.string.edit_ride_delete), leadingIcon = KtmIcons.Delete, onClick = actions.onDelete, destructive = true,
            modifier = Modifier.fullBleed().padding(bottom = KtmSpacing.lg),
        )
    }
}
