package com.navigator.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.navigator.app.ble.BccuConnectionService
import com.navigator.app.ui.theme.Barlow
import com.navigator.app.ui.theme.BarlowCondensed
import com.navigator.app.ui.theme.JetBrainsMono
import com.navigator.app.ui.theme.Ktm
import com.navigator.app.ui.theme.OpenDashIcons

private data class Tile(
    val label: String,
    val sub: String,
    val icon: ImageVector,
    val tint: androidx.compose.ui.graphics.Color,
    val accent: Boolean,
)

/**
 * Grid / Home (screen 02). A persistent connection banner, the READY TO RIDE
 * headline, and a 2x2 tile grid. The two primary tiles (Notification,
 * Direction) carry the orange corner accent; Home and Exit are utility tiles.
 * The currently focused tile (driven by the handlebar remote via
 * [ControllerStateMachine.gridSelection]) is highlighted so RCM navigation is
 * obvious; tiles are also tappable and feed the same pipeline.
 */
@Composable
fun GridMenuScreen(
    gridSelection: Int,
    onSelectGrid: (Int) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
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

    val notifCount by com.navigator.app.notifications.NotificationRepository.entries.collectAsState()
    val navText by com.navigator.app.notifications.NotificationRepository.currentNavText.collectAsState()

    val tiles = listOf(
        Tile("NOTIFICATION", if (notifCount.isEmpty()) "None" else "${notifCount.size} new",
            OpenDashIcons.Bell, Ktm.Orange, accent = true),
        Tile("DIRECTION", if (navText != null) "Nav active" else "No route",
            OpenDashIcons.Navigation, Ktm.Orange, accent = true),
        Tile("HOME", "Minimize app", OpenDashIcons.House, Ktm.TextSecondary, accent = false),
        Tile("EXIT", "Disconnect", OpenDashIcons.Power, Ktm.Danger, accent = false),
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Ktm.Screen)
            .systemBarsPadding()
            .padding(horizontal = 18.dp)
            .padding(top = 6.dp, bottom = 12.dp),
    ) {
        // Header: wordmark + settings entry (touch-only, not on the RCM path).
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("NAVIGATOR", color = Ktm.White, fontFamily = BarlowCondensed,
                    fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic, fontSize = 20.sp)
                Spacer(Modifier.size(8.dp))
                // Brand pill — shows which bike skin is active (KTM / Husqvarna).
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Ktm.Orange)
                        .padding(horizontal = 9.dp, vertical = 3.dp),
                ) {
                    Text(
                        com.navigator.app.ui.theme.themeFor(
                            com.navigator.app.settings.AppSettings(context).brand
                        ).displayName.uppercase(),
                        color = Ktm.OnAccent, fontFamily = BarlowCondensed, fontWeight = FontWeight.Bold,
                        fontSize = 12.sp, letterSpacing = 0.8.sp,
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    OpenDashIcons.Settings, contentDescription = "Settings", tint = Ktm.Muted,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(onClick = onOpenSettings)
                        .padding(6.dp)
                        .size(22.dp),
                )
            }
        }

        ConnectionBanner()

        QuickToggles()

        Text(
            buildReadyHeadline(),
            modifier = Modifier.padding(top = 14.dp, bottom = 6.dp),
            fontFamily = BarlowCondensed,
            fontWeight = FontWeight.Bold,
            fontStyle = FontStyle.Italic,
            fontSize = 34.sp,
            lineHeight = 31.sp,
            letterSpacing = (-0.4).sp,
        )

        if (!notificationAccessGranted) {
            NotificationAccessWarning(onOpenSettings)
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(tiles.size, key = { it }) { index ->
                GridTile(
                    tile = tiles[index],
                    focused = index == gridSelection,
                    onClick = { onSelectGrid(index) },
                )
            }
        }
    }
}

private fun buildReadyHeadline() = androidx.compose.ui.text.buildAnnotatedString {
    withStyleColor(Ktm.White) { append("READY TO\n") }
    withStyleColor(Ktm.Orange) { append("RIDE.") }
}

private inline fun androidx.compose.ui.text.AnnotatedString.Builder.withStyleColor(
    color: androidx.compose.ui.graphics.Color,
    block: androidx.compose.ui.text.AnnotatedString.Builder.() -> Unit,
) {
    pushStyle(androidx.compose.ui.text.SpanStyle(color = color))
    block()
    pop()
}

/**
 * One-tap ride tools right on the home screen (R20 field request): the
 * settings that get flipped at the kerb - approach beeps, overspeed alert,
 * power saver - without digging into Settings. Touch-only, deliberately not on
 * the handlebar-remote path (the 2x2 grid below owns that).
 */
