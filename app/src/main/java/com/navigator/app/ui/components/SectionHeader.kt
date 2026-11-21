package com.navigator.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.navigator.app.ui.theme.KtmLayout
import com.navigator.app.ui.theme.KtmSpacing
import com.navigator.app.ui.theme.KtmTheme

/** The only section-header style: titleSmall, 24 dp above and 8 dp below. */
@Composable
fun SectionHeader(
    text: String,
    modifier: Modifier = Modifier,
    trailing: String? = null,
    /** An accent link at the right ("See all"); its touch area is widened to 48 dp by Compose. */
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth().padding(start = KtmSpacing.lg, end = KtmSpacing.lg, top = KtmSpacing.xl, bottom = KtmSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text, style = MaterialTheme.typography.titleSmall, color = KtmTheme.colors.fg2,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        // What is chosen, at the right (the accent's name).
        if (trailing != null) {
            Text(
                trailing, style = MaterialTheme.typography.bodySmall, color = KtmTheme.colors.fg2, maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = KtmLayout.sectionTrailingMax).padding(start = KtmSpacing.sm),
            )
        }
        if (action != null && onAction != null) {
            Text(
                action, style = MaterialTheme.typography.labelLarge, color = KtmTheme.accent.text, maxLines = 1,
                // The header keeps one rhythm with or without a link: the text is its natural height, and Compose widens
                // the touch area of a clickable to 48 dp (the minimum touch target) around it.
                modifier = Modifier.clickable(role = Role.Button, onClick = onAction).padding(start = KtmSpacing.md),
            )
        }
    }
}
