package com.centurywar.screens

import android.graphics.Canvas
import android.graphics.Color
import com.centurywar.engine.Ui
import com.centurywar.game.Defs
import kotlin.math.min

internal class BattleBottomHud(private val screen: BattleScreen) {
    fun drawBack(c: Canvas) {
        with(screen) {
        val top = h - hudH
        ui.fill.color = Color.argb(190, 16, 20, 32)
        c.drawRect(0f, top, w, h, ui.fill)
        ui.fill.color = Ui.OUTLINE
        c.drawRect(0f, top - 2.5f * dp, w, top, ui.fill)
        ui.fill.color = Color.argb(160, 222, 170, 70)
        c.drawRect(0f, top, w, top + 1.5f * dp, ui.fill)
        // separator między jednostkami a akcjami
        val sx = (unitBtn[3].r.right + turretBtn.r.left) / 2
        ui.fill.color = Color.argb(90, 255, 255, 255)
        c.drawRect(sx - 1 * dp, unitBtn[0].r.top + 6 * dp, sx + 1 * dp, unitBtn[0].r.bottom - 6 * dp, ui.fill)
    
        }
    }

    /** Trzy belki na przycisku; złote oznaczają już kupione stopnie. */
    fun drawUnitTiers(c: Canvas) {
        with(screen) {
        val p = battle.player
        for (i in 0..3) {
            val b = unitBtn[i]
            if (!b.visible) continue
            val bw = min(7 * dp, (b.r.width() - 15 * dp) / 3f)
            for (t in 0 until Defs.MAX_UNIT_TIER) {
                val x = b.r.left + 4 * dp + t * (bw + 2 * dp)
                ui.fill.color = if (t < p.unitTier[i]) Ui.GOLD else Ui.FRAME
                c.drawRoundRect(x, b.r.top + 4 * dp, x + bw, b.r.top + 7 * dp, 1.5f * dp, 1.5f * dp, ui.fill)
            }
        }
    
        }
    }

    fun drawQueue(c: Canvas) {
        with(screen) {
        val p = battle.player
        if (p.queueSize == 0 || !unitBtn[0].visible) return
        val y = unitBtn[0].r.top - 10 * dp
        for (i in 0 until p.queueSize) {
            val x = unitBtn[0].r.left + 9 * dp + i * 16 * dp
            ui.fill.color = Ui.OUTLINE
            c.drawCircle(x, y, 6.5f * dp, ui.fill)
            ui.fill.color = if (i == 0) Ui.GOLD else Color.rgb(220, 224, 232)
            c.drawCircle(x, y, 4.5f * dp, ui.fill)
        }
    
        }
    }
}
