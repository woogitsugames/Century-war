package com.centurywar.game

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Dzień 68: nowe style przeciwnika nie przelosowują starych seedów i przeżywają zapis bitwy. */
class AiPersonalityD68Test {
    /** Wzór losowania sprzed dnia 68 z jawnie zamrożoną piątką stylów. */
    private val frozen = listOf("ASSAULT", "ENGINEER", "ECONOMIST", "SIEGER", "BALANCED")

    private fun legacy(seed: Long, index: Int): String {
        val n = seed xor (seed ushr 32) xor (index.toLong() * 0x5DEECE66DL)
        return frozen[((n and Long.MAX_VALUE) % frozen.size).toInt()]
    }

    @Test fun newStylesAreAppendedAfterTheFrozenFive() {
        assertEquals(frozen, AiPersonality.LEGACY.map { it.name })
        assertEquals(frozen, AiPersonality.entries.take(5).map { it.name })
        assertEquals(listOf("SCREEN", "RAIDS", "CONTACT_RALLY"), AiPersonality.entries.drop(5).map { it.name })
    }

    @Test fun seedPersonalityOutsideSkirmishIsUnchanged() {
        for (seed in listOf(1L, 2L, 7L, 91001L, 123_456_789L, -5L, Long.MAX_VALUE) + (100L..400L)) {
            for (mode in listOf(Mode.CAMPAIGN, Mode.DAILY, Mode.ORDERS)) {
                val b = Battle(BattleConfig(mode, seed, 1f))
                assertEquals("$mode $seed", legacy(seed, 1), b.ai!!.personality.name)
                assertEquals("$mode $seed gracz", legacy(seed, 0), Ai(b, 0).personality.name)
            }
            // Gracz w potyczce (AI gracza w testach i powtórkach) też losuje z piątki.
            val s = Battle(BattleConfig(Mode.SKIRMISH, seed, 1f))
            assertEquals(legacy(seed, 0), Ai(s, 0).personality.name)
        }
    }

    @Test fun skirmishDrawsAllStyles() {
        val seen = HashSet<AiPersonality>()
        for (seed in 1L..400L) seen.add(Battle(BattleConfig(Mode.SKIRMISH, seed, 1f)).ai!!.personality)
        assertEquals(AiPersonality.entries.toSet(), seen)
    }

    @Test fun mirrorArmyDaysKeepFrozenDraw() {
        var checked = 0
        for (day in Daily.RELEASE_DAY until Daily.RELEASE_DAY + 2000L) {
            val cfg = Daily.config(day, Bonuses())
            if (cfg.dailyEvent != DailyEvent.MIRROR_ARMY) continue
            val expected = frozen[Rng(day * 1_000_003L + 71L).nextInt(frozen.size)]
            assertEquals("dzień $day", expected, cfg.aiPersonality!!.name)
            checked++
        }
        assertTrue(checked > 5)
    }

    @Test fun campaignStylesOnlyOnLateStages() {
        for (stage in Campaign.stages) {
            val cfg = Campaign.config(stage, Bonuses(), 5L)
            assertEquals(stage.personality, cfg.aiPersonality)
            if (stage.index < 8) assertNull("etap ${stage.index + 1}", stage.personality)
            // Samouczek nigdy nie dostaje stałego stylu.
            assertNull(Campaign.config(stage, Bonuses(), 5L, tutorial = true).aiPersonality)
        }
        val styled = Campaign.stages.mapNotNull { it.personality }
        assertEquals("każdy nowy styl dokładnie raz", AiPersonality.entries.drop(5), styled.sortedBy { it.ordinal })
        for ((number, style) in Campaign.STAGE_PERSONALITIES) {
            assertEquals(style, Battle(Campaign.config(Campaign.stages[number - 1], Bonuses(), 9L)).ai!!.personality)
        }
    }

    @Test fun newStylesSurviveSnapshotMidBattle() {
        for (style in AiPersonality.entries.drop(5)) for (seed in listOf(11L, 12L)) {
            val cfg = BattleConfig(Mode.SKIRMISH, seed, 1f, startEra = 1, maxEra = 2, aiPersonality = style)
            val a = Battle(cfg)
            a.playerAi = Ai(a, 0, AiPersonality.BALANCED)
            repeat(60 * 75) { if (!a.over) { a.step(); a.fx.count = 0 } }
            val restored = BattleSnapshot.decode(BattleSnapshot.encode(a))
            assertNotNull(restored)
            val b = restored!!.battle
            assertEquals(style, b.ai!!.personality)
            repeat(60 * 60) {
                if (!a.over) { a.step(); a.fx.count = 0 }
                if (!b.over) { b.step(); b.fx.count = 0 }
            }
            assertArrayEquals("$style $seed", BattleSnapshot.encode(a), BattleSnapshot.encode(b))
        }
    }

    @Test fun raidsReleaseSmallGroupsRepeatedly() {
        var releases = 0
        for (seed in 1L..6L) {
            val b = Battle(BattleConfig(Mode.SKIRMISH, seed, 1f, startEra = 2, maxEra = 2,
                aiPersonality = AiPersonality.RAIDS), commanderEnabled = false)
            b.playerAi = Ai(b, 0, AiPersonality.BALANCED)
            var holding = b.enemy.hold
            while (!b.over && b.time < 120f) {
                b.step(); b.fx.count = 0
                if (holding && !b.enemy.hold) releases++
                holding = b.enemy.hold
            }
        }
        assertTrue("Krótkie uderzenia: za mało wyjść grup ($releases)", releases >= 6 * 3)
    }

    @Test fun contactRallyNeverRalliesOnTheMarch() {
        for (seed in 1L..6L) {
            val b = Battle(BattleConfig(Mode.SKIRMISH, seed, 1f, startEra = 2, maxEra = 2,
                aiPersonality = AiPersonality.CONTACT_RALLY), commanderEnabled = false)
            b.playerAi = Ai(b, 0, AiPersonality.BALANCED)
            var used = b.enemy.ralliesUsed
            while (!b.over && b.time < 240f) {
                b.step(); b.fx.count = 0
                if (b.enemy.ralliesUsed > used) {
                    used = b.enemy.ralliesUsed
                    var near = 0
                    for (u in b.units) {
                        if (u.alive && u.side == 0 && kotlin.math.abs(u.x - b.enemy.front) < 260f) near++
                    }
                    val threatened = b.enemy.baseHp < b.enemy.baseMaxHp * 0.6f
                    assertTrue("Zryw bez kontaktu (seed $seed, t=${b.time})", near >= 1 || threatened)
                }
            }
        }
    }
}
