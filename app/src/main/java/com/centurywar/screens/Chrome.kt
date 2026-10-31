package com.centurywar.screens

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import com.centurywar.engine.Button
import com.centurywar.engine.Icons
import com.centurywar.engine.L
import com.centurywar.engine.Screen
import com.centurywar.engine.Ui

/**
 * Wspólne elementy ekranów menu: przycisk "Wstecz", wstęga tytułu, przyciemnienie
 * tła i plakietki zasobów w prawym górnym rogu. Dzięki temu wszystkie ekrany
 * wyglądają spójnie.
 */
class Chrome(private val s: Screen) {
    private val shade = Paint()
    private var shadeH = -1f
    private var shadeA = -1

    /** Wysokość paska nagłówka (od góry ekranu, z wcięciem). */
    val top get() = s.g.insetT + 64 * s.dp

    fun layoutBack(b: Button) {
        b.label = L["back"]
        b.iconId = Icons.BACK
        b.set(s.g.insetL + 12 * s.dp, s.g.insetT + 10 * s.dp, 118 * s.dp, 44 * s.dp)
    }

    /** Przyciemnia ilustrowane tło (mocniej u góry i u dołu), żeby treść była czytelna. */
    fun dim(c: Canvas, alpha: Int) {
        val h = s.h
        if (h != shadeH || alpha != shadeA) {
            shadeH = h; shadeA = alpha
            shade.shader = LinearGradient(0f, 0f, 0f, h,
                intArrayOf(Color.argb(alpha + 40, 8, 10, 20), Color.argb(alpha - 20, 8, 10, 20), Color.argb(alpha + 40, 8, 10, 20)),
                floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        }
        c.drawRect(0f, 0f, s.w, h, shade)
    }

    fun title(c: Canvas, text: String) {
        val ui = s.ui
        val cx = s.w / 2 + (s.g.insetL - s.g.insetR) / 2
        val maxW = s.w - s.g.insetL - s.g.insetR - 2 * 150 * s.dp - 260 * s.dp
        ui.ribbon(c, cx, s.g.insetT + 32 * s.dp, text, 20f, Ui.RIBBON, 220 * s.dp, Ui.GOLD,
            maxW.coerceAtLeast(160 * s.dp))
    }

    /** Plakietki w prawym górnym rogu (od prawej). Zwraca lewą krawędź. */
    fun badges(c: Canvas, medals: Int, stars: Int = -1): Float {
        val ui = s.ui
        var x = s.w - s.g.insetR - 14 * s.dp
        val cy = s.g.insetT + 32 * s.dp
        val ms = medals.toString()
        x = ui.badge(c, x, cy, Icons.MEDAL, ms, 16f, Ui.GOLD, alignRight = true) - ui.badgeWidth(ms, 16f)
        if (stars >= 0) {
            val ss = stars.toString()
            x -= 8 * s.dp
            x = ui.badge(c, x, cy, Icons.STAR, ss, 16f, Ui.GOLD, alignRight = true) - ui.badgeWidth(ss, 16f)
        }
        return x
    }
}
