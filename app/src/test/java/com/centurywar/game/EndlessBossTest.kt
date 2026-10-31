package com.centurywar.game

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Rotacja, umiejętności i deterministyczność bossów Endless. */
class EndlessBossTest {
    private val damage = Battle::class.java.getDeclaredMethod("damageUnit",
        Fighter::class.java, Float::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!,
        Role::class.java, Int::class.javaPrimitiveType!!, Boolean::class.javaPrimitiveType!!
    ).apply { isAccessible = true }

    private fun hit(b: Battle, u: Fighter, amount: Float, role: Role = Role.MELEE,
                    projectile: Boolean = false) {
        damage.invoke(b, u, amount, 0, role, -1, projectile)
    }

    private fun bossAt(w: Int): Battle {
        val b = Battle(BattleConfig(Mode.ENDLESS, w.toLong() + 67L, 1f))
        while (b.wave < w) {
            if (b.pendingCards != null) b.chooseEndlessCard(0)
            // Testuje dyrektora, nie wynik długiej bitwy. Zwalnia pulę między falami.
            for (u in b.units) u.active = false
            b.player.aliveCount = 0
            b.enemy.aliveCount = 0
            b.enemy.queueSize = 0
            b.waveTimer = 0f
            b.step()
        }
        assertNotNull("Boss w fali $w", b.currentBoss())
        return b
    }

    @Test fun bossesRotateByWaveWithoutRandomDraws() {
        assertEquals(EndlessBosses.NONE, EndlessBosses.kindAtWave(9))
        assertEquals(EndlessBosses.NONE, EndlessBosses.kindAtWave(11))
        for (n in 1..12) {
            val w = n * 10
            val b = bossAt(w)
            val u = b.currentBoss()!!
            assertEquals((n - 1) % 5 + 1, u.bossKind)
            assertEquals(if (w >= 60) n % 5 + 1 else 0, u.bossSecondaryKind)
            assertEquals(u.bossKind, EndlessBosses.kindAtWave(w))
            assertEquals(u.bossSecondaryKind, EndlessBosses.secondaryAtWave(w))
            assertEquals(n, b.bossesSpawned)
        }
    }

    @Test fun shieldProtectsAgainstProjectilesOnlyAboveHalfHp() {
        val b = bossAt(30)
        val u = b.currentBoss()!!
        val base = u.maxHp
        u.hp = base
        hit(b, u, 30f, Role.RANGED, false)
        val fullHit = base - u.hp
        u.hp = base
        hit(b, u, 30f, Role.RANGED, true)
        assertEquals(fullHit * 0.5f, base - u.hp, 0.001f)

        u.hp = base * 0.48f
        hit(b, u, 30f, Role.RANGED, true)
        assertEquals(fullHit, base * 0.48f - u.hp, 0.001f)

        u.hp = base
        hit(b, u, 30f, Role.MELEE, false)
        assertTrue(base - u.hp > 0f)
        assertFalse(u.bossRageActive)
    }

    @Test fun recruiterHonorsSideCapAndObjectPool() {
        val b = bossAt(40)
        val u = b.currentBoss()!!
        b.chooseEndlessCard(0)
        b.enemy.queueSize = 0
        b.enemy.cap = b.enemy.aliveCount + 1
        u.bossSummonCd = 0f
        b.waveTimer = 800f
        val before = b.enemy.aliveCount
        b.step()
        assertEquals(before + 1, b.enemy.aliveCount)
        val recruits = b.units.filter { it.alive && it.side == 1 && !it.isBoss }
        assertEquals(1, recruits.size)
        assertEquals(Role.MELEE, recruits.first().def.role)
        assertTrue(kotlin.math.abs(recruits.first().x - u.x) < 100f)
        u.bossSummonCd = 0f
        b.step()
        assertEquals(before + 1, b.enemy.aliveCount)

        // Pula pełna, choć limit strony jest wyższy: bez nadpisania żywych obiektów.
        b.enemy.cap = Battle.MAX_UNITS
        for (other in b.units) if (!other.active) {
            other.active = true
            other.dying = 0f
        }
        u.bossSummonCd = 0f
        b.step()
        assertEquals(before + 1, b.enemy.aliveCount)
        assertEquals(Battle.MAX_UNITS, b.units.count { it.active })
    }

    @Test fun rageTurnsOnOnceAndEmitsOneEffect() {
        val b = bossAt(50)
        val u = b.currentBoss()!!
        b.chooseEndlessCard(0)
        b.fx.count = 0
        u.hp = u.maxHp * 0.51f
        hit(b, u, u.maxHp * 0.1f)
        assertTrue(u.bossRageActive)
        val count = b.fx.count
        assertEquals(1, count)
        hit(b, u, 1f)
        assertEquals(count, b.fx.count)
        assertTrue(u.alive)
    }

    @Test fun secondAbilityWorksAndBossDefeatStillGivesCard() {
        val b = bossAt(60)
        val u = b.currentBoss()!!
        assertTrue(u.hasBossAbility(EndlessBosses.KNOCKBACK))
        assertTrue(u.hasBossAbility(EndlessBosses.AURA))
        assertFalse(u.hasBossAbility(EndlessBosses.RECRUITER))
        b.chooseEndlessCard(0)
        u.hp = 0.1f
        hit(b, u, 1f)
        assertEquals(1, b.bossesDefeated)
        assertEquals(1, b.bossRewardsPending)
        val score = b.score()
        assertTrue(score >= b.wave * 250L + 1500L)
        b.waveTimer = 800f
        b.step()
        assertNotNull(b.pendingCards)
    }

    @Test fun newThemesAdjustStatsAndKeepOldOrdinals() {
        assertEquals(6, EndlessWaveTheme.SIEGE_TRAIN.ordinal)
        assertEquals(7, EndlessWaveTheme.SKIRMISHERS.ordinal)
        val siege = Battle(BattleConfig(Mode.ENDLESS, 81L, 1f))
        siege.nextWaveTheme = EndlessWaveTheme.SIEGE_TRAIN
        siege.waveTimer = 0f
        siege.step()
        assertEquals(0.85f, siege.enemy.speedMul, 0.0001f)
        val swarm = Battle(BattleConfig(Mode.ENDLESS, 81L, 1f))
        swarm.nextWaveTheme = EndlessWaveTheme.SKIRMISHERS
        swarm.waveTimer = 0f
        swarm.step()
        assertEquals(1.05f * 0.8f, swarm.enemy.hpMul, 0.0001f)
    }

    @Test fun tenSeedsRemainByteIdenticalAfterIdenticalCommands() {
        for (seed in 1L..10L) {
            fun play(): Battle {
                val b = Battle(BattleConfig(Mode.ENDLESS, seed, 1f))
                repeat(65) {
                    if (b.pendingCards != null) b.chooseEndlessCard(0)
                    b.waveTimer = 0f
                    b.step()
                    b.fx.count = 0
                }
                return b
            }
            val a = play()
            val b = play()
            assertEquals(a.score(), b.score())
            assertArrayEquals(BattleSnapshot.encode(a), BattleSnapshot.encode(b))
        }
    }
}
