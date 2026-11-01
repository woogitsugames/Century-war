package com.centurywar.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CampaignDifficultyTest {
    @Test
    fun exactMultipliersPreserveNormalStageRamp() {
        for (stage in Campaign.stages) {
            val normal = Campaign.config(stage, Bonuses(), 123L)
            assertEquals(stage.aiPower, normal.aiPower, 0f)
            assertEquals(CampaignDifficulty.NORMAL, Campaign.difficulty(normal))
            val easy = Campaign.config(stage, Bonuses(), 123L, difficulty = CampaignDifficulty.EASY)
            val hard = Campaign.config(stage, Bonuses(), 123L, difficulty = CampaignDifficulty.HARD)
            assertEquals(stage.aiPower * 0.8f, easy.aiPower, 0f)
            assertEquals(stage.aiPower * 1.2f, hard.aiPower, 0f)
            assertEquals(CampaignDifficulty.EASY, Campaign.difficulty(easy))
            assertEquals(CampaignDifficulty.HARD, Campaign.difficulty(hard))
            assertTrue(easy.aiPower < normal.aiPower && normal.aiPower < hard.aiPower)
        }
        assertNull(Campaign.difficulty(BattleConfig(Mode.SKIRMISH, 42L, 1f)))
    }

    @Test
    fun tutorialKeepsOriginalReductionAtNormalAndScalesBothOtherDifficulties() {
        val stage = Campaign.stages[0]
        for (diff in CampaignDifficulty.entries) {
            val cfg = Campaign.config(stage, Bonuses(), 23L, tutorial = true, difficulty = diff)
            assertEquals(stage.aiPower * diff.powerMultiplier * 0.8f, cfg.aiPower, 0f)
            assertEquals(diff, Campaign.difficulty(cfg))
        }
        assertEquals(stage.aiPower * 0.8f,
            Campaign.config(stage, Bonuses(), 23L, tutorial = true).aiPower, 0f)
    }

    @Test
    fun localReplayAndSnapshotPreservePowerAndVerification() {
        for (difficulty in CampaignDifficulty.entries) {
            val cfg = Campaign.config(Campaign.stages[0], Bonuses(), 12345L, difficulty = difficulty)
            val battle = Battle(cfg)
            val controller = BattleController(battle)
            assertTrue(controller.queue(Role.MELEE))
            repeat(90) {
                if (!battle.over) {
                    battle.step()
                    battle.fx.count = 0
                }
            }
            if (!battle.over) assertTrue(controller.surrender())
            val log = controller.log()
            val meta = ReplayRecord.Meta(Mode.CAMPAIGN, 0, battle.winner, battle.score(),
                battle.time, battle.wave, 1793461200000L, "Dowódca")
            val decoded = ReplayRecord.decode(ReplayRecord.encode(cfg, log, meta))
            assertNotNull(decoded.log)
            assertEquals(cfg.aiPower, decoded.cfg.aiPower, 0f)
            assertEquals(difficulty, Campaign.difficulty(decoded.cfg))
            assertEquals(battle.score(), Replay.verify(decoded.cfg, decoded.log!!).score)
            val restored = BattleSnapshot.decode(BattleSnapshot.encode(battle))
            assertNotNull(restored)
            assertEquals(cfg.aiPower, restored!!.battle.cfg.aiPower, 0f)
            assertEquals(difficulty, Campaign.difficulty(restored.battle.cfg))
        }
    }
}
