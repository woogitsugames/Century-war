package com.centurywar.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.Locale

/**
 * Odczyt kluczy dodanych w turze 92–115 bez zakładania prefiksu nazwy klucza.
 * W gałęziach roboczych tej tury funkcje mogą jeszcze nie istnieć.
 */
internal object Review116Scope {
    private val sections = Regex("""(?m)^\s*private\s+fun\s+part(\d+)\s*\(""")
    private val entries = Regex("""(?m)^\s*"(\w+)"\s+to\s+t\(""")

    fun keys(): List<String> {
        val sourceFile = listOf(
            File("src/main/java/com/centurywar/engine/L.kt"),
            File("app/src/main/java/com/centurywar/engine/L.kt"),
        ).firstOrNull { it.isFile } ?: return emptyList()

        val source = sourceFile.readText()
        val parts = sections.findAll(source).toList()
        val result = ArrayList<String>()
        for (i in parts.indices) {
            val number = parts[i].groupValues[1].toIntOrNull() ?: continue
            if (number !in 92..115) continue
            val from = parts[i].range.last + 1
            val until = parts.getOrNull(i + 1)?.range?.first ?: source.length
            result.addAll(entries.findAll(source.substring(from, until)).map { it.groupValues[1] })
        }
        return result
    }
}

/** Kontrola nowo dodanych tłumaczeń wobec glosariusza ze zlecenia 88. */
class LTestReview116 {
    private val formatSpec = Regex("""%(?:\d+\$)?(?:\.\d+)?[dsf]""")
    private val escapedPercent = Regex("%%")

    private fun reviewKeys(): List<String> {
        val keys = Review116Scope.keys()
        assumeTrue("Nie ma jeszcze part92()–part115() w gałęzi bazowej", keys.isNotEmpty())
        return keys
    }

    @Test
    fun newEntriesHaveAllLanguagesAndIdenticalPlaceholders() {
        val keys = reviewKeys()
        assertEquals("Powtórzone klucze w częściach 92–115", keys.size, keys.toSet().size)

        for (key in keys) {
            val row = L.raw(key)
            assertNotNull("Brak klucza w tabeli runtime: " + key, row)
            row!!
            assertEquals(key + ": liczba języków", L.CODES.size, row.size)

            val plural = row.any { '|' in it }
            val englishForms = row[L.EN].split('|')
            for (i in L.CODES.indices) {
                val code = L.CODES[i]
                val value = row[i]
                assertTrue(key + "/" + code + ": pusty przekład", value.isNotBlank())
                val forms = value.split('|')
                if (plural) {
                    val expected = when (code) {
                        "pl", "uk" -> 3
                        "tr" -> 1
                        else -> 2
                    }
                    assertEquals(key + "/" + code + ": liczba form", expected, forms.size)
                }
                for (j in forms.indices) {
                    val english = englishForms[j.coerceAtMost(englishForms.lastIndex)]
                    val expected = formatSpec.findAll(english.replace("%%", ""))
                        .map { it.value }.toList()
                    val actual = formatSpec.findAll(forms[j].replace("%%", ""))
                        .map { it.value }.toList()
                    assertEquals(key + "/" + code + "#" + j + ": argumenty", expected, actual)
                    assertEquals(key + "/" + code + "#" + j + ": znaki procentu",
                        escapedPercent.findAll(english).count(),
                        escapedPercent.findAll(forms[j]).count())
                }
            }
        }
    }

    @Test
    fun newEntriesRespectGlossary88() {
        val keys = reviewKeys()
        val tower = Regex("""\btowers?\b""", RegexOption.IGNORE_CASE)
        val badGermanPercent = Regex("""(?:\d+|%d|%f)%%?""")
        val ukrainianLatinResources = Regex("""\b(?:XP|HP)\b""")
        val wrongDutchAges = Regex("""\b(?:stoomtijdperk|siliciumtijdperk)\b""",
            RegexOption.IGNORE_CASE)
        val wrongTurkishPercent = Regex("""\d+%|%d\s*%%""")
        val canonicalKeys = listOf(
            "cx_turret", "cx_barrage", "rally", "evolve", "advance",
            "era_steam", "era_silicon", "endless", "daily", "weekly", "skirmish",
        )

        for (key in keys) {
            val row = L.raw(key)!!
            val en = row[L.EN]
            assertFalse(key + "/en: tower zamiast turret", tower.containsMatchIn(en))
            assertFalse(key + "/en: Steam Era", en.contains("Steam Era", ignoreCase = true))
            assertFalse(key + "/en: Silicon Era", en.contains("Silicon Era", ignoreCase = true))
            assertFalse(key + "/de: procent bez odstępu",
                badGermanPercent.containsMatchIn(row[2]))
            assertFalse(key + "/uk: łaciński skrót zasobu",
                ukrainianLatinResources.containsMatchIn(row[7]))
            assertFalse(key + "/nl: niekanoniczna nazwa epoki",
                wrongDutchAges.containsMatchIn(row[8]))
            assertFalse(key + "/tr: Evrim", row[9].contains("Evrim", ignoreCase = true))
            assertFalse(key + "/tr: znak procentu po liczbie",
                wrongTurkishPercent.containsMatchIn(row[9]))

            // Samodzielne nazwy powinny zgadzać się z istniejącym glosariuszem
            // we wszystkich dziesięciu językach.
            for (canonicalKey in canonicalKeys) {
                val canonical = L.raw(canonicalKey) ?: continue
                if (en != canonical[L.EN]) continue
                for (i in L.CODES.indices) {
                    assertEquals(key + "/" + L.CODES[i] + " = " + canonicalKey,
                        canonical[i], row[i])
                }
            }
        }
    }


