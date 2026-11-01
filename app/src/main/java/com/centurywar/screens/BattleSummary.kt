package com.centurywar.screens

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.centurywar.engine.L
import com.centurywar.engine.Ui
import com.centurywar.game.Battle
import com.centurywar.game.Replay

/**
 * Próbki poza symulacją: jeden zapis na sekundę czasu gry oraz stan końcowy.
 * Stały bufor kołowy obejmuje maksymalny czas powtórki (120 minut).
 * Nie wpływa na BattleSnapshot ani format Replay.
 */
internal class BattleSummarySamples {
    private companion object {
        const val STEPS_PER_SAMPLE = 60
        const val CAPACITY = Replay.MAX_STEPS / STEPS_PER_SAMPLE + 2
        const val MAX_EVOLUTIONS = 32
    }

    private val steps = IntArray(CAPACITY)
    private val armyPlayer = IntArray(CAPACITY)
    private val armyEnemy = IntArray(CAPACITY)
    private val hpPlayer = IntArray(CAPACITY)
    private val hpEnemy = IntArray(CAPACITY)
    private val goldPlayer = IntArray(CAPACITY)
    private val goldEnemy = IntArray(CAPACITY)
    private var start = 0
    var count = 0
        private set
    var revision = 0
        private set

    private var lastPlayerEra = -1
    private var lastEnemyEra = -1
    private val evolutionSteps = IntArray(MAX_EVOLUTIONS)
    private val evolutionSides = IntArray(MAX_EVOLUTIONS)
    var evolutionCount = 0
        private set

    var maxArmy = 1
        private set
    var maxHp = 1
        private set
    var maxGold = 1
        private set
    // Napisy formatowane leniwie w formatLabels() (z BattleSummaryChart.prepare),
    // a nie w write(), które działa co sekundę w update bitwy.
    var maxArmyText = "1"
        private set
    var maxHpText = "1"
        private set
    var maxGoldText = "1"
        private set
    var firstTimeText = "0:00"
        private set
    var lastTimeText = "0:00"
        private set
    var playerGoldText = "0"
        private set
    var enemyGoldText = "0"
        private set
    private var labelsRevision = 0

    private fun slot(index: Int): Int = (start + index) % CAPACITY
    fun stepAt(index: Int): Int = steps[slot(index)]
    fun evolutionStep(index: Int): Int = evolutionSteps[index]
    fun evolutionSide(index: Int): Int = evolutionSides[index]

    /** 0 = armia, 1 = HP bazy, 2 = złoto; 0 = gracz, 1 = przeciwnik. */
    fun valueAt(metric: Int, side: Int, index: Int): Int {
        val i = slot(index)
        return when (metric) {
            0 -> if (side == 0) armyPlayer[i] else armyEnemy[i]
            1 -> if (side == 0) hpPlayer[i] else hpEnemy[i]
            else -> if (side == 0) goldPlayer[i] else goldEnemy[i]
        }
    }

    private fun observeEvolutions(battle: Battle) {
        val p = battle.player.era
        val e = battle.enemy.era
        if (lastPlayerEra >= 0 && p > lastPlayerEra) addEvolution(battle.stepCount, 0)
        if (lastEnemyEra >= 0 && e > lastEnemyEra) addEvolution(battle.stepCount, 1)
        lastPlayerEra = p
        lastEnemyEra = e
    }

    private fun addEvolution(step: Int, side: Int) {
        if (evolutionCount >= MAX_EVOLUTIONS) return
        evolutionSteps[evolutionCount] = step
        evolutionSides[evolutionCount++] = side
        revision++
    }

    /** Wywołanie po każdym kroku symulacji; bez alokacji w pozostałych 59 krokach. */
    fun capture(battle: Battle) {
        observeEvolutions(battle)
        val step = battle.stepCount
        if (count == 0 || (step % STEPS_PER_SAMPLE == 0 && step > stepAt(count - 1))) {
            write(battle)
        }
    }

    /** Dodatkowy punkt końcowy pozwala pokazać wynik także krótszej bitwy. */
    fun finish(battle: Battle) {
        observeEvolutions(battle)
        if (count == 0 || battle.stepCount >= stepAt(count - 1)) write(battle)
    }

