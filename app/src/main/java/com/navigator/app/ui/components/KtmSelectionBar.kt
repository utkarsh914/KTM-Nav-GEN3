package com.navigator.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.navigator.app.ui.theme.KtmIcons
import com.navigator.app.ui.theme.KtmLayout
import com.navigator.app.ui.theme.KtmSpacing
import com.navigator.app.ui.theme.KtmTheme
import com.navigator.app.ui.theme.pageInsets

/** The tinted contextual bar that takes the top bar's place while selecting: close, the count, then [actions]. */
@Composable
fun KtmSelectionBar(title: String, closeLabel: String, onClose: () -> Unit, actions: @Composable RowScope.() -> Unit) {
    val a = KtmTheme.accent
    Row(
        Modifier.background(a.container)
            .pageInsets(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
            .fillMaxWidth().height(KtmLayout.topBar).padding(start = KtmSpacing.xs, end = KtmSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(KtmSpacing.xs),
    ) {
        KtmIconButton(KtmIcons.Close, closeLabel, onClose, tint = a.onContainer)
        Text(
            title, style = MaterialTheme.typography.titleLarge, color = a.onContainer, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        actions()
    }
}
