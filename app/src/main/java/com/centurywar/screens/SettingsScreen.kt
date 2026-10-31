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
import com.centurywar.meta.OnlineState
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
    private val shakeBtn = btn(13)
    private val showFps = btn(5)
    private val snd = btn(8)
    private val music = btn(12)
    private val sfxLevel = btn(18)
    private val volumeSliders = arrayOf(music, sfxLevel)
    private val name = btn(6)
    private val reset = btn(7)
    private val notifications = btn(9)
    private val notifyHour = btn(10)
    private val playGames = btn(11)
    private val access = btn(14)
    private val hintSettings = btn(15)
    private val globalRank = btn(16)
    private val newIdentity = btn(17)
    private val localRows = listOf(quality, fps, lang, snd, sfxLevel, music, vib, shakeBtn, showFps, name, notifications, notifyHour, access, hintSettings, reset)
    private val onlineRows = listOf(quality, fps, lang, snd, sfxLevel, music, vib, shakeBtn, showFps, name, notifications, notifyHour, playGames, access, hintSettings, reset)
    private val globalRows = listOf(quality, fps, lang, snd, sfxLevel, music, vib, shakeBtn, showFps, name, notifications, notifyHour, globalRank, newIdentity, access, hintSettings, reset)
    private val bothRows = listOf(quality, fps, lang, snd, sfxLevel, music, vib, shakeBtn, showFps, name, notifications, notifyHour, playGames, globalRank, newIdentity, access, hintSettings, reset)
    /** Wiersz Play Games tylko, gdy PGS jest dostępne (prawdziwe ID w games_ids.xml). */
    private fun rows() = when {
        g.scores != null && g.online.available -> bothRows
        g.scores != null -> globalRows
        g.online.available -> onlineRows
        else -> localRows
    }
    private fun iconOf(b: Button) = when (b) {
        quality -> Icons.MONITOR; fps -> Icons.STOPWATCH; lang -> Icons.GLOBE; snd -> Icons.SPEAKER; sfxLevel -> Icons.SPEAKER; music -> Icons.NOTE
        vib -> Icons.VIBRATE; shakeBtn -> Icons.BOMB; showFps -> Icons.BOLT; name -> Icons.PENCIL; notifications -> Icons.CALENDAR
        notifyHour -> Icons.HOURGLASS; playGames -> Icons.TROPHY; access -> Icons.PERSON; hintSettings -> Icons.BOOK
        globalRank -> Icons.GLOBE; newIdentity -> Icons.RESTART; else -> Icons.RESTART
    }
    private fun labelKeyOf(b: Button) = when (b) {
        quality -> "quality"; fps -> "fps"; lang -> "language"; snd -> "sound"; sfxLevel -> "sfx_volume"; music -> "music_volume"; vib -> "vibration"; shakeBtn -> "screen_shake"
        showFps -> "show_fps"; name -> "name"; notifications -> "notifications"; notifyHour -> "notify_hour"
        access -> "accessibility"; hintSettings -> "hint_settings"
        globalRank -> "global_ranking"; newIdentity -> "global_identity"
        else -> "play_games"
    }
    private val hourLabels = Array(24) { String.format(java.util.Locale.ROOT, "%02d:00", it) }
    private var rowsPerColumn = 10
    private var columnWidth = 0f
    private var lastAvailable = false
    private val panel = RectF()
    private val rowR = RectF()
    private var resetArmed = 0f
    private var identityArmed = 0f
    private var dt = 0f
    private var scroll = 0f
    private var maxScroll = 0f
    private var contentTop = 0f
    private var contentBottom = 0f
    private var sliderPointer = -1
    private var activeSlider: Button? = null
    private var shownMusicPercent = -1
    private var musicPercentLabel = ""
    private var shownSfxPercent = -1
    private var sfxPercentLabel = ""

    override fun isDragScreen() = true

    override fun onDrag(dx: Float, dy: Float) {
        scroll = (scroll - dy).coerceIn(0f, maxScroll)
        layout()
    }

    override fun layout() {
        chrome.layoutBack(back)
        val pw = min(620 * dp, w - g.insetL - g.insetR - 32 * dp)
        val rows = rows()
        lastAvailable = g.online.available
        playGames.visible = lastAvailable
        val avail = h - chrome.top - g.insetB - 20 * dp
        // Dwie kolumny na szerokich ekranach; wąski telefon przewija pełne wiersze.
        val columns = if (pw >= 470 * dp) 2 else 1
        rowsPerColumn = (rows.size + columns - 1) / columns
        columnWidth = (pw - 24 * dp) / columns
        val step = (if (ui.textScale > 1.15f) 58f else 54f) * dp
        val ph = step * rowsPerColumn + 44 * dp
        contentTop = chrome.top + 10 * dp
        contentBottom = h - g.insetB - 10 * dp
        maxScroll = max(0f, ph - (contentBottom - contentTop))
        scroll = scroll.coerceIn(0f, maxScroll)
        val top = contentTop + max(0f, (avail - ph) * 0.35f) - scroll
        panel.set(w / 2 - pw / 2, top, w / 2 + pw / 2, top + ph)
        val bw = min(124 * dp, columnWidth * 0.42f)
        for ((i, b) in rows.withIndex()) {
            val col = i / rowsPerColumn
            val line = i % rowsPerColumn
            val left = panel.left + 12 * dp + col * columnWidth
            val y = panel.top + 24 * dp + line * step
            if (b === reset) b.set(left + 9 * dp, y + 4 * dp, columnWidth - 18 * dp, step - 10 * dp)
            else if (b === music || b === sfxLevel) {
                // Suwak potrzebuje więcej miejsca niż przycisk, inaczej na 720p ma kilka dp.
                val sw = max(bw, columnWidth * 0.52f)
                b.set(left + columnWidth - 10 * dp - sw, y + 4 * dp, sw, step - 10 * dp)
            }
            else b.set(left + columnWidth - 10 * dp - bw, y + 4 * dp, bw, step - 10 * dp)
        }
        for (b in rows) if (b !== music) b.visible = b.r.bottom > contentTop && b.r.top < contentBottom
        reset.iconId = Icons.RESTART
        access.iconId = Icons.PERSON
    }

    override fun update(dt: Float) {
        this.dt = dt
        if (resetArmed > 0f) resetArmed -= dt
        if (identityArmed > 0f) identityArmed -= dt
        val p = g.profile
        back.label = L["back"]
        quality.label = when (p.quality) { 1 -> L["q_low"]; 2 -> L["q_med"]; 3 -> L["q_high"]; else -> "${L["q_auto"]} (${g.quality.level})" }
        fps.label = when (p.fpsCap) { 30 -> "30"; 60 -> "60"; else -> L["fps_max"] }
        lang.label = L.indexOf(p.lang).let { if (it >= 0) L.NAMES[it] else "Auto" }
        snd.label = if (p.sound) L["on"] else L["off"]
        snd.color = if (p.sound) Ui.BTN_GREEN else Ui.BTN
        music.visible = false // miejsce na ręcznie rysowany suwak, nie na przycisk
        sfxLevel.visible = false
        vib.label = if (p.vibration) L["on"] else L["off"]
        vib.color = if (p.vibration) Ui.BTN_GREEN else Ui.BTN
        shakeBtn.label = L[when (p.screenShake) { 0 -> "shake_off"; 1 -> "shake_low"; else -> "shake_full" }]
        showFps.label = if (p.showFps) L["on"] else L["off"]
        showFps.color = if (p.showFps) Ui.BTN_GREEN else Ui.BTN
        name.label = p.name
        notifications.label = if (p.notifications) L["on"] else L["off"]
        notifications.color = if (p.notifications) Ui.BTN_GREEN else Ui.BTN
        notifyHour.label = hourLabels[p.notifyHour.coerceIn(0, 23)]
        // PGS loguje się asynchronicznie: gdy dostępność się zmieni, przelicz układ wierszy.
        if (g.online.available != lastAvailable) layout()
        playGames.label = L[when (g.online.state) {
            OnlineState.CONNECTED -> "pgs_connected"
            OnlineState.SYNCING -> "pgs_syncing"
            OnlineState.CHECKING -> "pgs_checking"
            OnlineState.ERROR -> "pgs_error"
            OnlineState.SIGNED_OUT -> "pgs_signed_out"
            else -> "pgs_disabled"
        }]
        playGames.color = if (g.online.state == OnlineState.CONNECTED) Ui.BTN_GREEN else Ui.BTN
        access.label = L["access_open"]
        globalRank.label = if (p.globalRanking) L["on"] else L["off"]
        globalRank.color = if (p.globalRanking) Ui.BTN_GREEN else Ui.BTN
        newIdentity.label = if (identityArmed > 0f) L["reset_confirm"] else L["global_new_id"]
        newIdentity.enabled = p.playerId.isNotEmpty()
        reset.label = if (resetArmed > 0f) L["reset_confirm"] else L["reset"]
        reset.color = if (resetArmed > 0f) Color.rgb(200, 60, 40) else Ui.BTN_RED
    }

    override fun render(c: Canvas) {
        bg.draw(c, w, h, dt)
        chrome.dim(c, 120)
        c.save()
        c.clipRect(g.insetL, contentTop, w - g.insetR, contentBottom)
        ui.panel(c, panel)
        for ((i, b) in rows().withIndex()) {
            if (b !== reset && b.r.bottom > contentTop && b.r.top < contentBottom) {
                val col = i / rowsPerColumn
                val left = panel.left + 12 * dp + col * columnWidth
                rowR.set(left + 2 * dp, b.r.top - 3 * dp, left + columnWidth - 2 * dp, b.r.bottom + 3 * dp)
                ui.fill.color = if (i % 2 == 0) Color.argb(36, 255, 255, 255) else Color.argb(14, 255, 255, 255)
                c.drawRoundRect(rowR, 10 * dp, 10 * dp, ui.fill)
                val isz = min(24 * dp, b.r.height() * 0.7f)
                Icons.draw(c, iconOf(b), left + 10 * dp + isz / 2, b.r.centerY(), isz, dp = dp)
                val tx = left + 20 * dp + isz
                val label = L[labelKeyOf(b)]
                ui.textO(c, label, tx, b.r.centerY() + 5.5f * dp,
                    ui.fit(label, 15f, b.r.left - tx - 6 * dp), Ui.TEXT)
            }
            if (b === music || b === sfxLevel) {
                if (b.r.bottom > contentTop && b.r.top < contentBottom) drawVolumeSlider(c, b)
            } else ui.button(c, b, 15f)
        }
        c.restore()
        chrome.title(c, L["settings"])
        ui.button(c, back, 16f)
    }


    private fun drawVolumeSlider(c: Canvas, b: Button) {
        val left = b.r.left + 14 * dp
        val right = b.r.right - 55 * dp
        val y = b.r.centerY()
        val fraction = if (b === music) g.profile.musicVolume else g.profile.sfxVolume
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
        val text = if (b === music) {
            if (percent != shownMusicPercent) {
                shownMusicPercent = percent
                musicPercentLabel = percent.toString() + "%"
            }
            musicPercentLabel
        } else {
            if (percent != shownSfxPercent) {
                shownSfxPercent = percent
                sfxPercentLabel = percent.toString() + "%"
            }
            sfxPercentLabel
        }
        ui.textO(c, text, b.r.right - 5 * dp, y + 5 * dp, 13f, Ui.TEXT, Paint.Align.RIGHT)
    }

    private fun setVolumeFromX(b: Button, x: Float) {
        val left = b.r.left + 14 * dp
        val right = b.r.right - 55 * dp
        val portion = ((x - left) / (right - left).coerceAtLeast(1f)).coerceIn(0f, 1f)
        val value = (portion * 100f + 0.5f).toInt() / 100f
        if (b === music) {
            g.profile.musicVolume = value
            g.applyMusicVolume()
        } else {
            g.profile.sfxVolume = value
            g.applySfxVolume()
        }
    }

    override fun onTouch(e: TouchEvent): Boolean {
        if (sliderPointer >= 0) {
            if (e.action == TouchEvent.CANCEL) {
                sliderPointer = -1
                activeSlider = null
                g.profile.save()
                return true
            }
            if (e.pointer != sliderPointer) return true
            val b = activeSlider ?: return true
            when (e.action) {
                TouchEvent.MOVE -> setVolumeFromX(b, e.x)
                TouchEvent.UP -> {
                    setVolumeFromX(b, e.x)
                    sliderPointer = -1
                    activeSlider = null
                    g.profile.save()
                }
            }
            return true
        }
        // Strefa dotyku suwaka obejmuje cały pas wiersza w pionie (niski wiersz na 720p).
        if (e.action == TouchEvent.DOWN) {
            for (b in volumeSliders) {
                if (e.x >= b.r.left && e.x <= b.r.right &&
                    e.y >= max(contentTop, b.r.top - 5 * dp) && e.y <= min(contentBottom, b.r.bottom + 5 * dp)) {
                    sliderPointer = e.pointer
                    activeSlider = b
                    setVolumeFromX(b, e.x)
                    return true
                }
            }
        }
        return false
    }

    override fun onClick(b: Button) {
        val p = g.profile
        when (b.id) {
            -1 -> { p.save(); g.pop() }
            1 -> { p.quality = (p.quality + 1) % 4; g.setQuality(g.effectiveQualityLevel()) }
            2 -> { p.fpsCap = when (p.fpsCap) { 0 -> 60; 60 -> 30; else -> 0 }; g.applyFrameRate() }
            3 -> { val i = L.indexOf(p.lang); p.lang = if (p.lang.isEmpty()) L.CODES[0] else if (i in 0 until L.CODES.size - 1) L.CODES[i + 1] else ""; g.onLanguageChanged() }
            4 -> p.vibration = !p.vibration
            13 -> p.screenShake = (p.screenShake + 1) % 3
            8 -> { p.sound = !p.sound; g.applySfxVolume() }
            5 -> p.showFps = !p.showFps
            6 -> g.host.requestText(L["name"], p.name) { n -> p.name = n; p.save() }
            7 -> if (resetArmed > 0f) { p.reset(); g.history?.clear(); resetArmed = 0f } else resetArmed = 3f
            9 -> {
                p.notifications = !p.notifications
                p.save()
                g.host.onNotificationSettingsChanged()
            }
            10 -> g.host.requestNotifyHour(p.notifyHour) { hour ->
                p.notifyHour = hour.coerceIn(0, 23)
                p.save()
                g.host.onNotificationSettingsChanged()
            }
            11 -> { g.push(PlayGamesScreen(g)); return }
            14 -> { g.push(AccessibilityScreen(g)); return }
            15 -> { g.push(HintSettingsScreen(g)); return }
            16 -> {
                if (p.globalRanking) {
                    p.disableGlobalRanking()
                    g.scores?.setEnabled(false)
                } else {
                    g.host.requestGlobalRankingConsent {
                        p.enableGlobalRanking()
                        g.scores?.setEnabled(true)
                    }
                }
                return
            }
            17 -> {
                if (p.playerId.isNotEmpty()) {
                    if (identityArmed > 0f) {
                        g.scores?.clearQueue()
                        p.renewRankingId()
                        identityArmed = 0f
                    } else identityArmed = 3f
                }
                return
            }
        }
        p.save()
    }
}
