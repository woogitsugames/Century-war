package com.centurywar.screens

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.centurywar.engine.Button
import com.centurywar.engine.Icons
import com.centurywar.engine.L
import com.centurywar.engine.Palette
import com.centurywar.engine.Ui
import com.centurywar.game.Bonuses
import com.centurywar.game.Defs
import com.centurywar.game.Role
import com.centurywar.game.UnitDef
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Porównanie dwóch jednostek, macierz kontr i przekrój DPS przez epoki.
 * Wszystkie napisy, wartości i wysokości słupków są buforowane w [layout].
 */
internal class CodexComparePanel(private val screen: CodexScreen, startEra: Int) {
    private companion object {
        const val CONTROL_HEIGHT = 106f
        const val CARD_TOP = 36f
        const val CARD_HEIGHT = 270f
        const val COUNTER_TOP = 338f
        const val COUNTER_HEIGHT = 235f
        const val CHART_TOP = 592f
        const val CHART_HEIGHT = 216f
        const val CONTENT_HEIGHT = 824f
        const val BUTTON_BASE = 300
        val PANEL_COLOR = Color.rgb(42, 52, 74)
    }

    private val ui get() = screen.ui
    private val dp get() = screen.dp
    private val buttons = Array(8) { index ->
        screen.btn(BUTTON_BASE + index).apply {
            label = if (index % 2 == 0) "‹" else "›"
            visible = false
        }
    }
    private val eras = intArrayOf(
        startEra.coerceIn(0, Defs.ERA_COUNT - 1),
        if (startEra.coerceIn(0, Defs.ERA_COUNT - 1) < Defs.ERA_COUNT - 1)
            startEra.coerceIn(0, Defs.ERA_COUNT - 1) + 1 else max(0, Defs.ERA_COUNT - 2),
    )
    private val roles = intArrayOf(0, 1)
    private val units = arrayOfNulls<UnitDef>(2)
    private val eraTexts = Array(2) { "" }
    private val roleTexts = Array(2) { "" }
    private val nameTexts = Array(2) { "" }
    private val statLabels = Array(CodexCompareModel.STATS) { "" }
    private val values = Array(2) { FloatArray(CodexCompareModel.STATS) }
    private val valueTexts = Array(2) { Array(CodexCompareModel.STATS) { "" } }
    private val directions = Array(2) { IntArray(CodexCompareModel.STATS) }
    private val matrix = Array(Role.entries.size) { Array(Role.entries.size) { "" } }
    private val roleNames = Array(Role.entries.size) { "" }
    private val growthDps = FloatArray(Defs.ERA_COUNT)
    private val growthLabels = Array(Defs.ERA_COUNT) { "" }
    private val growthEraLabels = Array(Defs.ERA_COUNT) { "" }
    private var growthMax = 1f

    private var heading = ""
    private var legend = ""
    private var matrixHeading = ""
    private var matrixAxis = ""
    private var chartHeading = ""
    private var chartAxis = ""
    private var x = 0f
    private var y = 0f
    private var width = 0f
    private val rect = RectF()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    val controlHeight get() = CONTROL_HEIGHT * dp
    val contentHeight get() = CONTENT_HEIGHT * dp

    fun hide() {
        for (button in buttons) button.visible = false
    }

    /** Selektory epoki i roli pozostają widoczne, przewija się tylko zestaw wyników. */
    fun layout(left: Float, top: Float, contentWidth: Float, withResearch: Boolean) {
        x = left
        y = top
        width = contentWidth
        val gap = 8 * dp
        val half = (width - gap) / 2f
        val arrow = min(43 * dp, half * 0.24f)
        for (side in 0..1) {
            val sx = x + side * (half + gap)
            for (row in 0..1) {
                val by = y + (9 + row * 48) * dp
                val base = side * 4 + row * 2
                buttons[base].set(sx + 4 * dp, by, arrow, 42 * dp)
                buttons[base + 1].set(sx + half - arrow - 4 * dp, by, arrow, 42 * dp)
                buttons[base].visible = true
                buttons[base + 1].visible = true
            }
        }
        rebuild(withResearch)
    }

