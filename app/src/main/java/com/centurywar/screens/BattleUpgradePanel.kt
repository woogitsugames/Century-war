package com.centurywar.screens

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.centurywar.engine.Icons
import com.centurywar.engine.L
import com.centurywar.engine.Ui
import com.centurywar.game.Defs
import kotlin.math.min

internal class BattleUpgradePanel(private val screen: BattleScreen) {
    fun layoutUpgrade() {
        with(screen) {
        val pw = min(425 * dp, w - g.insetL - g.insetR - 32 * dp)
        val ph = min(260 * dp, h - g.insetT - g.insetB - 20 * dp)
        val cy = (g.insetT + h - g.insetB) / 2f
        upgradePanel.set(w / 2 - pw / 2, cy - ph / 2, w / 2 + pw / 2, cy + ph / 2)
        upgradeBuy.set(upgradePanel.left + 25 * dp, upgradePanel.bottom - 62 * dp, pw - 50 * dp, 48 * dp)
        upgradeClose.set(upgradePanel.right - 38 * dp, upgradePanel.top - 12 * dp, 48 * dp, 48 * dp)
    
        }
    }

    fun layoutTurrets() {
        with(screen) {
        val pw = min(480 * dp, w - 40 * dp)
        val rowH = 50 * dp
        turretSlotsLaid = battle.player.slots
        val ph = 62 * dp + rowH * turretSlotsLaid + 10 * dp
        panel.set(w / 2 - pw / 2, (h - hudH - ph) / 2 + topH / 2, w / 2 + pw / 2, (h - hudH - ph) / 2 + topH / 2 + ph)
        val bw = min(140 * dp, pw * 0.3f)
        val sw = min(116 * dp, pw * 0.25f)
        for (i in 0 until Defs.MAX_TURRET_SLOTS) {
            val y = panel.top + 50 * dp + i * rowH
            slotSell[i].set(panel.right - 20 * dp - sw, y, sw, 48 * dp)
            slotBuy[i].set(slotSell[i].r.left - 8 * dp - bw, y, bw, 48 * dp)
        }
        closeBtn.set(panel.right - 38 * dp, panel.top - 12 * dp, 48 * dp, 48 * dp)
    
        }
    }


    fun drawUpgrade(c: Canvas) {
        with(screen) {
        val idx = showUpgrade
        if (idx !in 0..3) return
        val role = roles[idx]
        val current = battle.player.unitTier[idx]
        val next = min(Defs.MAX_UNIT_TIER, current + 1)
        val def = battle.player.unit(role)
        val pr = upgradePanel
        c.drawColor(Color.argb(158, 0, 0, 0))
        ui.panel(c, pr)
        ui.ribbon(c, pr.centerX(), pr.top + 4 * dp, L["unit_upgrade_title"], 18f,
            Ui.RIBBON, pr.width() * 0.62f, Ui.GOLD, pr.width() - 30 * dp)
        val name = L[def.tierNameKeys[next - 1]]
        ui.textO(c, name, pr.centerX(), pr.top + 66 * dp,
            ui.fit(name, 17f, pr.width() - 36 * dp), Ui.TEXT, Paint.Align.CENTER)
        ui.textO(c, upgradeTierText, pr.centerX(), pr.top + 88 * dp, 13f, Ui.GOLD, Paint.Align.CENTER)
        r.drawUnitIcon(c, def, pr.left + 64 * dp, pr.top + 172 * dp, 80 * dp, Ui.PLAYER, next)
        val tx = pr.left + 122 * dp
        val tw = pr.right - tx - 12 * dp
        val stat = L["unit_upgrade_stats"]
        val trait = L[upgradeRoleKeys[idx]]
        ui.textO(c, stat, tx, pr.top + 116 * dp, ui.fit(stat, 12f, tw), Ui.TEXT)
        ui.textO(c, trait, tx, pr.top + 140 * dp, ui.fit(trait, 12f, tw), Ui.GOLD)
        val note = L["unit_upgrade_future"]
        ui.textO(c, note, tx, pr.top + 162 * dp, ui.fit(note, 11f, tw), Ui.DIM)
        val reset = L["unit_upgrade_reset"]
        ui.textO(c, reset, pr.centerX(), upgradeBuy.r.top - 11 * dp, ui.fit(reset, 10f, pr.width() - 25 * dp),
            Ui.DIM, Paint.Align.CENTER)
        ui.button(c, upgradeBuy, 15f)
        ui.closeButton(c, upgradeClose)
    
        }
    }

    fun drawTurrets(c: Canvas) {
        with(screen) {
        val p = battle.player
        ui.panel(c, panel)
        ui.ribbon(c, panel.centerX(), panel.top + 4 * dp, turretHeading, 16f,
            Ui.RIBBON, panel.width() * 0.5f, Ui.GOLD, panel.width() - 40 * dp)
        for (i in 0 until p.slots) {
            val b = slotBuy[i]
            val te = p.turretEra[i]
            tmpR.set(panel.left + 14 * dp, b.r.top - 2 * dp, panel.right - 14 * dp, b.r.bottom + 2 * dp)
            ui.fill.color = if (i % 2 == 0) Color.argb(34, 255, 255, 255) else Color.argb(14, 255, 255, 255)
            c.drawRoundRect(tmpR, 8 * dp, 8 * dp, ui.fill)
            val cy = b.r.centerY()
            val mr = b.r.height() * 0.5f
            ui.medallion(c, panel.left + 20 * dp + mr, cy, mr, if (te >= 0) Ui.ERA[te] else Color.rgb(60, 64, 78), if (te >= 0) Icons.TOWER else -1, Ui.TRIM, Ui.PLAYER)
            if (te < 0) ui.textO(c, slotNumbers[i], panel.left + 20 * dp + mr, cy + 5 * dp, 14f, Ui.DIM, Paint.Align.CENTER, 1.6f)
            val tx = panel.left + 28 * dp + mr * 2
            val name = if (te >= 0) L[Defs.turret(te, p.turretVariant[i]).nameKey] else L["empty"]
            val avail = b.r.left - tx - 6 * dp
            ui.textO(c, slotLabels[i], tx, cy - 3 * dp, 10f, Ui.DIM, Paint.Align.LEFT, 1.5f)
            ui.textO(c, name, tx, cy + 12 * dp, ui.fit(name, 13f, avail), if (te >= 0) Ui.TEXT else Ui.DIM, Paint.Align.LEFT, 1.8f)
            ui.button(c, slotBuy[i], 13f)
            ui.button(c, slotSell[i], 13f)
        }
        ui.closeButton(c, closeBtn)
    
        }
    }
}
