package com.navigator.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.navigator.app.ble.BccuProtocol.TurnIcon
import com.navigator.app.data.settings.SettingSpecs
import com.navigator.app.ui.theme.KtmIcons
import com.navigator.app.ui.theme.KtmSpacing
import com.navigator.app.ui.theme.KtmTheme

/** Debug-only gallery of the shared components (design §3.6), dark and light. Open the previews in Android Studio. */
@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    Column(Modifier.padding(top = KtmSpacing.xl), verticalArrangement = Arrangement.spacedBy(KtmSpacing.sm)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = KtmTheme.colors.fg2)
        content()
    }
}

private class FakeSnackbar(override val visuals: SnackbarVisuals) : SnackbarData {
    override fun performAction() = Unit
    override fun dismiss() = Unit
}

private class Visuals(override val message: String, override val actionLabel: String?) : SnackbarVisuals {
    override val withDismissAction = false
    override val duration = SnackbarDuration.Short
}

@Composable
internal fun ComponentGallery() {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(Modifier.padding(KtmSpacing.edge)) {
            KtmTopBar(title = "Settings", onBack = {}, actions = {
                KtmIconButton(KtmIcons.More, "More", {})
            })
            LargeTitle("Rides")

            Group("Buttons · styles") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ButtonStyle.entries.forEach { KtmButton(it.name, {}, style = it) }
                }
            }
            Group("Buttons · states and sizes") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    KtmButton("Enabled", {}); KtmButton("Disabled", {}, enabled = false)
                    KtmButton("Loading", {}, loading = true); KtmButton("With icon", {}, icon = KtmIcons.Navigation)
                    KtmButton("Small", {}, size = ButtonSize.Small); KtmButton("Riding", {}, size = ButtonSize.Riding)
                    KtmButton("Tile", {}, size = ButtonSize.Tile, style = ButtonStyle.Tonal, icon = KtmIcons.Add)
                }
            }
            Group("Icon buttons") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KtmIconButton(KtmIcons.Share, "Share", {})
                    KtmIconButton(KtmIcons.Edit, "Edit", {}, style = IconButtonStyle.Tonal)
                    KtmIconButton(KtmIcons.Add, "Add", {}, style = IconButtonStyle.Accent)
                    MapControlButton(KtmIcons.Recenter, "Recenter", {})
                    MapControlButton(KtmIcons.Recenter, "Recenter", {}, accent = true)
                    MapControlButton(KtmIcons.CompassNorth, "North", {}, rotationDegrees = 30f)
                }
            }
            Group("Controls") {
                var on by remember { mutableStateOf(true) }
                var avg by remember { mutableStateOf("Moving") }
                var vol by remember { mutableStateOf("35%") }
                var n by remember { mutableIntStateOf(80) }
                var name by remember { mutableStateOf("Arj") }
                KtmSwitchRow("Latin letters only", on, { on = it }, supporting = "Kannada, Hindi names are transliterated")
                KtmSwitchRow("Disabled", false, {}, enabled = false)
                KtmSegmented(listOf("Moving", "Overall"), avg, { avg = it }, { it })
                KtmSegmented(SettingSpecs.beepVolumeSteps.map { "$it%" }, vol, { vol = it }, { it }, Modifier.padding(top = 8.dp))
                KtmStepper(n, SettingSpecs.overspeedLimit, { n = it }, "km/h", "30–200 km/h", "Decrease", "Increase", "Edit speed alert", {})
                KtmTextField(name, { name = it }, "Your name", helper = "Shown on the dash")
                KtmTextField("5", {}, "Keep last N rides", error = "Enter a number from 10 to 1000")
                KtmCheckboxRow("WhatsApp", true, {})
                KtmRadioRow("Bike", true, {})
            }
            Group("Chips, pill, badges") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    KtmFilterChip("Fuel", true, {}, leadingIcon = KtmIcons.Fuel)
                    KtmFilterChip("Food", false, {}, leadingIcon = KtmIcons.Food)
                    BikeStatusPill("KTM 7F21", PillTone.Connected, {})
                    BikeStatusPill("Connecting…", PillTone.Connecting, {})
                    BikeStatusPill("Pair your bike", PillTone.NotPaired, {})
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BadgeTone.entries.forEach { KtmBadge(it.name, tone = it) }
                }
            }
            Group("Rows, cards, banners") {
                SectionHeader("Bike & dash")
                ListRow("Bike", supporting = "KTM 7F21 · notifications on", leadingIcon = KtmIcons.Bike, trailing = { RowChevron() })
                ListDivider()
                ListRow("Average speed", leadingIcon = KtmIcons.Speed, trailing = { RowValue("Moving") })
                KtmCard { Text("Ride card", color = KtmTheme.colors.fg) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { StatTile("Distance", "6.3", unit = "km"); StatTile("Top speed", "61", unit = "km/h") }
                FixItBanner("Notification access needed", "Mirror mode reads Google Maps' turn notifications.", "Allow", {})
                InfoBanner("You can still start. Google will pick the route.", title = "Couldn't load routes")
                InfoBanner("Offline", tone = BannerTone.Bad)
            }
            Group("Sheet and snackbar") {
                KtmSheetContent(title = "Sheet title", actions = {
                    KtmButton("Save", {}, Modifier.padding(bottom = 8.dp), size = ButtonSize.Rest)
                }) { Text("Handle + title row + content.", color = KtmTheme.colors.fg2) }
                KtmSnackbar(FakeSnackbar(Visuals("Ride deleted", "Undo")))
            }
            Group("Activity strip") {
                ActivityStrip(ActivityStripState.Trip("250 m · 100 Feet Rd", "Toit Brewpub · 6:29 pm · ● Rec", TurnIcon.QUITE_RIGHT), {})
                ActivityStrip(ActivityStripState.Recording("Recording · 12:48", "6.4 km · since 5:58 pm", paused = false), {}, Modifier.padding(top = 8.dp))
                ActivityStrip(ActivityStripState.Recording("Recording paused", "12:48 · 6.4 km", paused = true), {}, Modifier.padding(top = 8.dp))
                ActivityStrip(ActivityStripState.Mirroring("Mirroring · 350 m", "From Google Maps · 2 s ago"), {}, Modifier.padding(top = 8.dp))
            }
            Group("Dash preview") {
                DashPreview(DashPreviewState.Turn("6:12 pm", TurnIcon.QUITE_RIGHT, "250 m", "100 Feet Rd", "ETA 6:29 pm", "5.9 km"))
                DashPreview(DashPreviewState.Message("6:12 pm", "WhatsApp", "Rahul: at Toit, table 4", "ETA 6:29 pm", "5.9 km"), Modifier.padding(top = 8.dp))
                DashPreview(DashPreviewState.NotConnected("Dash not connected", "Turn the ignition on. We'll reconnect automatically."), Modifier.padding(top = 8.dp))
            }
            Group("Empty state and skeleton") {
                EmptyState(KtmIcons.Route, "No rides yet", "Every trip you navigate is recorded here.", primary = StateAction("Record a ride", {}), secondary = StateAction("Import GPX", {}))
                SkeletonRow(); SkeletonRow()
            }
        }
    }
}

@Preview(name = "Components dark", widthDp = 400, heightDp = 3600, showBackground = true)
@Composable
private fun ComponentGalleryDark() = KtmTheme(darkTheme = true) { ComponentGallery() }

@Preview(name = "Components light", widthDp = 400, heightDp = 3600, showBackground = true)
@Composable
private fun ComponentGalleryLight() = KtmTheme(darkTheme = false) { ComponentGallery() }
