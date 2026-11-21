package com.navigator.app.ui.feature.navigate.link

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import com.navigator.app.R
import com.navigator.app.nav.destination.LinkFailure
import com.navigator.app.nav.destination.SavedPlace
import com.navigator.app.ui.components.ButtonStyle
import com.navigator.app.ui.components.KtmButton
import com.navigator.app.ui.components.KtmSheetContent
import com.navigator.app.ui.components.LandscapePanelSpec
import com.navigator.app.ui.components.SheetHandle
import com.navigator.app.ui.components.Skeleton
import com.navigator.app.ui.components.consumeMapTouches
import com.navigator.app.ui.shell.SnackbarClearance
import com.navigator.app.ui.theme.KtmElevation
import com.navigator.app.ui.theme.KtmLayout
import com.navigator.app.ui.theme.KtmShapes
import com.navigator.app.ui.theme.KtmSpacing
import com.navigator.app.ui.theme.KtmTheme
import com.navigator.app.ui.theme.pageInsets
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** What looking up a shared link came to. */
sealed interface LinkOutcome {
    data class Found(val place: SavedPlace) : LinkOutcome
    data class Failed(val reason: LinkFailure) : LinkOutcome
}

/** What the shared-link card shows. [Mirror] is the card of the mirror engine, which cannot plan in the app. */
enum class SharedLinkState { Idle, Loading, Failed, Mirror }

/**
 * A Google Maps link shared into the app (HM9): resolves it to a place without blocking the screen,
 * and keeps the link so a failure can be retried. Opening a second link replaces the first.
 */
class SharedLinkController(
    private val scope: CoroutineScope,
    private val resolve: suspend (String) -> LinkOutcome,
) {
    private val _state = MutableStateFlow(SharedLinkState.Idle)
    val state: StateFlow<SharedLinkState> = _state.asStateFlow()

    private val _failure = MutableStateFlow<LinkFailure?>(null)

    /** Why the last lookup failed, while the state is [SharedLinkState.Failed]. */
    val failure: StateFlow<LinkFailure?> = _failure.asStateFlow()

    private val _resolved = Channel<SavedPlace>(Channel.BUFFERED)

    /** The place a link resolved to, once. */
    val resolved: Flow<SavedPlace> = _resolved.receiveAsFlow()

    private var link: String? = null
    private var job: Job? = null

    fun open(text: String) {
        link = text
        run()
    }

    fun retry() = run()

    /** The rider closed the card (or went to search instead). */
    fun dismiss() {
        job?.cancel()
        link = null
        _state.value = SharedLinkState.Idle
    }

    private fun run() {
        val text = link ?: return
        job?.cancel()
        _state.value = SharedLinkState.Loading
        job = scope.launch {
            // A lookup that throws is a failed lookup: the card says so, the app does not go down.
            val outcome = try {
                resolve(text)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LinkOutcome.Failed(LinkFailure.UNREADABLE)
            }
            when (outcome) {
                is LinkOutcome.Found -> {
                    _failure.value = null
                    _state.value = SharedLinkState.Idle
                    _resolved.send(outcome.place)
                }
                is LinkOutcome.Failed -> {
                    _failure.value = outcome.reason
                    _state.value = SharedLinkState.Failed
                }
            }
        }
    }
}

class SharedLinkActions(
    val onRetry: () -> Unit,
    val onSearchInstead: () -> Unit,
    val onDismiss: () -> Unit,
    val onOpenMaps: () -> Unit = {},
    /** Null when in-app maps are not available on this phone or build: the option is then not offered. */
    val onSwitchToInApp: (() -> Unit)? = null,
)

/**
 * The card that opens over the map for a shared link: a skeleton while it resolves, the failure with
 * Try again and Search instead, or in mirror mode the way to carry on in Google Maps. Not modal: the
 * map stays usable.
 */
