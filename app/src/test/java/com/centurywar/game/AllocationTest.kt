package com.centurywar.game

import com.sun.management.ThreadMXBean
import java.lang.management.ManagementFactory
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Pomiar bajtów alokowanych przez wątek, po rozgrzaniu JIT. Nie obejmuje stworzenia bitwy.
 * Profil specjalnie nie przechodzi do nowej epoki (wybór doktryny tworzy ofertę).
 */
class AllocationTest {
    @Test fun battleStepAllocatesNoObjectsAfterWarmup() {
        val bean = ManagementFactory.getThreadMXBean() as? ThreadMXBean
        assumeTrue("ThreadMXBean musi obsługiwać pomiar bajtów", bean != null &&
            bean.isThreadAllocatedMemorySupported)
        val mx = bean!!
        if (!mx.isThreadAllocatedMemoryEnabled) mx.isThreadAllocatedMemoryEnabled = true
        val b = Battle(BattleConfig(Mode.SKIRMISH, 8001L, 1f, startEra = 8, maxEra = 8))
        b.ai!!.thinkTimer = Float.MAX_VALUE
        b.player.cap = 36
        b.enemy.cap = 36
        repeat(24) {
            b.spawn(b.player, Defs.unit(8, Role.HEAVY))
            b.spawn(b.enemy, Defs.unit(8, Role.HEAVY))
        }
        // Bez dodatkowych spawnów, awansu epoki i kontrolek test sprawdza gorącą pętlę.
        repeat(3000) { b.step(); b.fx.count = 0 }
        val tid = Thread.currentThread().id
        val before = mx.getThreadAllocatedBytes(tid)
        repeat(10_000) { b.step(); b.fx.count = 0 }
        val bytes = mx.getThreadAllocatedBytes(tid) - before
        if (System.getenv("PERF_REPORT") == "true") println("Battle.step 10000: $bytes B")
        // Mały margines dla deoptymalizacji JIT podczas pomiaru (poniżej 1 B/krok).
        assertTrue("Alokacje Battle.step: $bytes B / 10000 kroków", bytes in 0L..8192L)
    }
}
