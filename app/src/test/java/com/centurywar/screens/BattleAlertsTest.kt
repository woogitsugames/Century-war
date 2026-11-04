package com.centurywar.screens

import com.centurywar.game.Battle
import com.centurywar.game.BattleConfig
import com.centurywar.game.Mode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** R10-03: czysty detektor sygnałów bitwy (bez Androida). */
class BattleAlertsTest {
    private fun battle(mode: Mode = Mode.SKIRMISH) = Battle(BattleConfig(mode, 1003L, 1f))

    private fun setHp(b: Battle, share: Float) { b.player.baseHp = b.player.baseMaxHp * share }

    @Test fun baseThresholdsFireOnceDownAndRearmAfterRepair() {
        val b = battle(Mode.ENDLESS)
        val a = BattleAlerts()
        var t = 0f
        fun next(share: Float): Int { setHp(b, share); t += 0.1f; return a.step(b, t) }
        assertEquals(0, a.step(b, t))
        assertEquals(0, next(1f))
        assertEquals(BattleAlerts.BASE_HALF, next(0.49f))
        assertEquals(0, next(0.45f))
        assertEquals(BattleAlerts.BASE_LOW, next(0.24f))
        assertEquals(0, next(0.20f))
        // Naprawa w Endless: 60% to jeszcze nie „powyżej 60%”, ale już powyżej 35%.
        assertEquals(0, next(0.60f))
        assertEquals(0, next(0.49f))
        assertEquals(BattleAlerts.BASE_LOW, next(0.24f))
        assertEquals(0, next(0.61f))
        assertEquals(BattleAlerts.BASE_HALF, next(0.5f))
        // Spadek przez oba progi naraz daje oba bity.
        val c = battle()
        val d = BattleAlerts()
        d.step(c, 0f)
        setHp(c, 0.2f)
        assertEquals(BattleAlerts.BASE_HALF or BattleAlerts.BASE_LOW, d.step(c, 0.1f))
    }

    @Test fun enemyEraTwiceGivesTwoEvents() {
        val b = battle()
        val a = BattleAlerts()
        a.step(b, 0f)
        var events = 0
        for (i in 1..30) {
            if (i == 5 || i == 20) b.enemy.era++
            if (a.step(b, i * 0.1f) and BattleAlerts.ENEMY_ERA != 0) events++
        }
        assertEquals(2, events)
    }

    @Test fun abilityReadyOnlyAfterUseAndAtMostEvery8Seconds() {
        val b = battle()
        val p = b.player
        val a = BattleAlerts()
        // Odnowienie trwające od startu bitwy nie jest „użyciem”.
        p.abilityCd[0] = 3f
        a.step(b, 0f)
        p.abilityCd[0] = 0f
        assertEquals(0, a.step(b, 0.1f) and BattleAlerts.ABILITY_READY)
        // Ostrzał użyty i odnowiony: dokładnie jedno zdarzenie.
        p.abilityCd[0] = 20f
        assertEquals(0, a.step(b, 1f))
        p.abilityCd[0] = 5f
        assertEquals(0, a.step(b, 16f))
        p.abilityCd[0] = 0f
        assertEquals(BattleAlerts.ABILITY_READY, a.step(b, 21f) and BattleAlerts.ABILITY_READY)
        assertEquals(0, a.step(b, 21.1f) and BattleAlerts.ABILITY_READY)
        // Ponowne użycie i gotowość w ciągu 8 s od poprzedniego sygnału: cisza.
        p.abilityCd[0] = 2f
        a.step(b, 22f)
        p.abilityCd[0] = 0f
        assertEquals(0, a.step(b, 24f) and BattleAlerts.ABILITY_READY)
    }

    @Test fun siegeOnceAndNeverInEndless() {
        val b = battle()
        val a = BattleAlerts()
        a.step(b, 0f)
        var events = 0
        b.time = b.siegeStart - 1f
        if (a.step(b, b.time) and BattleAlerts.SIEGE != 0) events++
        for (i in 0..20) {
            b.time = b.siegeStart + i
            if (a.step(b, b.time) and BattleAlerts.SIEGE != 0) events++
        }
        assertEquals(1, events)
        val e = battle(Mode.ENDLESS)
        val ea = BattleAlerts()
        ea.step(e, 0f)
        for (i in 0..20) {
            e.time = e.siegeStart + i
            assertEquals(0, ea.step(e, e.time) and BattleAlerts.SIEGE)
        }
    }

    @Test fun resumedBattleHasNoFalseEvents() {
        val b = battle()
        setHp(b, 0.3f)
        b.enemy.era = 2
        b.player.abilityCd[0] = 4f
        b.time = b.siegeStart + 30f
        val a = BattleAlerts()
        assertEquals(0, a.step(b, b.time))
        for (i in 1..10) assertEquals(0, a.step(b, b.time + i * 0.1f))
        // Dalszy spadek poniżej 25% nadal jest zdarzeniem.
        setHp(b, 0.2f)
        assertEquals(BattleAlerts.BASE_LOW, a.step(b, b.time + 2f))
    }

    @Test fun baseHitNeedsNoticeableDamageWithinASecond() {
        val b = battle()
        val a = BattleAlerts()
        a.step(b, 0f)
        val max = b.player.baseMaxHp
        b.player.baseHp = max * 0.99f
        a.step(b, 0.1f)
        assertTrue(a.baseHitAt < 0f)
        b.player.baseHp = max * 0.97f
        a.step(b, 0.3f)
        assertEquals(0.3f, a.baseHitAt, 0f)
        // Kapanie 0,5% co 2 s nie zapala znaku.
        val c = battle()
        val d = BattleAlerts()
        d.step(c, 0f)
        for (i in 1..20) {
            c.player.baseHp -= c.player.baseMaxHp * 0.005f
            d.step(c, i * 2f)
        }
        assertTrue(d.baseHitAt < 0f)
    }
}
