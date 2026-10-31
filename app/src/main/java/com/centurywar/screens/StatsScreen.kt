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
import com.centurywar.game.AiPersonality
import com.centurywar.game.Daily
import com.centurywar.game.Defs
import com.centurywar.game.Doctrine
import com.centurywar.game.EndlessCard
import com.centurywar.game.Mode
import com.centurywar.game.Modifier
import com.centurywar.meta.BattleHistory
import com.centurywar.meta.Profile
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/** Dwie zakładki: podsumowanie całego profilu i lokalna historia 100 bitew. */
class StatsScreen(g: Game) : Screen(g) {
    private val bg = MenuBg()
    private val chrome = Chrome(this)
    private val back = btn(-1)
    private val tabs = Array(2) { btn(it + 10) }
    private val detailBack = btn(12)
    private val panel = RectF()
    private val row = RectF()
    private val chart = RectF()
    private val modeKeys = arrayOf("campaign", "skirmish", "endless", "daily")
    private val modeIcons = intArrayOf(Icons.MAP, Icons.CROSSED_SWORDS, Icons.TOWER, Icons.CALENDAR)
    private val roleKeys = arrayOf("bh_role_0", "bh_role_1", "bh_role_2", "bh_role_3")
    private val aiKeys = arrayOf("bh_ai_0", "bh_ai_1", "bh_ai_2", "bh_ai_3", "bh_ai_4")
    private var tab = 0
    private var selected = -1
    private var scroll = 0f
    private var maxScroll = 0f
    private var elapsed = 0f
    private var history: List<BattleHistory.Record> = emptyList()
    private var metrics = BattleHistory.aggregate(emptyList(), 0L)
    private val modeTexts = Array(4) { "" }
    private val bestTexts = Array(4) { "" }
    private val extraKeys = arrayOf("bh_streak", "bh_best_streak", "bh_total_time", "bh_role", "bh_doctrine")
    private val extraTexts = Array(5) { "" }
    private val aiNames = Array(5) { "" }
    private val aiTexts = Array(5) { "" }
    private var emptyText = ""
    private var battlesLegend = ""
    private var winsLegend = ""
    private var dayMax = 1
    private var endlessMax = 1L
    private val historyTitles = ArrayList<String>(BattleHistory.LIMIT)
    private val historySubtitles = ArrayList<String>(BattleHistory.LIMIT)
    private val historyTimes = ArrayList<String>(BattleHistory.LIMIT)
    private val detailNames = ArrayList<String>(48)
    private val detailValues = ArrayList<String>(48)
    private val detailIcons = ArrayList<Int>(48)
    private val tipLines = ArrayList<String>(5)
    private val tipPanel = RectF()

    private fun clock(s: Long): String {
        val seconds = s.coerceAtLeast(0L)
        return if (seconds >= 3600L) String.format(Locale.ROOT, "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
        else String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60)
    }

    private fun dateLabel(date: Long, now: Long): String {
        val days = (BattleHistory.dayNumber(now) - BattleHistory.dayNumber(date)).coerceAtLeast(0L)
        return when (days) {
            0L -> L["bh_today"]
            1L -> L["bh_yesterday"]
            else -> L.f("bh_days_ago", days.coerceAtMost(999_999L).toInt())
        }
    }

    private fun resultLabel(result: Int): String = when (result) {
        BattleHistory.WIN -> L["victory"]
        BattleHistory.SURRENDER -> L["bh_surrendered"]
        else -> L["defeat"]
    }

    private fun modeLabel(i: Int): String = L[modeKeys[i.coerceIn(0, 3)]]

