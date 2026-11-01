package com.centurywar.screens

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.centurywar.engine.Button
import com.centurywar.engine.Game
import com.centurywar.engine.Icons
import com.centurywar.engine.L
import com.centurywar.engine.Palette
import com.centurywar.engine.Screen
import com.centurywar.engine.Ui
import com.centurywar.game.Battle
import com.centurywar.game.BattleConfig
import com.centurywar.game.BattleRenderer
import com.centurywar.game.Defs
import com.centurywar.game.Mode
import com.centurywar.game.Role
import kotlin.math.max
import kotlin.math.min

/** Wszystkie opcje dostępności w jednej przewijanej karcie. */
class AccessibilityScreen(g: Game) : Screen(g) {
    override val crashName get() = "AccessibilityScreen"
    private val bg = MenuBg()
    private val chrome = Chrome(this)
    private val back = btn(-1)
    private val palettes = Array(Palette.COUNT) { btn(10 + it) }
    private val sizes = arrayOf(btn(30), btn(31), btn(32))
    private val markers = btn(40)
    private val shake = btn(41)
    private val flash = btn(42)
    private val leftHud = btn(43)
    private val renderer = BattleRenderer(g, Battle(BattleConfig(Mode.SKIRMISH, 7L, 1f)))
    private val demoUnit = Defs.unit(0, Role.MELEE)

    private val panel = RectF()
    private val preview = RectF()
    private val goodState = RectF()
    private val badState = RectF()
    private var scroll = 0f
    private var maxScroll = 0f
    private var contentTop = 0f
    private var contentBottom = 0f
    private var dt = 0f

    override fun isDragScreen() = true

    override fun layout() {
        chrome.layoutBack(back)
        val pw = min(720f * dp, w - g.insetL - g.insetR - 32f * dp)
        val left = (w + g.insetL - g.insetR - pw) / 2f
        contentTop = chrome.top + 8f * dp
        contentBottom = h - g.insetB - 10f * dp
        val cardH = 779f * dp
        maxScroll = max(0f, cardH - (contentBottom - contentTop))
        scroll = scroll.coerceIn(0f, maxScroll)
        panel.set(left, contentTop - scroll, left + pw, contentTop - scroll + cardH)
        val bw = (pw - 52f * dp) / 2f
        for (i in palettes.indices) {
            val col = i % 2
            val row = i / 2
            palettes[i].set(left + 20f * dp + col * (bw + 12f * dp),
                panel.top + (63f + row * 53f) * dp, bw, 45f * dp)
        }
        preview.set(panel.left + 18f * dp, panel.top + 186f * dp, panel.right - 18f * dp, panel.top + 340f * dp)
        goodState.set(preview.left + 8f * dp, panel.top + 352f * dp, preview.centerX() - 5f * dp, panel.top + 389f * dp)
        badState.set(preview.centerX() + 5f * dp, panel.top + 352f * dp, preview.right - 8f * dp, panel.top + 389f * dp)
        val sw = (pw - 56f * dp) / 3f
        for (i in sizes.indices) sizes[i].set(
            panel.left + 20f * dp + i * (sw + 8f * dp), panel.top + 444f * dp, sw, 44f * dp)
        val toggleW = min(132f * dp, pw * 0.31f)
        markers.set(panel.right - 20f * dp - toggleW, panel.top + 533f * dp, toggleW, 42f * dp)
        shake.set(panel.right - 20f * dp - toggleW, panel.top + 594f * dp, toggleW, 42f * dp)
        flash.set(panel.right - 20f * dp - toggleW, panel.top + 655f * dp, toggleW, 42f * dp)
        leftHud.set(panel.right - 20f * dp - toggleW, panel.top + 716f * dp, toggleW, 42f * dp)
        for (b in buttons) if (b !== back) {
            b.visible = b.r.top >= contentTop && b.r.bottom <= contentBottom
        }
    }

    override fun onDrag(dx: Float, dy: Float) {
        scroll = (scroll - dy).coerceIn(0f, maxScroll)
        layout()
    }

    override fun update(dt: Float) {
        this.dt = dt
        back.label = L["back"]
        for (i in palettes.indices) {
            palettes[i].label = L["access_palette_$i"]
            palettes[i].selected = g.profile.palette == i
            palettes[i].color = if (palettes[i].selected) Ui.BTN_GREEN else Ui.BTN
        }
        for (i in sizes.indices) {
            sizes[i].label = when (i) { 0 -> "100%"; 1 -> "115%"; else -> "130%" }
            sizes[i].selected = g.profile.textScale == when (i) { 0 -> 100; 1 -> 115; else -> 130 }
        }
        markers.label = L[if (g.profile.teamMarkers) "on" else "off"]
        shake.label = L[if (g.profile.reduceShake) "on" else "off"]
        flash.label = L[if (g.profile.reduceFlash) "on" else "off"]
        leftHud.label = L[if (g.profile.leftHandedHud) "on" else "off"]
        markers.color = if (g.profile.teamMarkers) Ui.BTN_GREEN else Ui.BTN
        shake.color = if (g.profile.reduceShake) Ui.BTN_GREEN else Ui.BTN
        flash.color = if (g.profile.reduceFlash) Ui.BTN_GREEN else Ui.BTN
        leftHud.color = if (g.profile.leftHandedHud) Ui.BTN_GREEN else Ui.BTN
    }

