package com.centurywar.game.art

import android.graphics.Canvas
import com.centurywar.game.EraArt

/**
 * Oznaczenia dowódcy dla dziewięciu epok i wariantów. Nakładka na istniejącą sylwetkę,
 * dzięki czemu jednostki zachowują rysunek swojej epoki. Zero nowych obiektów w klatce.
 */
class CommanderArt(private val pen: Pen) {
    private companion object {
        val GOLD = 0xFFE6BD54.toInt()
        val SILVER = 0xFFB6C8D1.toInt()
        val CYAN = 0xFF70ECF2.toInt()
        val BROWN = 0xFF825329.toInt()
        val PURPLE = 0xFFAF95E8.toInt()
    }

    fun draw(c: Canvas, art: EraArt, team: Int) {
        val accent = when (art) {
            EraArt.TRIBAL -> BROWN
            EraArt.BRONZE, EraArt.POWDER -> GOLD
            EraArt.IRON, EraArt.FEUDAL, EraArt.STEAM -> SILVER
            EraArt.STEEL, EraArt.COMBUSTION -> GOLD
            EraArt.SILICON, EraArt.GALVANIC, EraArt.SWARM -> CYAN
            EraArt.ORBITAL, EraArt.BIOTECH -> PURPLE
        }
        // Poprzeczny, widoczny na całym polu znak drużyny.
        c.drawRoundRect(-10f, -49f, 9f, -40f, 3f, 3f, pen.fp(team))
        c.drawRoundRect(-10f, -49f, 9f, -40f, 3f, 3f, pen.op(2.3f))
        c.drawRect(-8f, -47f, 7f, -45f, pen.fp(accent))
        c.drawCircle(0f, -43f, 3.4f, pen.fp(accent))
        c.drawCircle(0f, -43f, 3.4f, pen.op(1.4f))
        // Własny proporzec ze znakiem epoki; nie zasłania broni jednostki.
        c.drawLine(-14f, -32f, -14f, -72f, pen.lp(Pen.OUTLINE, 3f))
        val flag = pen.path
        flag.reset()
        flag.moveTo(-14f, -72f)
        flag.lineTo(5f, -68f)
        flag.lineTo(-2f, -61f)
        flag.lineTo(-14f, -63f)
        flag.close()
        c.drawPath(flag, pen.fp(team))
        c.drawPath(flag, pen.op(2.4f))
        c.drawLine(-10f, -68f, -4f, -66f, pen.lp(accent, 2.5f))
        // Silhouette korony/insygnium — od piór po hełm orbitalny.
        when (art) {
            EraArt.TRIBAL -> {
                c.drawLine(-6f, -70f, -10f, -82f, pen.lp(accent, 3f))
                c.drawLine(0f, -70f, 0f, -85f, pen.lp(team, 3.5f))
                c.drawLine(6f, -70f, 11f, -82f, pen.lp(accent, 3f))
            }
            EraArt.BRONZE -> {
                c.drawArc(-12f, -85f, 12f, -62f, 195f, 150f, false, pen.lp(accent, 4f))
                c.drawCircle(0f, -79f, 3f, pen.fp(team))
            }
            EraArt.IRON -> {
                c.drawRect(-10f, -73f, 10f, -69f, pen.fp(SILVER))
                c.drawLine(-8f, -76f, 0f, -83f, pen.lp(accent, 3f))
                c.drawLine(0f, -83f, 8f, -76f, pen.lp(accent, 3f))
            }
            EraArt.FEUDAL -> {
                c.drawRect(-9f, -75f, 9f, -68f, pen.fp(SILVER))
                c.drawRect(-2f, -82f, 2f, -69f, pen.fp(GOLD))
                c.drawLine(-10f, -75f, 10f, -75f, pen.lp(Pen.OUTLINE, 2f))
            }
            EraArt.POWDER -> {
                c.drawLine(-15f, -74f, 14f, -74f, pen.lp(Pen.OUTLINE, 5f))
                c.drawLine(-9f, -74f, 0f, -84f, pen.lp(accent, 4f))
                c.drawLine(0f, -84f, 9f, -74f, pen.lp(accent, 4f))
            }
            EraArt.STEAM -> {
                c.drawCircle(0f, -76f, 9f, pen.fp(accent))
                c.drawCircle(0f, -76f, 9f, pen.op(2.5f))
                c.drawCircle(0f, -76f, 4f, pen.fp(team))
                c.drawLine(-13f, -76f, 13f, -76f, pen.lp(Pen.OUTLINE, 2f))
            }
            EraArt.STEEL, EraArt.COMBUSTION -> {
                c.drawRoundRect(-11f, -80f, 11f, -67f, 4f, 4f, pen.fp(team))
                c.drawRoundRect(-11f, -80f, 11f, -67f, 4f, 4f, pen.op(2.5f))
                c.drawRect(-8f, -74f, 8f, -71f, pen.fp(accent))
            }
            EraArt.SILICON, EraArt.GALVANIC, EraArt.SWARM -> {
                c.drawLine(0f, -71f, 0f, -83f, pen.lp(accent, 3.5f))
                c.drawCircle(0f, -85f, 4f, pen.fp(accent))
                c.drawCircle(0f, -85f, 4f, pen.op(2f))
                c.drawLine(-9f, -75f, 9f, -75f, pen.lp(accent, 3f))
            }
            EraArt.ORBITAL, EraArt.BIOTECH -> {
                c.drawOval(-15f, -84f, 15f, -65f, pen.lp(accent, 3f))
                c.drawCircle(0f, -76f, 4f, pen.fp(team))
                c.drawCircle(0f, -76f, 4f, pen.op(1.8f))
            }
        }
    }
}
