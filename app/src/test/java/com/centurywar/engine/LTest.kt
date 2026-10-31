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
            val e = L.raw(k)!!
            val en = e[L.EN]
            val enSpecs = spec.findAll(en.replace("%%", "")).map { it.value }.toList()
            val enPct = Regex("%%").findAll(en).count()
            for (i in e.indices) {
                val s = e[i]
                if (s.isEmpty()) continue
                val specs = spec.findAll(s.replace("%%", "")).map { it.value }.toList()
                val pct = Regex("%%").findAll(s).count()
                if (specs != enSpecs || pct != enPct) bad += "$k/${L.CODES[i]}: '$s'"
            }
        }
        assertTrue("Niezgodne placeholdery: $bad", bad.isEmpty())
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
                assertTrue("$k/${L.CODES[i]}: '$out'", !out.contains("%d") && !out.contains("%s"))
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
