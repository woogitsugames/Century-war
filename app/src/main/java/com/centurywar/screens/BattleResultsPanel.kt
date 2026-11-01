package com.centurywar.screens

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.centurywar.engine.Icons
import com.centurywar.engine.L
import com.centurywar.engine.Ui
import com.centurywar.game.Mode
import com.centurywar.meta.SkirmishSetup
import kotlin.math.min

internal class BattleResultsPanel(private val screen: BattleScreen) {
    private val resultPanel = RectF()
    private val resultTipLines = arrayOfNulls<String>(16)

    fun layout() {
        with(screen) {
        val rw = min(460 * dp, w - g.insetL - g.insetR - 40 * dp)
        val rh = min(h - g.insetT - g.insetB - 40 * dp, 430 * dp)
        resultPanel.set(w / 2 - rw / 2, h / 2 - rh / 2 + 16 * dp,
            w / 2 + rw / 2, h / 2 + rh / 2 + 16 * dp)
        val gap = 5 * dp
        val usable = rw - 32 * dp - gap * 2
        val bh = 50 * dp
        val first = usable * 0.26f
        val middle = usable * 0.44f
        restartEnd.set(resultPanel.left + 16 * dp, resultPanel.bottom - bh - 22 * dp, first, bh)
        replayEnd.set(restartEnd.r.right + gap, restartEnd.r.top, middle, bh)
        continueBtn.set(replayEnd.r.right + gap, restartEnd.r.top, usable - first - middle, bh)
        if (finished) prepareResultTipLines()
    
        }
    }

    fun refreshTexts() {
        with(screen) {
        if (!finished) return
        val won = battle.winner == 0
        resultTitle = when {
            cfg.weekly >= 0L -> L["weekly_over"]
            cfg.mode == Mode.ENDLESS -> L["siege_over"]
            won -> L["victory"]
            else -> L["defeat"]
        }
        val seconds = battle.time.toInt()
        resultTimeText = "%d:%02d".format(seconds / 60, seconds % 60)
        resultKillsText = battle.player.kills.toString()
        resultWaveText = battle.wave.toString()
        resultWaveLabel = L.f("wave", battle.wave).substringBefore(' ')
        resultBossesText = battle.bossesDefeated.toString()
        resultScoreText = battle.score().toString()
        resultStars = if (cfg.mode == Mode.CAMPAIGN && won) battle.stars() else 0
        resultCustomSkirmish = cfg.mode == Mode.SKIRMISH && !SkirmishSetup.isRanked(cfg)
        resultUnrankedText = L["sk_unranked"]
        resultTimeLabel = L["time"]
        resultKillsLabel = L["kills"]
        resultBossesLabel = L["bosses_defeated"]
        resultWeeklyTiersLabel = L["weekly_tiers"]
        resultScoreLabel = L["score"]
        resultRewardLabel = L["reward"]
        resultNewRecordLabel = L["new_record"]
        weeklyResultLabel = if (weeklyResultKey.isEmpty()) "" else L[weeklyResultKey]
        rewardText = "+" + L.plural("medals_n", totalMedalsGained.toLong())
        retainedProgressText = L.f("battle84_kept", battle.player.kills, missionsAdvanced)
        if (defeatReasonCount > 0) {
            resultReasonText1 = defeatReasonText(defeatReasons[0]!!)
            resultReasonText2 = if (defeatReasonCount > 1) defeatReasonText(defeatReasons[1]!!) else ""
            tipText = L["why_lost"] + ": " + resultReasonText1 +
                (if (resultReasonText2.isNotEmpty()) "; " + resultReasonText2 else "")
        } else {
            resultReasonText1 = ""
            resultReasonText2 = ""
            tipText = if (tipKey.isEmpty()) "" else L["why_lost"] + ": " + L[tipKey]
        }
        prepareResultTipLines()
    
        }
    }

    /** Zawijanie tylko przy rozliczeniu, zmianie języka lub wymiarów ekranu. */
    fun prepareResultTipLines() {
        with(screen) {
        if (tipText.isEmpty()) { resultTipCount = 0; return }
        val scale = ui.textScale
        ui.textScale = min(scale, 1.15f)
        try {
            resultTipCount = HintFrame.wrapInto(ui, tipText,
                resultPanel.width() - 79 * dp, 13f, resultTipLines)
        } finally {
            ui.textScale = scale
        }
    
        }
    }

    /** Rysowanie wcześniej złamanych wierszy nie tworzy nowych ciągów znaków. */
    private fun drawWrappedLines(c: Canvas, lines: Array<String?>, count: Int,
                                 x: Float, y: Float, sizeDp: Float, color: Int, align: Paint.Align) {
        with(screen) {
        var yy = y
        val lineH = sizeDp * dp * ui.textScale * 1.3f
        for (i in 0 until count) {
            val line = lines[i] ?: continue
            ui.text(c, line, x, yy, sizeDp, color, align)
            yy += lineH
        }
    
        }
    }

    private fun drawResultRow(c: Canvas, y: Float, icon: Int, label: String, value: String, color: Int = Ui.TEXT): Float {
        return with(screen) {
        val lh = 26 * dp
        if (y + lh > restartEnd.r.top - 8 * dp) return@with y
        val lx = resultPanel.left + 30 * dp
        val rx = resultPanel.right - 30 * dp
        tmpR.set(lx - 10 * dp, y, rx + 10 * dp, y + lh - 3 * dp)
        ui.fill.color = Color.argb(30, 255, 255, 255)
        c.drawRoundRect(tmpR, 8 * dp, 8 * dp, ui.fill)
        val cy = tmpR.centerY()
        Icons.draw(c, icon, lx + 8 * dp, cy, 18 * dp, dp = dp)
        ui.textO(c, label, lx + 24 * dp, cy + 5 * dp, 14f, Ui.DIM, Paint.Align.LEFT, 1.6f)
        ui.textO(c, value, rx, cy + 5 * dp, 15f, color, Paint.Align.RIGHT, 1.8f)
        y + lh
    
        }
    }

