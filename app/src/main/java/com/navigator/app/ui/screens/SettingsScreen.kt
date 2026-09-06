package com.navigator.app.ui.screens

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.navigator.app.ble.BccuConnectionService
import com.navigator.app.ble.BccuProtocol
import com.navigator.app.ride.RideStore
import com.navigator.app.settings.AppSettings
import com.navigator.app.ui.components.GroupCard
import com.navigator.app.ui.components.KtmToggle
import com.navigator.app.ui.components.MonoValue
import com.navigator.app.ui.components.SettingsRow
import com.navigator.app.ui.theme.Barlow
import com.navigator.app.ui.theme.BarlowCondensed
import com.navigator.app.ui.theme.Ktm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private enum class SettingsDialog {
    NONE, NAME, NAV_APP, MIRROR_APPS, CALL_AUDIO, OVERSPEED_LIMIT, THEME, NAV_THEME, ACCENT, ACCENT_CUSTOM,
    RIDE_MIN_DISTANCE, RIDE_MIN_DURATION, RIDE_HISTORY_LIMIT,
}

/**
 * Settings (screen 05) — the §5 restructure into four labelled groups
 * (Connection, Notifications, Navigation, Diagnostics). Every capability from
 * the original flat list is preserved: name, Gemini key, nav detection +
 * manual override, mirror-from apps picker, call-audio device, battery
 * optimization, logs, test notification, test guidance, symbol testing, build
 * version — plus the new Re-pair, quick-mute and marquee toggles.
 */
