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
import kotlin.math.max
import kotlin.math.min

class SettingsScreen(g: Game) : Screen(g) {
    private val bg = MenuBg()
    private val chrome = Chrome(this)
    private val back = btn(-1)
    private val quality = btn(1)
    private val fps = btn(2)
    private val lang = btn(3)
    private val vib = btn(4)
    private val showFps = btn(5)
    private val snd = btn(8)
    private val music = btn(9)
    private val name = btn(6)
    private val reset = btn(7)
    private val rows = listOf(quality, fps, lang, snd, music, vib, showFps, name, reset)
    private val icons = intArrayOf(Icons.MONITOR, Icons.STOPWATCH, Icons.GLOBE, Icons.SPEAKER, Icons.SPEAKER, Icons.VIBRATE, Icons.BOLT, Icons.PENCIL, Icons.RESTART)
    private val labelKeys = arrayOf("quality", "fps", "language", "sound", "music_volume", "vibration", "show_fps", "name")
    private val panel = RectF()
    private val rowR = RectF()
    private var resetArmed = 0f
    private var dt = 0f
    private var musicPointer = -1
    private var shownMusicPercent = -1
    private var musicPercentLabel = ""

    override fun layout() {
        chrome.layoutBack(back)
        val pw = min(620 * dp, w - g.insetL - g.insetR - 32 * dp)
        val avail = h - chrome.top - g.insetB - 20 * dp
        val step = min(54 * dp, (avail - 44 * dp) / rows.size)
        val ph = step * rows.size + 44 * dp
        val top = chrome.top + 10 * dp + max(0f, (avail - ph) * 0.35f)
        panel.set(w / 2 - pw / 2, top, w / 2 + pw / 2, top + ph)
        val bw = min(250 * dp, pw * 0.46f)
        for ((i, b) in rows.withIndex()) {
            val y = panel.top + 24 * dp + i * step
            if (b === reset) b.set(panel.left + 24 * dp, y + 4 * dp, pw - 48 * dp, step - 10 * dp)
            else b.set(panel.right - 22 * dp - bw, y + 4 * dp, bw, step - 10 * dp)
        }
        reset.iconId = Icons.RESTART
    }

    override fun update(dt: Float) {
        this.dt = dt
        if (resetArmed > 0f) resetArmed -= dt
        val p = g.profile
        back.label = L["back"]
        quality.label = when (p.quality) { 1 -> L["q_low"]; 2 -> L["q_med"]; 3 -> L["q_high"]; else -> "${L["q_auto"]} (${g.quality.level})" }
        fps.label = when (p.fpsCap) { 30 -> "30"; 60 -> "60"; else -> L["fps_max"] }
        lang.label = L.indexOf(p.lang).let { if (it >= 0) L.NAMES[it] else "Auto" }
        snd.label = if (p.sound) L["on"] else L["off"]
        snd.color = if (p.sound) Ui.BTN_GREEN else Ui.BTN
        music.visible = false // miejsce na ręcznie rysowany suwak, nie na przycisk
        vib.label = if (p.vibration) L["on"] else L["off"]
        vib.color = if (p.vibration) Ui.BTN_GREEN else Ui.BTN
        showFps.label = if (p.showFps) L["on"] else L["off"]
        showFps.color = if (p.showFps) Ui.BTN_GREEN else Ui.BTN
        name.label = p.name
        reset.label = if (resetArmed > 0f) L["reset_confirm"] else L["reset"]
        reset.color = if (resetArmed > 0f) Color.rgb(200, 60, 40) else Ui.BTN_RED
    }