    private fun refresh() {
        history = g.history?.snapshot() ?: emptyList()
        val now = System.currentTimeMillis()
        metrics = BattleHistory.aggregate(history, now)
        val p = g.profile
        var totalModeTime = 0L
        for (i in modeTexts.indices) {
            val battles = p.modeBattles[i]
            val wins = p.modeWins[i]
            modeTexts[i] = wins.toString() + "/" + battles + " (" +
                BattleHistory.percent(wins, battles) + "%) · " + clock(p.modeSeconds[i])
            totalModeTime += p.modeSeconds[i]
        }
        extraTexts[0] = p.winStreak.toString()
        extraTexts[1] = p.bestWinStreak.toString()
        extraTexts[2] = clock(max(p.playSeconds, totalModeTime))
        val trainedTotal = metrics.roles.fold(0L) { total, n -> total + n }
        extraTexts[3] = if (metrics.favoriteRole >= 0 && trainedTotal > 0L) {
            val share = metrics.roles[metrics.favoriteRole].toLong() * 100L / trainedTotal
            L[roleKeys[metrics.favoriteRole]] + " · " + share + "%"
        } else L["none"]
        extraTexts[4] = if (metrics.favoriteDoctrine >= 0) L[Doctrine.entries[metrics.favoriteDoctrine].key] else L["none"]
        val boardIds = arrayOf(Profile.BOARD_CAMPAIGN, Profile.BOARD_SKIRMISH,
            Profile.BOARD_ENDLESS, Profile.dailyBoard(Daily.dayNumber(now)))
        for (i in bestTexts.indices) {
            bestTexts[i] = p.top(boardIds[i]).maxOfOrNull { it.score }?.toString() ?: L["none"]
        }
        for (i in aiNames.indices) {
            aiNames[i] = L[aiKeys[i]]
            val total = metrics.aiBattles[i]
            val wins = metrics.aiWins[i]
            aiTexts[i] = wins.toString() + "/" + total + " (" + BattleHistory.percent(wins, total) + "%)"
        }
        dayMax = max(1, metrics.days.maxOrNull() ?: 0)
        endlessMax = max(1L, metrics.endlessScores.maxOrNull() ?: 0L)
        emptyText = L["bh_empty"]
        battlesLegend = L["bh_battles"]
        winsLegend = L["bh_wins"]
        historyTitles.clear(); historySubtitles.clear(); historyTimes.clear()
        for (e in history) {
            val caption = modeLabel(e.mode) + " · " + resultLabel(e.result)
            val stage = if (e.mode == Mode.CAMPAIGN.ordinal && e.stage >= 0)
                " · " + L.f("stage_n", e.stage + 1) else ""
            historyTitles.add(caption + stage)
            historySubtitles.add(dateLabel(e.date, now) + " · " + L["score"] + ": " + e.score)
            historyTimes.add(clock(e.seconds.toLong()))
        }
    }

    override fun isDragScreen() = true

    override fun layout() {
        chrome.layoutBack(back)
        val pw = min(950 * dp, w - g.insetL - g.insetR - 24 * dp)
        val cx = (w + g.insetL - g.insetR) / 2f
        val gap = 8 * dp
        val tw = (pw - gap) / 2f
        tabs[0].label = L["stats"]; tabs[0].iconId = Icons.CHART
        tabs[1].label = L["bh_history"]; tabs[1].iconId = Icons.BOOK
        tabs[0].set(cx - pw / 2, chrome.top + 3 * dp, tw, 42 * dp)
        tabs[1].set(cx + gap / 2, chrome.top + 3 * dp, tw, 42 * dp)
        detailBack.label = L["back"]; detailBack.iconId = Icons.BACK
        detailBack.set(cx - pw / 2 + 8 * dp, chrome.top + 5 * dp, min(132 * dp, pw * 0.4f), 38 * dp)
        panel.set(cx - pw / 2, chrome.top + 53 * dp, cx + pw / 2, h - g.insetB - 12 * dp)
        refresh()
        if (selected in history.indices) openDetails(selected) else {
            selected = -1
            adjustScroll()
        }
    }

