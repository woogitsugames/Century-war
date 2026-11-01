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

    private val spec = Regex("%(?:\\d+\\$)?(?:\\.\\d+)?[dsf]")

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
    fun placeholderRegexSupportsPositionalArguments() {
        val samples = "%1\$s %2\$d %.1f %%"
        assertEquals(
            listOf("%1\$s", "%2\$d", "%.1f"),
            spec.findAll(samples.replace("%%", "")).map { it.value }.toList()
        )
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


    /** Regresje terminologii i formatowania po audycie tłumaczeń 88. */
    @Test
    fun audit88TerminologyAndFormatting() {
        val en = L.CODES.indexOf("en")
        val de = L.CODES.indexOf("de")
        val uk = L.CODES.indexOf("uk")
        val nl = L.CODES.indexOf("nl")
        val tr = L.CODES.indexOf("tr")
        val tower = Regex("\\btowers?\\b", RegexOption.IGNORE_CASE)
        val germanPercentWithoutSpace = Regex("""\d+%%?""")
        for (key in L.keys) {
            val translation = L.raw(key)!!
            assertTrue("$key/en: ${translation[en]}", !tower.containsMatchIn(translation[en]))
            assertTrue("$key/de: ${translation[de]}",
                !germanPercentWithoutSpace.containsMatchIn(translation[de]) &&
                    !translation[de].contains("%d%%"))
            assertTrue("$key/uk: ${translation[uk]}", !translation[uk].contains("XP"))
            assertTrue("$key/tr: ${translation[tr]}", !translation[tr].contains("Evrim"))
        }
        assertEquals("Steam Age", L.text("era_steam", en))
        assertEquals("Silicon Age", L.text("era_silicon", en))
        assertTrue(L.text("ach_steam_twins_d", en).contains("Steam Age"))
        assertTrue(L.text("ach_silicon_twins_d", en).contains("Silicon Age"))
        assertTrue(L.text("tip53_evolve", en).startsWith("Evolve to"))
        assertTrue(L.text("ach_steam_twins_d", uk).contains("Доби пари"))
        assertTrue(L.text("ach_silicon_twins_d", uk).contains("Доби кремнію"))
        assertTrue(L.text("ach_steam_twins_d", nl).contains("Stoomtijd"))
        assertTrue(L.text("ach_silicon_twins_d", nl).contains("Siliciumtijd"))
        assertEquals("ОД", L.text("xp", uk))
        assertEquals("ОЗ", L.text("cx_hp", uk))
        assertEquals("Çağ atla", L.text("evolve", tr))
        assertEquals("İlerle", L.text("advance", tr))
        assertTrue(L.text("tip53_intro", de).contains("Schaltfläche"))
        assertTrue(L.text("hint_missions", de).contains("Belohnungen ab"))
        for (i in L.CODES.indices) {
            val paid = L.raw("ms_login_paid")!![i]
            assertTrue("ms_login_paid/${L.CODES[i]}", ":" !in paid && "%s" in paid)
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

    /** Kontrola krótkich etykiet przycisków i zakładek z części 53–67. */
    @Test
    fun passTwoInteractiveLabelsFitWithin18Characters() {
        val keys = listOf(
            "tip53_barrage_title", "tip53_speed_title", "tip53_queue_title",
            "cx62_eras", "cx62_units", "cx62_variants", "cx62_towers",
            "cx62_abilities", "cx62_doctrines", "cx62_weather",
            "cx62_modifiers", "cx62_cards", "cx62_bosses", "cx62_no_variants",
            "pause_quick", "report_problem", "report_send", "report_cancel",
        )
        for (key in keys) {
            val translations = L.raw(key) ?: error("Brak klucza $key")
            for (i in L.CODES.indices) {
                val value = translations[i]
                assertTrue(key + "/" + L.CODES[i] + ": " + value + " (" + value.length + ")",
                    value.length <= 18)
            }
        }
    }

    @Test
    fun passTwoBarrageTitleUsesCanonicalTranslation() {
        val canonical = L.raw("cx_barrage")!!
        val tooltip = L.raw("tip53_barrage_title")!!
        for (i in L.CODES.indices) {
            assertEquals(L.CODES[i], canonical[i], tooltip[i])
        }
    }

    /** Przypadki 1/2/5/21/22/25 i turecki znak procentu przed liczbą. */
    @Test
    fun passTwoMissionPluralsAndTurkishPercentOrder() {
        L.setCode("pl")
        for ((count, suffix) in listOf(
            1 to "bitwę", 2 to "bitwy", 5 to "bitew",
            21 to "bitew", 22 to "bitwy", 25 to "bitew",
        )) {
            assertEquals("Wygraj $count $suffix przy niepogodzie",
                L.plural("ms_weather_wins", count.toLong()))
        }
        L.setCode("uk")
        for ((count, suffix) in listOf(
            1 to "битву в негоду", 2 to "битви в негоду", 5 to "битв у негоду",
            21 to "битву в негоду", 22 to "битви в негоду", 25 to "битв у негоду",
        )) {
            assertEquals("Виграй $count $suffix",
                L.plural("ms_weather_wins", count.toLong()))
        }
        L.setCode("tr")
        val rally = L.f("cx62_rally_d", 7, 3, 12)
        assertTrue(rally, rally.contains("+%7"))
        assertTrue(rally, !rally.contains("%d") && !rally.contains("%%"))
        val rain = L.f("cx62_rain_d", 10, 20, 30)
        assertTrue(rain, rain.contains("−%10"))
        assertTrue(rain, rain.contains("−%20"))
        assertTrue(rain, rain.contains("−%30"))
        for (key in listOf(
            "ms_weather_wins", "ms_custom_skirmish", "ms_endless_bosses",
            "ms_unit_upgrades", "ms_replays_watched", "ms_endless_cards",
        )) {
            for (i in L.CODES.indices) {
                L.lang = i
                for (n in longArrayOf(1L, 2L, 5L, 21L, 22L, 25L)) {
                    val result = L.plural(key, n)
                    assertTrue(key + "/" + L.CODES[i] + "/" + n + ": " + result,
                        result.contains(n.toString()) && !result.contains('|') && !result.contains("%d"))
                }
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


/**
 * Kadry lokalizacji 720p dla zlecenia 78. Osobna klasa w LTest.kt,
 * bez edytowania współdzielonego ScreenshotTest.kt.
 */
@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
@org.robolectric.annotation.Config(sdk = [35])
class ScreenshotTestTranslation78Test {
    private val out = java.io.File("build/screenshots").apply { mkdirs() }

    private fun game(): Game {
        val host = object : Host {
            override fun vibrate(ms: Long) {}
            override fun requestText(title: String, current: String, onResult: (String) -> Unit) {}
            override fun applyRenderScale(scale: Float) {}
            override fun applyFrameRate(fps: Int) {}
            override fun exit() {}
        }
        val file = java.io.File.createTempFile("pass78", ".json").apply { delete() }
        val g = Game(host, com.centurywar.meta.Profile(file).apply {
            tutorialDone = true
            medals = 340
            hintsSeen = Long.MAX_VALUE
        }, Quality.LOW)
        g.density = 1.8f
        g.resize(1600, 720, 1600, 720)
        return g
    }

    private fun field(obj: Any, name: String) =
        obj.javaClass.getDeclaredField(name).apply { isAccessible = true }

    private fun shot(g: Game, code: String, screen: String) {
        g.finishScreenTransition()
        val bitmap = android.graphics.Bitmap.createBitmap(
            g.width, g.height, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        repeat(3) { g.frame(canvas, 1f / 60f) }
        val image = java.io.File(out, "phone720_" + code + "_" + screen + ".png")
        image.outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
        org.junit.Assert.assertTrue("Pusty zrzut: " + image.name, image.length() > 1000L)
        if (screen == "battle" || screen == "pause") {
            // Dodatkowa kopia w raporcie JUnit trafia do artefaktów CI.
            val artifactDir = java.io.File("build/test-results/testDebugUnitTest/l10n88")
                .apply { mkdirs() }
            image.copyTo(java.io.File(artifactDir, image.name), overwrite = true)
        }
    }

    internal fun capture(code: String, onlyBattle: Boolean = false) {
        val previous = L.lang
        try {
            val g = game()
            g.profile.lang = code
            L.setCode(code)

            val paused = com.centurywar.screens.BattleScreen(
                g, com.centurywar.game.BattleConfig(
                    com.centurywar.game.Mode.SKIRMISH, 7801L, 1.2f,
                    weather = com.centurywar.game.Weather.NIGHT))
            g.setScreen(paused)
            val battle = field(paused, "battle").get(paused) as com.centurywar.game.Battle
            battle.time = 125f
            battle.player.kills = 17
            battle.player.goldEarned = 1426f
            shot(g, code, "battle")
            paused.onClick(field(paused, "pauseBtn").get(paused) as Button)
            (field(g, "noticeQueue").get(g) as MutableCollection<*>).clear()
            (field(g, "noticeIcons").get(g) as MutableCollection<*>).clear()
            field(g, "achievementSeconds").setFloat(g, 0f)
            shot(g, code, "pause")
            if (onlyBattle) return

            g.profile.hintsSeen = 0L
            val weather = com.centurywar.screens.BattleScreen(
                g, com.centurywar.game.BattleConfig(
                    com.centurywar.game.Mode.SKIRMISH, 7802L, 1f,
                    weather = com.centurywar.game.Weather.WIND))
            g.setScreen(weather)
            val hint = field(weather, "hints").get(weather) as com.centurywar.screens.HintOverlay
            hint.update(0.26f)
            org.junit.Assert.assertEquals(
                com.centurywar.screens.HintOverlay.WEATHER, hint.activeId)
            shot(g, code, "hint_weather")

            val codex = com.centurywar.screens.CodexScreen(g, 5)
            g.setScreen(codex)
            codex.showCategory(4)
            shot(g, code, "codex_abilities")
            codex.showCategory(8)
            shot(g, code, "codex_cards")

            val missions = com.centurywar.meta.Missions(g.profile)
                .refresh(System.currentTimeMillis())
            missions.daily.clear()
            missions.weekly.clear()
            missions.daily.add(com.centurywar.meta.MissionItem(
                "pass78_weather", com.centurywar.meta.MissionType.WEATHER_WINS, 1, 5))
            missions.daily.add(com.centurywar.meta.MissionItem(
                "pass78_custom", com.centurywar.meta.MissionType.CUSTOM_SKIRMISH, 2, 5))
            missions.daily.add(com.centurywar.meta.MissionItem(
                "pass78_bosses", com.centurywar.meta.MissionType.ENDLESS_BOSSES, 5, 5))
            missions.weekly.add(com.centurywar.meta.MissionItem(
                "pass78_upgrades", com.centurywar.meta.MissionType.UNIT_UPGRADES, 2, 5))
            missions.weekly.add(com.centurywar.meta.MissionItem(
                "pass78_replays", com.centurywar.meta.MissionType.REPLAYS_WATCHED, 5, 5))
            missions.weekly.add(com.centurywar.meta.MissionItem(
                "pass78_cards", com.centurywar.meta.MissionType.ENDLESS_CARDS, 21, 5))
            g.setScreen(com.centurywar.screens.MissionsScreen(g))
            shot(g, code, "missions")
        } finally {
            L.lang = previous
        }
    }

    @org.junit.Test fun german720p() { capture("de") }
    @org.junit.Test fun turkish720p() { capture("tr") }
    @org.junit.Test fun ukrainian720p() { capture("uk") }
}

/** Zlecenie 88: kadry bitwy i pauzy w trzech językach, również w filtrze *LTest*. */
@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
@org.robolectric.annotation.Config(sdk = [35])
class LTestScreens88 {
    @org.junit.Test fun germanBattleAndPause() =
        ScreenshotTestTranslation78Test().capture("de", onlyBattle = true)

    @org.junit.Test fun turkishBattleAndPause() =
        ScreenshotTestTranslation78Test().capture("tr", onlyBattle = true)

    @org.junit.Test fun ukrainianBattleAndPause() =
        ScreenshotTestTranslation78Test().capture("uk", onlyBattle = true)
}
