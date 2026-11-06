package com.centurywar.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MilestoneRibbonTest {
    @Test fun fifoAndLifetime() {
        val ribbon = MilestoneRibbon()
        ribbon.push("pierwszy"); ribbon.push("drugi"); ribbon.push("trzeci")
        assertEquals("pierwszy", ribbon.current)
        assertEquals(2, ribbon.pending)
        ribbon.update(MilestoneRibbon.SHOW_TIME - .1f, false)
        assertEquals("pierwszy", ribbon.current)
        ribbon.update(.11f, false)
        assertEquals("drugi", ribbon.current)
        assertEquals(1, ribbon.pending)
        ribbon.update(MilestoneRibbon.SHOW_TIME, false)
        assertEquals("trzeci", ribbon.current)
        ribbon.update(MilestoneRibbon.SHOW_TIME, false)
        assertFalse(ribbon.active)
        assertNull(ribbon.current)
        assertEquals(0, ribbon.pending)
    }

    @Test fun holdFreezesCurrentAndQueue() {
        val ribbon = MilestoneRibbon()
        ribbon.push("a"); ribbon.push("b")
        ribbon.update(2.9f, false)
        ribbon.update(100f, true)
        assertEquals("a", ribbon.current)
        assertEquals(1, ribbon.pending)
        ribbon.update(.05f, false)
        assertEquals("a", ribbon.current)
        ribbon.update(.06f, false)
        assertEquals("b", ribbon.current)
    }

    @Test fun overflowingQueueKeepsFirstPendingAndNewest() {
        val ribbon = MilestoneRibbon()
        for (i in 0..1000) ribbon.push("$i")
        assertTrue(ribbon.active)
        assertEquals(2, ribbon.pending)
        assertEquals("0", ribbon.current)
        ribbon.update(MilestoneRibbon.SHOW_TIME, false)
        assertEquals("1", ribbon.current)
        ribbon.update(MilestoneRibbon.SHOW_TIME, false)
        assertEquals("1000", ribbon.current)
        ribbon.update(MilestoneRibbon.SHOW_TIME, false)
        assertFalse(ribbon.active)
    }

    @Test fun clearResetsEverythingAndAllowsReuse() {
        val ribbon = MilestoneRibbon()
        ribbon.push("a"); ribbon.push("b"); ribbon.drawn.set(1f, 2f, 3f, 4f)
        ribbon.clear()
        assertFalse(ribbon.active)
        assertNull(ribbon.current)
        assertEquals(0, ribbon.pending)
        assertTrue(ribbon.drawn.isEmpty)
        ribbon.push("nowy")
        assertEquals("nowy", ribbon.current)
        ribbon.update(MilestoneRibbon.SHOW_TIME - .1f, false)
        assertEquals("nowy", ribbon.current)
    }
}