@Composable
fun SharedLinkCard(
    state: SharedLinkState,
    actions: SharedLinkActions,
    modifier: Modifier = Modifier,
    failure: LinkFailure? = null,
) {
    if (state == SharedLinkState.Idle) return
    val c = KtmTheme.colors
    // Landscape: the card keeps a panel's width at the end of the screen, clear of the Where-to / plan panel at the start.
    BoxWithConstraints(modifier.fillMaxSize()) {
    val landscape = LandscapePanelSpec.isLandscape(maxWidth, maxHeight)
    var heightPx by remember { mutableIntStateOf(0) }
    // A snackbar (the retry, the unreadable link) sits above the card, not under it.
    SnackbarClearance(bottom = with(LocalDensity.current) { heightPx.toDp() })
    Column(
        Modifier.align(if (landscape) Alignment.BottomEnd else Alignment.BottomCenter)
            .widthIn(max = if (landscape) KtmLayout.panelWide else Dp.Unspecified).fillMaxWidth()
            .onSizeChanged { heightPx = it.height }
            .shadow(KtmElevation.sheet, KtmShapes.SheetTop).background(c.surf, KtmShapes.SheetTop).consumeMapTouches()
            .pageInsets(WindowInsetsSides.Horizontal),
    ) {
        SheetHandle()
        // The shared anatomy (gutters, the gesture-area spacer); a short card, so it does not scroll.
        KtmSheetContent(Modifier.semantics { liveRegion = LiveRegionMode.Polite }, scrollable = false, imePadding = false) {
        when (state) {
            SharedLinkState.Loading -> {
                Title(R.string.shared_link_loading)
                Skeleton(Modifier.fillMaxWidth(0.7f).padding(top = KtmSpacing.md), shape = KtmShapes.Badge)
                Skeleton(Modifier.fillMaxWidth(0.4f).padding(top = KtmSpacing.sm))
                Buttons { KtmButton(stringResource(R.string.common_cancel), actions.onDismiss, Modifier.fillMaxWidth(), style = ButtonStyle.Tonal) }
            }
            SharedLinkState.Failed -> {
                Title(R.string.shared_link_failed_title)
                Body(
                    when (failure) {
                        LinkFailure.NO_CONNECTION -> R.string.shared_link_failed_connection
                        LinkFailure.NOT_A_MAPS_LINK -> R.string.shared_link_failed_not_maps
                        LinkFailure.PLACE_NOT_FOUND -> R.string.shared_link_failed_no_place
                        else -> R.string.shared_link_failed_message
                    },
                )
                Buttons {
                    KtmButton(stringResource(R.string.navigate_map_retry), actions.onRetry, Modifier.fillMaxWidth())
                    KtmButton(stringResource(R.string.shared_link_search_instead), actions.onSearchInstead, Modifier.fillMaxWidth(), style = ButtonStyle.Tonal)
                }
            }
            SharedLinkState.Mirror -> {
                Title(R.string.shared_link_mirror_title)
                Body(R.string.shared_link_mirror_message)
                Buttons {
                    KtmButton(stringResource(R.string.shared_link_open_maps), actions.onOpenMaps, Modifier.fillMaxWidth())
                    actions.onSwitchToInApp?.let { switch ->
                        KtmButton(stringResource(R.string.shared_link_switch), switch, Modifier.fillMaxWidth(), style = ButtonStyle.Tonal)
                    }
                    KtmButton(stringResource(R.string.common_cancel), actions.onDismiss, Modifier.fillMaxWidth(), style = ButtonStyle.Text)
                }
            }
            SharedLinkState.Idle -> Unit
        }
        }
    }
    }
}

@Composable
private fun Title(res: Int) = Text(stringResource(res), style = MaterialTheme.typography.headlineSmall, color = KtmTheme.colors.fg)

@Composable
private fun Body(res: Int) = Text(
    stringResource(res), style = MaterialTheme.typography.bodyMedium, color = KtmTheme.colors.fg2,
    modifier = Modifier.padding(top = KtmSpacing.sm),
)

@Composable
private fun Buttons(content: @Composable () -> Unit) {
    Column(Modifier.padding(top = KtmSpacing.lg), verticalArrangement = Arrangement.spacedBy(KtmSpacing.sm)) { content() }
}
