package com.navigator.app.ui.feature.navigate.riding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.navigator.app.R
import com.navigator.app.ui.map.rememberOffNorth
import com.navigator.app.ui.theme.KtmElevation
import com.navigator.app.ui.theme.KtmIcons
import com.navigator.app.ui.theme.KtmLayout
import com.navigator.app.ui.theme.KtmShapes
import com.navigator.app.ui.theme.KtmSpacing
import com.navigator.app.ui.theme.KtmTheme
import com.navigator.app.ui.theme.rememberHaptics

/**
 * NV1/NV8: the one map control. Overview shows the whole remaining route; after the rider moves the map (or opens
 * the overview) it becomes an accent Recenter, and a compass appears above it when the map is turned (tap = north up).
 */
@Composable
fun RidingMapButton(
    recenter: Boolean,
    bearing: () -> Float,
    onTap: () -> Unit,
    onNorthUp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val r = KtmTheme.riding
    val a = KtmTheme.accent
    val haptics = rememberHaptics()
    Column(modifier, horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(KtmSpacing.sm)) {
        val offNorth by rememberOffNorth(bearing)
        if (recenter && offNorth) {
            val north = stringResource(R.string.riding_north_up)
            Box(
                Modifier.size(KtmLayout.targetPrimary).shadow(KtmElevation.onMap, KtmShapes.Card).clip(KtmShapes.Card).background(r.surface)
                    .clickable(role = Role.Button, onClickLabel = north) { haptics.tap(); onNorthUp() }
                    .semantics { contentDescription = north },
                contentAlignment = Alignment.Center,
            ) { Icon(KtmIcons.NavigationUp, null, tint = r.onSurface, modifier = Modifier.graphicsLayer { rotationZ = -bearing() }) }
        }
        val label = stringResource(if (recenter) R.string.riding_recenter else R.string.riding_overview)
        val motion = KtmTheme.motion
        // MO4: one button, two jobs: the colour turns accent and the icon rotates into Recenter.
        val container by animateColorAsState(if (recenter) a.fill else r.surface, motion.ridingColor(), label = "mapButton")
        Box(
            Modifier.size(KtmLayout.targetRiding).shadow(KtmElevation.onMap, KtmShapes.Card).clip(KtmShapes.Card)
                .background(container)
                .clickable(role = Role.Button, onClickLabel = label) { haptics.tap(); onTap() }
                .semantics { contentDescription = label },
            contentAlignment = Alignment.Center,
        ) {
            AnimatedContent(
                targetState = recenter,
                transitionSpec = {
                    (fadeIn(motion.ridingFade()) + scaleIn(motion.ridingSpatial(), initialScale = MORPH_SCALE)) togetherWith
                        (fadeOut(motion.ridingFadeOut()) + scaleOut(motion.ridingExit(), targetScale = MORPH_SCALE))
                },
                label = "mapButtonIcon",
            ) { toRecenter ->
                val turn by transition.animateFloat(transitionSpec = { motion.ridingSpatial() }, label = "mapButtonTurn") {
                    if (it == EnterExitState.Visible || motion.reduced) 0f else if (toRecenter) -MORPH_TURN else MORPH_TURN
                }
                Icon(
                    if (toRecenter) KtmIcons.Recenter else KtmIcons.Route, null, tint = if (toRecenter) a.onFill else r.onSurface,
                    modifier = Modifier.graphicsLayer { rotationZ = turn },
                )
            }
        }
    }
}

private const val MORPH_SCALE = 0.6f
private const val MORPH_TURN = 90f
