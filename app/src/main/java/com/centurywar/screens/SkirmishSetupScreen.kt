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
import com.centurywar.engine.TouchEvent
import com.centurywar.engine.Ui
import com.centurywar.game.Defs
import com.centurywar.game.Modifier
import com.centurywar.meta.SkirmishSetup
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/** Potyczka własna: jeden przewijany formularz lub dwie kolumny na rozłożonym Foldzie. */
class SkirmishSetupScreen(g: Game) : Screen(g) {
    private val bg = MenuBg()
    private val chrome = Chrome(this)
    private val back = btn(-1)
    private val play = btn(-2)
    private val defaults = btn(-3)
    private val shuffle = btn(-4)
    private val eraButtons = Array(4) { btn(10 + it) }
    private val personalities = Array(6) { btn(100 + it) }
    private val presets = Array(4) { btn(200 + it) }
    private val powerButtons = Array(2) { btn(220 + it) }
    private val mods = Array(Modifier.values().size) { btn(300 + it) }
    private val panel = RectF()
    private val footer = RectF()
    private val slider = RectF()
    private val marker = RectF()
    private var setup = g.profile.skirmishSetup.sanitized()
    private var scroll = 0f
    private var maxScroll = 0f
    private var contentTop = 0f
    private var contentBottom = 0f
    private var twoColumns = false
    private var draggingPower = false
    private var dt = 0f

    private var colL = 0f
    private var colW = 0f
    private var colR = 0f
    private val labelsY = FloatArray(6)
    private val historyY = FloatArray(10)
    private val historyLines = Array(10) { "" }
    private var historyCount = 0
    private val sectionX = FloatArray(6)
    private var fromY = 0f
    private var toY = 0f
    private var powerY = 0f
    private var descriptionY = 0f
    private var modLimitY = 0f
    private var fromName = ""
    private var toName = ""
    private var powerName = ""
    private var description = ""
    private var summary = ""
    private var rankingText = ""

    private val personIcons = intArrayOf(
        Icons.CROSSED_SWORDS, Icons.WRENCH, Icons.COIN, Icons.TOWER, Icons.SHIELD,
    )
    private val modIcons = intArrayOf(
        Icons.COIN, Icons.WALL, Icons.SPEED, Icons.TOWER, Icons.CROSSHAIR,
        Icons.FLAME, Icons.CHEST, Icons.HOURGLASS, Icons.SHIELD, Icons.SKULL,
    )

    override fun isDragScreen() = true

    override fun layout() {
        chrome.layoutBack(back)
        val pad = 16 * dp
        val availW = w - g.insetL - g.insetR - pad * 2
        val width = min(820 * dp, availW)
        val left = (w + g.insetL - g.insetR - width) / 2
        contentTop = chrome.top + 2 * dp
        contentBottom = max(contentTop + 48 * dp, h - g.insetB - 128 * dp)
        panel.set(left, contentTop, left + width, contentBottom)
        footer.set(left, contentBottom + 5 * dp, left + width, h - g.insetB - 7 * dp)
        twoColumns = width >= 560 * dp && h >= w * 0.68f
        colL = left + 16 * dp
        colW = if (twoColumns) (width - 46 * dp) / 2 else width - 32 * dp
        colR = colL + colW + 14 * dp

        // Wysokość obu kolumn obliczamy niezależnie; przewijamy tylko wspólną listę.
        val top = contentTop + 14 * dp - scroll
        val endLeft = placeLeft(colL, top)
        val endRight = placeModifiers(if (twoColumns) colR else colL,
            if (twoColumns) top else endLeft + 12 * dp)
        val contentHeight = if (twoColumns) max(endLeft, endRight) else endRight
        maxScroll = max(0f, contentHeight - top - (contentBottom - contentTop) + 20 * dp)
        if (scroll > maxScroll) { scroll = maxScroll; layout(); return }

        val gap = 7 * dp
        val minW = min(130 * dp, (width - 50 * dp) * 0.26f)
        val bigW = min(205 * dp, width - 44 * dp - 2 * minW - 2 * gap)
        val total = 2 * minW + bigW + 2 * gap
        val bx = footer.centerX() - total / 2
        val by = footer.bottom - 51 * dp
        defaults.set(bx, by, minW, 43 * dp)
        shuffle.set(bx + minW + gap, by, minW, 43 * dp)
        play.set(bx + 2 * (minW + gap), by, bigW, 43 * dp)
        defaults.label = L["sk_defaults"]; defaults.iconId = Icons.RESTART
        shuffle.label = L["sk_shuffle"]; shuffle.iconId = Icons.SPEED
        play.label = L["play"]; play.iconId = Icons.PLAY; play.highlight = true
        rebuildLabels()
    }

