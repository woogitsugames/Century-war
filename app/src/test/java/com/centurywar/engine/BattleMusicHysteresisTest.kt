package com.centurywar.engine

import com.centurywar.game.Ai
import com.centurywar.game.Battle
import com.centurywar.game.BattleConfig
import com.centurywar.game.Defs
import com.centurywar.game.Mode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Przenikanie spokojna/bojowa w realnych bitwach (AI kontra AI, napięcie liczone jak w
 * BattleScreen) i histereza [BattleMixFollower]: krótkie przerwy w walce nie „pompują”.
 */
class BattleMusicHysteresisTest {
    /** Stare wygładzanie (do porównania): liniowo 1/1,5 s w obie strony, bez podtrzymania. */
    private class OldSmoother {
        var value = 0f
        fun update(dt: Float, want: Float) {
            val max = dt / 1.5f
            value += (want - value).coerceIn(-max, max)
        }
    }

    private class Stats {
        var seconds = 0f
        var crossings = 0      // przejścia przez 0,5 (spokojna <-> bojowa)
        var reversals = 0      // zawrócenia kierunku po ruchu o >= 0,25
        var travel = 0f        // łączna droga miksu
        private var above = false
        private var dir = 0
        private var extreme = 0f
        private var last = 0f
        fun add(dt: Float, v: Float) {
            seconds += dt
            travel += kotlin.math.abs(v - last)
            val nowAbove = v > 0.5f
            if (nowAbove != above) { crossings++; above = nowAbove }
            val d = when { v > last + 1e-6f -> 1; v < last - 1e-6f -> -1; else -> 0 }
            if (d != 0) {
                if (dir == 0) { dir = d; extreme = last }
                else if (d != dir) {
                    if (kotlin.math.abs(last - extreme) >= 0.25f) reversals++
                    dir = d; extreme = last
                }
            }
            last = v
        }
        val perMinute: Float get() = crossings / (seconds / 60f)
    }

    private fun tension(b: Battle): Float {
        if (b.over || b.pendingChoice != null || b.pendingCards != null || b.pendingVariant != null) return 0f
        var allyFront = Float.NEGATIVE_INFINITY
        var enemyFront = Float.POSITIVE_INFINITY
        for (u in b.units) {
            if (!u.alive) continue
            if (u.side == 0) { if (u.x > allyFront) allyFront = u.x } else if (u.x < enemyFront) enemyFront = u.x
        }
        val hp = b.player
        val ratio = if (hp.baseMaxHp > 0f) hp.baseHp / hp.baseMaxHp else 1f
        return BattleMusicIntensity.level(allyFront, enemyFront, hp.baseFront, Defs.LANE, ratio)
    }

    @Test fun realBattlesDoNotPumpBetweenCalmAndBattle() {
        val oldAll = Stats()
        val newAll = Stats()
        val frame = 2 * Battle.STEP // 30 kl./s, jak tani telefon
        val report = StringBuilder("seed  min   napięcie>0,5  stare: przejść/min zawróceń   nowe: przejść/min zawróceń\n")
        for (seed in listOf(11L, 12L, 13L, 14L, 15L, 16L)) {
            val b = Battle(BattleConfig(Mode.SKIRMISH, seed, 1f))
            b.playerAi = Ai(b, 0)
            val old = OldSmoother()
            val new = BattleMixFollower()
            val so = Stats()
            val sn = Stats()
            var high = 0
            var samples = 0
            val maxSteps = (15f * 60f / Battle.STEP).toInt()
            var steps = 0
            while (!b.over && steps < maxSteps) {
                b.step(); b.step(); b.fx.count = 0; steps += 2
                val want = RecordedMixer.battleWeight(tension(b))
                if (want > 0.5f) high++
                samples++
                old.update(frame, want); so.add(frame, old.value)
                new.update(frame, want); sn.add(frame, new.value)
            }
            for ((all, s) in listOf(oldAll to so, newAll to sn)) {
                all.seconds += s.seconds; all.crossings += s.crossings; all.reversals += s.reversals; all.travel += s.travel
            }
            report.append("%4d %5.1f %8.0f%%        %6.2f %6d          %6.2f %6d\n".format(
                seed, so.seconds / 60f, 100f * high / samples, so.perMinute, so.reversals, sn.perMinute, sn.reversals))
        }
        report.append("razem %.1f min: stare %.2f przejść/min, %d zawróceń, droga %.0f; nowe %.2f przejść/min, %d zawróceń, droga %.0f".format(
            oldAll.seconds / 60f, oldAll.perMinute, oldAll.reversals, oldAll.travel,
            newAll.perMinute, newAll.reversals, newAll.travel))
        println(report)
        assertTrue("bitwy trwają: ${oldAll.seconds}", oldAll.seconds > 120f)
        assertTrue("histereza zmniejsza liczbę przejść:\n$report", newAll.crossings * 2 <= oldAll.crossings)
        assertTrue("najwyżej ok. 2 przejścia na minutę:\n$report", newAll.perMinute <= 2.2f)
    }

    @Test fun riseIsQuickAndFallWaitsForHold() {
        val f = BattleMixFollower()
        var t = 0f
        while (t < BattleMixFollower.RISE_S + 0.05f) { f.update(0.04f, 1f); t += 0.04f }
        assertEquals("pełne starcie po ~1,5 s", 1f, f.value, 0f)
        // Walka cichnie: przez HOLD_S nic się nie zmienia.
        t = 0f
        while (t < BattleMixFollower.HOLD_S - 0.1f) { f.update(0.04f, 0f); t += 0.04f }
        assertEquals("podtrzymanie", 1f, f.value, 0f)
        // Potem powolny spadek (FALL_S na pełną skalę).
        t = 0f
        while (t < 2f) { f.update(0.04f, 0f); t += 0.04f }
        assertEquals(1f - 2f / BattleMixFollower.FALL_S, f.value, 0.04f)
        t = 0f
        while (t < BattleMixFollower.FALL_S) { f.update(0.04f, 0f); t += 0.04f }
        assertEquals(0f, f.value, 0f)
    }

    @Test fun shortLullsKeepBattleVersion() {
        val f = BattleMixFollower()
        repeat(60) { f.update(0.04f, 0.8f) }
        // Wahania: 5 s przerwy, 1 s walki, i tak w kółko — miks nie schodzi poniżej szczytu.
        repeat(10) {
            repeat(125) { f.update(0.04f, 0f) }
            repeat(25) { f.update(0.04f, 0.8f) }
            assertEquals(0.8f, f.value, 1e-4f)
        }
        // Drobny spadek celu (w granicy tolerancji) też podtrzymuje.
        repeat(500) { f.update(0.04f, 0.7f) }
        assertEquals(0.8f, f.value, 1e-4f)
        // Wzrost w trakcie spadku od razu podnosi miks.
        repeat(250) { f.update(0.04f, 0f) }
        val low = f.value
        assertTrue(low < 0.8f)
        repeat(10) { f.update(0.04f, 1f) }
        assertTrue(f.value > low)
        f.reset()
        assertEquals(0f, f.value, 0f)
        f.update(0.04f, Float.NaN)
        assertEquals(0f, f.value, 0f)
    }
}
