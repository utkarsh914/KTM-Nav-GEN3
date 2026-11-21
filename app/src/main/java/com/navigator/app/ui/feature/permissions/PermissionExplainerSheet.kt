package com.navigator.app.ui.feature.permissions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.navigator.app.R
import com.navigator.app.ui.components.ButtonStyle
import com.navigator.app.ui.components.KtmButton
import com.navigator.app.ui.components.KtmModalSheet
import com.navigator.app.ui.components.KtmStep
import com.navigator.app.ui.components.KtmSwitch
import com.navigator.app.ui.components.StepState
import com.navigator.app.ui.theme.KtmIcons
import com.navigator.app.ui.theme.KtmLayout
import com.navigator.app.ui.theme.KtmShapes
import com.navigator.app.ui.theme.KtmSpacing
import com.navigator.app.ui.theme.KtmTheme

/** DL10 / DL11: says what the next Android screen is for before leaving the app, so it isn't an unexplained jump. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionExplainerSheet(explainer: Explainer, onGo: () -> Unit, onDismiss: () -> Unit) {
    val c = KtmTheme.colors
    KtmModalSheet(onDismiss = onDismiss, title = stringResource(explainer.title)) {
        Column(verticalArrangement = Arrangement.spacedBy(KtmSpacing.sm)) {
            Text(stringResource(explainer.body), style = MaterialTheme.typography.bodyMedium, color = c.fg2)
            explainer.steps.forEachIndexed { i, step -> KtmStep(i + 1, stringResource(step), StepState.Active) }
            if (explainer.appRow) AppRow()
            explainer.note?.let { Text(stringResource(it), style = MaterialTheme.typography.bodySmall, color = c.fg2) }
            Row(Modifier.padding(top = KtmSpacing.sm), horizontalArrangement = Arrangement.spacedBy(KtmSpacing.sm)) {
                KtmButton(stringResource(R.string.perm_explainer_cancel), onDismiss, Modifier.weight(1f), style = ButtonStyle.Outline)
                KtmButton(stringResource(explainer.go), onGo, Modifier.weight(1f))
            }
        }
    }
}

/** What Android's list will show for this app: its name and a switch (here only a picture of it). */
@Composable
private fun AppRow() {
    Row(
        Modifier.fillMaxWidth().background(KtmTheme.colors.surf2, KtmShapes.Card).padding(horizontal = KtmSpacing.lg, vertical = KtmSpacing.md)
            .clearAndSetSemantics {},
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(KtmSpacing.md),
    ) {
        Box(Modifier.size(KtmLayout.appIcon).background(KtmTheme.accent.container, KtmShapes.Control), contentAlignment = Alignment.Center) {
            Icon(KtmIcons.Navigation, null, tint = KtmTheme.accent.onContainer)
        }
        Text(stringResource(R.string.perm_access_app), style = MaterialTheme.typography.titleMedium, color = KtmTheme.colors.fg, modifier = Modifier.weight(1f))
        KtmSwitch(checked = true, onCheckedChange = null)
    }
}
