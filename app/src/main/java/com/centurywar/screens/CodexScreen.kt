package com.centurywar.screens

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.centurywar.engine.Button
import com.centurywar.engine.Game
import com.centurywar.engine.Icons
import com.centurywar.engine.L
import com.centurywar.engine.Screen
import com.centurywar.engine.Ui
import com.centurywar.game.Battle
import com.centurywar.game.BattleConfig
import com.centurywar.game.Body
import com.centurywar.game.BattleRenderer
import com.centurywar.game.Bonuses
import com.centurywar.game.Defs
import com.centurywar.game.Weather
import com.centurywar.game.EndlessBosses
import com.centurywar.game.EndlessWaveTheme
import com.centurywar.game.Mode
import com.centurywar.game.Role
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Kodeks: encyklopedia epok. Dla wybranej epoki pokazuje 4 jednostki (podgląd,
 * rola, opis, statystyki), wieżę i ostrzał specjalny oraz koszt awansu w XP.
 * Wszystkie liczby pochodzą z [Defs]; przełącznik "z badaniami" nakłada bonusy
 * z [com.centurywar.meta.Profile.bonuses] tak, jak robi to [Battle].
 *
 * Teksty i wartości liczymy w [rebuild] (zmiana epoki, przełącznika, rozmiaru),
 * więc samo rysowanie klatki nie alokuje pamięci.
 */
class CodexScreen(g: Game, startEra: Int = 0) : Screen(g) {
    private companion object {
        const val CARDS = 6          // 4 jednostki + wieża + ostrzał
        const val MAX_STATS = 9
        const val MAX_LINES = 3
        const val TURRET = 4
        const val BARRAGE = 5
        const val PAGE_ERAS = 0
        const val PAGE_UNITS = 1
        const val PAGE_VARIANTS = 2
        const val PAGE_TOWERS = 3
        const val PAGE_ABILITIES = 4
        const val PAGE_DOCTRINES = 5
        const val PAGE_WEATHER = 6
        const val PAGE_MODIFIERS = 7
        const val PAGE_CARDS = 8
        const val PAGE_BOSSES = 9
        const val MAX_GUIDE = 40
        const val MAX_GUIDE_LINES = 8
        val CATEGORY_KEYS = arrayOf(
            "cx62_eras", "cx62_units", "cx62_variants", "cx62_towers",
            "cx62_abilities", "cx62_doctrines", "cx62_weather",
            "cx62_modifiers", "cx62_cards", "cx62_bosses",
        )
        val ROLE_COL = intArrayOf(
            Color.rgb(170, 60, 50), Color.rgb(54, 128, 84), Color.rgb(60, 104, 170), Color.rgb(110, 76, 170),
            Color.rgb(170, 122, 36), Color.rgb(196, 84, 40),
        )
        val CARD_BODY = Color.rgb(44, 54, 78)
    }

    private val bg = MenuBg()
    private val chrome = Chrome(this)
    private val back = btn(-1)
    private val toggle = btn(-2)
    private val variantBtn = Array(2) { btn(-3 - it) }
    private val tabs = Array(Defs.ERA_COUNT) { btn(100 + it) }
    private val categories = Array(CATEGORY_KEYS.size) { btn(200 + it) }
    private val endlessWaveThemes = EndlessWaveTheme.values()
    private val endlessBossNames = arrayOfNulls<String>(EndlessBosses.MAX_KIND)
    private val endlessBossDetails = arrayOfNulls<String>(EndlessBosses.MAX_KIND)
    private val endlessWaveNames = arrayOfNulls<String>(endlessWaveThemes.size)
    private val endlessWaveDetails = arrayOfNulls<String>(endlessWaveThemes.size)
    private var bossSectionTitle = ""
    private var waveSectionTitle = ""
    private val bossIcons = intArrayOf(Icons.ADVANCE, Icons.SPEED, Icons.SHIELD, Icons.HELMET, Icons.FLAME)
    private val waveIcons = intArrayOf(Icons.CROSSED_SWORDS, Icons.CROSSHAIR, Icons.SHIELD,
        Icons.SPEED, Icons.CROWN, Icons.BOMB, Icons.TOWER, Icons.HELMET)

    /** Atrapa bitwy tylko po to, by użyć proceduralnych sylwetek jednostek z renderera. */
    private val renderer = BattleRenderer(g, Battle(BattleConfig(Mode.SKIRMISH, 1L, 1f)))

    private var era = startEra.coerceIn(0, Defs.ERA_COUNT - 1)
    private var selectedVariant = if (Defs.spec(era).variants.isNotEmpty()) 0 else -1
    private var withResearch = false
    private var page = PAGE_ERAS
    private var activeCards = CARDS
    private var variantTitle = ""
    private val variantLines = arrayOfNulls<String>(MAX_GUIDE_LINES)
    private var variantLineCount = 0
    private var dt = 0f

