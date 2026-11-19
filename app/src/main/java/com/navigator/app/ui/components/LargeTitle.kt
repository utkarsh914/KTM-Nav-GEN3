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

/** Tab-root heading, placed directly under the 64 dp top bar. */
@Composable
fun LargeTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text, style = MaterialTheme.typography.headlineLarge, color = KtmTheme.colors.fg,
        modifier = modifier.semantics { heading() }.fillMaxWidth().padding(horizontal = KtmSpacing.edge).padding(top = KtmSpacing.lg, bottom = KtmSpacing.sm),
    )
}
