package com.centurywar.screens

import android.graphics.Canvas
import android.graphics.Paint
import com.centurywar.engine.Button
import com.centurywar.engine.Game
import com.centurywar.engine.L
import com.centurywar.engine.Screen
import com.centurywar.engine.Ui
import com.centurywar.game.Mode
import kotlin.math.min

class MenuScreen(g: Game) : Screen(g) {
    private val bg = MenuBg()
    private val campaign = btn(1)
    private val endless = btn(2)
    private val daily = btn(3)
    private val skirmish = btn(4)
    private val research = btn(5)
    private val rankings = btn(6)
    private val settings = btn(7)
    private var dt = 0f

    override fun layout() {
        campaign.label = L["campaign"]; endless.label = L["endless"]; daily.label = L["daily"]
        skirmish.label = L["skirmish"]; research.label = L["research"]; rankings.label = L["rankings"]; settings.label = "⚙"
        campaign.highlight = true
        val bw = min(250 * dp, (w - g.insetL - g.insetR - 60 * dp) / 2)
        val bh = 48 * dp
        val gap = 12 * dp
        val top = h * 0.38f
        val cx = w / 2
        campaign.set(cx - bw - gap / 2, top, bw, bh)
        endless.set(cx + gap / 2, top, bw, bh)
        daily.set(cx - bw - gap / 2, top + bh + gap, bw, bh)
        skirmish.set(cx + gap / 2, top + bh + gap, bw, bh)
        research.set(cx - bw - gap / 2, top + (bh + gap) * 2, bw, bh)
        rankings.set(cx + gap / 2, top + (bh + gap) * 2, bw, bh)
        settings.set(w - g.insetR - 56 * dp, g.insetT + 12 * dp, 44 * dp, 44 * dp)
    }

    override fun update(dt: Float) {
        this.dt = dt
        research.sub = "${g.profile.medals} ${L["medals"]}"
        val pr = g.profile
        daily.sub = if (pr.lastRankedDaily != com.centurywar.game.Daily.dayNumber()) "★ ${L["daily_free"]}" else ""
        campaign.sub = "${pr.totalStars()}/${pr.stars.size * 3} ★"
        endless.sub = if (pr.bestWave > 0) "${L["best"]}: ${pr.bestWave}" else ""
    }

    override fun render(c: Canvas) {
        bg.draw(c, w, h, dt)
        ui.text(c, L["title"], w / 2, h * 0.17f, 44f, Ui.GOLD, Paint.Align.CENTER, true, true)
        ui.text(c, L["subtitle"], w / 2, h * 0.17f + 28 * dp, 15f, Ui.TEXT, Paint.Align.CENTER, false, true)
        for (b in buttons) ui.button(c, b, if (b === settings) 20f else 17f)
        ui.text(c, "${g.profile.name}  •  ${g.profile.medals} ${L["medals"]}", g.insetL + 16 * dp, g.insetT + 30 * dp, 14f, Ui.TEXT, Paint.Align.LEFT, true, true)
        ui.text(c, "v0.1", w - g.insetR - 12 * dp, h - 10 * dp, 10f, Ui.DIM, Paint.Align.RIGHT)
    }

    override fun onClick(b: Button) {
        when (b.id) {
            1 -> g.push(CampaignScreen(g))
            2 -> g.push(ModeScreen(g, Mode.ENDLESS))
            3 -> g.push(ModeScreen(g, Mode.DAILY))
            4 -> g.push(ModeScreen(g, Mode.SKIRMISH))
            5 -> g.push(ResearchScreen(g))
            6 -> g.push(RankingsScreen(g))
            7 -> g.push(SettingsScreen(g))
        }
    }
}
