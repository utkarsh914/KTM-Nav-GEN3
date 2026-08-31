package com.navigator.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings as AndroidSettings
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.navigator.app.nav.providers.NotificationNavProvider
import com.navigator.app.notifications.NotificationRepository
import com.navigator.app.ui.OpenDashPermissions
import com.navigator.app.ui.components.ConnectPill
import com.navigator.app.ui.components.Eyebrow
import com.navigator.app.ui.components.IconPill
import com.navigator.app.ui.components.TurnIconRef
import com.navigator.app.ui.theme.Barlow
import com.navigator.app.ui.theme.BarlowCondensed
import com.navigator.app.ui.theme.JetBrainsMono
import com.navigator.app.ui.theme.Ktm
import com.navigator.app.ui.theme.OpenDashIcons

/**
 * Home screen for the notification-mirror engine. The rider navigates in Google
 * Maps (or another nav app); [com.navigator.app.notifications.AppNotificationListener]
 * mirrors the turn-by-turn to the KTM dash. This screen shows the same guidance
 * the dash is receiving, a shortcut into Google Maps, plus connection + settings.
 * SDK-first stays the default; this is only the home when the engine is set to
 * "Notification mirror" (Settings), keeping the two engines mutually exclusive.
 */
@Composable
fun MirrorHomeScreen(
    onOpenSettings: () -> Unit,
    onOpenConnect: () -> Unit,
    onExit: () -> Unit = {},
) {
    val context = LocalContext.current
    val guidance by NotificationRepository.navGuidance.collectAsState()
    val navText by NotificationRepository.currentNavText.collectAsState()
    val navPackage by NotificationRepository.currentNavPackage.collectAsState()
    val paused by NotificationNavProvider.paused.collectAsState()

    val hasNav = navPackage != null && (guidance != null || navText != null)

    // Notification access is what lets us read Google Maps' turn notifications;
    // without it the mirror never sees any guidance. Re-check on resume so the
    // banner clears right after the user grants it in system settings.
    var notifAccess by remember { mutableStateOf(OpenDashPermissions.notificationAccessGranted(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) {
                notifAccess = OpenDashPermissions.notificationAccessGranted(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    // Back at the mirror home confirms full close, matching the map home.
    var showExitConfirm by remember { mutableStateOf(false) }
    BackHandler { showExitConfirm = true }

    Box(modifier = Modifier.fillMaxSize().background(Ktm.Screen)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 22.dp)
                .padding(top = 10.dp, bottom = 22.dp),
        ) {
            // Top bar: bike connection (left) + settings (right) — the same
            // shared controls the map home uses, so the two engines match. The
            // mirror-status label sits just below.
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                ConnectPill(onClick = onOpenConnect)
                Spacer(Modifier.weight(1f))
                IconPill(OpenDashIcons.Settings, "Settings", onClick = onOpenSettings)
            }
            Spacer(Modifier.height(16.dp))
            Eyebrow(
                if (paused) "Notification mirror · paused" else "Notification mirror · to dash",
                fontSize = 12, letterSpacing = 2.0, color = if (paused) Ktm.Dim else Ktm.Orange,
            )

            Spacer(Modifier.height(20.dp))

            if (!notifAccess) {
                NotifAccessBanner {
                    runCatching {
                        context.startActivity(Intent(AndroidSettings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    }
                }
                Spacer(Modifier.height(14.dp))
            }

            if (!hasNav) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        if (notifAccess) {
                            "No active navigation\n\nStart a trip in Google Maps —\nthe turns appear here and on your dash."
                        } else {
                            "Enable notification access above,\nthen start a trip in Google Maps."
                        },
                        color = Ktm.Muted2, fontFamily = BarlowCondensed, fontWeight = FontWeight.SemiBold,
                        fontSize = 22.sp, textAlign = TextAlign.Center, lineHeight = 30.sp,
                    )
                }
            } else {
                MirrorTurnCard(guidance, navText)
                Spacer(Modifier.height(14.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MirrorStatTile("ETA", guidance?.eta ?: "—", Modifier.weight(1f))
                    MirrorStatTile("Remaining", guidance?.remaining ?: "—", Modifier.weight(1f))
                }
                Spacer(Modifier.weight(1f))
            }

            // Pause/resume only makes sense while a trip is actually mirroring.
            if (hasNav) {
                PauseMirrorButton(paused = paused) { NotificationNavProvider.setPaused(!paused) }
                Spacer(Modifier.height(10.dp))
            }
            OpenGoogleMapsButton {
                val intent = context.packageManager.getLaunchIntentForPackage("com.google.android.apps.maps")
                    ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://maps.google.com"))
                runCatching { context.startActivity(intent) }
            }
        }

        if (showExitConfirm) {
            AlertDialog(
                onDismissRequest = { showExitConfirm = false },
                containerColor = Ktm.Surface,
                titleContentColor = Ktm.White,
                textContentColor = Ktm.TextSecondary,
                title = { Text("Close KTM Navigator?", fontFamily = BarlowCondensed, fontWeight = FontWeight.Bold) },
                text = { Text("This stops navigation to the dash and exits the app.", fontFamily = Barlow) },
                confirmButton = {
                    TextButton(onClick = { showExitConfirm = false; onExit() }) {
                        Text("CLOSE", color = Ktm.Danger)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showExitConfirm = false }) { Text("CANCEL", color = Ktm.TextPrimary) }
                },
            )
        }
    }
}

