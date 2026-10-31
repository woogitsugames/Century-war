package com.centurywar.screens

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.centurywar.engine.Button
import com.centurywar.engine.Game
import com.centurywar.engine.Icons
import com.centurywar.engine.L
import com.centurywar.engine.Screen
import com.centurywar.engine.Ui
import com.centurywar.meta.OnlineState
import kotlin.math.min

/** Logowanie i stan chmurowego profilu; żadne operacje sieciowe nie dotykają pętli gry. */
class PlayGamesScreen(g: Game) : Screen(g) {
    private val bg = MenuBg()
    private val chrome = Chrome(this)
    private val back = btn(-1)
    private val action = btn(1)
    private val panel = RectF()
    private var dt = 0f

    override fun layout() {
        chrome.layoutBack(back)
        val pw = min(620 * dp, w - g.insetL - g.insetR - 32 * dp)
        val ph = min(290 * dp, h - chrome.top - g.insetB - 24 * dp)
        val top = chrome.top + (h - chrome.top - g.insetB - ph) * 0.5f
        panel.set(w / 2 - pw / 2, top, w / 2 + pw / 2, top + ph)
        val bw = min(260 * dp, pw - 36 * dp)
        action.set(panel.centerX() - bw / 2, panel.bottom - 64 * dp, bw, 43 * dp)
        action.iconId = Icons.GLOBE
    }

    override fun update(dt: Float) {
        this.dt = dt
        back.label = L["back"]
        action.label = if (g.online.state == OnlineState.CONNECTED || g.online.state == OnlineState.SYNCING)
            L["pgs_sync"] else L["pgs_sign_in"]
        action.enabled = g.online.state != OnlineState.CHECKING && g.online.state != OnlineState.SYNCING
        action.color = if (g.online.state == OnlineState.CONNECTED) Ui.BTN_GREEN else Ui.BTN
    }

    override fun render(c: Canvas) {
        bg.draw(c, w, h, dt)
        chrome.dim(c, 120)
        ui.panel(c, panel)
        chrome.title(c, L["play_games"])
        Icons.draw(c, Icons.GLOBE, panel.centerX(), panel.top + 46 * dp, 50 * dp, dp = dp)
        ui.textO(c, L["pgs_account_status"], panel.centerX(), panel.top + 94 * dp, 15f, Ui.TEXT, Paint.Align.CENTER)
        val key = when (g.online.state) {
            OnlineState.CONNECTED -> "pgs_connected"
            OnlineState.SYNCING -> "pgs_syncing"
            OnlineState.CHECKING -> "pgs_checking"
            OnlineState.SIGNED_OUT -> "pgs_signed_out"
            OnlineState.ERROR -> "pgs_error"
            else -> "pgs_disabled"
        }
        ui.textO(c, L[key], panel.centerX(), panel.top + 124 * dp, 20f,
            if (g.online.state == OnlineState.CONNECTED) Ui.GOOD else Ui.GOLD, Paint.Align.CENTER)
        ui.textO(c, L["pgs_info"], panel.centerX(), panel.top + 158 * dp,
            ui.fit(L["pgs_info"], 13f, panel.width() - 36 * dp), Ui.DIM, Paint.Align.CENTER)
        ui.button(c, action, 15f)
        ui.button(c, back, 16f)
    }

    override fun onClick(b: Button) {
        when (b.id) {
            -1 -> g.pop()
            1 -> if (g.online.state == OnlineState.CONNECTED) g.online.syncNow() else g.online.signIn()
        }
    }
}