    override fun render(c: Canvas) {
        bg.draw(c, w, h, dt)
        chrome.dim(c, 120)
        c.save()
        c.clipRect(g.insetL, contentTop, w - g.insetR, contentBottom)
        ui.panel(c, panel)
        val x = panel.left + 20f * dp
        val maxLabelW = panel.width() - 40f * dp
        heading(c, L["access_palette"], x, panel.top + 44f * dp, maxLabelW)
        for (b in palettes) if (b.visible) ui.button(c, b, 14f)

        heading(c, L["access_preview"], x, panel.top + 177f * dp, maxLabelW)
        ui.card(c, preview, Ui.PANEL, 10f, Ui.FRAME)
        val pX = preview.left + preview.width() * 0.26f
        val eX = preview.left + preview.width() * 0.74f
        val bottom = preview.bottom - 26f * dp
        renderer.drawUnitIcon(c, demoUnit, pX, bottom, 67f * dp, Ui.PLAYER)
        renderer.drawUnitIcon(c, demoUnit, eX, bottom, 67f * dp, Ui.ENEMY)
        renderer.drawTeamMarker(c, pX, bottom - 82f * dp, 0, 15f * dp)
        renderer.drawTeamMarker(c, eX, bottom - 82f * dp, 1, 15f * dp)
        val barW = min(108f * dp, preview.width() * 0.3f)
        ui.bar(c, pX - barW / 2f, preview.bottom - 18f * dp, barW, 6f * dp, 0.72f, Ui.PLAYER)
        ui.bar(c, eX - barW / 2f, preview.bottom - 18f * dp, barW, 6f * dp, 0.45f, Ui.ENEMY)
        if (g.profile.teamMarkers) {
            val until = eX - barW / 2f + 0.45f * barW
            var xx = eX - barW / 2f + 5f * dp
            while (xx < until) {
                c.drawLine(xx, preview.bottom - 18f * dp, xx, preview.bottom - 12f * dp,
                    ui.stroke.apply { color = Ui.OUTLINE; strokeWidth = 1.4f * dp })
                xx += 10f * dp
            }
        }
        ui.card(c, goodState, Ui.PANEL, 8f, Ui.GOOD)
        ui.card(c, badState, Ui.PANEL, 8f, Ui.BAD)
        Icons.draw(c, Icons.CHECK, goodState.left + 18f * dp, goodState.centerY(), 21f * dp, Ui.GOOD, dp = dp)
        Icons.draw(c, Icons.SKULL, badState.left + 18f * dp, badState.centerY(), 21f * dp, Ui.BAD, dp = dp)
        val good = L["access_good"]
        val bad = L["access_bad"]
        ui.textO(c, good, goodState.left + 34f * dp, goodState.centerY() + 5f * dp,
            ui.fit(good, 13f, goodState.width() - 44f * dp), Ui.GOOD)
        ui.textO(c, bad, badState.left + 34f * dp, badState.centerY() + 5f * dp,
            ui.fit(bad, 13f, badState.width() - 44f * dp), Ui.BAD)

        heading(c, L["access_text"], x, panel.top + 432f * dp, maxLabelW)
        for (b in sizes) if (b.visible) ui.button(c, b, 15f)
        val sample = L["access_sample"]
        ui.text(c, sample, panel.centerX(), panel.top + 511f * dp,
            ui.fit(sample, 16f, maxLabelW), Ui.TEXT, Paint.Align.CENTER)

        rowLabel(c, L["access_team_markers"], markers)
        rowLabel(c, L["access_reduce_shake"], shake)
        rowLabel(c, L["access_reduce_flash"], flash)
        rowLabel(c, L["access_left_handed_hud"], leftHud)
        if (markers.visible) ui.button(c, markers, 14f)
        if (shake.visible) ui.button(c, shake, 14f)
        if (flash.visible) ui.button(c, flash, 14f)
        if (leftHud.visible) ui.button(c, leftHud, 14f)
        c.restore()
        chrome.title(c, L["accessibility"])
        ui.button(c, back, 16f)
    }

    private fun heading(c: Canvas, s: String, x: Float, y: Float, maxW: Float) {
        ui.textO(c, s, x, y, ui.fit(s, 18f, maxW), Ui.GOLD)
    }

    private fun rowLabel(c: Canvas, s: String, b: Button) {
        val x = panel.left + 20f * dp
        ui.textO(c, s, x, b.r.centerY() + 5f * dp,
            ui.fit(s, 15f, b.r.left - x - 10f * dp), Ui.TEXT)
    }

    override fun onClick(b: Button) {
        when (b.id) {
            -1 -> { g.profile.save(); g.pop(); return }
            in 10..13 -> g.profile.palette = b.id - 10
            30 -> g.profile.textScale = 100
            31 -> g.profile.textScale = 115
            32 -> g.profile.textScale = 130
            40 -> g.profile.teamMarkers = !g.profile.teamMarkers
            41 -> g.profile.reduceShake = !g.profile.reduceShake
            42 -> g.profile.reduceFlash = !g.profile.reduceFlash
            43 -> g.profile.leftHandedHud = !g.profile.leftHandedHud
        }
        g.applyAccessibility()
        g.profile.save()
    }
}