@SuppressLint("MissingPermission")
@Composable
fun SettingsScreen(
    settings: AppSettings,
    onBack: () -> Unit,
    onOpenLogs: () -> Unit,
    onOpenSymbolTest: () -> Unit,
    onOpenTurnCalibration: () -> Unit,
    onChangeBrand: () -> Unit = {},
    onOpenPlaces: () -> Unit = {},
    onOpenRecordings: () -> Unit = {},
    onEngineChanged: (Boolean) -> Unit = {},
    themeMode: com.navigator.app.ui.theme.ThemeMode = com.navigator.app.ui.theme.ThemeMode.SYSTEM,
    onThemeModeChanged: (com.navigator.app.ui.theme.ThemeMode) -> Unit = {},
    navThemeMode: com.navigator.app.ui.theme.NavThemeMode = com.navigator.app.ui.theme.NavThemeMode.DAY_NIGHT,
    onNavThemeModeChanged: (com.navigator.app.ui.theme.NavThemeMode) -> Unit = {},
    accentColorId: String? = null,
    onAccentColorChanged: (String?) -> Unit = {},
    listState: androidx.compose.foundation.lazy.LazyListState = androidx.compose.foundation.lazy.rememberLazyListState(),
    onRepair: () -> Unit,
) {
    val context = LocalContext.current
    val pm = context.packageManager


    var userName by remember { mutableStateOf(settings.userName ?: "") }
    var navAutoDetect by remember { mutableStateOf(settings.navAppOverride == null) }
    var navOverride by remember { mutableStateOf(settings.navAppOverride) }
    var sourceApps by remember { mutableStateOf(settings.notificationSourceApps) }
    var preferredAudioAddress by remember { mutableStateOf(settings.preferredCallAudioDeviceAddress) }
    var mirrorEnabled by remember { mutableStateOf(settings.mirrorEnabled) }
    var marqueeEnabled by remember { mutableStateOf(settings.marqueeEnabled) }

    var turnBeepEnabled by remember { mutableStateOf(settings.turnBeepEnabled) }
    var beepVolume by remember { mutableStateOf(settings.beepVolumePercent) }
    var overspeedEnabled by remember { mutableStateOf(settings.overspeedEnabled) }
    var overspeedLimit by remember { mutableStateOf(settings.overspeedLimitKmh) }

    var rideRecordingOn by remember { mutableStateOf(settings.rideRecordingEnabled) }
    var rideMinDist by remember { mutableStateOf(settings.rideMinDistanceMeters) }
    var rideMinDur by remember { mutableStateOf(settings.rideMinDurationSeconds) }
    var rideHistoryLimit by remember { mutableStateOf(settings.rideHistoryLimit) }

    var googleNavOn by remember { mutableStateOf(settings.googleNavEnabled) }
    var dialog by remember { mutableStateOf(SettingsDialog.NONE) }

    // Both of these are slow binder IPC (enumerating every installed app; the
    // Bluetooth stack) and were previously computed synchronously inside
    // remember{} during first composition, stalling the screen's first frame.
    // Load them off the main thread so Settings opens instantly; the dialogs
    // that consume them tolerate the brief empty-then-populated window.
    val installedApps by produceState(initialValue = emptyList<android.content.pm.ApplicationInfo>()) {
        value = withContext(Dispatchers.IO) {
            pm.getInstalledApplications(PackageManager.GET_META_DATA)
                .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
                .sortedBy { pm.getApplicationLabel(it).toString() }
        }
    }
    val bondedAudioDevices by produceState(initialValue = emptyList<android.bluetooth.BluetoothDevice>()) {
        value = withContext(Dispatchers.IO) {
            val adapter = context.getSystemService(BluetoothManager::class.java).adapter
            adapter?.bondedDevices?.filter {
                it.bluetoothClass?.hasService(android.bluetooth.BluetoothClass.Service.AUDIO) == true
            } ?: emptyList()
        }
    }

    var notificationAccessGranted by remember {
        mutableStateOf(NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName))
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationAccessGranted =
                    NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val connectionState by BccuConnectionService.connectionState.collectAsState()
    val connected = connectionState == BccuConnectionService.ConnectionState.AUTHENTICATED
    val vehicleName = settings.bondedDeviceName ?: "Not paired"

    fun appLabel(pkg: String): String = try {
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (e: Exception) { pkg }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Ktm.Screen)
            .systemBarsPadding(),
    ) {
        com.navigator.app.ui.components.ScreenTopBar(
            title = "Settings",
            onBack = onBack,
            modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 10.dp, bottom = 6.dp),
        )

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp, top = 6.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ===== Appearance =====
            item {
                GroupCard("Appearance") {
                    SettingsRow("Theme", showDivider = true, onClick = { dialog = SettingsDialog.THEME }) {
                        val label = when (themeMode) {
                            com.navigator.app.ui.theme.ThemeMode.SYSTEM -> "System"
                            com.navigator.app.ui.theme.ThemeMode.LIGHT -> "Light"
                            com.navigator.app.ui.theme.ThemeMode.DARK -> "Dark"
                        }
                        MonoValue("$label ›")
                    }
                    SettingsRow("Navigation theme", showDivider = true, onClick = { dialog = SettingsDialog.NAV_THEME }) {
                        val label = when (navThemeMode) {
                            com.navigator.app.ui.theme.NavThemeMode.APP -> "App default"
                            com.navigator.app.ui.theme.NavThemeMode.DAY_NIGHT -> "Day / Night"
                        }
                        MonoValue("$label ›")
                    }
                    SettingsRow("Accent colour", showDivider = false, onClick = { dialog = SettingsDialog.ACCENT }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(16.dp).clip(CircleShape).background(Ktm.Orange)
                                    .border(1.dp, Ktm.BorderSoft, CircleShape),
                            )
                            androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))
                            val label = com.navigator.app.ui.theme.accentFor(accentColorId)?.displayName ?: "Match bike"
                            MonoValue("$label ›")
                        }
                    }
                }
            }

            // ===== Connection =====
            item {
                GroupCard("Connection") {
                    SettingsRow("Vehicle", showDivider = true) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (connected) {
                                Box(Modifier.size(7.dp).clip(CircleShape).background(Ktm.Green))
                                androidx.compose.foundation.layout.Spacer(Modifier.size(7.dp))
                            }
                            MonoValue(vehicleName, color = if (connected) Ktm.Green else Ktm.Dim)
                        }
                    }
                    SettingsRow("Re-pair vehicle", showDivider = true, onClick = onRepair) {
                        OutlinedPill("RE-PAIR")
                    }
                    SettingsRow("Change bike / brand", showDivider = true, onClick = onChangeBrand) {
                        MonoValue("${com.navigator.app.ui.theme.themeFor(settings.brand).displayName} ›")
                    }
                    SettingsRow("Your name", showDivider = false, onClick = { dialog = SettingsDialog.NAME }) {
                        MonoValue(userName.ifBlank { "Set ›" })
                    }
                }
            }

            // ===== Navigation engine =====
            if (com.navigator.app.nav.providers.GoogleNavSdkController.isAvailable(context)) {
                item {
                    GroupCard("Navigation engine") {
                        SettingsRow(
                            "In-app maps (Google)", showDivider = true,
                            onClick = {
                                if (!googleNavOn) {
                                    googleNavOn = true; settings.googleNavEnabled = true; onEngineChanged(true)
                                }
                            },
                        ) { if (googleNavOn) OutlinedPill("ACTIVE") }
                        SettingsRow(
                            "Notification mirror", showDivider = true,
                            onClick = {
                                if (googleNavOn) {
                                    googleNavOn = false; settings.googleNavEnabled = false; onEngineChanged(false)
                                }
                            },
                        ) { if (!googleNavOn) OutlinedPill("ACTIVE") }
                        Text(
                            if (googleNavOn) {
                                "Enter a destination in the app and Google guides you on the dash. " +
                                    "Needs internet; shows an extra notification while navigating."
                            } else {
                                "The home mirrors turn-by-turn from another nav app (e.g. Google Maps) " +
                                    "to the dash. Start navigation in Google Maps. Works offline."
                            },
                            color = Ktm.Muted2,
                            fontFamily = Barlow,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 14.dp).padding(vertical = 10.dp),
                        )
                        SettingsRow("Saved places", showDivider = false, onClick = onOpenPlaces) {
                            Text("›", color = Ktm.Dim, fontSize = 18.sp)
                        }
                    }
                }
            }

            // ===== Ride recording =====
            item {
                GroupCard("Ride recording") {
                    SettingsRow("Record rides", showDivider = rideRecordingOn) {
                        KtmToggle(rideRecordingOn, { rideRecordingOn = it; settings.rideRecordingEnabled = it })
                    }
                    // Trivial/aborted trips shorter than BOTH cutoffs are dropped
                    // on finish. Tap to enter an exact value.
                    if (rideRecordingOn) {
                        SettingsRow(
                            "Min distance to keep", showDivider = true,
                            onClick = { dialog = SettingsDialog.RIDE_MIN_DISTANCE },
                        ) {
                            MonoValue(
                                if (rideMinDist >= 1000) "%.1f km ›".format(rideMinDist / 1000f)
                                else "$rideMinDist m ›",
                            )
                        }
                        SettingsRow(
                            "Min duration to keep", showDivider = true,
                            onClick = { dialog = SettingsDialog.RIDE_MIN_DURATION },
                        ) {
                            MonoValue(
                                if (rideMinDur >= 60) "${rideMinDur / 60} min ›" else "$rideMinDur s ›",
                            )
                        }
                        SettingsRow(
                            "Keep last N rides", showDivider = true,
                            onClick = { dialog = SettingsDialog.RIDE_HISTORY_LIMIT },
                        ) {
                            MonoValue("$rideHistoryLimit ›")
                        }
                    }
                    SettingsRow("Ride recordings", showDivider = false, onClick = onOpenRecordings) {
                        Text("›", color = Ktm.Dim, fontSize = 18.sp)
                    }
                }
            }

            // ===== Notifications =====
            item {
                GroupCard("Notifications") {
                    SettingsRow(
                        "Notification access", showDivider = true,
                        onClick = if (notificationAccessGranted) null else ({
                            context.startActivity(Intent(AndroidSettings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                        }),
                    ) {
                        MonoValue(
                            if (notificationAccessGranted) "Granted" else "Grant ›",
                            color = if (notificationAccessGranted) Ktm.Green else Ktm.Orange,
                        )
                    }
                    SettingsRow("Mirror-from apps", showDivider = true, onClick = { dialog = SettingsDialog.MIRROR_APPS }) {
                        MonoValue("${sourceApps.size} selected ›")
                    }
                    SettingsRow("Mirror to dash (quick-mute)", showDivider = true) {
                        KtmToggle(mirrorEnabled, { mirrorEnabled = it; settings.mirrorEnabled = it })
                    }
                    SettingsRow("Marquee scroll", showDivider = false) {
                        KtmToggle(marqueeEnabled, { marqueeEnabled = it; settings.marqueeEnabled = it })
                    }
                }
            }

            // ===== Navigation =====
            item {
                GroupCard("Navigation") {
                    SettingsRow("Auto-detect nav app", showDivider = true) {
                        KtmToggle(navAutoDetect, { on ->
                            navAutoDetect = on
                            if (on) { navOverride = null; settings.navAppOverride = null }
                            else { dialog = SettingsDialog.NAV_APP }
                        })
                    }
                    if (!navAutoDetect) {
                        SettingsRow("Nav app", showDivider = true, onClick = { dialog = SettingsDialog.NAV_APP }) {
                            MonoValue((navOverride?.let { appLabel(it) } ?: "Choose") + " ›")
                        }
                    }
                    SettingsRow("Call-audio device", showDivider = false, onClick = { dialog = SettingsDialog.CALL_AUDIO }) {
                        val label = bondedAudioDevices.firstOrNull { it.address == preferredAudioAddress }
                            ?.let { it.name ?: it.address } ?: "None"
                        MonoValue("$label ›")
                    }
                }
            }

            // ===== Riding =====
            item {
                GroupCard("Riding") {
                    SettingsRow("Turn approach beeps (stereo)", showDivider = true) {
                        KtmToggle(turnBeepEnabled, { turnBeepEnabled = it; settings.turnBeepEnabled = it })
                    }
                    // Tap to cycle presets - a slider is fiddly with gloves on.
                    SettingsRow(
                        "Beep volume", showDivider = true,
                        onClick = {
                            beepVolume = when {
                                beepVolume < 35 -> 35
                                beepVolume < 60 -> 60
                                beepVolume < 100 -> 100
                                else -> 20
                            }
                            settings.beepVolumePercent = beepVolume
                        },
                    ) {
                        MonoValue("$beepVolume% ›")
                    }
                    SettingsRow("Swap beep left/right", showDivider = true) {
                        var swapBeep by remember { mutableStateOf(settings.swapBeepChannels) }
                        KtmToggle(swapBeep, { swapBeep = it; settings.swapBeepChannels = it })
                    }
                    // Hear-it-before-you-ride previews: the real beep at the
                    // configured volume/side order, the real voice prompt, and
                    // the overspeed tone (R21 field request).
                    SettingsRow(
                        "Hear turn beeps (L-R-L)", showDivider = true,
                        onClick = {
                            com.navigator.app.audio.TurnBeeper.volumePercent = settings.beepVolumePercent
                            com.navigator.app.audio.TurnBeeper.swapChannels = settings.swapBeepChannels
                            com.navigator.app.audio.TurnBeeper.preview()
                        },
                    ) { MonoValue("Play ›") }
                    SettingsRow(
                        "Hear overspeed alert", showDivider = true,
                        onClick = {
                            runCatching {
                                // Match the live alert: alarm stream, max volume.
                                val tg = android.media.ToneGenerator(android.media.AudioManager.STREAM_ALARM, 100)
                                tg.startTone(android.media.ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 400)
                                android.os.Handler(android.os.Looper.getMainLooper())
                                    .postDelayed({ runCatching { tg.release() } }, 800)
                            }
                        },
                    ) { MonoValue("Play ›") }
                    SettingsRow("Overspeed alert", showDivider = true) {
                        KtmToggle(overspeedEnabled, { overspeedEnabled = it; settings.overspeedEnabled = it })
                    }
                    // "Allow all the time" is a SEPARATE grant from the normal
                    // location permission and can't be requested in the same
                    // dialog - without it, GPS features are dead whenever the
                    // service auto-started in the background (boot / bike
                    // reconnect) rather than from the app being open.
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        var bgLocationGranted by remember {
                            mutableStateOf(
                                androidx.core.content.ContextCompat.checkSelfPermission(
                                    context, android.Manifest.permission.ACCESS_BACKGROUND_LOCATION
                                ) == PackageManager.PERMISSION_GRANTED
                            )
                        }
                        val bgLocationLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                            androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
                        ) { granted ->
                            bgLocationGranted = granted
                            if (granted) {
                                BccuConnectionService.reevaluateLocationIfRunning()
                            } else {
                                // Android suppresses the request UI after repeated
                                // denials (or when foreground location is missing) -
                                // never leave this as a silent dead button.
                                android.widget.Toast.makeText(
                                    context,
                                    "Choose \"Allow all the time\" under Permissions → Location",
                                    android.widget.Toast.LENGTH_LONG,
                                ).show()
                                context.startActivity(
                                    Intent(
                                        AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        Uri.parse("package:${context.packageName}"),
                                    ),
                                )
                            }
                        }
                        val fineLocationLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                            androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
                        ) { result ->
                            // Background can only be requested once foreground is granted.
                            if (result[android.Manifest.permission.ACCESS_FINE_LOCATION] == true) {
                                BccuConnectionService.reevaluateLocationIfRunning()
                                bgLocationLauncher.launch(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                            }
                        }
                        SettingsRow(
                            "Background location (\"all the time\")", showDivider = true,
                            onClick = if (bgLocationGranted) null else ({
                                val fineGranted = androidx.core.content.ContextCompat.checkSelfPermission(
                                    context, android.Manifest.permission.ACCESS_FINE_LOCATION
                                ) == PackageManager.PERMISSION_GRANTED
                                if (fineGranted) {
                                    bgLocationLauncher.launch(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                                } else {
                                    fineLocationLauncher.launch(arrayOf(
                                        android.Manifest.permission.ACCESS_FINE_LOCATION,
                                        android.Manifest.permission.ACCESS_COARSE_LOCATION,
                                    ))
                                }
                            }),
                        ) {
                            MonoValue(
                                if (bgLocationGranted) "Granted" else "Grant ›",
                                color = if (bgLocationGranted) Ktm.Green else Ktm.Orange,
                            )
                        }
                    }
                    SettingsRow("Speed limit", showDivider = false, onClick = { dialog = SettingsDialog.OVERSPEED_LIMIT }) {
                        MonoValue("$overspeedLimit km/h ›")
                    }
                }
            }

            // ===== Diagnostics =====
            item {
                GroupCard("Diagnostics") {
                    SettingsRow("View & share logs", showDivider = true, onClick = onOpenLogs) {
                        Text("›", color = Ktm.Dim, fontSize = 18.sp)
                    }
                    SettingsRow("Symbol testing", showDivider = true, onClick = onOpenSymbolTest) {
                        Text("›", color = Ktm.Dim, fontSize = 18.sp)
                    }
                    SettingsRow("Turn icon calibration", showDivider = true, onClick = onOpenTurnCalibration) {
                        Text("›", color = Ktm.Dim, fontSize = 18.sp)
                    }
                    SettingsRow(
                        "Send test notification", showDivider = true,
                        onClick = {
                            BccuConnectionService.sendNotificationIfRunning(
                                "Hey ${userName.ifBlank { "there" }}",
                                BccuProtocol.NotificationIcon.NOTIFICATION_WAYPOINT,
                            )
                        },
                    ) { Text("SEND", color = Ktm.Orange, fontFamily = BarlowCondensed, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.sp) }
                    SettingsRow(
                        "Send test guidance", showDivider = true,
                        onClick = {
                            BccuConnectionService.sendTurnIconIfRunning(BccuProtocol.TurnIcon.GO_STRAIGHT)
                            BccuConnectionService.sendGuidanceIfRunning("50 m", "Test Road")
                        },
                    ) { Text("SEND", color = Ktm.Orange, fontFamily = BarlowCondensed, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.sp) }
                    SettingsRow(
                        "Battery optimization", showDivider = true,
                        onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                val powerManager = context.getSystemService(PowerManager::class.java)
                                if (!powerManager.isIgnoringBatteryOptimizations(context.packageName)) {
                                    context.startActivity(
                                        Intent(
                                            AndroidSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                            Uri.parse("package:${context.packageName}"),
                                        ),
                                    )
                                }
                            }
                        },
                    ) { Text("›", color = Ktm.Dim, fontSize = 18.sp) }
                    SettingsRow("Build", showDivider = false) {
                        MonoValue(com.navigator.app.BuildConfig.VERSION_NAME)
                    }
                }
            }
        }
    }

    // ===== Dialogs =====
    when (dialog) {
        SettingsDialog.NAME -> TextFieldDialog(
            title = "Your name", initial = userName, label = "Name",
            onDismiss = { dialog = SettingsDialog.NONE },
            onConfirm = { userName = it; settings.userName = it; dialog = SettingsDialog.NONE },
        )
        SettingsDialog.OVERSPEED_LIMIT -> TextFieldDialog(
            title = "Overspeed limit (km/h)", initial = overspeedLimit.toString(), label = "km/h",
            onDismiss = { dialog = SettingsDialog.NONE },
            onConfirm = { value ->
                value.trim().toIntOrNull()?.takeIf { it in 30..200 }?.let {
                    overspeedLimit = it
                    settings.overspeedLimitKmh = it
                }
                dialog = SettingsDialog.NONE
            },
        )
        SettingsDialog.RIDE_MIN_DISTANCE -> TextFieldDialog(
            title = "Min distance to keep (m)",
            initial = rideMinDist.toString(), label = "metres", numeric = true,
            onDismiss = { dialog = SettingsDialog.NONE },
            onConfirm = { value ->
                value.trim().toIntOrNull()
                    ?.takeIf { it in AppSettings.RIDE_MIN_DISTANCE_MIN_M..AppSettings.RIDE_MIN_DISTANCE_MAX_M }
                    ?.let { rideMinDist = it; settings.rideMinDistanceMeters = it }
                dialog = SettingsDialog.NONE
            },
        )
        SettingsDialog.RIDE_MIN_DURATION -> TextFieldDialog(
            title = "Min duration to keep (s)",
            initial = rideMinDur.toString(), label = "seconds", numeric = true,
            onDismiss = { dialog = SettingsDialog.NONE },
            onConfirm = { value ->
                value.trim().toIntOrNull()
                    ?.takeIf { it in AppSettings.RIDE_MIN_DURATION_MIN_S..AppSettings.RIDE_MIN_DURATION_MAX_S }
                    ?.let { rideMinDur = it; settings.rideMinDurationSeconds = it }
                dialog = SettingsDialog.NONE
            },
        )
        SettingsDialog.RIDE_HISTORY_LIMIT -> TextFieldDialog(
            title = "Keep last N rides",
            initial = rideHistoryLimit.toString(), label = "10 - 1000", numeric = true,
            onDismiss = { dialog = SettingsDialog.NONE },
            onConfirm = { value ->
                value.trim().toIntOrNull()
                    ?.takeIf { it in AppSettings.RIDE_HISTORY_LIMIT_MIN..AppSettings.RIDE_HISTORY_LIMIT_MAX }
                    ?.let {
                        rideHistoryLimit = it
                        settings.rideHistoryLimit = it
                        // Apply immediately so lowering the limit prunes now.
                        RideStore(context).pruneToLimit(it)
                    }
                dialog = SettingsDialog.NONE
            },
        )
        SettingsDialog.NAV_APP -> SingleChoiceDialog(
            title = "Navigation app",
            options = AppSettings.KNOWN_NAV_APPS.toList(),
            labelFor = { appLabel(it) },
            selected = navOverride,
            onDismiss = {
                // Cancelling with nothing chosen falls back to auto-detect.
                if (navOverride == null) navAutoDetect = true
                dialog = SettingsDialog.NONE
            },
            onSelect = {
                navOverride = it; settings.navAppOverride = it; navAutoDetect = false
                dialog = SettingsDialog.NONE
            },
        )
        SettingsDialog.CALL_AUDIO -> SingleChoiceDialog(
            title = "Call-audio device",
            options = bondedAudioDevices.map { it.address },
            labelFor = { addr -> bondedAudioDevices.firstOrNull { it.address == addr }?.let { it.name ?: it.address } ?: addr },
            selected = preferredAudioAddress,
            onDismiss = { dialog = SettingsDialog.NONE },
            onSelect = { preferredAudioAddress = it; settings.preferredCallAudioDeviceAddress = it; dialog = SettingsDialog.NONE },
        )
        SettingsDialog.MIRROR_APPS -> MultiChoiceDialog(
            title = "Mirror-from apps",
            options = installedApps.map { it.packageName },
            labelFor = { appLabel(it) },
            selected = sourceApps,
            onDismiss = { dialog = SettingsDialog.NONE },
            onToggle = { pkg, checked ->
                sourceApps = if (checked) sourceApps + pkg else sourceApps - pkg
                settings.notificationSourceApps = sourceApps
            },
        )
        SettingsDialog.THEME -> SingleChoiceDialog(
            title = "Theme",
            options = com.navigator.app.ui.theme.ThemeMode.entries.toList(),
            labelFor = {
                when (it) {
                    com.navigator.app.ui.theme.ThemeMode.SYSTEM -> "System default"
                    com.navigator.app.ui.theme.ThemeMode.LIGHT -> "Light"
                    com.navigator.app.ui.theme.ThemeMode.DARK -> "Dark"
                }
            },
            selected = themeMode,
            onDismiss = { dialog = SettingsDialog.NONE },
            onSelect = { onThemeModeChanged(it); dialog = SettingsDialog.NONE },
        )
        SettingsDialog.NAV_THEME -> SingleChoiceDialog(
            title = "Navigation theme",
            options = com.navigator.app.ui.theme.NavThemeMode.entries.toList(),
            labelFor = {
                when (it) {
                    com.navigator.app.ui.theme.NavThemeMode.APP -> "Follow app theme"
                    com.navigator.app.ui.theme.NavThemeMode.DAY_NIGHT -> "Day / Night (auto by time)"
                }
            },
            selected = navThemeMode,
            onDismiss = { dialog = SettingsDialog.NONE },
            onSelect = { onNavThemeModeChanged(it); dialog = SettingsDialog.NONE },
        )
        SettingsDialog.ACCENT -> AccentPickerDialog(
            selectedId = accentColorId,
            brandAccent = com.navigator.app.ui.theme.identityFor(settings.brand).accent,
            onDismiss = { dialog = SettingsDialog.NONE },
            onSelect = { id -> onAccentColorChanged(id); dialog = SettingsDialog.NONE },
            onCustom = { dialog = SettingsDialog.ACCENT_CUSTOM },
        )
        SettingsDialog.ACCENT_CUSTOM -> CustomColorPickerDialog(
            initial = com.navigator.app.ui.theme.accentFor(accentColorId)?.color ?: Ktm.Orange,
            onCancel = { dialog = SettingsDialog.ACCENT },
            onConfirm = { hex -> onAccentColorChanged(hex); dialog = SettingsDialog.NONE },
        )
        SettingsDialog.NONE -> {}
    }
}

