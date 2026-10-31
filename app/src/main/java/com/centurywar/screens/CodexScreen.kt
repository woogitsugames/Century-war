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
import com.centurywar.game.Battle
import com.centurywar.game.BattleConfig
import com.centurywar.game.Body
import com.centurywar.game.BattleRenderer
import com.centurywar.game.Bonuses
import com.centurywar.game.Defs
import com.centurywar.game.EraBranches
import com.centurywar.game.Mode
import com.centurywar.game.Role
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Kodeks: encyklopedia epok. Dla wybranej epoki pokazuje 4 jednostki (podgląd,
 * rola, opis, statystyki), wieżę i ostrzał specjalny oraz koszt awansu w XP.
 * Wszystkie liczby pochodzą z [Defs]; przełącznik "z badaniami" nakłada bonusy
 * z [com.centurywar.meta.Profile.bonuses] tak, jak robi to [Battle].
 *
 * Teksty i wartości liczymy w [rebuild] (zmiana epoki, przełącznika, rozmiaru),
 * więc samo rysowanie klatki nie alokuje pamięci.
 */
class CodexScreen(g: Game, startEra: Int = 0) : Screen(g) {
    private companion object {
        const val CARDS = 6          // 4 jednostki + wieża + ostrzał
        const val MAX_STATS = 9
        const val MAX_LINES = 3
        const val TURRET = 4
        const val BARRAGE = 5
        val ROLE_COL = intArrayOf(
            Color.rgb(170, 60, 50), Color.rgb(54, 128, 84), Color.rgb(60, 104, 170), Color.rgb(110, 76, 170),
            Color.rgb(170, 122, 36), Color.rgb(196, 84, 40),
        )
        val CARD_BODY = Color.rgb(44, 54, 78)
    }

    private val bg = MenuBg()
    private val chrome = Chrome(this)
    private val back = btn(-1)
    private val toggle = btn(-2)
    private val tabs = Array(Defs.ERA_COUNT) { btn(100 + it) }
    private val branchTabs = Array(2) { btn(200 + it) }

    /** Atrapa bitwy tylko po to, by użyć proceduralnych sylwetek jednostek z renderera. */
    private val renderer = BattleRenderer(g, Battle(BattleConfig(Mode.SKIRMISH, 1L, 1f)))

    private var era = startEra.coerceIn(0, Defs.ERA_COUNT - 1)
    private var branch = 0
    private var withResearch = false
    private var dt = 0f

    // układ
    private var left = 0f
    private var contentW = 0f
    private var viewTop = 0f
    private var cols = 2
    private var cw = 0f
    private var ch = 0f
    private var statCols = 3
    private var previewW = 0f
    private var previewH = 0f
    private var scroll = 0f
    private var maxScroll = 0f
    private val gap get() = 12 * dp
    private val headerH get() = 50 * dp
    private val rowH get() = 21 * dp

    // dane kart (przeliczane w rebuild)
    private val titles = arrayOfNulls<String>(CARDS)
    private val roles = arrayOfNulls<String>(CARDS)
    private val roleDefs = Role.values()
    private val lines = Array(CARDS) { arrayOfNulls<String>(MAX_LINES) }
    private val lineCount = IntArray(CARDS)
    private val statN = IntArray(CARDS)
    private val statIcon = Array(CARDS) { IntArray(MAX_STATS) }
    private val statLabel = Array(CARDS) { arrayOfNulls<String>(MAX_STATS) }
    private val statVal = Array(CARDS) { arrayOfNulls<String>(MAX_STATS) }
    private val statUp = Array(CARDS) { BooleanArray(MAX_STATS) }
    private var eraName = ""
    private var eraNum = ""
    private var evolveText = ""
    private var eliteTag = ""
    private var eliteLocked = false

    private val card = RectF()
    private val box = RectF()
    private val pf = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun isDragScreen() = true

