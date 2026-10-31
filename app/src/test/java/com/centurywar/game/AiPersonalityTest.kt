package com.centurywar.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiPersonalityTest {

    private fun autoplay(config: BattleConfig, minutes: Float = 15f): Battle {
        val battle = Battle(config)
        battle.playerAi = Ai(battle, 0, AiPersonality.BALANCED)
        val limit = (minutes * 60f / Battle.STEP).toInt()
        var steps = 0
        while (!battle.over && steps++ < limit) {
            battle.step()
            battle.fx.count = 0
        }
        return battle
    }

    @Test
    fun everyPersonalityFinishesAndChangesArmyComposition() {
        val compositions = HashSet<String>()
        var totalSeconds = 0f
        for (personality in AiPersonality.entries) {
            val battle = autoplay(BattleConfig(
                Mode.SKIRMISH, 1408L, 1.35f, startEra = 2, maxEra = 2,
                modifiers = setOf(Modifier.FRAGILE_BASES, Modifier.RICH_START),
                playerBonuses = Bonuses(baseHpMul = 0.7f, unitHpMul = 0.9f),
                aiPersonality = personality,
            ))
            val roles = battle.enemy.spawnedByRole.joinToString("/")
            println("${personality.name}: t=${battle.time}s winner=${battle.winner} " +
                "spawned=${battle.enemy.spawned} roles=$roles")
            assertTrue("${personality.name}: bitwa nie skończyła się w 15 minut", battle.over)
            assertTrue("${personality.name}: brak jednostek", battle.enemy.spawned > 0)
            compositions.add(roles)
            totalSeconds += battle.time
        }
        println("średni czas pięciu strategii: ${totalSeconds / AiPersonality.entries.size}s")
        assertTrue("armie dowódców nie różnią się składem", compositions.size >= 3)
    }

    @Test
    fun deterministicForEachPersonality() {
        for (personality in AiPersonality.entries) {
            val cfg = BattleConfig(Mode.SKIRMISH, 7252L, 1f, startEra = 2, maxEra = 2,
                modifiers = setOf(Modifier.FRAGILE_BASES, Modifier.RICH_START),
                aiPersonality = personality)
            val a = autoplay(cfg)
            val b = autoplay(cfg)
            assertEquals(personality.name, a.time, b.time, 0f)
            assertEquals(a.winner, b.winner)
            assertTrue(a.enemy.spawnedByRole.contentEquals(b.enemy.spawnedByRole))
        }
    }

    @Test
    fun fogStopsReactingToUnseenEnemiesOnBothSides() {
        val battle = Battle(BattleConfig(Mode.SKIRMISH, 1L, 1f,
            modifiers = setOf(Modifier.FOG_OF_WAR)))
        val enemyBrain = battle.ai!!
        val playerBrain = Ai(battle, 0, AiPersonality.BALANCED)
        val fighter = battle.units[0]
        fighter.active = true
        fighter.dying = -1f
        fighter.side = 0
        fighter.x = battle.enemy.front - 651f
        assertFalse(enemyBrain.canSeeEnemy(fighter))
        fighter.x = battle.enemy.front - 650f
        assertTrue(enemyBrain.canSeeEnemy(fighter))
        fighter.side = 1
        fighter.x = battle.player.front + 651f
        assertFalse(playerBrain.canSeeEnemy(fighter))
        fighter.x = battle.player.front + 650f
        assertTrue(playerBrain.canSeeEnemy(fighter))
    }

    @Test
    fun rallySupportsBattleLineBeforeEnemyReachesBase() {
        val battle = Battle(BattleConfig(Mode.SKIRMISH, 908L, 1f,
            aiPersonality = AiPersonality.ENGINEER))
        val me = battle.enemy
        val def = Defs.unit(me.era, Role.MELEE)
        repeat(5) { assertTrue(battle.spawn(me, def)) }
        val foe = battle.player
        assertTrue(battle.spawn(foe, Defs.unit(foe.era, Role.MELEE)))
        me.front = 1500f
        battle.units[5].x = 1450f
        assertTrue(me.aliveCount >= 5)
        assertTrue(me.baseFront - me.front < Defs.LANE * 0.45f)
        battle.ai!!.update(1f)
        assertTrue("Zryw powinien wspierać walkę na linii starcia", me.abilityCd[1] > 0f)
    }

    @Test
    fun hiddenArmyDoesNotChangeAiOrdersOrTriggerBarrage() {
        val cfg = BattleConfig(Mode.SKIRMISH, 34L, 1f,
            modifiers = setOf(Modifier.FOG_OF_WAR),
            aiPersonality = AiPersonality.BALANCED)
        val hidden = Battle(cfg)
        val empty = Battle(cfg)
        for (i in 0 until 4) {
            val u = hidden.units[i]
            u.active = true
            u.dying = -1f
            u.side = 0
            u.def = Defs.unit(0, Role.RANGED)
            u.x = hidden.enemy.baseFront - 900f
            u.hp = u.def.hp
            u.dmg = u.def.dmg
        }
        hidden.ai!!.update(1f)
        empty.ai!!.update(1f)
        assertEquals(empty.enemy.queueSize, hidden.enemy.queueSize)
        assertEquals(empty.enemy.queue[0], hidden.enemy.queue[0])
        assertEquals(0f, hidden.enemy.abilityCd[0], 0f)
    }

    @Test
    fun siegeDoctrineImprovesDamageToEnemyBase() {
        val cfg = BattleConfig(Mode.SKIRMISH, 777L, 1f, startEra = 0, maxEra = 0)
        val normal = Battle(cfg)
        val improved = Battle(cfg)
        val def = Defs.unit(0, Role.MELEE)
        assertTrue(normal.spawn(normal.player, def))
        assertTrue(improved.spawn(improved.player, def))
        normal.units[0].x = normal.enemy.baseFront - 12f
        improved.units[0].x = improved.enemy.baseFront - 12f
        improved.applyDoctrine(improved.player, Doctrine.SIEGE)
        repeat(120) { normal.step(); improved.step() }
        val normalDamage = normal.enemy.baseMaxHp - normal.enemy.baseHp
        val improvedDamage = improved.enemy.baseMaxHp - improved.enemy.baseHp
        assertTrue("zwykła jednostka powinna zaatakować bazę", normalDamage > 0f)
        assertTrue("doktryna oblężnicza musi zwiększać obrażenia bazy",
            improvedDamage > normalDamage * 1.2f)
    }

    @Test
    fun noUndisclosedArmyOrTurretBonuses() {
        val b = Battle(BattleConfig(Mode.CAMPAIGN, 18L, 1.7f,
            stageIndex = 24))
        assertEquals(b.player.cap, b.enemy.cap)
        assertEquals(b.player.slots, b.enemy.slots)
        assertTrue("mnożnik dochodów jest jawny", b.enemy.incomeMul == 1.7f)
    }

    @Test
    fun aimedBarrageHitsOnlyNearTargetAndOldApiStillWorks() {
        val cfg = BattleConfig(Mode.SKIRMISH, 17L, 1f)
        val battle = Battle(cfg)
        assertTrue(battle.castBarrage(battle.enemy, 1900f))
        var projectiles = 0
        for (p in battle.projs) {
            if (!p.active) continue
            projectiles++
            assertTrue("pocisk poza skupiskiem: ${p.tx}", p.tx in 1780f..2020f)
        }
        assertEquals(16, projectiles)
        assertFalse(battle.castBarrage(battle.enemy, 1900f))

        val legacy = Battle(cfg)
        assertTrue(legacy.castBarrage(legacy.enemy))
        assertEquals(16, legacy.projs.count { it.active })
    }

    @Test
    fun aiWaitsForEraWhenXpAlmostReadyAndItHasAnArmy() {
        val cfg = BattleConfig(Mode.SKIRMISH, 12L, 1f, startEra = 0, maxEra = 1,
            aiPersonality = AiPersonality.ECONOMIST)
        val b = Battle(cfg)
        val me = b.enemy
        assertTrue(b.spawn(me, Defs.unit(me.era, Role.MELEE)))
        assertTrue(b.spawn(me, Defs.unit(me.era, Role.MELEE)))
        me.xp = Defs.evolveXp[me.era] * 0.9f
        b.ai!!.update(1f)
        assertEquals(0, me.queueSize)
        b.time = 13f
        b.ai!!.update(1f)
        assertTrue("oszczędzanie nie może blokować produkcji bez końca", me.queueSize > 0)
        me.xp = Defs.evolveXp[me.era].toFloat()
        b.ai!!.update(1f)
        assertEquals("ekonomista odracza awans", 0, me.era)
        b.time = 21f
        b.ai!!.update(1f)
        assertEquals(1, me.era)
    }

    @Test
    fun aiCanSellObsoleteTurretWithoutCreatingGold() {
        val cfg = BattleConfig(Mode.SKIRMISH, 13L, 1f, startEra = 2, maxEra = 2,
            aiPersonality = AiPersonality.ENGINEER)
        val b = Battle(cfg)
        val me = b.enemy
        b.time = 70f
        me.gold = 0f
        me.turretEra[0] = 0
        repeat(2) {
            assertTrue(b.spawn(b.player, Defs.unit(b.player.era, Role.RANGED)))
            b.units[it].x = me.baseFront - 350f
        }
        b.ai!!.update(1f)
        assertEquals(-1, me.turretEra[0])
        var balance = me.gold
        for (i in 0 until me.queueSize) balance += Defs.allUnits[me.queue[i]].cost
        assertEquals(me.turretCost(0) / 2f, balance, 0f)
    }
}
