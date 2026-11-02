package com.centurywar.screens

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.centurywar.crash.CrashReports
import com.centurywar.engine.Button
import com.centurywar.engine.Game
import com.centurywar.engine.BattleMusicIntensity
import com.centurywar.engine.Icons
import com.centurywar.engine.L
import com.centurywar.engine.Screen
import com.centurywar.engine.Sound
import com.centurywar.engine.TouchEvent
import com.centurywar.engine.TooltipBubble
import com.centurywar.game.FxBuffer
import com.centurywar.game.Body
import com.centurywar.game.EraArt
import com.centurywar.game.Proj
import com.centurywar.engine.Ui
import com.centurywar.game.Battle
import com.centurywar.game.BattleController
import com.centurywar.game.BattleConfig
import com.centurywar.game.BattleRenderer
import com.centurywar.game.BattleSnapshot
import com.centurywar.game.ReplayRecord
import com.centurywar.game.RestoredBattle
import com.centurywar.game.Daily
import com.centurywar.game.Defs
import com.centurywar.game.Doctrine
import com.centurywar.game.EndlessCard
import com.centurywar.game.EndlessBosses
import com.centurywar.game.EndlessRarity
import com.centurywar.game.Mode
import com.centurywar.game.Weekly
import com.centurywar.game.Role
import com.centurywar.game.Skirmish
import com.centurywar.game.Weather
import com.centurywar.meta.Achievements
import com.centurywar.meta.AchievementBattleTracker
import com.centurywar.meta.BattleHistory
import com.centurywar.meta.Profile
import com.centurywar.meta.SkirmishSetup
import com.centurywar.meta.Missions
import kotlin.math.max
import kotlin.math.min

/** Mechaniczne wydzielenie BattleUpgradePanel; ten sam stan i kolejność operacji co w BattleScreen. */
internal class BattleUpgradePanel(private val owner: BattleScreen) {
    private var gridTurrets = false
    private var turretRowH = 0f
    private var turretHeaderH = 0f
    private var turretColW = 0f

    fun layoutUpgradePanel() {
        with(owner) {
        val pw = min(425 * dp, w - g.insetL - g.insetR - 32 * dp)
        val ph = min(268 * dp, h - g.insetT - g.insetB - 20 * dp)
        val cy = (g.insetT + h - g.insetB) / 2f
        upgradePanel.set(w / 2 - pw / 2, cy - ph / 2, w / 2 + pw / 2, cy + ph / 2)
        upgradeBuy.set(upgradePanel.left + 25 * dp, upgradePanel.bottom - 72 * dp, pw - 50 * dp, 48 * dp)
        upgradeClose.set(upgradePanel.right - 38 * dp, upgradePanel.top - 12 * dp, 48 * dp, 48 * dp)
        }
    }

    fun layoutTurretPanel() {
        with(owner) {
        val pw = min(480 * dp, w - g.insetL - g.insetR - 40 * dp)
        val narrow = pw < 350 * dp
        val slots = battle.player.slots.coerceIn(0, Defs.MAX_TURRET_SLOTS)
        turretSlotsLaid = slots
        // Zamykanie wystaje o 12 dp: górna granica panelu musi leżeć pod paskiem.
        val safeTop = topH + g.insetT + 12 * dp
        val safeBottom = h - hudH - 4 * dp
        val freeH = (safeBottom - safeTop).coerceAtLeast(0f)
        val idealRow = (if (narrow) 104 else 50) * dp
        val idealH = 72 * dp + slots * idealRow
        // Na poziomym 720p cztery wiersze nie mieszczą się w polu bitwy.
        // Dwie kolumny zachowują dostęp do wszystkich slotów i większe cele dotyku.
        gridTurrets = slots > 2 && idealH > freeH && pw >= 316 * dp
        turretHeaderH = (if (gridTurrets) 26 else 50) * dp
        val footer = (if (gridTurrets) 2 else 22) * dp
        val rows = if (gridTurrets) (slots + 1) / 2 else slots
        val wantedRow = if (gridTurrets) 64 * dp else idealRow
        turretRowH = min(wantedRow, ((freeH - turretHeaderH - footer) / max(1, rows).toFloat()).coerceAtLeast(0f))
        val ph = min(freeH, turretHeaderH + rows * turretRowH + footer)
        val cx = (g.insetL + w - g.insetR) / 2f
        val centeredY = (h - hudH - ph) / 2f + topH / 2f
        val py = centeredY.coerceIn(safeTop, safeBottom - ph)
        panel.set(cx - pw / 2f, py, cx + pw / 2f, py + ph)
        turretColW = if (gridTurrets) (pw - 12 * dp) / 2f else 0f
        for (i in 0 until Defs.MAX_TURRET_SLOTS) {
            if (i >= slots) {
                slotBuy[i].r.setEmpty(); slotTier[i].r.setEmpty(); slotSell[i].r.setEmpty()
                continue
            }
            val y: Float
            val bw: Float
            val tw: Float
            val sw: Float
            val bh: Float
            val bx: Float
            if (gridTurrets) {
                val col = i % 2
                val row = i / 2
                val cellLeft = panel.left + 4 * dp + col * (turretColW + 4 * dp)
                bw = (turretColW - 4 * dp) / 3f
                tw = bw; sw = bw
                bh = min(48 * dp, (turretRowH - 13 * dp).coerceAtLeast(0f))
                bx = cellLeft
                y = panel.top + turretHeaderH + row * turretRowH + turretRowH - bh - 1 * dp
            } else {
                bw = 64 * dp; tw = 64 * dp; sw = 54 * dp
                bh = min(48 * dp, (if (narrow) turretRowH - 50 * dp else turretRowH - 2 * dp).coerceAtLeast(0f))
                bx = panel.right - (20 + 54 + 4 + 64 + 4 + 64) * dp
                y = panel.top + turretHeaderH + i * turretRowH + turretRowH - bh - (if (narrow) 6 else 2) * dp
            }
            slotBuy[i].set(bx, y, bw, bh)
            slotTier[i].set(bx + bw + (if (gridTurrets) 2 else 4) * dp, y, tw, bh)
            slotSell[i].set(bx + bw + tw + (if (gridTurrets) 4 else 8) * dp, y, sw, bh)
            // Jeśli brakuje 1–3 dp na fizyczne 48 dp, zachowujemy cel dotyku.
            val slop = max(0f, (48 * dp - bh) / 2f)
            slotBuy[i].hitSlop = slop
            slotTier[i].hitSlop = slop
            slotSell[i].hitSlop = slop
        }
        closeBtn.set(panel.right - 38 * dp, panel.top - 12 * dp, 48 * dp, 48 * dp)
        }
    }