    fun onClick(button: Button): Boolean {
        val index = button.id - BUTTON_BASE
        if (index !in buttons.indices) return false
        val side = index / 4
        when (index % 4) {
            0 -> eras[side] = (eras[side] + Defs.ERA_COUNT - 1) % Defs.ERA_COUNT
            1 -> eras[side] = (eras[side] + 1) % Defs.ERA_COUNT
            2 -> roles[side] = (roles[side] + Role.entries.size - 1) % Role.entries.size
            3 -> roles[side] = (roles[side] + 1) % Role.entries.size
        }
        return true
    }

    internal fun selectedEra(side: Int): Int = eras[side]
    internal fun selectedRole(side: Int): Role = Role.entries[roles[side]]

    private fun rebuild(withResearch: Boolean) {
        val bonuses = if (withResearch) screen.g.profile.bonuses() else Bonuses()
        heading = L["cx134_compare"]
        legend = L["cx134_legend"]
        matrixHeading = L["cx134_counter"]
        matrixAxis = L["cx134_axis"]
        chartAxis = L["cx134_chart_axis"]
        for (i in statLabels.indices) statLabels[i] = L[CodexCompareModel.statKeys[i]]
        for (i in roleNames.indices) roleNames[i] = L[CodexCompareModel.roleKeys[i]]
        for (side in 0..1) {
            val unit = Defs.unit(eras[side], Role.entries[roles[side]])
            units[side] = unit
            eraTexts[side] = L[Defs.eraKeys[eras[side]]]
            roleTexts[side] = roleNames[roles[side]]
            nameTexts[side] = L[unit.nameKey]
            for (field in 0 until CodexCompareModel.STATS) {
                values[side][field] = CodexCompareModel.value(unit, bonuses, field)
            }
        }
        for (field in 0 until CodexCompareModel.STATS) {
            for (side in 0..1) {
                val advantage = CodexCompareModel.advantage(
                    values[side][field], values[1 - side][field], field)
                directions[side][field] = advantage
                val marker = when {
                    advantage > 0 -> "↑ "
                    advantage < 0 -> "↓ "
                    else -> "= "
                }
                valueTexts[side][field] = marker + number(values[side][field])
            }
        }
        for (attacker in Role.entries) for (defender in Role.entries) {
            val multiplier = CodexCompareModel.counter(attacker, defender)
            matrix[attacker.ordinal][defender.ordinal] =
                String.format(Locale.ROOT, "×%.2f", multiplier)
        }
        chartHeading = L["cx134_growth"] + " · " + roleTexts[0]
        growthMax = 1f
        for (e in 0 until Defs.ERA_COUNT) {
            val dps = CodexCompareModel.eraDps(e, Role.entries[roles[0]], bonuses)
            growthDps[e] = dps
            growthMax = max(growthMax, dps)
            growthLabels[e] = shortNumber(dps)
            growthEraLabels[e] = (e + 1).toString()
        }
    }

    private fun number(v: Float): String = when {
        abs(v) >= 10000f -> String.format(Locale.ROOT, "%,d", v.roundToInt()).replace(',', ' ')
        abs(v) >= 100f -> v.roundToInt().toString()
        else -> String.format(Locale.ROOT, "%.1f", v)
    }

    private fun shortNumber(v: Float): String = when {
        v >= 10000f -> String.format(Locale.ROOT, "%.0fk", v / 1000f)
        v >= 1000f -> String.format(Locale.ROOT, "%.1fk", v / 1000f)
        v >= 100f -> v.roundToInt().toString()
        else -> String.format(Locale.ROOT, "%.1f", v)
    }

