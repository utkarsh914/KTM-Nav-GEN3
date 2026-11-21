package com.navigator.app.ui.feature.navigate.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.onSizeChanged
import com.navigator.app.ui.components.LandscapePanel
import com.navigator.app.ui.components.SheetHandle
import com.navigator.app.ui.components.consumeMapTouches
import com.navigator.app.ui.theme.KtmElevation
import com.navigator.app.ui.theme.KtmShapes
import com.navigator.app.ui.theme.KtmSpacing
import com.navigator.app.ui.theme.KtmTheme

/** True inside the landscape panel: a sheet fills its height so its main button stays at the foot (PL5). */
val LocalFillPanel = compositionLocalOf { false }

/**
 * Where the place and route sheets sit: a bottom sheet in portrait, a 352 dp side panel on the cutout side in
 * landscape. It tells the host how much of the map it covers ([onInsets]: left, bottom, in px) so the camera
 * can frame the route in the part that is left open.
 */
@Composable
fun PlanPanel(
    onInsets: (leftPx: Int, bottomPx: Int) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = KtmTheme.colors
    val report by rememberUpdatedState(onInsets)
    BoxWithConstraints(modifier.fillMaxSize()) {
        if (maxWidth > maxHeight) {
            LandscapePanel(onFootprint = { report(it, 0) }) {
                CompositionLocalProvider(LocalFillPanel provides true) { content() }
            }
        } else {
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .heightIn(max = maxHeight * 0.85f)
                    .onSizeChanged { report(0, it.height) }
                    .shadow(KtmElevation.sheet, KtmShapes.SheetTop)
                    .background(c.surf, KtmShapes.SheetTop)
                    .consumeMapTouches()
                    .padding(horizontal = KtmSpacing.lg),
            ) {
                SheetHandle()
                content()
                Box(Modifier.windowInsetsPadding(WindowInsets.navigationBars).padding(bottom = KtmSpacing.lg))
            }
        }
    }
}
