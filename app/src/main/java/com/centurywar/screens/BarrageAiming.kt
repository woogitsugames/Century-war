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

/** Mechaniczne wydzielenie BarrageAiming; ten sam stan i kolejność operacji co w BattleScreen. */
internal class BarrageAiming(private val owner: BattleScreen) {

    fun onButton() {
        with(owner) {
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
                    aimWorldX = snapBarrageTarget(r.worldX(aimScreenX))
                }
        }
    }

    fun onTouch(e: TouchEvent): Boolean {
        with(owner) {
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
                    aimWorldX = snapBarrageTarget(r.worldX(e.x))
                }
                // Przeciągnięcie celownika: ostrzał spada po puszczeniu palca.
                if (e.action == TouchEvent.UP && aimStartedInField && aimMoved) {
                    fireAimedBarrage(aimWorldX)
                    aimingBarrage = false
                    aimStartedInField = false
                    return true
                }
            }
                return false
        }
    }

    fun drawAim(c: Canvas) {
        with(owner) {
                r.drawBarrageAim(c, aimWorldX, fieldRect)
        }
    }

    fun drawHint(c: Canvas) {
        with(owner) {
                ui.ribbon(c, w * 0.5f, fieldRect.top + 22 * dp, L["barrage_aim_hint"],
                    15f, Ui.PLAYER, 0f, Ui.TEXT, w - g.insetL - g.insetR - 24 * dp)
            }
        }
    }

    fun fireAimedBarrage(worldX: Float) {
        with(owner) {
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
    fun snapBarrageTarget(x: Float): Float {
        with(owner) {
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
        return bestX.coerceIn(near, far)
        }
    }
}
