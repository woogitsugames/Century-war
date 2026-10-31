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
        Role::class.java, Int::class.javaPrimitiveType!!, Boolean::class.javaPrimitiveType!!,
        Boolean::class.javaPrimitiveType!!
    ).apply { isAccessible = true }

    private fun hit(b: Battle, u: Fighter, amount: Float, role: Role = Role.MELEE,
                    projectile: Boolean = false) {
        damage.invoke(b, u, amount, 0, role, -1, true, projectile)
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

    @Test fun knockbackPushesTargetOnItsCooldown() {
        val b = bossAt(10)
        b.chooseEndlessCard(0)
        val boss = b.currentBoss()!!
        b.enemy.queueSize = 0
        assertTrue(b.spawn(b.player, Defs.unit(b.player.era, Role.MELEE)))
        val soldier = b.units.first { it.alive && it.side == 0 }
        soldier.x = boss.x - boss.radius - soldier.radius
        soldier.hp = 1_000_000f
        soldier.maxHp = soldier.hp
        boss.bossCd = 0f
        val before = soldier.x
        b.waveTimer = 800f
        b.step()
        assertTrue("Odrzut powinien cofnąć jednostkę", soldier.x < before - 30f)
        assertTrue("Odrzut włącza odnowienie", boss.bossCd > 5f)
    }

    @Test fun auraAcceleratesAlliesIncludingSecondaryAura() {
        for (w in intArrayOf(20, 60)) {
            val b = bossAt(w)
            b.chooseEndlessCard(0)
            b.enemy.queueSize = 0
            assertTrue(b.spawn(b.enemy, Defs.unit(b.enemy.era, Role.MELEE)))
            val boss = b.currentBoss()!!
            val ally = b.units.first { it.alive && it.side == 1 && !it.isBoss }
            ally.x = boss.x - 45f
            ally.cd = 100f
            b.waveTimer = 800f
            val plain = BattleSnapshot.decode(BattleSnapshot.encode(b))!!.battle
            plain.currentBoss()!!.bossKind = EndlessBosses.SHIELD
            plain.currentBoss()!!.bossSecondaryKind = EndlessBosses.NONE
            b.step()
            plain.step()
            val allyWithoutAura = plain.units.first { it.alive && it.side == 1 && !it.isBoss }
            assertTrue("Aura musi przyspieszać ataki sojuszników", ally.cd < allyWithoutAura.cd)
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

    @Test fun rageBoostsDamageAndAttackRateWithoutChangingMovementSpeed() {
        val b = bossAt(50)
        b.chooseEndlessCard(0)
        b.enemy.queueSize = 0
        val boss = b.currentBoss()!!
        boss.x = b.player.baseFront + boss.radius
        boss.prevX = Float.NaN
        boss.bossRageActive = true
        boss.hp = boss.maxHp * 0.4f
        boss.cd = 0f
        b.player.baseMaxHp = 100_000_000f
        b.player.baseHp = b.player.baseMaxHp
        b.waveTimer = 800f
        val plain = BattleSnapshot.decode(BattleSnapshot.encode(b))!!.battle
        val regular = plain.currentBoss()!!
        regular.bossRageActive = false
        b.step()
        plain.step()
        if (boss.def.projectile == Proj.NONE) {
            val ragingDmg = 100_000_000f - b.player.baseHp
            val normalDmg = 100_000_000f - plain.player.baseHp
            assertEquals(normalDmg * 1.5f, ragingDmg, normalDmg * 0.01f)
        } else {
            val ragingShot = b.projs.first { it.active && it.side == 1 }
            val normalShot = plain.projs.first { it.active && it.side == 1 }
            assertEquals(normalShot.dmg * 1.5f, ragingShot.dmg, 0.01f)
        }
        // Osobny krok z długim cooldownem: dotyczy tempa ataku, a nie marszu.
        boss.cd = 100f
        regular.cd = 100f
        boss.x = b.player.baseFront + 200f
        regular.x = plain.player.baseFront + 200f
        boss.prevX = Float.NaN
        regular.prevX = Float.NaN
        b.step()
        plain.step()
        assertTrue(boss.cd < regular.cd)
        assertEquals(boss.x, regular.x, 0.01f)
    }

    @Test fun secondaryRecruiterAndRageAreEffective() {
        val recruits = bossAt(80)
        recruits.chooseEndlessCard(0)
        val carrier = recruits.currentBoss()!!
        assertTrue(carrier.hasBossAbility(EndlessBosses.RECRUITER))
        carrier.bossSummonCd = 0f
        recruits.enemy.queueSize = 0
        recruits.waveTimer = 800f
        val initial = recruits.enemy.aliveCount
        recruits.step()
        assertEquals(initial + 2, recruits.enemy.aliveCount)

        val fury = bossAt(90)
        fury.chooseEndlessCard(0)
        val leader = fury.currentBoss()!!
        assertTrue(leader.hasBossAbility(EndlessBosses.RAGE))
        leader.hp = leader.maxHp * 0.51f
        hit(fury, leader, leader.maxHp * 0.1f)
        assertTrue(leader.bossRageActive)
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

    /** Pomiar 10 seedów: wynik drukowany do raportu CI, bez fikcyjnej bazy odniesienia. */
    @Test fun tenSeedAiWaveReport() {
        val waves = IntArray(10)
        for (i in waves.indices) {
            val b = Battle(BattleConfig(Mode.ENDLESS, 1001L + i, 1f))
            b.playerAi = Ai(b, 0)
            var steps = 0
            while (!b.over && steps < 60_000) {
                b.step()
                b.fx.count = 0
                steps++
            }
            waves[i] = b.wave
            println("Endless seed=${1001 + i} wave=${b.wave} over=${b.over} steps=$steps")
            assertTrue("Gracz AI nie doszedł do piątej fali (seed ${1001 + i})", b.wave >= 5)
        }
        println("Endless 10 seedów: średnia=${waves.average()}, fale=${waves.joinToString()}")
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
