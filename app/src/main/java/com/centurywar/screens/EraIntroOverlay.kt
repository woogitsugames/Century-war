package com.centurywar.screens

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Picture
import android.graphics.RectF
import com.centurywar.engine.Game
import com.centurywar.engine.L
import com.centurywar.engine.Ui
import com.centurywar.game.Battle
import com.centurywar.game.BattleConfig
import com.centurywar.game.BattleRenderer
import com.centurywar.game.Defs
import com.centurywar.game.Mode
import com.centurywar.game.Role
import com.centurywar.game.art.Backdrops
import kotlin.math.max
import kotlin.math.min

/**
 * Krótka, jednorazowa plansza fabularna. Nie dotyka symulacji ani logu komend.
 * Wszystkie sylwetki i krajobraz nagrywamy przed renderowaniem do Picture.
 */
class EraIntroOverlay private constructor(
    private val g: Game,
    val era: Int,
    scene: Battle,
    private val renderer: BattleRenderer,
) {
    companion object {
        // Dość czasu na przeczytanie; dotknięcie zamyka wcześniej.
        const val DURATION = 7f

        /** Mapa i bitwa używają tej samej maski profilu. */
        fun showIfFirst(g: Game, era: Int, battle: Battle? = null, renderer: BattleRenderer? = null): EraIntroOverlay? {
            if (!g.profile.markEraIntroSeen(era)) return null
            return create(g, era, battle, renderer)
        }

        /** Podgląd do testów obrazkowych, bez zmiany profilu. */
        internal fun preview(g: Game, era: Int): EraIntroOverlay =
            create(g, era, null, null)

        private fun create(g: Game, era: Int, battle: Battle?, renderer: BattleRenderer?): EraIntroOverlay {
            require(era in 0 until Defs.ERA_COUNT)
            val artBattle = if (battle != null && battle.player.era == era) battle
                else Battle(BattleConfig(Mode.SKIRMISH, 68L, 1f, startEra = era, maxEra = era))
            return EraIntroOverlay(g, era, artBattle, renderer ?: BattleRenderer(g, artBattle))
        }
    }

    private val backdrop = Backdrops(g, scene)
    private val units = Array(4) { Defs.unit(era, Role.entries[it]) }
    private val unitNames = Array(4) { "" }
    private val cards = Array(4) { RectF() }
    private val panel = RectF()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var landscape: Picture? = null
    private var figures: Picture? = null
    private var title = ""
    private var story = ""
    private var newUnitsLabel = ""
    private var skipLabel = ""
    private var remaining = DURATION

    val active: Boolean get() = remaining > 0f

    // Teksty muszą być gotowe przed pierwszym rysowaniem, nie dopiero po zmianie języka.
    init { onLanguageChanged() }

    fun onLanguageChanged() {
        title = L[Defs.eraKeys[era]]
        story = L["era_intro_story_$era"]
        newUnitsLabel = L["era_intro_new_units"]
        skipLabel = L["era_intro_skip"]
        for (i in units.indices) unitNames[i] = L[units[i].nameKey]
    }

    fun update(dt: Float) {
        remaining = (remaining - dt.coerceAtLeast(0f)).coerceAtLeast(0f)
    }

    fun dismiss() { remaining = 0f }

    /** Po zmianie rozmiaru lub gęstości przygotowujemy tło i postacie poza render(). */
    fun layout() {
        val w = g.width.toFloat()
        val h = g.height.toFloat()
        val d = g.ui.dp
        val freeW = max(1f, w - g.insetL - g.insetR)
        val freeH = max(1f, h - g.insetT - g.insetB)
        val pw = min(800f * d, freeW - 22f * d).coerceAtLeast(1f)
        val ph = min(430f * d, freeH - 20f * d).coerceAtLeast(1f)
        val cx = (g.insetL + w - g.insetR) * 0.5f
        val cy = (g.insetT + h - g.insetB) * 0.5f
        panel.set(cx - pw * 0.5f, cy - ph * 0.5f, cx + pw * 0.5f, cy + ph * 0.5f)

        val inner = 16f * d
        val gap = min(10f * d, pw * 0.016f)
        val cw = max(1f, (pw - inner * 2f - gap * 3f) / 4f)
        val cardTop = panel.top + min(164f * d, ph * 0.39f)
        val cardBottom = max(cardTop + 1f, panel.bottom - min(46f * d, ph * 0.12f))
        for (i in cards.indices) {
            val x = panel.left + inner + i * (cw + gap)
            cards[i].set(x, cardTop, x + cw, cardBottom)
        }

        // Krajobraz epoki, wykorzystujący tę samą klasę Backdrops co bitwa.
        val bg = Picture()
        val backgroundCanvas = bg.beginRecording(g.width, g.height)
        val ground = h * 0.77f
        val scale = max(d * 0.83f, w / 1100f)
        backdrop.drawSky(backgroundCanvas, w, h, ground, 0f)
        backdrop.drawLandscape(backgroundCanvas, w, ground, 0f, scale)
        backdrop.drawGround(backgroundCanvas, w, h, ground, 0f, scale)
        bg.endRecording()
        landscape = bg

        // Sylwetki korzystają z rendererów jednostek, a nie z plików graficznych.
        val portraits = Picture()
        val portraitCanvas = portraits.beginRecording(g.width, g.height)
        for (i in cards.indices) {
            val r = cards[i]
            val figureHeight = min(r.width() * 1.45f, (r.height() - 42f * d).coerceAtLeast(12f * d))
            renderer.drawUnitIcon(portraitCanvas, units[i], r.centerX(),
                r.bottom - 29f * d, figureHeight, Ui.PLAYER)
        }
        portraits.endRecording()
        figures = portraits
    }

    /** Wyłącznie odtwarzanie gotowych Picture i malowanie tekstów; brak alokacji klatek. */
    fun draw(c: Canvas) {
        if (!active) return
        val d = g.ui.dp
        landscape?.let { c.drawPicture(it) }
        paint.color = Color.argb(124, 5, 8, 18)
        c.drawRect(0f, 0f, g.width.toFloat(), g.height.toFloat(), paint)
        g.ui.panel(c, panel, Color.argb(245, 26, 34, 52), 16f)
        g.ui.ribbon(c, panel.centerX(), panel.top + 7f * d, title,
            23f, Ui.RIBBON, panel.width() * 0.58f, Ui.GOLD, panel.width() - 22f * d)
        g.ui.textO(c, story, panel.centerX(), panel.top + min(96f * d, panel.height() * 0.23f),
            g.ui.fit(story, 14f, panel.width() - 34f * d, 8f), Ui.TEXT, Paint.Align.CENTER)
        g.ui.textO(c, newUnitsLabel, panel.centerX(), cards[0].top - 10f * d,
            12f, Ui.GOLD, Paint.Align.CENTER)

        for (i in cards.indices) {
            val r = cards[i]
            paint.color = Color.argb(220, 18, 28, 47)
            c.drawRoundRect(r, 10f * d, 10f * d, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1.7f * d
            paint.color = Ui.ERA[era]
            c.drawRoundRect(r, 10f * d, 10f * d, paint)
            paint.style = Paint.Style.FILL
        }
        figures?.let { c.drawPicture(it) }
        for (i in cards.indices) {
            val r = cards[i]
            g.ui.textO(c, unitNames[i], r.centerX(), r.bottom - 10f * d,
                g.ui.fit(unitNames[i], 11f, r.width() - 8f * d, 8f),
                Ui.TEXT, Paint.Align.CENTER)
        }
        g.ui.textO(c, skipLabel, panel.centerX(), panel.bottom - 14f * d,
            g.ui.fit(skipLabel, 11f, panel.width() - 20f * d, 8f),
            Ui.DIM, Paint.Align.CENTER)
    }
}
