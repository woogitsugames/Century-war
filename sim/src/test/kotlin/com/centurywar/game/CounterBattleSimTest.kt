package com.centurywar.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Testy kontr na rzeczywistej symulacji, bez renderera i Androida. */
class CounterBattleSimTest {
    private fun battle(era: Int = 0, seed: Long = 6L): Battle =
        Battle(BattleConfig(Mode.SKIRMISH, seed, 1f, startEra = era, maxEra = era)).apply {
            ai = null
        }

    private fun place(b: Battle, side: Int, def: UnitDef, x: Float, cd: Float = 1000f): Fighter {
        assertTrue(b.spawn(b.sides[side], def))
        return b.units.first { it.alive && it.serial == b.serialCounter }.apply {
            this.x = x
            this.cd = cd
        }
    }

    private fun withSplash(def: UnitDef, radius: Float): UnitDef = UnitDef(
        def.id, def.era, def.role, def.nameKey, def.cost, def.hp, def.dmg,
        def.range, def.cooldown, def.speed, def.trainTime, def.size,
        radius, def.projectile, def.weapon, def.body, def.siegeMul,
    )

    private fun untilHit(b: Battle, target: Fighter, initialHp: Float): Float {
        var frames = 0
        while (target.hp == initialHp && frames++ < 100) b.step()
        assertTrue("Pocisk powinien trafić w cel", target.hp < initialHp)
        return initialHp - target.hp
    }

    @Test
    fun matrixIsBoundedAndEliteIsNeutralVsUnits() {
        for (attacker in Role.values()) for (defender in Role.values()) {
            val multiplier = Defs.counterMultiplier(attacker, defender)
            assertTrue("Mnożnik poza przedziałem: $attacker/$defender", multiplier in 1f..1.5f)
        }
        assertEquals(1.35f, Defs.counterMultiplier(Role.MELEE, Role.RANGED), 0.0001f)
        assertEquals(1.30f, Defs.counterMultiplier(Role.RANGED, Role.HEAVY), 0.0001f)
        assertEquals(1.35f, Defs.counterMultiplier(Role.HEAVY, Role.MELEE), 0.0001f)
        for (role in Role.values()) {
            assertEquals(1f, Defs.counterMultiplier(Role.ELITE, role), 0f)
        }
        assertEquals(1.6f, Defs.unit(2, Role.ELITE).siegeMul, 0f)
    }

    @Test
    fun meleeDirectHitCountersRanged() {
        val b = battle()
        val attacker = place(b, 0, Defs.unit(0, Role.MELEE), 1300f, 0f)
        val target = place(b, 1, Defs.unit(0, Role.RANGED), 1320f)
        val hp = target.hp
        b.step()
        assertEquals(attacker.dmg * 1.35f, hp - target.hp, 0.01f)
    }

    @Test
    fun meleeSplashUsesCounterOnEveryTarget() {
        val b = battle()
        val attacker = place(b, 0, withSplash(Defs.unit(0, Role.MELEE), 80f), 1300f, 0f)
        val a = place(b, 1, Defs.unit(0, Role.RANGED), 1320f)
        val c = place(b, 1, Defs.unit(0, Role.RANGED), 1340f)
        val hpA = a.hp
        val hpC = c.hp
        b.step()
        assertEquals(attacker.dmg * 1.35f, hpA - a.hp, 0.01f)
        assertEquals(attacker.dmg * 1.35f, hpC - c.hp, 0.01f)
    }

    @Test
    fun projectileRemembersRangedRoleUntilImpact() {
        val b = battle()
        val attacker = place(b, 0, Defs.unit(0, Role.RANGED), 1200f, 0f)
        val target = place(b, 1, Defs.unit(0, Role.HEAVY), 1350f)
        val hp = target.hp
        b.step()
        val p = b.projs.first { it.active }
        assertEquals(Role.RANGED, p.shooterRole)
        assertEquals(-1, p.turretEra)
        assertEquals(attacker.dmg * 1.30f, untilHit(b, target, hp), 0.01f)
    }

