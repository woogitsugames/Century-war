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
import com.centurywar.game.Campaign
import com.centurywar.game.Defs
import com.centurywar.game.Stage
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Mapa kampanii: epoki × 4 etapy, przewijana pionowo. Każda epoka to pas z
 * wstęgą i medalionem epoki, a etapy to węzły na krętej ścieżce (wąż: kolejne
 * epoki idą na przemian w prawo i w lewo).
 */
class CampaignScreen(g: Game) : Screen(g) {
    private val bg = MenuBg()
    private val chrome = Chrome(this)
    private val back = btn(-1)
    private val tiles = Campaign.stages.map { s -> btn(s.index).also { it.tag = s } }
    private val start = btn(-2)
    private val close = btn(-3)
    private var scroll = 0f
    private var maxScroll = 0f
    private var selected: Stage? = null
    private val info = RectF()
    private val band = RectF()
    private val tmp = RectF()
    private var rowH = 0f
    private var top = 0f
    private var nodeR = 0f
    private var bandL = 0f
    private var bandR = 0f
    private var pathL = 0f
    private var dt = 0f
    private var time = 0f
    private val cx = FloatArray(Campaign.stages.size)
    private val cy = FloatArray(Campaign.stages.size)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun isDragScreen() = selected == null

    override fun layout() {
        chrome.layoutBack(back)
        start.label = L["start"]
        start.iconId = Icons.PLAY
        start.highlight = true
        top = chrome.top
        rowH = min(136 * dp, max(112 * dp, (h - top) / 3.2f))
        nodeR = min(28 * dp, rowH * 0.2f)
        bandL = g.insetL + 12 * dp
        bandR = w - g.insetR - 12 * dp
        pathL = bandL + min(200 * dp, (bandR - bandL) * 0.3f)
        maxScroll = max(0f, top + rowH * Defs.ERA_COUNT + 16 * dp - h + g.insetB)
        val iw = min(460 * dp, w - g.insetL - g.insetR - 40 * dp)
        val ih = min(300 * dp, h - g.insetT - g.insetB - 70 * dp)
        info.set(w / 2 - iw / 2, h / 2 - ih / 2 + 22 * dp, w / 2 + iw / 2, h / 2 + ih / 2 + 22 * dp)
        start.set(info.centerX() - 90 * dp, info.bottom - 66 * dp, 180 * dp, 48 * dp)
        close.set(info.right - 30 * dp, info.top - 14 * dp, 42 * dp, 42 * dp)
        // Przewiń do pierwszego nieukończonego etapu.
        val row = g.profile.unlockedStage() / 4
        scroll = (row * rowH - (h - top) / 3).coerceIn(0f, maxScroll)
        place()
    }

    private fun place() {
        val unlocked = g.profile.unlockedStage()
        val span = bandR - pathL - nodeR * 3f
        for (t in tiles) {
            val s = t.tag as Stage
            val row = s.index / 4
            var col = s.index % 4
            if (row % 2 == 1) col = 3 - col
            val x = pathL + nodeR * 1.6f + span * col / 3f
            val y = top + row * rowH - scroll + rowH * 0.44f + (if (col % 2 == 0) -1f else 1f) * rowH * 0.07f
            cx[s.index] = x; cy[s.index] = y
            val r = nodeR * (if (s.index % 4 == 3) 1.1f else 1f)
            t.set(x - r * 1.35f, y - r * 1.25f, r * 2.7f, r * 2.7f)
            // Po wstawieniu epoki weteran może mieć gwiazdki za „dziurą” nowych etapów.
            t.enabled = s.index <= unlocked || g.profile.stars[s.index] > 0
            t.highlight = s.index == unlocked
            t.visible = selected == null && t.r.bottom > top && t.r.top < h
        }
        start.visible = selected != null
        close.visible = selected != null
    }

    override fun update(dt: Float) { this.dt = dt; time += dt; place() }

    override fun onDrag(dx: Float, dy: Float) {
        scroll = (scroll - dy).coerceIn(0f, maxScroll)
    }

    override fun render(c: Canvas) {
        bg.draw(c, w, h, dt)
        chrome.dim(c, 110)
        c.save()
        c.clipRect(0f, top - 4 * dp, w, h)
        val unlocked = g.profile.unlockedStage()
        for (e in 0 until Defs.ERA_COUNT) {
            val y = top + e * rowH - scroll
            if (y > h || y + rowH < top) continue
            drawBand(c, e, y)
        }
        // ścieżka (kropki) między etapami
        for (i in 0 until tiles.size - 1) {
            if (cy[i] > h + rowH || cy[i + 1] < top - rowH) continue
            dots(c, cx[i], cy[i], cx[i + 1], cy[i + 1], i < unlocked)
        }
        for (t in tiles) if (t.r.bottom > top - 10 * dp && t.r.top < h) drawNode(c, t, unlocked)
        c.restore()
        // nagłówek
        ui.button(c, back, 16f)
        chrome.title(c, L["campaign"])
        chrome.badges(c, g.profile.medals, g.profile.totalStars())
        selected?.let { drawInfo(c, it) }
    }

