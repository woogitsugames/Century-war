package com.centurywar.screens

import android.graphics.Canvas
import com.centurywar.engine.L
import com.centurywar.engine.TouchEvent
import com.centurywar.engine.Ui

internal class BarrageAiming(private val screen: BattleScreen) {
    var aimingBarrage = false
    var aimWorldX = 0f
    var aimScreenX = 0f
    var aimDownX = 0f
    var aimDownY = 0f
    var aimStartedInField = false
    var aimMoved = false

    fun fire(worldX: Float) {
        with(screen) {
        if (paused || finished || !battle.abilityReady(battle.player, 0)) return
        achievementEvents.beforeAimedBarrage()
        val fired = commands.barrage(worldX)
        achievementEvents.afterAimedBarrage(fired)
        if (fired) haptics.onBarrage()
    
        }
    }

    /**
     * Przyciąganie celownika do najbliższego widocznego skupiska wrogów (do 110 j. od palca):
     * środek grupy w promieniu rozrzutu ostrzału. Bez alokacji; tylko interfejs, nie symulacja.
     */
    fun snap(x: Float): Float {
        return with(screen) {
        val p = battle.player
        val near = p.baseFront + 220f
        val far = p.baseFront + 1370f
        var bestCount = 0
        var bestX = x
        var bestDist = Float.MAX_VALUE
        for (u in battle.units) {
            if (!u.alive || u.side != 1) continue
            if (battle.fog && !p.fogSight && u.x - p.front > 650f) continue
            if (u.x < near || u.x > far || kotlin.math.abs(u.x - x) > 110f) continue
            var n = 0
            var sum = 0f
            for (v in battle.units) {
                if (!v.alive || v.side != 1 || kotlin.math.abs(v.x - u.x) > 120f) continue
                n++; sum += v.x
            }
            val cx = sum / n
            val d = kotlin.math.abs(cx - x)
            if (n > bestCount || (n == bestCount && d < bestDist)) { bestCount = n; bestX = cx; bestDist = d }
        }
        bestX.coerceIn(near, far)
    
        }
    }


    fun onButton() {
        with(screen) {
            val p = battle.player

                if (aimingBarrage) {
                    // Drugie dotknięcie: szybki ostrzał, liczony do osiągnięć jak celowany.
                    achievementEvents.beforeAimedBarrage()
                    val fired = commands.barrage()
                    achievementEvents.afterAimedBarrage(fired)
                    if (fired) haptics.onBarrage()
                    aimingBarrage = false
                } else if (battle.abilityReady(p, 0)) {
                    showTurrets = false
                    aimingBarrage = true
                    aimScreenX = w * 0.5f
                    aimWorldX = snap(r.worldX(aimScreenX))
                }
            
        }
    }

    fun onTouch(e: TouchEvent): Boolean {
        with(screen) {
        if (aimingBarrage) {
            when (e.action) {
                TouchEvent.DOWN -> {
                    aimDownX = e.x
                    aimDownY = e.y
                    aimMoved = false
                    aimStartedInField = fieldRect.contains(e.x, e.y)
                }
                TouchEvent.MOVE -> if (kotlin.math.abs(e.x - aimDownX) > 10f * dp ||
                    kotlin.math.abs(e.y - aimDownY) > 10f * dp) aimMoved = true
            }
            if ((e.action == TouchEvent.DOWN || e.action == TouchEvent.MOVE) &&
                aimStartedInField && fieldRect.contains(e.x, e.y)) {
                aimScreenX = e.x
                aimWorldX = snap(r.worldX(e.x))
            }
            // Przeciągnięcie celownika: ostrzał spada po puszczeniu palca.
            if (e.action == TouchEvent.UP && aimStartedInField && aimMoved) {
                fire(aimWorldX)
                aimingBarrage = false
                aimStartedInField = false
                return true
            }
        }

        }
        return false
    }

    fun onTap(x: Float, y: Float): Boolean {
        if (!aimingBarrage) return false
        with(screen) {
            if (!paused && !finished && !aimMoved && aimStartedInField &&
                battle.abilityReady(battle.player, 0) &&
                fieldRect.contains(x, y) && (!showTurrets || !panel.contains(x, y))) {
                fire(snap(r.worldX(x)))
            }
            aimingBarrage = false
        }
        return true
    }

    fun drawTarget(c: Canvas) {
        with(screen) {
            if (aimingBarrage) r.drawBarrageAim(c, aimWorldX, fieldRect)
        }
    }

    fun drawHint(c: Canvas) {
        with(screen) {
            if (aimingBarrage) {
                ui.ribbon(c, w * 0.5f, fieldRect.top + 22 * dp, L["barrage_aim_hint"],
                    15f, Ui.PLAYER, 0f, Ui.TEXT, w - g.insetL - g.insetR - 24 * dp)
            }
        }
    }

    fun resetGesture() {
        aimStartedInField = false
        aimMoved = false
    }
}
