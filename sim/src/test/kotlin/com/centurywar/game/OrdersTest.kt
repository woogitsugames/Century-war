package com.centurywar.game

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Dzień 62: Rozkazy sztabu (tryb B z R14). */
class OrdersTest {
    private fun step(b: Battle, n: Int) = repeat(n) { if (!b.over) { b.step(); b.fx.count = 0 } }

    @Test fun nineOrdersOnePerEraInStableOrder() {
        assertEquals(Orders.COUNT, Orders.all.size)
        assertEquals((0 until Defs.ERA_COUNT).toList(), Orders.all.map { it.era })
        Orders.all.forEachIndexed { i, o ->
            assertEquals(i, o.index)
            assertEquals(2, o.bonus.size)
            assertTrue(o.limitSeconds in 120..360)
            assertTrue(o.maxEra >= o.era)
            // Każdy rozkaz pozwala graczowi szkolić co najmniej jedną rolę.
            assertTrue(Role.entries.any { o.playerAllowed and OrderRules.roleBit(it) != 0 })
            assertTrue(Role.entries.any { o.enemyAllowed and OrderRules.roleBit(it) != 0 })
        }
        assertEquals(Orders.COUNT, Orders.all.map { it.key }.toSet().size)
        assertEquals(Mode.ORDERS, Mode.entries.last())
    }

    @Test fun defaultsKeepOtherModesUnrestricted() {
        val cfg = BattleConfig(Mode.SKIRMISH, 5L, 1f)
        assertEquals(OrderRules.ALL, cfg.playerAllowed)
        assertEquals(OrderRules.ALL, cfg.enemyAllowed)
        assertEquals(0, cfg.timeLimitSteps)
        assertFalse(cfg.holdWins)
        val b = Battle(cfg)
        for (role in Role.entries) assertTrue(b.roleAllowed(b.player, role))
        assertEquals(Battle.SIEGE_START, b.siegeStart)
    }

    @Test fun restrictionsBlockRolesCommanderTurretsAndBarrage() {
        val order = Orders.all[1] // tylko wręcz, dowódca, ostrzał, zryw; wróg tylko dystans
        val b = Battle(Orders.config(order, 77L))
        b.player.gold = 100_000f
        b.enemy.gold = 100_000f
        assertTrue(b.canQueue(b.player, Role.MELEE))
        assertFalse(b.canQueue(b.player, Role.RANGED))
        assertFalse(b.canQueue(b.player, Role.HEAVY))
        assertFalse(b.canQueue(b.player, Role.ELITE))
        assertEquals(0, b.player.slots)
        assertTrue(b.canSummonCommander(b.player))
        assertFalse(b.canSummonCommander(b.enemy))
        assertTrue(b.canQueue(b.enemy, Role.RANGED))
        assertFalse(b.canQueue(b.enemy, Role.MELEE))
        assertEquals(0, b.enemy.slots)

        val steam = Battle(Orders.config(Orders.all[5], 77L)) // bez zrywu i dowódcy
        assertFalse(steam.player.rallyUnlocked)
        assertTrue(steam.player.slots > 0)
        assertTrue(steam.abilityReady(steam.player, 0))
        val noBarrage = Battle(Orders.config(Orders.all[1], 1L).let {
            BattleConfig(it.mode, it.seed, it.aiPower, it.startEra, it.maxEra, it.modifiers,
                it.playerBonuses, it.stageIndex, playerAllowed = OrderRules.MELEE,
                timeLimitSteps = it.timeLimitSteps)
        })
        assertFalse(noBarrage.abilityReady(noBarrage.player, 0))
        assertFalse(noBarrage.castBarrage(noBarrage.player))
    }

    @Test fun aiTrainsOnlyAllowedRoles() {
        val order = Orders.all[6] // gracz: ciężcy i elity; wróg: tylko wręcz
        val b = Battle(Orders.config(order, 4_242L))
        b.playerAi = Ai(b, 0)
        step(b, 60 * 120)
        assertEquals(0, b.player.spawnedByRole[Role.MELEE.ordinal])
        assertEquals(0, b.player.spawnedByRole[Role.RANGED.ordinal])
        assertTrue(b.player.spawnedByRole[Role.HEAVY.ordinal] > 0)
        assertEquals(0, b.enemy.spawnedByRole[Role.RANGED.ordinal])
        assertEquals(0, b.enemy.spawnedByRole[Role.HEAVY.ordinal])
        assertTrue(b.enemy.spawnedByRole[Role.MELEE.ordinal] > 0)
    }

