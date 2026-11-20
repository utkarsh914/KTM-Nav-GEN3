package com.navigator.app.ui.feature.record

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.core.content.ContextCompat
import com.navigator.app.R
import com.navigator.app.ui.components.KtmFab
import com.navigator.app.ride.RecordStartPolicy
import com.navigator.app.ride.RecordStartResult
import com.navigator.app.ride.RideRecordingService
import com.navigator.app.ride.RideSession
import com.navigator.app.ui.shell.LocalFeedback
import com.navigator.app.ui.theme.KtmElevation
import com.navigator.app.ui.theme.KtmIcons
import com.navigator.app.ui.theme.KtmLayout
import com.navigator.app.ui.theme.KtmShapes
import com.navigator.app.ui.theme.KtmSpacing
import com.navigator.app.ui.theme.KtmTheme
import com.navigator.app.ui.theme.rememberHaptics

/** Starts a manual recording (no destination); a refusal is said in a snackbar, never a Toast. */
@Composable
fun rememberStartRecording(): () -> Unit {
    val context = LocalContext.current
    val feedback = LocalFeedback.current
    val resources = LocalResources.current
    return remember(context, feedback) {
        {
            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
            when (RecordStartPolicy.result(granted) { RideRecordingService.startManual(context) }) {
                RecordStartResult.Started -> Unit
                RecordStartResult.NeedsLocation -> feedback.snackbar(resources.getString(R.string.record_needs_location))
                RecordStartResult.Failed -> feedback.snackbar(resources.getString(R.string.record_failed))
            }
        }
    }
}

/**
 * "Record a ride" pill on the browse map. While a recording runs, the activity strip owns it (pause, stop), so the
 * pill steps aside.
 */
@Composable
fun RecordRidePill(modifier: Modifier = Modifier) {
    val session by RideSession.state.collectAsState()
    if (session.active) return
    val start = rememberStartRecording()
    val c = KtmTheme.colors
    val haptics = rememberHaptics()
    Row(
        modifier.shadow(KtmElevation.onMap, KtmShapes.Pill).height(KtmLayout.targetRest).clip(KtmShapes.Pill).background(c.surf)
            .clickable(role = Role.Button) { haptics.tap(); start() }
            .padding(start = KtmLayout.pillStartPadding, end = KtmSpacing.lg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(KtmSpacing.sm),
    ) {
        Icon(KtmIcons.Record, null, tint = c.bad, modifier = Modifier.size(KtmLayout.iconInline))
        Text(stringResource(R.string.record_ride), style = KtmTheme.type.buttonCompact, color = c.fg, maxLines = 1)
    }
}

/** The Rides tab's Record button: a 56 dp accent pill. The host hides it while a recording runs. */
@Composable
fun RecordFab(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) = KtmFab(label, KtmIcons.Record, onClick, modifier)
