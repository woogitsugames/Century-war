package com.centurywar.game

import org.junit.Assert.assertArrayEquals
import org.junit.Test
import java.security.MessageDigest

/** Dwa niezależne silniki: stary skan slotów i indeks żywych jednostek. */
class BattleIndexTest {
    private fun verify(cfg: BattleConfig, steps: Int) {
        val old = Battle(cfg).apply { useIndexedScan = false }
        val optimized = Battle(cfg)
        old.playerAi = Ai(old, 0)
        optimized.playerAi = Ai(optimized, 0)
        repeat(steps) { i ->
            old.step(); optimized.step()
            old.fx.count = 0; optimized.fx.count = 0
            if ((i + 1) % 600 == 0) {
                val expected = BattleSnapshot.encode(old)
                val actual = BattleSnapshot.encode(optimized)
                assertArrayEquals("Krok ${i + 1}: niezgodny zrzut", expected, actual)
                if (System.getenv("PERF_REPORT") == "true" && i + 1 == steps) {
                    val digest = MessageDigest.getInstance("SHA-256").digest(expected)
                    println("snapshot sha256 ${cfg.mode} steps=$steps: " +
                        digest.joinToString("") { "%02x".format(it.toInt() and 0xff) })
                }
            }
        }
    }

    @Test fun campaignStage36IsBitIdentical() =
        verify(Campaign.config(Campaign.stages.last(), Bonuses(), 3737L), 6000)

    @Test fun skirmishIsBitIdentical() =
        verify(BattleConfig(Mode.SKIRMISH, 3738L, 1.1f), 9000)

    @Test fun endlessIsBitIdentical() =
        verify(BattleConfig(Mode.ENDLESS, 3739L, 1f), 9000)
}
