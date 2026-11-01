package com.centurywar.game

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Pomiar bajtów alokowanych przez wątek, po rozgrzaniu JIT. Nie obejmuje stworzenia bitwy.
 * Profil specjalnie nie przechodzi do nowej epoki (wybór doktryny tworzy ofertę).
 */
class AllocationTest {
    @Test fun battleStepAllocatesNoObjectsAfterWarmup() {
        // com.sun.management nie jest widoczne przy kompilacji z android.jar, więc przez refleksję.
        val mx: Any = Class.forName("java.lang.management.ManagementFactory").getMethod("getThreadMXBean").invoke(null)!!
        val api = runCatching { Class.forName("com.sun.management.ThreadMXBean") }.getOrNull()
        assumeTrue("ThreadMXBean musi obsługiwać pomiar bajtów", api != null && api.isInstance(mx) &&
            api.getMethod("isThreadAllocatedMemorySupported").invoke(mx) == true)
        api!!.getMethod("setThreadAllocatedMemoryEnabled", java.lang.Boolean.TYPE).invoke(mx, true)
        val allocated = api.getMethod("getThreadAllocatedBytes", java.lang.Long.TYPE)
        val b = Battle(BattleConfig(Mode.SKIRMISH, 8001L, 1f, startEra = 8, maxEra = 8))
        b.ai!!.thinkTimer = Float.MAX_VALUE
        b.player.hold = true; b.enemy.hold = true
        b.player.cap = 36
        b.enemy.cap = 36
        repeat(24) {
            b.spawn(b.player, Defs.unit(8, Role.HEAVY))
            b.spawn(b.enemy, Defs.unit(8, Role.HEAVY))
        }
        // Bez dodatkowych spawnów, awansu epoki i kontrolek test sprawdza gorącą pętlę.
        repeat(3000) { b.step(); b.fx.count = 0 }
        assertFalse("Pomiar wymaga aktywnej bitwy", b.over)
        val tid = Thread.currentThread().id
        val before = (allocated.invoke(mx, tid) as Long)
        repeat(10_000) { b.step(); b.fx.count = 0 }
        val bytes = (allocated.invoke(mx, tid) as Long) - before
        assertFalse("Bitwa nie może się zakończyć podczas pomiaru", b.over)
        if (System.getenv("PERF_REPORT") == "true") println("Battle.step 10000: $bytes B")
        // Mały margines dla deoptymalizacji JIT podczas pomiaru (poniżej 1 B/krok).
        assertTrue("Alokacje Battle.step: $bytes B / 10000 kroków", bytes in 0L..8192L)
    }
}