    override fun layout() {
        chrome.layoutBack(back)
        toggle.iconId = Icons.FLASK
        val tw = min(210 * dp, w * 0.24f)
        toggle.set(w - g.insetR - 12 * dp - tw, g.insetT + 10 * dp, tw, 44 * dp)

        left = g.insetL + 16 * dp
        contentW = w - g.insetL - g.insetR - 32 * dp

        // zakładki epok: jeden rząd, a gdy za wąsko, dwa rzędy po 4
        val tabGap = 8 * dp
        val perRow = if ((contentW - 7 * tabGap) / 8 < 66 * dp) 4 else 8
        val tabW = (contentW - (perRow - 1) * tabGap) / perRow
        val tabH = 40 * dp
        val tabTop = chrome.top + 2 * dp
        for (i in tabs.indices) {
            val b = tabs[i]
            b.label = L[Defs.eraKeys[i]]
            b.set(left + (i % perRow) * (tabW + tabGap), tabTop + (i / perRow) * (tabH + tabGap), tabW, tabH)
        }
        val rows = (tabs.size + perRow - 1) / perRow
        viewTop = tabTop + rows * tabH + (rows - 1) * tabGap + 10 * dp
        val branchAvailable = EraBranches.available(era)
        val branchWidth = min(180 * dp, (contentW - 10 * dp) / 2)
        for (i in 0..1) {
            val b = branchTabs[i]
            b.visible = branchAvailable
            if (branchAvailable) {
                b.set(left + i * (branchWidth + 10 * dp), viewTop, branchWidth, 38 * dp)
            }
        }
        if (branchAvailable) viewTop += 48 * dp

        cols = (contentW / (280 * dp)).toInt().coerceIn(1, 3)
        cw = (contentW - (cols - 1) * gap) / cols
        statCols = if (cw >= 340 * dp) 3 else 2
        previewW = min(128 * dp, cw * 0.34f)
        previewH = 112 * dp
        val unitRows = (MAX_STATS + statCols - 1) / statCols
        ch = 10 * dp + previewH + 10 * dp + unitRows * rowH + 12 * dp
        val gridRows = (CARDS + cols - 1) / cols
        val contentH = headerH + gridRows * (ch + gap)
        maxScroll = max(0f, contentH - (h - g.insetB - viewTop) + 8 * dp)
        scroll = scroll.coerceIn(0f, maxScroll)
        rebuild()
    }