    fun draw(c: Canvas) {
        with(screen) {
        val a = min(1f, resultTime * 2f)
        c.drawColor(Color.argb((170 * a).toInt(), 0, 0, 0))
        val won = battle.winner == 0
        val rp = resultPanel
        // Wjazd panelu od dołu.
        val slide = (1f - a) * 60 * dp
        c.save()
        c.translate(0f, slide)
        ui.panel(c, rp)
        val good = won || cfg.mode == Mode.ENDLESS
        ui.ribbon(c, rp.centerX(), rp.top + 2 * dp, resultTitle, 26f,
            if (good) Color.rgb(206, 140, 30) else Color.rgb(140, 40, 40),
            rp.width() * 0.7f, if (good) Color.rgb(255, 246, 200) else Ui.TEXT, rp.width() + 40 * dp)
        var y = rp.top + 44 * dp
        if (cfg.mode == Mode.CAMPAIGN && won) {
            ui.stars(c, rp.centerX(), y + 22 * dp, 46 * dp, resultStars, 3, 1.05f)
            y += 54 * dp
        } else {
            val iconSize = if (won) 40f else 32f
            Icons.draw(c, if (good) Icons.TROPHY else Icons.SKULL,
                rp.centerX(), y + iconSize * 0.5f * dp, iconSize * dp, dp = dp)
            y += (if (won) 48f else 36f) * dp
        }
        if (resultCustomSkirmish) {
            ui.textO(c, resultUnrankedText, rp.centerX(), y + 7 * dp,
                ui.fit(resultUnrankedText, 14f, rp.width() - 32 * dp), Ui.GOLD, Paint.Align.CENTER)
            y += (if (won) 28f else 20f) * dp
        }
        y = drawResultRow(c, y, Icons.STOPWATCH, resultTimeLabel, resultTimeText)
        y = drawResultRow(c, y, Icons.SKULL, resultKillsLabel, resultKillsText)
        if (cfg.mode == Mode.ENDLESS) {
            y = drawResultRow(c, y, Icons.TOWER, resultWaveLabel, resultWaveText)
            y = drawResultRow(c, y, Icons.CROWN, resultBossesLabel, resultBossesText)
            if (cfg.weekly >= 0L)
                y = drawResultRow(c, y, Icons.MEDAL, resultWeeklyTiersLabel, weeklyResultLabel, Ui.GOLD)
        }
        if (cfg.mode != Mode.CAMPAIGN)
            y = drawResultRow(c, y, Icons.STAR, resultScoreLabel, resultScoreText, Ui.GOLD)
        if (totalMedalsGained > 0) y = drawResultRow(c, y, Icons.MEDAL,
            resultRewardLabel, rewardText, Ui.GOLD)
        if (!won && y + 23 * dp < restartEnd.r.top) {
            ui.textO(c, retainedProgressText, rp.centerX(), y + 17 * dp,
                ui.fit(retainedProgressText, 11f, rp.width() - 32 * dp),
                Ui.GOOD, Paint.Align.CENTER, 1.3f)
            y += 23 * dp
        }
        if (newRecord && y + 26 * dp < restartEnd.r.top) {
            ui.textO(c, resultNewRecordLabel, rp.centerX(), y + 20 * dp, 18f, Ui.GOOD, Paint.Align.CENTER)
            y += 28 * dp
        }
        if (resultReasonText1.isNotEmpty() && y + 20 * dp < restartEnd.r.top) {
            // Dwa konkretne powody zawsze w osobnych wierszach; bez obcięcia drugiego przez zawijanie.
            val lx = rp.left + 53 * dp
            val reasonWidth = rp.right - 15 * dp - lx
            Icons.draw(c, tipIcon, rp.left + 34 * dp, y + 13 * dp, 20 * dp, dp = dp)
            ui.textO(c, resultReasonText1, lx, y + 16 * dp,
                ui.fit(resultReasonText1, 12f, reasonWidth, 8f), Ui.TEXT, Paint.Align.LEFT)
            if (resultReasonText2.isNotEmpty() && y + 39 * dp < restartEnd.r.top)
                ui.textO(c, resultReasonText2, lx, y + 34 * dp,
                    ui.fit(resultReasonText2, 12f, reasonWidth, 8f), Ui.TEXT, Paint.Align.LEFT)
        } else if (tipText.isNotEmpty() && y + 20 * dp < restartEnd.r.top) {
            Icons.draw(c, tipIcon, rp.left + 34 * dp, y + 13 * dp, 20 * dp, dp = dp)
            val available = ((restartEnd.r.top - y - 14 * dp) /
                (13f * dp * ui.textScale * 1.3f)).toInt().coerceAtLeast(0)
            drawWrappedLines(c, resultTipLines, min(resultTipCount, available),
                rp.left + 53 * dp, y + 18 * dp, 13f, Ui.TEXT, Paint.Align.LEFT)
        }
        c.restore()
        ui.button(c, continueBtn, 17f)
        ui.button(c, restartEnd, 17f)
        if (replayEnd.visible) ui.button(c, replayEnd, 15f)
    
        }
    }
}
