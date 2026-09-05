package com.navigator.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.navigator.app.nav.ktm.DistanceFormatter
import com.navigator.app.nav.model.DistanceUnits
import com.navigator.app.ride.RideStore
import com.navigator.app.ride.RecordedRide
import com.navigator.app.ui.components.ScreenTopBar
import com.navigator.app.ui.theme.Barlow
import com.navigator.app.ui.theme.BarlowCondensed
import com.navigator.app.ui.theme.JetBrainsMono
import com.navigator.app.ui.theme.Ktm

/**
 * Lists recorded rides, newest first. Tapping a ride opens its replay; the trash
 * icon deletes it (index entry + track file). Data comes straight from
 * [RideStore] following the [SavedPlacesScreen] pattern.
 */
@Composable
fun RideHistoryScreen(onBack: () -> Unit, onOpen: (String) -> Unit) {
    val context = LocalContext.current
    val store = remember { RideStore(context) }
    var rides by remember { mutableStateOf(store.list()) }

    fun refresh() { rides = store.list() }

    Column(modifier = Modifier.fillMaxSize().background(Ktm.Screen).systemBarsPadding()) {
        ScreenTopBar(
            title = "Ride recordings",
            onBack = onBack,
            modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 10.dp, bottom = 6.dp),
        )

        if (rides.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "No rides recorded yet.\nStart a navigation to record one.",
                    color = Ktm.Muted2, fontFamily = Barlow, fontSize = 15.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp),
            contentPadding = PaddingValues(bottom = 24.dp, top = 6.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(rides, key = { it.id }) { ride ->
                RideCard(
                    ride = ride,
                    onOpen = { onOpen(ride.id) },
                    onDelete = { store.delete(ride.id); refresh() },
                )
            }
        }
    }
}

@Composable
private fun RideCard(ride: RecordedRide, onOpen: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Ktm.RadiusCard))
            .background(Ktm.Surface)
            .clickable(onClick = onOpen)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                ride.destinationLabel ?: "Ride",
                color = Ktm.White, fontFamily = BarlowCondensed, fontWeight = FontWeight.Bold,
                fontSize = 18.sp, maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            Spacer(Modifier.size(2.dp))
            Text(
                formatRideDate(ride.startMs),
                color = Ktm.Muted2, fontFamily = Barlow, fontSize = 12.sp,
            )
            Spacer(Modifier.size(6.dp))
            Text(
                buildString {
                    append(DistanceFormatter.format(ride.distanceMeters, DistanceUnits.METRIC))
                    append("  •  ")
                    append(formatDuration(ride.durationSeconds))
                    append("  •  ")
                    append("${ride.maxSpeedKmh.toInt()} km/h max")
                },
                color = Ktm.TextSecondary, fontFamily = JetBrainsMono, fontSize = 12.sp,
            )
        }
        Icon(
            Icons.Filled.Delete, "Delete ride", tint = Ktm.Dim,
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(6.dp))
                .clickable(onClick = onDelete)
                .padding(2.dp),
        )
    }
}

/** e.g. "Sun 6 Sep, 14:32". */
internal fun formatRideDate(ms: Long): String {
    if (ms <= 0L) return ""
    val dt = java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneId.systemDefault())
    return dt.format(java.time.format.DateTimeFormatter.ofPattern("EEE d MMM, HH:mm"))
}
