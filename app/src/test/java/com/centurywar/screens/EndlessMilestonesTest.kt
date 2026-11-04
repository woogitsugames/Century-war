package com.centurywar.screens

import com.centurywar.screens.EndlessMilestones.Companion.RECORD
import com.centurywar.screens.EndlessMilestones.Companion.TIER_0
import com.centurywar.screens.EndlessMilestones.Companion.TIER_1
import com.centurywar.screens.EndlessMilestones.Companion.TIER_2
import org.junit.Assert.assertEquals
import org.junit.Test

/** R11-03: progi tygodnia i rekord fali ogłaszane raz, bez stanu sprzed bitwy lub wznowienia. */
class EndlessMilestonesTest {
    /** Maski zdarzeń kolejnych fal od [from] do [to] (pierwsze wywołanie: start lub wznowienie). */
    private fun run(m: EndlessMilestones, from: Int, to: Int): Map<Int, Int> {
        val out = LinkedHashMap<Int, Int>()
        assertEquals(0, m.step(from))
        for (w in from + 1..to) {
            val mask = m.step(w)
            if (mask != 0) out[w] = mask
            assertEquals("ta sama fala drugi raz", 0, m.step(w))
        }
        return out
    }

    @Test fun weeklyTiersOnce() {
        val m = EndlessMilestones(0, 0, true)
        assertEquals(mapOf(10 to TIER_0, 20 to TIER_1, 30 to TIER_2), run(m, 0, 60))
        assertEquals(3, m.tierEvents)
        assertEquals(2, m.lastTier)
        assertEquals(0, m.recordEvents)
    }

    @Test fun tiersClaimedBeforeBattleAreSilent() {
        val m = EndlessMilestones(0, 0b011, true)
        assertEquals(mapOf(30 to TIER_2), run(m, 0, 40))
        assertEquals(1, m.tierEvents)
    }

    @Test fun regularEndlessHasNoTiers() {
        val m = EndlessMilestones(0, 0, false)
        assertEquals(emptyMap<Int, Int>(), run(m, 0, 60))
    }

    @Test fun recordOnceOnlyWhenBestExists() {
        val m = EndlessMilestones(38, 0, false)
        assertEquals(mapOf(39 to RECORD), run(m, 0, 70))
        assertEquals(1, m.recordEvents)
        val first = EndlessMilestones(0, 0, false)
        assertEquals(emptyMap<Int, Int>(), run(first, 0, 70))
    }

    @Test fun tierAndRecordOnTheSameWave() {
        val m = EndlessMilestones(19, 0b001, true)
        assertEquals(mapOf(20 to (TIER_1 or RECORD), 30 to TIER_2), run(m, 0, 35))
    }

    @Test fun wavesToRecordFromBestMinusFive() {
        val m = EndlessMilestones(41, 0, false)
        m.step(0)
        for (w in 1..45) {
            m.step(w)
            val expected = if (w in 36..41) 42 - w else 0
            assertEquals("fala $w", expected, m.wavesToRecord)
        }
        val none = EndlessMilestones(0, 0, false)
        none.step(0)
        none.step(1)
        assertEquals(0, none.wavesToRecord)
    }

    @Test fun resumeFromWave25DoesNotAnnounceBronzeOrSilver() {
        val m = EndlessMilestones(20, 0, true)
        // Wznowienie: pierwsze wywołanie od razu z falą 25 (rekord 20 pobity przed zabiciem procesu).
        assertEquals(mapOf(30 to TIER_2), run(m, 25, 40))
        assertEquals(0, m.recordEvents)
        val r = EndlessMilestones(30, 0, false)
        assertEquals(mapOf(31 to RECORD), run(r, 25, 40))
        assertEquals(6, EndlessMilestones(30, 0, false).also { it.step(25) }.wavesToRecord)
    }
}
