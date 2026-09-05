package com.navigator.app.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.navigator.app.ble.BccuConnectionService
import com.navigator.app.ui.theme.Barlow
import com.navigator.app.ui.theme.BarlowCondensed
import com.navigator.app.ui.theme.Ktm
import com.navigator.app.ui.theme.OpenDashIcons

/** Subtle press feedback shared by the app's tappable chrome: a gentle scale-down
 *  while held that springs back on release. Pair the passed [interaction] source with
 *  the element's `clickable(...)` and apply the returned factor via `graphicsLayer`.
 *  Kept intentionally small so it reads as tactile, never bouncy. */
@Composable
private fun rememberPressScale(
    interaction: MutableInteractionSource,
    pressedScale: Float = 0.97f,
): Float {
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "pressScale",
    )
    return scale
}

/** Small uppercase, letter-spaced eyebrow/group label (Barlow Condensed 700). */
@Composable
fun Eyebrow(
    text: String,
    color: Color = Ktm.Orange,
    fontSize: Int = 12,
    letterSpacing: Double = 2.0,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text.uppercase(),
        modifier = modifier,
        color = color,
        fontFamily = BarlowCondensed,
        fontWeight = FontWeight.Bold,
        fontSize = fontSize.sp,
        letterSpacing = letterSpacing.sp,
    )
}

/** The 42x24 orange pill toggle from the handoff (screens 04 & 05). */
@Composable
fun KtmToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val knobOffset by animateDpAsState(if (checked) 21.dp else 3.dp, label = "knob")
    Box(
        modifier = modifier
            .width(42.dp)
            .height(24.dp)
            .clip(CircleShape)
            .background(if (checked) Ktm.Orange else Ktm.SurfaceDisabled)
            .clickable { onCheckedChange(!checked) },
    ) {
        Box(
            modifier = Modifier
                .padding(start = knobOffset)
                .align(Alignment.CenterStart)
                .size(18.dp)
                .clip(CircleShape)
                .background(if (checked) Ktm.OnAccent else Ktm.Muted),
        )
    }
}

/** A labelled group of setting rows inside one bordered card (screen 05). */
@Composable
fun GroupCard(
    label: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Eyebrow(
            label,
            fontSize = 11,
            letterSpacing = 2.0,
            modifier = Modifier.padding(start = 4.dp, bottom = 7.dp),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Ktm.RadiusCard))
                .background(Ktm.Surface)
                .border(1.dp, Ktm.Border, RoundedCornerShape(Ktm.RadiusCard)),
        ) {
            content()
        }
    }
}

/** One row within a [GroupCard]: label on the left, arbitrary trailing content on the right. */
@Composable
fun SettingsRow(
    label: String,
    showDivider: Boolean,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    // Rows are large, so a whisper of scale (0.985) reads better than the chrome default.
    val scale = if (onClick != null) rememberPressScale(interaction, pressedScale = 0.985f) else 1f
    val base = Modifier
        .fillMaxWidth()
        .let { if (onClick != null) it.graphicsLayer { scaleX = scale; scaleY = scale } else it }
        .let {
            if (onClick != null) {
                it.clickable(
                    interactionSource = interaction,
                    indication = LocalIndication.current,
                    onClick = onClick,
                )
            } else it
        }
    Column(modifier = base) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                label,
                color = Ktm.TextPrimary,
                fontFamily = Barlow,
                fontSize = 14.sp,
                modifier = Modifier.weight(1f, fill = false),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Box(modifier = Modifier.padding(start = 12.dp)) { trailing() }
        }
        if (showDivider) {
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Ktm.RowDivider))
        }
    }
}

/** Full-width primary (orange) action button used across onboarding/pairing. */
@Composable
fun KtmPrimaryButton(
    text: String,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val scale = rememberPressScale(interaction)
    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .fillMaxWidth()
            .clip(RoundedCornerShape(Ktm.RadiusButton))
            .background(if (enabled) Ktm.Orange else Ktm.SurfaceDisabled)
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                enabled = enabled,
                onClick = onClick,
            )
            .padding(vertical = 15.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text.uppercase(), color = if (enabled) Ktm.OnAccent else Ktm.Dim,
            fontFamily = BarlowCondensed, fontWeight = FontWeight.Bold, fontSize = 16.sp, letterSpacing = 1.5.sp,
        )
    }
}

