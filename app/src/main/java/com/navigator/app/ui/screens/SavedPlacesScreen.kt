package com.navigator.app.ui.screens

import android.content.Context
import android.location.LocationManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.navigator.app.nav.destination.FavoritePlace
import com.navigator.app.nav.destination.FavoriteSlot
import com.navigator.app.nav.destination.PlaceSuggestion
import com.navigator.app.nav.destination.PlacesClient
import com.navigator.app.nav.destination.PlacesStore
import com.navigator.app.nav.destination.SavedPlace
import com.navigator.app.ui.components.DeleteConfirmDialog
import com.navigator.app.ui.components.Eyebrow
import com.navigator.app.ui.components.GroupCard
import com.navigator.app.ui.components.PopIconButton
import com.navigator.app.ui.components.ScreenTopBar
import com.navigator.app.ui.components.SelectableCard
import com.navigator.app.ui.components.SelectionTopBarActions
import com.navigator.app.ui.components.SettingsRow
import com.navigator.app.ui.theme.Barlow
import com.navigator.app.ui.theme.BarlowCondensed
import com.navigator.app.ui.theme.Ktm
import com.navigator.app.ui.theme.OpenDashIcons
import com.navigator.app.ui.theme.rememberHaptics
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Manage Home / Work + Saved places (see docs/architecture.md §3).
 *
 * The Saved list uses the same standalone [SelectableCard] chrome as the ride
 * history: tap a card to drop its pin on the map ([onOpenOnMap]); long-press to
 * enter multi-select (select-all + bulk delete in the top bar). Home & Work stay
 * as one compact grouped card (two fixed slots). Every destructive action —
 * single, bulk, or clearing Home/Work — routes through the shared confirmation
 * dialog. Setting Home/Work or adding a saved place opens an inline place search
 * that reuses [PlacesClient] + persists via [PlacesStore].
 */
