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
import com.centurywar.game.Skirmish
import com.centurywar.game.BattleConfig
import com.centurywar.game.Daily
import com.centurywar.game.DailyEvent
import com.centurywar.game.Defs
import com.centurywar.game.Mode
import com.centurywar.game.Weekly
import com.centurywar.meta.Profile
import kotlin.math.max
import kotlin.math.min
import java.util.Locale

/** Ekran przed bitwą dla trybów: bez końca, wyzwanie dnia, potyczka. */
class ModeScreen(g: Game, private val mode: Mode, private var weeklySelected: Boolean = false,
                 private val previewDailyDay: Long? = null) : Screen(g) {
    override val crashName get() = "ModeScreen"
    // Pionowe karty trybów wypełniają wysokość okna; baner zasłaniał ich tytuł i zakładki.
    // Tura 10: także w poziomie wstęga tytułu leży na panelu i baner (np. serii dni) ją zasłaniał.
    // Ekran nie wysyła własnych powiadomień; kolejka czeka na następny ekran.
    override val blocksNotices: Boolean get() = true
    private val bg = MenuBg()
    private val chrome = Chrome(this)
    private val back = btn(-1)
    private val start = btn(-2)
    private val diff = Array(4) { btn(it) }
    private val dailyTab = btn(10)
    private val weeklyTab = btn(11)
    private val panel = RectF()
    private val row = RectF()
    private val hints = HintOverlay(g)

    init { buttons.add(0, hints.okBtn) }
    private var dt = 0f
    /** Dzień UTC: odświeżany w update(), żeby ekran otwarty przez północ nie startował wczorajszego wyzwania. */
    private var day = previewDailyDay ?: Daily.dayNumber()
    private var dailyCfg: BattleConfig? = null
    private var dailyMods = ""
    private var dailyWeather = ""
    private var dailyEventTitle = ""
    private var dailyEventIcon = Icons.CALENDAR
    private var dailyEventLines = emptyArray<String>()
    private var dailyLang = -1
    private var dailyInfoLines = emptyArray<String>()
    private var dailyModsLines = emptyArray<String>()
    private var dailyEras = ""
    private var dailyPower = ""
    private var dailyRanked = ""
    private var dailyBest = ""
    private var dailyEventDesc = ""
    /** R11-01: numer wersji danych karty; tekst dla czytnika powstaje dopiero przy zmianie. */
    private var a11yVersion = 0
    private var a11yBuiltVersion = -1
    private var a11yBuiltWeekly = false
    private var a11yBuiltLang = -1
    private var a11yText = ""
    private var week = -1L
    private var weeklyCfg: BattleConfig? = null
    private var weeklyLang = -1
    private var weeklySecond = -1L
    private var weeklyInfo = ""
    private var weeklyInfoLines = emptyArray<String>()
    private var weeklyModsLines = emptyArray<String>()
    private var weeklyEras = ""
    private var weeklyMods = ""
    private var weeklyCard = ""
    private var weeklyBest = ""
    private var weeklyClaimMask = 0
    private var weeklyCountdown = ""
    private val tierNames = Array(3) { "" }
    private val tierColors = intArrayOf(
        Color.rgb(191, 117, 62), Color.rgb(184, 194, 211), Color.rgb(236, 193, 62),
    )

    private var labelTimer = 0f
    private var endlessBest = ""
    private var endlessInfoLines = emptyArray<String>()
    internal val cachedEndlessInfoLines: Array<String> get() = endlessInfoLines
    private var endlessScore = ""
    private var skirmishRange = ""
    private var skirmishPower = ""
    private var skirmishScore = ""

    private var endlessKeyWave = -1
    private var endlessKeyScore = -2L
    private var endlessKeyLang = -1

    private fun refreshLabels() {
        val pr = g.profile
        when (mode) {
            Mode.ENDLESS -> {
                // Napisy budowane tylko przy zmianie danych lub języka (bez alokacji co sekundę).
                val best = pr.top(Profile.BOARD_ENDLESS).firstOrNull()
                val score = best?.score ?: -1L
                if (endlessKeyWave != pr.bestWave || endlessKeyScore != score || endlessKeyLang != L.lang) {
                    endlessKeyWave = pr.bestWave; endlessKeyScore = score; endlessKeyLang = L.lang
                    endlessBest = "${L["best"]}: ${L.f("wave", pr.bestWave)}"
                    endlessScore = if (best == null) "" else "${L["score"]}: ${best.score}"
                    a11yVersion++
                }
            }
            Mode.DAILY -> Unit // Karta dzienna i tygodniowa ma własną pamięć podręczną (rebuildDaily/rebuildWeekly).
            else -> {
                skirmishRange = L.f("eras_range", L[Defs.eraKeys[0]], L[Defs.eraKeys[Defs.ERA_COUNT - 1]])
                skirmishPower = L.f("ai_power", (aiPower() * 100).toInt())
                val best = pr.top(Profile.BOARD_SKIRMISH).firstOrNull()
                skirmishScore = if (best == null) "" else "${L["best"]}: ${best.score}"
                a11yVersion++
            }
        }
    }

    private val modeIcon = when (mode) { Mode.ENDLESS -> Icons.TOWER; Mode.DAILY -> Icons.CALENDAR; else -> Icons.CROSSED_SWORDS }
    private val diffIcons = intArrayOf(Icons.SHIELD, Icons.CROSSED_SWORDS, Icons.FLAME, Icons.SKULL)

    override fun layout() {
        chrome.layoutBack(back)
        val pw = min(600 * dp, w - g.insetL - g.insetR - 40 * dp)
        val avail = h - chrome.top - g.insetB - 26 * dp
        var ph = min(avail, 440 * dp)
        if (mode == Mode.ENDLESS) {
            // Tura 10 (szlif 2): na wysokich ekranach (Fold) panel nie zostaje w 2/3 pusty —
            // wysokość wynika z treści (opis, rekord fali, wynik, Graj). Telefony poziomo mają
            // najwyżej ~400 dp wysokości: tam panel zostaje jak był.
            endlessInfoLines = cachedWrap(L["endless_info"], pw - (26f + 42f + 42f + 18f + 24f) * dp, 15f)
            val textH = 58 * dp + endlessInfoLines.size * 15f * dp * ui.textScale * 1.3f + 12 * dp +
                (if (g.profile.top(Profile.BOARD_ENDLESS).isEmpty()) 1 else 2) * 30 * dp
            val content = max(textH, (52f + 84f) * dp) + 24 * dp + 76 * dp
            if (h - g.insetT - g.insetB >= 480 * dp) ph = min(ph, content)
        }
        val top = chrome.top + 14 * dp + max(0f, (avail - ph) * 0.4f)
        panel.set(w / 2 - pw / 2, top, w / 2 + pw / 2, top + ph)
        start.set(w / 2 - 110 * dp, panel.bottom - 76 * dp, 220 * dp, 52 * dp)
        start.highlight = true
        start.iconId = Icons.PLAY
        val tabWidth = min(118 * dp, (pw - 42 * dp) / 2f)
        dailyTab.set(panel.centerX() - tabWidth - 4 * dp, panel.top + 27 * dp, tabWidth, 48 * dp)
        weeklyTab.set(panel.centerX() + 4 * dp, panel.top + 27 * dp, tabWidth, 48 * dp)
        dailyTab.label = L["weekly_day_tab"]
        weeklyTab.label = L["weekly_week_tab"]
        dailyTab.iconId = Icons.CALENDAR
        weeklyTab.iconId = Icons.MEDAL
        dailyTab.visible = mode == Mode.DAILY
        weeklyTab.visible = mode == Mode.DAILY
        weeklyLang = -1 // Po zmianie rozmiaru ekranu przelicz szerokości tekstu.
        dailyLang = -1
        val gap = 8 * dp
        val dw = (pw - 44 * dp - gap * 3) / 4
        for (i in 0..3) {
            diff[i].set(panel.left + 22 * dp + i * (dw + gap), start.r.top - 62 * dp, dw, 48 * dp)
            diff[i].label = L["diff_$i"]
            diff[i].iconId = diffIcons[i]
            diff[i].visible = mode == Mode.SKIRMISH
        }
        if (mode == Mode.DAILY) hints.showScreen(HintOverlay.DAILY_MENU, start.r)
        if (mode == Mode.ENDLESS) hints.showScreen(HintOverlay.ENDLESS_MENU, start.r)
    }

    override fun onLanguageChanged() {
        // Nazwy modyfikatorów wyzwania są zapamiętane razem z konfiguracją dnia.
        dailyCfg = null
        super.onLanguageChanged()
    }

    override fun update(dt: Float) {
        hints.update(dt)
        this.dt = dt
        val today = previewDailyDay ?: Daily.dayNumber()
        if (today != day) { day = today; dailyCfg = null; labelTimer = 0f }
        labelTimer -= dt
        if (labelTimer <= 0f) { labelTimer = 1f; refreshLabels() }
        if (mode == Mode.DAILY) {
            if (dailyCfg == null || dailyLang != L.lang) rebuildDaily()
            if (week != Weekly.weekNumber(today) || weeklyLang != L.lang) rebuildWeekly(today)
            val second = System.currentTimeMillis() / 1000L
            if (second != weeklySecond) {
                weeklySecond = second
                val remaining = ((week * 7L + 4L) * 86_400L - second).coerceAtLeast(0L)
                val clock = String.format(Locale.ROOT, "%dd %02d:%02d:%02d",
                    remaining / 86_400L, remaining / 3600L % 24L,
                    remaining / 60L % 60L, remaining % 60L)
                weeklyCountdown = L.f("weekly_ends", clock)
            }
        }
        dailyTab.selected = !weeklySelected
        weeklyTab.selected = weeklySelected
        start.label = L["play"]
        for (i in 0..3) diff[i].selected = g.profile.skirmishDiff == i
    }

    /** Dzielenie tekstu przy przebudowie karty, nie w klatce renderowania. */
    private fun cachedWrap(value: String, width: Float, size: Float): Array<String> {
        val lines = ArrayList<String>()
        var line = ""
        for (word in value.split(' ')) {
            val candidate = if (line.isEmpty()) word else "$line $word"
            if (line.isNotEmpty() && ui.measure(candidate, size) > width) {
                lines.add(line)
                line = word
            } else line = candidate
        }
        if (line.isNotEmpty()) lines.add(line)
        return lines.toTypedArray()
    }

    /** Dzienna karta bez dużego medalionu: obie zakładki mieszczą się na 720p. */
    private fun rebuildDaily() {
        dailyLang = L.lang
        val cfg = Daily.config(day, g.profile.bonuses())
        dailyCfg = cfg
        val full = panel.width() - 40 * dp
        val colWidth = if (stackedColumns()) full else (full - 16 * dp) / 2f
        val event = cfg.dailyEvent
        val rule = Daily.ruleSet(day)
        dailyInfoLines = if (event == null && rule == null)
            cachedWrap(L["daily_info"], full, 13f) else emptyArray()
        dailyEventDesc = when {
            event != null -> L[event.descriptionKey]
            rule != null -> L[rule.descriptionKey]
            else -> L["daily_info"]
        }
        a11yVersion++
        dailyEventTitle = when {
            event != null -> L[event.key]
            rule != null -> L[rule.key]
            else -> ""
        }
        dailyEventLines = when {
            event != null -> cachedWrap(L[event.descriptionKey], full - 23 * dp, 12f)
            rule != null -> cachedWrap(L[rule.descriptionKey], full - 23 * dp, 12f)
            else -> emptyArray()
        }
        dailyEventIcon = when {
            event != null -> Icons.dailyEventIcon(event)
            rule == null -> Icons.CALENDAR
            else -> Icons.FLAG
        }
        dailyMods = (if (event != null && rule != null) L[rule.key] + " · " else "") +
            L["modifiers"] + ": " + cfg.modifiers.joinToString(", ") { L[it.key] }
        dailyModsLines = cachedWrap(dailyMods, colWidth - 23 * dp, 12f)
        dailyEras = L.f("eras_range", L[Defs.eraKeys[cfg.startEra]], L[Defs.eraKeys[cfg.maxEra]])
        dailyPower = L.f("ai_power", (cfg.aiPower * 100).toInt())
        dailyWeather = L[cfg.weather.key]
        val ranked = g.profile.lastRankedDaily != day
        dailyRanked = L.f("daily_ranked", L[if (ranked) "daily_free" else "daily_used"])
        val score = g.profile.top(Profile.dailyBoard(day)).firstOrNull()?.score
        dailyBest = if (score == null) "" else L["best"] + ": " + score
    }

    /**
     * Tura 10: wąski panel (zewnętrzny ekran Folda) ma kolumny po ~120 dp i drobne pismo;
     * wtedy wiersze idą jedną kolumną na całą szerokość.
     */
    private fun stackedColumns() = (panel.width() - 56 * dp) / 2f < 190 * dp

    /** Pierwszy wiersz treści pod zakładkami Dzień/Tydzień (wcześniej 81 dp: tekst dotykał zakładek). */
    private fun contentTop() = max(panel.top + 81 * dp, dailyTab.r.bottom + 15 * dp)

    private fun drawDaily(c: Canvas, pr: Profile) {
        val x = panel.left + 20 * dp
        val full = panel.width() - 40 * dp
        val stacked = stackedColumns()
        val colWidth = if (stacked) full else (full - 16 * dp) / 2f
        var y = contentTop()
        if (dailyEventTitle.isNotEmpty()) {
            Icons.draw(c, dailyEventIcon, x + 9 * dp, y - 4 * dp, 18 * dp, dp = dp)
            ui.textO(c, dailyEventTitle, x + 23 * dp, y,
                ui.fit(dailyEventTitle, 14f, full - 23 * dp), Ui.GOLD)
            y += 18 * dp
            for (line in dailyEventLines) {
                ui.textO(c, line, x + 23 * dp, y, 12f, Ui.TEXT)
                y += 15 * dp
            }
            y += 7 * dp
        } else {
            for (line in dailyInfoLines) {
                ui.textO(c, line, x, y, 13f)
                y += 17 * dp
            }
            y += 8 * dp
        }
        val cfg = dailyCfg
        val leftRows = if (cfg != null) 4 else 3
        val modsH = dailyModsLines.size * 16 * dp
        val bestH = if (dailyBest.isNotEmpty()) 11 * dp else 0f
        // Tura 10: na niskim panelu (720p) wiersz rankingowy wchodził na przycisk Graj.
        val limit = start.r.top - 9 * dp
        val natural = if (dailyEventTitle.isNotEmpty()) 26 * dp else 29 * dp
        val rowGap = if (stacked)
            min(natural, max(17 * dp, (limit - y - modsH - bestH) / leftRows))
        else min(natural, max(17 * dp, (limit - y) / (leftRows - 1)))
        val rightX = if (stacked) x else x + colWidth + 16 * dp
        Icons.draw(c, Icons.TOWER, x + 9 * dp, y - 4 * dp, 18 * dp, dp = dp)
        ui.textO(c, dailyEras, x + 23 * dp, y,
            ui.fit(dailyEras, 13f, colWidth - 23 * dp), Ui.TEXT)
        val modsTop = if (stacked) y + leftRows * rowGap else y
        Icons.draw(c, Icons.FLAG, rightX + 9 * dp, modsTop - 4 * dp, 18 * dp, dp = dp)
        var modsY = modsTop
        for (line in dailyModsLines) {
            ui.textO(c, line, rightX + 23 * dp, modsY, 12f, Ui.GOLD)
            modsY += 16 * dp
        }
        y += rowGap
        Icons.draw(c, Icons.SKULL, x + 9 * dp, y - 4 * dp, 18 * dp, dp = dp)
        ui.textO(c, dailyPower, x + 23 * dp, y,
            ui.fit(dailyPower, 13f, colWidth - 23 * dp), Ui.BAD)
        if (dailyBest.isNotEmpty()) {
            Icons.draw(c, Icons.TROPHY, rightX + 9 * dp, modsY + 7 * dp, 18 * dp, dp = dp)
            ui.textO(c, dailyBest, rightX + 23 * dp, modsY + 11 * dp,
                ui.fit(dailyBest, 13f, colWidth - 23 * dp), Ui.GOLD)
        }
        y += rowGap
        // Pogoda dnia (zlecenie 33) w lewej kolumnie, pod siłą AI.
        if (cfg != null) {
            Icons.draw(c, Icons.weatherIcon(cfg.weather), x + 9 * dp, y - 4 * dp, 18 * dp, dp = dp)
            ui.textO(c, dailyWeather, x + 23 * dp, y,
                ui.fit(dailyWeather, 13f, colWidth - 23 * dp), Ui.TEXT)
            y += rowGap
        }
        val ranked = pr.lastRankedDaily != day
        Icons.draw(c, if (ranked) Icons.CHECK else Icons.LOCK, x + 9 * dp, y - 4 * dp, 18 * dp, dp = dp)
        ui.textO(c, dailyRanked, x + 23 * dp, y,
            ui.fit(dailyRanked, 13f, full - 23 * dp), if (ranked) Ui.GOOD else Ui.DIM)
    }

    /** Odświeżenie opisów wyłącznie przy zmianie tygodnia lub języka. */
    private fun rebuildWeekly(today: Long) {
        week = Weekly.weekNumber(today)
        weeklyLang = L.lang
        weeklySecond = -1L
        g.profile.syncWeekly(week)
        weeklyClaimMask = g.profile.weeklyClaimedFor(week)
        val cfg = Weekly.config(week)
        weeklyCfg = cfg
        weeklyInfo = Weekly.ruleSet(week)?.let { L[it.key] + ". " + L["weekly_info"] }
            ?: L["weekly_info"]
        weeklyEras = L.f("eras_range", L[Defs.eraKeys[cfg.startEra]], L[Defs.eraKeys[cfg.maxEra]])
        weeklyMods = L["modifiers"] + ": " + cfg.modifiers.joinToString(", ") { L[it.key] }
        weeklyCard = L["weekly_start_card"] + ": " + L[cfg.weeklyCard!!.textKey]
        val full = panel.width() - 40 * dp
        weeklyInfoLines = cachedWrap(weeklyInfo, full, 13f)
        weeklyModsLines = cachedWrap(weeklyMods,
            (if (stackedColumns()) full else (full - 16 * dp) / 2f) - 23 * dp, 12f)
        val score = g.profile.top(Profile.weeklyBoard(week)).firstOrNull()?.score
        weeklyBest = L["best"] + ": " + (score?.toString() ?: L["no_scores"])
        a11yVersion++
        for (i in 0 until Weekly.TIER_COUNT) {
            tierNames[i] = L.f("weekly_tier", L[when (i) {
                0 -> "weekly_bronze"; 1 -> "weekly_silver"; else -> "weekly_gold"
            }], Weekly.threshold(i), Weekly.medalReward(i))
        }
    }

    /** Karta tygodnia ma zwartą siatkę, by zmieścić się także na 720p. */
    private fun drawWeekly(c: Canvas, pr: Profile) {
        val x = panel.left + 20 * dp
        val full = panel.width() - 40 * dp
        val stacked = stackedColumns()
        val colWidth = if (stacked) full else (full - 16 * dp) / 2f
        var y = contentTop()
        for (line in weeklyInfoLines) {
            ui.textO(c, line, x, y, 13f)
            y += 17 * dp
        }
        y += 7 * dp
        val leftX = x
        val rightX = if (stacked) x else x + colWidth + 16 * dp
        // Tura 10: progi medali nie mogą wchodzić na „Koniec za…” i „Najlepszy” (720p);
        // w wąskim panelu każdy próg ma własny wiersz zamiast pisma 6 sp.
        val medalRows = if (stacked) Weekly.TIER_COUNT else 1
        val medalStep = 21 * dp
        val lowestMedal = start.r.top - 16 * dp - (medalRows - 1) * medalStep
        val modsH = weeklyModsLines.size * 16 * dp
        val rowGap = if (stacked)
            min(24 * dp, max(17 * dp, (lowestMedal - 17 * dp - y - modsH - 8 * dp) / 3f))
        else min(24 * dp, max(17 * dp, (lowestMedal - 17 * dp - y) / 2f))
        Icons.draw(c, Icons.TOWER, leftX + 9 * dp, y - 4 * dp, 18 * dp, dp = dp)
        ui.textO(c, weeklyEras, leftX + 23 * dp, y, ui.fit(weeklyEras, 13f, colWidth - 23 * dp), Ui.TEXT)
        val leftTop = y
        y += rowGap
        Icons.draw(c, Icons.CROWN, leftX + 9 * dp, y - 4 * dp, 18 * dp, dp = dp)
        ui.textO(c, weeklyCard, leftX + 23 * dp, y, ui.fit(weeklyCard, 13f, colWidth - 23 * dp), Ui.GOLD)
        y += rowGap
        Icons.draw(c, Icons.HOURGLASS, leftX + 9 * dp, y - 4 * dp, 18 * dp, dp = dp)
        ui.textO(c, weeklyCountdown, leftX + 23 * dp, y, ui.fit(weeklyCountdown, 13f, colWidth - 23 * dp), Ui.TEXT)

        var ry = if (stacked) y + rowGap else leftTop
        Icons.draw(c, Icons.FLAG, rightX + 9 * dp, ry - 4 * dp, 18 * dp, dp = dp)
        for (line in weeklyModsLines) {
            ui.textO(c, line, rightX + 23 * dp, ry, 12f)
            ry += 16 * dp
        }
        ry += 8 * dp
        Icons.draw(c, Icons.TROPHY, rightX + 9 * dp, ry - 4 * dp, 18 * dp, dp = dp)
        ui.textO(c, weeklyBest, rightX + 23 * dp, ry, ui.fit(weeklyBest, 13f, colWidth - 23 * dp), Ui.GOLD)

        val contentBottom = max(y, ry)
        val medalY = min(lowestMedal,
            max(start.r.top - 42 * dp - (medalRows - 1) * medalStep, contentBottom + 17 * dp))
        val mask = weeklyClaimMask
        for (i in 0 until Weekly.TIER_COUNT) {
            val claimed = mask and (1 shl i) != 0
            val tint = if (claimed) tierColors[i] else Ui.DIM
            if (stacked) {
                val my = medalY + i * medalStep
                Icons.draw(c, Icons.MEDAL, x + 11 * dp, my - 1 * dp, 20 * dp, tint = tint, dp = dp)
                ui.textO(c, tierNames[i], x + 27 * dp, my + 4 * dp,
                    ui.fit(tierNames[i], 12f, full - 27 * dp), tint)
                continue
            }
            val centerX = x + (i + 0.5f) * full / Weekly.TIER_COUNT
            Icons.draw(c, Icons.MEDAL, centerX - 38 * dp, medalY - 1 * dp,
                24 * dp, tint = tint, dp = dp)
            ui.textO(c, tierNames[i], centerX - 23 * dp, medalY + 4 * dp,
                ui.fit(tierNames[i], 11f, full / 3f - 35 * dp), tint)
        }
    }

    private fun line(c: Canvas, icon: Int, s: String, x: Float, y: Float, color: Int = Ui.TEXT, size: Float = 15f): Float {
        val isz = 22 * dp
        Icons.draw(c, icon, x + isz / 2, y - 5 * dp, isz, dp = dp)
        ui.textO(c, s, x + isz + 10 * dp, y, size, color)
        return 30 * dp
    }

    override fun render(c: Canvas) {
        bg.draw(c, w, h, dt)
        chrome.dim(c, 100)
        ui.panel(c, panel)
        val pr = g.profile
        val title = when (mode) { Mode.ENDLESS -> L["endless"]; Mode.DAILY -> L["daily"]; else -> L["skirmish"] }
        ui.ribbon(c, panel.centerX(), panel.top + 4 * dp, if (mode == Mode.DAILY && weeklySelected) L["weekly"] else title, 22f, Ui.RIBBON, panel.width() * 0.5f, Ui.GOLD, panel.width() * 0.9f)
        if (mode == Mode.DAILY) {
            ui.button(c, dailyTab, 13f)
            ui.button(c, weeklyTab, 13f)
        }
        if (mode == Mode.DAILY) {
            if (weeklySelected) drawWeekly(c, pr) else drawDaily(c, pr)
            for (bi in buttons.indices) { val b = buttons[bi]; if (b !== start && b !== dailyTab && b !== weeklyTab) ui.button(c, b, 14f) }
            ui.button(c, start, 20f)
            hints.draw(c)
            return
        }

        val mr = 42 * dp
        val mx = panel.left + 26 * dp + mr
        val my = panel.top + (if (mode == Mode.DAILY) 87 else 52) * dp + mr
        ui.medallion(c, mx, my, mr, Ui.ERA[when (mode) { Mode.ENDLESS -> 3; Mode.DAILY -> Defs.ERA_COUNT - 1; else -> 2 }], modeIcon)
        val x = mx + mr + 18 * dp
        var y = panel.top + (if (mode == Mode.DAILY) 93 else 58) * dp
        val tw = panel.right - 24 * dp - x
        val lowLimit = if (mode == Mode.SKIRMISH) diff[0].r.top - 26 * dp else start.r.top - 10 * dp
        when (mode) {
            Mode.ENDLESS -> {
                for (line in endlessInfoLines) {
                    ui.text(c, line, x, y, 15f)
                    y += 15f * dp * ui.textScale * 1.3f
                }
                y += 12 * dp
                y += line(c, Icons.TROPHY, endlessBest, x, y, Ui.GOLD, 16f)
                if (endlessScore.isNotEmpty()) line(c, Icons.STAR, endlessScore, x, y)
            }
            Mode.DAILY -> Unit // Renderowanie dziennej karty jest wyżej.
            else -> {
                y += line(c, Icons.TOWER, skirmishRange, x, y)
                y += line(c, if (aiPower() > 1f) Icons.SKULL else Icons.CHECK, skirmishPower, x, y, if (aiPower() > 1f) Ui.BAD else Ui.GOOD)
                ui.bar(c, x + 32 * dp, y - 14 * dp, min(tw - 32 * dp, 220 * dp), 9 * dp, aiPower() / 1.8f, if (aiPower() > 1f) Ui.BAD else Ui.GOOD)
                y += 14 * dp
                if (y < lowLimit && skirmishScore.isNotEmpty()) line(c, Icons.TROPHY, skirmishScore, x, y, Ui.TEXT, 14f)
                // separator + etykieta trudności
                row.set(panel.left + 22 * dp, diff[0].r.top - 30 * dp, panel.right - 22 * dp, diff[0].r.top - 28 * dp)
                ui.fill.color = Color.argb(70, 255, 255, 255); c.drawRect(row, ui.fill)
                ui.textO(c, L["difficulty"], panel.centerX(), diff[0].r.top - 10 * dp, 14f, Ui.GOLD, Paint.Align.CENTER)
            }
        }
        for (bi in buttons.indices) { val b = buttons[bi]; if (b !== start && b !== dailyTab && b !== weeklyTab) ui.button(c, b, if (b === back) 16f else 14f) }
        ui.button(c, start, 20f)
        hints.draw(c)
    }

    private fun aiPower() = Skirmish.power(g.profile.skirmishDiff)

    // ------------------------------------------------ R11-01: czytnik ekranu

    override fun accessibilityTitle(): String = when (mode) {
        Mode.ENDLESS -> L["endless"]
        Mode.DAILY -> if (weeklySelected) L["weekly"] else L["daily"]
        else -> L["skirmish"]
    }

    override fun accessibilityEligible(b: Button): Boolean =
        if (hints.accessibilityOpen) b === hints.okBtn else b !== hints.okBtn

    override fun accessibilityCheckable(b: Button): Boolean = b.id in 0..3 && mode == Mode.SKIRMISH

    override fun accessibilityInfoCount(): Int = 1

    override fun accessibilityInfo(i: Int): String {
        if (hints.accessibilityOpen) return hints.accessibilityBody
        if (a11yBuiltVersion == a11yVersion && a11yBuiltWeekly == weeklySelected && a11yBuiltLang == L.lang)
            return a11yText
        a11yBuiltVersion = a11yVersion
        a11yBuiltWeekly = weeklySelected
        a11yBuiltLang = L.lang
        val text = when (mode) {
            Mode.ENDLESS -> accessibilitySentences(L["endless_info"], endlessBest, endlessScore)
            Mode.DAILY -> if (weeklySelected) {
                val mask = weeklyClaimMask
                accessibilitySentences(weeklyInfo, weeklyEras, weeklyCard, weeklyMods, weeklyBest,
                    tierText(0, mask), tierText(1, mask), tierText(2, mask))
            } else accessibilitySentences(
                if (dailyEventTitle.isEmpty()) "" else "$dailyEventTitle:", dailyEventDesc,
                dailyEras, dailyPower, if (dailyWeather.isEmpty()) "" else L.f("a196_weather", dailyWeather),
                dailyMods, dailyBest, dailyRanked)
            else -> accessibilitySentences(skirmishRange, skirmishPower, skirmishScore)
        }
        // Ten sam tekst (np. odświeżenie co sekundę) nie przebudowuje drzewa.
        if (text != a11yText) a11yText = text
        return a11yText
    }

    private fun tierText(i: Int, mask: Int): String =
        if (mask and (1 shl i) != 0) tierNames[i] + ", " + L["a196_done"] else tierNames[i]

    override fun accessibilityInfoBounds(i: Int, out: RectF) {
        if (hints.accessibilityOpen) hints.accessibilityBounds(out) else out.set(panel)
    }


    override fun onClick(b: Button) {
        if (hints.onClick(b)) return
        hints.onAction()
        val pr = g.profile
        when (b.id) {
            -1 -> g.pop()
            -2 -> {
                val seed = System.nanoTime()
                when (mode) {
                    Mode.ENDLESS -> g.setScreen(BattleScreen(g, BattleConfig(Mode.ENDLESS, seed, 1f, playerBonuses = pr.bonuses())))
                    Mode.DAILY -> {
                        if (weeklySelected) {
                            val currentWeek = Weekly.weekNumber(Daily.dayNumber())
                            g.setScreen(BattleScreen(g, Weekly.config(currentWeek)))
                        } else {
                            val today = Daily.dayNumber()
                            val ranked = pr.lastRankedDaily != today
                            if (ranked) { pr.lastRankedDaily = today; pr.save() }
                            g.setScreen(BattleScreen(g, Daily.config(today, pr.bonuses()), ranked))
                        }
                    }
                    else -> g.setScreen(BattleScreen(g, BattleConfig(Mode.SKIRMISH, seed, aiPower(), playerBonuses = pr.bonuses())))
                }
            }
            in 0..3 -> { pr.skirmishDiff = b.id; pr.save(); labelTimer = 0f }
            10 -> weeklySelected = false
            11 -> weeklySelected = true
        }
    }
}