    fun drawSelectors(c: Canvas) {
        val gap = 8 * dp
        val half = (width - gap) / 2f
        for (side in 0..1) {
            val sx = x + side * (half + gap)
            rect.set(sx, y, sx + half, y + CONTROL_HEIGHT * dp - 2 * dp)
            ui.card(c, rect, PANEL_COLOR, 9f, Ui.OUTLINE)
            val center = sx + half / 2
            val available = max(24 * dp, half - 100 * dp)
            ui.textO(c, eraTexts[side], center, y + 35 * dp,
                ui.fit(eraTexts[side], 12.5f, available, 8f),
                Ui.TEXT, Paint.Align.CENTER)
            ui.textO(c, roleTexts[side], center, y + 83 * dp,
                ui.fit(roleTexts[side], 12.5f, available, 8f),
                Ui.GOLD, Paint.Align.CENTER)
        }
        for (button in buttons) ui.button(c, button, 16f)
    }

    fun drawContent(c: Canvas, top: Float, bottom: Float) {
        if (top + CONTENT_HEIGHT * dp < y + CONTROL_HEIGHT * dp || top > bottom) return
        ui.textO(c, heading, x + 7 * dp, top + 26 * dp, 18f, Ui.GOLD)
        drawUnits(c, top + CARD_TOP * dp, bottom)
        val legendY = top + (CARD_TOP + CARD_HEIGHT + 18f) * dp
        if (legendY >= y + CONTROL_HEIGHT * dp && legendY <= bottom) {
            ui.text(c, legend, x + 8 * dp, legendY,
                ui.fit(legend, 11f, width - 16 * dp, 8f), Ui.DIM)
        }
        drawCounters(c, top + COUNTER_TOP * dp, bottom)
        drawGrowth(c, top + CHART_TOP * dp, bottom)
    }

    private fun drawUnits(c: Canvas, top: Float, bottom: Float) {
        if (top > bottom || top + CARD_HEIGHT * dp < y + CONTROL_HEIGHT * dp) return
        val gap = 8 * dp
        val half = (width - gap) / 2f
        val rowsTop = top + 91 * dp
        for (side in 0..1) {
            val sx = x + side * (half + gap)
            rect.set(sx, top, sx + half, top + CARD_HEIGHT * dp)
            ui.card(c, rect, PANEL_COLOR, 11f, Ui.OUTLINE)
            val unit = units[side] ?: continue
            val nameWidth = max(60 * dp, half - 59 * dp)
            ui.textO(c, nameTexts[side], sx + 10 * dp, top + 25 * dp,
                ui.fit(nameTexts[side], 16f, nameWidth, 9f), Ui.TEXT)
            ui.text(c, roleTexts[side], sx + 10 * dp, top + 48 * dp,
                ui.fit(roleTexts[side], 11.5f, nameWidth, 8f), Ui.GOLD)
            screen.drawCompareIcon(c, unit, sx + half - 30 * dp, top + 68 * dp, 46 * dp)

            for (field in 0 until CodexCompareModel.STATS) {
                val baseline = rowsTop + field * 27 * dp
                if (field % 2 == 0) {
                    paint.color = Color.argb(35, 255, 255, 255)
                    c.drawRoundRect(sx + 5 * dp, baseline - 17 * dp,
                        sx + half - 5 * dp, baseline + 8 * dp, 5 * dp, 5 * dp, paint)
                }
                val label = statLabels[field]
                val text = valueTexts[side][field]
                val available = max(30 * dp, half * 0.47f - 8 * dp)
                ui.text(c, label, sx + 10 * dp, baseline,
                    ui.fit(label, 11f, available, 7f), Ui.DIM)
                val col = when (directions[side][field]) {
                    1 -> Palette.active.good
                    -1 -> Palette.active.bad
                    else -> Ui.TEXT
                }
                ui.textO(c, text, sx + half - 9 * dp, baseline,
                    ui.fit(text, 13f, half * 0.51f - 8 * dp, 8f),
                    col, Paint.Align.RIGHT)
            }
        }
    }

