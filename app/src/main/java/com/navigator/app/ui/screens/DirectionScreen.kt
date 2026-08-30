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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.navigator.app.notifications.NotificationRepository
import com.navigator.app.ui.theme.Barlow
import com.navigator.app.ui.theme.BarlowCondensed
import com.navigator.app.ui.theme.JetBrainsMono
import com.navigator.app.ui.theme.Ktm
import com.navigator.app.ui.theme.OpenDashIcons

/**
 * Direction / turn-by-turn (screen 03). Mirrors what the phone's nav app is
 * showing (and what OpenDash sends to the dash's center display): turn arrow,
 * distance, road, plus ETA / remaining. Includes an Open Maps action. The
 * data comes from the detected nav app's own notification, so fields may be
 * partially present.
 */
@Composable
fun DirectionScreen(
    onOpenMaps: () -> Unit,
    onSetDestination: () -> Unit = {},
    showGoogleNav: Boolean = false,
) {
    val guidance by NotificationRepository.navGuidance.collectAsState()
    val navText by NotificationRepository.currentNavText.collectAsState()
    val navPackage by NotificationRepository.currentNavPackage.collectAsState()

    val hasNav = navPackage != null && (guidance != null || navText != null)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Ktm.Screen)
            .systemBarsPadding()
            .padding(horizontal = 22.dp)
            .padding(top = 10.dp, bottom = 22.dp),
    ) {
        com.navigator.app.ui.components.Eyebrow(
            "Turn-by-turn · mirrored to dash",
            fontSize = 12, letterSpacing = 2.0,
            color = Ktm.Orange,
        )

        Spacer(Modifier.height(20.dp))

        if (!hasNav) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    "No active navigation detected",
                    color = Ktm.Muted2, fontFamily = BarlowCondensed, fontWeight = FontWeight.SemiBold,
                    fontSize = 22.sp, textAlign = TextAlign.Center,
                )
            }
        } else {
            TurnCard(guidance, navText)
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StatTile("ETA", guidance?.eta ?: "—", Modifier.weight(1f))
                StatTile("Remaining", guidance?.remaining ?: "—", Modifier.weight(1f))
            }
            Spacer(Modifier.weight(1f))
        }

        if (showGoogleNav) {
            SetDestinationButton(onClick = onSetDestination)
            Spacer(Modifier.height(10.dp))
        }
        OpenMapsButton(enabled = navPackage != null, onClick = onOpenMaps)
    }
}

@Composable
private fun SetDestinationButton(onClick: () -> Unit) {
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
        Icon(OpenDashIcons.Navigation, contentDescription = null,
            tint = Ktm.OnAccent, modifier = Modifier.size(19.dp))
        Spacer(Modifier.size(10.dp))
        Text("NAVIGATE WITH GOOGLE", color = Ktm.OnAccent,
            fontFamily = BarlowCondensed, fontWeight = FontWeight.Bold, fontSize = 16.sp, letterSpacing = 1.5.sp)
    }
}

@Composable
private fun TurnCard(guidance: NotificationRepository.NavGuidance?, navText: String?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Ktm.SurfaceAlt)
            .border(1.dp, Ktm.Border, RoundedCornerShape(18.dp))
            .padding(horizontal = 22.dp, vertical = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Real classified maneuver icon (same one sent to the dash) when we have
        // it; otherwise the generic arrow. Uses the official-icon renderer.
        val turnIcon = guidance?.turnIcon
        if (turnIcon != null) {
            com.navigator.app.ui.components.TurnIconRef(icon = turnIcon, size = 88.dp, color = Ktm.Orange)
        } else {
            Icon(OpenDashIcons.TurnArrow, contentDescription = null, tint = Ktm.Orange,
                modifier = Modifier.size(88.dp))
        }

        val distance = guidance?.distance
        if (distance != null) {
            Text(
                distanceAnnotated(distance),
                fontFamily = BarlowCondensed, fontWeight = FontWeight.Bold, fontSize = 64.sp,
                lineHeight = 54.sp, color = Ktm.White,
                modifier = Modifier.padding(top = 14.dp),
            )
        } else if (navText != null) {
            Text(
                navText, color = Ktm.White, fontFamily = BarlowCondensed, fontWeight = FontWeight.Bold,
                fontSize = 26.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 14.dp),
            )
        }

        guidance?.maneuver?.let { maneuver ->
            Text(
                maneuver, color = Ktm.TextSecondary, fontFamily = BarlowCondensed,
                fontWeight = FontWeight.SemiBold, fontSize = 22.sp, letterSpacing = 0.4.sp,
                modifier = Modifier.padding(top = 6.dp), textAlign = TextAlign.Center,
            )
        }
        val road = guidance?.road
        if (road != null) {
            Text(
                road, color = Ktm.Orange, fontFamily = BarlowCondensed, fontWeight = FontWeight.Bold,
                fontStyle = FontStyle.Italic, fontSize = 26.sp, letterSpacing = 0.3.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private fun distanceAnnotated(distance: String) = buildAnnotatedString {
    // Split trailing unit (e.g. "400 m" -> "400" + " m") so the unit renders smaller.
    val match = Regex("""^(\S+)\s*(.*)$""").find(distance.trim())
    if (match != null && match.groupValues[2].isNotBlank()) {
        append(match.groupValues[1])
        withStyle(androidx.compose.ui.text.SpanStyle(fontSize = 30.sp, color = Ktm.Muted2)) {
            append(" " + match.groupValues[2])
        }
    } else {
        append(distance)
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(Ktm.RadiusRow))
            .background(Ktm.Surface)
            .border(1.dp, Ktm.Border, RoundedCornerShape(Ktm.RadiusRow))
            .padding(horizontal = 15.dp, vertical = 13.dp),
    ) {
        com.navigator.app.ui.components.Eyebrow(label, color = Ktm.Dim, fontSize = 11, letterSpacing = 1.5)
        Text(value, color = Ktm.White, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold,
            fontSize = 22.sp, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun OpenMapsButton(enabled: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Ktm.RadiusButton))
            .background(Ktm.Surface)
            .border(1.dp, Ktm.BorderSoft, RoundedCornerShape(Ktm.RadiusButton))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(15.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(OpenDashIcons.Navigation, contentDescription = null,
            tint = if (enabled) Ktm.White else Ktm.Dim, modifier = Modifier.size(19.dp))
        Spacer(Modifier.size(10.dp))
        Text("OPEN MAPS", color = if (enabled) Ktm.White else Ktm.Dim,
            fontFamily = BarlowCondensed, fontWeight = FontWeight.Bold, fontSize = 16.sp, letterSpacing = 1.5.sp)
    }
}
