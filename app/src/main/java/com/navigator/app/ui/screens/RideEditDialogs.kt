package com.navigator.app.ui.screens

import com.navigator.app.ride.formatElapsed
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.navigator.app.nav.ktm.DistanceFormatter
import com.navigator.app.nav.model.DistanceUnits
import com.navigator.app.ride.RideMetrics
import com.navigator.app.ride.RidePoint
import com.navigator.app.ride.RideTrim
import com.navigator.app.ui.theme.Barlow
import com.navigator.app.ui.theme.BarlowCondensed
import com.navigator.app.ui.theme.Ktm

@Composable
private fun DialogAction(text: String, color: androidx.compose.ui.graphics.Color, enabled: Boolean = true, onClick: () -> Unit) {
    TextButton(onClick = onClick, enabled = enabled, modifier = Modifier.heightIn(min = 48.dp)) {
        Text(text, color = if (enabled) color else Ktm.Dim, fontFamily = BarlowCondensed, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}

/** Choose the part of the ride to keep (temporary: the trim page's real view comes with the map preview). Applying rewrites the track and cannot be undone. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TrimRideDialog(
    points: List<RidePoint>,
    onApply: (from: Int, to: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val last = points.lastIndex.coerceAtLeast(1)
    var range by remember { mutableStateOf(0f..last.toFloat()) }
    val from = range.start.toInt().coerceIn(0, last - 1)
    val to = range.endInclusive.toInt().coerceIn(from + 1, last)
    val kept = remember(from, to) { RideTrim.trim(points, from, to)?.let { RideMetrics.summarize(it) } }
    val unchanged = from == 0 && to == last
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Ktm.Surface,
        titleContentColor = Ktm.White,
        textContentColor = Ktm.TextSecondary,
        title = { Text("Trim ride", fontFamily = BarlowCondensed, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("Drag the handles to keep only the part you want.", fontFamily = Barlow, fontSize = 14.sp)
                RangeSlider(
                    value = range, onValueChange = { range = it },
                    valueRange = 0f..last.toFloat(),
                    colors = SliderDefaults.colors(
                        thumbColor = Ktm.Orange, activeTrackColor = Ktm.Orange, inactiveTrackColor = Ktm.Border,
                    ),
                    modifier = Modifier.padding(top = 8.dp),
                )
                kept?.let {
                    Text(
                        "Keeps " + DistanceFormatter.format(it.distanceMeters, DistanceUnits.METRIC) + " \u00b7 " +
                            formatElapsed(it.durationSeconds * 1000L),
                        color = Ktm.White, fontFamily = Barlow, fontSize = 14.sp,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text("This permanently removes the rest of the track.", color = Ktm.Muted2, fontFamily = Barlow, fontSize = 12.sp)
            }
        },
        confirmButton = { DialogAction("TRIM", Ktm.Danger, enabled = !unchanged && kept != null) { onApply(from, to) } },
        dismissButton = { DialogAction("CANCEL", Ktm.TextPrimary, onClick = onDismiss) },
    )
}
