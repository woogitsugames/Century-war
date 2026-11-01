package com.centurywar.screens

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.centurywar.engine.Button
import com.centurywar.engine.Game
import com.centurywar.engine.Icons
import com.centurywar.engine.L
import com.centurywar.engine.Screen
import com.centurywar.engine.Ui
import com.centurywar.meta.ReplayStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Lista metadanych odczytywana na osobnym wątku; bez odczytu dysku podczas renderowania. */
class ReplayListScreen(g: Game) : Screen(g) {
    override val crashName get() = "ReplayListScreen"
    override val blocksNotices: Boolean get() = true
    private data class Display(val entry: ReplayStore.Entry, val title: String, val detail: String)

    private val bg = MenuBg()
    private val chrome = Chrome(this)
    private val recordsTabs = RecordsTabs(this, 2)
    private val back = btn(-1)
    private val panel = RectF()
    private val row = RectF()
    private var entries: List<Display> = emptyList()
    private var loading = true
    private var offset = 0f
    private var dt = 0f

    init { refresh() }

    private fun refresh() {
        loading = true
        g.replays?.listAsync { list ->
            val formatter = SimpleDateFormat("dd.MM.yy HH:mm", Locale.ROOT)
            val labels = list.map { e ->
                val mode = L[when (e.meta.mode) {
                    com.centurywar.game.Mode.CAMPAIGN -> "campaign"
                    com.centurywar.game.Mode.ENDLESS -> "endless"
                    com.centurywar.game.Mode.DAILY -> "daily"
                    com.centurywar.game.Mode.SKIRMISH -> "skirmish"
                }]
                val suffix = if (e.meta.stage >= 0) " #${e.meta.stage + 1}" else
                    if (e.meta.wave > 0) " ${e.meta.wave}" else ""
                val origin = if (e.imported) L["replay_from_friend"] + " · " else ""
                Display(e, mode + suffix, origin + e.meta.nick + " · " +
                    formatter.format(Date(e.meta.dateMillis)) + " · " + e.meta.score)
            }
            g.post { entries = labels; loading = false; offset = offset.coerceIn(0f, maxOffset()) }
        } ?: run { loading = false }
    }

    override fun layout() {
        chrome.layoutBack(back)
        recordsTabs.layout()
        val pw = minOf(940 * dp, w - g.insetL - g.insetR - 24 * dp)
        panel.set((w - pw) / 2, recordsTabs.contentTop + 8 * dp,
            (w + pw) / 2, h - g.insetB - 12 * dp)
        offset = offset.coerceIn(0f, maxOffset())
    }

    private fun rowTop() = panel.top + 46 * dp
    private fun maxOffset(): Float = maxOf(0f, entries.size * 56 * dp -
        (panel.bottom - rowTop() - 14 * dp))

    override fun update(dt: Float) { this.dt = dt }

    override fun render(c: Canvas) {
        bg.draw(c, w, h, dt)
        chrome.dim(c, 120)
        ui.panel(c, panel)
        ui.button(c, back, 15f)
        recordsTabs.draw(c)
        chrome.title(c, L["records"])
        if (loading || entries.isEmpty()) {
            ui.textO(c, L[if (loading) "replay_loading" else "replay_empty"],
                panel.centerX(), panel.centerY(), 17f, Ui.DIM, Paint.Align.CENTER)
            drawHeldTooltip(c)
            return
        }
        val left = panel.left + 14 * dp
        val right = panel.right - 14 * dp
        val top = rowTop()
        c.save()
        c.clipRect(left, top, right, panel.bottom - 12 * dp)
        for (i in entries.indices) {
            val y = top + i * 56 * dp - offset
            if (y + 52 * dp < top || y > panel.bottom) continue
            val item = entries[i]
            row.set(left, y, right, y + 51 * dp)
            ui.card(c, row, if (i % 2 == 0) Color.rgb(48, 62, 82) else Color.rgb(38, 51, 70), 8f)
            val x = row.left + 14 * dp
            ui.textO(c, item.title, x, y + 22 * dp, 14f, Ui.TEXT)
            ui.text(c, item.detail, x, y + 40 * dp,
                ui.fit(item.detail, 11f, row.width() - 191 * dp), Ui.DIM)
            val pinsX = row.right - 137 * dp
            Icons.draw(c, Icons.FLAG, pinsX, y + 25 * dp, 24 * dp,
                if (item.entry.pinned) Ui.GOLD else Ui.DIM, dp = dp)
            val shareX = row.right - 83 * dp
            Icons.draw(c, Icons.GLOBE, shareX, y + 21 * dp, 23 * dp,
                if (item.entry.playable) Ui.TEXT else Ui.DIM, dp = dp)
            ui.text(c, L["replay_share"], shareX, y + 44 * dp,
                ui.fit(L["replay_share"], 9f, 68 * dp), Ui.DIM, Paint.Align.CENTER)
            Icons.draw(c, Icons.CLOSE, row.right - 29 * dp, y + 25 * dp,
                21 * dp, Ui.BAD, dp = dp)
            if (!item.entry.playable) {
                ui.text(c, L["replay_old_version"], row.right - 183 * dp,
                    y + 14 * dp, 10f, Ui.BAD, Paint.Align.RIGHT)
            }
        }
        c.restore()
        drawHeldTooltip(c)
    }

    override fun isDragScreen() = true

    override fun onDrag(dx: Float, dy: Float) {
        offset = (offset - dy).coerceIn(0f, maxOffset())
    }

    override fun onTap(x: Float, y: Float) {
        if (loading || x < panel.left || x > panel.right || y < rowTop() || y > panel.bottom) return
        val index = ((y - rowTop() + offset) / (56 * dp)).toInt()
        val item = entries.getOrNull(index) ?: return
        val id = item.entry.id
        val right = panel.right - 14 * dp
        when {
            x > right - 51 * dp -> { g.replays?.deleteAsync(id); refresh() }
            x > right - 108 * dp -> if (item.entry.playable) g.host.shareReplay(id)
                else g.showNotice(L["replay_old_version"], Icons.CLOSE)
            x > right - 165 * dp -> { g.replays?.pinAsync(id, !item.entry.pinned); refresh() }
            else -> g.push(ReplayScreen(g, id))
        }
    }

    override fun onClick(b: Button) {
        if (recordsTabs.onClick(b)) return
        if (b.id == -1) g.pop()
    }
    override fun onBack(): Boolean { g.pop(); return true }
}
