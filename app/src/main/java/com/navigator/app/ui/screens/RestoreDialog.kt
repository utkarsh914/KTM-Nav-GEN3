package com.navigator.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.navigator.app.backup.BackupManager
import com.navigator.app.ui.theme.Barlow
import com.navigator.app.ui.theme.BarlowCondensed
import com.navigator.app.ui.theme.Ktm

/** Pick which parts of a backup to restore (rides, places, settings), with what the file holds. */
@Composable
internal fun RestoreDialog(
    preview: BackupManager.Preview,
    onRestore: (rides: Boolean, places: Boolean, settings: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var rides by remember { mutableStateOf(preview.newRides > 0) }
    var places by remember { mutableStateOf(preview.places > 0) }
    var settings by remember { mutableStateOf(preview.settings > 0) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Ktm.Surface, titleContentColor = Ktm.White, textContentColor = Ktm.TextSecondary,
        title = { Text("Restore from backup", fontFamily = BarlowCondensed, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("Choose what to restore. Nothing is ever deleted.", fontFamily = Barlow, fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))
                RestoreRow(
                    "Rides", if (preview.ridesInFile == 0) "none in this file"
                    else "${preview.newRides} new (${preview.ridesInFile} in file)",
                    checked = rides, enabled = preview.newRides > 0,
                ) { rides = it }
                RestoreRow(
                    "Saved places", if (preview.places == 0) "none in this file" else "${preview.places} in file, merged with yours",
                    checked = places, enabled = preview.places > 0,
                ) { places = it }
                RestoreRow(
                    "Settings", if (preview.settings == 0) "none in this file" else "${preview.settings} in file, replace yours (restart the app to apply)",
                    checked = settings, enabled = preview.settings > 0,
                ) { settings = it }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onRestore(rides, places, settings) },
                enabled = rides || places || settings,
                modifier = Modifier.heightIn(min = 56.dp),
            ) { Text("RESTORE", color = if (rides || places || settings) Ktm.Orange else Ktm.Dim) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 56.dp)) {
                Text("CANCEL", color = Ktm.TextPrimary)
            }
        },
    )
}

@Composable
private fun RestoreRow(title: String, detail: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
            .clickable(enabled = enabled, role = Role.Checkbox) { onChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked && enabled, onCheckedChange = null, enabled = enabled,
            colors = CheckboxDefaults.colors(checkedColor = Ktm.Orange, uncheckedColor = Ktm.Muted2, checkmarkColor = Ktm.OnAccent),
        )
        Column(Modifier.padding(start = 12.dp)) {
            Text(title, color = if (enabled) Ktm.White else Ktm.Dim, fontFamily = Barlow, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Text(detail, color = Ktm.Muted2, fontFamily = Barlow, fontSize = 12.sp)
        }
    }
}
