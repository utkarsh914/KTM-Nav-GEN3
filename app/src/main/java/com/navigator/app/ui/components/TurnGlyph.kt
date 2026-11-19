package com.navigator.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.navigator.app.ble.BccuProtocol.TurnIcon

/**
 * The official dash turn glyph, so the phone and the dash show identical arrows. A separate family
 * from [com.navigator.app.ui.theme.KtmIcons]: used for guidance only, at 56-72 dp while riding.
 */
@Composable
fun TurnGlyph(icon: TurnIcon, tint: Color, modifier: Modifier = Modifier, size: Dp = 56.dp) {
    TurnIconRef(icon = icon, modifier = modifier, size = size, color = tint)
}
