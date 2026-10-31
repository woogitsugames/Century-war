package com.centurywar.game

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EndlessCardsTest {

    private fun newBattle(seed: Long = 7L) = Battle(BattleConfig(Mode.ENDLESS, seed, 1f))

    @Test
    fun poolHasAtLeast25CardsAndTranslatedTexts() {
        assertTrue(EndlessCard.values().size >= 25)
        for (card in EndlessCard.values()) {
            assertTrue(EndlessTexts.title(card, true).isNotBlank())
            assertTrue(EndlessTexts.title(card, false).isNotBlank())
            assertTrue(EndlessTexts.description(card, true).isNotBlank())
            assertTrue(EndlessTexts.description(card, false).isNotBlank())
        }
    }

    @Test
    fun cardsApplyTheirModifiers() {
        val b = newBattle()
        val p = b.player
        val base = p.baseHp
        b.applyEndlessCard(p, EndlessCard.BLOOD_GOLD)
        assertEquals(base * 0.8f, p.baseHp, 0.01f)
        assertEquals(1.5f, p.incomeMul, 0.001f)
        assertEquals(1.5f, p.killGoldMul, 0.001f)

        b.applyEndlessCard(p, EndlessCard.TOWER_SLOT)
        assertEquals(3, p.slots)
        b.applyEndlessCard(p, EndlessCard.BURNING_TOWERS)
        assertEquals(0.25f, p.turretBurnRatio, 0.001f)
        b.applyEndlessCard(p, EndlessCard.AUTO_BARRAGE)
        assertEquals(30f, p.autoBarrageInterval, 0.001f)
        b.applyEndlessCard(p, EndlessCard.CLONING)
        assertEquals(0.1f, p.cloneChance, 0.001f)
        b.applyEndlessCard(p, EndlessCard.ECONOMY)
        assertTrue(Doctrine.ECONOMY in p.doctrines)
        assertEquals(1, p.cardStacks[EndlessCard.ECONOMY.ordinal])
        assertEquals(1, p.cardStacks[EndlessCard.BLOOD_GOLD.ordinal])
    }

    @Test
    fun baseHpCardEffectsSurviveEraAdvancement() {
        val b = newBattle()
        val p = b.player
        b.applyEndlessCard(p, EndlessCard.BLOOD_GOLD)
        assertEquals(0.8f, p.baseHpMul, 0.001f)
        val ratioBefore = p.baseHp / p.baseMaxHp
        p.xp = Defs.evolveXp[0].toFloat()
        assertTrue(b.evolve(p))
        assertEquals(Defs.baseHp(1) * 0.8f, p.baseMaxHp, 0.01f)
        assertTrue(p.baseHp / p.baseMaxHp >= ratioBefore)

        b.chooseDoctrine(0)
        b.applyEndlessCard(p, EndlessCard.FORTRESS)
        assertEquals(1f, p.baseHpMul, 0.001f)
    }

    @Test
    fun freeEliteArrivesWithNewWaveAndCloningCreatesCopy() {
        val b = newBattle()
        val p = b.player
        b.applyEndlessCard(p, EndlessCard.FREE_ELITE)
        b.waveTimer = 0f
        b.step()
        assertEquals(1, p.spawned)
        assertEquals(Role.ELITE, b.units.first { it.alive && it.side == 0 }.def.role)

        b.applyEndlessCard(p, EndlessCard.CLONING)
        p.cloneChance = 1f // Wymuszona szansa pozwala sprawdzić ścieżkę sukcesu.
        assertTrue(b.queueUnit(p, Role.MELEE))
        p.trainT = Defs.unit(p.era, Role.MELEE).trainTime
        b.step()
        assertEquals(3, p.spawned)
    }

    @Test
    fun burningTurretAppliesDamageOverTime() {
        val b = newBattle()
        val p = b.player
        b.applyEndlessCard(p, EndlessCard.BURNING_TOWERS)
        assertTrue(b.buyTurret(p, 0))
        b.spawn(b.enemy, Defs.unit(b.enemy.era, Role.HEAVY))
        val foe = b.units.first { it.alive && it.side == 1 }
        foe.x = p.baseFront + 170f
        foe.hp = foe.maxHp
        b.waveTimer = 900f
        var ignited = false
        repeat(110) {
            b.step()
            if (foe.burnLeft > 0f) ignited = true
        }
        assertTrue("Pocisk wieży powinien podpalić przeciwnika", ignited)
        assertTrue("Cel powinien stracić HP", foe.hp < foe.maxHp)
    }

    @Test
    fun autoBarrageDoesNotBlockManualBarrage() {
        val b = newBattle()
        val p = b.player
        b.applyEndlessCard(p, EndlessCard.AUTO_BARRAGE)
        b.waveTimer = 900f
        p.autoBarrageTimer = Battle.STEP / 2f
        val before = p.abilityCd[0]
        b.step()
        assertEquals(before, p.abilityCd[0], 0.001f)
        assertTrue("Automatyczny ostrzał musi użyć puli pocisków",
            b.projs.any { it.active && it.side == 0 })
        assertTrue(b.castBarrage(p))
    }

    @Test
    fun everyFifthWaveOffersThreeDistinctCards() {
        val b = newBattle()
        repeat(5) {
            b.waveTimer = 0f
            b.step()
            b.fx.count = 0
        }
        assertEquals(5, b.wave)
        val cards = b.pendingCards
        assertNotNull(cards)
        assertNotNull(b.pendingChoice) // Dotychczasowy ekran nadal widzi modal.
        assertEquals(3, cards!!.distinct().size)
        val chosen = cards[1]
        assertTrue(b.chooseEndlessCard(1))
        assertNull(b.pendingCards)
        assertNull(b.pendingChoice)
        assertEquals(1, b.player.cardStacks[chosen.ordinal])
        assertFalse(b.chooseEndlessCard(0))
    }

    @Test
    fun bossAppearsEveryTenWavesAndGivesExtraCardOnDefeat() {
        val b = newBattle()
        b.playerAi = Ai(b, 0)
        repeat(9) {
            b.waveTimer = 0f
            b.step()
        }
        assertEquals(0, b.bossesSpawned)
        assertEquals("Nadciąga boss", b.nextWaveNamePl)
        b.waveTimer = 0f
        b.step()
        assertEquals(10, b.wave)
        assertEquals(1, b.bossesSpawned)
        val boss = b.units.first { it.alive && it.isBoss }
        assertEquals(Role.ELITE, boss.def.role)
        assertTrue(boss.maxHp > boss.def.hp * b.enemy.hpMul * 3f)

        // Bezpośrednie starcie pozwala sprawdzić nagrodę bez przewijania minut symulacji.
        boss.x = b.player.baseFront + 60f
        boss.hp = 0.1f
        assertTrue(b.spawn(b.player, Defs.unit(b.player.era, Role.MELEE)))
        val soldier = b.units.first { it.alive && it.side == 0 }
        soldier.x = b.player.baseFront + 22f
        soldier.cd = 0f
        val cardsBefore = b.player.cardStacks.sum()
        b.waveTimer = 900f
        repeat(4) { b.step() }
        assertEquals(1, b.bossesDefeated)
        assertEquals(cardsBefore + 1, b.player.cardStacks.sum())
        assertTrue(b.score() >= 10 * 250L + 1500L)

        repeat(10) {
            b.waveTimer = 0f
            b.step()
        }
        assertEquals(20, b.wave)
        assertEquals(2, b.bossesSpawned)
    }

    @Test
    fun cardOffersBossesAndThemesAreDeterministicForSameSeed() {
        fun run(seed: Long): Battle {
            val b = newBattle(seed)
            repeat(25) {
                b.waveTimer = 0f
                b.step()
                if (b.pendingCards != null) b.chooseEndlessCard(1)
            }
            return b
        }
        val a = run(2026L)
        val c = run(2026L)
        assertEquals(a.wave, c.wave)
        assertEquals(a.score(), c.score())
        assertEquals(a.bossesSpawned, c.bossesSpawned)
        assertEquals(a.bossesDefeated, c.bossesDefeated)
        assertEquals(a.nextWaveTheme, c.nextWaveTheme)
        assertEquals(a.currentWaveTheme, c.currentWaveTheme)
        assertArrayEquals(a.player.cardStacks, c.player.cardStacks)
        assertEquals(a.enemy.queueSize, c.enemy.queueSize)
        assertEquals(a.player.baseHp, c.player.baseHp, 0.001f)
    }
}
