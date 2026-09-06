package com.navigator.app.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
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
 * Lists recorded rides, newest first. A plain tap opens the replay; a long-press
 * enters multi-select mode (selection state is hoisted to [MainActivity] so it
 * survives opening a ride and coming back). Deletes ask for confirmation, and a
 * star pins a ride so the history-limit prune never removes it.
 */
@Composable
fun RideHistoryScreen(
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    selectionMode: Boolean,
    selectedIds: Set<String>,
    onSelectionModeChange: (Boolean) -> Unit,
    onSelectedIdsChange: (Set<String>) -> Unit,
) {
    val context = LocalContext.current
    val store = remember { RideStore(context) }
    var rides by remember { mutableStateOf(store.list()) }
    // Confirmation target: a single ride id, or the sentinel for "delete selected".
    var confirmDelete by remember { mutableStateOf<DeleteTarget?>(null) }

    fun refresh() { rides = store.list() }

    fun toggle(id: String) {
        onSelectedIdsChange(if (id in selectedIds) selectedIds - id else selectedIds + id)
    }

    fun enterSelection(id: String) {
        onSelectionModeChange(true)
        onSelectedIdsChange(selectedIds + id)
    }

    fun exitSelection() {
        onSelectionModeChange(false)
        onSelectedIdsChange(emptySet())
    }

    // Selection may reference rides that no longer exist after a delete; keep it
    // consistent with the current list.
    val allIds = rides.map { it.id }.toSet()
    val validSelected = selectedIds.intersect(allIds)

    Column(modifier = Modifier.fillMaxSize().background(Ktm.Screen).systemBarsPadding()) {
        ScreenTopBar(
            title = if (selectionMode) "${validSelected.size}/${rides.size} selected" else "Ride recordings",
            onBack = onBack,
            modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 10.dp, bottom = 6.dp),
        ) {
            if (selectionMode) {
                // Single toggle: same icon, filled when all are selected, else outlined.
                val allSelected = rides.isNotEmpty() && validSelected.size == rides.size
                PopIconButton(
                    icon = if (allSelected) Icons.Filled.CheckCircle else Icons.Outlined.CheckCircle,
                    desc = if (allSelected) "Deselect all" else "Select all",
                    tint = if (allSelected) Ktm.Orange else Ktm.White,
                    onClick = {
                        onSelectedIdsChange(if (allSelected) emptySet() else allIds)
                    },
                )
                Spacer(Modifier.size(6.dp))
                PopIconButton(
                    icon = Icons.Filled.Delete,
                    desc = "Delete selected",
                    tint = if (validSelected.isEmpty()) Ktm.Dim else Ktm.Danger,
                    onClick = { if (validSelected.isNotEmpty()) confirmDelete = DeleteTarget.Selected },
                )
            }
        }

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
                    selectionMode = selectionMode,
                    selected = ride.id in validSelected,
                    onTap = { if (selectionMode) toggle(ride.id) else onOpen(ride.id) },
                    onLongPress = { if (!selectionMode) enterSelection(ride.id) else toggle(ride.id) },
                    onToggleSave = {
                        val newSaved = !ride.saved
                        store.setSaved(ride.id, newSaved)
                        refresh()
                        android.widget.Toast.makeText(
                            context,
                            if (newSaved) "Ride saved" else "Ride unsaved",
                            android.widget.Toast.LENGTH_SHORT,
                        ).show()
                    },
                    onDelete = { confirmDelete = DeleteTarget.Single(ride.id) },
                )
            }
        }
    }

    confirmDelete?.let { target ->
        val count = when (target) {
            is DeleteTarget.Single -> 1
            DeleteTarget.Selected -> validSelected.size
        }
        DeleteConfirmDialog(
            count = count,
            onConfirm = {
                when (target) {
                    is DeleteTarget.Single -> store.delete(target.id)
                    DeleteTarget.Selected -> {
                        store.deleteAll(validSelected)
                        exitSelection()
                    }
                }
                refresh()
                confirmDelete = null
            },
            onDismiss = { confirmDelete = null },
        )
    }
}

