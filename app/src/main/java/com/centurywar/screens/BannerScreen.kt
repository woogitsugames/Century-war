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
import com.centurywar.game.art.Banners
import com.centurywar.meta.BannerCatalog
import kotlin.math.max
import kotlin.math.min

/** Wybór wyłącznie kosmetyczny; przewijana siatka działa też na wąskim Foldzie. */
class BannerScreen(g: Game) : Screen(g) {
    override val crashName get() = "BannerScreen"
    private val bg = MenuBg()
    private val chrome = Chrome(this)
    private val scrollCue = ScrollCue()
    private val art = Banners()
    private val back = btn(-1)
    private val equip = btn(-2)
    private val choices = Array(BannerCatalog.COUNT) { btn(it) }
    private val names = Array(BannerCatalog.COUNT) { "" }
    private val rules = Array(BannerCatalog.COUNT) { "" }
    private val panel = RectF()
    private val preview = RectF()
    private val tile = RectF()
    private var selected = g.profile.selectedBanner
    private var scroll = 0f
    private var maxScroll = 0f
    private var gridTop = 0f
    private var gridBottom = 0f
    private var columns = 3
    private var tileWidth = 0f
    private var tileHeight = 0f
    private var tileGap = 0f
    private var dt = 0f

    init {
        if (g.profile.refreshBanners()) g.profile.save()
        refreshLabels()
    }

    private fun refreshLabels() {
        for (i in names.indices) {
            names[i] = L["banner_name_$i"]
            rules[i] = L["banner_req_$i"]
        }
    }

    override fun onLanguageChanged() {
        refreshLabels()
        layout()
    }

    override fun layout() {
        chrome.layoutBack(back)
        val pw = min(1060 * dp, (w - g.insetL - g.insetR - 26 * dp).coerceAtLeast(180 * dp))
        panel.set((w + g.insetL - g.insetR - pw) / 2f, chrome.top + 8 * dp,
            (w + g.insetL - g.insetR + pw) / 2f, h - g.insetB - 10 * dp)
        val ph = panel.height().coerceAtLeast(100 * dp)
        val previewHeight = min(112 * dp, max(76 * dp, ph * 0.31f))
        preview.set(panel.left + 10 * dp, panel.top + 8 * dp,
            panel.right - 10 * dp, panel.top + previewHeight)
        equip.iconId = Icons.FLAG
        val equipWidth = min(200 * dp, (pw - 32 * dp).coerceAtLeast(90 * dp))
        equip.set(panel.centerX() - equipWidth / 2, panel.bottom - 54 * dp, equipWidth, 48 * dp)
        gridTop = preview.bottom + 8 * dp
        gridBottom = max(gridTop + 32 * dp, equip.r.top - 7 * dp)
        columns = when {
            pw >= 890 * dp -> 6
            pw >= 515 * dp -> 4
            else -> 3
        }
        tileGap = 7 * dp
        tileWidth = (pw - 26 * dp - (columns - 1) * tileGap) / columns
        tileHeight = min(91 * dp, max(66 * dp, tileWidth * 0.68f))
        val rows = (choices.size + columns - 1) / columns
        maxScroll = max(0f, rows * (tileHeight + tileGap) - tileGap - (gridBottom - gridTop))
        scroll = scroll.coerceIn(0f, maxScroll)
        for (i in choices.indices) {
            val col = i % columns
            val row = i / columns
            val x = panel.left + 13 * dp + col * (tileWidth + tileGap)
            val y = gridTop + row * (tileHeight + tileGap) - scroll
            choices[i].set(x, y, tileWidth, tileHeight)
            choices[i].visible = y < gridBottom && y + tileHeight > gridTop
        }
        updateButtons()
    }

