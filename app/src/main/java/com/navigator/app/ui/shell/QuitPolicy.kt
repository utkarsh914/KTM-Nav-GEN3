package com.navigator.app.ui.shell

/** Quit asks first only while something would be lost: a trip (in-app or mirrored) or a recording. */
internal object QuitPolicy {
    fun needsConfirm(tripLive: Boolean, recording: Boolean, mirrorGuiding: Boolean): Boolean = tripLive || recording || mirrorGuiding
}
