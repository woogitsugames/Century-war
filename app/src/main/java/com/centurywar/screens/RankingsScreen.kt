package com.centurywar.screens

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.centurywar.engine.Button
import com.centurywar.engine.Game
import com.centurywar.engine.L
import com.centurywar.engine.Screen
import com.centurywar.engine.Ui
import com.centurywar.game.Daily
import com.centurywar.meta.Profile
import kotlin.math.min

class RankingsScreen(g: Game) : Screen(g) {
    private val bg = MenuBg()
    private val back = btn(-1)
    private val rename = btn(-2)
    private val tabs = Array(4) { btn(it) }
    private var tab = 0
    private val panel = RectF()
    private var dt = 0f
    private val day = Daily.dayNumber()

    private fun board(i: Int) = when (i) {
        0 -> Profile.BOARD_ENDLESS
        1 -> Profile.dailyBoard(day)
        2 -> Profile.BOARD_CAMPAIGN
        else -> Profile.BOARD_SKIRMISH
    }

    override fun layout() {
        back.label = L["back"]
        back.set(g.insetL + 12 * dp, g.insetT + 10 * dp, 100 * dp, 38 * dp)
        rename.label = "✎ ${L["name"]}"
        rename.set(w - g.insetR - 190 * dp, g.insetT + 10 * dp, 178 * dp, 38 * dp)
        val keys = arrayOf("board_endless", "board_daily", "board_campaign", "board_skirmish")
        val tw = min(190 * dp, (w - g.insetL - g.insetR - 40 * dp) / 4)
        for (i in 0..3) {
            tabs[i].label = L[keys[i]]
            tabs[i].set(w / 2 - tw * 2 - 6 * dp + i * (tw + 4 * dp), g.insetT + 58 * dp, tw, 36 * dp)
        }
        panel.set(w / 2 - min(520 * dp, w * 0.45f), g.insetT + 104 * dp, w / 2 + min(520 * dp, w * 0.45f), h - 12 * dp)
    }

    override fun update(dt: Float) {
        this.dt = dt
        for (i in 0..3) tabs[i].selected = i == tab
    }

    override fun render(c: Canvas) {
        bg.draw(c, w, h, dt)
        c.drawColor(Color.argb(110, 0, 0, 0))
        for (b in tabs) ui.button(c, b, 12f)
        ui.button(c, back); ui.button(c, rename, 13f)
        ui.panel(c, panel)
        val list = g.profile.top(board(tab))
        var y = panel.top + 30 * dp
        if (list.isEmpty()) ui.text(c, L["no_scores"], panel.centerX(), y + 20 * dp, 16f, Ui.DIM, Paint.Align.CENTER)
        val lh = 24 * dp
        for ((i, e) in list.withIndex()) {
            if (y > panel.bottom - 36 * dp) break
            val me = e.name == g.profile.name
            val col = if (i == 0) Ui.GOLD else if (me) Ui.XP else Ui.TEXT
            ui.text(c, "${i + 1}.", panel.left + 18 * dp, y, 14f, col, Paint.Align.LEFT, true)
            ui.text(c, e.name, panel.left + 56 * dp, y, 14f, col)
            ui.text(c, e.score.toString(), panel.right - 18 * dp, y, 14f, col, Paint.Align.RIGHT, true)
            y += lh
        }
        val pr = g.profile
        val hrs = pr.playSeconds / 3600f
        ui.text(c, "${L["stats"]}: ${pr.wins}/${pr.battles}  •  ${L["kills"]} ${pr.totalKills}  •  ${"%.1f".format(hrs)} h",
            panel.centerX(), panel.bottom - 30 * dp, 12f, Ui.DIM, Paint.Align.CENTER)
        ui.text(c, L["online_note"], panel.centerX(), panel.bottom - 12 * dp, 10f, Ui.DIM, Paint.Align.CENTER)
    }

    override fun onClick(b: Button) {
        when (b.id) {
            -1 -> g.pop()
            -2 -> g.host.requestText(L["name"], g.profile.name) { n -> g.profile.name = n; g.profile.save() }
            in 0..3 -> tab = b.id
        }
    }
}
