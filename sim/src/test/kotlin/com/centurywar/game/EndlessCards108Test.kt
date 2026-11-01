package com.centurywar.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Zlecenie 108: zdarzeniowe karty i nowe formacje fal, bez losowości poza Rng. */
class EndlessCards108Test {
    private fun battle(seed: Long = 108L) = Battle(BattleConfig(Mode.ENDLESS, seed, 1f))

    @Test fun newCardsRespectLimitsAndCannotBeReofferedWithoutEffect() {
        val b = battle()
        val p = b.player
        for (card in arrayOf(EndlessCard.FIELD_MEDICS, EndlessCard.FINAL_SPARK)) {
            repeat(EndlessCards.MAX_PERSISTENT_STACKS) {
                assertTrue(card.name, EndlessCards.canOffer(card, p))
                b.applyEndlessCard(p, card)
            }
            assertFalse(card.name, EndlessCards.canOffer(card, p))
        }
        for (card in arrayOf(EndlessCard.FIRST_GUARD, EndlessCard.BOSS_RECALL)) {
            assertTrue(EndlessCards.canOffer(card, p))
            b.applyEndlessCard(p, card)
            assertFalse(card.name, EndlessCards.canOffer(card, p))
            b.applyEndlessCard(p, card)
            assertEquals(1, p.cardStacks[card.ordinal])
        }
        assertEquals(EndlessCards.MAX_PERSISTENT_STACKS,
            p.cardStacks[EndlessCard.FIELD_MEDICS.ordinal])
    }

    @Test fun fieldMedicsHealOnlySurvivingWoundedAlliesAtNextWave() {
        val b = battle()
        assertTrue(b.spawn(b.player, b.player.unit(Role.MELEE)))
        val wounded = b.units.first { it.alive && it.side == 0 }
        wounded.hp = wounded.maxHp * 0.5f
        b.applyEndlessCard(b.player, EndlessCard.FIELD_MEDICS)
        b.waveTimer = 0f
        b.step()
        assertEquals(wounded.maxHp * 0.58f, wounded.hp, 0.02f)
        assertEquals(1, b.wave)
    }

    private fun firstHit(withGuard: Boolean): Float {
        val b = battle()
        if (withGuard) b.applyEndlessCard(b.player, EndlessCard.FIRST_GUARD)
        assertTrue(b.spawn(b.player, b.player.unit(Role.MELEE)))
        assertTrue(b.spawn(b.enemy, b.enemy.unit(Role.MELEE)))
        val ally = b.units.first { it.alive && it.side == 0 }
        val foe = b.units.first { it.alive && it.side == 1 }
        ally.x = Defs.LANE / 2f
        foe.x = ally.x + 15f
        ally.cd = 1000f
        foe.cd = 0f
        b.waveTimer = 900f
        val hp = ally.hp
        b.step()
        val loss = hp - ally.hp
        assertTrue("Przeciwnik musi trafić jednostkę", loss > 0f)
        return loss
    }

    @Test fun firstGuardBlocksFortyPercentOfFullHealthHit() {
        val normal = firstHit(false)
        val guarded = firstHit(true)
        assertEquals(normal * 0.6f, guarded, 0.003f)
    }

    @Test fun fallenUnitDamagesNearbyEnemiesWithoutHittingAllies() {
        val b = battle()
        b.applyEndlessCard(b.player, EndlessCard.FINAL_SPARK)
        assertTrue(b.spawn(b.player, b.player.unit(Role.MELEE)))
        assertTrue(b.spawn(b.enemy, b.enemy.unit(Role.MELEE)))
        val ally = b.units.first { it.alive && it.side == 0 }
        val foe = b.units.first { it.alive && it.side == 1 }
        ally.x = Defs.LANE / 2f
        foe.x = ally.x + 15f
        ally.hp = 0.01f
        ally.cd = 1000f
        foe.cd = 0f
        b.waveTimer = 900f
        val enemyHp = foe.hp
        b.step()
        assertFalse("Żołnierz powinien polec w ataku", ally.alive)
        assertTrue("Iskra powinna zranić przeciwnika", foe.hp < enemyHp)
        assertEquals(1, b.enemy.aliveCount)
    }

    @Test fun defeatingBossRefreshesBothCooldowns() {
        val b = battle()
        b.applyEndlessCard(b.player, EndlessCard.BOSS_RECALL)
        b.wave = 9
        b.nextBossToSpawn = 10
        b.waveTimer = 0f
        b.step()
        assertEquals(1, b.bossesSpawned)
        b.pendingCards?.let { b.chooseEndlessCard(0) }
        val boss = b.currentBoss()!!
        assertTrue(b.spawn(b.player, b.player.unit(Role.MELEE)))
        val ally = b.units.first { it.alive && it.side == 0 }
        boss.x = b.player.baseFront + 65f
        boss.hp = 0.01f
        boss.cd = 1000f
        ally.x = boss.x - 12f
        ally.cd = 0f
        b.player.abilityCd[0] = 12f
        b.player.abilityCd[1] = 19f
        b.waveTimer = 900f
        repeat(8) { if (b.bossesDefeated == 0) b.step() }
        assertEquals(1, b.bossesDefeated)
        assertEquals(0f, b.player.abilityCd[0], 0f)
        assertEquals(0f, b.player.abilityCd[1], 0f)
    }

    @Test fun themedFormationsKeepTheirOrderAndUseSeededThemes() {
        val b = battle()
        b.currentWaveTheme = EndlessWaveTheme.PIKE_SCREEN
        assertEquals(Role.HEAVY, b.waveRole(99, 0))
        assertEquals(Role.HEAVY, b.waveRole(0, 1))
        assertEquals(Role.RANGED, b.waveRole(79, 2))
        assertEquals(Role.MELEE, b.waveRole(80, 2))
        b.currentWaveTheme = EndlessWaveTheme.TWIN_RAID
        b.wave = 4
        assertEquals(Role.MELEE, b.waveRole(99, 0))
        assertEquals(Role.MELEE, b.waveRole(0, 1))
        assertEquals(Role.RANGED, b.waveRole(99, 2))
        assertEquals(Role.ELITE, b.waveRole(0, 3))
        b.wave = 3
        assertEquals(Role.RANGED, b.waveRole(0, 3))

        val a = Rng(10_108L)
        val other = Rng(10_108L)
        val sequence = List(300) { EndlessCards.nextTheme(a) }
        assertEquals(sequence, List(300) { EndlessCards.nextTheme(other) })
        assertTrue(EndlessWaveTheme.PIKE_SCREEN in sequence)
        assertTrue(EndlessWaveTheme.TWIN_RAID in sequence)
    }

    @Test fun newCardsAndWavesRemainDeterministicAcrossRuns() {
        fun simulate(): ByteArray {
            val b = battle(27108L)
            for (card in arrayOf(EndlessCard.FIELD_MEDICS, EndlessCard.FINAL_SPARK,
                EndlessCard.FIRST_GUARD, EndlessCard.BOSS_RECALL)) {
                b.applyEndlessCard(b.player, card)
            }
            repeat(4) { wave ->
                b.nextWaveTheme = if (wave % 2 == 0) EndlessWaveTheme.PIKE_SCREEN
                    else EndlessWaveTheme.TWIN_RAID
                b.waveTimer = 0f
                b.step()
            }
            repeat(180) { b.step() }
            return BattleSnapshot.encode(b)
        }
        assertTrue(simulate().contentEquals(simulate()))
    }
}