    @Test fun timeLimitDecidesHoldAndDestroy() {
        fun quick(holdWins: Boolean): Battle {
            val base = Orders.config(Orders.all[0], 3L)
            return Battle(BattleConfig(base.mode, base.seed, base.aiPower, base.startEra, base.maxEra,
                base.modifiers, base.playerBonuses, base.stageIndex, aiPersonality = base.aiPersonality,
                playerAllowed = base.playerAllowed, timeLimitSteps = 600, holdWins = holdWins))
        }
        val hold = quick(true)
        step(hold, 700)
        assertTrue(hold.over)
        assertEquals(600, hold.stepCount)
        assertEquals(0, hold.winner)
        val destroy = quick(false)
        step(destroy, 700)
        assertTrue(destroy.over)
        assertEquals(1, destroy.winner)
        assertEquals(0, Orders.stars(Orders.all[0], destroy))
    }

    @Test fun starsMedalsPointsAndUnlocks() {
        assertEquals(10, Orders.medalsFor(0, 1))
        assertEquals(45, Orders.medalsFor(0, 3))
        assertEquals(20, Orders.medalsFor(2, 3))
        assertEquals(0, Orders.medalsFor(3, 3))
        assertEquals(0, Orders.medalsFor(3, 1))
        val stars = IntArray(Campaign.stages.size)
        assertFalse(Orders.unlocked(Orders.all[0], stars))
        stars[0] = 1
        assertTrue(Orders.unlocked(Orders.all[0], stars))
        assertFalse(Orders.unlocked(Orders.all[1], stars))
        stars[3] = 2
        assertTrue(Orders.unlocked(Orders.all[1], stars))
        assertEquals(4, Orders.unlockStageNumber(Orders.all[1]))
        assertEquals(32, Orders.unlockStageNumber(Orders.all[8]))
        assertFalse(Orders.unlocked(Orders.all[8], stars))
    }

    /** Zabezpieczenie przed rozkazem niemożliwym po przyszłych zmianach balansu (R14). */
    @Test fun everyOrderHasReachableThreeStars() {
        for (order in Orders.all) {
            var best = 0
            var trial = 0
            while (best < 3 && trial < 40) {
                val b = OrdersBalanceReport.play(order, OrdersBalanceReport.seedFor(order.index, trial++))
                assertTrue("Rozkaz ${order.key} bez rozstrzygnięcia", b.over)
                assertTrue("Rozkaz ${order.key} trwał dłużej niż limit",
                    b.stepCount <= order.limitSeconds * 60)
                best = maxOf(best, Orders.stars(order, b))
                if (Orders.stars(order, b) > 0) assertTrue(Orders.points(order, b) >= 1000)
            }
            assertEquals("Rozkaz ${order.key}: brak 3★ w 40 seedach", 3, best)
        }
    }

    @Test fun snapshotAndReplayRecordKeepOrderRules() {
        val order = Orders.all[2]
        val a = Battle(Orders.config(order, 9_001L))
        a.playerAi = Ai(a, 0)
        step(a, 60 * 30)
        val bytes = BattleSnapshot.encode(a)
        val decoded = BattleSnapshot.decode(bytes)
        assertNotNull(decoded)
        val restored = decoded!!.battle
        assertEquals(Mode.ORDERS, restored.cfg.mode)
        assertEquals(order.playerAllowed, restored.cfg.playerAllowed)
        assertEquals(order.enemyAllowed, restored.cfg.enemyAllowed)
        assertEquals(order.limitSeconds * 60, restored.cfg.timeLimitSteps)
        assertEquals(false, restored.cfg.holdWins)
        assertArrayEquals(bytes, BattleSnapshot.encode(restored))
        step(a, 600); step(restored, 600)
        assertArrayEquals(BattleSnapshot.encode(a), BattleSnapshot.encode(restored))

        // Plik powtórki: konfiguracja z polami rozkazu i weryfikacja przebiegu.
        val cfg = Orders.config(Orders.all[0], 31L)
        val b = Battle(cfg)
        val ctrl = BattleController(b)
        ctrl.queue(Role.MELEE)
        step(b, 300)
        ctrl.surrender()
        val meta = ReplayRecord.Meta(Mode.ORDERS, 0, b.winner, b.score(), b.time, b.wave, 1L, "Sztab")
        val record = ReplayRecord.decode(ReplayRecord.encode(cfg, ctrl.log(), meta))
        assertTrue(record.playable)
        assertEquals(cfg.playerAllowed, record.cfg.playerAllowed)
        assertEquals(cfg.timeLimitSteps, record.cfg.timeLimitSteps)
        assertEquals(true, record.cfg.holdWins)
        assertEquals(b.score(), Replay.verify(record.cfg, record.log!!).score)
    }

    @Test fun ordersUseEarlySiegeOnlyInOrdersMode() {
        val b = Battle(Orders.config(Orders.all[3], 1L))
        assertEquals(Orders.SIEGE_START, b.siegeStart)
        assertEquals(Orders.SIEGE_RAMP, b.siegeRamp)
        assertEquals(Battle.SIEGE_START, Battle(Daily.config(Daily.RELEASE_DAY, Bonuses())).siegeStart)
    }
}
