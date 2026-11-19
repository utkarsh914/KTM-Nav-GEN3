package com.navigator.app.ui.components

import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.navigator.app.R
import com.navigator.app.ui.theme.KtmIcons
import com.navigator.app.ui.theme.KtmLayout
import com.navigator.app.ui.theme.KtmSpacing
import com.navigator.app.ui.theme.KtmTheme

/**
 * 64 dp on every tab and page, even with no actions, so the large title below always lands in the
 * same place. It insets itself for the status bar
 * and cutout, so a page needs no extra padding. Actions are 48 dp icon buttons, never labelled buttons. [title] may be empty on a
 * tab root that shows a [LargeTitle] instead.
 */
@Composable
fun KtmTopBar(
    modifier: Modifier = Modifier,
    title: String = "",
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .fillMaxWidth().height(KtmLayout.topBar).padding(start = if (onBack != null) KtmSpacing.xs else KtmSpacing.edge, end = KtmSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(KtmSpacing.xs),
    ) {
        if (onBack != null) {
            KtmIconButton(KtmIcons.ArrowLeft, stringResource(R.string.common_back), onBack)
        }
        Text(
            title, style = MaterialTheme.typography.headlineSmall, color = KtmTheme.colors.fg,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.semantics { heading() }.weight(1f).padding(start = if (onBack != null) KtmSpacing.sm else 0.dp),
        )
        actions()
    }
}