/** Full-width secondary (outlined) action button. */
@Composable
fun KtmOutlineButton(
    text: String,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val scale = rememberPressScale(interaction)
    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .fillMaxWidth()
            .clip(RoundedCornerShape(Ktm.RadiusButton))
            .background(Ktm.Surface)
            .border(1.dp, if (enabled) Ktm.BorderSoft else Ktm.Border, RoundedCornerShape(Ktm.RadiusButton))
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                enabled = enabled,
                onClick = onClick,
            )
            .padding(vertical = 15.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text.uppercase(), color = if (enabled) Ktm.White else Ktm.Dim,
            fontFamily = BarlowCondensed, fontWeight = FontWeight.Bold, fontSize = 16.sp, letterSpacing = 1.5.sp,
        )
    }
}

/** Monospace trailing value (times, RSSI, masked keys). */
@Composable
fun MonoValue(text: String, color: Color = Ktm.Dim, fontSize: Int = 12) {
    Text(
        text,
        color = color,
        fontFamily = com.navigator.app.ui.theme.JetBrainsMono,
        fontSize = fontSize.sp,
    )
}

/** Circular back button (Surface fill + soft border) — the app's standard nav-up
 *  control, matching the map/mirror home icon buttons. */
@Composable
fun CircleBackButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val scale = rememberPressScale(interaction)
    Box(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .size(44.dp)
            .clip(CircleShape)
            .background(Ktm.Surface)
            .border(1.dp, Ktm.BorderSoft, CircleShape)
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(OpenDashIcons.ChevronLeft, contentDescription = "Back", tint = Ktm.White,
            modifier = Modifier.size(22.dp))
    }
}

/** Standard secondary-screen header: circular back button + orange eyebrow label
 *  (+ optional trailing content), matching the NavigationHome/MirrorHome idiom. */
@Composable
fun ScreenTopBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircleBackButton(onClick = onBack)
        Eyebrow(
            title, fontSize = 13, letterSpacing = 2.0,
            modifier = Modifier.padding(start = 14.dp).weight(1f),
        )
        trailing()
    }
}

/** A 52dp rounded-square icon button. Shared by both home screens (settings,
 *  clear, recenter) so the control chrome is identical across engines and lines
 *  up with the search bar / connection pill height. */
@Composable
fun IconPill(icon: ImageVector, contentDescription: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val scale = rememberPressScale(interaction)
    Box(
        modifier = Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .size(Ktm.ControlHeight)
            .clip(RoundedCornerShape(Ktm.RadiusButton))
            .background(Ktm.Surface)
            .border(1.dp, Ktm.Border, RoundedCornerShape(Ktm.RadiusButton))
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = Ktm.White, modifier = Modifier.size(22.dp))
    }
}

/** Rich bike-connection button (status dot + bike icon + device label).
 *  Subscribes to the live connection state itself. Shared by both home screens
 *  so the connection affordance is identical across engines. */
@Composable
fun ConnectPill(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val state by BccuConnectionService.connectionState.collectAsState()
    val deviceName by BccuConnectionService.deviceName.collectAsState()

    val connected = state == BccuConnectionService.ConnectionState.AUTHENTICATED
    val connecting = state == BccuConnectionService.ConnectionState.CONNECTING
    val dotColor = when {
        connected -> Ktm.Green
        connecting -> Ktm.Orange
        else -> Ktm.Danger
    }
    val label = when {
        connected -> deviceName ?: "Connected"
        connecting -> "Connecting…"
        else -> "Connect"
    }

    val interaction = remember { MutableInteractionSource() }
    val scale = rememberPressScale(interaction)
    Row(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .height(Ktm.ControlHeight)
            .clip(RoundedCornerShape(Ktm.RadiusButton))
            .background(Ktm.Surface)
            .border(1.dp, if (connected) Ktm.ConnBorder else Ktm.Border, RoundedCornerShape(Ktm.RadiusButton))
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(9.dp).clip(CircleShape).background(dotColor))
        Spacer(Modifier.size(9.dp))
        Icon(OpenDashIcons.Bike, null, tint = Ktm.White, modifier = Modifier.size(18.dp))
        Spacer(Modifier.size(8.dp))
        Text(label, color = Ktm.White, fontFamily = BarlowCondensed, fontWeight = FontWeight.Bold, fontSize = 15.sp, letterSpacing = 0.5.sp)
    }
}
