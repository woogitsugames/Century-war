package com.centurywar.server

import com.centurywar.game.Battle
import com.centurywar.game.BattleConfig
import com.centurywar.game.BattleController
import com.centurywar.game.Bonuses
import com.centurywar.game.Mode
import com.centurywar.game.Replay
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale

/**
 * Ręczny benchmark granicy protokołu, uruchamiany wyłącznie przez SERVER_BENCH=true.
 * Ogromne HP bazy pozwala syntetycznej grze Endless dotrwać do twardego limitu.
 * Konfiguracja NIE jest zgłoszeniem akceptowanym przez API rankingu.
 */
@EnabledIfEnvironmentVariable(named = "SERVER_BENCH", matches = "true")
class ServerReplayBenchmarkTest {
    @Test
    fun najdluzszaPowtorka() {
        val config = BattleConfig(
            Mode.ENDLESS,
            seed = 121_432_000L,
            aiPower = 1f,
            playerBonuses = Bonuses(baseHpMul = 1_000_000_000f),
        )
        val recording = Battle(config)
        recording.playerAi = null
        val controller = BattleController(recording)

        while (!recording.over && recording.stepCount < Replay.MAX_STEPS) {
            // Decyzje są zapisywane jedyną legalną drogą, tak jak w grze.
            if (recording.pendingVariant != null) check(controller.variant(0))
            if (recording.pendingChoice != null) check(controller.doctrine(0))
            if (recording.pendingCards != null) check(controller.card(0))
            if (recording.stepCount % 14 == 0) {
                check(controller.stance(!recording.player.hold))
            }
            val before = recording.stepCount
            recording.step()
            check(recording.stepCount == before + 1) {
                "Brak postępu symulacji w kroku $before"
            }
            recording.fx.count = 0
        }

        assertEquals(Replay.MAX_STEPS, recording.stepCount, "Próba nie osiągnęła limitu kroków")
        val log = controller.log()
        assertTrue(log.commands.size in 30_000..Replay.MAX_COMMANDS,
            "Benchmark wymaga wielu zaakceptowanych komend")
        val encoded = Replay.encode(log)
        assertEquals(log, Replay.decode(encoded))

        // Dwie pełne weryfikacje rozgrzewają JIT i ścieżkę dekodowania.
        repeat(2) {
            val result = Replay.verify(config, Replay.decode(encoded))
            assertEquals(Replay.MAX_STEPS, result.steps)
            assertEquals(recording.score(), result.score)
        }

        val verifyMs = LongArray(5)
        val totalMs = LongArray(5)
        repeat(5) { index ->
            val started = System.nanoTime()
            val decoded = Replay.decode(encoded)
            val afterDecode = System.nanoTime()
            val result = Replay.verify(config, decoded)
            val finished = System.nanoTime()
            assertEquals(Replay.MAX_STEPS, result.steps)
            assertEquals(recording.score(), result.score)
            verifyMs[index] = (finished - afterDecode) / 1_000_000L
            totalMs[index] = (finished - started) / 1_000_000L
        }

        val cpu = runCatching {
            Files.readAllLines(Path.of("/proc/cpuinfo"))
                .firstOrNull { it.startsWith("model name") }
                ?.substringAfter(':')?.trim()
        }.getOrNull() ?: System.getProperty("os.arch")
        val quota = runCatching {
            Files.readString(Path.of("/sys/fs/cgroup/cpu.max")).trim()
        }.getOrNull() ?: "nieznany"
        val heapMb = Runtime.getRuntime().maxMemory() / (1024L * 1024L)

        fun summary(values: LongArray): String {
            val sorted = values.sorted()
            return "pomiary=${values.joinToString(",")} ms, mediana=${sorted[2]} ms, maksimum=${sorted[4]} ms"
        }

        println("SERVER_BENCH_121 cpu=$cpu, processors=${Runtime.getRuntime().availableProcessors()}, " +
            "quota=$quota, heap_max=${heapMb}MiB, java=${System.getProperty("java.version")}")
        println("SERVER_BENCH_121 steps=${log.steps}, commands=${log.commands.size}, " +
            "encoded_bytes=${encoded.toByteArray(Charsets.UTF_8).size}, wave=${recording.wave}")
        println("SERVER_BENCH_121 Replay.verify: ${summary(verifyMs)}")
        println("SERVER_BENCH_121 Replay.decode+verify: ${summary(totalMs)}")
        assertTrue(verifyMs.all { it >= 0L })
    }
}
