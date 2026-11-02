package com.centurywar.engine

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Regresje zlecenia 136: krótkie etykiety i znaczenie nowych tłumaczeń. */
class LTestReview136 {
    private val originalLanguage = L.lang

    @After
    fun restoreLanguage() { L.lang = originalLanguage }

    @Test
    fun shortActionAndSelectorLabelsFit() {
        val keys = listOf(
            "banner_open", "banner_equip", "banner_equipped",
            "banner_unlocked", "banner_locked",
            "campaign126_difficulty", "campaign126_easy",
            "campaign126_normal", "campaign126_hard",
            "cx127_economist", "cx127_sieger", "cx127_balanced",
            "cx134_tab", "first_play", "evo139_go", "evo139_wait",
            "turret140_dmg", "turret140_reload", "turret140_cost",
        )
        for (key in keys) {
            val values = requireNotNull(L.raw(key)) { "Brak klucza: $key" }
            assertEquals(key, L.CODES.size, values.size)
            for (index in values.indices) {
                assertTrue("$key/${L.CODES[index]}: ${values[index]}",
                    values[index].length <= 18)
            }
        }
    }

    @Test
    fun reviewedTermsMatchExistingGameVocabulary() {
        assertEquals("Siege Expert", L.raw("cx127_sieger")!![L.EN])
        assertEquals("Sitiador", L.raw("cx127_sieger")!![6])
        assertEquals("Tactician", L.raw("cx127_balanced")!![L.EN])
        assertTrue(L.raw("banner_req_9")!![2].contains("Tageschallenges"))
        assertTrue(L.raw("banner_req_11")!![9].contains("günlük mücadele"))
        assertTrue(L.raw("ach_modifier_victories_10_d")!![8]
            .contains("modificatoren"))
        assertTrue(L.raw("ms_double_mod_wins")!![8].contains("modificatoren"))
        assertTrue(L.raw("wn_135_profile")!![7].contains("резервна копія"))
    }

    @Test
    fun formattedHintsKeepArgumentsAndPercentSigns() {
        for (lang in L.CODES.indices) {
            L.lang = lang
            val stats = L.f("turret140_stats", 10, 20, 100, 135)
            assertTrue("${L.CODES[lang]}: $stats", stats.contains("10") && stats.contains("20"))
            assertTrue("${L.CODES[lang]}: $stats", stats.contains("100") && stats.contains("135"))
            val tip = L.f("turret140_tip", 250)
            assertTrue("${L.CODES[lang]}: $tip", tip.contains("250") && tip.contains("35"))
            assertTrue("${L.CODES[lang]}: $tip", tip.contains("%"))
            val elite = L.f("elite141_locked", L["r_elite"], 60)
            assertTrue("${L.CODES[lang]}: $elite", elite.contains("60") && elite.contains(L["r_elite"]))
            val locked = L.f("cp143_locked", 8)
            assertTrue("${L.CODES[lang]}: $locked", locked.contains("8"))
        }
    }
}