@Composable
private fun QuickToggles() {
    val context = LocalContext.current
    val settings = remember { com.navigator.app.settings.AppSettings(context) }
    var beeps by remember { mutableStateOf(settings.turnBeepEnabled) }
    var overspeed by remember { mutableStateOf(settings.overspeedEnabled) }
    var powerSave by remember { mutableStateOf(settings.powerSaveEnabled) }

    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        QuickChip("BEEPS", beeps, Modifier.weight(1f)) {
            beeps = !beeps; settings.turnBeepEnabled = beeps
            if (!beeps) com.navigator.app.audio.TurnBeeper.reset()
        }
        QuickChip("SPEED", overspeed, Modifier.weight(1f)) {
            overspeed = !overspeed; settings.overspeedEnabled = overspeed
        }
        QuickChip("PWR SAVE", powerSave, Modifier.weight(1f)) {
            powerSave = !powerSave; settings.powerSaveEnabled = powerSave
            BccuConnectionService.reevaluateEngineDetectIfRunning()
        }
    }
}

@Composable
private fun QuickChip(label: String, on: Boolean, modifier: Modifier = Modifier, onToggle: () -> Unit) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(9.dp))
            .background(if (on) Ktm.SurfaceAlt else Ktm.Surface)
            .border(1.dp, if (on) Ktm.Orange else Ktm.Border, RoundedCornerShape(9.dp))
            .clickable(onClick = onToggle)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(if (on) Ktm.Green else Ktm.Dim),
        )
        Text(
            label,
            color = if (on) Ktm.White else Ktm.Dim,
            fontFamily = BarlowCondensed,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            letterSpacing = 0.8.sp,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun ConnectionBanner() {
    val state by BccuConnectionService.connectionState.collectAsState()
    val deviceName by BccuConnectionService.deviceName.collectAsState()
    val rssi by BccuConnectionService.signalRssi.collectAsState()

    val connected = state == BccuConnectionService.ConnectionState.AUTHENTICATED
    val connecting = state == BccuConnectionService.ConnectionState.CONNECTING
    val dotColor = when {
        connected -> Ktm.Green
        connecting -> Ktm.Orange
        else -> Ktm.Danger
    }
    val title = when {
        connected -> "CONNECTED"
        connecting -> "CONNECTING…"
        else -> "DISCONNECTED"
    }
    val sub = when {
        connected -> "${deviceName ?: "bike"} · session secure"
        connecting -> "${deviceName ?: "bike"} · handshaking"
        else -> "Not linked to the dash"
    }
    val borderColor = if (connected) Ktm.ConnBorder else Ktm.Border

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(11.dp))
            .background(Ktm.ConnBanner)
            .border(1.dp, borderColor, RoundedCornerShape(11.dp))
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(11.dp).clip(CircleShape).background(dotColor))
        Column(modifier = Modifier.weight(1f).padding(start = 11.dp)) {
            Text(title, color = Ktm.White, fontFamily = BarlowCondensed,
                fontWeight = FontWeight.Bold, fontSize = 15.sp, letterSpacing = 0.6.sp, lineHeight = 15.sp)
            Text(sub, color = Ktm.ConnSub, fontFamily = Barlow, fontSize = 12.sp,
                modifier = Modifier.padding(top = 2.dp))
        }
        if (connected && rssi != null) {
            Text("${rssi}dBm", color = Ktm.Green, fontFamily = JetBrainsMono, fontSize = 11.sp)
        }
    }
}

@Composable
private fun NotificationAccessWarning(onOpenSettings: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(Ktm.SurfaceAlt)
            .border(1.dp, Ktm.Danger, RoundedCornerShape(11.dp))
            .clickable(onClick = onOpenSettings)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Notification access off — tap to fix in Settings",
            color = Ktm.TextSecondary, fontFamily = Barlow, fontSize = 13.sp,
            modifier = Modifier.weight(1f),
        )
        Text("›", color = Ktm.Danger, fontSize = 18.sp)
    }
}

@Composable
private fun GridTile(tile: Tile, focused: Boolean, onClick: () -> Unit) {
    val borderColor = if (focused) Ktm.Orange else Ktm.Border
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(Ktm.RadiusCard))
            .background(if (focused) Ktm.SurfaceAlt else Ktm.Surface)
            .border(if (focused) 2.dp else 1.dp, borderColor, RoundedCornerShape(Ktm.RadiusCard))
            .clickable(onClick = onClick)
            .drawWithContent {
                drawContent()
                if (tile.accent) {
                    val s = 34.dp.toPx()
                    val path = Path().apply {
                        moveTo(size.width, 0f)
                        lineTo(size.width - s, 0f)
                        lineTo(size.width, s)
                        close()
                    }
                    drawPath(path, Ktm.Orange)
                }
            }
            .padding(horizontal = 15.dp, vertical = 16.dp),
    ) {
        Icon(tile.icon, contentDescription = tile.label, tint = tile.tint,
            modifier = Modifier.align(Alignment.TopStart).size(30.dp))
        Column(modifier = Modifier.align(Alignment.BottomStart)) {
            Text(tile.label, color = Ktm.White, fontFamily = BarlowCondensed,
                fontWeight = FontWeight.Bold, fontSize = 19.sp, letterSpacing = 0.5.sp)
            Text(tile.sub, color = Ktm.Dim, fontFamily = Barlow, fontSize = 12.sp)
        }
    }
}