    private fun updateButtons() {
        back.label = L["back"]
        equip.label = when {
            selected == g.profile.selectedBanner -> L["banner_equipped"]
            g.profile.bannerUnlocked(selected) -> L["banner_equip"]
            else -> L["banner_locked"]
        }
        equip.enabled = g.profile.bannerUnlocked(selected) && selected != g.profile.selectedBanner
        equip.color = if (equip.enabled) Ui.BTN_GREEN else Ui.BTN
    }

    override fun update(dt: Float) {
        this.dt = dt
        updateButtons()
    }

    override fun render(c: Canvas) {
        bg.draw(c, w, h, dt)
        chrome.dim(c, 120)
        ui.panel(c, panel)
        chrome.title(c, L["banner_title"])
        ui.card(c, preview, Color.rgb(30, 48, 76), 10f,
            if (selected == g.profile.selectedBanner) Ui.GOLD else 0)

        val bannerWidth = min(160 * dp, preview.width() * 0.30f)
        val bannerHeight = min(68 * dp, preview.height() - 16 * dp)
        art.draw(c, selected, preview.left + 17 * dp, preview.centerY() - bannerHeight / 2,
            bannerWidth, bannerHeight)
        val tx = preview.left + bannerWidth + 29 * dp
        val tw = (preview.right - tx - 8 * dp).coerceAtLeast(35 * dp)
        ui.textO(c, names[selected], tx, preview.top + 25 * dp,
            ui.fit(names[selected], 17f, tw, 10f), Ui.GOLD)
        val status = when {
            selected == g.profile.selectedBanner -> L["banner_equipped"]
            g.profile.bannerUnlocked(selected) -> L["banner_unlocked"]
            else -> L["banner_locked"]
        }
        ui.textO(c, status, tx, preview.top + 46 * dp,
            ui.fit(status, 12f, tw, 9f), Ui.TEXT)
        ui.textO(c, rules[selected], tx, preview.bottom - 12 * dp,
            ui.fit(rules[selected], 12f, tw, 8f), Ui.DIM)

        c.save()
        c.clipRect(panel.left + 6 * dp, gridTop, panel.right - 6 * dp, gridBottom)
        for (i in choices.indices) {
            val b = choices[i]
            if (!b.visible) continue
            tile.set(b.r)
            val unlocked = g.profile.bannerUnlocked(i)
            val picked = selected == i
            ui.card(c, tile,
                if (picked) Color.rgb(56, 87, 122) else Color.rgb(37, 53, 74),
                9f, if (picked) Ui.GOLD else 0)
            val fw = min(tile.width() * 0.55f, 71 * dp)
            val fh = min(tile.height() * 0.48f, 41 * dp)
            art.draw(c, i, tile.centerX() - fw / 2, tile.top + 7 * dp, fw, fh)
            if (!unlocked) {
                Icons.draw(c, Icons.LOCK, tile.right - 13 * dp,
                    tile.top + 13 * dp, 17 * dp, Ui.GOLD, dp = dp)
            }
            ui.textO(c, names[i], tile.centerX(), tile.bottom - 11 * dp,
                ui.fit(names[i], 11f, tile.width() - 10 * dp, 8f),
                if (unlocked) Ui.TEXT else Ui.DIM, Paint.Align.CENTER)
        }
        c.restore()
        scrollCue.vertical(c, panel.right - 6 * dp, gridTop, gridBottom,
            scroll, maxScroll, dp)
        ui.button(c, equip, 14f)
        ui.button(c, back, 16f)
        drawHeldTooltip(c)
    }

    override fun isDragScreen(): Boolean = true

    override fun onDrag(dx: Float, dy: Float) {
        if (maxScroll <= 0f) return
        scroll = (scroll - dy).coerceIn(0f, maxScroll)
        layout()
    }

    override fun onClick(b: Button) {
        when (b) {
            back -> g.pop()
            equip -> {
                if (g.profile.selectBanner(selected)) updateButtons()
            }
            else -> if (b.id in 0 until BannerCatalog.COUNT && b.visible) {
                selected = b.id
                updateButtons()
            }
        }
    }
}
