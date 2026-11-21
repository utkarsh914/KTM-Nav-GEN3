package com.navigator.app.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState

/**
 * Leaves the page once [pop] is true and the page is on top and settled. A one-shot "close yourself" event can
 * arrive while the page is covered or still animating, where a pop is refused and the event is gone; as state
 * this tries again when the page becomes the resumed one.
 */
@Composable
fun PopWhenResumed(pop: Boolean, onPop: () -> Unit) {
    val state by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    LaunchedEffect(pop, state) { if (pop && state == Lifecycle.State.RESUMED) onPop() }
}
