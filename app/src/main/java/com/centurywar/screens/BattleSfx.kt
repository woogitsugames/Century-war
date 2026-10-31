package com.centurywar.screens

import com.centurywar.engine.Game
import com.centurywar.engine.Sound
import com.centurywar.game.FxBuffer
import com.centurywar.game.Proj
import kotlin.math.abs
import kotlin.math.min

/** Wspólne mapowanie zdarzeń bitwy na dźwięki dla rozgrywki i powtórki. */
object BattleSfx {
    fun play(g: Game, fx: FxBuffer, camX: Float, worldVisible: Float) {
        if (fx.count == 0 || g.sound == null) return
        val center = camX + worldVisible / 2
        val range = worldVisible * 0.9f
        for (i in 0 until fx.count) {
            val vol = (1f - abs(fx.x[i] - center) / range).coerceIn(0.15f, 1f)
            when (fx.type[i]) {
                FxBuffer.HIT -> g.sfx(Sound.HIT, vol * 0.5f, 60)
                FxBuffer.DEATH -> g.sfx(Sound.DEATH, vol * 0.6f, 90)
                FxBuffer.EXPLOSION -> g.sfx(Sound.EXPLOSION, vol * min(1f, fx.value[i]), 120)
                FxBuffer.MUZZLE -> {
                    val p = fx.value[i].toInt()
                    when (p) {
                        Proj.BEAM, Proj.PLASMA -> g.sfx(Sound.LASER, vol * 0.5f, 70)
                        Proj.BULLET, Proj.SHELL, Proj.BALL, Proj.ROCKET -> g.sfx(Sound.SHOT, vol * 0.55f, 55)
                        else -> g.sfx(Sound.ARROW, vol * 0.45f, 70)
                    }
                }
                FxBuffer.BASE_HIT -> g.sfx(Sound.THUMP, vol * 0.6f, 120)
                FxBuffer.EVOLVE -> g.sfx(Sound.EVOLVE, if (fx.side[i] == 0) 1f else 0.5f, 300)
                FxBuffer.GOLD -> g.sfx(Sound.COIN, 0.35f, 110)
                FxBuffer.BARRAGE -> g.sfx(Sound.EXPLOSION, 0.8f, 200, 0.8f)
            }
        }
    }
}
