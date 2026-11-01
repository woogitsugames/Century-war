package com.centurywar.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Regresje tekstów nowych ekranów i palet po przeglądzie 120. */
class LTestReview120 {
    @Test
    fun actionAndPaletteLabelsFit() {
        val keys = listOf(
            "wn_continue",
            "profile_export_action", "profile_import_action", "profile_restore_action",
            "bs104_stats", "bs104_chart",
            "access_palette_0", "access_palette_1", "access_palette_2", "access_palette_3",
        )
        for (key in keys) {
            val values = L.raw(key) ?: error("Brak klucza: " + key)
            assertEquals(key, L.CODES.size, values.size)
            for (i in values.indices) {
                assertTrue(key + "/" + L.CODES[i] + ": " + values[i],
                    values[i].length <= 18)
            }
        }
    }

    @Test
    fun reviewedTerminologyStaysConsistent() {
        assertEquals("Era change", L.raw("bs104_evolution")!![L.EN])
        assertEquals("Blue and amber", L.raw("access_palette_1")!![L.EN])
        assertEquals("Błękit i bursztyn", L.raw("access_palette_1")!![L.PL])
        for (i in L.CODES.indices) {
            val endless = L.raw("endless")!![i]
            val chart = L.raw("bh105_best_wave")!![i]
            assertTrue("Tryb Endless: " + L.CODES[i], chart.contains(endless, ignoreCase = true))
        }
    }
}
