package com.centurywar.screens

import android.graphics.Canvas
import android.graphics.Color
import com.centurywar.engine.Button
import com.centurywar.engine.Icons
import com.centurywar.engine.L
import com.centurywar.engine.Screen
import com.centurywar.engine.Ui
import kotlin.math.min

/**
 * Cztery zakładki Rekordów. Zmiana ekranu zachowuje stos nawigacji,
 * więc Wstecz zawsze wraca do miejsca, z którego otwarto Rekordy.
 */
internal class RecordsTabs(private val owner: Screen, private val active: Int) {
    private companion object {
        /** Dzień 36: najmniejszy podpis zakładki na zewnętrznym Foldzie. */
        const val MIN_LABEL_SP = 10f
    }
    private val chrome = Chrome(owner)
    private val keys = arrayOf("rankings", "stats", "replay_list", "achievements")
    private val symbols = intArrayOf(Icons.TROPHY, Icons.CHART, Icons.PLAY, Icons.CROWN)
    private val tabs = Array(4) { owner.btn(200 + it) }

    /**
     * Dzień 36 (R12, poz. 8): na wąskim pionowym ekranie (zewnętrzny Fold) cztery zakładki po
     * ok. 73 dp miały podpis pod ikoną 7–9 sp (przy tekście 130% prawie wszędzie, po niemiecku
     * i angielsku już przy 100%) albo samą ikonę. Gdy którykolwiek podpis nie mieści się tam
     * w 10 sp, zakładki stoją w siatce 2 × 2 bez ikon, z pełną nazwą (≥ 10 sp). Telefony,
     * rozłożony Fold i języki, w których podpisy się mieszczą, zachowują jeden rząd.
     */
    internal var grid = false
        private set

    val contentTop: Float get() = chrome.top + (if (grid) 116 else 62) * owner.dp

    fun layout() {
        val d = owner.dp
        val physicalDp = (owner.w - owner.g.insetL - owner.g.insetR) / owner.g.density
        val width = min((if (physicalDp >= 960f) 620 else 950) * d,
            owner.w - owner.g.insetL - owner.g.insetR - 24 * d)
        val left = (owner.w + owner.g.insetL - owner.g.insetR - width) / 2f
        val gap = 6 * d
        val tw = (width - gap * 3) / tabs.size
        val narrow = owner.w - owner.g.insetL - owner.g.insetR < 420 * d && owner.h > owner.w
        grid = false
        // Podpis pod ikoną ma szerokość przycisku minus 8 dp (Ui.button, ikona wyśrodkowana).
        if (narrow) for (key in keys) if (owner.ui.measure(L[key], MIN_LABEL_SP, true) > tw - 8 * d) grid = true
        val cellW = (width - gap) / 2f
        for (i in tabs.indices) {
            val b = tabs[i]
            val name = L[keys[i]]
            b.tooltipTitle = name
            b.tooltipBody = ""
            b.selected = i == active
            b.color = if (i == active) Color.rgb(84, 104, 140) else Ui.BTN
            if (grid) {
                b.label = name
                b.iconId = -1
                b.set(left + (i % 2) * (cellW + gap), chrome.top + (4 + (i / 2) * 54) * d, cellW, 48 * d)
            } else {
                b.label = chrome.tabLabel(name, name, tw, 48 * d, 12f)
                b.iconId = symbols[i]
                b.set(left + i * (tw + gap), chrome.top + 4 * d, tw, 48 * d)
            }
        }
    }

    fun draw(c: Canvas) {
        for (b in tabs) owner.ui.button(c, b, 12f)
    }

    fun onClick(b: Button): Boolean {
        if (b.id !in 200..203) return false
        val target = b.id - 200
        if (target == active) return true
        val g = owner.g
        g.replace(when (target) {
            0 -> RankingsScreen(g)
            1 -> StatsScreen(g)
            2 -> ReplayListScreen(g)
            else -> AchievementsScreen(g)
        })
        return true
    }
}
