package com.centurywar.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Każda zmiana konfiguracji, logu, wyniku lub stanu końcowego wymaga świadomej aktualizacji wzorca. */
class GoldenReplayTest {
    @Test fun pinnedResultsAndFullSnapshotsMatch() {
        assertTrue("Oczekiwano 8–10 niezależnych wzorców", GoldenFixtures.scenarios.size in 8..10)
        for (scenario in GoldenFixtures.scenarios) {
            val file = "golden/${scenario.id}.json"
            val stored = javaClass.classLoader.getResourceAsStream(file)?.bufferedReader(Charsets.UTF_8)?.use {
                it.readText()
            } ?: error("Brakuje wzorca ${file}; uruchom :sim:regenerateGolden -PgoldenReason='...'")
            assertEquals("Regresja symulacji: ${scenario.id}", stored, GoldenFixtures.render(scenario))
        }
    }
}