    private fun adjustScroll() {
        val content = when {
            selected >= 0 -> (detailNames.size * 34 + 38 +
                if (tipLines.isEmpty()) 0 else (48 + tipLines.size * 19)) * dp
            tab == 1 -> history.size * 66 * dp + 24 * dp
            else -> 970 * dp // sekcje, dwa wykresy i pięć wierszy AI
        }
        maxScroll = max(0f, content - panel.height() + 26 * dp)
        scroll = scroll.coerceIn(0f, maxScroll)
        for (b in tabs) b.visible = selected < 0
        detailBack.visible = selected >= 0
        for (i in tabs.indices) {
            tabs[i].selected = tab == i
            tabs[i].color = if (tab == i) Color.rgb(84, 104, 140) else Ui.BTN
        }
    }

    override fun update(dt: Float) { elapsed = dt }

    override fun onDrag(dx: Float, dy: Float) {
        scroll = (scroll - dy).coerceIn(0f, maxScroll)
    }

    override fun onTap(x: Float, y: Float) {
        if (selected >= 0 || tab != 1 || !panel.contains(x, y)) return
        val index = ((y - panel.top - 12 * dp + scroll) / (66 * dp)).toInt()
        if (index in history.indices) openDetails(index)
    }

    private fun detail(label: String, value: String, icon: Int = -1) {
        detailNames.add(label); detailValues.add(value); detailIcons.add(icon)
    }

    private fun openDetails(index: Int) {
        selected = index
        scroll = 0f
        detailNames.clear(); detailValues.clear(); detailIcons.clear(); tipLines.clear()
        val e = history[index]
        val now = System.currentTimeMillis()
        detail(L["bh_history"], modeLabel(e.mode) + " · " + resultLabel(e.result), modeIcons[e.mode])
        detail(L["bh_date"], dateLabel(e.date, now))
        if (e.stage >= 0 && e.mode == Mode.CAMPAIGN.ordinal) detail(L["bh_stage"], (e.stage + 1).toString(), Icons.MAP)
        detail(L["stars"], e.stars.toString(), Icons.STAR)
        detail(L["score"], e.score.toString(), Icons.TROPHY)
        detail(L["time"], clock(e.seconds.toLong()), Icons.STOPWATCH)
        detail(L["kills"], e.kills.toString(), Icons.SWORD)
        detail(L["bh_lost"], e.lost.toString(), Icons.SKULL)
        detail(L["bh_era"], Defs.eraKeys.getOrNull(e.era)?.let { L[it] } ?: e.era.toString(), Icons.EVOLVE)
        detail(L["bh_advanced"], e.advanced.toString(), Icons.CHEVRONS)
        detail(L["bh_roles"], "", Icons.HELMET)
        for (i in roleKeys.indices) detail(L[roleKeys[i]], e.roles.getOrElse(i) { 0 }.toString(), Icons.PERSON)
        detail(L["bh_doctrines"], "", Icons.BOOK)
        var doctrineCount = 0
        for (i in Doctrine.entries.indices) if ((e.doctrines and (1 shl i)) != 0) {
            doctrineCount++
            detail(L[Doctrine.entries[i].key], "", doctrineIcon(i))
        }
        if (doctrineCount == 0) detail(L["none"], "")
        if (e.mode == Mode.ENDLESS.ordinal) {
            detail(L["bh_cards"], "", Icons.BOOK)
            var cardCount = 0
            for (i in EndlessCard.entries.indices) {
                val n = e.cards.getOrElse(i) { 0 }
                if (n > 0) {
                    cardCount++
                    detail(L[EndlessCard.entries[i].textKey], "×" + n, Icons.CHECK)
                }
            }
            if (cardCount == 0) detail(L["none"], "")
        }
        detail(L["turrets"], e.turrets.toString(), Icons.TOWER)
        detail(L["bh_barrages"], e.barrages.toString(), Icons.BOMB)
        detail(L["rally"], e.rallies.toString(), Icons.BOLT)
        detail(L["bh_gold"], e.gold.toString(), Icons.COINS)
        detail(L["bh_damage"], e.baseDamage.toString(), Icons.SHIELD)
        if (e.mode == Mode.ENDLESS.ordinal) {
            detail(L["bh_wave"], e.wave.toString(), Icons.FLAG)
            detail(L["bh_bosses"], e.bosses.toString(), Icons.CROWN)
        }
        detail(L["ai_power"], e.aiPower.toString() + "%", Icons.HELMET)
        if (e.personality in aiKeys.indices) detail(L["bh_opponent"], L[aiKeys[e.personality]], Icons.HELMET)
        detail(L["modifiers"], "", Icons.FLASK)
        var modCount = 0
        for (i in Modifier.entries.indices) if ((e.modifiers and (1 shl i)) != 0) {
            modCount++
            detail(L[Modifier.entries[i].key], "", Icons.FLASK)
        }
        if (modCount == 0) detail(L["none"], "")
        detail(L["reward"], e.medals.toString(), Icons.MEDAL)
        if (e.result != BattleHistory.WIN && e.tipKey.isNotEmpty()) {
            val maxWidth = max(100f * dp, panel.width() - 64 * dp)
            var current = ""
            for (word in L[e.tipKey].split(' ')) {
                val next = if (current.isEmpty()) word else current + " " + word
                if (current.isNotEmpty() && ui.measure(next, 12f) > maxWidth) {
                    tipLines.add(current)
                    current = word
                } else current = next
            }
            if (current.isNotEmpty()) tipLines.add(current)
        }
        // Identyfikator powtórki można już przechować; odtwarzacz dołącza zadanie 29.
        if (e.replayId.isNotEmpty()) detail(L["bh_replay"], e.replayId, Icons.PLAY)
        adjustScroll()
    }