    /** Przelicza wszystkie teksty i wartości (bez tego render nie alokuje). */
    private fun rebuild() {
        val b = if (withResearch) g.profile.bonuses() else Bonuses()
        val e = Defs.eras[era]
        val v = e.variants.getOrNull(branch)
        eraName = if (v == null) L[e.nameKey] else L[e.nameKey] + " · " + L[v.nameKey]
        eraNum = L.f("cx_era_n", era + 1, Defs.ERA_COUNT)
        evolveText = if (e.evolveXp == Int.MAX_VALUE) L["cx_final"] else L.f("cx_evolve", num(e.evolveXp.toFloat()))
        eliteLocked = !g.profile.bonuses().eliteUnlocked
        eliteTag = L.f("cx_needs", L["r_elite"])

        val textW = cw - previewW - 3 * 12 * dp
        for (i in 0 until 4) {
            val u = v?.units?.get(i) ?: e.units[i]
            titles[i] = L[u.nameKey]
            roles[i] = L[when (u.role) { Role.MELEE -> "cx_melee"; Role.RANGED -> "cx_ranged"; Role.HEAVY -> "cx_heavy"; Role.ELITE -> "cx_elite" }]
            wrapInto(i, L[u.nameKey + "_fl"], textW)
            val hp = u.hp * b.unitHpMul
            val dmg = u.dmg * b.unitDmgMul
            var n = 0
            n = stat(i, n, Icons.COIN, "cx_cost", num(u.cost.toFloat()), false)
            n = stat(i, n, Icons.HEART, "cx_hp", num(hp), b.unitHpMul != 1f)
            n = stat(i, n, Icons.SWORD, "cx_dmg", num(dmg), b.unitDmgMul != 1f)
            n = stat(i, n, Icons.FLAME, "cx_dps", num(dmg / u.cooldown), b.unitDmgMul != 1f)
            n = stat(i, n, Icons.CROSSHAIR, "cx_range", num(u.range), false)
            n = stat(i, n, Icons.SPEED, "cx_speed", num(u.speed), false)
            n = stat(i, n, Icons.STOPWATCH, "cx_train", sec(u.trainTime / b.trainMul), b.trainMul != 1f)
            n = stat(i, n, Icons.BOMB, "cx_splash", if (u.splash > 0f) num(u.splash) else "—", false)
            n = stat(i, n, Icons.WALL, "cx_siege", if (u.siegeMul != 1f) "×" + dec(u.siegeMul) else "—", false)
            statN[i] = n
        }

        val t = v?.turret ?: e.turret
        titles[TURRET] = L[t.nameKey]
        roles[TURRET] = L["cx_turret"]
        wrapInto(TURRET, L["cx_turret_d"], textW)
        val tdmg = t.dmg * b.turretDmgMul
        val tUp = b.turretDmgMul != 1f
        var n = 0
        n = stat(TURRET, n, Icons.COIN, "cx_cost", num(t.cost.toFloat()), false)
        n = stat(TURRET, n, Icons.SWORD, "cx_dmg", num(tdmg), tUp)
        n = stat(TURRET, n, Icons.FLAME, "cx_dps", num(tdmg / t.cooldown), tUp)
        n = stat(TURRET, n, Icons.CROSSHAIR, "cx_range", num(t.range), false)
        n = stat(TURRET, n, Icons.HOURGLASS, "cx_reload", sec(t.cooldown), false)
        n = stat(TURRET, n, Icons.BOMB, "cx_splash", if (t.splash > 0f) num(t.splash) else "—", false)
        statN[TURRET] = n

        titles[BARRAGE] = L[v?.abilityKey ?: e.abilityKey]
        roles[BARRAGE] = L["cx_barrage"]
        wrapInto(BARRAGE, L["cx_barrage_d"], textW)
        n = 0
        n = stat(BARRAGE, n, Icons.METEOR, "cx_hit", num(Defs.abilityDmg(era) * b.unitDmgMul), b.unitDmgMul != 1f)
        n = stat(BARRAGE, n, Icons.HOURGLASS, "cx_cd", sec(Battle.BARRAGE_CD * b.abilityCdMul), b.abilityCdMul != 1f)
        statN[BARRAGE] = n

        for (i in branchTabs.indices) {
            val tb = branchTabs[i]
            tb.visible = v != null
            if (v != null) {
                tb.label = L[e.variants[i].nameKey]
                tb.selected = i == branch
                tb.color = if (i == branch) Ui.ERA[era] else Ui.BTN
            }
        }
        for (i in tabs.indices) {
            val tb = tabs[i]
            tb.selected = i == era
            tb.color = if (i == era) Ui.ERA[i] else Ui.blend(Ui.ERA[i], Ui.BTN, 0.55f)
        }
        toggle.label = L["cx_research"]
        toggle.sub = if (withResearch) L["on"] else L["off"]
        toggle.highlight = withResearch
    }

    private fun stat(card: Int, n: Int, icon: Int, key: String, value: String, up: Boolean): Int {
        statIcon[card][n] = icon
        statLabel[card][n] = L[key]
        statVal[card][n] = value
        statUp[card][n] = up
        return n + 1
    }

    /** Zawija opis do [width] w maks. [MAX_LINES] wiersze (poza pętlą rysowania). */
    private fun wrapInto(card: Int, s: String, width: Float) {
        val out = lines[card]
        var n = 0
        val sb = StringBuilder()
        for (word in s.split(' ')) {
            val trial = if (sb.isEmpty()) word else "$sb $word"
            if (sb.isNotEmpty() && ui.measure(trial, 12.5f) > width && n < MAX_LINES - 1) {
                out[n++] = sb.toString()
                sb.setLength(0); sb.append(word)
            } else {
                sb.setLength(0); sb.append(trial)
            }
        }
        if (sb.isNotEmpty()) out[n++] = sb.toString()
        lineCount[card] = n
    }

    private fun num(v: Float): String {
        val a = abs(v)
        return when {
            a >= 10000f -> String.format(Locale.ROOT, "%,d", v.roundToInt()).replace(',', ' ')
            a >= 100f -> v.roundToInt().toString()
            else -> dec(v)
        }
    }

    private fun dec(v: Float): String {
        val r = (v * 10f).roundToInt()
        return if (r % 10 == 0) (r / 10).toString() else String.format(Locale.ROOT, "%.1f", r / 10f)
    }

    private fun sec(v: Float) = dec(v) + " s"

    override fun update(dt: Float) { this.dt = dt }