    // układ
    private var left = 0f
    private var contentW = 0f
    private var viewTop = 0f
    private var cols = 2
    private var cw = 0f
    private var ch = 0f
    private var statCols = 3
    private var previewW = 0f
    private var previewH = 0f
    private var scroll = 0f
    private var maxScroll = 0f
    private val gap get() = 12 * dp
    private val headerH get() =
        (if (page == PAGE_VARIANTS) 97f + variantLineCount * guideLineDp else 75f) * dp
    private val guideLineDp get() = if (ui.textScale > 1.15f) 20f else 17f
    private val rowH get() = (if (ui.textScale > 1.15f) 26f else 21f) * dp
    private val endlessRowH get() = 52 * dp
    private val endlessHeaderH get() = 34 * dp

    // dane kart (przeliczane w rebuild)
    private val titles = arrayOfNulls<String>(CARDS)
    private val roles = arrayOfNulls<String>(CARDS)
    private val counterHints = arrayOfNulls<String>(CARDS)
    private val lines = Array(CARDS) { arrayOfNulls<String>(MAX_LINES) }
    private val lineCount = IntArray(CARDS)
    private val statN = IntArray(CARDS)
    private val statIcon = Array(CARDS) { IntArray(MAX_STATS) }
    private val statLabel = Array(CARDS) { arrayOfNulls<String>(MAX_STATS) }
    private val statVal = Array(CARDS) { arrayOfNulls<String>(MAX_STATS) }
    private val statUp = Array(CARDS) { BooleanArray(MAX_STATS) }
    private var eraName = ""
    private var eraNum = ""
    private var evolveText = ""
    private var eliteTag = ""
    private val weatherNames = Array(Weather.entries.size) { "" }
    private val weatherDescriptions = Array(Weather.entries.size) { "" }
    private var eliteLocked = false

    // Wiersze informacyjne mają obliczone pozycje i zawinięte teksty; render nie tworzy obiektów.
    private val guideNames = arrayOfNulls<String>(MAX_GUIDE)
    private val guideBadges = arrayOfNulls<String>(MAX_GUIDE)
    private val guideIcons = IntArray(MAX_GUIDE)
    private val guideLines = Array(MAX_GUIDE) { arrayOfNulls<String>(MAX_GUIDE_LINES) }
    private val guideLineCounts = IntArray(MAX_GUIDE)
    private val guideTops = FloatArray(MAX_GUIDE)
    private val guideHeights = FloatArray(MAX_GUIDE)
    private var guideCount = 0
    private var guideHeight = 0f
    private var guideTitle = ""

    private val card = RectF()
    private val box = RectF()
    private val pf = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun isDragScreen() = true

    /** Dla testów zrzutów oraz nawigacji: jeden punkt wejścia dla wszystkich zakładek. */
    internal fun showCategory(index: Int) {
        if (index !in CATEGORY_KEYS.indices) return
        page = index
        scroll = 0f
        layout()
    }