/**
 * Accent picker showing each option as a colour swatch. "Match bike" (null id)
 * follows the brand's own accent; every other row overrides it app-wide.
 */
@Composable
private fun AccentPickerDialog(
    selectedId: String?,
    brandAccent: androidx.compose.ui.graphics.Color,
    onDismiss: () -> Unit,
    onSelect: (String?) -> Unit,
    onCustom: () -> Unit,
) {
    // "Custom" = a stored id that isn't a known preset (a hex colour).
    val isCustom = selectedId != null &&
        com.navigator.app.ui.theme.AccentPalettes.none { it.id == selectedId }
    val customColor = if (isCustom) com.navigator.app.ui.theme.accentFor(selectedId)?.color else null
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Ktm.Surface,
        titleContentColor = Ktm.White,
        title = { Text("Accent colour", fontFamily = BarlowCondensed, fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                AccentSwatchRow(
                    label = "Match bike",
                    color = brandAccent,
                    selected = selectedId == null,
                    onClick = { onSelect(null) },
                )
                com.navigator.app.ui.theme.AccentPalettes.forEach { option ->
                    AccentSwatchRow(
                        label = option.displayName,
                        color = option.color,
                        selected = selectedId == option.id,
                        onClick = { onSelect(option.id) },
                    )
                }
                // Custom colour — opens the colour picker. Its swatch shows the
                // chosen custom colour when active, otherwise a rainbow hint.
                CustomAccentRow(
                    label = if (isCustom) "Custom (${selectedId})" else "Custom…",
                    swatch = customColor,
                    selected = isCustom,
                    onClick = onCustom,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("DONE", color = Ktm.Orange) } },
    )
}

