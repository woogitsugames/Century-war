package com.centurywar.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Granice zasobów, stanów i deterministyczny fuzz komend gracza. */
class BattleRobustnessTest {
    private fun battle(
        seed: Long = 42L,
        startEra: Int = 0,
        maxEra: Int = 7,
        modifiers: Set<Modifier> = emptySet(),
        bonuses: Bonuses = Bonuses(),
    ): Battle = Battle(
        BattleConfig(Mode.SKIRMISH, seed, 1f, startEra, maxEra, modifiers, bonuses),
    ).apply { ai = null }

    @Test
    fun fullUnitPoolRejectsAdditionalSpawnWithoutException() {
        val b = battle(startEra = 2, maxEra = 2)
        val def = Defs.unit(2, Role.MELEE)
        repeat(Battle.MAX_UNITS) { assertTrue(b.spawn(b.player, def)) }
        assertFalse(b.spawn(b.player, def))
        assertEquals(72, b.units.count { it.active })
        repeat(30) { b.step(); b.fx.count = 0 }
        assertEquals(Battle.MAX_UNITS, b.units.size)
        assertTrue(b.units.count { it.active } <= Battle.MAX_UNITS)
    }

    @Test
    fun fullProjectilePoolDoesNotConsumeAbilityWithoutFiring() {
        val b = battle()
        repeat(Battle.MAX_PROJ / 16) {
            b.player.abilityCd[0] = 0f
            assertTrue(b.castBarrage(b.player))
        }
        assertEquals(Battle.MAX_PROJ, b.projs.count { it.active })
        b.player.abilityCd[0] = 0f
        assertFalse(b.castBarrage(b.player))
        assertEquals(0f, b.player.abilityCd[0], 0f)
        repeat(90) { b.step(); b.fx.count = 0 }
        assertTrue(b.projs.count { it.active } <= Battle.MAX_PROJ)
        assertTrue(b.player.gold.isFinite())
    }

    @Test
    fun sameMinAndMaxEraRejectsEvolveAndAcceptsVeteranXp() {
        for (era in intArrayOf(0, 3, 7)) {
            val b = battle(startEra = era, maxEra = era)
            b.player.xp = 10_000_000f
            assertFalse(b.canEvolve(b.player))
            assertFalse(b.evolve(b.player))
            // Epoka z wariantami (zlecenie 28) czeka najpierw na wybór gracza.
            if (b.pendingVariant != null) assertTrue(b.chooseVariant(0))
            b.step()
            assertEquals(era, b.player.era)
            assertTrue("Brak awansu rangi dla epoki $era", b.player.rank > 0)
        }
    }

    @Test
    fun allModifiersCanRunTogether() {
        val modifiers = Modifier.values().toSet()
        val b = battle(modifiers = modifiers, bonuses = Bonuses(turretSlots = 4))
        assertEquals(0, b.player.slots)
        assertEquals(0, b.enemy.slots)
        assertTrue(b.player.baseMaxHp > 0f)
        repeat(360) { b.step(); b.fx.count = 0 }
        assertFinite(b, 0)
    }

    @Test
    fun noTurretsIsStrongerThanArsenalDoctrine() {
        val b = battle(modifiers = setOf(Modifier.NO_TURRETS))
        val oldCostMultiplier = b.player.turretCostMul
        b.applyDoctrine(b.player, Doctrine.ARSENAL)
        assertEquals(0, b.player.slots)
        assertEquals(-1, b.freeSlot(b.player))
        assertEquals(oldCostMultiplier, b.player.turretCostMul, 0f)
        assertFalse(b.buyTurret(b.player, 0))
    }

    @Test
    fun surrenderWhileChoosingDoctrineCancelsDialog() {
        val b = battle(maxEra = 1)
        b.player.xp = Defs.evolveXp[0].toFloat()
        assertTrue(b.evolve(b.player))
        assertNotNull(b.pendingChoice)
        val t = b.time
        b.step()
        assertEquals(t, b.time, 0f)
        b.surrender()
        assertTrue(b.over)
        assertEquals(1, b.winner)
        assertNull(b.pendingChoice)
        b.chooseDoctrine(0)
        assertTrue(b.player.doctrines.isEmpty())
        assertEquals(0f, b.player.baseHp, 0f)
    }

