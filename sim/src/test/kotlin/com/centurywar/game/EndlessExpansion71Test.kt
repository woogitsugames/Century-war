package com.centurywar.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Regresja ośmiu nowych kart, interakcji i stałych ordinali zlecenia 71. */
class EndlessExpansion71Test {
    private fun battle(seed: Long = 71L) = Battle(BattleConfig(Mode.ENDLESS, seed, 1f))

    @Test fun eightCardsHaveRequestedRaritiesAndAppendOnlyOrdinals() {
        // 30 bazowych + 4 karty doktryn ze zlecenia 69; dalsze karty (108) dopisano za nimi.
        val added = EndlessCard.values().drop(34).take(8)
        assertEquals(8, added.size)
        assertEquals(listOf(EndlessRarity.COMMON, EndlessRarity.COMMON,
            EndlessRarity.UNCOMMON, EndlessRarity.UNCOMMON, EndlessRarity.UNCOMMON,
            EndlessRarity.RARE, EndlessRarity.RARE, EndlessRarity.LEGENDARY),
            added.map { it.rarity })
        assertEquals(34, EndlessCard.SUPPLY_LINE.ordinal)
        assertEquals(41, EndlessCard.CITADEL_HEART.ordinal)
    }

    @Test fun economicSynergyIsIndependentOfCardOrder() {
        val before = battle()
        before.applyEndlessCard(before.player, EndlessCard.ECONOMY)
        before.applyEndlessCard(before.player, EndlessCard.SCAVENGER)
        before.applyEndlessCard(before.player, EndlessCard.SUPPLY_LINE)
        val after = battle()
        after.applyEndlessCard(after.player, EndlessCard.SUPPLY_LINE)
        after.applyEndlessCard(after.player, EndlessCard.ECONOMY)
        after.applyEndlessCard(after.player, EndlessCard.SCAVENGER)
        assertEquals(before.player.incomeMul, after.player.incomeMul, 0.0001f)
        assertEquals(1.25f * 1.05f * 1.05f, after.player.incomeMul, 0.0001f)
    }

    @Test fun trainingSpeedMovementAndTurretsKeepSynergyInEitherOrder() {
        val a = battle()
        val b = battle()
        val first = arrayOf(EndlessCard.WAR_DRUMS, EndlessCard.MARCH,
            EndlessCard.TOWER_NETWORK, EndlessCard.TOWER_SLOT,
            EndlessCard.MUSTER_BANNER, EndlessCard.MOBILIZATION)
        for (card in first) a.applyEndlessCard(a.player, card)
        for (card in first.reversed()) b.applyEndlessCard(b.player, card)
        assertEquals(a.player.speedMul, b.player.speedMul, 0.0001f)
        assertEquals(a.player.trainMul, b.player.trainMul, 0.0001f)
        assertEquals(a.player.turretDmgMul, b.player.turretDmgMul, 0.0001f)
        assertEquals(a.player.cap, b.player.cap)
        assertEquals(1.08f * 1.06f * 1.2f, a.player.speedMul, 0.001f)
        assertEquals(1.05f * 1.08f, a.player.trainMul, 0.001f)
        assertEquals(1.08f * 1.04f, a.player.turretDmgMul, 0.001f)
    }

    @Test fun citadelSynergyAndLimitWorkInBothOrders() {
        val a = battle()
        val b = battle()
        val pair = arrayOf(EndlessCard.CITADEL_HEART, EndlessCard.REPAIR_CREW)
        for (card in pair) a.applyEndlessCard(a.player, card)
        for (card in pair.reversed()) b.applyEndlessCard(b.player, card)
        assertEquals(0.08f, a.player.repairPerWave, 0.001f)
        assertEquals(a.player.repairPerWave, b.player.repairPerWave, 0.001f)
        repeat(50) { a.applyEndlessCard(a.player, EndlessCard.CITADEL_HEART) }
        assertEquals(0.3f, a.player.repairPerWave, 0.001f)
    }

    @Test fun ironCurtainSlowsOnlyAfterTurretHitAndExpires() {
        val b = battle()
        val p = b.player
        b.applyEndlessCard(p, EndlessCard.IRON_CURTAIN)
        assertTrue(b.buyTurret(p, 0))
        assertTrue(b.spawn(b.enemy, Defs.unit(b.enemy.era, Role.HEAVY)))
        val foe = b.units.first { it.alive && it.side == 1 }
        foe.x = p.baseFront + 170f
        b.waveTimer = 900f
        var slowed = false
        for (i in 0 until 140) {
            b.step()
            b.fx.count = 0
            if (foe.slowLeft > 0f) { slowed = true; break }
        }
        assertTrue("Spowolnienie musi wynikać z trafienia wieży", slowed)
        assertTrue(b.sellTurret(p, 0))
        repeat(125) { b.step(); b.fx.count = 0 }
        assertEquals(0f, foe.slowLeft, 0.001f)
        assertFalse(b.over)
    }

    @Test fun lastBastionStacksWithLastStandBelowQuarterBaseHp() {
        val a = battle()
        val b = battle()
        for (battle in arrayOf(a, b)) {
            battle.applyEndlessCard(battle.player, EndlessCard.LAST_STAND)
            battle.player.baseHp = battle.player.baseMaxHp * 0.2f
            assertTrue(battle.spawn(battle.player, Defs.unit(0, Role.MELEE)))
            assertTrue(battle.spawn(battle.enemy, Defs.unit(0, Role.HEAVY)))
            val soldier = battle.units.first { it.alive && it.side == 0 }
            val foe = battle.units.first { it.alive && it.side == 1 }
            soldier.x = battle.player.baseFront + 180f
            foe.x = soldier.x + 10f
            soldier.cd = 0f
            foe.cd = 100f
            battle.waveTimer = 900f
        }
        b.applyEndlessCard(b.player, EndlessCard.LAST_BASTION)
        val enemyHpA = a.units.first { it.alive && it.side == 1 }.hp
        val enemyHpB = b.units.first { it.alive && it.side == 1 }.hp
        a.step(); b.step()
        val damageA = enemyHpA - a.units.first { it.alive && it.side == 1 }.hp
        val damageB = enemyHpB - b.units.first { it.alive && it.side == 1 }.hp
        assertTrue("Jednostka bazowa powinna trafić wroga", damageA > 0f)
        assertEquals(damageA * 1.3f, damageB, 0.01f)
    }
}