@Composable
fun SavedPlacesScreen(
    onBack: () -> Unit,
    onOpenOnMap: (SavedPlace) -> Unit = {},
) {
    val context = LocalContext.current
    val store = remember { PlacesStore(context) }
    var favorites by remember { mutableStateOf(store.favorites()) }
    // Non-null while the inline picker is shown; the slot to write on pick.
    var picking by remember { mutableStateOf<FavoriteSlot?>(null) }
    // Multi-select over the SAVED (OTHER) list, keyed by rounded coordinates.
    var selectionMode by remember { mutableStateOf(false) }
    var selectedKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    var confirmDelete by remember { mutableStateOf<PlaceDeleteTarget?>(null) }

    fun refresh() { favorites = store.favorites() }

    val home = favorites.firstOrNull { it.slot == FavoriteSlot.HOME }
    val work = favorites.firstOrNull { it.slot == FavoriteSlot.WORK }
    val saved = favorites.filter { it.slot == FavoriteSlot.OTHER }

    // Keep the selection consistent with the current list after deletes.
    val allKeys = saved.map { store.favoriteKey(it.place) }.toSet()
    val validSelected = selectedKeys.intersect(allKeys)

    fun toggle(key: String) {
        selectedKeys = if (key in selectedKeys) selectedKeys - key else selectedKeys + key
    }

    fun enterSelection(key: String) {
        selectionMode = true
        selectedKeys = selectedKeys + key
    }

    fun exitSelection() {
        selectionMode = false
        selectedKeys = emptySet()
    }

    BackHandler(enabled = picking != null) { picking = null }
    // While multi-select is active, back exits it instead of leaving the screen.
    BackHandler(enabled = picking == null && selectionMode) { exitSelection() }

    if (picking != null) {
        PlacePicker(
            title = when (picking) {
                FavoriteSlot.HOME -> "Set Home"
                FavoriteSlot.WORK -> "Set Work"
                else -> "Add place"
            },
            onBack = { picking = null },
            onPicked = { place ->
                store.saveFavorite(place, picking!!)
                refresh()
                picking = null
            },
        )
        return
    }

    Column(
        modifier = Modifier.fillMaxSize().background(Ktm.Screen).systemBarsPadding(),
    ) {
        ScreenTopBar(
            title = if (selectionMode) "${validSelected.size}/${saved.size} selected" else "Saved places",
            onBack = { if (selectionMode) exitSelection() else onBack() },
            modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 10.dp, bottom = 6.dp),
        ) {
            if (selectionMode) {
                SelectionTopBarActions(
                    allSelected = saved.isNotEmpty() && validSelected.size == saved.size,
                    hasSelection = validSelected.isNotEmpty(),
                    onToggleSelectAll = {
                        val allSelected = saved.isNotEmpty() && validSelected.size == saved.size
                        selectedKeys = if (allSelected) emptySet() else allKeys
                    },
                    onDelete = { confirmDelete = PlaceDeleteTarget.Selected },
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp),
            contentPadding = PaddingValues(bottom = 24.dp, top = 6.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Home & Work — a compact grouped card of two fixed slots. Hidden
            // while selecting, which focuses the selection on the saved list.
            if (!selectionMode) {
                item {
                    GroupCard("Home & Work") {
                        SlotRow(
                            title = "Home",
                            subtitle = home?.place?.label,
                            showDivider = true,
                            onClick = home?.let { { onOpenOnMap(it.place) } } ?: { picking = FavoriteSlot.HOME },
                            onRemove = home?.let { { confirmDelete = PlaceDeleteTarget.Slot(FavoriteSlot.HOME) } },
                        )
                        SlotRow(
                            title = "Work",
                            subtitle = work?.place?.label,
                            showDivider = false,
                            onClick = work?.let { { onOpenOnMap(it.place) } } ?: { picking = FavoriteSlot.WORK },
                            onRemove = work?.let { { confirmDelete = PlaceDeleteTarget.Slot(FavoriteSlot.WORK) } },
                        )
                    }
                }
                item {
                    Eyebrow(
                        "Saved", fontSize = 11, letterSpacing = 2.0,
                        modifier = Modifier.padding(start = 4.dp, top = 6.dp),
                    )
                }
            }

            if (saved.isEmpty() && !selectionMode) {
                item {
                    Text(
                        "No saved places yet.",
                        color = Ktm.Muted2, fontFamily = Barlow, fontSize = 14.sp,
                        modifier = Modifier.padding(start = 4.dp, top = 2.dp, bottom = 2.dp),
                    )
                }
            } else {
                items(saved, key = { store.favoriteKey(it.place) }) { fav ->
                    val key = store.favoriteKey(fav.place)
                    SavedPlaceCard(
                        fav = fav,
                        selectionMode = selectionMode,
                        selected = key in validSelected,
                        onTap = { if (selectionMode) toggle(key) else onOpenOnMap(fav.place) },
                        onLongPress = { if (!selectionMode) enterSelection(key) else toggle(key) },
                        onDelete = { confirmDelete = PlaceDeleteTarget.Single(fav.place) },
                    )
                }
            }

            if (!selectionMode) {
                item { AddPlaceCard(onClick = { picking = FavoriteSlot.OTHER }) }
            }
        }
    }

    confirmDelete?.let { target ->
        val (title, message) = when (target) {
            is PlaceDeleteTarget.Single -> "Delete this place?" to
                "This removes the saved place. This can't be undone."
            PlaceDeleteTarget.Selected -> {
                val n = validSelected.size
                (if (n > 1) "Delete $n places?" else "Delete this place?") to
                    "This removes the saved places. This can't be undone."
            }
            is PlaceDeleteTarget.Slot -> {
                val name = if (target.slot == FavoriteSlot.HOME) "Home" else "Work"
                "Remove $name?" to "This clears your saved $name location."
            }
        }
        DeleteConfirmDialog(
            title = title,
            message = message,
            confirmLabel = if (target is PlaceDeleteTarget.Slot) "REMOVE" else "DELETE",
            onConfirm = {
                when (target) {
                    is PlaceDeleteTarget.Single -> store.removeFavorite(target.place.lat, target.place.lng)
                    PlaceDeleteTarget.Selected -> {
                        store.removeFavorites(validSelected)
                        exitSelection()
                    }
                    is PlaceDeleteTarget.Slot -> store.removeFavoriteSlot(target.slot)
                }
                refresh()
                confirmDelete = null
            },
            onDismiss = { confirmDelete = null },
        )
    }
}

private sealed interface PlaceDeleteTarget {
    data class Single(val place: SavedPlace) : PlaceDeleteTarget
    data object Selected : PlaceDeleteTarget
    data class Slot(val slot: FavoriteSlot) : PlaceDeleteTarget
}

/** A standalone saved-place card (matches the ride cards): title + address, a
 *  per-card delete when browsing, long-press to multi-select. */
@Composable
private fun SavedPlaceCard(
    fav: FavoritePlace,
    selectionMode: Boolean,
    selected: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onDelete: () -> Unit,
) {
    SelectableCard(
        selected = selected,
        selectionMode = selectionMode,
        onTap = onTap,
        onLongPress = onLongPress,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                fav.place.label.ifBlank { "Saved place" },
                color = Ktm.White, fontFamily = BarlowCondensed, fontWeight = FontWeight.Bold,
                fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            fav.place.address?.let {
                Spacer(Modifier.size(2.dp))
                Text(
                    it, color = Ktm.Muted2, fontFamily = Barlow, fontSize = 12.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (!selectionMode) {
            PopIconButton(
                icon = Icons.Filled.Delete, desc = "Delete place", tint = Ktm.Dim,
                boxSize = 32.dp, iconSize = 26.dp, onClick = onDelete,
            )
        }
    }
}

/** The "Add place" action, styled as a card so it sits with the saved list. */
@Composable
private fun AddPlaceCard(onClick: () -> Unit) {
    val haptics = rememberHaptics()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Ktm.RadiusCard))
            .background(Ktm.Surface)
            .border(1.dp, Ktm.Border, RoundedCornerShape(Ktm.RadiusCard))
            .clickable { haptics.tap(); onClick() }
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(OpenDashIcons.Search, null, tint = Ktm.Orange, modifier = Modifier.size(20.dp))
        Spacer(Modifier.size(12.dp))
        Text(
            "Add place", color = Ktm.TextPrimary, fontFamily = Barlow, fontSize = 14.sp,
            modifier = Modifier.weight(1f),
        )
    }
}

