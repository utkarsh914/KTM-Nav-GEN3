package com.navigator.app.ui.screens

import android.content.Intent
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.navigator.app.settings.AppSettings
import com.navigator.app.ui.OpenDashPermissions
import com.navigator.app.ui.components.Eyebrow
import com.navigator.app.ui.components.KtmOutlineButton
import com.navigator.app.ui.components.KtmPrimaryButton
import com.navigator.app.ui.theme.Barlow
import com.navigator.app.ui.theme.BarlowCondensed
import com.navigator.app.ui.theme.Ktm
import com.navigator.app.ui.theme.OpenDashIcons

/**
 * First-run onboarding: a lean two-step setup — a name step and an explained
 * permission checklist. Nothing is requested silently on launch: the rider
 * grants permissions here with context, which also fixes the crash where the
 * pairing screen scanned before the Bluetooth permission existed.
 *
 * The name step is skippable straight to setup; every step is individually
 * scrollable so the layout survives small screens and short aspect ratios
 * without clipping the pinned action buttons.
 */
private const val STEP_COUNT = 2

@Composable
fun OnboardingScreen(settings: AppSettings, onComplete: () -> Unit) {
    var step by remember { mutableIntStateOf(0) }
    var userName by remember { mutableStateOf(settings.userName ?: "") }

    // "Skip all": one tap out of the whole wizard (R21 field request - after a
    // reinstall everything is already granted and the walkthrough is friction).
    // Still fires the runtime-permission request so a genuinely fresh install
    // isn't left with a scan that can't run; the system returns instantly when
    // everything is already granted. Completes regardless of the answer, same
    // as the permission step's "Proceed anyway".
    val skipPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        settings.onboardingComplete = true
        onComplete()
    }
    val skipAll = {
        settings.userName = userName
        skipPermLauncher.launch(OpenDashPermissions.runtimePermissions())
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Ktm.Screen)
            .systemBarsPadding()
            .padding(horizontal = 22.dp)
            .padding(top = 12.dp, bottom = 18.dp),
    ) {
        OnboardingHeader(step = step)
        Spacer(Modifier.height(26.dp))

        AnimatedContent(
            targetState = step,
            transitionSpec = {
                val forward = targetState > initialState
                val dir = if (forward) 1 else -1
                (slideInHorizontally(tween(280)) { w -> dir * w / 6 } + fadeIn(tween(220)))
                    .togetherWith(
                        slideOutHorizontally(tween(280)) { w -> -dir * w / 6 } + fadeOut(tween(160))
                    )
            },
            label = "onboarding-step",
            modifier = Modifier.weight(1f),
        ) { current ->
            Column(modifier = Modifier.fillMaxSize()) {
                when (current) {
                    0 -> NameStep(
                        userName = userName,
                        onNameChange = { userName = it; settings.userName = it },
                        onContinue = { step = 1 },
                        onSkip = skipAll,
                    )
                    else -> PermissionsStep(
                        onBack = { step = 0 },
                        onProceed = {
                            settings.userName = userName
                            settings.onboardingComplete = true
                            onComplete()
                        },
                    )
                }
            }
        }
    }
}

/** Wordmark + a segmented progress indicator that widens the current step. */
@Composable
private fun OnboardingHeader(step: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text("KTM ", color = Ktm.Orange, fontFamily = BarlowCondensed,
            fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic, fontSize = 22.sp)
        Text("NAVIGATOR", color = Ktm.White, fontFamily = BarlowCondensed,
            fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic, fontSize = 22.sp)
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(STEP_COUNT) { i ->
                Box(
                    modifier = Modifier
                        .size(width = if (i == step) 20.dp else 7.dp, height = 7.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                i == step -> Ktm.Orange
                                i < step -> Ktm.OrangeDeep
                                else -> Ktm.Border
                            }
                        ),
                )
            }
        }
    }
}

/* ----------------------------------------------------------------------- */
/* Shared step chrome                                                      */
/* ----------------------------------------------------------------------- */

/** Back (left) + Skip (right) row shared by the steps. */
@Composable
private fun IntroFooter(onBack: (() -> Unit)?, onSkip: (() -> Unit)?) {
    Spacer(Modifier.height(4.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            Text(
                "Back", color = Ktm.Dim, fontFamily = Barlow, fontSize = 13.sp,
                modifier = Modifier.clickable(onClick = onBack).padding(8.dp),
            )
        }
        Spacer(Modifier.weight(1f))
        if (onSkip != null) {
            Text(
                "Skip all", color = Ktm.Dim, fontFamily = Barlow, fontSize = 13.sp,
                modifier = Modifier.clickable(onClick = onSkip).padding(8.dp),
            )
        }
    }
}

/* ----------------------------------------------------------------------- */
/* Name + permission steps                                                 */
/* ----------------------------------------------------------------------- */

@Composable
private fun ColumnScope.NameStep(
    userName: String,
    onNameChange: (String) -> Unit,
    onContinue: () -> Unit,
    onSkip: () -> Unit,
) {
    Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
        Eyebrow("Almost there", color = Ktm.Orange, fontSize = 12, letterSpacing = 2.0)
        Spacer(Modifier.height(8.dp))
        Text(
            "What should\nwe call you?",
            color = Ktm.White, fontFamily = BarlowCondensed, fontWeight = FontWeight.Bold,
            fontStyle = FontStyle.Italic, fontSize = 38.sp, lineHeight = 36.sp, letterSpacing = (-0.5).sp,
        )
        Spacer(Modifier.height(14.dp))
        Text(
            "Used to personalize the dash greeting and test messages. This never leaves your phone.",
            color = Ktm.Muted2, fontFamily = Barlow, fontSize = 15.sp, lineHeight = 22.sp,
        )
        Spacer(Modifier.height(26.dp))
        Eyebrow("Your name", color = Ktm.Dim, fontSize = 11, letterSpacing = 1.5)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = userName,
            onValueChange = onNameChange,
            singleLine = true,
            placeholder = { Text("e.g. Rider", color = Ktm.Dim, fontFamily = Barlow) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            colors = ktmFieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )
    }
    Spacer(Modifier.height(14.dp))
    KtmPrimaryButton("Continue", enabled = userName.isNotBlank(), onClick = onContinue)
    IntroFooter(onBack = null, onSkip = onSkip)
}

