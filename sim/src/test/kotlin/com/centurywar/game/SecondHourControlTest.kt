package com.centurywar.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** R11-02: kontrola determinizmu etapu 13 i wznowienie przerwanej bitwy etapu 9. */
class SecondHourControlTest {
    private fun expectedLine(stage: Int, seed: Long, limit: Int): String {
        val scenario = GoldenFixtures.Scenario("stage-$stage", limit) {
            Campaign.config(Campaign.stages[stage - 1], Bonuses(startGold = 350f), seed)
        }
        return GoldenFixtures.render(scenario).lines().first { "\"expected\"" in it }.trim()
    }

    /** Wynik zapisany przed R11-02 (PROTOCOL 0.8.6): etap 13 ma nadal stare zasady presji. */
    @Test
    fun stageThirteenUnchanged() {
        assertEquals(STAGE_13_BEFORE, expectedLine(13, 91013L, 60 * 60 * 14))
    }

    @Test
    fun stageNineSnapshotResumesOnNewRules() {
        val battle = Battle(Campaign.config(Campaign.stages[8], Bonuses(), 91009L))
        repeat(60 * 60 * 4) { battle.step(); battle.fx.count = 0 }
        assertFalse(battle.over)
        val bytes = BattleSnapshot.encode(battle)
        assertEquals(SnapshotReject.OK, BattleSnapshot.probe(bytes))
        val restored = BattleSnapshot.decode(bytes)!!.battle
        assertEquals(Campaign.SECOND_SIEGE_START, restored.siegeStart)
        assertEquals(Campaign.SECOND_SIEGE_RAMP, restored.siegeRamp)
        while (!restored.over && restored.time < 30 * 60f) { restored.step(); restored.fx.count = 0 }
        assertTrue(restored.over)
    }

    companion object {
        const val STAGE_13_BEFORE = "\"expected\": {\"score\":10898,\"winner\":1,\"wave\":0,\"steps\":45449," +
            "\"kills\":263,\"enemyKills\":336,\"timeBits\":1144872688," +
            "\"snapshotSha256\":\"5107928f35b7935c7ab4bdd8ba33bf954b4e8a5d622015b332bc12812ff32765\"}"
    }
}