    /** Mnożnik obrażeń: wiersz atakuje rolę wskazaną w kolumnie. */
    private fun drawCounters(c: Canvas, top: Float, bottom: Float) {
        if (top > bottom || top + COUNTER_HEIGHT * dp < y + CONTROL_HEIGHT * dp) return
        rect.set(x, top, x + width, top + COUNTER_HEIGHT * dp)
        ui.card(c, rect, PANEL_COLOR, 11f, Ui.OUTLINE)
        ui.textO(c, matrixHeading, x + 12 * dp, top + 25 * dp, 16f, Ui.GOLD)
        ui.text(c, matrixAxis, x + 12 * dp, top + 47 * dp,
            ui.fit(matrixAxis, 11f, width - 24 * dp, 8f), Ui.DIM)
        val l = x + 8 * dp
        val colWidth = (width - 16 * dp) / 5f
        val rowHeight = 32 * dp
        val tableTop = top + 59 * dp
        for (col in 0 until Role.entries.size) {
            val title = roleNames[col]
            val cx = l + (col + 1.5f) * colWidth
            ui.textO(c, title, cx, tableTop + 20 * dp,
                ui.fit(title, 11f, colWidth - 5 * dp, 7f),
                Ui.TEXT, Paint.Align.CENTER)
        }
        for (row in 0 until Role.entries.size) {
            val ry = tableTop + (row + 1) * rowHeight
            val title = roleNames[row]
            ui.textO(c, title, l + colWidth / 2f, ry + 20 * dp,
                ui.fit(title, 11f, colWidth - 5 * dp, 7f),
                Ui.TEXT, Paint.Align.CENTER)
            for (col in 0 until Role.entries.size) {
                val m = CodexCompareModel.counter(Role.entries[row], Role.entries[col])
                val cellX = l + (col + 1) * colWidth
                val boosted = m > 1f
                paint.color = if (boosted) Ui.blend(Palette.active.good, PANEL_COLOR, 0.68f)
                    else Color.rgb(35, 43, 61)
                c.drawRoundRect(cellX + 2 * dp, ry + 2 * dp,
                    cellX + colWidth - 2 * dp, ry + rowHeight - 2 * dp,
                    5 * dp, 5 * dp, paint)
                val label = matrix[row][col]
                ui.textO(c, label, cellX + colWidth / 2f, ry + 22 * dp,
                    ui.fit(label, 12.5f, colWidth - 6 * dp, 8f),
                    if (boosted) Palette.active.good else Ui.DIM, Paint.Align.CENTER)
            }
        }
    }

    /** Słupki w skali liniowej, z dokładnym DPS nad każdym słupkiem. */
    private fun drawGrowth(c: Canvas, top: Float, bottom: Float) {
        if (top > bottom || top + CHART_HEIGHT * dp < y + CONTROL_HEIGHT * dp) return
        rect.set(x, top, x + width, top + CHART_HEIGHT * dp)
        ui.card(c, rect, PANEL_COLOR, 11f, Ui.OUTLINE)
        ui.textO(c, chartHeading, x + 12 * dp, top + 25 * dp,
            ui.fit(chartHeading, 16f, width - 24 * dp, 9f), Ui.GOLD)
        ui.text(c, chartAxis, x + 12 * dp, top + 47 * dp,
            ui.fit(chartAxis, 11f, width - 24 * dp, 8f), Ui.DIM)
        val baseline = top + 187 * dp
        val left = x + 12 * dp
        val slot = (width - 24 * dp) / Defs.ERA_COUNT
        val maxHeight = 105 * dp
        paint.color = Ui.DIM
        c.drawRect(left, baseline, left + slot * Defs.ERA_COUNT, baseline + dp, paint)
        for (era in 0 until Defs.ERA_COUNT) {
            val cx = left + (era + 0.5f) * slot
            val barHeight = max(2 * dp, growthDps[era] / growthMax * maxHeight)
            paint.color = if (era == eras[0]) Palette.active.good else Ui.XP
            c.drawRoundRect(cx - slot * 0.3f, baseline - barHeight,
                cx + slot * 0.3f, baseline, 3 * dp, 3 * dp, paint)
            val value = growthLabels[era]
            ui.text(c, value, cx, baseline - barHeight - 6 * dp,
                ui.fit(value, 10f, slot - 3 * dp, 7f),
                if (era == eras[0]) Palette.active.good else Ui.TEXT,
                Paint.Align.CENTER)
            ui.textO(c, growthEraLabels[era], cx, baseline + 18 * dp,
                10f, Ui.DIM, Paint.Align.CENTER)
        }
    }
}