    override fun layout() {
        chrome.layoutBack(back)
        left = g.insetL + 16 * dp
        contentW = w - g.insetL - g.insetR - 32 * dp

        val tabGap = 7 * dp
        val topicW = (contentW - 4 * tabGap) / 5f
        val topicH = (if (ui.textScale > 1.15f) 44f else 40f) * dp
        val tabTop = chrome.top + 2 * dp
        for (i in categories.indices) {
            val b = categories[i]
            b.label = L[CATEGORY_KEYS[i]]
            b.selected = i == page
            b.highlight = b.selected
            b.set(left + (i % 5) * (topicW + tabGap),
                tabTop + (i / 5) * (topicH + tabGap), topicW, topicH)
        }
        viewTop = tabTop + 2 * topicH + tabGap + 12 * dp

        val hasEra = page <= PAGE_ABILITIES
        toggle.visible = hasEra
        toggle.iconId = Icons.FLASK
        val compact = w - g.insetL - g.insetR < 700 * dp
        val tw = if (compact) 44 * dp else min(210 * dp, w * 0.24f)
        toggle.set(w - g.insetR - 12 * dp - tw, g.insetT + 10 * dp, tw, 44 * dp)

        // Wybór epoki pozostaje dostępny na stronach związanych z konkretną epoką.
        val eraGap = 8 * dp
        val perRow = if ((contentW - (Defs.ERA_COUNT - 1) * eraGap) /
            Defs.ERA_COUNT < 66 * dp) 5 else Defs.ERA_COUNT
        val eraW = (contentW - (perRow - 1) * eraGap) / perRow
        val eraH = 40 * dp
        for (i in tabs.indices) {
            val b = tabs[i]
            b.visible = hasEra
            b.label = L[Defs.eraKeys[i]]
            if (hasEra) b.set(left + (i % perRow) * (eraW + eraGap),
                viewTop + (i / perRow) * (eraH + eraGap), eraW, eraH)
        }
        if (hasEra) {
            val rows = (tabs.size + perRow - 1) / perRow
            viewTop += rows * eraH + (rows - 1) * eraGap + 10 * dp
        }

        val variants = Defs.spec(era).variants
        val vbW = (contentW - 10 * dp) / 2f
        for (i in variantBtn.indices) {
            val b = variantBtn[i]
            b.visible = page == PAGE_VARIANTS && i in variants.indices
            if (b.visible) b.set(left + i * (vbW + 10 * dp), viewTop, vbW, 40 * dp)
        }
        if (page == PAGE_VARIANTS && variants.isNotEmpty()) viewTop += 50 * dp

        cols = (contentW / ((if (ui.textScale > 1.15f) 340f else 280f) * dp))
            .toInt().coerceIn(1, 3)
        cw = (contentW - (cols - 1) * gap) / cols
        statCols = if (cw >= 340 * dp) 3 else 2
        previewW = min(128 * dp, cw * 0.34f)
        previewH = 112 * dp
        val unitRows = (MAX_STATS + statCols - 1) / statCols
        ch = 10 * dp + previewH + 36 * dp + unitRows * rowH + 12 * dp
        activeCards = when (page) {
            PAGE_ERAS, PAGE_VARIANTS -> if (page == PAGE_VARIANTS && variants.isEmpty()) 0 else CARDS
            PAGE_UNITS -> 4
            PAGE_TOWERS, PAGE_ABILITIES -> 1
            else -> 0
        }
        rebuild()
        val gridH = ((activeCards + cols - 1) / cols) * (ch + gap)
        val contentH = if (hasEra) headerH + gridH + 12 * dp else 0f
        val allH = contentH + guideHeight
        maxScroll = max(0f, allH - (h - g.insetB - viewTop) + 8 * dp)
        scroll = scroll.coerceIn(0f, maxScroll)
    }

