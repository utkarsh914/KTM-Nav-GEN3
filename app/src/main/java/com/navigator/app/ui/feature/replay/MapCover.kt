package com.navigator.app.ui.feature.replay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.navigator.app.data.rides.ThumbPoint
import com.navigator.app.ui.feature.rides.ThumbnailGeometry
import com.navigator.app.ui.feature.rides.drawRoute
import com.navigator.app.ui.theme.KtmLayout
import com.navigator.app.ui.theme.KtmSpacing
import com.navigator.app.ui.theme.KtmTheme

/**
 * MO8: the ride card's route thumbnail, grown to the size of the page, stands in for the map until the map has
 * drawn its first framed frame, then fades away: the track you tapped becomes the track you are looking at, and a
 * map view that is still starting (or being resized by the transition) is never seen blank.
 */
@Composable
fun MapCover(visible: Boolean, points: List<ThumbPoint>?, modifier: Modifier = Modifier) {
    val c = KtmTheme.colors
    AnimatedVisibility(visible, modifier, exit = fadeOut(KtmTheme.motion.effectsDefault())) {
        Box(Modifier.fillMaxSize().background(c.surf3).clearAndSetSemantics {}) {
            if (!points.isNullOrEmpty()) {
                val neutral = c.fg2
                val stroke = KtmLayout.outline
                Canvas(Modifier.fillMaxSize()) {
                    val px = ThumbnailGeometry.project(points, size.width, size.height, pad = KtmSpacing.xxxl.toPx())
                    drawRoute(px, stroke.toPx() * COVER_STROKE, neutral)
                }
            }
        }
    }
}

/** The thumbnail's line is drawn this much heavier on the full-size cover, so it reads as the same route. */
private const val COVER_STROKE = 2f

/** The cover goes this long after the map has framed the track, so the map has painted, and never later than [COVER_MAX_MS]. */
const val COVER_HOLD_MS = 250L
const val COVER_MAX_MS = 3_000L
