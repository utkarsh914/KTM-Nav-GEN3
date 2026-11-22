package com.navigator.app.ui.map

/**
 * The one way padding reaches the shared map: what the page asked for plus what the shell covers. Every change makes
 * the map re-lay itself out and block the UI thread for several frames, so an equal total is never sent again.
 */
internal class PaddingFunnel(private val send: (MapInsets) -> Unit) {
    private var requested = MapInsets()
    private var shell = MapInsets()
    private var applied: MapInsets? = null
    private var attached = false

    fun attach() { attached = true; apply() }

    fun detach() { attached = false; applied = null }

    fun request(p: MapInsets) { requested = p; apply() }

    fun setShell(i: MapInsets) { if (i != shell) { shell = i; apply() } }

    /** Sends the total again, e.g. after the SDK may have re-padded its own chrome. */
    fun reapply() { applied = null; apply() }

    private fun apply() {
        if (!attached) return
        val total = requested + shell
        if (applied == total) return
        applied = total
        send(total)
    }
}