/** The "Custom…" entry in the accent list: a colour-wheel hint (or the current
 *  custom colour) that opens the full picker. */
@Composable
private fun CustomAccentRow(
    label: String,
    swatch: androidx.compose.ui.graphics.Color?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val wheel = androidx.compose.ui.graphics.Brush.sweepGradient(
        listOf(
            androidx.compose.ui.graphics.Color.Red,
            androidx.compose.ui.graphics.Color.Yellow,
            androidx.compose.ui.graphics.Color.Green,
            androidx.compose.ui.graphics.Color.Cyan,
            androidx.compose.ui.graphics.Color.Blue,
            androidx.compose.ui.graphics.Color.Magenta,
            androidx.compose.ui.graphics.Color.Red,
        ),
    )
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(Ktm.RadiusRow))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(26.dp).clip(CircleShape)
                .then(
                    if (swatch != null) Modifier.background(swatch) else Modifier.background(wheel),
                )
                .border(if (selected) 2.dp else 1.dp, if (selected) Ktm.White else Ktm.BorderSoft, CircleShape),
        )
        androidx.compose.foundation.layout.Spacer(Modifier.size(14.dp))
        Text(label, color = Ktm.TextPrimary, fontFamily = Barlow, modifier = Modifier.weight(1f))
        Text("›", color = Ktm.Dim, fontSize = 18.sp)
    }
}

