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
import com.centurywar.meta.Achievements
import com.centurywar.meta.Profile
import com.centurywar.meta.ResearchDef
import kotlin.math.max
import kotlin.math.min

/** Drzewko badań: trwałe ulepszenia kupowane za medale z każdej bitwy (także przegranej). */
class ResearchScreen(g: Game) : Screen(g) {
    private val bg = MenuBg()
    private val chrome = Chrome(this)
    private val back = btn(-1)
    private val cards = Profile.research.mapIndexed { i, d -> btn(i).also { it.tag = d } }
    private var scroll = 0f
    private var maxScroll = 0f
    private var cols = 3
    private var cw = 0f
    private var ch = 0f
    private var top = 0f
    private var dt = 0f
    private val tmp = RectF()
    private val hintMedalsRect = RectF()
    private val hints = HintOverlay(g)

    init { buttons.add(0, hints.okBtn) }
    private val costs = arrayOfNulls<String>(cards.size)
    private val costLv = IntArray(cards.size) { -1 }

    private fun iconFor(key: String) = when (key) {
        "r_elite" -> Icons.CROWN
        "r_rally" -> Icons.FLAG
        "r_income" -> Icons.COINS
        "r_unitDmg" -> Icons.SWORD
        "r_unitHp" -> Icons.SHIELD
        "r_startGold" -> Icons.CHEST
        "r_baseHp" -> Icons.WALL
        "r_turretDmg" -> Icons.CROSSHAIR
        "r_train" -> Icons.STOPWATCH
        "r_xp" -> Icons.BOOK
        "r_cap" -> Icons.TENT
        "r_cd" -> Icons.HOURGLASS
        else -> Icons.WRENCH
    }

    private fun colorFor(key: String) = when (key) {
        "r_elite", "r_rally" -> Color.rgb(110, 76, 170)
        "r_income", "r_startGold" -> Color.rgb(176, 128, 40)
        "r_unitDmg", "r_turretDmg" -> Color.rgb(170, 60, 50)
        "r_unitHp", "r_baseHp" -> Color.rgb(60, 104, 170)
        "r_xp", "r_cd" -> Color.rgb(40, 130, 150)
        else -> Color.rgb(84, 120, 70)
    }

    override fun isDragScreen() = true

    override fun layout() {
        chrome.layoutBack(back)
        val avail = w - g.insetL - g.insetR - 32 * dp
        cols = max(if (ui.textScale > 1.15f) 1 else 2, (avail / ((if (ui.textScale > 1.15f) 280f else 250f) * dp)).toInt())
        cw = (avail - (cols - 1) * 12 * dp) / cols
        ch = (if (ui.textScale > 1.15f) 128f else 108f) * dp
        top = chrome.top + 6 * dp
        val rows = (cards.size + cols - 1) / cols
        maxScroll = max(0f, top + rows * (ch + 12 * dp) + 12 * dp - h + g.insetB)
        place()
        hintMedalsRect.set(w - g.insetR - 172 * dp, g.insetT + 7 * dp,
            w - g.insetR - 12 * dp, g.insetT + 48 * dp)
        hints.showScreen(HintOverlay.RESEARCH, hintMedalsRect)
    }

    private fun place() {
        for ((i, b) in cards.withIndex()) {
            val d = b.tag as ResearchDef
            b.set(g.insetL + 16 * dp + (i % cols) * (cw + 12 * dp), top + 6 * dp + (i / cols) * (ch + 12 * dp) - scroll, cw, ch)
            b.enabled = g.profile.canBuy(d)
            b.label = ""
            val lv = g.profile.level(d.key)
            if (costLv[i] != lv) { costLv[i] = lv; costs[i] = if (lv >= d.max) L["max"] else d.cost(lv).toString() }
        }
    }

    override fun update(dt: Float) { this.dt = dt; place(); hints.update(dt) }
    override fun onDrag(dx: Float, dy: Float) { hints.onAction(); scroll = (scroll - dy).coerceIn(0f, maxScroll) }

