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
import com.centurywar.game.Campaign
import com.centurywar.game.Defs
import com.centurywar.game.Stage
import kotlin.math.max
import kotlin.math.min

/** Mapa kampanii: 8 epok × 4 etapy, przewijana pionowo. */
class CampaignScreen(g: Game) : Screen(g) {
    private val bg = MenuBg()
    private val back = btn(-1)
    private val tiles = Campaign.stages.map { s -> btn(s.index).also { it.tag = s } }
    private val start = btn(-2)
    private val close = btn(-3, "✕")
    private var scroll = 0f
    private var maxScroll = 0f
    private var selected: Stage? = null
    private val info = RectF()
    private var rowH = 0f
    private var top = 0f
    private var dt = 0f

    override fun isDragScreen() = selected == null

    override fun layout() {
        back.label = L["back"]
        back.set(g.insetL + 12 * dp, g.insetT + 10 * dp, 100 * dp, 38 * dp)
        start.label = L["start"]
        rowH = 78 * dp
        top = g.insetT + 60 * dp
        maxScroll = max(0f, top + rowH * Defs.ERA_COUNT + 20 * dp - h)
        val iw = min(440 * dp, w - 40 * dp)
        val ih = min(300 * dp, h - 40 * dp)
        info.set(w / 2 - iw / 2, h / 2 - ih / 2, w / 2 + iw / 2, h / 2 + ih / 2)
        start.set(info.centerX() - 80 * dp, info.bottom - 58 * dp, 160 * dp, 44 * dp)
        close.set(info.right - 44 * dp, info.top + 8 * dp, 34 * dp, 34 * dp)
        // Przewiń do pierwszego nieukończonego etapu.
        val row = g.profile.unlockedStage() / 4
        scroll = (row * rowH - h / 3).coerceIn(0f, maxScroll)
        place()
    }

    private fun place() {
        val l = g.insetL + 150 * dp
        val avail = w - g.insetR - 16 * dp - l
        val tw = min(150 * dp, (avail - 3 * 10 * dp) / 4)
        val unlocked = g.profile.unlockedStage()
        for (t in tiles) {
            val s = t.tag as Stage
            val row = s.index / 4
            val col = s.index % 4
            t.set(l + col * (tw + 10 * dp), top + row * rowH - scroll, tw, rowH - 14 * dp)
            t.label = L.f("stage_n", s.index + 1)
            val st = g.profile.stars[s.index]
            t.sub = if (s.index > unlocked) L["locked"] else "★".repeat(st) + "☆".repeat(3 - st)
            t.enabled = s.index <= unlocked
            t.highlight = s.index == unlocked
            t.visible = selected == null && t.r.bottom > top - rowH && t.r.top < h
        }
        start.visible = selected != null
        close.visible = selected != null
    }

    override fun update(dt: Float) { this.dt = dt; place() }

    override fun onDrag(dx: Float, dy: Float) {
        scroll = (scroll - dy).coerceIn(0f, maxScroll)
    }

    override fun render(c: Canvas) {
        bg.draw(c, w, h, dt)
        c.drawColor(Color.argb(90, 0, 0, 0))
        c.save()
        c.clipRect(0f, top - 8 * dp, w, h)
        for (e in 0 until Defs.ERA_COUNT) {
            val y = top + e * rowH - scroll
            if (y > h || y + rowH < top - rowH) continue
            ui.text(c, L[Defs.eraKeys[e]], g.insetL + 16 * dp, y + 30 * dp, 15f, Ui.GOLD, Paint.Align.LEFT, true, true)
            ui.text(c, "${(e + 1)}/8", g.insetL + 16 * dp, y + 48 * dp, 11f, Ui.DIM)
        }
        for (t in tiles) ui.button(c, t, 14f)
        c.restore()
        ui.button(c, back)
        ui.text(c, "${g.profile.totalStars()} ★   ${g.profile.medals} ${L["medals"]}", w - g.insetR - 16 * dp, g.insetT + 34 * dp, 15f, Ui.GOLD, Paint.Align.RIGHT, true, true)
        selected?.let { drawInfo(c, it) }
    }

    private fun drawInfo(c: Canvas, s: Stage) {
        c.drawColor(Color.argb(140, 0, 0, 0))
        ui.panel(c, info)
        var y = info.top + 36 * dp
        val x = info.left + 20 * dp
        ui.text(c, L.f("stage_n", s.index + 1), x, y, 20f, Ui.GOLD, Paint.Align.LEFT, true); y += 30 * dp
        ui.text(c, L.f("eras_range", L[Defs.eraKeys[s.startEra]], L[Defs.eraKeys[s.maxEra]]), x, y, 14f); y += 24 * dp
        ui.text(c, L.f("ai_power", (s.aiPower * 100).toInt()), x, y, 14f, if (s.aiPower > 1f) Ui.BAD else Ui.GOOD); y += 24 * dp
        val mods = if (s.modifiers.isEmpty()) L["none"] else s.modifiers.joinToString(", ") { L[it.key] }
        y += ui.wrap(c, "${L["modifiers"]}: $mods", x, y, info.width() - 40 * dp, 14f)
        ui.button(c, start, 17f)
        ui.button(c, close)
    }

    override fun onClick(b: Button) {
        when (b.id) {
            -1 -> g.pop()
            -2 -> selected?.let { s ->
                g.setScreen(BattleScreen(g, Campaign.config(s, g.profile.bonuses(), System.nanoTime())))
            }
            -3 -> selected = null
            else -> selected = b.tag as? Stage
        }
    }

    override fun onBack(): Boolean {
        if (selected != null) { selected = null; return true }
        return false
    }
}