    override fun render(c: Canvas) {
        bg.draw(c, w, h, dt)
        chrome.dim(c, 120)
        ui.panel(c, panel)
        chrome.title(c, L["settings"])
        for ((i, b) in rows.withIndex()) {
            if (b !== reset) {
                rowR.set(panel.left + 16 * dp, b.r.top - 3 * dp, panel.right - 16 * dp, b.r.bottom + 3 * dp)
                ui.fill.color = if (i % 2 == 0) Color.argb(36, 255, 255, 255) else Color.argb(14, 255, 255, 255)
                c.drawRoundRect(rowR, 10 * dp, 10 * dp, ui.fill)
                val isz = min(26 * dp, b.r.height() * 0.7f)
                Icons.draw(c, icons[i], panel.left + 30 * dp + isz / 2, b.r.centerY(), isz, dp = dp)
                val tx = panel.left + 42 * dp + isz
                val label = L[labelKeys[i]]
                ui.textO(c, label, tx, b.r.centerY() + 5.5f * dp, ui.fit(label, 16f, b.r.left - tx - 8 * dp), Ui.TEXT)
            }
            if (b === music) drawMusicSlider(c) else ui.button(c, b, 15f)
        }
        ui.button(c, back, 16f)
    }


    private fun drawMusicSlider(c: Canvas) {
        val left = music.r.left + 14 * dp
        val right = music.r.right - 55 * dp
        val y = music.r.centerY()
        val fraction = g.profile.musicVolume
        ui.fill.color = Ui.OUTLINE
        c.drawRoundRect(left, y - 6 * dp, right, y + 6 * dp, 6 * dp, 6 * dp, ui.fill)
        ui.fill.color = Ui.DIM
        c.drawRoundRect(left, y - 3 * dp, right, y + 3 * dp, 3 * dp, 3 * dp, ui.fill)
        ui.fill.color = Ui.GOLD
        val x = left + (right - left) * fraction
        c.drawRoundRect(left, y - 3 * dp, x, y + 3 * dp, 3 * dp, 3 * dp, ui.fill)
        ui.fill.color = Ui.TEXT
        c.drawCircle(x, y, 8 * dp, ui.fill)
        val percent = (fraction * 100f + 0.5f).toInt()
        if (percent != shownMusicPercent) {
            shownMusicPercent = percent
            musicPercentLabel = "$" + "{percent}%"
        }
        ui.textO(c, musicPercentLabel, music.r.right - 5 * dp, y + 5 * dp, 13f,
            Ui.TEXT, Paint.Align.RIGHT)
    }

    private fun setMusicFromX(x: Float) {
        val left = music.r.left + 14 * dp
        val right = music.r.right - 55 * dp
        val portion = ((x - left) / (right - left).coerceAtLeast(1f)).coerceIn(0f, 1f)
        g.profile.musicVolume = (portion * 100f + 0.5f).toInt() / 100f
        g.applyMusicVolume()
    }

    override fun onTouch(e: TouchEvent): Boolean {
        if (musicPointer >= 0) {
            if (e.action == TouchEvent.CANCEL) {
                musicPointer = -1
                g.profile.save()
                return true
            }
            if (e.pointer != musicPointer) return true
            when (e.action) {
                TouchEvent.MOVE -> setMusicFromX(e.x)
                TouchEvent.UP -> {
                    setMusicFromX(e.x)
                    musicPointer = -1
                    g.profile.save()
                }
            }
            return true
        }
        if (e.action == TouchEvent.DOWN && music.r.contains(e.x, e.y)) {
            musicPointer = e.pointer
            setMusicFromX(e.x)
            return true
        }
        return false
    }

    override fun onClick(b: Button) {
        val p = g.profile
        when (b.id) {
            -1 -> { p.save(); g.pop() }
            1 -> { p.quality = (p.quality + 1) % 4; g.setQuality(g.effectiveQualityLevel()) }
            2 -> { p.fpsCap = when (p.fpsCap) { 0 -> 60; 60 -> 30; else -> 0 }; g.applyFrameRate() }
            3 -> { val i = L.indexOf(p.lang); p.lang = if (p.lang.isEmpty()) L.CODES[0] else if (i in 0 until L.CODES.size - 1) L.CODES[i + 1] else ""; g.applyLanguage() }
            4 -> p.vibration = !p.vibration
            8 -> p.sound = !p.sound
            5 -> p.showFps = !p.showFps
            6 -> g.host.requestText(L["name"], p.name) { n -> p.name = n; p.save() }
            7 -> if (resetArmed > 0f) { p.reset(); resetArmed = 0f } else resetArmed = 3f
        }
        p.save()
    }
}