    private fun write(battle: Battle) {
        val step = battle.stepCount
        val i = if (count > 0 && step == stepAt(count - 1)) {
            slot(count - 1)
        } else {
            if (count == CAPACITY) start = (start + 1) % CAPACITY
            else count++
            slot(count - 1)
        }
        steps[i] = step
        armyPlayer[i] = battle.player.aliveCount
        armyEnemy[i] = battle.enemy.aliveCount
        hpPlayer[i] = battle.player.baseHp.toInt().coerceAtLeast(0)
        hpEnemy[i] = battle.enemy.baseHp.toInt().coerceAtLeast(0)
        goldPlayer[i] = battle.player.gold.toInt().coerceAtLeast(0)
        goldEnemy[i] = battle.enemy.gold.toInt().coerceAtLeast(0)
        maxArmy = maxOf(maxArmy, battle.player.cap, battle.enemy.cap, armyPlayer[i], armyEnemy[i])
        maxHp = maxOf(maxHp, battle.player.baseMaxHp.toInt(), battle.enemy.baseMaxHp.toInt(),
            hpPlayer[i], hpEnemy[i])
        maxGold = maxOf(maxGold, goldPlayer[i], goldEnemy[i])
        revision++
    }

    /** Odświeża napisy osi tylko po zmianie próbek; wołane poza pętlą bitwy. */
    fun formatLabels() {
        if (labelsRevision == revision) return
        labelsRevision = revision
        maxArmyText = maxArmy.toString()
        maxHpText = maxHp.toString()
        maxGoldText = maxGold.toString()
        if (count > 0) {
            val last = slot(count - 1)
            playerGoldText = goldPlayer[last].toString()
            enemyGoldText = goldEnemy[last].toString()
            firstTimeText = clock(stepAt(0))
            lastTimeText = clock(steps[last])
        }
    }

    private fun clock(step: Int): String {
        val seconds = step / STEPS_PER_SAMPLE
        val remainder = seconds % 60
        return (seconds / 60).toString() + ":" + if (remainder < 10) "0$remainder" else remainder.toString()
    }
}

/** Wspólny wykres wyników i powtórek; wszystkie ścieżki powstają poza render(). */
internal class BattleSummaryChart {
    private val paths = Array(6) { Path() }
    private val top = FloatArray(3)
    private val bottom = FloatArray(3)
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private var left = 0f
    private var right = 0f
    private var dp = 1f
    private var metricCount = 0
    private var cachedRevision = -1
    private var cachedLeft = Float.NaN
    private var cachedTop = Float.NaN
    private var cachedRight = Float.NaN
    private var cachedBottom = Float.NaN
    private var cachedDp = Float.NaN

    private var labelPlayer = ""
    private var labelEnemy = ""
    private var labelEvolution = ""
    private var labelGold = ""
    private val metricLabels = Array(3) { "" }

    init { refreshLanguage() }

    fun refreshLanguage() {
        labelPlayer = L["bs104_player"]
        labelEnemy = L["bs104_enemy"]
        labelEvolution = L["bs104_evolution"]
        labelGold = L["bs104_gold"]
        metricLabels[0] = L["bs104_army"]
        metricLabels[1] = L["bs104_hp"]
        metricLabels[2] = L["bs104_gold"]
    }

    /** Przeliczane przy zakończeniu bitwy, zmianie wymiarów albo dopisaniu próbek. */
    fun prepare(samples: BattleSummarySamples, area: RectF, scale: Float) {
        samples.formatLabels()
        if (cachedRevision == samples.revision && cachedLeft == area.left && cachedTop == area.top &&
            cachedRight == area.right && cachedBottom == area.bottom && cachedDp == scale) return
        cachedRevision = samples.revision
        cachedLeft = area.left; cachedTop = area.top
        cachedRight = area.right; cachedBottom = area.bottom; cachedDp = scale
        dp = scale
        left = area.left + 12f * dp
        right = area.right - 12f * dp
        val contentTop = area.top + 24f * dp
        val contentBottom = area.bottom - 24f * dp
        metricCount = if (area.height() >= 240f * dp) 3 else 2
        val section = (contentBottom - contentTop) / metricCount
        val first = if (samples.count > 0) samples.stepAt(0) else 0
        val last = if (samples.count > 0) samples.stepAt(samples.count - 1) else first
        val span = (last - first).coerceAtLeast(1)
        val maxSegments = ((right - left) / (2.5f * dp)).toInt().coerceAtLeast(1)
        val stride = maxOf(1, (samples.count - 1) / maxSegments)
        for (metric in 0..2) {
            top[metric] = contentTop + metric * section + 17f * dp
            bottom[metric] = contentTop + (metric + 1) * section - 4f * dp
            val height = (bottom[metric] - top[metric]).coerceAtLeast(1f)
            val peak = when (metric) { 0 -> samples.maxArmy; 1 -> samples.maxHp; else -> samples.maxGold }
            for (side in 0..1) {
                val path = paths[metric * 2 + side]
                path.rewind()
                if (samples.count == 0) continue
                var index = 0
                var x = left
                var y = bottom[metric] - samples.valueAt(metric, side, 0).toFloat() / peak * height
                path.moveTo(x, y)
                while (index + stride < samples.count) {
                    index += stride
                    x = left + (samples.stepAt(index) - first).toFloat() / span * (right - left)
                    y = bottom[metric] - samples.valueAt(metric, side, index).toFloat() / peak * height
                    path.lineTo(x, y)
                }
                if (index != samples.count - 1) {
                    index = samples.count - 1
                    x = left + (samples.stepAt(index) - first).toFloat() / span * (right - left)
                    y = bottom[metric] - samples.valueAt(metric, side, index).toFloat() / peak * height
                    path.lineTo(x, y)
                }
            }
        }
    }