    fun drawUpgradePanel(c: Canvas) {
        with(owner) {
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

    fun drawTurretPanel(c: Canvas) {
        with(owner) {
        val p = battle.player
        ui.panel(c, panel)
        ui.ribbon(c, panel.centerX(), panel.top + 4 * dp, turretHeading, 16f,
            Ui.RIBBON, panel.width() * 0.5f, Ui.GOLD, panel.width() - 40 * dp)
        val narrow = panel.width() < 350 * dp
        for (i in 0 until p.slots) {
            val b = slotBuy[i]
            val te = p.turretEra[i]
            val name = if (te >= 0) L[Defs.turret(te, p.turretVariant[i]).nameKey] else L["empty"]
            ui.fill.color = if (i % 2 == 0) Color.argb(34, 255, 255, 255) else Color.argb(14, 255, 255, 255)
            if (gridTurrets) {
                val cellLeft = panel.left + 4 * dp + (i % 2) * (turretColW + 4 * dp)
                val rowTop = panel.top + turretHeaderH + (i / 2) * turretRowH
                tmpR.set(cellLeft - 2 * dp, rowTop + 1 * dp,
                    cellLeft + turretColW + 2 * dp, rowTop + turretRowH - 1 * dp)
                c.drawRoundRect(tmpR, 6 * dp, 6 * dp, ui.fill)
                val cy = rowTop + 7 * dp
                val mr = 6 * dp
                ui.medallion(c, cellLeft + mr, cy, mr,
                    if (te >= 0) Ui.ERA[te] else Color.rgb(60, 64, 78),
                    if (te >= 0) Icons.TOWER else -1, Ui.TRIM, Ui.PLAYER)
                if (te < 0) ui.textO(c, slotNumbers[i], cellLeft + mr, cy + 2 * dp,
                    8f, Ui.DIM, Paint.Align.CENTER, 1.2f)
                val tx = cellLeft + 16 * dp
                ui.textO(c, slotNumbers[i], tx, cy + 2 * dp, 9f, Ui.DIM)
                val nameX = tx + 14 * dp
                ui.textO(c, name, nameX, cy + 2 * dp,
                    ui.fit(name, 10f, cellLeft + turretColW - nameX - 3 * dp),
                    if (te >= 0) Ui.TEXT else Ui.DIM)
            } else {
                val rowTop = panel.top + turretHeaderH + i * turretRowH
                tmpR.set(panel.left + 14 * dp, b.r.top - (if (narrow) 50 else 2) * dp,
                    panel.right - 14 * dp, b.r.bottom + 2 * dp)
                c.drawRoundRect(tmpR, 8 * dp, 8 * dp, ui.fill)
                val offset = if (narrow) min(50 * dp, (turretRowH - b.r.height()).coerceAtLeast(0f)) else 0f
                val cy = b.r.centerY() - offset
                val mr = b.r.height() * 0.5f
                ui.medallion(c, panel.left + 20 * dp + mr, cy, mr,
                    if (te >= 0) Ui.ERA[te] else Color.rgb(60, 64, 78),
                    if (te >= 0) Icons.TOWER else -1, Ui.TRIM, Ui.PLAYER)
                if (te < 0) ui.textO(c, slotNumbers[i], panel.left + 20 * dp + mr,
                    cy + 5 * dp, 14f, Ui.DIM, Paint.Align.CENTER, 1.6f)
                val tx = panel.left + 28 * dp + mr * 2
                val avail = (if (narrow) panel.right - 14 * dp else b.r.left) - tx - 6 * dp
                val dense = !narrow && turretRowH < 45 * dp
                ui.textO(c, slotLabels[i], tx, cy - (if (dense) 4 else 3) * dp,
                    10f, Ui.DIM, Paint.Align.LEFT, 1.5f)
                ui.textO(c, name, tx, cy + (if (dense) 9 else 12) * dp,
                    ui.fit(name, 13f, avail), if (te >= 0) Ui.TEXT else Ui.DIM,
                    Paint.Align.LEFT, 1.8f)
                if (te >= 0 && !dense) {
                    val barWidth = 8 * dp
                    for (tier in 0..2) {
                        ui.fill.color = if (tier < p.turretTier[i]) Ui.GOLD else Ui.DIM
                        val left = tx + tier * (barWidth + 2 * dp)
                        c.drawRect(left, cy + 17 * dp, left + barWidth, cy + 20 * dp, ui.fill)
                    }
                }
            }
            ui.button(c, slotBuy[i], 11f)
            ui.button(c, slotTier[i], 11f)
            ui.button(c, slotSell[i], 11f)
        }
        ui.closeButton(c, closeBtn)
        }
    }
}