/** HSV (hue 0..360, sat/value 0..1) -> opaque Compose [Color], via the platform
 *  converter so it's version-stable. */
private fun hsvColor(hue: Float, sat: Float, value: Float): androidx.compose.ui.graphics.Color =
    androidx.compose.ui.graphics.Color(
        android.graphics.Color.HSVToColor(floatArrayOf(hue.coerceIn(0f, 360f), sat.coerceIn(0f, 1f), value.coerceIn(0f, 1f))),
    )

/**
 * Full custom-colour picker: a saturation/value panel over a hue slider, with a
 * live preview + hex readout. Lets the rider pick ANY accent, not just a preset.
 */
@Composable
private fun CustomColorPickerDialog(
    initial: androidx.compose.ui.graphics.Color,
    onCancel: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val start = remember(initial) {
        val out = FloatArray(3)
        android.graphics.Color.colorToHSV(initial.toArgb(), out)
        out
    }
    var hue by remember { mutableStateOf(start[0]) }
    var sat by remember { mutableStateOf(start[1]) }
    var value by remember { mutableStateOf(start[2].coerceAtLeast(0.06f)) }
    val color = hsvColor(hue, sat, value)
    val hex = com.navigator.app.ui.theme.hexOf(color)

    AlertDialog(
        onDismissRequest = onCancel,
        containerColor = Ktm.Surface,
        titleContentColor = Ktm.White,
        title = { Text("Custom colour", fontFamily = BarlowCondensed, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                // Saturation (x) × value (y) panel for the current hue.
                Box(
                    modifier = Modifier.fillMaxWidth().height(170.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .pointerInput(Unit) {
                            detectTapGestures { o ->
                                sat = (o.x / size.width).coerceIn(0f, 1f)
                                value = (1f - o.y / size.height).coerceIn(0f, 1f)
                            }
                        }
                        .pointerInput(Unit) {
                            detectDragGestures { change, _ ->
                                sat = (change.position.x / size.width).coerceIn(0f, 1f)
                                value = (1f - change.position.y / size.height).coerceIn(0f, 1f)
                            }
                        },
                ) {
                    androidx.compose.foundation.Canvas(Modifier.matchParentSize()) {
                        drawRect(
                            androidx.compose.ui.graphics.Brush.horizontalGradient(
                                listOf(androidx.compose.ui.graphics.Color.White, hsvColor(hue, 1f, 1f)),
                            ),
                        )
                        drawRect(
                            androidx.compose.ui.graphics.Brush.verticalGradient(
                                listOf(androidx.compose.ui.graphics.Color.Transparent, androidx.compose.ui.graphics.Color.Black),
                            ),
                        )
                        val cx = sat * size.width
                        val cy = (1f - value) * size.height
                        drawCircle(
                            androidx.compose.ui.graphics.Color.White,
                            radius = 7.dp.toPx(),
                            center = androidx.compose.ui.geometry.Offset(cx, cy),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()),
                        )
                    }
                }
                androidx.compose.foundation.layout.Spacer(Modifier.size(14.dp))
                // Hue slider.
                Box(
                    modifier = Modifier.fillMaxWidth().height(26.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .pointerInput(Unit) {
                            detectTapGestures { o ->
                                hue = (o.x / size.width * 360f).coerceIn(0f, 360f)
                            }
                        }
                        .pointerInput(Unit) {
                            detectDragGestures { change, _ ->
                                hue = (change.position.x / size.width * 360f).coerceIn(0f, 360f)
                            }
                        },
                ) {
                    androidx.compose.foundation.Canvas(Modifier.matchParentSize()) {
                        val spectrum = (0..6).map { hsvColor(it * 60f, 1f, 1f) }
                        drawRect(androidx.compose.ui.graphics.Brush.horizontalGradient(spectrum))
                        val x = (hue / 360f) * size.width
                        drawRect(
                            color = androidx.compose.ui.graphics.Color.White,
                            topLeft = androidx.compose.ui.geometry.Offset(x - 2.dp.toPx(), 0f),
                            size = androidx.compose.ui.geometry.Size(4.dp.toPx(), size.height),
                        )
                    }
                }
                androidx.compose.foundation.layout.Spacer(Modifier.size(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(30.dp).clip(CircleShape).background(color)
                            .border(1.dp, Ktm.BorderSoft, CircleShape),
                    )
                    androidx.compose.foundation.layout.Spacer(Modifier.size(12.dp))
                    Text(hex, color = Ktm.TextPrimary, fontFamily = com.navigator.app.ui.theme.JetBrainsMono, fontSize = 15.sp)
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(hex) }) { Text("SELECT", color = Ktm.Orange) } },
        dismissButton = { TextButton(onClick = onCancel) { Text("BACK", color = Ktm.Dim) } },
    )
}

@Composable
private fun AccentSwatchRow(
    label: String,
    color: androidx.compose.ui.graphics.Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(Ktm.RadiusRow))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(26.dp).clip(CircleShape).background(color)
                .border(if (selected) 2.dp else 1.dp, if (selected) Ktm.White else Ktm.BorderSoft, CircleShape),
        )
        androidx.compose.foundation.layout.Spacer(Modifier.size(14.dp))
        Text(
            label, color = Ktm.TextPrimary, fontFamily = Barlow,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Icon(
                Icons.Filled.Check, "Selected",
                tint = Ktm.Orange, modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun OutlinedPill(text: String) {
    Text(
        text, color = Ktm.Orange, fontFamily = BarlowCondensed, fontWeight = FontWeight.Bold,
        fontSize = 12.sp, letterSpacing = 1.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(Ktm.RadiusButton))
            .border(1.dp, Ktm.Orange, RoundedCornerShape(Ktm.RadiusButton))
            .padding(horizontal = 12.dp, vertical = 5.dp),
    )
}

@Composable
private fun TextFieldDialog(
    title: String, initial: String, label: String,
    numeric: Boolean = false,
    onDismiss: () -> Unit, onConfirm: (String) -> Unit,
) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Ktm.Surface,
        titleContentColor = Ktm.White,
        textContentColor = Ktm.TextPrimary,
        title = { Text(title, fontFamily = BarlowCondensed, fontWeight = FontWeight.Bold) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { new -> value = if (numeric) new.filter { it.isDigit() } else new },
                label = { Text(label) }, singleLine = true,
                keyboardOptions = if (numeric) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(value) }) { Text("SAVE", color = Ktm.Orange) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("CANCEL", color = Ktm.Dim) } },
    )
}