private sealed interface DeleteTarget {
    data class Single(val id: String) : DeleteTarget
    data object Selected : DeleteTarget
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RideCard(
    ride: RecordedRide,
    selectionMode: Boolean,
    selected: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onToggleSave: () -> Unit,
    onDelete: () -> Unit,
) {
    // Subtle bump whenever this card's selection flips (skips first composition,
    // so it never fires on initial render or when scrolling back into view).
    val cardScale = remember { Animatable(1f) }
    var firstComposition by remember { mutableStateOf(true) }
    LaunchedEffect(selected) {
        if (firstComposition) { firstComposition = false; return@LaunchedEffect }
        cardScale.snapTo(0.96f)
        cardScale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow))
    }
    // Border grows/fades in smoothly on select (at target on first frame → no
    // spurious animation).
    val borderWidth by animateDpAsState(if (selected) 2.dp else 0.dp, label = "border")
    val borderColor by animateColorAsState(if (selected) Ktm.Orange else Color.Transparent, label = "borderColor")
    Row(
        modifier = Modifier
            .graphicsLayer { scaleX = cardScale.value; scaleY = cardScale.value }
            .fillMaxWidth()
            .clip(RoundedCornerShape(Ktm.RadiusCard))
            .background(Ktm.Surface)
            .border(borderWidth, borderColor, RoundedCornerShape(Ktm.RadiusCard))
            .combinedClickable(onClick = onTap, onLongClick = onLongPress)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selectionMode) {
            Icon(
                if (selected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                if (selected) "Selected" else "Not selected",
                tint = if (selected) Ktm.Orange else Ktm.Dim,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.size(12.dp))
        }
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
        if (!selectionMode) {
            SaveStar(saved = ride.saved, boxSize = 32.dp, iconSize = 26.dp, onToggle = onToggleSave)
            Spacer(Modifier.size(8.dp))
            PopIconButton(
                icon = Icons.Filled.Delete, desc = "Delete ride", tint = Ktm.Dim,
                boxSize = 32.dp, iconSize = 26.dp, onClick = onDelete,
            )
        } else if (ride.saved) {
            // Still surface the pinned state while selecting.
            Icon(
                Icons.Filled.Star, "Saved", tint = Ktm.Orange,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

/**
 * An icon button with a subtle spring "pop" on tap (same feel as [SaveStar]).
 * Used for the multi-select top-bar actions and the per-card delete.
 */
@Composable
private fun PopIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    desc: String,
    tint: androidx.compose.ui.graphics.Color = Ktm.White,
    boxSize: Dp = 40.dp,
    iconSize: Dp = 24.dp,
    onClick: () -> Unit,
) {
    val scale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    Box(
        modifier = Modifier
            .size(boxSize)
            .clip(RoundedCornerShape(Ktm.RadiusButton))
            .clickable {
                scope.launch {
                    scale.snapTo(0.6f)
                    scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow))
                }
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon, desc, tint = tint,
            modifier = Modifier
                .size(iconSize)
                .graphicsLayer { scaleX = scale.value; scaleY = scale.value },
        )
    }
}

/**
 * Save/pin toggle star with a subtle "pop" on tap: the icon dips then springs
 * back to full size. Shared by the history list and the replay screen.
 */
@Composable
internal fun SaveStar(saved: Boolean, boxSize: Dp, iconSize: Dp, onToggle: () -> Unit) {
    val scale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    Box(
        modifier = Modifier
            .size(boxSize)
            .clip(RoundedCornerShape(Ktm.RadiusButton))
            .clickable {
                scope.launch {
                    scale.snapTo(0.6f)
                    scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow))
                }
                onToggle()
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (saved) Icons.Filled.Star else Icons.Outlined.StarBorder,
            if (saved) "Saved - tap to unsave" else "Save permanently",
            tint = if (saved) Ktm.Orange else Ktm.Dim,
            modifier = Modifier
                .size(iconSize)
                .graphicsLayer { scaleX = scale.value; scaleY = scale.value },
        )
    }
}

@Composable
private fun DeleteConfirmDialog(count: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Ktm.Surface,
        titleContentColor = Ktm.White,
        textContentColor = Ktm.TextSecondary,
        title = {
            Text(
                if (count > 1) "Delete $count rides?" else "Delete this ride?",
                fontFamily = BarlowCondensed, fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Text(
                "This permanently removes the recorded track. This can't be undone.",
                fontFamily = Barlow,
            )
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("DELETE", color = Ktm.Danger) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("CANCEL", color = Ktm.TextPrimary) } },
    )
}

/** e.g. "Sun 6 Sep, 14:32". */
internal fun formatRideDate(ms: Long): String {
    if (ms <= 0L) return ""
    val dt = java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneId.systemDefault())
    return dt.format(java.time.format.DateTimeFormatter.ofPattern("EEE d MMM, HH:mm"))
}
