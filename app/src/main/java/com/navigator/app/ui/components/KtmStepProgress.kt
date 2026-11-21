package com.navigator.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import com.navigator.app.R
import com.navigator.app.ui.theme.KtmIcons
import com.navigator.app.ui.theme.KtmLayout
import com.navigator.app.ui.theme.KtmShapes
import com.navigator.app.ui.theme.KtmSpacing
import com.navigator.app.ui.theme.KtmTheme

/** The first-run bar (ON2 to ON9): one segment per step, the done ones filled. Shared by onboarding and pairing. */
@Composable
fun KtmStepProgress(position: Int, total: Int, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.onb_progress, position, total)
    Row(
        modifier.semantics { contentDescription = description; progressBarRangeInfo = ProgressBarRangeInfo(position.toFloat(), 1f..total.toFloat(), total - 1) },
        horizontalArrangement = Arrangement.spacedBy(KtmSpacing.xs),
    ) {
        repeat(total) { i ->
            Box(
                Modifier.weight(1f).height(KtmSpacing.xs)
                    .background(if (i < position) KtmTheme.accent.fill else KtmTheme.colors.line2, KtmShapes.Pill),
            )
        }
    }
}

/**
 * The top of a first-run screen: the bar, then Back (and [trailing], like Skip) on a row of their own under it.
 * With no back the row keeps its height so the page does not jump between steps.
 */
@Composable
fun KtmStepHeader(
    position: Int,
    total: Int,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Column(modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)).fillMaxWidth()) {
        KtmStepProgress(position, total, Modifier.padding(horizontal = KtmSpacing.edge).padding(top = KtmSpacing.lg))
        Row(
            Modifier.fillMaxWidth().height(KtmLayout.targetRest).padding(horizontal = KtmSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) KtmIconButton(KtmIcons.ArrowLeft, stringResource(R.string.common_back), onBack)
            Spacer(Modifier.weight(1f))
            trailing()
        }
    }
}
