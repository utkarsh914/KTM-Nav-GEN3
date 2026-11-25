package com.navigator.app.ui.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MapOwnershipTest {
    private val a = Any()
    private val b = Any()
    private val map = MapOwnership()

    @Test fun aPushHandsTheMapOnAndTheOldPageDisposingAfterwardsChangesNothing() {
        map.claim(a)
        map.claim(b) // B settled on top
        assertFalse("A is disposed once B has settled", map.release(a))
        assertTrue(map.isOwner(b))
    }

    @Test fun theOldPageLeavingBeforeTheNewOneArrivesLeavesNoOwnerUntilItClaims() {
        map.claim(a)
        assertTrue(map.release(a))
        assertFalse(map.isOwner(a))
        assertFalse(map.isOwner(b))
        map.claim(b)
        assertTrue(map.isOwner(b))
    }

    @Test fun aCancelledBackSwipeNeverCostsThePageOnTopItsMap() {
        map.claim(b) // B is on top and owns the map
        // A is only composed for the preview: it does not claim (see MapClaimRule), and is disposed when the swipe is cancelled.
        assertFalse(map.release(a))
        assertTrue(map.isOwner(b))
    }

    @Test fun aPageThatLostTheMapTakesItBackWhenItIsOnTopAgain() {
        map.claim(a)
        map.claim(b)
        assertFalse(map.isOwner(a))
        map.claim(a)
        assertTrue(map.isOwner(a))
        assertFalse(map.isOwner(b))
    }

    @Test fun onlyTheSettledTopPageMayClaim() {
        assertTrue(MapClaimRule.canClaim("a", "a", settled = true))
        assertFalse("a back-swipe preview is not the top", MapClaimRule.canClaim("b", "a", settled = true))
        assertFalse("not while the page is still moving", MapClaimRule.canClaim("a", "a", settled = false))
        assertTrue("outside a page (previews, tests)", MapClaimRule.canClaim(null, null, settled = true))
        assertEquals(false, MapClaimRule.canClaim("b", "a", settled = false))
    }
}
