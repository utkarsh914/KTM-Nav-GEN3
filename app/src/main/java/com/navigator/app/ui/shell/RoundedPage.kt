package com.navigator.app.ui.shell

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.navigator.app.ui.theme.KtmLayout
import com.navigator.app.ui.theme.KtmTheme

/**
 * A pushed page with no map under it: it gets rounded corners while it leaves, so a back swipe previews the page
 * shrinking like the system's. Pages that host the map are not wrapped: a clip layer over the Nav SDK's map view is
 * not worth the risk.
 */
internal inline fun <reified T : Any> NavGraphBuilder.listPage(
    noinline content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit,
) = composable<T> { entry -> RoundedWhileLeaving { content(entry) } }

@Composable
private fun AnimatedContentScope.RoundedWhileLeaving(content: @Composable () -> Unit) {
    val reduced = KtmTheme.motion.reduced
    val radius by transition.animateDp(transitionSpec = { tween(if (reduced) 0 else LEAVE_MS) }, label = "pageCorner") { state ->
        if (state == EnterExitState.PostExit && !reduced) KtmLayout.pageLeaveCorner else 0.dp
    }
    Box(Modifier.clip(RoundedCornerShape(radius))) { content() }
}

private const val LEAVE_MS = 200
