package com.navigator.app.ui.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Badge
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ShortNavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.WideNavigationRailDefaults
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.navigationsuite.ExperimentalMaterial3AdaptiveNavigationSuiteApi
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteItem
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldState
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldValue
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.material3.adaptive.navigationsuite.rememberNavigationSuiteScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass
import com.navigator.app.R
import com.navigator.app.ui.components.KtmSnackbarHost
import com.navigator.app.ui.theme.KtmTheme

/** Portrait: a 64 dp bar. A phone on its side (short and wide): a rail, so the map keeps its height. */
internal fun shellNavigationType(minWidthDp: Int): NavigationSuiteType =
    if (minWidthDp < WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND) NavigationSuiteType.ShortNavigationBarCompact
    else NavigationSuiteType.WideNavigationRailCollapsed

/**
 * The shared frame of the four tabs: a bottom bar in portrait, a rail in landscape (the scaffold
 * insets it for the camera cutout and passes the rest of the insets on), the activity strip docked
 * directly above the bar, and the snackbar above that. Pushed pages and riding mode hide the bar
 * ([currentTab] null, or [immersive]); the content stays in one place in the composition, so the
 * map is never rebuilt by the change.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3AdaptiveNavigationSuiteApi::class)
@Composable
fun MainShell(
    currentTab: Tab?,
    immersive: Boolean,
    settingsNeedsAttention: Boolean,
    onSelectTab: (Tab) -> Unit,
    feedback: Feedback,
    strip: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    val c = KtmTheme.colors
    val a = KtmTheme.accent
    val size = currentWindowAdaptiveInfoV2().windowSizeClass
    val type = shellNavigationType(size.minWidthDp)
    val showBar = currentTab != null && !immersive
    val state: NavigationSuiteScaffoldState = rememberNavigationSuiteScaffoldState(
        initialValue = if (showBar) NavigationSuiteScaffoldValue.Visible else NavigationSuiteScaffoldValue.Hidden,
    )
    LaunchedEffect(showBar) { if (showBar) state.show() else state.hide() }

    val barColors = NavigationSuiteDefaults.colors(
        shortNavigationBarContainerColor = c.surf,
        shortNavigationBarContentColor = c.fg2,
        wideNavigationRailColors = WideNavigationRailDefaults.colors(containerColor = c.surf, contentColor = c.fg2),
    )
    val itemColors = ShortNavigationBarItemDefaults.colors(
        selectedIconColor = a.onContainer, selectedTextColorTopIconPosition = c.fg, selectedTextColorStartIconPosition = c.fg, selectedIndicatorColor = a.container,
        unselectedIconColor = c.fg2, unselectedTextColor = c.fg2,
    )
    val attention = stringResource(R.string.tab_needs_attention)

    NavigationSuiteScaffold(
        navigationItems = {
            Tab.entries.forEach { tab ->
                val dot = tab == Tab.Settings && settingsNeedsAttention
                val label = stringResource(tab.label)
                NavigationSuiteItem(
                    selected = tab == currentTab,
                    onClick = { onSelectTab(tab) },
                    modifier = Modifier.semantics { if (dot) stateDescription = attention },
                    icon = { Icon(tab.icon, contentDescription = null) },
                    label = { Text(label, maxLines = 1) },
                    navigationSuiteType = type,
                    badge = if (dot) ({ Badge(containerColor = c.bad) }) else null,
                    colors = itemColors,
                )
            }
        },
        navigationSuiteType = type,
        navigationSuiteColors = barColors,
        containerColor = c.bg,
        contentColor = c.fg,
        state = state,
    ) {
        // With the bar hidden (pushed pages) nothing above consumes the gesture-bar and cutout insets
        // any more, so the strip and snackbar take them themselves.
        val ownInsets = if (showBar) Modifier
        else Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxSize()) {
                content()
                KtmSnackbarHost(
                    feedback.snackbarHost,
                    Modifier.align(Alignment.BottomCenter).then(ownInsets).padding(bottom = 8.dp + feedback.snackbarLift),
                )
            }
            if (!immersive) Box(ownInsets) { strip() }
        }
    }
}
