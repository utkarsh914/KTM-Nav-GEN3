package com.navigator.app.ui.map

/**
 * Who the shared map answers to. Only the owner's controller may write to it (overlays, camera, chrome, listeners); a
 * screen that is not the owner keeps what it wants and takes the map back when it is the settled top page again. A
 * release by a non-owner changes nothing, so a page that is disposed after another has taken over cannot strip it.
 */
class MapOwnership {
    @Volatile private var owner: Any? = null

    fun claim(token: Any) { owner = token }

    /** Returns true when [token] was the owner and has let go. */
    fun release(token: Any): Boolean {
        if (owner !== token) return false
        owner = null
        return true
    }

    fun isOwner(token: Any): Boolean = owner === token
}

/** When a page may take the map: it is the top of the back stack (not a back-swipe preview) and has arrived. */
internal object MapClaimRule {
    fun canClaim(topEntryId: String?, entryId: String?, settled: Boolean): Boolean =
        settled && (entryId == null || topEntryId == entryId)
}