    private fun doctrineIcon(i: Int): Int = when (i) {
        0 -> Icons.COINS
        1 -> Icons.BOMB
        2 -> Icons.SHIELD
        3 -> Icons.BOLT
        4 -> Icons.CHEVRONS
        5 -> Icons.FLAME
        6 -> Icons.TOWER
        7 -> Icons.HOURGLASS
        else -> Icons.BAG
    }

    override fun onBack(): Boolean {
        if (selected >= 0) {
            selected = -1
            scroll = 0f
            adjustScroll()
            return true
        }
        return false
    }

    override fun onClick(b: Button) {
        when (b.id) {
            -1 -> if (!onBack()) g.pop()
            12 -> onBack()
            10, 11 -> {
                tab = b.id - 10
                scroll = 0f
                adjustScroll()
            }
        }
    }

    override fun render(c: Canvas) {
        bg.draw(c, w, h, elapsed)
        chrome.dim(c, 120)
        ui.panel(c, panel)
        if (selected >= 0) ui.button(c, detailBack, 14f)
        else for (i in tabs.indices) ui.button(c, tabs[i], 14f)
        c.save()
        c.clipRect(panel.left + 3 * dp, panel.top + 3 * dp,
            panel.right - 3 * dp, panel.bottom - 4 * dp)
        if (selected >= 0) drawDetails(c)
        else if (tab == 0) drawStats(c) else drawHistory(c)
        c.restore()
        ui.button(c, back, 16f)
        chrome.title(c, if (selected >= 0) L["bh_details"] else L["stats"])
    }

    private fun drawSection(c: Canvas, title: String, y: Float) {
        ui.textO(c, title, panel.left + 20 * dp, y + 16 * dp, 14f, Ui.GOLD)
        ui.fill.color = Color.argb(85, 220, 170, 70)
        c.drawRect(panel.left + 18 * dp, y + 21 * dp, panel.right - 18 * dp, y + 23 * dp, ui.fill)
    }