    /** Przelicza wszystkie teksty i wartości (bez tego render nie alokuje). */
    private fun rebuild() {
        for (type in Weather.entries) {
            weatherNames[type.ordinal] = L[type.key]
            weatherDescriptions[type.ordinal] = L[type.key + "_d"]
        }
        val b = if (withResearch) g.profile.bonuses() else Bonuses()
        val e = Defs.eras[era]
        val variantIndex = if (page == PAGE_VARIANTS) selectedVariant else -1
        val variant = Defs.spec(era).variants.getOrNull(variantIndex)
        variantTitle = if (variant == null) L["cx62_no_variants"] else L[variant.nameKey]
        variantLineCount = if (variant == null) 0 else wrapWords(
            L[variant.descKey], contentW - 20 * dp, 12.5f, variantLines)
        eraName = L[e.nameKey]
        eraNum = L.f("cx_era_n", era + 1, Defs.ERA_COUNT)
        evolveText = if (e.evolveXp == Int.MAX_VALUE) L["cx_final"] else L.f("cx_evolve", num(e.evolveXp.toFloat()))
        eliteLocked = !g.profile.bonuses().eliteUnlocked
        eliteTag = L.f("cx_needs", L["r_elite"])

        val textW = cw - previewW - 3 * 12 * dp
        for (i in 0 until 4) {
            val u = Defs.unit(era, Role.entries[i], variantIndex)
            titles[i] = L[u.nameKey]
            roles[i] = L[when (u.role) { Role.MELEE -> "cx_melee"; Role.RANGED -> "cx_ranged"; Role.HEAVY -> "cx_heavy"; Role.ELITE -> "cx_elite" }]
            counterHints[i] = L[when (u.role) {
                Role.MELEE -> "cx_counter_ranged"
                Role.RANGED -> "cx_counter_heavy"
                Role.HEAVY -> "cx_counter_melee"
                Role.ELITE -> "cx_counter_elite"
            }]
            wrapInto(i, L[u.nameKey + "_fl"], textW)
            val hp = u.hp * b.unitHpMul
            val dmg = u.dmg * b.unitDmgMul
            var n = 0
            n = stat(i, n, Icons.COIN, "cx_cost", num(u.cost.toFloat()), false)
            n = stat(i, n, Icons.HEART, "cx_hp", num(hp), b.unitHpMul != 1f)
            n = stat(i, n, Icons.SWORD, "cx_dmg", num(dmg), b.unitDmgMul != 1f)
            n = stat(i, n, Icons.FLAME, "cx_dps", num(dmg / u.cooldown), b.unitDmgMul != 1f)
            n = stat(i, n, Icons.CROSSHAIR, "cx_range", num(u.range), false)
            n = stat(i, n, Icons.SPEED, "cx_speed", num(u.speed), false)
            n = stat(i, n, Icons.STOPWATCH, "cx_train", sec(u.trainTime / b.trainMul), b.trainMul != 1f)
            n = stat(i, n, Icons.BOMB, "cx_splash", if (u.splash > 0f) num(u.splash) else "—", false)
            n = stat(i, n, Icons.WALL, "cx_siege", if (u.siegeMul != 1f) "×" + dec(u.siegeMul) else "—", false)
            statN[i] = n
        }

        val t = Defs.turret(era, variantIndex)
        titles[TURRET] = L[t.nameKey]
        roles[TURRET] = L["cx_turret"]
        counterHints[TURRET] = if (era >= 4) L["cx_counter_turret"] else null
        wrapInto(TURRET, L["cx_turret_d"], textW)
        val tdmg = t.dmg * b.turretDmgMul
        val tUp = b.turretDmgMul != 1f
        var n = 0
        n = stat(TURRET, n, Icons.COIN, "cx_cost", num(t.cost.toFloat()), false)
        n = stat(TURRET, n, Icons.SWORD, "cx_dmg", num(tdmg), tUp)
        n = stat(TURRET, n, Icons.FLAME, "cx_dps", num(tdmg / t.cooldown), tUp)
        n = stat(TURRET, n, Icons.CROSSHAIR, "cx_range", num(t.range), false)
        n = stat(TURRET, n, Icons.HOURGLASS, "cx_reload", sec(t.cooldown), false)
        n = stat(TURRET, n, Icons.BOMB, "cx_splash", if (t.splash > 0f) num(t.splash) else "—", false)
        statN[TURRET] = n

        titles[BARRAGE] = if (variant == null) L[e.abilityKey] else L["ab_" + variant.key]
        roles[BARRAGE] = L["cx_barrage"]
        counterHints[BARRAGE] = null
        wrapInto(BARRAGE, if (variant == null) L["cx_barrage_d"] else L[variant.descKey], textW)
        n = 0
        n = stat(BARRAGE, n, Icons.METEOR, "cx_hit", num(Defs.abilityDmg(era) * b.unitDmgMul), b.unitDmgMul != 1f)
        n = stat(BARRAGE, n, Icons.HOURGLASS, "cx_cd", sec(Battle.BARRAGE_CD * b.abilityCdMul), b.abilityCdMul != 1f)
        val volley = Defs.barrage(era, variantIndex)
        n = stat(BARRAGE, n, Icons.METEOR, "cx_count", volley.count.toString(), false)
        n = stat(BARRAGE, n, Icons.BOMB, "cx_splash", num(volley.splash), false)
        statN[BARRAGE] = n

        for (i in tabs.indices) {
            val tb = tabs[i]
            tb.selected = i == era
            tb.color = if (i == era) Ui.ERA[i] else Ui.blend(Ui.ERA[i], Ui.BTN, 0.55f)
        }
        val compactToggle = w - g.insetL - g.insetR < 700 * dp
        toggle.label = if (compactToggle) "" else L["cx_research"]
        toggle.sub = if (compactToggle) "" else if (withResearch) L["on"] else L["off"]
        toggle.highlight = withResearch
        for (i in variantBtn.indices) {
            val vb = variantBtn[i]
            if (i < Defs.spec(era).variants.size) vb.label = L[Defs.spec(era).variants[i].nameKey]
            vb.selected = i == selectedVariant
            vb.highlight = vb.selected
        }
        bossSectionTitle = L["cx_endless_bosses"]
        waveSectionTitle = L["cx_endless_waves"]
        for (i in endlessBossNames.indices) {
            endlessBossNames[i] = L[EndlessBosses.nameKeys[i + 1]]
            endlessBossDetails[i] = L[EndlessBosses.descriptionKeys[i + 1]]
        }
        for (i in endlessWaveThemes.indices) {
            val key = endlessWaveThemes[i].textKey
            endlessWaveNames[i] = L[key]
            endlessWaveDetails[i] = L[key + "_d"]
        }
        buildGuide()
    }

    private fun percentDown(multiplier: Float): Int = ((1f - multiplier) * 100f).roundToInt()
    private fun percentUp(multiplier: Float): Int = ((multiplier - 1f) * 100f).roundToInt()