    private fun drawBand(c: Canvas, e: Int, y: Float) {
        val col = Ui.ERA[e]
        band.set(bandL, y + 6 * dp, bandR, y + rowH - 6 * dp)
        ui.card(c, band, Ui.blend(col, Color.rgb(16, 18, 28), 0.62f).let { Color.argb(228, Color.red(it), Color.green(it), Color.blue(it)) }, 16f)
        // pas ziemi pod ścieżką
        fill.color = Color.argb(50, Color.red(col), Color.green(col), Color.blue(col))
        tmp.set(pathL - 6 * dp, band.top + 12 * dp, band.right - 10 * dp, band.bottom - 12 * dp)
        c.drawRoundRect(tmp, 30 * dp, 30 * dp, fill)
        // medalion epoki + nazwa
        val mr = min(30 * dp, rowH * 0.24f)
        val mx = band.left + mr + 14 * dp
        val my = band.top + band.height() * 0.42f
        ui.medallion(c, mx, my, mr, col, -1)
        ui.textO(c, (e + 1).toString(), mx, my + 8 * dp, 22f, Ui.TEXT, Paint.Align.CENTER)
        val tx = mx + mr + 10 * dp
        val avail = pathL - tx - 12 * dp
        val name = L[Defs.eraKeys[e]]
        ui.textO(c, name, tx, my - 2 * dp, ui.fit(name, 17f, avail), Ui.GOLD)
        var es = 0
        for (i in e * 4 until e * 4 + 4) es += g.profile.stars[i]
        Icons.draw(c, Icons.STAR, tx + 8 * dp, my + 16 * dp, 16 * dp, dp = dp)
        ui.textO(c, "$es/12", tx + 19 * dp, my + 21 * dp, 13f, if (es == 12) Ui.GOLD else Ui.TEXT)
    }

    private fun dots(c: Canvas, x0: Float, y0: Float, x1: Float, y1: Float, done: Boolean) {
        val dx = x1 - x0; val dy = y1 - y0
        val len = sqrt(dx * dx + dy * dy)
        val step = 13 * dp
        val n = (len / step).toInt()
        for (i in 1 until n) {
            val f = i / n.toFloat()
            val x = x0 + dx * f
            val y = y0 + dy * f
            if (sqrt((x - x0) * (x - x0) + (y - y0) * (y - y0)) < nodeR * 1.1f) continue
            if (sqrt((x - x1) * (x - x1) + (y - y1) * (y - y1)) < nodeR * 1.1f) continue
            fill.color = Ui.OUTLINE
            c.drawCircle(x, y, 4f * dp, fill)
            fill.color = if (done) Ui.GOLD else Color.rgb(110, 116, 132)
            c.drawCircle(x, y, 2.6f * dp, fill)
        }
    }

    private fun drawNode(c: Canvas, t: Button, unlocked: Int) {
        val s = t.tag as Stage
        val i = s.index
        val big = i % 4 == 3
        val r = nodeR * (if (big) 1.1f else 1f)
        val x = cx[i]
        val y = cy[i] + if (t.pressed) 2 * dp else 0f
        val st = g.profile.stars[i]
        val locked = i > unlocked
        val current = i == unlocked
        val era = i / 4
        if (current) {
            // pulsująca poświata
            val pulse = 0.5f + 0.5f * sin(time * 4f)
            fill.color = Color.argb((90 + 80 * pulse).toInt(), 255, 210, 80)
            c.drawCircle(x, y, r * (1.25f + 0.12f * pulse), fill)
        }
        val body = when {
            locked -> Color.rgb(84, 88, 102)
            current -> Ui.BTN_HI
            else -> Ui.ERA[era]
        }
        val ring = when { locked -> Color.rgb(130, 134, 148); big -> Ui.RIBBON; else -> Ui.TRIM }
        // cień + kontur + pierścień + tarcza
        fill.color = Color.argb(110, 0, 0, 0); c.drawCircle(x, y + 4 * dp, r, fill)
        fill.color = Ui.OUTLINE; c.drawCircle(x, y, r, fill)
        fill.color = ring; c.drawCircle(x, y, r - 2.2f * dp, fill)
        fill.color = Ui.OUTLINE; c.drawCircle(x, y, r - 5f * dp, fill)
        fill.color = Ui.blend(body, Color.BLACK, 0.3f); c.drawCircle(x, y, r - 6.2f * dp, fill)
        fill.color = body; c.drawCircle(x, y - 2.5f * dp, r - 7.5f * dp, fill)
        fill.color = Color.argb(60, 255, 255, 255); c.drawCircle(x - r * 0.25f, y - r * 0.3f, r * 0.28f, fill)
        if (locked) {
            Icons.draw(c, Icons.LOCK, x, y - 2 * dp, r * 0.95f, dp = dp)
        } else {
            ui.textO(c, (i + 1).toString(), x, y + 6.5f * dp, if (big) 21f else 19f, Ui.TEXT, Paint.Align.CENTER)
        }
        if (big) Icons.draw(c, Icons.FLAG, x + r * 0.78f, y - r * 0.8f, r * 0.75f, Ui.RIBBON, dp = dp)
        if (!locked) ui.stars(c, x, y + r + 4 * dp, r * 0.62f, st, 3, 0.95f)
    }

