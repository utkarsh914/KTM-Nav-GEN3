package com.navigator.app.ui.feature.permissions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.navigator.app.R
import com.navigator.app.ui.components.ButtonStyle
import com.navigator.app.ui.components.KtmButton
import com.navigator.app.ui.components.KtmModalSheet
import com.navigator.app.ui.theme.KtmSpacing
import com.navigator.app.ui.theme.KtmTheme

/** DL10 / DL11: says what the next Android screen is for before leaving the app, so it isn't an unexplained jump. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionExplainerSheet(explainer: Explainer, onGo: () -> Unit, onDismiss: () -> Unit) {
    KtmModalSheet(onDismiss = onDismiss, title = stringResource(explainer.title)) {
        Column(verticalArrangement = Arrangement.spacedBy(KtmSpacing.lg)) {
            Text(stringResource(explainer.body), style = MaterialTheme.typography.bodyMedium, color = KtmTheme.colors.fg2)
            KtmButton(stringResource(explainer.go), onGo, Modifier.fillMaxWidth())
            KtmButton(stringResource(R.string.perm_explainer_cancel), onDismiss, Modifier.fillMaxWidth(), style = ButtonStyle.Text)
        }
    }
}
