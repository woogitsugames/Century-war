package com.centurywar.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R11-02 i etapy 13–18: kontrola determinizmu etapu 19 (stare zasady presji) i wznowienie
 * przerwanej bitwy etapu 9.
 */
class SecondHourControlTest {
    private fun expectedLine(stage: Int, seed: Long, limit: Int): String {
        val scenario = GoldenFixtures.Scenario("stage-$stage", limit) {
            Campaign.config(Campaign.stages[stage - 1], Bonuses(startGold = 350f), seed)
        }
        return GoldenFixtures.render(scenario).lines().first { "\"expected\"" in it }.trim()
    }

    /** Wynik zapisany przy PROTOCOL 0.8.7: etap 19 ma nadal stare zasady presji. */
    @Test
    fun stageNineteenUnchanged() {
        assertEquals(STAGE_19_BEFORE, expectedLine(19, 91019L, 60 * 60 * 25))
    }

    @Test
    fun stageNineSnapshotResumesOnNewRules() {
        val battle = Battle(Campaign.config(Campaign.stages[8], Bonuses(), 91009L))
        repeat(60 * 60 * 2) { battle.step(); battle.fx.count = 0 }
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
        const val STAGE_19_BEFORE = "\"expected\": {\"score\":18985,\"winner\":1,\"wave\":0,\"steps\":32345," +
            "\"kills\":142,\"enemyKills\":304,\"timeBits\":1141294422," +
            "\"snapshotSha256\":\"6f1a86c91a824604babed2436184678064a887dffa7e5dfff01fd631fb785a35\"}"
    }
}