    private fun drawPair(c: Canvas, title: String, value: String, y: Float, icon: Int = -1) {
        val l = panel.left + 18 * dp
        val r = panel.right - 18 * dp
        row.set(l, y, r, y + 29 * dp)
        ui.fill.color = Color.argb(34, 255, 255, 255)
        c.drawRoundRect(row, 5 * dp, 5 * dp, ui.fill)
        val textL = l + (if (icon >= 0) 36f else 10f) * dp
        if (icon >= 0) Icons.draw(c, icon, l + 17 * dp, y + 15 * dp, 20 * dp, dp = dp)
        val labelWidth = max(50f * dp, row.width() * 0.48f)
        ui.textO(c, title, textL, y + 20 * dp, ui.fit(title, 12.5f, labelWidth), Ui.TEXT)
        ui.textO(c, value, r - 10 * dp, y + 20 * dp,
            ui.fit(value, 12.5f, max(40f * dp, row.width() - labelWidth - 42 * dp)),
            Ui.GOLD, Paint.Align.RIGHT)
    }

    private fun drawStats(c: Canvas) {
        var y = panel.top + 8 * dp - scroll
        drawSection(c, L["bh_by_mode"], y); y += 28 * dp
        for (i in modeKeys.indices) {
            drawPair(c, modeLabel(i), modeTexts[i], y, modeIcons[i])
            y += 34 * dp
        }
        drawSection(c, L["bh_total"], y); y += 27 * dp
        for (i in extraTexts.indices) {
            drawPair(c, L[extraKeys[i]], extraTexts[i], y,
                if (i == 3) Icons.HELMET else if (i == 4) Icons.BOOK else Icons.TROPHY)
            y += 29 * dp
        }
        drawSection(c, L["bh_best"], y); y += 27 * dp
        for (i in bestTexts.indices) {
            drawPair(c, modeLabel(i), bestTexts[i], y, modeIcons[i])
            y += 29 * dp
        }
        drawSection(c, L["bh_last14"], y); y += 27 * dp
        drawDays(c, y); y += 136 * dp
        drawSection(c, L["bh_endless20"], y); y += 27 * dp
        drawEndless(c, y); y += 130 * dp
        drawSection(c, L["bh_vs_ai"], y); y += 27 * dp
        for (i in aiNames.indices) {
            drawPair(c, aiNames[i], aiTexts[i], y, Icons.HELMET)
            y += 29 * dp
        }
        ui.text(c, L["bh_local"], panel.centerX(), y + 17 * dp, 10f, Ui.DIM, Paint.Align.CENTER)
    }

    private fun drawDays(c: Canvas, top: Float) {
        val l = panel.left + 18 * dp
        val r = panel.right - 18 * dp
        chart.set(l, top, r, top + 115 * dp)
        ui.card(c, chart, Color.rgb(36, 46, 62), 9f)
        ui.text(c, battlesLegend, l + 12 * dp, top + 16 * dp, 11f, Ui.XP)
        ui.text(c, winsLegend, l + 105 * dp, top + 16 * dp, 11f, Ui.GOOD)
        val baseline = chart.bottom - 10 * dp
        val space = (chart.width() - 26 * dp) / 14f
        for (i in 0 until 14) {
            val x = l + 13 * dp + i * space
            val totalHeight = (metrics.days[i].toFloat() / dayMax) * 76 * dp
            val winHeight = (metrics.dayWins[i].toFloat() / dayMax) * 76 * dp
            ui.fill.color = Ui.XP
            c.drawRect(x + space * 0.12f, baseline - totalHeight,
                x + space * 0.78f, baseline, ui.fill)
            ui.fill.color = Ui.GOOD
            c.drawRect(x + space * 0.12f, baseline - winHeight,
                x + space * 0.78f, baseline, ui.fill)
        }
    }