    private fun drawInfo(c: Canvas, s: Stage) {
        c.drawColor(Color.argb(150, 0, 0, 0))
        ui.panel(c, info)
        ui.ribbon(c, info.centerX(), info.top + 4 * dp, L.f("stage_n", s.index + 1), 20f, Ui.ERA[s.index / 4].let { Ui.blend(it, Ui.RIBBON, 0.35f) }, info.width() * 0.55f)
        var y = info.top + 48 * dp
        val x = info.left + 24 * dp
        val tw = info.width() - 48 * dp
        // gwiazdki
        ui.stars(c, info.centerX(), y + 4 * dp, 30 * dp, g.profile.stars[s.index], 3, 1.05f)
        y += 34 * dp
        val isz = 20 * dp
        Icons.draw(c, Icons.TOWER, x + isz / 2, y - 5 * dp, isz, dp = dp)
        ui.textO(c, L.f("eras_range", L[Defs.eraKeys[s.startEra]], L[Defs.eraKeys[s.maxEra]]), x + isz + 8 * dp, y, 14f, Ui.TEXT)
        y += 28 * dp
        val pw = s.aiPower
        Icons.draw(c, if (pw > 1f) Icons.SKULL else Icons.CHECK, x + isz / 2, y - 5 * dp, isz, dp = dp)
        ui.textO(c, L.f("ai_power", (pw * 100).toInt()), x + isz + 8 * dp, y, 14f, if (pw > 1f) Ui.BAD else Ui.GOOD)
        ui.bar(c, x + tw * 0.62f, y - 10 * dp, tw * 0.38f, 8 * dp, (pw / 1.8f).coerceIn(0f, 1f), if (pw > 1f) Ui.BAD else Ui.GOOD)
        y += 28 * dp
        Icons.draw(c, Icons.FLAG, x + isz / 2, y - 5 * dp, isz, dp = dp)
        ui.textO(c, "${L["modifiers"]}:", x + isz + 8 * dp, y, 14f, Ui.GOLD)
        y += 10 * dp
        if (s.modifiers.isEmpty()) {
            ui.text(c, L["none"], x + isz + 8 * dp, y + 16 * dp, 13f, Ui.DIM)
        } else {
            chips(c, s, x, y, tw)
        }
        ui.button(c, start, 18f)
        ui.closeButton(c, close)
    }

    private fun chips(c: Canvas, s: Stage, x0: Float, y0: Float, width: Float) {
        var x = x0
        var y = y0
        val hh = 24 * dp
        for (m in s.modifiers) {
            val label = L[m.key]
            val cw = ui.measure(label, 12f, true) + 18 * dp
            if (x + cw > x0 + width && x > x0) { x = x0; y += hh + 6 * dp }
            if (y + hh > start.r.top - 6 * dp) break
            tmp.set(x, y, x + cw, y + hh)
            ui.card(c, tmp, Color.rgb(84, 60, 110), 10f)
            ui.textO(c, label, x + cw / 2, y + hh * 0.62f, 12f, Ui.TEXT, Paint.Align.CENTER, 1.6f)
            x += cw + 6 * dp
        }
    }

    override fun onClick(b: Button) {
        when (b.id) {
            -1 -> g.pop()
            -2 -> selected?.let { s ->
                g.setScreen(BattleScreen(g, Campaign.config(s, g.profile.bonuses(), System.nanoTime(),
                    tutorial = s.index == 0 && !g.profile.tutorialDone)))
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
