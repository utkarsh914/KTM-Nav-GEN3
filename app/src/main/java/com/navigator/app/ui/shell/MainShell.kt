package com.navigator.app.ui.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Badge
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.ShortNavigationBarDefaults
import androidx.compose.material3.ShortNavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.WideNavigationRailDefaults
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.navigationsuite.ExperimentalMaterial3AdaptiveNavigationSuiteApi
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuite
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteItem
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldLayout
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldState
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldValue
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.material3.adaptive.navigationsuite.rememberNavigationSuiteScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass
import com.navigator.app.R
import com.navigator.app.ui.components.KtmSnackbarHost
import com.navigator.app.ui.components.PillTone
import com.navigator.app.ui.components.pillDotColor
import com.navigator.app.ui.map.MapInsets
import com.navigator.app.ui.theme.KtmLayout
import com.navigator.app.ui.theme.KtmSpacing
import com.navigator.app.ui.theme.KtmTheme
import com.navigator.app.ui.theme.pageInsets
import kotlin.math.roundToInt

/** The bike's state for the rail's status dot (MR7): its tone and the state in words. */
data class BikeRailStatus(val tone: PillTone, val state: String)

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
    /** The bike's state as a dot on the Navigate item, with its words for screen readers; shown in the landscape rail only. */
    bike: () -> BikeRailStatus?,
    onSelectTab: (Tab) -> Unit,
    feedback: Feedback,
    strip: @Composable () -> Unit,
    /** Where the pages' area sits in the window, so the map layer behind the shell can be padded to it. */
    onContentInsets: (MapInsets) -> Unit = {},
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

    val navigationItems: @Composable () -> Unit = {
            Tab.entries.forEach { tab ->
                val dot = tab == Tab.Settings && settingsNeedsAttention
                // The landscape rail's dot repeats the bike pill (which Navigate and Mirror keep, as they open the bike sheet).
                val bikeDot = if (tab == Tab.Navigate && type != NavigationSuiteType.ShortNavigationBarCompact) bike() else null
                val label = stringResource(tab.label)
                NavigationSuiteItem(
                    selected = tab == currentTab,
                    onClick = { onSelectTab(tab) },
                    modifier = Modifier.semantics {
                        if (dot) stateDescription = attention
                        if (bikeDot != null) stateDescription = bikeDot.state
                    },
                    icon = { Icon(tab.icon, contentDescription = null) },
                    label = { Text(label, maxLines = 1) },
                    navigationSuiteType = type,
                    badge = when {
                        dot -> ({ Badge(containerColor = c.warn) })
                        bikeDot != null -> ({ Badge(containerColor = pillDotColor(bikeDot.tone, c)) })
                        else -> null
                    },
                    colors = itemColors,
                )
            }
    }
    // Not NavigationSuiteScaffold: it wraps everything in a Material Surface, which swallows every touch, and the shared
    // map sits behind this shell. Same layout and bar animation from its two public parts, minus the Surface (see
    // docs/ENGINEERING_NOTES.md, "The map layer").
    CompositionLocalProvider(LocalContentColor provides c.fg) {
    NavigationSuiteScaffoldLayout(
        navigationSuite = { NavigationSuite(navigationSuiteType = type, colors = barColors, content = navigationItems) },
        navigationSuiteType = type,
        state = state,
    ) {
      Box(Modifier.consumeWindowInsets(barInsets(type, state))) {
        // Only the portrait bar sits under the content and takes the gesture-bar inset. Without it (pushed pages, the
        // landscape rail) nothing above does, so the strip and snackbar take the bottom and cutout insets themselves.
        val ownsBottom = !(showBar && type == NavigationSuiteType.ShortNavigationBarCompact)
        val ownInsets = if (!ownsBottom) Modifier
        else Modifier.pageInsets(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)
        var drawn by remember(immersive) { mutableStateOf(false) }
        Column(Modifier.fillMaxSize()) {
            val window = LocalWindowInfo.current.containerSize
            Box(
                Modifier.weight(1f).fillMaxSize().onGloballyPositioned { at ->
                    val b = at.boundsInWindow()
                    onContentInsets(MapInsets(left = b.left.roundToInt(), right = (window.width - b.right).roundToInt().coerceAtLeast(0), bottom = (window.height - b.bottom).roundToInt().coerceAtLeast(0)))
                },
            ) {
                content()
                // 8 dp above whatever is under it (§3.3): the strip brings its own 8 dp, so only the bare bar needs one here.
                // Sheets, panels and buttons drawn over the content say so (SnackbarClearance); the inset already applied is not counted twice.
                val cleared = feedback.cleared
                val density = LocalDensity.current
                val applied = if (ownsBottom) with(density) { WindowInsets.safeDrawing.getBottom(density).toDp() } else 0.dp
                val appliedStart = if (ownsBottom) with(density) { WindowInsets.safeDrawing.getLeft(density, LayoutDirection.Ltr).toDp() } else 0.dp
                KtmSnackbarHost(
                    feedback.snackbarHost,
                    Modifier.align(Alignment.BottomCenter).then(ownInsets)
                        .padding(start = (cleared.start - appliedStart).coerceAtLeast(0.dp), bottom = (if (drawn && !immersive) 0.dp else KtmSpacing.sm) + (cleared.bottom - applied).coerceAtLeast(0.dp)),
                )
            }
            if (!immersive) {
                // An empty strip must not pad the gesture inset: it would leave a band under the map of a pushed page.
                Box(if (drawn) ownInsets else Modifier) { Box(Modifier.onSizeChanged { drawn = it.height > 0 }) { strip() } }
            }
        }
      }
    }
    }
}

/** What the bar or rail takes of the window insets, as the scaffold would have consumed them. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3AdaptiveNavigationSuiteApi::class)
@Composable
private fun barInsets(type: NavigationSuiteType, state: NavigationSuiteScaffoldState): WindowInsets =
    if (state.currentValue == NavigationSuiteScaffoldValue.Hidden && !state.isAnimating) WindowInsets(0, 0, 0, 0)
    else when (type) {
        NavigationSuiteType.ShortNavigationBarCompact ->
            ShortNavigationBarDefaults.windowInsets.only(WindowInsetsSides.Bottom).add(WindowInsets(bottom = KtmLayout.tabBar))
        NavigationSuiteType.WideNavigationRailCollapsed -> WideNavigationRailDefaults.windowInsets.only(WindowInsetsSides.Start)
        else -> WindowInsets(0, 0, 0, 0)
    }
