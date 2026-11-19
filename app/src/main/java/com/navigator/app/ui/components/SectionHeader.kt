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
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = KtmTheme.colors.fg2,
        modifier = modifier.semantics { heading() }.fillMaxWidth().padding(start = KtmSpacing.lg, end = KtmSpacing.lg, top = KtmSpacing.xl, bottom = KtmSpacing.sm),
    )
}
