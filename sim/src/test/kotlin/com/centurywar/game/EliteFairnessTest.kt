package com.centurywar.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Uczciwe AI: w kampanii i potyczce elity wroga zależą od badania „Doktryna elit” gracza. */
class EliteFairnessTest {
    private fun research(elite: Boolean): Bonuses {
        val levels = IntArray(Research.defs.size)
        if (elite) levels[0] = 1
        return Research.bonuses(levels)
    }

    private fun play(cfg: BattleConfig): Battle {
        val b = Battle(cfg)
        b.playerAi = Ai(b, 0)
        var steps = 0
        while (!b.over && steps < 60 * 60 * 15) { b.step(); b.fx.count = 0; steps++ }
        return b
    }

    @Test fun campaignWithoutResearchEnemyNeverTrainsElite() {
        for (stageIndex in intArrayOf(9, 14, 20)) {
            val stage = Campaign.stages[stageIndex]
            for (seed in 1L..3L) {
                val b = Battle(Campaign.config(stage, research(false), seed))
                assertFalse(b.enemy.eliteUnlocked)
                assertFalse(b.canQueue(b.enemy, Role.ELITE))
                val played = play(Campaign.config(stage, research(false), seed))
                assertEquals("etap ${stageIndex + 1}, seed $seed", 0,
                    played.enemy.spawnedByRole[Role.ELITE.ordinal])
                assertEquals(0, played.player.spawnedByRole[Role.ELITE.ordinal])
            }
        }
    }

    @Test fun campaignWithResearchEnemyMayTrainElite() {
        var elites = 0
        for (stageIndex in intArrayOf(9, 14, 20)) {
            val stage = Campaign.stages[stageIndex]
            for (seed in 1L..3L) {
                val b = Battle(Campaign.config(stage, research(true), seed))
                assertTrue(b.enemy.eliteUnlocked)
                elites += play(Campaign.config(stage, research(true), seed))
                    .enemy.spawnedByRole[Role.ELITE.ordinal]
            }
        }
        assertTrue("Wróg z odblokowaną elitą powinien ją czasem szkolić", elites > 0)
    }

    @Test fun skirmishFollowsPlayerResearchButEndlessAndDailyKeepTheirRules() {
        val skirmish = Battle(BattleConfig(Mode.SKIRMISH, 7L, 1f, playerBonuses = research(false)))
        assertFalse(skirmish.enemy.eliteUnlocked)
        val endless = Battle(BattleConfig(Mode.ENDLESS, 7L, 1f, playerBonuses = research(false)))
        assertTrue(endless.enemy.eliteUnlocked)
        val daily = Battle(Daily.config(Daily.RELEASE_DAY + 1L, research(false)))
        assertTrue(daily.enemy.eliteUnlocked)
    }
}