    /** Konkretne regresje znalezione podczas przeglądu gałęzi 104/105/106/108. */
    @Test
    fun newTextUsesCanonicalNamesForGameplayConcepts() {
        reviewKeys()

        // Wykres rozwoju epoki nie może mylić awansu z postawą armii "Advance".
        L.raw("bs104_evolution")?.let { row ->
            assertFalse("bs104_evolution/en: 'Advance' to postawa armii",
                row[L.EN].contains("advance", ignoreCase = true))
        }

        // Nazwa trybu nie powinna pozostawać w języku angielskim w lokalizacjach.
        L.raw("bh105_best_wave")?.let { row ->
            for (i in L.CODES.indices) {
                if (i == L.EN) continue
                assertFalse("bh105_best_wave/" + L.CODES[i] + ": Endless bez tłumaczenia",
                    row[i].contains("Endless", ignoreCase = true))
            }
        }

        // W ogłoszeniu nowości należy użyć tych samych nazw wież co w Kodeksie.
        L.raw("wn_010_iron")?.let { row ->
            val canonical = L.raw("cx_turret")!!
            for (i in L.CODES.indices) {
                assertTrue("wn_010_iron/" + L.CODES[i] + ": wieża musi być " + canonical[i],
                    row[i].contains(canonical[i], ignoreCase = true))
            }
        }

        // Karta odnawia dokładnie dwie zdolności. Nie należy ich nazywać
        // "atakiem", "salwą" ani nazwą badania odblokowującego zdolność.
        L.raw("ec_boss_recall_d")?.let { row ->
            val barrage = L.raw("cx_barrage")!!
            val rally = L.raw("rally")!!
            for (i in L.CODES.indices) {
                val code = L.CODES[i]
                assertTrue("ec_boss_recall_d/" + code + ": brak " + barrage[i],
                    row[i].contains(barrage[i], ignoreCase = true))
                assertTrue("ec_boss_recall_d/" + code + ": brak " + rally[i],
                    row[i].contains(rally[i], ignoreCase = true))
            }
        }
    }

    @Test
    fun newPluralsAndShortActionLabels() {
        val keys = reviewKeys()
        // Wyłącznie rzeczywiste przyciski. Np. profile_backup_export jest
        // opisem wiersza ustawień, a nie krótką etykietą akcji.
        val shortActions = setOf(
            "wn_continue", "profile_export_action", "profile_import_action",
            "profile_restore_action", "bs104_stats", "bs104_chart",
        )
        val saved = L.lang
        try {
            for (key in keys) {
                val row = L.raw(key)!!
                if (key in shortActions) {
                    for (i in L.CODES.indices) {
                        val value = row[i]
                        assertTrue(key + "/" + L.CODES[i] + ": etykieta ma " +
                            value.length + " znaków (maks. 18)", value.length <= 18)
                    }
                }

                if (row.none { '|' in it }) continue
                for (i in L.CODES.indices) {
                    val forms = row[i].split('|')
                    // Przypadki z kilkoma argumentami sprawdza test placeholderów.
                    if (!forms.all { form ->
                        formatSpec.findAll(form).map { it.value }.toList() == listOf("%d")
                    }) continue

                    L.lang = i
                    for (n in longArrayOf(1L, 2L, 5L, 21L, 22L, 25L)) {
                        val index = L.pluralIndex(i, n).coerceAtMost(forms.lastIndex)
                        val expected = String.format(Locale.ROOT, forms[index], n)
                        assertEquals(key + "/" + L.CODES[i] + "/" + n,
                            expected, L.plural(key, n))
                    }
                }
            }
        } finally {
            L.lang = saved
        }
    }
}
