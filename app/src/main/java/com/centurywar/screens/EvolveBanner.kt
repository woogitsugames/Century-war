package com.centurywar.screens

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.centurywar.engine.Button
import com.centurywar.engine.L
import com.centurywar.engine.Ui
import com.centurywar.game.Defs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Zlecenie 139: półprzezroczysty baner „Możesz awansować!” na środku pola bitwy.
 * Pokazuje się raz na epokę, gdy awans staje się dostępny; nie pauzuje bitwy.
 * „Awansuj” przechodzi przez tę samą ścieżkę co przycisk Ewolucji (BattleScreen.onClick).
 * Przyciski są prealokowane, teksty składane tylko przy pokazaniu banera.
 */
internal class EvolveBanner(private val owner: BattleScreen, val goBtn: Button, val waitBtn: Button) {
    companion object {
        const val GO = 139
        const val WAIT = 140
        /** Czas widoczności przed zanikaniem (s). */
        const val SHOW_TIME = 8f
        const val FADE_TIME = 0.6f
        /** Krótka zwłoka: baner nie miga przy chwilowej dostępności ani w kadrach z dt = 0. */
        const val DELAY = 0.5f
    }

    /** Epoka, dla której baner już się pojawił (raz na epokę). */
    var shownForEra = -1
        private set
    var active = false
        private set
    private var elapsed = 0f
    private var availableFor = 0f
    private var pulse = 0f
    private var hidden = false
    private var title = ""
    private var subtitle = ""
    val rect = RectF()
    private val bg = Paint(Paint.ANTI_ALIAS_FLAG)
    private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val tmp = RectF()

    init {
        goBtn.visible = false
        waitBtn.visible = false
        goBtn.highlight = true
        goBtn.iconId = com.centurywar.engine.Icons.EVOLVE
    }

    /**
     * [canEvolve] — awans dostępny teraz; [blocked] — pauza, panel, wybór karty, samouczek,
     * inna podpowiedź. Zablokowany baner nie startuje (odkładamy go) i jest ukryty.
     */
    fun update(dt: Float, era: Int, maxEra: Int, canEvolve: Boolean, blocked: Boolean) {
        pulse += dt
        if (pulse > 1000f) pulse = 0f
        hidden = blocked
        if (active) {
            if (!canEvolve || era != shownForEra) { dismiss(); return }
            if (!blocked) elapsed += dt
            if (elapsed >= SHOW_TIME + FADE_TIME) { dismiss(); return }
        } else if (canEvolve && !blocked && era < maxEra && shownForEra != era) {
            availableFor += dt
            if (availableFor >= DELAY) activate(era)
        } else if (!canEvolve) {
            availableFor = 0f
        }
        val show = active && !hidden && elapsed < SHOW_TIME + FADE_TIME
        goBtn.visible = show
        waitBtn.visible = show
    }

    private fun activate(era: Int) {
        active = true
        availableFor = 0f
        shownForEra = era
        elapsed = 0f
        refreshTexts()
        layout()
    }

    fun refreshTexts() {
        val era = shownForEra
        title = L["evo139_title"]
        subtitle = if (era in 0 until Defs.ERA_COUNT - 1) {
            L.f("evo139_next", L[Defs.eraKeys[era + 1]])
        } else ""
        goBtn.label = L["evo139_go"]
        waitBtn.label = L["evo139_wait"]
    }

    fun dismiss() {
        active = false
        goBtn.visible = false
        waitBtn.visible = false
    }

    /** Baner mieści się wyłącznie w polu bitwy: nie zasłania paska górnego ani dolnego HUD. */
    fun layout() {
        val dp = owner.dp
        val f = owner.fieldRect
        val gap = 8 * dp
        val bw = min(400 * dp, f.width() - 2 * gap - owner.g.insetL - owner.g.insetR)
        val bh = min(132 * dp, f.height() - 2 * gap)
        if (bw <= 0f || bh <= 0f) { rect.setEmpty(); return }
        val cx = (f.left + owner.g.insetL + f.right - owner.g.insetR) * 0.5f
        val cy = f.top + gap + (f.height() - 2 * gap) * 0.42f
        val top = (cy - bh / 2).coerceIn(f.top + gap, f.bottom - gap - bh)
        rect.set(cx - bw / 2, top, cx + bw / 2, top + bh)
        val pad = 14 * dp
        val bGap = 10 * dp
        val btnH = 48 * dp
        val btnW = min(170 * dp, (bw - 2 * pad - bGap) / 2f)
        val y = rect.bottom - pad - btnH
        val left = cx - btnW - bGap / 2
        val right = cx + bGap / 2
        // „Awansuj” po stronie przycisku Ewolucji (w trybie leworęcznym lustrzanie).
        val evolveOnRight = owner.evolveBtn.r.centerX() >= cx
        goBtn.set(if (evolveOnRight) right else left, y, btnW, btnH)
        waitBtn.set(if (evolveOnRight) left else right, y, btnW, btnH)
    }

    /** Alfa banera: pełna przez SHOW_TIME, potem łagodne zanikanie. */
    private fun alpha(): Float =
        if (elapsed <= SHOW_TIME) 1f else (1f - (elapsed - SHOW_TIME) / FADE_TIME).coerceIn(0f, 1f)

    fun draw(c: Canvas, ui: Ui) {
        if (!active || hidden || rect.isEmpty) return
        val dp = owner.dp
        val a = alpha()
        if (a <= 0f) return
        val save = if (a < 1f) c.saveLayerAlpha(rect.left - 8 * dp, rect.top - 8 * dp,
            rect.right + 8 * dp, rect.bottom + 8 * dp, (a * 255).toInt()) else -1
        // Tło ok. 60% przezroczystości: pole bitwy pozostaje widoczne.
        bg.color = Color.argb(102, 12, 16, 28)
        val rad = 14 * dp
        c.drawRoundRect(rect, rad, rad, bg)
        border.color = Color.argb(200, Color.red(Ui.GOLD), Color.green(Ui.GOLD), Color.blue(Ui.GOLD))
        border.strokeWidth = 2f * dp
        c.drawRoundRect(rect, rad, rad, border)
        val w = rect.width() - 28 * dp
        ui.textO(c, title, rect.centerX(), rect.top + 30 * dp, ui.fit(title, 20f, w),
            Ui.GOLD, Paint.Align.CENTER)
        if (subtitle.isNotEmpty()) {
            ui.textO(c, subtitle, rect.centerX(), rect.top + 52 * dp, ui.fit(subtitle, 14f, w),
                Ui.TEXT, Paint.Align.CENTER)
        }
        ui.button(c, goBtn, 15f)
        ui.button(c, waitBtn, 15f)
        if (save >= 0) c.restoreToCount(save)
    }

    /** Delikatna pulsująca poświata przycisku Ewolucji, dopóki awans czeka. */
    fun drawEvolveGlow(c: Canvas, evolve: Button, canEvolve: Boolean) {
        if (!canEvolve || !evolve.visible || evolve.r.isEmpty) return
        val dp = owner.dp
        val s = (sin(pulse * 4.2f) + 1f) * 0.5f
        tmp.set(evolve.r)
        val grow = (2f + 3f * s) * dp
        tmp.inset(-grow, -grow)
        glow.color = Color.argb((70 + 110 * s).toInt(), 255, 214, 92)
        glow.strokeWidth = max(2f * dp, 3.5f * dp * s)
        val rad = min(14f * dp, tmp.height() * 0.32f)
        c.drawRoundRect(tmp, rad, rad, glow)
    }
}