@Composable
private fun ColumnScope.PermissionsStep(
    onBack: () -> Unit,
    onProceed: () -> Unit,
) {
    val context = LocalContext.current

    // Re-checked on resume so grants made in system settings reflect here.
    var tick by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) tick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { tick++ }

    // Read live status (tick forces recomposition after grants).
    val btGranted = tick.let { OpenDashPermissions.bluetoothGranted(context) }
    val notifPost = tick.let { OpenDashPermissions.notificationsPostGranted(context) }
    val notifAccess = tick.let { OpenDashPermissions.notificationAccessGranted(context) }
    val locationGranted = tick.let {
        OpenDashPermissions.isGranted(context, android.Manifest.permission.ACCESS_FINE_LOCATION)
    }
    val runtimeAllGranted = btGranted && notifPost

    Eyebrow("Permissions", color = Ktm.Orange, fontSize = 12, letterSpacing = 2.0)
    Spacer(Modifier.height(8.dp))
    Text(
        "A few permissions.",
        color = Ktm.White, fontFamily = BarlowCondensed, fontWeight = FontWeight.Bold,
        fontStyle = FontStyle.Italic, fontSize = 34.sp, letterSpacing = (-0.4).sp,
    )
    Spacer(Modifier.height(6.dp))
    Text(
        "KTM Navigator needs these to talk to your bike. Nothing leaves your phone.",
        color = Ktm.Muted2, fontFamily = Barlow, fontSize = 14.sp, lineHeight = 20.sp,
    )

    Spacer(Modifier.height(18.dp))
    Column(
        modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PermissionCard(
            icon = OpenDashIcons.Bluetooth,
            title = "Nearby devices",
            desc = "Find and connect to your bike's dash over Bluetooth. Required to pair.",
            granted = btGranted,
            onGrant = { permLauncher.launch(OpenDashPermissions.runtimePermissions()) },
        )
        PermissionCard(
            icon = OpenDashIcons.Bell,
            title = "Notification access",
            desc = "Read incoming notifications so they can be mirrored to the dash.",
            granted = notifAccess,
            onGrant = { context.startActivity(Intent(AndroidSettings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
        )
        PermissionCard(
            icon = OpenDashIcons.LocateFixed,
            title = "Location",
            desc = "For speed alerts and in-app navigation. Also needed to find your bike on older Android.",
            granted = locationGranted,
            onGrant = { permLauncher.launch(OpenDashPermissions.runtimePermissions()) },
        )
        PermissionCard(
            icon = OpenDashIcons.Navigation,
            title = "Post notifications",
            desc = "Show the persistent connection status and dash-mirroring service.",
            granted = notifPost,
            onGrant = { permLauncher.launch(OpenDashPermissions.runtimePermissions()) },
        )
    }

    Spacer(Modifier.height(12.dp))
    if (!runtimeAllGranted) {
        KtmPrimaryButton("Grant permissions") {
            permLauncher.launch(OpenDashPermissions.runtimePermissions())
        }
        Spacer(Modifier.height(10.dp))
        KtmOutlineButton(
            if (btGranted) "Proceed" else "Proceed anyway",
            onClick = onProceed,
        )
    } else {
        KtmPrimaryButton("Proceed", onClick = onProceed)
    }
    IntroFooter(onBack = onBack, onSkip = null)
}

@Composable
private fun PermissionCard(
    icon: ImageVector,
    title: String,
    desc: String,
    granted: Boolean,
    onGrant: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Ktm.RadiusRow))
            .background(Ktm.Surface)
            .border(1.dp, if (granted) Ktm.ConnBorder else Ktm.Border, RoundedCornerShape(Ktm.RadiusRow))
            .clickable(enabled = !granted, onClick = onGrant)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = if (granted) Ktm.Green else Ktm.Orange,
            modifier = Modifier.size(24.dp))
        Column(modifier = Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(title, color = Ktm.White, fontFamily = BarlowCondensed, fontWeight = FontWeight.Bold,
                fontSize = 17.sp, letterSpacing = 0.3.sp)
            Text(desc, color = Ktm.Dim, fontFamily = Barlow, fontSize = 12.5.sp, lineHeight = 17.sp,
                modifier = Modifier.padding(top = 2.dp))
        }
        if (granted) {
            Icon(OpenDashIcons.Check, contentDescription = "Granted", tint = Ktm.Green, modifier = Modifier.size(20.dp))
        } else {
            Text("GRANT", color = Ktm.Orange, fontFamily = BarlowCondensed, fontWeight = FontWeight.Bold,
                fontSize = 12.sp, letterSpacing = 1.sp)
        }
    }
}

@Composable
internal fun ktmFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Ktm.Orange,
    unfocusedBorderColor = Ktm.Border,
    focusedTextColor = Ktm.White,
    unfocusedTextColor = Ktm.TextPrimary,
    cursorColor = Ktm.Orange,
    focusedContainerColor = Ktm.SurfaceAlt,
    unfocusedContainerColor = Ktm.SurfaceAlt,
)