@Composable
private fun <T> SingleChoiceDialog(
    title: String, options: List<T>, labelFor: (T) -> String, selected: T?,
    onDismiss: () -> Unit, onSelect: (T) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Ktm.Surface,
        titleContentColor = Ktm.White,
        title = { Text(title, fontFamily = BarlowCondensed, fontWeight = FontWeight.Bold) },
        text = {
            if (options.isEmpty()) {
                Text("Nothing available.", color = Ktm.Dim, fontFamily = Barlow)
            } else {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    options.forEach { option ->
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .selectable(selected == option) { onSelect(option) }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = selected == option, onClick = { onSelect(option) },
                                colors = RadioButtonDefaults.colors(selectedColor = Ktm.Orange, unselectedColor = Ktm.Dim),
                            )
                            Text(labelFor(option), color = Ktm.TextPrimary, fontFamily = Barlow)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("DONE", color = Ktm.Orange) } },
    )
}

@Composable
private fun <T> MultiChoiceDialog(
    title: String, options: List<T>, labelFor: (T) -> String, selected: Set<T>,
    onDismiss: () -> Unit, onToggle: (T, Boolean) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Ktm.Surface,
        titleContentColor = Ktm.White,
        title = { Text(title, fontFamily = BarlowCondensed, fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                options.forEach { option ->
                    val checked = option in selected
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clickable { onToggle(option, !checked) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = checked, onCheckedChange = { onToggle(option, it) },
                            colors = CheckboxDefaults.colors(checkedColor = Ktm.Orange, uncheckedColor = Ktm.Dim),
                        )
                        Text(labelFor(option), color = Ktm.TextPrimary, fontFamily = Barlow)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("DONE", color = Ktm.Orange) } },
    )
}
