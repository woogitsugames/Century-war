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
    private val chrome = Chrome(owner)
    private val keys = arrayOf("rankings", "stats", "replay_list", "achievements")
    private val symbols = intArrayOf(Icons.TROPHY, Icons.CHART, Icons.PLAY, Icons.CROWN)
    private val tabs = Array(4) { owner.btn(200 + it) }

    val contentTop: Float get() = chrome.top + 62 * owner.dp

    fun layout() {
        val d = owner.dp
        val physicalDp = (owner.w - owner.g.insetL - owner.g.insetR) / owner.g.density
        val width = min((if (physicalDp >= 960f) 620 else 950) * d,
            owner.w - owner.g.insetL - owner.g.insetR - 24 * d)
        val left = (owner.w + owner.g.insetL - owner.g.insetR - width) / 2f
        val gap = 6 * d
        val tw = (width - gap * 3) / tabs.size
        for (i in tabs.indices) {
            val b = tabs[i]
            val name = L[keys[i]]
            b.label = chrome.tabLabel(name, name, tw, 48 * d, 12f)
            b.iconId = symbols[i]
            b.tooltipTitle = name
            b.tooltipBody = ""
            b.selected = i == active
            b.color = if (i == active) Color.rgb(84, 104, 140) else Ui.BTN
            b.set(left + i * (tw + gap), chrome.top + 4 * d, tw, 48 * d)
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
