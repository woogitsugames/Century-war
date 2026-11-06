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

    @Test fun twoBatchesOfNineOrdersOnePerEraInStableOrder() {
        assertEquals(Orders.COUNT, Orders.all.size)
        assertEquals(2 * Orders.BATCH, Orders.COUNT)
        val eras = (0 until Defs.ERA_COUNT).toList()
        assertEquals(eras + eras, Orders.all.map { it.era })
        Orders.all.forEachIndexed { i, o ->
            assertEquals(i, o.index)
            assertEquals(i / Orders.BATCH, o.batch)
            assertEquals(2, o.bonus.size)
            assertTrue(o.limitSeconds in 120..360)
            assertTrue(o.maxEra >= o.era)
            assertTrue(o.incomePct in 1..100)
            assertTrue(o.enemyAllowed != 0)
            // Eskorta wymaga dowódcy w masce gracza.
            if (o.goal.escort) assertTrue(o.playerAllowed and OrderRules.COMMANDER != 0)
            assertTrue(Role.entries.any { o.enemyAllowed and OrderRules.roleBit(it) != 0 })
            // Partia 1: gracz szkoli co najmniej jedną rolę; partia 2 może mieć obronę samymi wieżami.
            if (o.batch == 0) assertTrue(Role.entries.any { o.playerAllowed and OrderRules.roleBit(it) != 0 })
            else assertTrue(o.playerAllowed and (OrderRules.TURRETS or 15) != 0)
        }
        assertEquals("o209_first_line", Orders.all[0].nameKey)
        assertEquals("o211_chief_guard", Orders.all[9].nameKey)
        assertEquals("o211_final_spear_d", Orders.all[17].briefKey)
        assertEquals(Orders.COUNT, Orders.all.map { it.key }.toSet().size)
        assertEquals(Mode.ORDERS, Mode.entries.last())
        // Partia 2 ma wszystkie nowe rodzaje celów.
        val batch2 = Orders.all.drop(Orders.BATCH)
        assertTrue(batch2.any { it.goal == Orders.Goal.ESCORT })
        assertTrue(batch2.any { it.goal == Orders.Goal.SPEARHEAD })
        assertTrue(batch2.any { it.incomePct < 100 })
        assertTrue(batch2.any { Role.entries.none { r -> it.playerAllowed and OrderRules.roleBit(r) != 0 } })
        val kinds = batch2.flatMap { it.bonus }.map { it.kind }.toSet()
        for (k in listOf(Orders.ObjectiveKind.COMMANDER_ALIVE, Orders.ObjectiveKind.GOLD_LEFT,
            Orders.ObjectiveKind.FIELDED_MAX)) assertTrue(k in kinds)
    }

    @Test fun defaultsKeepOtherModesUnrestricted() {
        val cfg = BattleConfig(Mode.SKIRMISH, 5L, 1f)
        assertEquals(OrderRules.ALL, cfg.playerAllowed)
        assertEquals(OrderRules.ALL, cfg.enemyAllowed)
        assertEquals(0, cfg.timeLimitSteps)
        assertFalse(cfg.holdWins)
        assertEquals(0, cfg.orderFlags)
        assertFalse(cfg.escort)
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
        val first = Orders.all[0]
        assertEquals(10, Orders.medalsFor(first, 0, 1))
        assertEquals(45, Orders.medalsFor(first, 0, 3))
        assertEquals(20, Orders.medalsFor(first, 2, 3))
        assertEquals(0, Orders.medalsFor(first, 3, 3))
        assertEquals(0, Orders.medalsFor(first, 3, 1))
        // Dzień 67: partia 2 (rozkazy 10–18) — 15/20/30, razem 65 za rozkaz; partia 1 bez zmian.
        for (o in Orders.all) {
            val expected = if (o.index < Orders.BATCH) intArrayOf(10, 15, 20) else intArrayOf(15, 20, 30)
            assertArrayEquals("rozkaz ${o.index + 1}", expected, Orders.starMedals(o))
        }
        val second = Orders.all[Orders.BATCH]
        assertEquals(15, Orders.medalsFor(second, 0, 1))
        assertEquals(65, Orders.medalsFor(second, 0, 3))
        assertEquals(30, Orders.medalsFor(second, 2, 3))
        assertEquals(0, Orders.medalsFor(second, 3, 3))
        assertEquals(Orders.BATCH * 45 + Orders.BATCH * 65,
            Orders.all.sumOf { Orders.medalsFor(it, 0, Orders.MAX_STARS) })
        val stars = IntArray(Campaign.stages.size)
        assertFalse(Orders.unlocked(Orders.all[0], stars, IntArray(Orders.COUNT)))
        stars[0] = 1
        assertTrue(Orders.unlocked(Orders.all[0], stars, IntArray(Orders.COUNT)))
        assertFalse(Orders.unlocked(Orders.all[1], stars, IntArray(Orders.COUNT)))
        stars[3] = 2
        assertTrue(Orders.unlocked(Orders.all[1], stars, IntArray(Orders.COUNT)))
        assertEquals(4, Orders.unlockStageNumber(Orders.all[1]))
        assertEquals(32, Orders.unlockStageNumber(Orders.all[8]))
        assertFalse(Orders.unlocked(Orders.all[8], stars, IntArray(Orders.COUNT)))
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

    /** Dzień 66: eskorta — dowódca od pierwszego kroku, zostaje za linią, jego śmierć = porażka. */
    @Test fun escortCommanderStartsBehindTheLineAndItsDeathLoses() {
        val order = Orders.all.first { it.goal == Orders.Goal.ESCORT }
        val cfg = Orders.config(order, 66L)
        assertTrue(cfg.escort)
        assertTrue(cfg.holdWins)
        val b = Battle(cfg)
        b.playerAi = Ai(b, 0)
        step(b, 1)
        assertTrue(Orders.commanderAlive(b))
        val cmd = b.units.first { it.alive && it.isCommander && it.side == 0 }
        step(b, 60 * 40)
        if (!b.over) {
            assertTrue((cmd.x - b.player.baseFront) <= Battle.ESCORT_LINE + 1f)
            // Śmierć dowódcy kończy bitwę porażką, choć baza stoi.
            cmd.hp = 0f
            cmd.dying = 0f
            b.player.aliveCount--
            b.player.commanderCd = Battle.COMMANDER_COOLDOWN
            step(b, 1)
            assertTrue(b.over)
            assertEquals(1, b.winner)
            assertEquals(0, Orders.stars(order, b))
        }
        // Bez eskorty dowódca nie pojawia się sam.
        val plain = Battle(Orders.config(Orders.all[0], 66L))
        step(plain, 1)
        assertFalse(Orders.commanderAlive(plain))
        val spear = Orders.config(Orders.all.first { it.goal == Orders.Goal.SPEARHEAD }, 1L)
        assertTrue(spear.escort)
        assertFalse(spear.holdWins)
    }

    /** Dzień 66: nowe cele dodatkowe i obcięty dochód. */
    @Test fun newObjectiveKindsAndIncome() {
        val order = Orders.all.first { it.incomePct < 100 }
        val cfg = Orders.config(order, 5L)
        assertEquals(order.incomePct / 100f, cfg.playerBonuses.incomeMul, 1e-6f)
        assertEquals(Daily.normalizedBonuses().popCap, cfg.playerBonuses.popCap)
        assertEquals(1f, Orders.config(Orders.all[0], 5L).playerBonuses.incomeMul, 0f)
        val base = Orders.config(Orders.all[0], 3L)
        val b = Battle(BattleConfig(base.mode, base.seed, base.aiPower, base.startEra, base.maxEra,
            base.modifiers, base.playerBonuses, base.stageIndex, aiPersonality = base.aiPersonality,
            playerAllowed = base.playerAllowed, timeLimitSteps = 300, holdWins = true))
        step(b, 400)
        assertTrue(Orders.mainDone(b))
        b.player.gold = 500f
        assertTrue(Orders.objectiveDone(Orders.Objective(Orders.ObjectiveKind.GOLD_LEFT, 500), b))
        assertFalse(Orders.objectiveDone(Orders.Objective(Orders.ObjectiveKind.GOLD_LEFT, 501), b))
        b.player.spawned = 10
        assertTrue(Orders.objectiveDone(Orders.Objective(Orders.ObjectiveKind.FIELDED_MAX, 10), b))
        assertFalse(Orders.objectiveDone(Orders.Objective(Orders.ObjectiveKind.FIELDED_MAX, 9), b))
        assertFalse(Orders.objectiveDone(Orders.Objective(Orders.ObjectiveKind.COMMANDER_ALIVE), b))
        b.player.commanderCd = -1f
        assertTrue(Orders.objectiveDone(Orders.Objective(Orders.ObjectiveKind.COMMANDER_ALIVE), b))
    }

    /** Dzień 66: rozkaz partii 2 wymaga 2★ w rozkazie tej samej epoki z partii 1. */
    @Test fun batchTwoUnlocksAfterTwoStarsInPairedOrder() {
        val campaign = IntArray(Campaign.stages.size) { 1 }
        val stars = IntArray(Orders.COUNT)
        for (o in Orders.all.drop(Orders.BATCH)) {
            val pre = Orders.prerequisite(o)!!
            assertEquals(o.era, pre.era)
            assertEquals(0, pre.batch)
            assertFalse(Orders.unlocked(o, campaign, stars))
            stars[pre.index] = 1
            assertFalse(Orders.unlocked(o, campaign, stars))
            stars[pre.index] = Orders.BATCH2_UNLOCK_STARS
            assertTrue(Orders.unlocked(o, campaign, stars))
            // Bez epoki w kampanii nadal zablokowany.
            if (o.era > 0) assertFalse(Orders.unlocked(o, IntArray(Campaign.stages.size), stars))
        }
        for (o in Orders.all.take(Orders.BATCH)) assertEquals(null, Orders.prerequisite(o))
        assertEquals(18 * Orders.MAX_POINTS, Orders.COUNT * 3500)
    }

    /** Dzień 66: zrzut v23 zapisuje eskortę; zrzut v22 (partia 1) wczytuje się bez reguł rozkazu. */
    @Test fun snapshotV23KeepsOrderFlagsAndV22StillLoads() {
        val escort = Battle(Orders.config(Orders.all.first { it.goal.escort }, 4L))
        escort.playerAi = Ai(escort, 0)
        step(escort, 120)
        val restored = BattleSnapshot.decode(BattleSnapshot.encode(escort))!!.battle
        assertEquals(OrderFlags.ESCORT, restored.cfg.orderFlags)
        step(escort, 600); step(restored, 600)
        assertArrayEquals(BattleSnapshot.encode(escort), BattleSnapshot.encode(restored))

        val old = Battle(Orders.config(Orders.all[2], 4L))
        old.playerAi = Ai(old, 0)
        step(old, 120)
        val v22 = BattleSnapshot.encode(old, false, 0f, 7L, 22)
        val back = BattleSnapshot.decode(v22)!!.battle
        assertEquals(0, back.cfg.orderFlags)
        assertEquals(Orders.all[2].playerAllowed, back.cfg.playerAllowed)
        assertEquals(Orders.all[2].limitSeconds * 60, back.cfg.timeLimitSteps)
        assertEquals(old.stepCount, back.stepCount)
    }
}
