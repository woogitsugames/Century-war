package com.centurywar.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R11-02, etapy 13–18 i etap 19 (dzień 35): kontrola determinizmu etapu 21 (stare zasady
 * presji, stageRamp bez zmian) i wznowienie przerwanej bitwy etapu 9. Do PROTOCOL 0.8.8
 * kontrola była na etapie 19; od 0.8.9 etap 19 jest lżejszy, więc przeniesiona na 21.
 */
class SecondHourControlTest {
    private fun expectedLine(stage: Int, seed: Long, limit: Int): String {
        val scenario = GoldenFixtures.Scenario("stage-$stage", limit) {
            Campaign.config(Campaign.stages[stage - 1], Bonuses(startGold = 350f), seed)
        }
        return GoldenFixtures.render(scenario).lines().first { "\"expected\"" in it }.trim()
    }

    /**
     * Wynik zapisany przy PROTOCOL 0.8.8: etap 21 ma nadal stare zasady presji i siłę AI.
     * Dzień 58: tylko skrót zrzutu zmieniony (format v21 z 4 nowymi stosami kart), reszta bez zmian.
     * Dzień 62: tylko skrót zrzutu (nagłówek v22), wynik, kroki i zabójstwa bez zmian.
     */
    @Test
    fun stageTwentyOneUnchanged() {
        assertEquals(STAGE_21_BEFORE, expectedLine(21, 91021L, 60 * 60 * 25))
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
        const val STAGE_21_BEFORE = "\"expected\": {\"score\":23482,\"winner\":1,\"wave\":0,\"steps\":36436," +
            "\"kills\":149,\"enemyKills\":297,\"timeBits\":1142411538," +
            "\"snapshotSha256\":\"7b40e2240e2c59a44b6d53c3e8a3c1a9b3c179d3a539dce485180415137220f6\"}"
    }
}
