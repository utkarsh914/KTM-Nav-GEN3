package com.navigator.app.ui.screens

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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.navigator.app.nav.destination.FavoritePlace
import com.navigator.app.nav.destination.FavoriteSlot
import com.navigator.app.nav.destination.PlaceSuggestion
import com.navigator.app.nav.destination.SavedPlace
import com.navigator.app.nav.ktm.DistanceFormatter
import com.navigator.app.nav.model.DistanceUnits
import com.navigator.app.ui.theme.BarlowCondensed
import com.navigator.app.ui.theme.JetBrainsMono
import com.navigator.app.ui.theme.Ktm
import com.navigator.app.ui.theme.OpenDashIcons

// Destination-search UI (search panel + recents/favorites rows) extracted from
// NavigationHomeScreen. Same package, so these `internal` helpers are called
// directly by the main screen.

@Composable
internal fun SearchPanel(
    query: String,
    onQuery: (String) -> Unit,
    loading: Boolean,
    results: List<PlaceSuggestion>,
    recents: List<SavedPlace>,
    favorites: List<FavoritePlace>,
    origin: Pair<Double, Double>?,
    onBack: () -> Unit,
    onPickSuggestion: (PlaceSuggestion) -> Unit,
    onPickSaved: (SavedPlace) -> Unit,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    Column(
        modifier = Modifier.fillMaxSize().background(Ktm.Screen)
            .systemBarsPadding().padding(horizontal = 16.dp).padding(top = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                OpenDashIcons.ChevronLeft, "Back", tint = Ktm.TextSecondary,
                modifier = Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).clickable(onClick = onBack),
            )
            Spacer(Modifier.size(8.dp))
            OutlinedTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                modifier = Modifier.weight(1f).focusRequester(focus),
                placeholder = { Text("Enter Destination", color = Ktm.Dim) },
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
        }

        Spacer(Modifier.height(12.dp))

        val showResults = query.trim().length >= 3
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                loading -> CircularProgressIndicator(
                    color = Ktm.Orange,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp),
                )
                showResults && results.isEmpty() -> Text(
                    "No matches", color = Ktm.Muted2, fontFamily = BarlowCondensed, fontSize = 18.sp,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp),
                )
                showResults -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(results, key = { it.placeId }) { s ->
                        SuggestionRow(s) { onPickSuggestion(s) }
                    }
                }
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val homes = favorites.filter { it.slot == FavoriteSlot.HOME }
                    val works = favorites.filter { it.slot == FavoriteSlot.WORK }
                    val saved = favorites.filter { it.slot == FavoriteSlot.OTHER }
                    val home = homes.firstOrNull()
                    val work = works.firstOrNull()
                    if (home != null || work != null) {
                        item { SectionLabel("FAVORITES") }
                        if (home != null && work != null) {
                            // Both set: Home and Work share one row, side by side.
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    FavoriteHalfCard(
                                        icon = OpenDashIcons.House, title = "Home", subtitle = home.place.label,
                                        modifier = Modifier.weight(1f),
                                    ) { onPickSaved(home.place) }
                                    FavoriteHalfCard(
                                        icon = OpenDashIcons.Briefcase, title = "Work", subtitle = work.place.label,
                                        modifier = Modifier.weight(1f),
                                    ) { onPickSaved(work.place) }
                                }
                            }
                        } else {
                            // Only one set: keep the full-width row.
                            val only = home ?: work!!
                            item {
                                SavedRow(
                                    icon = if (only.slot == FavoriteSlot.HOME) OpenDashIcons.House else OpenDashIcons.Briefcase,
                                    title = if (only.slot == FavoriteSlot.HOME) "Home" else "Work",
                                    subtitle = only.place.label,
                                    origin = origin, lat = only.place.lat, lng = only.place.lng,
                                ) { onPickSaved(only.place) }
                            }
                        }
                    }
                    if (saved.isNotEmpty()) {
                        item { SectionLabel("SAVED") }
                        items(saved, key = { "s-${it.place.lat},${it.place.lng}" }) { f ->
                            SavedRow(
                                icon = OpenDashIcons.Bookmark, title = f.place.label,
                                subtitle = f.place.address, origin = origin,
                                lat = f.place.lat, lng = f.place.lng,
                            ) { onPickSaved(f.place) }
                        }
                    }
                    if (recents.isNotEmpty()) {
                        item { SectionLabel("RECENT") }
                        items(recents, key = { "r-${it.lat},${it.lng}" }) { p ->
                            SavedRow(
                                icon = OpenDashIcons.Clock, title = p.label, subtitle = p.address,
                                origin = origin, lat = p.lat, lng = p.lng,
                            ) { onPickSaved(p) }
                        }
                    }
                    if (recents.isEmpty() && favorites.isEmpty()) {
                        item {
                            Text(
                                "Search for a place to get started.",
                                color = Ktm.Muted2, fontFamily = BarlowCondensed, fontSize = 16.sp,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text, color = Ktm.Muted2, fontFamily = BarlowCondensed, fontWeight = FontWeight.Bold,
        fontSize = 12.sp, letterSpacing = 1.5.sp,
        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
    )
}

@Composable
private fun SuggestionRow(s: PlaceSuggestion, onClick: () -> Unit) {
    RowCard(onClick) {
        Column(Modifier.weight(1f)) {
            Text(s.primary, color = Ktm.White, fontFamily = BarlowCondensed, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
            s.secondary?.let { Text(it, color = Ktm.Muted2, fontFamily = BarlowCondensed, fontSize = 14.sp) }
        }
        s.distanceMeters?.takeIf { it > 0 }?.let {
            Spacer(Modifier.size(10.dp))
            Text(
                "~" + DistanceFormatter.format(it, DistanceUnits.METRIC),
                color = Ktm.Orange, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold, fontSize = 14.sp,
            )
        }
    }
}

@Composable
private fun SavedRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    origin: Pair<Double, Double>?,
    lat: Double,
    lng: Double,
    onClick: () -> Unit,
) {
    RowCard(onClick) {
        Icon(icon, null, tint = Ktm.TextSecondary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Ktm.White, fontFamily = BarlowCondensed, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
            subtitle?.let { Text(it, color = Ktm.Muted2, fontFamily = BarlowCondensed, fontSize = 14.sp) }
        }
        distanceLabel(origin, lat, lng)?.let {
            Spacer(Modifier.size(10.dp))
            Text(it, color = Ktm.Orange, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
}

/** Compact half-width favorite card used when both Home and Work are set. */
@Composable
private fun FavoriteHalfCard(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(Ktm.RadiusRow))
            .background(Ktm.Surface)
            .border(1.dp, Ktm.Border, RoundedCornerShape(Ktm.RadiusRow))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
    ) {
        Icon(icon, null, tint = Ktm.TextSecondary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.size(8.dp))
        Text(title, color = Ktm.White, fontFamily = BarlowCondensed, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
        subtitle?.let {
            Text(
                it, color = Ktm.Muted2, fontFamily = BarlowCondensed, fontSize = 14.sp,
                maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun RowCard(onClick: () -> Unit, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(Ktm.RadiusRow))
            .background(Ktm.Surface)
            .border(1.dp, Ktm.Border, RoundedCornerShape(Ktm.RadiusRow))
            .clickable(onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}