    @Test
    fun cancelEmptyQueueAndInvalidTurretSlotsAreSafe() {
        val b = battle()
        val gold = b.player.gold
        assertFalse(b.cancelLast(b.player))
        assertEquals(gold, b.player.gold, 0f)
        assertTrue(b.queueUnit(b.player, Role.MELEE))
        assertTrue(b.cancelLast(b.player))
        assertFalse(b.cancelLast(b.player))
        assertEquals(gold, b.player.gold, 0f)
        for (slot in intArrayOf(-5, -1, 2, 4, 10)) {
            assertEquals(-1, b.upgradeCost(b.player, slot))
            assertFalse(b.upgradeTurret(b.player, slot))
            assertFalse(b.sellTurret(b.player, slot))
            assertFalse(b.buyTurret(b.player, slot))
        }
        assertFalse(b.abilityReady(b.player, -1))
        assertFalse(b.abilityReady(b.player, 2))
    }

    @Test
    fun baseHpNeverDropsBelowZeroOnOverkill() {
        val b = battle(startEra = 0, maxEra = 0)
        val u = b.units[0]
        assertTrue(b.spawn(b.player, Defs.unit(0, Role.MELEE)))
        u.x = b.enemy.baseFront - 10f
        u.cd = 0f
        u.dmg = b.enemy.baseMaxHp * 10f
        b.step()
        assertTrue(b.over)
        assertEquals(0f, b.enemy.baseHp, 0f)
        assertFinite(b, 0)
    }

    private fun assertFinite(b: Battle, seed: Int) {
        for (side in b.sides) {
            assertTrue("NaN/Inf złota: seed=$seed", side.gold.isFinite())
            assertTrue("Ujemne złoto: seed=$seed", side.gold >= 0f)
            assertTrue("NaN/Inf XP: seed=$seed", side.xp.isFinite())
            assertTrue("NaN/Inf HP: seed=$seed", side.baseHp.isFinite() && side.baseMaxHp.isFinite())
            assertTrue("HP bazy poza zakresem: seed=$seed",
                side.baseHp >= 0f && side.baseHp <= side.baseMaxHp)
        }
        for (unit in b.units) if (unit.active) {
            assertTrue("NaN/Inf HP jednostki: seed=$seed", unit.hp.isFinite() && unit.maxHp.isFinite())
            assertTrue("HP jednostki poza zakresem: seed=$seed",
                unit.hp >= 0f && unit.hp <= unit.maxHp)
        }
    }

    @Test
    fun fuzzTwoHundredSeedsTenMinutesEach() {
        val roles = Role.values()
        val allModifiers = Modifier.values()
        for (seed in 0 until 200) {
            val commands = Rng(0x6A09E667F3BCC909L xor seed.toLong())
            val era = commands.nextInt(3)
            val maxEra = minOf(7, era + commands.nextInt(3))
            val mods = allModifiers.filter { commands.nextInt(5) == 0 }.toSet()
            val b = Battle(BattleConfig(
                Mode.SKIRMISH, 10_000L + seed, 0.85f + commands.nextFloat() * 0.55f,
                era, maxEra, mods,
            ))
            var step = 0
            val maxSteps = (10f * 60f / Battle.STEP).toInt()
            while (!b.over && step < maxSteps) {
                if (step % 24 == 0) {
                    val p = b.player
                    when (commands.nextInt(12)) {
                        0, 1, 2 -> b.queueUnit(p, roles[commands.nextInt(roles.size)])
                        3 -> b.cancelLast(p)
                        4 -> b.evolve(p)
                        5 -> b.buyArmory(p)
                        6 -> { val slot = b.freeSlot(p); if (slot >= 0) b.buyTurret(p, slot) }
                        7 -> b.upgradeTurret(p, commands.nextInt(8) - 2)
                        8 -> b.sellTurret(p, commands.nextInt(8) - 2)
                        9 -> b.castBarrage(p)
                        10 -> b.castRally(p)
                        11 -> p.hold = !p.hold
                    }
                    if (b.pendingChoice != null) b.chooseDoctrine(commands.nextInt(3))
                }
                b.step()
                b.fx.count = 0
                if (step % 120 == 0 || b.over) assertFinite(b, seed)
                step++
            }
            assertFinite(b, seed)
            assertTrue("Czas symulacji poza limitem: seed=$seed", b.time <= 600.1f)
        }
    }
}
