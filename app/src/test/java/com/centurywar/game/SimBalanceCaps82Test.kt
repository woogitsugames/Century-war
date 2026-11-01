package com.centurywar.game

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Limity i wygaszanie wielokrotnych kart bez zmiany kolejności enumów. */
class SimBalanceCaps82Test {
    private fun endless(seed: Long = 82L, bonuses: Bonuses = Bonuses()) =
        Battle(BattleConfig(Mode.ENDLESS, seed, 1f, playerBonuses = bonuses))

    @Test fun cooldownFromResearchCardsAndDailyEventHasHardFloor() {
        val combined = Battle(BattleConfig(Mode.ENDLESS, 82L, 1f,
            playerBonuses = Bonuses(abilityCdMul = 0.05f),
            dailyEvent = DailyEvent.RAPID_ABILITIES))
        assertEquals(Battle.MIN_ABILITY_CD_MUL, combined.player.abilityCdMul, 0.00001f)
        assertFalse(EndlessCards.canOffer(EndlessCard.TACTICS, combined.player))
        assertFalse(EndlessCards.canOffer(EndlessCard.RALLY_MASTER, combined.player))

        val b = endless()
        val p = b.player
        repeat(30) {
            b.applyEndlessCard(p, EndlessCard.TACTICS)
            b.applyEndlessCard(p, EndlessCard.RALLY_MASTER)
        }
        assertEquals(Battle.MIN_ABILITY_CD_MUL, p.abilityCdMul, 0.00001f)
        assertTrue(b.castBarrage(p))
        assertEquals(Battle.BARRAGE_CD * Battle.MIN_ABILITY_CD_MUL, p.abilityCd[0], 0.0001f)
        assertTrue(b.castRally(p))
        assertEquals(Battle.RALLY_CD * Battle.MIN_ABILITY_CD_MUL, p.abilityCd[1], 0.0001f)

        val restored = BattleSnapshot.decode(BattleSnapshot.encode(b))
        assertNotNull(restored)
        assertEquals(p.abilityCdMul, restored!!.battle.player.abilityCdMul, 0f)
        assertArrayEquals(p.cardStacks, restored.battle.player.cardStacks)
    }

    @Test fun exhaustedCardsAndUnavailableAbilitiesAreNotOffered() {
        val b = endless()
        val p = b.player
        assertFalse(EndlessCards.canOffer(EndlessCard.MEDIC, p))
        assertTrue(EndlessCards.canOffer(EndlessCard.MEDIC, p.apply { baseHp -= 1f }))
        p.cloneChance = 0.6f
        p.freeElitesPerWave = 4
        p.repairPerWave = 0.3f
        p.cap = Battle.MAX_UNITS
        p.autoBarrageInterval = 15f
        p.abilityCdMul = Battle.MIN_ABILITY_CD_MUL
        p.slots = 0
        p.rallyUnlocked = false
        for (c in arrayOf(EndlessCard.CLONING, EndlessCard.FREE_ELITE,
            EndlessCard.REPAIR_CREW, EndlessCard.CITADEL_HEART,
            EndlessCard.MOBILIZATION, EndlessCard.MUSTER_BANNER,
            EndlessCard.AUTO_BARRAGE, EndlessCard.RALLY_MASTER,
            EndlessCard.TACTICS, EndlessCard.BANNER, EndlessCard.ARSENAL,
            EndlessCard.VIGILANCE, EndlessCard.BURNING_TOWERS)) {
            assertFalse(c.name, EndlessCards.canOffer(c, p))
        }
        assertTrue(EndlessCards.canOffer(EndlessCard.VIGILANCE, p, fog = true))
        val picks = Array(3) { EndlessCard.ECONOMY }
        for (seed in 1L..60L) {
            EndlessCards.draw(Rng(seed), p, picks)
            assertEquals(3, picks.distinct().size)
            for (c in picks) assertTrue(c.name, EndlessCards.canOffer(c, p))
        }
    }

    @Test fun repeatedPersistentCardsHaveDiminishingReturnsAndStopAtEight() {
        val cases = arrayOf(
            EndlessCard.BERSERK, EndlessCard.FRONTLINE_PLATE,
            EndlessCard.FORTRESS, EndlessCard.LAST_STAND, EndlessCard.BURNING_TOWERS,
        )
        for (card in cases) {
            val b = endless()
            val p = b.player
            for (i in 1..EndlessCards.MAX_PERSISTENT_STACKS) {
                assertTrue(card.name, EndlessCards.canOffer(card, p))
                b.applyEndlessCard(p, card)
                assertEquals(i, p.cardStacks[card.ordinal])
            }
            assertFalse(EndlessCards.canOffer(card, p))
            b.applyEndlessCard(p, card)
            assertEquals(EndlessCards.MAX_PERSISTENT_STACKS, p.cardStacks[card.ordinal])
        }
        assertEquals(1f, EndlessCards.stackPower(1), 0f)
        assertEquals(0.6f, EndlessCards.stackPower(2), 0f)
        assertEquals(0.4f, EndlessCards.stackPower(3), 0f)
        assertEquals(2.8f, EndlessCards.totalStackPower(100), 0.00001f)

        val b = endless()
        val p = b.player
        b.applyEndlessCard(p, EndlessCard.BERSERK)
        assertEquals(1.3f, p.dmgMul, 0.00001f)
        b.applyEndlessCard(p, EndlessCard.BERSERK)
        assertEquals(1.3f * 1.18f, p.dmgMul, 0.00001f)
        b.applyEndlessCard(p, EndlessCard.BERSERK)
        assertEquals(1.3f * 1.18f * 1.12f, p.dmgMul, 0.00001f)

        val burn = endless()
        repeat(20) { burn.applyEndlessCard(burn.player, EndlessCard.BURNING_TOWERS) }
        assertEquals(0.7f, burn.player.turretBurnRatio, 0.0001f)
    }

    @Test fun doctrineOffersExcludeLockedRallyAndMeaninglessSight() {
        val b = Battle(BattleConfig(Mode.SKIRMISH, 82L, 1f,
            startEra = 0, maxEra = 1, variantsEnabled = false,
            playerBonuses = Bonuses(rallyUnlocked = false, turretSlots = 0)))
        b.player.xp = Defs.evolveXp[0].toFloat()
        assertTrue(b.evolve(b.player))
        val offer = b.pendingChoice
        assertNotNull(offer)
        assertFalse(Doctrine.BANNER in offer!!)
        assertFalse(Doctrine.VIGILANCE in offer)
        assertFalse(Doctrine.ARSENAL in offer)
    }

    @Test fun supplyGrantsScaledGoldImmediatelyAndKeepsTemporaryKillBonus() {
        val b = endless()
        val p = b.player
        val before = p.gold
        b.applyDoctrine(p, Doctrine.SUPPLY)
        assertEquals(before + 250f * Defs.costScale(p.era), p.gold, 0.0001f)
        assertEquals(60f, p.supplyUntil, 0.0001f)
    }

    @Test fun economySupplyLineSynergyRemainsIndependentOfCardOrder() {
        val one = endless()
        val two = endless()
        repeat(2) { one.applyEndlessCard(one.player, EndlessCard.ECONOMY) }
        repeat(2) { one.applyEndlessCard(one.player, EndlessCard.SUPPLY_LINE) }
        repeat(2) { two.applyEndlessCard(two.player, EndlessCard.SUPPLY_LINE) }
        repeat(2) { two.applyEndlessCard(two.player, EndlessCard.ECONOMY) }
        assertEquals(one.player.incomeMul, two.player.incomeMul, 0.0001f)
    }
}
