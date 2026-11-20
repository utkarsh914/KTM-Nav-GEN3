package com.navigator.app.ui.feature.navigate.riding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.navigator.app.R
import com.navigator.app.nav.providers.StartFailure
import com.navigator.app.ui.components.ButtonSize
import com.navigator.app.ui.components.ButtonStyle
import com.navigator.app.ui.components.KtmButton
import com.navigator.app.ui.components.SheetHandle
import com.navigator.app.ui.theme.KtmElevation
import com.navigator.app.ui.theme.KtmShapes
import com.navigator.app.ui.theme.KtmSpacing
import com.navigator.app.ui.theme.KtmTheme

class StartFailedActions(
    val onRetry: () -> Unit,
    val onBack: () -> Unit,
    val onClose: () -> Unit,
    val onMirror: () -> Unit,
)

/**
 * NV6: the trip could not start. A sheet at the foot of the screen, within thumb reach, with a 64 dp Try again,
 * the way back to the route, and a plain Close. When the phone cannot run in-app navigation at all, retrying is
 * pointless and Switch to mirror mode takes its place.
 */
@Composable
fun StartFailedSheet(failure: StartFailure, canRetry: Boolean, actions: StartFailedActions, modifier: Modifier = Modifier) {
    val ui = StartFailureMapper.of(failure)
    val r = KtmTheme.riding
    Column(
        modifier.fillMaxWidth().shadow(KtmElevation.sheet, KtmShapes.SheetTop).background(r.surface, KtmShapes.SheetTop)
            .padding(horizontal = KtmSpacing.lg).windowInsetsPadding(WindowInsets.navigationBars).padding(bottom = KtmSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(KtmSpacing.sm),
    ) {
        SheetHandle()
        Text(
            stringResource(R.string.start_failed_title), style = MaterialTheme.typography.headlineSmall, color = r.onSurface,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            stringResource(ui.message), style = MaterialTheme.typography.bodyLarge, color = r.onSurfaceVariant,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        if (ui.canRetry && canRetry) {
            KtmButton(stringResource(R.string.start_failed_retry), actions.onRetry, Modifier.fillMaxWidth(), size = ButtonSize.Riding)
        }
        if (ui.offerMirror) {
            KtmButton(stringResource(R.string.start_failed_mirror), actions.onMirror, Modifier.fillMaxWidth(), size = ButtonSize.Riding)
        }
        KtmButton(
            stringResource(R.string.start_failed_back), actions.onBack, Modifier.fillMaxWidth(),
            style = ButtonStyle.Tonal, size = ButtonSize.Rest,
        )
        KtmButton(
            stringResource(R.string.common_close), actions.onClose, Modifier.fillMaxWidth(),
            style = ButtonStyle.Text, size = ButtonSize.Small,
        )
    }
}
