package com.navigator.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.navigator.app.ui.theme.KtmShapes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.navigator.app.ui.theme.KtmLayout
import com.navigator.app.ui.theme.KtmSpacing
import com.navigator.app.ui.theme.KtmTheme

/** Scrim behind sheets and dialogs: 60% black. */
val KtmScrim = Color.Black.copy(alpha = 0.6f)

/** The drag handle: a 32 × 4 bar with a 12 dp zone above and below (28 dp in all). */
@Composable
fun SheetHandle(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(KtmLayout.sheetHandleZone), contentAlignment = Alignment.Center) {
        Box(Modifier.size(width = 32.dp, height = 4.dp).background(KtmTheme.colors.line2, RoundedCornerShape(2.dp)))
    }
}

/**
 * Sheet anatomy, stateless so it can be previewed and reused inside any sheet host: a 48 dp title
 * row, content 16 dp from the sides, then [actions] 16 dp below and 16 dp above the gesture area.
 * It sizes to its content, so there is never an empty band.
 */
@Composable
fun KtmSheetContent(
    modifier: Modifier = Modifier,
    title: String? = null,
    scrollable: Boolean = true,
    actions: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    // [scrollable] must be false when [content] holds its own scrolling list.
    Column(
        modifier.fillMaxWidth()
            .let { if (scrollable) it.verticalScroll(rememberScrollState()) else it }
            .padding(horizontal = KtmSpacing.lg),
    ) {
        if (title != null) {
            Box(Modifier.fillMaxWidth().height(KtmLayout.sheetTitleRow), contentAlignment = Alignment.CenterStart) {
                Text(
                    title, style = MaterialTheme.typography.headlineSmall, color = KtmTheme.colors.fg,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.semantics { heading() },
                )
            }
            Spacer(Modifier.height(KtmSpacing.md))
        }
        content()
        if (actions != null) {
            Column(Modifier.padding(top = KtmSpacing.lg)) { actions() }
        }
        Box(Modifier.windowInsetsPadding(WindowInsets.navigationBars).height(KtmSpacing.lg))
    }
}

/** A modal sheet (scrim, drag to dismiss, predictive back) around [KtmSheetContent]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KtmModalSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    scrollable: Boolean = true,
    actions: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        sheetState = sheetState,
        shape = KtmShapes.SheetTop,
        containerColor = KtmTheme.colors.surf,
        scrimColor = KtmScrim,
        dragHandle = { SheetHandle() },
        contentWindowInsets = { WindowInsets(0) },
    ) {
        KtmSheetContent(title = title, scrollable = scrollable, actions = actions, content = content)
    }
}
