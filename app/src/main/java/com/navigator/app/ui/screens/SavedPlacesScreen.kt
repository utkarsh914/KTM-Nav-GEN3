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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.navigator.app.nav.destination.FavoriteSlot
import com.navigator.app.nav.destination.PlaceSuggestion
import com.navigator.app.nav.destination.PlacesClient
import com.navigator.app.nav.destination.PlacesStore
import com.navigator.app.nav.destination.SavedPlace
import com.navigator.app.ui.components.GroupCard
import com.navigator.app.ui.components.SettingsRow
import com.navigator.app.ui.theme.Barlow
import com.navigator.app.ui.theme.BarlowCondensed
import com.navigator.app.ui.theme.Ktm
import com.navigator.app.ui.theme.OpenDashIcons
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Manage Home / Work + Saved places (UX revamp — see docs/NAVIGATION_UX_REVAMP.md).
 * Setting Home/Work or adding a saved place opens an inline place search that
 * reuses [PlacesClient] + persists via [PlacesStore].
 */
@Composable
fun SavedPlacesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember { PlacesStore(context) }
    var favorites by remember { mutableStateOf(store.favorites()) }
    // Non-null while the inline picker is shown; the slot to write on pick.
    var picking by remember { mutableStateOf<FavoriteSlot?>(null) }

    fun refresh() { favorites = store.favorites() }

    BackHandler(enabled = picking != null) { picking = null }

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

    val home = favorites.firstOrNull { it.slot == FavoriteSlot.HOME }
    val work = favorites.firstOrNull { it.slot == FavoriteSlot.WORK }
    val saved = favorites.filter { it.slot == FavoriteSlot.OTHER }

    Column(
        modifier = Modifier.fillMaxSize().background(Ktm.Screen).systemBarsPadding(),
    ) {
        com.navigator.app.ui.components.ScreenTopBar(
            title = "Saved places",
            onBack = onBack,
            modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 10.dp, bottom = 6.dp),
        )

        LazyColumn(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp, top = 6.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                GroupCard("Home & Work") {
                    SlotRow(
                        icon = OpenDashIcons.House, title = "Home",
                        subtitle = home?.place?.label, showDivider = true,
                        onSet = { picking = FavoriteSlot.HOME },
                        onRemove = home?.let { { store.removeFavoriteSlot(FavoriteSlot.HOME); refresh() } },
                    )
                    SlotRow(
                        icon = OpenDashIcons.Briefcase, title = "Work",
                        subtitle = work?.place?.label, showDivider = false,
                        onSet = { picking = FavoriteSlot.WORK },
                        onRemove = work?.let { { store.removeFavoriteSlot(FavoriteSlot.WORK); refresh() } },
                    )
                }
            }

            item {
                GroupCard("Saved") {
                    if (saved.isEmpty()) {
                        SettingsRow("No saved places yet", showDivider = true) {}
                    } else {
                        saved.forEach { fav ->
                            SlotRow(
                                icon = OpenDashIcons.Bookmark,
                                title = fav.place.label,
                                subtitle = fav.place.address,
                                showDivider = true,
                                onSet = null,
                                onRemove = { store.removeFavorite(fav.place.lat, fav.place.lng); refresh() },
                            )
                        }
                    }
                    SettingsRow("Add place", showDivider = false, onClick = { picking = FavoriteSlot.OTHER }) {
                        Icon(OpenDashIcons.Search, null, tint = Ktm.Orange, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

/** A Home/Work/Saved row: tap to set (if [onSet] != null), trailing remove (if [onRemove] != null). */
@Composable
private fun SlotRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    showDivider: Boolean,
    onSet: (() -> Unit)?,
    onRemove: (() -> Unit)?,
) {
    SettingsRow(label = title, showDivider = showDivider, onClick = onSet) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (subtitle != null) {
                Text(
                    subtitle, color = Ktm.Muted2, fontFamily = Barlow, fontSize = 13.sp,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
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