    override fun onDrag(dx: Float, dy: Float) { scroll = (scroll - dy).coerceIn(0f, maxScroll) }

    override fun render(c: Canvas) {
        bg.draw(c, w, h, dt)
        chrome.dim(c, 130)
        for (i in tabs.indices) ui.button(c, tabs[i], 14f)
        for (i in branchTabs.indices) if (branchTabs[i].visible) ui.button(c, branchTabs[i], 15f)

        c.save()
        c.clipRect(0f, viewTop - 4 * dp, w, h)
        val y0 = viewTop - scroll
        drawEraHeader(c, y0)
        val gridTop = y0 + headerH
        for (i in 0 until CARDS) {
            val x = left + (i % cols) * (cw + gap)
            val y = gridTop + (i / cols) * (ch + gap)
            if (y > h || y + ch < viewTop - 10 * dp) continue
            card.set(x, y, x + cw, y + ch)
            drawCard(c, i)
        }
        c.restore()

        ui.button(c, back, 16f)
        ui.button(c, toggle, 15f)
        chrome.title(c, L["codex"])
    }

    private fun drawEraHeader(c: Canvas, y: Float) {
        val cy = y + headerH * 0.42f
        val col = Ui.ERA[era]
        ui.medallion(c, left + 18 * dp, cy, 18 * dp, col, Icons.CODEX)
        val tx = left + 44 * dp
        ui.textO(c, eraName, tx, cy + 2 * dp, 22f, Ui.GOLD)
        ui.textO(c, eraNum, tx + ui.measure(eraName, 22f, true) + 10 * dp, cy + 1 * dp, 13f, Ui.DIM)
        val right = left + contentW
        ui.badge(c, right, cy, Icons.XP, evolveText, 15f, Ui.XP, alignRight = true)
    }

    private fun drawCard(c: Canvas, i: Int) {
        val roleCol = ROLE_COL[i]
        ui.card(c, card, CARD_BODY, 14f, Ui.blend(roleCol, Color.WHITE, 0.25f))
        val pad = 10 * dp

        // podgląd
        box.set(card.left + pad + 2 * dp, card.top + pad + 2 * dp, card.left + pad + previewW, card.top + pad + previewH)
        drawPreview(c, i)

        // nazwa, rola, opis
        val tx = box.right + 12 * dp
        val tw = card.right - pad - 4 * dp - tx
        val title = titles[i] ?: ""
        ui.textO(c, title, tx, card.top + 32 * dp, ui.fit(title, 19f, tw), Ui.TEXT)
        val role = roles[i] ?: ""
        val rw = ui.measure(role, 11.5f, true) + 18 * dp
        box.set(tx, card.top + 40 * dp, tx + rw, card.top + 60 * dp)
        pf.color = Ui.OUTLINE
        c.drawRoundRect(box, 10 * dp, 10 * dp, pf)
        box.inset(1.6f * dp, 1.6f * dp)
        pf.color = roleCol
        c.drawRoundRect(box, 9 * dp, 9 * dp, pf)
        ui.textO(c, role, box.centerX(), box.centerY() + 4 * dp, 11.5f, Ui.TEXT, Paint.Align.CENTER, 1.6f)
        if (i == 3 && eliteLocked) {
            // kłódka na podglądzie + informacja obok plakietki roli
            val lx = box.right + 10 * dp
            val la = card.right - pad - lx - 18 * dp
            Icons.draw(c, Icons.LOCK, lx + 7 * dp, box.centerY(), 15 * dp, dp = dp)
            ui.textO(c, eliteTag, lx + 17 * dp, box.centerY() + 4 * dp, ui.fit(eliteTag, 11.5f, la, 7f), Ui.BAD, Paint.Align.LEFT, 1.6f)
        }
        val ln = lines[i]
        for (k in 0 until lineCount[i]) {
            ui.text(c, ln[k] ?: "", tx, card.top + 80 * dp + k * 16.5f * dp, 12.5f, Color.rgb(214, 218, 228))
        }

        // statystyki
        val st = card.top + pad + previewH + 10 * dp
        val n = statN[i]
        val rows = (n + statCols - 1) / statCols
        box.set(card.left + pad, st - 4 * dp, card.right - pad, st + rows * rowH + 2 * dp)
        pf.color = Color.argb(110, 10, 12, 22)
        c.drawRoundRect(box, 8 * dp, 8 * dp, pf)
        val cellW = (box.width() - 4 * dp) / statCols
        val icons = statIcon[i]
        val labels = statLabel[i]
        val values = statVal[i]
        val ups = statUp[i]
        for (k in 0 until n) {
            val cx = box.left + 2 * dp + (k % statCols) * cellW
            val cy = st + (k / statCols) * rowH + rowH * 0.5f
            if (k % statCols != 0) {
                pf.color = Color.argb(50, 255, 255, 255)
                c.drawRect(cx, cy - rowH * 0.36f, cx + 1 * dp, cy + rowH * 0.36f, pf)
            }
            Icons.draw(c, icons[k], cx + 12 * dp, cy, 15 * dp, dp = dp)
            val v = values[k] ?: ""
            val vSize = ui.fit(v, 13.5f, cellW * 0.55f)
            val vw = ui.measure(v, vSize, true)
            ui.textO(c, v, cx + cellW - 7 * dp, cy + 4.6f * dp, vSize, if (ups[k]) Ui.GOOD else Ui.TEXT, Paint.Align.RIGHT, 1.6f)
            val lab = labels[k] ?: ""
            val lAvail = cellW - 7 * dp - vw - 6 * dp - 23 * dp
            val lSize = min(11f, ui.fit(lab, 11f, lAvail, 7f))
            ui.text(c, lab, cx + 23 * dp, cy + 4 * dp, lSize, Ui.DIM)
        }
    }

