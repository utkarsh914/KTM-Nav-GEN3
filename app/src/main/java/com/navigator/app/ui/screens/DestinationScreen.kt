package com.navigator.app.ui.screens

import android.content.Context
import android.location.LocationManager
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.navigator.app.nav.destination.PlaceSuggestion
import com.navigator.app.nav.destination.PlacesClient
import com.navigator.app.nav.ktm.DistanceFormatter
import com.navigator.app.nav.model.DistanceUnits
import com.navigator.app.nav.model.NavDestination
import com.navigator.app.ui.theme.BarlowCondensed
import com.navigator.app.ui.theme.JetBrainsMono
import com.navigator.app.ui.theme.Ktm
import com.navigator.app.ui.theme.OpenDashIcons
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Destination entry - Search (Phase 5a). Type a place, see matches with a
 * straight-line distance, tap to resolve its coordinates and start Google
 * navigation. Map-pin and shared-link entry are added in later slices.
 */
@Composable
fun DestinationScreen(
    onBack: () -> Unit,
    onNavigate: (NavDestination) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // One session token per search session (groups autocomplete + details for billing).
    val sessionToken = remember { UUID.randomUUID().toString() }
    val origin = remember { lastLocation(context) }
    // Android client identity so the Android-restricted key accepts direct REST calls.
    val auth = remember { PlacesClient.androidAuth(context) }

    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<PlaceSuggestion>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var resolving by remember { mutableStateOf(false) }

    // Debounced autocomplete.
    LaunchedEffect(query) {
        val q = query.trim()
        if (q.length < 3) {
            results = emptyList()
            loading = false
            return@LaunchedEffect
        }
        loading = true
        delay(300)
        results = PlacesClient.autocomplete(
            input = q,
            sessionToken = sessionToken,
            auth = auth,
            originLat = origin?.first,
            originLng = origin?.second,
        )
        loading = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Ktm.Screen)
            .systemBarsPadding()
            .padding(horizontal = 22.dp)
            .padding(top = 10.dp, bottom = 22.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                OpenDashIcons.ChevronLeft, contentDescription = "Back", tint = Ktm.TextSecondary,
                modifier = Modifier.size(26.dp).clip(RoundedCornerShape(8.dp)).clickable(onClick = onBack),
            )
            Spacer(Modifier.size(8.dp))
            Text(
                "ENTER DESTINATION", color = Ktm.White, fontFamily = BarlowCondensed,
                fontWeight = FontWeight.Bold, fontSize = 20.sp, letterSpacing = 1.5.sp,
            )
        }

        Spacer(Modifier.height(16.dp))

        if (!PlacesClient.apiKeyPresent) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    "Search needs a Google API key.\nSet NAV_SDK_API_KEY in local.properties.",
                    color = Ktm.Muted2, fontFamily = BarlowCondensed, fontSize = 18.sp,
                )
            }
        } else {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
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
                    resolving || loading -> {
                        CircularProgressIndicator(
                            color = Ktm.Orange, modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp),
                        )
                    }
                    results.isEmpty() && query.trim().length >= 3 -> {
                        Text(
                            "No matches", color = Ktm.Muted2, fontFamily = BarlowCondensed, fontSize = 18.sp,
                            modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp),
                        )
                    }
                    else -> {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(results, key = { it.placeId }) { s ->
                                SuggestionRow(s) {
                                    if (resolving) return@SuggestionRow
                                    resolving = true
                                scope.launch {
                                    val loc = PlacesClient.details(s.placeId, sessionToken, auth)
                                    resolving = false
                                        if (loc != null) {
                                            onNavigate(NavDestination(loc.lat, loc.lng, s.primary))
                                        }
                                    }
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
private fun SuggestionRow(s: PlaceSuggestion, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Ktm.RadiusRow))
            .background(Ktm.Surface)
            .border(1.dp, Ktm.Border, RoundedCornerShape(Ktm.RadiusRow))
            .clickable(onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                s.primary, color = Ktm.White, fontFamily = BarlowCondensed,
                fontWeight = FontWeight.SemiBold, fontSize = 18.sp,
            )
            s.secondary?.let {
                Text(it, color = Ktm.Muted2, fontFamily = BarlowCondensed, fontSize = 14.sp)
            }
        }
        val d = s.distanceMeters
        if (d != null && d > 0) {
            Spacer(Modifier.size(10.dp))
            Text(
                "~" + DistanceFormatter.format(d, DistanceUnits.METRIC),
                color = Ktm.Orange, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold, fontSize = 14.sp,
            )
        }
    }
}

/** Best-effort last-known location for the autocomplete origin (permission already granted). */
private fun lastLocation(context: Context): Pair<Double, Double>? {
    return try {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
        for (p in providers) {
            val loc = runCatching { lm.getLastKnownLocation(p) }.getOrNull()
            if (loc != null) return loc.latitude to loc.longitude
        }
        null
    } catch (e: SecurityException) {
        null
    }
}
