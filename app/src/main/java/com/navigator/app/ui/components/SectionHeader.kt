package com.navigator.app.ui.components

import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.navigator.app.ui.theme.KtmSpacing
import com.navigator.app.ui.theme.KtmTheme

/** The only section-header style: titleSmall, 24 dp above and 8 dp below. */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier, trailing: String? = null) {
    androidx.compose.foundation.layout.Row(
        modifier.fillMaxWidth().padding(start = KtmSpacing.lg, end = KtmSpacing.lg, top = KtmSpacing.xl, bottom = KtmSpacing.sm),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Text(
            text, style = MaterialTheme.typography.titleSmall, color = KtmTheme.colors.fg2,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        // What is chosen, at the right (the accent's name).
        if (trailing != null) Text(trailing, style = MaterialTheme.typography.bodySmall, color = KtmTheme.colors.fg2)
    }
}