    private fun place(b: Button, x: Float, y: Float, width: Float, height: Float) {
        b.set(x, y, width, height)
        b.visible = y >= contentTop + 2 * dp && y + height <= contentBottom - 2 * dp
    }

    private fun placeLeft(x: Float, top: Float): Float {
        var y = top
        sectionX[0] = x; labelsY[0] = y + 17 * dp
        y += 32 * dp
        fromY = y + 29 * dp
        place(eraButtons[0], x + 4 * dp, y, 45 * dp, 43 * dp)
        place(eraButtons[1], x + colW - 49 * dp, y, 45 * dp, 43 * dp)
        y += 56 * dp
        toY = y + 29 * dp
        place(eraButtons[2], x + 4 * dp, y, 45 * dp, 43 * dp)
        place(eraButtons[3], x + colW - 49 * dp, y, 45 * dp, 43 * dp)
        for (i in eraButtons.indices) {
            eraButtons[i].iconId = if (i % 2 == 0) Icons.BACK else Icons.ADVANCE
            eraButtons[i].label = ""
        }
        y += 64 * dp
        sectionX[1] = x; labelsY[1] = y + 13 * dp
        y += 24 * dp
        val gap = 6 * dp
        val bw = (colW - gap) / 2
        for (i in personalities.indices) {
            val b = personalities[i]
            place(b, x + (i % 2) * (bw + gap), y + (i / 2) * 47 * dp, bw, 41 * dp)
            b.label = if (i == 0) L["sk_random"] else L["sk_person_" + (i - 1)]
            b.iconId = if (i == 0) Icons.CROSSED_SWORDS else personIcons[i - 1]
            b.selected = setup.personality == i - 1
        }
        y += 145 * dp
        descriptionY = y + 10 * dp
        y += 45 * dp
        sectionX[2] = x; labelsY[2] = y + 14 * dp
        y += 25 * dp
        val pGap = 5 * dp
        val pw = (colW - pGap * 3) / 4
        for (i in presets.indices) {
            val b = presets[i]
            place(b, x + i * (pw + pGap), y, pw, 38 * dp)
            b.label = L["diff_" + i]
            b.selected = setup.powerPercent == SkirmishSetup.PRESET_POWER[i]
        }
        y += 51 * dp
        powerY = y + 13 * dp
        y += 18 * dp
        place(powerButtons[0], x + 4 * dp, y, 43 * dp, 39 * dp)
        place(powerButtons[1], x + colW - 47 * dp, y, 43 * dp, 39 * dp)
        powerButtons[0].iconId = Icons.BACK
        powerButtons[1].iconId = Icons.ADVANCE
        slider.set(x + 59 * dp, y + 10 * dp, x + colW - 59 * dp, y + 28 * dp)
        y += 59 * dp
        sectionX[4] = x; labelsY[4] = y + 15 * dp
        y += 29 * dp
        historyCount = max(1, min(10, g.profile.skirmishHistory.size))
        for (i in 0 until historyCount) {
            historyY[i] = y + 14 * dp
            y += 24 * dp
        }
        return y + 8 * dp
    }

    private fun placeModifiers(x: Float, top: Float): Float {
        sectionX[3] = x; labelsY[3] = top + 16 * dp
        modLimitY = top + 33 * dp
        val startY = top + 48 * dp
        for (i in mods.indices) {
            val b = mods[i]
            place(b, x, startY + i * 56 * dp, colW, 52 * dp)
            b.label = L[Modifier.values()[i].key]
            b.sub = L["sk_moddesc_" + i]
            b.iconId = modIcons[i]
            b.selected = (setup.modifierMask and (1 shl i)) != 0
            b.subColor = Ui.DIM
        }
        return startY + mods.size * 56 * dp
    }