    /** Nie przydziela obiektów ani nie formatuje napisów podczas renderowania. */
    fun draw(c: Canvas, ui: Ui, samples: BattleSummarySamples, area: RectF) {
        if (metricCount == 0 || samples.count == 0) return
        ui.textO(c, labelPlayer, left, area.top + 13f * dp, 11f, Ui.PLAYER, Paint.Align.LEFT)
        ui.textO(c, labelEnemy, left + (right - left) * 0.33f, area.top + 13f * dp,
            11f, Ui.ENEMY, Paint.Align.LEFT)
        ui.textO(c, labelEvolution, right, area.top + 13f * dp, 10f, Ui.GOLD, Paint.Align.RIGHT)
        for (metric in 0 until metricCount) {
            val headerY = top[metric] - 5f * dp
            val maxText = when (metric) {
                0 -> samples.maxArmyText
                1 -> samples.maxHpText
                else -> samples.maxGoldText
            }
            ui.textO(c, metricLabels[metric], left, headerY, 10f, Ui.TEXT, Paint.Align.LEFT)
            ui.textO(c, maxText, right, headerY, 10f, Ui.DIM, Paint.Align.RIGHT)
            ui.fill.color = Color.argb(85, 120, 132, 158)
            c.drawRect(left, bottom[metric], right, bottom[metric] + dp, ui.fill)
            ui.fill.color = Color.argb(30, 120, 132, 158)
            val halfway = (top[metric] + bottom[metric]) * 0.5f
            c.drawRect(left, halfway, right, halfway + dp, ui.fill)
            for (side in 0..1) {
                linePaint.color = if (side == 0) Ui.PLAYER else Ui.ENEMY
                linePaint.strokeWidth = 2.1f * dp
                c.drawPath(paths[metric * 2 + side], linePaint)
            }
        }
        val first = samples.stepAt(0)
        val last = samples.stepAt(samples.count - 1)
        val span = (last - first).coerceAtLeast(1)
        linePaint.strokeWidth = 1.1f * dp
        for (i in 0 until samples.evolutionCount) {
            val step = samples.evolutionStep(i)
            if (step < first || step > last) continue
            val x = left + (step - first).toFloat() / span * (right - left)
            linePaint.color = if (samples.evolutionSide(i) == 0) Ui.GOLD else Ui.ENEMY
            c.drawLine(x, top[0], x, bottom[0], linePaint)
            ui.fill.color = linePaint.color
            c.drawCircle(x, top[0], 2.6f * dp, ui.fill)
        }
        val footer = area.bottom - 5f * dp
        ui.textO(c, samples.firstTimeText, left, footer, 10f, Ui.DIM, Paint.Align.LEFT)
        ui.textO(c, samples.lastTimeText, right, footer, 10f, Ui.DIM, Paint.Align.RIGHT)
        if (metricCount == 2) {
            val mid = (left + right) * 0.5f
            ui.textO(c, labelGold, mid - 22f * dp, footer, 10f, Ui.GOLD, Paint.Align.RIGHT)
            ui.textO(c, samples.playerGoldText, mid, footer, 10f, Ui.PLAYER, Paint.Align.RIGHT)
            ui.textO(c, samples.enemyGoldText, mid + 7f * dp, footer, 10f, Ui.ENEMY, Paint.Align.LEFT)
        }
    }
}
