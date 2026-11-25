package com.navigator.app.ui.map

import org.junit.Assert.assertEquals
import org.junit.Test

class PaddingFunnelTest {
    private val sent = mutableListOf<MapInsets>()
    private val funnel = PaddingFunnel { sent += it }

    @Test fun `insets add up`() {
        assertEquals(MapInsets(1, 2, 3, 4), MapInsets(1, 0, 3, 1) + MapInsets(0, 2, 0, 3))
    }

    @Test fun `nothing is sent before the map is attached, then the total once`() {
        funnel.request(MapInsets(bottom = 700))
        funnel.setShell(MapInsets(bottom = 100))
        assertEquals(emptyList<MapInsets>(), sent)
        funnel.attach()
        assertEquals(listOf(MapInsets(bottom = 800)), sent)
    }

    @Test fun `an equal total is not sent again`() {
        funnel.attach()
        funnel.request(MapInsets(bottom = 700)); funnel.setShell(MapInsets(bottom = 100))
        funnel.request(MapInsets(bottom = 700))
        funnel.setShell(MapInsets(bottom = 100))
        assertEquals(listOf(MapInsets(), MapInsets(bottom = 700), MapInsets(bottom = 800)), sent)
    }

    @Test fun `reapply sends the same total again`() {
        funnel.attach(); funnel.request(MapInsets(left = 5))
        funnel.reapply()
        assertEquals(listOf(MapInsets(), MapInsets(left = 5), MapInsets(left = 5)), sent)
    }

    @Test fun `a new map gets the padding again after detach`() {
        funnel.attach(); funnel.request(MapInsets(left = 5)); funnel.detach(); funnel.attach()
        assertEquals(MapInsets(left = 5), sent.last())
        assertEquals(3, sent.size)
    }

    @Test fun `a new owner asking for the same padding changes nothing`() {
        funnel.attach(); funnel.request(MapInsets(bottom = 700))
        funnel.request(MapInsets(bottom = 700)) // the next page replays what it wanted: same total
        assertEquals(listOf(MapInsets(), MapInsets(bottom = 700)), sent)
    }

    @Test fun `only the new owner's own padding is sent after a change of owner`() {
        funnel.attach(); funnel.request(MapInsets(bottom = 700))
        funnel.request(MapInsets(left = 300)) // the next page on top
        assertEquals(MapInsets(left = 300), sent.last())
        assertEquals(3, sent.size)
    }
}
