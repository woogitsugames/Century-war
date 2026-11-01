package com.centurywar.screens

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.centurywar.engine.Quality
import com.centurywar.engine.Ui
import com.centurywar.game.Defs
import com.centurywar.game.art.Pen
import com.centurywar.game.art.Soldiers
import kotlin.math.min
import kotlin.math.sin

/**
 * Miniatura żywej bitwy w menu. Korzysta z tych samych rysunków jednostek co bitwa,
 * ale nie uruchamia symulacji, dźwięku ani losowości. Co 20 s przenika do nowej epoki.
 */
internal class MenuBattleScene {
    private val pen = Pen()
    private val soldier = Soldiers(pen)
    private val shadow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val atmosphere = Paint()
    private val shadowRect = RectF()

    fun draw(c: Canvas, w: Float, h: Float, time: Float, animated: Boolean, quality: Int) {
        val era = if (animated) ((time / 20f).toInt() % Defs.ERA_COUNT) else 0
        val phase = if (animated) time % 20f else 0f
        // Ostatnie dwie sekundy cyklu: oba zestawy postaci współistnieją.
        val fade = if (phase > 18f) {
            val u = ((phase - 18f) * 0.5f).coerceIn(0f, 1f)
            u * u * (3f - 2f * u)
        } else 0f
        drawEra(c, w, h, time, era, 1f - fade, animated, quality)
        if (fade > 0f) drawEra(c, w, h, time, (era + 1) % Defs.ERA_COUNT, fade, animated, quality)
    }

    private fun drawEra(c: Canvas, w: Float, h: Float, t: Float, era: Int,
                        alpha: Float, animated: Boolean, quality: Int) {
        if (alpha <= 0f) return
        val spec = Defs.spec(era)
        atmosphere.color = Color.argb((22f * alpha).toInt(), Color.red(spec.accent),
            Color.green(spec.accent), Color.blue(spec.accent))
        c.drawRect(0f, h * 0.57f, w, h, atmosphere)

        pen.cv = c
        pen.alpha = alpha
        pen.flash = false
        pen.tintAmt = 0f
        pen.char = 0f
        pen.detail = if (quality == Quality.HIGH) 3 else 1
        pen.ow = 1.65f
        pen.outlineScale = 1f

        val scale = min(h / 450f, w / 880f).coerceAtLeast(0.5f)
        val floor = h * 0.875f
        val count = when (quality) { Quality.LOW -> 1; Quality.MED -> 2; else -> 3 }
        for (team in 0..1) {
            val color = if (team == 0) Ui.PLAYER else Ui.ENEMY
            for (i in 0 until count) {
                val drift = if (animated) sin(t * 0.8f + i * 0.7f) * 12f * scale else 0f
                val x = if (team == 0) w * 0.075f + i * 46f * scale + drift
                    else w * 0.925f - i * 46f * scale - drift
                val ranged = count > 1 && i == count - 1
                val weapon = spec.weapons[if (ranged) 1 else 0]
                shadow.color = Color.argb((55f * alpha).toInt(), 20, 22, 28)
                shadowRect.set(x - 19f * scale, floor - 3f * scale,
                    x + 19f * scale, floor + 5f * scale)
                c.drawOval(shadowRect, shadow)

                val saved = c.save()
                c.translate(x, floor)
                c.scale(if (team == 0) scale else -scale, scale)
                soldier.stride = if (animated) 0.8f else 0f
                soldier.windup = if (animated && !ranged) {
                    (0.5f + 0.5f * sin(t * 2.3f + i + team)).coerceIn(0f, 1f)
                } else -1f
                soldier.recoil = 0f
                soldier.collapse = 0f
                val walk = if (animated) t * 5f + i * 1.1f + team * 2.1f else 0f
                val attack = if (animated && !ranged) {
                    (1f - ((t * 1.25f + i * 0.21f + team * 0.48f) % 1f))
                } else 0f
                soldier.draw(spec.art, ranged, weapon, color, walk, attack,
                    mounted = false, heavy = false)
                c.restoreToCount(saved)
            }
        }
        pen.alpha = 1f
    }
}