    /** Słownik jest tylko widokiem: wszystkie kolekcje pochodzą z definicji symulacji. */
    private fun buildGuide() {
        guideCount = 0
        guideHeight = 40 * dp
        guideTitle = L[CATEGORY_KEYS[page]]
        when (page) {
            PAGE_VARIANTS -> {
                if (Defs.spec(era).variants.isEmpty()) {
                    addGuide(Icons.CODEX, L["cx62_no_variants"], L["cx62_no_variants_d"])
                }
                // Zbiorczy spis wszystkich ścieżek, także gdy bieżąca epoka ich nie ma.
                for (e in Defs.specs.indices) for (variant in Defs.spec(e).variants) {
                    addGuide(Icons.CODEX, L[variant.nameKey], L[variant.descKey],
                        L[Defs.eraKeys[e]])
                }
            }
            PAGE_ABILITIES -> {
                addGuide(Icons.FLASK, L["armory"], L.f("cx62_armory_d",
                    percentUp(Battle.ARMORY_BONUS), Battle.ARMORY_MAX_LEVEL))
                addGuide(Icons.FLAG, L["rally"], L.f("cx62_rally_d",
                    percentUp(Battle.RALLY_SPEED_BONUS), Battle.RALLY_TIME.roundToInt(),
                    Battle.RALLY_CD.roundToInt()))
            }
            PAGE_DOCTRINES -> for (d in com.centurywar.game.Doctrine.entries) {
                addGuide(Icons.CROWN, L[d.key], L[d.key + "_d"])
            }
            PAGE_WEATHER -> for (type in Weather.entries) {
                val detail = when (type) {
                    Weather.CLEAR -> L[type.key + "_d"]
                    Weather.RAIN -> L.f("cx62_rain_d",
                        percentDown(type.projectileRange),
                        percentDown(type.moveSpeed), percentDown(type.burnDamage))
                    Weather.WIND -> L.f("cx62_wind_d",
                        percentUp(Weather.WIND_WITH),
                        percentDown(Weather.WIND_AGAINST), Weather.WIND_SHIFT_SECONDS)
                    Weather.NIGHT -> L.f("cx62_night_d",
                        percentDown(type.projectileRange), percentDown(type.turretRange))
                }
                addGuide(Icons.weatherIcon(type), L[type.key], detail)
            }
            PAGE_MODIFIERS -> for (mod in com.centurywar.game.Modifier.entries) {
                addGuide(Icons.CROSSED_SWORDS, L[mod.key], L["sk_moddesc_" + mod.ordinal])
            }
            PAGE_CARDS -> for (entry in com.centurywar.game.EndlessCard.entries) {
                val rarity = L["er_" + entry.rarity.name.lowercase(Locale.ROOT)]
                addGuide(Icons.CODEX, L[entry.textKey], L[entry.textKey + "_d"], rarity)
            }
            PAGE_BOSSES -> {
                guideTitle = L["cx_endless_bosses"]
                for (i in endlessBossNames.indices) {
                    addGuide(bossIcons[i], endlessBossNames[i] ?: "",
                        endlessBossDetails[i] ?: "")
                }
                addGuide(Icons.CROSSED_SWORDS, L["cx_endless_waves"], L["cx62_waves_d"])
                for (i in endlessWaveThemes.indices) {
                    addGuide(waveIcons[i], endlessWaveNames[i] ?: "",
                        endlessWaveDetails[i] ?: "")
                }
            }
        }
        if (guideCount == 0) guideHeight = 0f
        else guideHeight += 10 * dp
    }

    /** Tekst i pozycje wiersza obliczane przy zmianie danych, nigdy na klatkę. */
    private fun addGuide(icon: Int, title: String, description: String, badge: String? = null) {
        check(guideCount < MAX_GUIDE)
        val i = guideCount++
        guideNames[i] = title
        guideBadges[i] = badge
        guideIcons[i] = icon
        guideLineCounts[i] = wrapWords(description, contentW - 68 * dp, 12f, guideLines[i])
        guideTops[i] = guideHeight
        guideHeights[i] = (if (badge == null) 45f else 62f) * dp +
            guideLineCounts[i] * guideLineDp * dp + 10 * dp
        guideHeight += guideHeights[i] + 6 * dp
    }

    /** Łamanie wyrazów tylko podczas przebudowania ekranu, także przy skali 130%. */
    private fun wrapWords(value: String, width: Float, font: Float, target: Array<String?>): Int {
        target.fill(null)
        val words = value.split(' ')
        val buffer = StringBuilder()
        var n = 0
        for (word in words) {
            val attempt = if (buffer.isEmpty()) word else "$buffer $word"
            if (buffer.isNotEmpty() && ui.measure(attempt, font) > width &&
                n < target.size - 1) {
                target[n++] = buffer.toString()
                buffer.setLength(0)
                buffer.append(word)
            } else {
                buffer.setLength(0)
                buffer.append(attempt)
            }
        }
        if (buffer.isNotEmpty()) target[n++] = buffer.toString()
        return n
    }