    private fun rebuildLabels() {
        fromName = L["sk_start"] + ": " + L[Defs.eraKeys[setup.startEra]]
        toName = L["sk_end"] + ": " + L[Defs.eraKeys[setup.maxEra]]
        powerName = L.f("ai_power", setup.powerPercent)
        description = if (setup.personality == -1) L["sk_desc_random"]
            else L["sk_desc_" + setup.personality]
        summary = L.f("eras_range", L[Defs.eraKeys[setup.startEra]], L[Defs.eraKeys[setup.maxEra]]) +
            "   " + L.f("ai_power", setup.powerPercent)
        rankingText = L[if (setup.isRanked()) "sk_ranked" else "sk_unranked"]
        for (i in 0 until historyCount) {
            val r = g.profile.skirmishHistory.getOrNull(i)
            historyLines[i] = if (r == null) L["sk_history_empty"] else {
                val date = if (r.day in 0L..120000L) java.time.LocalDate.ofEpochDay(r.day).toString() else "?"
                date + "  ·  " + r.score + "  ·  " +
                    L[if (r.won) "sk_win" else "sk_loss"] + "  ·  " +
                    L[if (r.ranked) "sk_classic" else "sk_custom"]
            }
        }
    }

    override fun update(dt: Float) { this.dt = dt }

    private fun textIfVisible(c: Canvas, s: String, x: Float, y: Float, size: Float,
                              color: Int = Ui.TEXT, align: Paint.Align = Paint.Align.LEFT,
                              width: Float = colW) {
        if (y >= contentTop + 10 * dp && y <= contentBottom - 4 * dp) {
            ui.textO(c, s, x, y, ui.fit(s, size, width), color, align)
        }
    }

    override fun render(c: Canvas) {
        bg.draw(c, w, h, dt)
        chrome.dim(c, 125)
        ui.panel(c, panel)
        ui.panel(c, footer)
        ui.ribbon(c, w / 2 + (g.insetL - g.insetR) / 2, g.insetT + 31 * dp,
            L["sk_setup"], 20f, Ui.RIBBON, 175 * dp, Ui.GOLD, min(w * 0.58f, 480 * dp))
        ui.button(c, back, 14f)

        c.save()
        c.clipRect(panel.left + 8 * dp, contentTop + 5 * dp, panel.right - 8 * dp, contentBottom - 5 * dp)
        textIfVisible(c, L["sk_eras"], sectionX[0], labelsY[0], 17f, Ui.GOLD)
        textIfVisible(c, fromName, colL + colW / 2, fromY, 14f, Ui.TEXT, Paint.Align.CENTER, colW - 106 * dp)
        textIfVisible(c, toName, colL + colW / 2, toY, 14f, Ui.TEXT, Paint.Align.CENTER, colW - 106 * dp)
        textIfVisible(c, L["sk_opponent"], sectionX[1], labelsY[1], 17f, Ui.GOLD)
        textIfVisible(c, description, colL + colW / 2, descriptionY, 12f, Ui.DIM, Paint.Align.CENTER, colW - 12 * dp)
        textIfVisible(c, L["sk_strength"], sectionX[2], labelsY[2], 17f, Ui.GOLD)
        textIfVisible(c, powerName, colL + colW / 2, powerY, 14f, Ui.GOLD, Paint.Align.CENTER, colW)
        if (slider.bottom > contentTop + 5 * dp && slider.top < contentBottom - 5 * dp) {
            ui.bar(c, slider.left, slider.centerY() - 4 * dp, slider.width(), 8 * dp,
                (setup.powerPercent - 60) / 140f, Ui.GOLD)
            val x = slider.left + (setup.powerPercent - 60) / 140f * slider.width()
            ui.fill.color = Ui.TEXT
            c.drawCircle(x, slider.centerY(), 10 * dp, ui.fill)
            ui.fill.color = Ui.GOLD
            c.drawCircle(x, slider.centerY(), 7 * dp, ui.fill)
        }
        textIfVisible(c, L["sk_history"], sectionX[4], labelsY[4], 17f, Ui.GOLD)
        for (i in 0 until historyCount)
            textIfVisible(c, historyLines[i], colL, historyY[i], 11f, Ui.TEXT, width = colW)
        textIfVisible(c, L["modifiers"], sectionX[3], labelsY[3], 17f, Ui.GOLD)
        textIfVisible(c, L["sk_mod_limit"], sectionX[3], modLimitY, 11f, Ui.DIM)
        for (b in eraButtons) ui.button(c, b, 13f)
        for (b in personalities) ui.button(c, b, 12f)
        for (b in presets) ui.button(c, b, 11f)
        for (b in powerButtons) ui.button(c, b, 13f)
        for (b in mods) ui.button(c, b, 13f)
        c.restore()

        val mid = footer.centerX()
        ui.textO(c, summary, mid, footer.top + 25 * dp,
            ui.fit(summary, 13f, footer.width() - 24 * dp), Ui.TEXT, Paint.Align.CENTER)
        ui.textO(c, rankingText, mid, footer.top + 43 * dp,
            ui.fit(rankingText, 12f, footer.width() - 24 * dp),
            if (setup.isRanked()) Ui.GOOD else Ui.GOLD, Paint.Align.CENTER)
        ui.button(c, defaults, 13f)
        ui.button(c, shuffle, 13f)
        ui.button(c, play, 17f)
    }

