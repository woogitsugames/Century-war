package com.centurywar.engine

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class LTest {
    private val saved = L.lang

    @After
    fun restore() { L.lang = saved }

    /** Klucze, które celowo mogą nie mieć przekładu (zastępuje je angielski). */
    private val allowedFallback = emptySet<String>()

    private val spec = Regex("%(\\.\\d+)?[dsf]")

    @Test
    fun everyKeyHasAllLanguages() {
        assertEquals(L.CODES.size, L.NAMES.size)
        val missing = mutableListOf<String>()
        for (k in L.keys) {
            val e = L.raw(k)!!
            assertTrue("$k: więcej kolumn niż języków", e.size <= L.CODES.size)
            for (i in L.CODES.indices) {
                if (e.getOrNull(i).isNullOrEmpty() && k !in allowedFallback) missing += "$k/${L.CODES[i]}"
            }
            assertTrue("$k: brak angielskiego", !e.getOrNull(L.EN).isNullOrEmpty())
        }
        assertTrue("Brak przekładów: $missing", missing.isEmpty())
    }

    /** hashMapOf po cichu nadpisuje powtórzony klucz (np. po scaleniu), więc sprawdzamy źródło. */
    @Test
    fun noDuplicateKeysInSource() {
        val src = listOf("src/main/java/com/centurywar/engine/L.kt", "app/src/main/java/com/centurywar/engine/L.kt")
            .map { java.io.File(it) }.firstOrNull { it.exists() } ?: return
        val keys = Regex("^\\s*\"(\\w+)\" to t\\(", RegexOption.MULTILINE).findAll(src.readText()).map { it.groupValues[1] }.toList()
        val dups = keys.groupingBy { it }.eachCount().filter { it.value > 1 }.keys
        assertTrue("Powtórzone klucze w L.kt: $dups", dups.isEmpty())
        assertEquals(keys.size, L.keys.size)
    }

    @Test
    fun placeholdersMatchEnglish() {
        val bad = mutableListOf<String>()
        for (k in L.keys) {
            val entry = L.raw(k)!!
            val englishForms = entry[L.EN].split('|')
            for (i in entry.indices) {
                if (entry[i].isEmpty()) continue
                for ((formIndex, variant) in entry[i].split('|').withIndex()) {
                    val en = englishForms[formIndex.coerceAtMost(englishForms.lastIndex)]
                    val enSpecs = spec.findAll(en.replace("%%", "")).map { it.value }.toList()
                    val specs = spec.findAll(variant.replace("%%", "")).map { it.value }.toList()
                    val enPct = Regex("%%").findAll(en).count()
                    val pct = Regex("%%").findAll(variant).count()
                    if (specs != enSpecs || pct != enPct) {
                        bad += k + "/" + L.CODES[i] + "#" + formIndex + ": '" + variant + "'"
                    }
                }
            }
        }
        assertTrue("Niezgodne placeholdery: $bad", bad.isEmpty())
    }

    @Test
    fun pluralEntriesHaveAllForms() {
        for (k in L.keys) {
            val entry = L.raw(k)!!
            if (entry.none { it.contains('|') }) continue
            for (i in L.CODES.indices) {
                val forms = entry[i].split('|')
                val expected = when (L.CODES[i]) {
                    "pl", "uk" -> 3
                    "tr" -> 1
                    else -> 2
                }
                assertEquals(k + "/" + L.CODES[i], expected, forms.size)
                assertTrue(k + "/" + L.CODES[i] + ": pusta forma", forms.all { it.isNotEmpty() })
                assertTrue(k + "/" + L.CODES[i] + ": separator", !L.text(k, i).contains('|'))
            }
        }
    }

    @Test
    fun pluralRules() {
        val samples = longArrayOf(0, 1, 2, 4, 5, 11, 12, 14, 21, 22, 25, 101, 111, 112)
        val pl = intArrayOf(2, 0, 1, 1, 2, 2, 2, 2, 2, 1, 2, 2, 2, 2)
        val uk = intArrayOf(2, 0, 1, 1, 2, 2, 2, 2, 0, 1, 2, 0, 2, 2)
        for (i in samples.indices) {
            assertEquals("pl/" + samples[i], pl[i], L.pluralIndex(0, samples[i]))
            assertEquals("uk/" + samples[i], uk[i], L.pluralIndex(7, samples[i]))
        }
        assertEquals(0, L.pluralIndex(4, 0)); assertEquals(0, L.pluralIndex(4, 1))
        assertEquals(1, L.pluralIndex(4, 2))
        assertEquals(0, L.pluralIndex(1, 1)); assertEquals(1, L.pluralIndex(1, 2))
        assertEquals(0, L.pluralIndex(9, 1)); assertEquals(0, L.pluralIndex(9, 5))
    }

    @Test
    fun pluralExamplesAndFallback() {
        L.lang = L.PL
        assertEquals("Wygraj 1 bitwę", L.plural("ms_wins", 1L))
        assertEquals("Wygraj 2 bitwy", L.plural("ms_wins", 2L))
        assertEquals("Wygraj 5 bitew", L.plural("ms_wins", 5L))
        assertEquals("Pokonaj 2 wrogów", L.plural("ms_kills", 2L))
        assertEquals("Zdobądź 1 nową gwiazdkę", L.plural("ms_stars", 1L))
        assertEquals("Zdobądź 2 nowe gwiazdki", L.plural("ms_stars", 2L))
        assertEquals("Zdobądź 5 nowych gwiazdek", L.plural("ms_stars", 5L))
        assertEquals("Wygraj z Mgłą 2 razy", L.plural("ms_modifier_wins", 2L, "Mgłą", 2))
        assertEquals("1 medal", L.plural("medals_n", 1L))
        assertEquals("2 medale", L.plural("medals_n", 2L))
        assertEquals("5 medali", L.plural("medals_n", 5L))
        assertEquals("21 medali", L.plural("medals_n", 21L))
        assertEquals("Dziś: +1 medal", L.f("ms_login_paid", L.plural("medals_n", 1L)))
        assertEquals("Wygraj %d bitew", L["ms_wins"])
        assertTrue(!L.f("ms_wins", 1).contains('|'))
        L.lang = L.EN
        assertEquals("Win 1 battle", L.plural("ms_wins", 1L))
        assertEquals("Win 2 battles", L.plural("ms_wins", 2L))
        L.lang = 7
        assertEquals("21 медаль", L.plural("medals_n", 21L))
        assertEquals("5 медалей", L.plural("medals_n", 5L))
    }

    @Test
    fun pluralNeverRendersSeparators() {
        for (i in L.CODES.indices) {
            L.lang = i
            for (k in L.keys) {
                if (L.raw(k)!!.none { it.contains('|') }) continue
                for (n in longArrayOf(1L, 2L, 5L)) {
                    val text = if (k == "ms_modifier_wins") L.plural(k, n, "x", n)
                        else L.plural(k, n)
                    assertTrue(k + "/" + L.CODES[i] + "/" + n, !text.contains('|'))
                }
            }
        }
    }

    @Test
    fun formatWorksInEveryLanguage() {
        for (i in L.CODES.indices) {
            L.lang = i
            for (k in L.keys) {
                val en = L.raw(k)!![L.EN]
                val specs = spec.findAll(en.replace("%%", "")).map { it.value }.toList()
                if (specs.isEmpty() && !en.contains("%%")) continue
                val args = specs.map<String, Any> { when (it.last()) { 'd' -> 7; 's' -> "x"; else -> 1.5f } }.toTypedArray()
                val out = L.f(k, *args)
                assertTrue("$k/${L.CODES[i]}: '$out'", !out.contains("%d") && !out.contains("%s") && !out.contains('|'))
            }
        }
    }


    /** Krótkie etykiety interakcji; tytuły i dłuższe opisy mogą się zawijać. */
    @Test
    fun buttonLabelsFitWithin18Characters() {
        val buttonKeys = setOf(
            "back",
            "play",
            "start",
            "campaign",
            "daily",
            "weekly",
            "skirmish",
            "research",
            "rankings",
            "settings",
            "accessibility",
            "replay",
            "replay_list",
            "replay_pin",
            "replay_delete",
            "replay_pause",
            "replay_play",
            "replay_follow",
            "replay_camera",
            "q_auto",
            "q_low",
            "q_med",
            "q_high",
            "fps_max",
            "on",
            "off",
            "show_fps",
            "reset",
            "global_allow",
            "global_cancel",
            "global_new_id",
            "global_local",
            "global_period_day",
            "global_period_week",
            "global_period_all",
            "global_top",
            "global_around",
            "pgs_sign_in",
            "pgs_sync",
            "evolve",
            "turrets",
            "armory",
            "hold",
            "advance",
            "rally",
            "resume",
            "quit",
            "restart",
            "continue",
            "sell",
            "upgrade",
            "choose_card",
            "tut_skip",
            "tut_next",
            "tut_go",
            "ms_claim",
            "ms_claimed",
            "hint_ok",
            "hint_replay",
            "hint_reset",
            "hint_confirm",
            "sk_defaults",
            "sk_shuffle",
            "sk_random",
            "sk_classic",
            "sk_custom",
            "sk_start",
            "sk_end",
            "sk_none",
            "sk_win",
            "sk_loss",
            "resume_yes",
            "resume_no"
        )
        for (key in buttonKeys) {
            val translations = L.raw(key)
            assertTrue("Brak etykiety przycisku: $key", translations != null)
            for (lang in L.CODES.indices) {
                val value = translations!![lang]
                assertTrue(
                    "$key/${L.CODES[lang]}: $value (${value.length} > 18)",
                    value.length <= 18
                )
            }
        }
    }

    @Test
    fun fallbackAndCodes() {
        assertEquals("missing_key", L["missing_key"])
        assertEquals("Back", L.text("back", 99))
        L.setCode("de"); assertEquals("Zurück", L["back"])
        L.setCode("uk"); assertEquals("Назад", L["back"])
        L.setCode("pt-BR"); assertEquals("Voltar", L["back"])
        L.setCode("pl"); assertTrue(L.polish); assertEquals("Wstecz", L["back"])
        L.polish = false; assertEquals("Back", L["back"])
        val def = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            L.setCode(""); assertEquals("Geri", L["back"])
            Locale.setDefault(Locale.forLanguageTag("ja-JP"))
            L.setCode(""); assertEquals("Back", L["back"])
            L.setCode("xx"); assertEquals("Back", L["back"])
        } finally {
            Locale.setDefault(def)
        }
    }
}