    private fun drawGuide(c: Canvas, y0: Float) {
        if (guideHeight <= 0f) return
        if (y0 + 40 * dp >= viewTop && y0 < h - g.insetB)
            ui.textO(c, guideTitle, left + 8 * dp, y0 + 26 * dp, 18f, Ui.GOLD)
        for (i in 0 until guideCount) {
            val y = y0 + guideTops[i]
            val rh = guideHeights[i]
            if (y >= h - g.insetB || y + rh < viewTop) continue
            box.set(left, y, left + contentW, y + rh)
            ui.card(c, box, CARD_BODY, 10f, Ui.OUTLINE)
            Icons.draw(c, guideIcons[i], left + 24 * dp, y + 25 * dp, 22 * dp, dp = dp)
            val name = guideNames[i] ?: ""
            val tx = left + 49 * dp
            val avail = contentW - 66 * dp
            ui.textO(c, name, tx, y + 25 * dp, ui.fit(name, 15f, avail, 10f), Ui.TEXT)
            val badge = guideBadges[i]
            val descTop = if (badge == null) 45f else 62f
            if (badge != null) ui.text(c, badge, tx, y + 45 * dp, 11f, Ui.GOLD)
            for (line in 0 until guideLineCounts[i]) {
                val body = guideLines[i][line] ?: ""
                ui.text(c, body, tx, y + (descTop + line * guideLineDp) * dp, 12f, Ui.DIM)
            }
        }
    }

    private fun stat(card: Int, n: Int, icon: Int, key: String, value: String, up: Boolean): Int {
        statIcon[card][n] = icon
        statLabel[card][n] = L[key]
        statVal[card][n] = value
        statUp[card][n] = up
        return n + 1
    }

    /** Zawija opis do [width] w maks. [MAX_LINES] wiersze (poza pętlą rysowania). */
    private fun wrapInto(card: Int, s: String, width: Float) {
        val out = lines[card]
        var n = 0
        val sb = StringBuilder()
        for (word in s.split(' ')) {
            val trial = if (sb.isEmpty()) word else "$sb $word"
            if (sb.isNotEmpty() && ui.measure(trial, 12.5f) > width && n < MAX_LINES - 1) {
                out[n++] = sb.toString()
                sb.setLength(0); sb.append(word)
            } else {
                sb.setLength(0); sb.append(trial)
            }
        }
        if (sb.isNotEmpty()) out[n++] = sb.toString()
        lineCount[card] = n
    }

    private fun num(v: Float): String {
        val a = abs(v)
        return when {
            a >= 10000f -> String.format(Locale.ROOT, "%,d", v.roundToInt()).replace(',', ' ')
            a >= 100f -> v.roundToInt().toString()
            else -> dec(v)
        }
    }

    private fun dec(v: Float): String {
        val r = (v * 10f).roundToInt()
        return if (r % 10 == 0) (r / 10).toString() else String.format(Locale.ROOT, "%.1f", r / 10f)
    }

    private fun sec(v: Float) = dec(v) + " s"

    override fun update(dt: Float) { this.dt = dt }

    override fun onDrag(dx: Float, dy: Float) { scroll = (scroll - dy).coerceIn(0f, maxScroll) }

    override fun render(c: Canvas) {
        bg.draw(c, w, h, dt)
        chrome.dim(c, 130)
        for (b in categories) ui.button(c, b, 12.5f)
        for (b in tabs) if (b.visible) ui.button(c, b, 13.5f)
        for (b in variantBtn) if (b.visible) ui.button(c, b, 13f)

        c.save()
        c.clipRect(0f, viewTop - 4 * dp, w, h - g.insetB)
        val y0 = viewTop - scroll
        val hasEra = page <= PAGE_ABILITIES
        if (hasEra) drawEraHeader(c, y0)
        val gridTop = y0 + (if (hasEra) headerH else 0f)
        for (slot in 0 until activeCards) {
            val i = when (page) {
                PAGE_TOWERS -> TURRET
                PAGE_ABILITIES -> BARRAGE
                else -> slot
            }
            val x = left + (slot % cols) * (cw + gap)
            val y = gridTop + (slot / cols) * (ch + gap)
            if (y > h - g.insetB || y + ch < viewTop - 10 * dp) continue
            card.set(x, y, x + cw, y + ch)
            drawCard(c, i)
        }
        val gridH = ((activeCards + cols - 1) / cols) * (ch + gap)
        val guideY = if (hasEra) gridTop + gridH + 12 * dp else y0
        drawGuide(c, guideY)
        c.restore()

        ui.button(c, back, 16f)
        if (toggle.visible) ui.button(c, toggle, 15f)
        chrome.title(c, L["codex"])
    }