@Composable
private fun NotifAccessBanner(onGrant: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Ktm.RadiusRow))
            .background(Ktm.SurfaceAlt)
            .border(1.dp, Ktm.Border, RoundedCornerShape(Ktm.RadiusRow))
            .clickable(onClick = onGrant)
            .padding(16.dp),
    ) {
        Text("Notification access needed", color = Ktm.Orange, fontFamily = BarlowCondensed,
            fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Spacer(Modifier.height(4.dp))
        Text(
            "Mirror mode reads Google Maps' turn notifications. Tap to grant access.",
            color = Ktm.Muted2, fontFamily = Barlow, fontSize = 13.sp,
        )
    }
}

// A leading token like "400 m" / "1.2 km" / "550 ft" (the distance-to-turn),
// as opposed to a text instruction like "Head toward …".
private val DISTANCE_RE =
    Regex("""^\s*[\d.,]+\s*(m|km|ft|mi|meters?|kilomet[re]*s?|feet|miles?)\b""", RegexOption.IGNORE_CASE)

private fun looksLikeDistance(s: String?): Boolean = s != null && DISTANCE_RE.containsMatchIn(s)

@Composable
private fun MirrorTurnCard(guidance: NotificationRepository.NavGuidance?, navText: String?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Ktm.SurfaceAlt)
            .border(1.dp, Ktm.Border, RoundedCornerShape(18.dp))
            .padding(horizontal = 22.dp, vertical = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val turnIcon = guidance?.turnIcon
        if (turnIcon != null) {
            TurnIconRef(icon = turnIcon, size = 80.dp, color = Ktm.Orange)
        } else {
            Icon(OpenDashIcons.TurnArrow, contentDescription = null, tint = Ktm.Orange, modifier = Modifier.size(80.dp))
        }

        Spacer(Modifier.height(14.dp))

        val distance = guidance?.distance?.takeIf { it.isNotBlank() }
        val road = guidance?.road?.takeIf { it.isNotBlank() }
        val maneuver = guidance?.maneuver?.takeIf { it.isNotBlank() }

        if (looksLikeDistance(distance)) {
            // Turn is a measured distance away: show it big, then the maneuver
            // label and the road it applies to.
            Text(
                distance!!, fontFamily = BarlowCondensed, fontWeight = FontWeight.Bold,
                fontSize = 56.sp, lineHeight = 60.sp, color = Ktm.White, textAlign = TextAlign.Center,
            )
            maneuver?.let {
                Text(
                    it, color = Ktm.TextSecondary, fontFamily = BarlowCondensed, fontWeight = FontWeight.SemiBold,
                    fontSize = 20.sp, lineHeight = 24.sp, letterSpacing = 0.4.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            road?.let {
                Text(
                    it, color = Ktm.Orange, fontFamily = BarlowCondensed, fontWeight = FontWeight.Bold,
                    fontStyle = FontStyle.Italic, fontSize = 24.sp, lineHeight = 28.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        } else {
            // A text instruction (e.g. "Head toward …"): render at a readable
            // size that wraps cleanly instead of a giant single "number".
            val instruction = distance ?: navText
            instruction?.let {
                Text(
                    it, color = Ktm.White, fontFamily = BarlowCondensed, fontWeight = FontWeight.Bold,
                    fontSize = 30.sp, lineHeight = 36.sp, textAlign = TextAlign.Center,
                )
            }
            road?.let {
                if (!it.equals(instruction, ignoreCase = true)) {
                    Text(
                        it, color = Ktm.Orange, fontFamily = BarlowCondensed, fontWeight = FontWeight.Bold,
                        fontStyle = FontStyle.Italic, fontSize = 24.sp, lineHeight = 28.sp, textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun MirrorStatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(Ktm.RadiusRow))
            .background(Ktm.Surface)
            .border(1.dp, Ktm.Border, RoundedCornerShape(Ktm.RadiusRow))
            .padding(horizontal = 15.dp, vertical = 13.dp),
    ) {
        Eyebrow(label, color = Ktm.Dim, fontSize = 11, letterSpacing = 1.5)
        Text(value, color = Ktm.White, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold,
            fontSize = 22.sp, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun PauseMirrorButton(paused: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Ktm.RadiusButton))
            .background(Ktm.Surface)
            .border(1.dp, if (paused) Ktm.Orange else Ktm.BorderSoft, RoundedCornerShape(Ktm.RadiusButton))
            .clickable(onClick = onClick)
            .padding(15.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (paused) "RESUME SENDING TO DASH" else "PAUSE SENDING TO DASH",
            color = if (paused) Ktm.Orange else Ktm.White,
            fontFamily = BarlowCondensed, fontWeight = FontWeight.Bold, fontSize = 16.sp, letterSpacing = 1.5.sp,
        )
    }
}

@Composable
private fun OpenGoogleMapsButton(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Ktm.RadiusButton))
            .background(Ktm.Orange)
            .clickable(onClick = onClick)
            .padding(15.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(OpenDashIcons.Navigation, contentDescription = null, tint = Ktm.OnAccent, modifier = Modifier.size(19.dp))
        Spacer(Modifier.size(10.dp))
        Text("OPEN GOOGLE MAPS", color = Ktm.OnAccent, fontFamily = BarlowCondensed,
            fontWeight = FontWeight.Bold, fontSize = 16.sp, letterSpacing = 1.5.sp)
    }
}