    private fun drawPreview(c: Canvas, i: Int) {
        val col = Ui.ERA[era]
        val rad = 10 * dp
        pf.color = Ui.OUTLINE
        c.drawRoundRect(box.left - 2 * dp, box.top - 2 * dp, box.right + 2 * dp, box.bottom + 2 * dp, rad + 2 * dp, rad + 2 * dp, pf)
        pf.color = Ui.blend(col, Color.rgb(16, 20, 34), 0.45f)
        c.drawRoundRect(box, rad, rad, pf)
        val groundY = box.bottom - box.height() * 0.2f
        c.save()
        c.clipRect(box)
        pf.color = Color.argb(40, 255, 255, 255)
        c.drawCircle(box.centerX(), box.top + box.height() * 0.38f, box.height() * 0.32f, pf)
        pf.color = Ui.blend(col, Color.rgb(20, 26, 20), 0.62f)
        c.drawRect(box.left, groundY, box.right, box.bottom, pf)
        pf.color = Color.argb(70, 0, 0, 0)
        c.drawOval(box.centerX() - box.width() * 0.32f, groundY - 4 * dp, box.centerX() + box.width() * 0.32f, groundY + 5 * dp, pf)
        when (i) {
            TURRET -> ui.medallion(c, box.centerX(), box.top + box.height() * 0.43f, box.height() * 0.33f, col, Icons.TOWER)
            BARRAGE -> ui.medallion(c, box.centerX(), box.top + box.height() * 0.43f, box.height() * 0.33f, ROLE_COL[BARRAGE], Icons.METEOR)
            else -> {
                val def = Defs.unit(era, roleDefs[i], if (EraBranches.available(era)) branch else EraBranches.NONE)
                val k = when (def.body) {
                    Body.HUMAN -> 0.8f
                    Body.MECH -> 0.7f
                    Body.BEAST -> 0.56f
                    Body.SIEGE -> 0.6f
                    Body.CANNON -> 0.42f
                    else -> 0.42f
                }
                renderer.drawUnitIcon(c, def, box.centerX(), groundY + 2 * dp, box.height() * k, Ui.PLAYER)
            }
        }
        c.restore()
    }

    override fun onClick(b: Button) {
        when {
            b.id == -1 -> g.pop()
            b.id == -2 -> { withResearch = !withResearch; rebuild() }
            b.id in 200..201 -> {
                if (EraBranches.available(era) && branch != b.id - 200) {
                    branch = b.id - 200; scroll = 0f; rebuild()
                }
            }
            b.id in 100 until 100 + Defs.ERA_COUNT -> {
                val e = b.id - 100
                if (e != era) { era = e; branch = 0; scroll = 0f; layout() }
            }
        }
    }
}