    /** Stała wysokość wierszy: na telefonie i Foldzie sekcje są przewijane razem z epoką. */
    private fun drawEndlessSection(c: Canvas, y: Float, title: String,
                                   names: Array<String?>, details: Array<String?>, icons: IntArray) {
        if (y < h && y + endlessHeaderH > viewTop) {
            ui.textO(c, title, left + 8 * dp, y + 24 * dp, 18f, Ui.GOLD)
        }
        for (i in names.indices) {
            val top = y + endlessHeaderH + i * endlessRowH
            if (top > h || top + endlessRowH < viewTop) continue
            box.set(left, top, left + contentW, top + endlessRowH - 3 * dp)
            ui.card(c, box, CARD_BODY, 8f, Ui.OUTLINE)
            Icons.draw(c, icons[i], left + 22 * dp, top + 20 * dp, 23 * dp, dp = dp)
            val name = names[i] ?: ""
            val desc = details[i] ?: ""
            val textLeft = left + 43 * dp
            val textWidth = contentW - 58 * dp
            ui.textO(c, name, textLeft, top + 19 * dp,
                ui.fit(name, 13f, textWidth, 9f), Ui.TEXT)
            ui.text(c, desc, textLeft, top + 37 * dp,
                ui.fit(desc, 11.5f, textWidth, 7f), Ui.DIM)
        }
    }

    private fun drawEraHeader(c: Canvas, y: Float) {
        val cy = y + 25 * dp
        val col = Ui.ERA[era]
        ui.medallion(c, left + 18 * dp, cy, 18 * dp, col, Icons.CODEX)
        val tx = left + 44 * dp
        val nameSize = ui.fit(eraName, 21f, contentW - 240 * dp, 12f)
        ui.textO(c, eraName, tx, cy + 2 * dp, nameSize, Ui.GOLD)
        ui.badge(c, left + contentW, cy, Icons.XP, evolveText, 14f, Ui.XP, alignRight = true)
        ui.text(c, eraNum, left + 8 * dp, y + 58 * dp, 12f, Ui.DIM)
        if (page == PAGE_VARIANTS) {
            ui.textO(c, variantTitle, left + 8 * dp, y + 85 * dp, 15f, Ui.GOLD)
            for (i in 0 until variantLineCount) {
                ui.text(c, variantLines[i] ?: "", left + 10 * dp,
                    y + (103 + i * guideLineDp) * dp, 12.5f, Ui.DIM)
            }
        }
    }

    private fun drawCard(c: Canvas, i: Int) {
        val roleCol = ROLE_COL[i]
        ui.card(c, card, CARD_BODY, 14f, Ui.blend(roleCol, Color.WHITE, 0.25f))
        val pad = 10 * dp

        // podgląd
        box.set(card.left + pad + 2 * dp, card.top + pad + 2 * dp, card.left + pad + previewW, card.top + pad + previewH)
        drawPreview(c, i)

        // nazwa, rola, opis
        val tx = box.right + 12 * dp
        val tw = card.right - pad - 4 * dp - tx
        val title = titles[i] ?: ""
        ui.textO(c, title, tx, card.top + 32 * dp, ui.fit(title, 19f, tw), Ui.TEXT)
        val role = roles[i] ?: ""
        val rw = ui.measure(role, 11.5f, true) + 18 * dp
        box.set(tx, card.top + 40 * dp, tx + rw, card.top + 60 * dp)
        pf.color = Ui.OUTLINE
        c.drawRoundRect(box, 10 * dp, 10 * dp, pf)
        box.inset(1.6f * dp, 1.6f * dp)
        pf.color = roleCol
        c.drawRoundRect(box, 9 * dp, 9 * dp, pf)
        ui.textO(c, role, box.centerX(), box.centerY() + 4 * dp, 11.5f, Ui.TEXT, Paint.Align.CENTER, 1.6f)
        if (i == 3 && eliteLocked) {
            // kłódka na podglądzie + informacja obok plakietki roli
            val lx = box.right + 10 * dp
            val la = card.right - pad - lx - 18 * dp
            Icons.draw(c, Icons.LOCK, lx + 7 * dp, box.centerY(), 15 * dp, dp = dp)
            ui.textO(c, eliteTag, lx + 17 * dp, box.centerY() + 4 * dp, ui.fit(eliteTag, 11.5f, la, 7f), Ui.BAD, Paint.Align.LEFT, 1.6f)
        }
        val ln = lines[i]
        for (k in 0 until lineCount[i]) {
            ui.text(c, ln[k] ?: "", tx, card.top + 80 * dp + k * 16.5f * dp, 12.5f, Color.rgb(214, 218, 228))
        }

        // statystyki
        val st = card.top + pad + previewH + 36 * dp
        val counterHint = counterHints[i]
        if (counterHint != null) {
            val available = cw - 2 * pad - 6 * dp
            ui.text(c, counterHint, card.left + pad + 3 * dp, st - 15 * dp,
                ui.fit(counterHint, 12f, available, 9f), Ui.GOOD)
        }
        val n = statN[i]
        val rows = (n + statCols - 1) / statCols
        box.set(card.left + pad, st - 4 * dp, card.right - pad, st + rows * rowH + 2 * dp)
        pf.color = Color.argb(110, 10, 12, 22)
        c.drawRoundRect(box, 8 * dp, 8 * dp, pf)
        val cellW = (box.width() - 4 * dp) / statCols
        val icons = statIcon[i]
        val labels = statLabel[i]
        val values = statVal[i]
        val ups = statUp[i]
        for (k in 0 until n) {
            val cx = box.left + 2 * dp + (k % statCols) * cellW
            val cy = st + (k / statCols) * rowH + rowH * 0.5f
            if (k % statCols != 0) {
                pf.color = Color.argb(50, 255, 255, 255)
                c.drawRect(cx, cy - rowH * 0.36f, cx + 1 * dp, cy + rowH * 0.36f, pf)
            }
            Icons.draw(c, icons[k], cx + 12 * dp, cy, 15 * dp, dp = dp)
            val v = values[k] ?: ""
            val vSize = ui.fit(v, 13.5f, cellW * 0.55f)
            val vw = ui.measure(v, vSize, true)
            ui.textO(c, v, cx + cellW - 7 * dp, cy + 4.6f * dp, vSize, if (ups[k]) Ui.GOOD else Ui.TEXT, Paint.Align.RIGHT, 1.6f)
            val lab = labels[k] ?: ""
            val lAvail = cellW - 7 * dp - vw - 6 * dp - 23 * dp
            val lSize = min(11f, ui.fit(lab, 11f, lAvail, 7f))
            ui.text(c, lab, cx + 23 * dp, cy + 4 * dp, lSize, Ui.DIM)
        }
    }