    override fun onTouch(e: TouchEvent): Boolean {
        if (e.action == TouchEvent.DOWN &&
            e.x >= slider.left - 9 * dp && e.x <= slider.right + 9 * dp &&
            e.y >= slider.top - 15 * dp && e.y <= slider.bottom + 15 * dp &&
            slider.top >= contentTop + 2 * dp && slider.bottom <= contentBottom - 2 * dp) {
            draggingPower = true
        }
        if (!draggingPower) return false
        if (e.action != TouchEvent.CANCEL) {
            val frac = ((e.x - slider.left) / slider.width()).coerceIn(0f, 1f)
            saveSetup(setup.copy(powerPercent = (60 + kotlin.math.round(frac * 28).toInt() * 5)))
        }
        if (e.action == TouchEvent.UP || e.action == TouchEvent.CANCEL) draggingPower = false
        return true
    }

    override fun onDrag(dx: Float, dy: Float) {
        scroll = (scroll - dy).coerceIn(0f, maxScroll)
        layout()
    }

    private fun saveSetup(changed: SkirmishSetup) {
        setup = changed.sanitized()
        g.profile.skirmishSetup = setup
        val index = SkirmishSetup.PRESET_POWER.indexOf(setup.powerPercent)
        if (index >= 0) g.profile.skirmishDiff = index
        g.profile.save()
        layout()
    }

    private fun randomize() {
        val first = Random.nextInt(Defs.ERA_COUNT)
        val last = Random.nextInt(first, Defs.ERA_COUNT)
        var mask = 0
        repeat(Random.nextInt(4)) { mask = mask or (1 shl Random.nextInt(Modifier.values().size)) }
        saveSetup(SkirmishSetup(first, last, Random.nextInt(-1, 5),
            60 + Random.nextInt(29) * 5, mask))
    }

    override fun onClick(b: Button) {
        when (b.id) {
            -1 -> g.pop()
            -2 -> g.setScreen(BattleScreen(g, setup.toConfig(System.nanoTime(), g.profile.bonuses())))
            -3 -> saveSetup(SkirmishSetup.defaults(g.profile.skirmishDiff))
            -4 -> randomize()
            10 -> saveSetup(setup.copy(startEra = (setup.startEra - 1).coerceAtLeast(0)))
            11 -> saveSetup(setup.copy(startEra = (setup.startEra + 1).coerceAtMost(setup.maxEra)))
            12 -> saveSetup(setup.copy(maxEra = (setup.maxEra - 1).coerceAtLeast(setup.startEra)))
            13 -> saveSetup(setup.copy(maxEra = (setup.maxEra + 1).coerceAtMost(Defs.ERA_COUNT - 1)))
            in 100..105 -> saveSetup(setup.copy(personality = b.id - 101))
            in 200..203 -> saveSetup(setup.copy(powerPercent = SkirmishSetup.PRESET_POWER[b.id - 200]))
            220 -> saveSetup(setup.copy(powerPercent = setup.powerPercent - 5))
            221 -> saveSetup(setup.copy(powerPercent = setup.powerPercent + 5))
            in 300 until 300 + mods.size -> {
                val bit = 1 shl (b.id - 300)
                val chosen = setup.modifierMask and bit != 0
                if (!chosen && Integer.bitCount(setup.modifierMask) >= 3) {
                    g.showNotice(L["sk_mod_limit"], Icons.LOCK)
                } else saveSetup(setup.copy(modifierMask = setup.modifierMask xor bit))
            }
        }
    }
}