    @Test
    fun projectileSplashCountersEveryHeavyTarget() {
        val b = battle()
        val attacker = place(b, 0, withSplash(Defs.unit(0, Role.RANGED), 80f), 1200f, 0f)
        val a = place(b, 1, Defs.unit(0, Role.HEAVY), 1350f)
        val c = place(b, 1, Defs.unit(0, Role.HEAVY), 1375f)
        val hpA = a.hp
        val hpC = c.hp
        b.step()
        assertEquals(Role.RANGED, b.projs.first { it.active }.shooterRole)
        assertEquals(attacker.dmg * 1.30f, untilHit(b, a, hpA), 0.01f)
        assertEquals(attacker.dmg * 1.30f, hpC - c.hp, 0.01f)
    }

    private fun towerHit(era: Int, targetRole: Role): Float {
        val b = battle(era)
        val td = Defs.eras[era].turret
        b.player.turretEra[0] = era
        b.player.turretCd[0] = 0f
        val target = place(b, 1, Defs.unit(era, targetRole), b.player.baseFront + 360f)
        val hp = target.hp
        b.step()
        val p = b.projs.first { it.active }
        assertNull(p.shooterRole)
        assertEquals(era, p.turretEra)
        val dealt = untilHit(b, target, hp)
        val expected = td.dmg * Defs.turretCounterMultiplier(era, target.def)
        assertEquals(expected, dealt, 0.25f)
        return dealt
    }

    @Test
    fun steamTurretDealsExtraDamageToHeavy() {
        val era = 4
        assertEquals(1.15f, Defs.turretCounterMultiplier(era, Defs.unit(era, Role.HEAVY)), 0f)
        assertEquals(1f, Defs.turretCounterMultiplier(era, Defs.unit(era, Role.MELEE)), 0f)
        assertEquals(1f, Defs.turretCounterMultiplier(3, Defs.unit(3, Role.HEAVY)), 0f)
        val againstHeavy = towerHit(era, Role.HEAVY)
        val againstMelee = towerHit(era, Role.MELEE)
        assertTrue(againstHeavy > againstMelee)
    }

    @Test
    fun oldTurretHasNoCounterBonus() {
        towerHit(3, Role.HEAVY)
    }

    @Test
    fun recycledBarrageProjectilesForgetPreviousShooter() {
        val b = battle(4)
        val p = b.projs[0]
        p.shooterRole = Role.RANGED
        p.turretEra = 4
        assertFalse(p.active)
        assertTrue(b.castBarrage(b.player))
        assertTrue(p.active)
        assertNull(p.shooterRole)
        assertEquals(-1, p.turretEra)
    }

    @Test
    fun eliteSiegeMultiplierStillDamagesBases() {
        val b = battle(2)
        val attacker = place(b, 0, Defs.unit(2, Role.ELITE), b.enemy.baseFront - 170f, 0f)
        val hp = b.enemy.baseHp
        var frames = 0
        while (b.enemy.baseHp == hp && frames++ < 100) b.step()
        assertTrue(b.enemy.baseHp < hp)
        assertEquals(attacker.dmg * attacker.def.siegeMul, hp - b.enemy.baseHp, 0.2f)
    }

    @Test
    fun counterCombatCanFinishBattle() {
        val b = battle()
        b.enemy.baseHp = 1f
        place(b, 0, Defs.unit(0, Role.MELEE), b.enemy.baseFront - 40f, 0f)
        b.step()
        assertTrue(b.over)
        assertEquals(0, b.winner)
    }

    @Test
    fun sameSeedKeepsCombatStateDeterministic() {
        fun run(seed: Long): Battle {
            val b = Battle(BattleConfig(Mode.SKIRMISH, seed, 1.05f))
            b.playerAi = Ai(b, 0)
            repeat(3600) { b.step(); b.fx.count = 0 }
            return b
        }
        val a = run(54321L)
        val b = run(54321L)
        assertEquals(a.time, b.time, 0f)
        assertEquals(a.winner, b.winner)
        assertEquals(a.player.kills, b.player.kills)
        assertEquals(a.enemy.kills, b.enemy.kills)
        assertEquals(a.player.baseHp, b.player.baseHp, 0f)
        assertEquals(a.enemy.baseHp, b.enemy.baseHp, 0f)
        assertEquals(a.score(), b.score())
    }
}