    private fun drawPreview(c: Canvas, i: Int) {
        val col = Ui.ERA[era]
        val rad = 10 * dp
        pf.color = Ui.OUTLINE
        c.drawRoundRect(box.left - 2 * dp, box.top - 2 * dp, box.right + 2 * dp, box.bottom + 2 * dp, rad + 2 * dp, rad + 2 * dp, pf)
        pf.color = Ui.blend(col, Color.rgb(16, 20, 34), 0.45f)
        c.drawRoundRect(box, rad, rad, pf)
        val groundY = box.bottom - box.height() * 0.2f
        c.save()
        c.clipRect(box)
        pf.color = Color.argb(40, 255, 255, 255)
        c.drawCircle(box.centerX(), box.top + box.height() * 0.38f, box.height() * 0.32f, pf)
        pf.color = Ui.blend(col, Color.rgb(20, 26, 20), 0.62f)
        c.drawRect(box.left, groundY, box.right, box.bottom, pf)
        pf.color = Color.argb(70, 0, 0, 0)
        c.drawOval(box.centerX() - box.width() * 0.32f, groundY - 4 * dp, box.centerX() + box.width() * 0.32f, groundY + 5 * dp, pf)
        when (i) {
            TURRET -> ui.medallion(c, box.centerX(), box.top + box.height() * 0.43f, box.height() * 0.33f, col, Icons.TOWER)
            BARRAGE -> ui.medallion(c, box.centerX(), box.top + box.height() * 0.43f, box.height() * 0.33f, ROLE_COL[BARRAGE], Icons.METEOR)
            else -> {
                val def = Defs.unit(era, Role.entries[i], if (page == PAGE_VARIANTS) selectedVariant else -1)
                val k = when (def.body) {
                    Body.HUMAN -> 0.8f
                    Body.MECH -> 0.7f
                    Body.BEAST -> 0.56f
                    Body.SIEGE -> 0.6f
                    Body.CANNON -> 0.42f
                    else -> 0.42f
                }
                renderer.drawUnitIcon(c, def, box.centerX(), groundY + 2 * dp, box.height() * k, Ui.PLAYER)
            }
        }
        c.restore()
    }

    override fun onClick(b: Button) {
        when {
            b.id == -1 -> g.pop()
            b.id == -2 -> { withResearch = !withResearch; layout() }
            b.id == -3 || b.id == -4 -> {
                selectedVariant = -3 - b.id
                scroll = 0f
                layout()
            }
            b.id in 200 until 200 + CATEGORY_KEYS.size -> showCategory(b.id - 200)
            b.id in 100 until 100 + Defs.ERA_COUNT -> {
                val e = b.id - 100
                if (e != era) {
                    era = e
                    selectedVariant = if (Defs.spec(e).variants.isEmpty()) -1 else 0
                    scroll = 0f
                    layout()
                }
            }
        }
    }

}
