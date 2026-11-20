package com.navigator.app.ui.components

import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.navigator.app.ui.theme.KtmLayout
import com.navigator.app.ui.theme.KtmTheme

/** A row of a top-bar overflow menu, at least 56 dp tall. */
@Composable
fun KtmMenuItem(text: String, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(text, style = MaterialTheme.typography.bodyLarge, color = KtmTheme.colors.fg) },
        onClick = onClick,
        modifier = Modifier.heightIn(min = KtmLayout.row1),
    )
}