    override fun render(c: Canvas) {
        bg.draw(c, w, h, dt)
        chrome.dim(c, 120)
        c.save()
        c.clipRect(0f, top, w, h)
        for ((i, b) in cards.withIndex()) {
            if (b.r.bottom < top - 10 * dp || b.r.top > h) continue
            drawCard(c, i, b)
        }
        c.restore()
        ui.button(c, back, 16f)
        chrome.title(c, L["research"])
        chrome.badges(c, g.profile.medals)
        hints.draw(c)
    }

    private fun drawCard(c: Canvas, i: Int, b: Button) {
        val d = b.tag as ResearchDef
        val lv = g.profile.level(d.key)
        val maxed = lv >= d.max
        val can = b.enabled
        val off = if (b.pressed) 2 * dp else 0f
        tmp.set(b.r.left, b.r.top + off, b.r.right, b.r.bottom + off)
        val body = when {
            maxed -> Color.rgb(44, 82, 56)
            can -> Color.rgb(52, 66, 94)
            else -> Color.rgb(38, 44, 60)
        }
        ui.card(c, tmp, body, 14f, if (can) Ui.GOLD else 0)
        val r = tmp
        val mr = min(32 * dp, r.height() * 0.3f)
        val mx = r.left + 12 * dp + mr
        val my = r.top + 12 * dp + mr
        ui.medallion(c, mx, my, mr, colorFor(d.key), iconFor(d.key), if (maxed) Ui.GOOD else Ui.TRIM)
        val x = mx + mr + 10 * dp
        val tw = r.right - 12 * dp - x
        val name = L[d.key]
        ui.textO(c, name, x, r.top + 26 * dp, ui.fit(name, 16f, tw), Ui.TEXT)
        val desc = L[d.key + "_d"]
        ui.text(c, desc, x, r.top + 46 * dp, ui.fit(desc, 12f, tw).let { min(it, 12f) }, Color.rgb(214, 218, 228))
        // pasek poziomu + liczba
        val by = r.bottom - 36 * dp
        ui.bar(c, r.left + 14 * dp, by, r.width() * 0.5f, 8 * dp, lv / d.max.toFloat(), if (maxed) Ui.GOOD else Ui.XP)
        ui.textO(c, L.f("level", lv, d.max), r.left + 14 * dp, r.bottom - 13 * dp, 12f, Ui.DIM, Paint.Align.LEFT, 1.6f)
        // przycisk kosztu
        val cost = costs[i] ?: ""
        val pw = ui.measure(cost, 14f, true) + 40 * dp
        val pl = r.right - 12 * dp - pw
        tmp.set(pl, r.bottom - 40 * dp, r.right - 12 * dp, r.bottom - 12 * dp)
        val pill = when { maxed -> Ui.BTN_GREEN; can -> Ui.BTN_HI; else -> Color.rgb(70, 74, 88) }
        ui.card(c, tmp, pill, 10f)
        val cy = tmp.centerY() - 1.5f * dp
        if (maxed) {
            Icons.draw(c, Icons.CHECK, pl + 15 * dp, cy, 18 * dp, dp = dp)
        } else {
            Icons.draw(c, Icons.MEDAL, pl + 15 * dp, cy, 18 * dp, dp = dp)
        }
        ui.textO(c, cost, pl + 27 * dp, cy + 5 * dp, 14f, if (maxed || can) Ui.TEXT else Ui.DIM)
    }

    override fun onClick(b: Button) {
        if (hints.onClick(b)) return
        hints.onAction()
        if (b.id == -1) { g.pop(); return }
        val d = b.tag as? ResearchDef ?: return
        if (g.profile.buy(d)) {
            g.showAchievements(Achievements.onResearchBought(g.profile, d, g.onlineAchievements))
            g.haptic(30)
        }
    }
}
