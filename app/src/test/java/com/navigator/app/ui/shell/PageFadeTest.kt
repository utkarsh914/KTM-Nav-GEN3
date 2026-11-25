package com.navigator.app.ui.shell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The page fades that fadeIn/fadeOut used to be (D28), now decided in one place so they can run without an offscreen layer. */
class PageFadeTest {
    private val none = PageMove(rideContainer = false, tab = false, pageToMap = false, mapToMap = false, mapToPage = false)

    @Test fun aPlainPushSlidesAndNothingFades() {
        assertNull(pageFade(none, entering = true, pop = false, reduced = false))
        assertNull(pageFade(none, entering = false, pop = false, reduced = false))
        assertNull(pageFade(none, entering = true, pop = true, reduced = false))
        assertNull(pageFade(none, entering = false, pop = true, reduced = false))
    }

    @Test fun aTabFadesInOverTheOldOneWhichStays() {
        val tab = none.copy(tab = true)
        assertEquals(PageFade.Default, pageFade(tab, entering = true, pop = false, reduced = false))
        assertNull(pageFade(tab, entering = false, pop = false, reduced = false))
    }

    @Test fun backToNavigateLeavesTheOldTabFadingOnTopOfAPageThatIsAlreadyThere() {
        val tab = none.copy(tab = true, pop = true)
        assertNull("the page it returns to is fully visible from the first frame", pageFade(tab, entering = true, pop = true, reduced = false))
        assertEquals(PageFade.Fast, pageFade(tab, entering = false, pop = true, reduced = false))
    }

    @Test fun aMapPageOverAPageFadesItsControlsInAndThePageAwayThenBackAgain() {
        val m = none.copy(pageToMap = true)
        assertEquals(PageFade.Default, pageFade(m, entering = true, pop = false, reduced = false))
        assertEquals(PageFade.Default, pageFade(m, entering = false, pop = false, reduced = false))
        assertNull(pageFade(m, entering = true, pop = true, reduced = false))
    }

    @Test fun backFromAMapPageToAPageDissolves() {
        val m = none.copy(mapToPage = true)
        assertNull(pageFade(m, entering = true, pop = false, reduced = false))
        assertEquals(PageFade.Default, pageFade(m, entering = true, pop = true, reduced = false))
        assertEquals(PageFade.Default, pageFade(m, entering = false, pop = true, reduced = false))
    }

    @Test fun twoMapPagesCrossFadeBothWays() {
        val m = none.copy(mapToMap = true)
        for (pop in listOf(false, true)) for (entering in listOf(false, true))
            assertEquals(PageFade.Default, pageFade(m, entering, pop, reduced = false))
    }

    @Test fun theRideCardMoveFadesTheReplayInAndOutQuicklyAndNothingElse() {
        val r = none.copy(rideContainer = true)
        assertEquals(PageFade.Default, pageFade(r, entering = true, pop = false, reduced = false))
        assertNull(pageFade(r, entering = false, pop = false, reduced = false))
        assertNull(pageFade(r, entering = true, pop = true, reduced = false))
        assertEquals(PageFade.Fast, pageFade(r, entering = false, pop = true, reduced = false))
    }

    @Test fun removeAnimationsMakesEveryPushAFadeAndEveryBackAQuickOne() {
        assertEquals(PageFade.Default, pageFade(none, entering = true, pop = false, reduced = true))
        assertNull(pageFade(none, entering = false, pop = false, reduced = true))
        assertNull(pageFade(none, entering = true, pop = true, reduced = true))
        assertEquals(PageFade.Fast, pageFade(none, entering = false, pop = true, reduced = true))
    }
}