    private fun drawEndless(c: Canvas, top: Float) {
        val l = panel.left + 18 * dp
        val r = panel.right - 18 * dp
        chart.set(l, top, r, top + 112 * dp)
        ui.card(c, chart, Color.rgb(36, 46, 62), 9f)
        val scores = metrics.endlessScores
        if (scores.isEmpty()) {
            ui.text(c, emptyText, chart.centerX(), chart.centerY(), 12f, Ui.DIM, Paint.Align.CENTER)
            return
        }
        val startX = chart.left + 20 * dp
        val availableWidth = max(1f, chart.width() - 40 * dp)
        val baseline = chart.bottom - 18 * dp
        var lastX = startX
        var lastY = baseline
        for (i in scores.indices) {
            val x = startX + if (scores.size == 1) availableWidth / 2 else availableWidth * i / (scores.size - 1)
            val y = baseline - scores[i].toFloat() / endlessMax.toFloat() * 73 * dp
            if (i > 0) {
                ui.stroke.color = Ui.XP
                ui.stroke.strokeWidth = 3 * dp
                c.drawLine(lastX, lastY, x, y, ui.stroke)
            }
            ui.fill.color = Ui.GOLD
            c.drawCircle(x, y, 3.5f * dp, ui.fill)
            lastX = x; lastY = y
        }
    }

    private fun drawHistory(c: Canvas) {
        if (history.isEmpty()) {
            Icons.draw(c, Icons.BOOK, panel.centerX(), panel.top + 68 * dp, 58 * dp, dp = dp)
            ui.textO(c, emptyText, panel.centerX(), panel.top + 122 * dp, 15f, Ui.DIM, Paint.Align.CENTER)
            return
        }
        val l = panel.left + 12 * dp
        val r = panel.right - 12 * dp
        for (i in history.indices) {
            val y = panel.top + 12 * dp + i * 66 * dp - scroll
            if (y > panel.bottom || y + 61 * dp < panel.top) continue
            row.set(l, y, r, y + 61 * dp)
            ui.card(c, row, Color.rgb(38, 48, 69), 10f)
            val e = history[i]
            Icons.draw(c, modeIcons[e.mode], l + 28 * dp, y + 30 * dp, 30 * dp, dp = dp)
            val tx = l + 55 * dp
            val textWidth = max(45f * dp, row.width() - 176 * dp)
            ui.textO(c, historyTitles[i], tx, y + 25 * dp,
                ui.fit(historyTitles[i], 14f, textWidth),
                if (e.result == BattleHistory.WIN) Ui.GOOD else Ui.BAD)
            ui.text(c, historySubtitles[i], tx, y + 45 * dp,
                ui.fit(historySubtitles[i], 11.5f, textWidth), Ui.DIM)
            ui.textO(c, historyTimes[i], r - 14 * dp, y + 35 * dp,
                13f, Ui.GOLD, Paint.Align.RIGHT)
        }
    }

    private fun drawDetails(c: Canvas) {
        if (selected !in history.indices) return
        for (i in detailNames.indices) {
            val y = panel.top + 10 * dp + i * 34 * dp - scroll
            if (y > panel.bottom || y + 31 * dp < panel.top) continue
            drawPair(c, detailNames[i], detailValues[i], y, detailIcons[i])
        }
        if (tipLines.isEmpty()) return
        val y = panel.top + 12 * dp + detailNames.size * 34 * dp - scroll
        val height = (42 + tipLines.size * 19) * dp
        if (y > panel.bottom || y + height < panel.top) return
        tipPanel.set(panel.left + 18 * dp, y, panel.right - 18 * dp, y + height)
        ui.card(c, tipPanel, Color.rgb(66, 56, 44), 9f, Ui.GOLD)
        Icons.draw(c, Icons.BOOK, tipPanel.left + 20 * dp, y + 18 * dp, 20 * dp, dp = dp)
        ui.textO(c, L["why_lost"], tipPanel.left + 37 * dp, y + 22 * dp, 13f, Ui.GOLD)
        for (i in tipLines.indices) {
            ui.text(c, tipLines[i], tipPanel.left + 18 * dp,
                y + (41 + i * 19) * dp, 12f, Ui.TEXT)
        }
    }
}
