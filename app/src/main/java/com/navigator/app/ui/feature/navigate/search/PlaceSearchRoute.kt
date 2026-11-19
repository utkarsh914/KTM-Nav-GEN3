package com.navigator.app.ui.feature.navigate.search

import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.navigator.app.R
import com.navigator.app.app.appContainer
import com.navigator.app.net.ConnectivityMonitor
import com.navigator.app.nav.destination.PlaceCategory
import com.navigator.app.nav.destination.SavedPlace
import com.navigator.app.ui.feature.navigate.ActivityCardModel
import com.navigator.app.ui.feature.navigate.voiceAvailable
import com.navigator.app.ui.screens.lastKnownLocation
import com.navigator.app.ui.shell.LocalFeedback

/**
 * Stateful host of [PlaceSearchScreen]: owns the view model for one search session, the voice launcher
 * and the messages (a place that would not load, a removed recent with Undo). What the picked place is
 * for is the caller's: [onPicked] gets it.
 */
@Composable
fun PlaceSearchRoute(
    mode: SearchMode,
    onPicked: (SavedPlace) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    initialQuery: String = "",
    initialCategory: PlaceCategory? = null,
    /** The route still ahead of the rider, for categories "along your route" (a stop for a live trip). */
    routeAhead: () -> List<Pair<Double, Double>>? = { null },
    activity: ActivityCardModel? = null,
    onResumeTrip: (() -> Unit)? = null,
    onQueryChanged: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val container = context.appContainer
    val feedback = LocalFeedback.current
    // The view model belongs to this one opening of the search: it is cleared when the screen leaves,
    // so a stale session (and what it captured) never lingers or leaks into the next opening.
    val owner = remember {
        object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }
    }
    DisposableEffect(owner) { onDispose { owner.viewModelStore.clear() } }
    val appContext = context.applicationContext
    val vm: PlaceSearchViewModel = viewModel(
        viewModelStoreOwner = owner,
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = PlaceSearchViewModel(
                places = container.places,
                api = PlacesClientApi(appContext),
                online = ConnectivityMonitor.isOnline,
                originProvider = { lastKnownLocation(appContext) },
                initialQuery = initialQuery,
                initialCategory = initialCategory,
            ) as T
        },
    )
    val state by vm.state.collectAsState()
    val currentRoute = rememberUpdatedState(routeAhead)
    vm.routeProvider = { currentRoute.value() }
    val picked = rememberUpdatedState(onPicked)
    val queryChanged = rememberUpdatedState(onQueryChanged)

    val hasVoice = remember { voiceAvailable(context) }
    val prompt = stringResource(R.string.navigate_voice_prompt)
    val speech = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.takeIf { it.isNotBlank() }
            ?.let { vm.setQuery(it) }
    }

    val loadFailed = stringResource(R.string.search_place_failed)
    val removed = stringResource(R.string.search_recent_removed)
    val undo = stringResource(R.string.search_undo)
    LaunchedEffect(vm) {
        vm.events.collect { e ->
            when (e) {
                is PlaceSearchEvent.Picked -> picked.value(e.place)
                PlaceSearchEvent.LoadFailed -> feedback.snackbar(loadFailed)
                is PlaceSearchEvent.RecentRemoved -> feedback.snackbar(removed, undo) { vm.undoRemoveRecent(e.place) }
            }
        }
    }
    LaunchedEffect(state.query) { queryChanged.value(state.query) }
    // A place lookup still running must not jump to another screen after the rider backed out.
    DisposableEffect(vm) { onDispose { vm.cancelPending() } }

    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (initialCategory == null) runCatching { focus.requestFocus() } }

    PlaceSearchScreen(
        state = state, mode = mode, voiceAvailable = hasVoice, modifier = modifier,
        activity = activity, focusRequester = focus,
        actions = PlaceSearchActions(
            onBack = onBack,
            onQuery = vm::setQuery,
            onVoice = {
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    .putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
                runCatching { speech.launch(intent) }
            },
            onCategory = vm::selectCategory,
            onSuggestion = vm::pickSuggestion,
            onPlace = vm::pickPlace,
            onCategoryResult = vm::pickCategoryResult,
            onRemoveRecent = vm::removeRecent,
            onResumeTrip = onResumeTrip,
        ),
    )
}