/** A Home/Work row inside the grouped card: tap to set (empty) or open on map
 *  (set), with a trailing remove (X) that asks for confirmation. */
@Composable
private fun SlotRow(
    title: String,
    subtitle: String?,
    showDivider: Boolean,
    onClick: (() -> Unit)?,
    onRemove: (() -> Unit)?,
) {
    SettingsRow(label = title, showDivider = showDivider, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (subtitle != null) {
                Text(
                    subtitle, color = Ktm.Muted2, fontFamily = Barlow, fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 180.dp),
                )
                if (onRemove != null) {
                    Spacer(Modifier.size(10.dp))
                    Icon(
                        OpenDashIcons.Close, "Remove", tint = Ktm.Dim,
                        modifier = Modifier.size(20.dp).clip(RoundedCornerShape(6.dp)).clickable(onClick = onRemove),
                    )
                }
            } else {
                Text("Set", color = Ktm.Orange, fontFamily = BarlowCondensed, fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 1.sp)
            }
        }
    }
}

// ============================ Picker ===================================

@Composable
private fun PlacePicker(
    title: String,
    onBack: () -> Unit,
    onPicked: (SavedPlace) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val auth = remember { PlacesClient.androidAuth(context) }
    val origin = remember { lastLocation(context) }
    val sessionToken = remember { UUID.randomUUID().toString() }

    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<PlaceSuggestion>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var resolving by remember { mutableStateOf(false) }

    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    LaunchedEffect(query) {
        val q = query.trim()
        if (q.length < 3) { results = emptyList(); loading = false; return@LaunchedEffect }
        loading = true
        delay(300)
        results = PlacesClient.autocomplete(
            input = q, sessionToken = sessionToken, auth = auth,
            originLat = origin?.first, originLng = origin?.second,
        )
        loading = false
    }

    Column(
        modifier = Modifier.fillMaxSize().background(Ktm.Screen).systemBarsPadding()
            .padding(horizontal = 16.dp).padding(top = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                OpenDashIcons.ChevronLeft, "Back", tint = Ktm.TextSecondary,
                modifier = Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).clickable(onClick = onBack),
            )
            Spacer(Modifier.size(8.dp))
            Text(title, color = Ktm.White, fontFamily = BarlowCondensed, fontWeight = FontWeight.Bold, fontSize = 20.sp, letterSpacing = 1.sp)
        }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = query, onValueChange = { query = it }, singleLine = true,
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
            placeholder = { Text("Search a place or address", color = Ktm.Dim) },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Ktm.Surface,
                unfocusedContainerColor = Ktm.Surface,
                focusedTextColor = Ktm.White,
                unfocusedTextColor = Ktm.White,
                cursorColor = Ktm.Orange,
                focusedIndicatorColor = Ktm.Orange,
                unfocusedIndicatorColor = Ktm.Border,
            ),
        )
        Spacer(Modifier.height(12.dp))
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                loading || resolving -> CircularProgressIndicator(
                    color = Ktm.Orange, modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp),
                )
                query.trim().length >= 3 && results.isEmpty() -> Text(
                    "No matches", color = Ktm.Muted2, fontFamily = BarlowCondensed, fontSize = 18.sp,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp),
                )
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(results, key = { it.placeId }) { s ->
                        PickerRow(s) {
                            if (!resolving) {
                                resolving = true
                                scope.launch {
                                    val loc = PlacesClient.details(s.placeId, sessionToken, auth)
                                    resolving = false
                                    if (loc != null) onPicked(SavedPlace(loc.lat, loc.lng, s.primary, s.secondary))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PickerRow(s: PlaceSuggestion, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(Ktm.RadiusRow))
            .background(Ktm.Surface)
            .border(1.dp, Ktm.Border, RoundedCornerShape(Ktm.RadiusRow))
            .clickable(onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(s.primary, color = Ktm.White, fontFamily = BarlowCondensed, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
            s.secondary?.let { Text(it, color = Ktm.Muted2, fontFamily = BarlowCondensed, fontSize = 14.sp) }
        }
    }
}

private fun lastLocation(context: Context): Pair<Double, Double>? {
    return try {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        for (p in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)) {
            val loc = runCatching { lm.getLastKnownLocation(p) }.getOrNull()
            if (loc != null) return loc.latitude to loc.longitude
        }
        null
    } catch (e: SecurityException) {
        null
    }
}
