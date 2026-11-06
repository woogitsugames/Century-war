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
import com.centurywar.game.Defs
import com.centurywar.game.Modifier
import com.centurywar.game.OrderRules
import com.centurywar.game.Orders
import com.centurywar.game.Weather
import com.centurywar.meta.OnlineState
import com.centurywar.meta.Profile
import kotlin.math.max
import kotlin.math.min

/** Dzień 62: teksty Rozkazów sztabu wspólne dla listy, karty rozkazu i wyników bitwy. */
internal object OrderTexts {
    fun clock(seconds: Int): String = String.format(java.util.Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60)

    fun goal(order: Orders.Order): String = L.f(when (order.goal) {
        Orders.Goal.HOLD -> "o209_goal_hold"
        Orders.Goal.DESTROY -> "o209_goal_destroy"
        Orders.Goal.ESCORT -> "o211_goal_escort"
        Orders.Goal.SPEARHEAD -> "o211_goal_spear"
    }, clock(order.limitSeconds))

    /** Ikona celu na liście: tarcza (obrona), miecze (atak), hełm (eskorta dowódcy). */
    fun goalIcon(order: Orders.Order): Int = when (order.goal) {
        Orders.Goal.HOLD -> Icons.SHIELD
        Orders.Goal.DESTROY -> Icons.CROSSED_SWORDS
        Orders.Goal.ESCORT, Orders.Goal.SPEARHEAD -> Icons.HELMET
    }

    /** Podpis zablokowanego rozkazu: etap kampanii (partia 1) albo gwiazdki w rozkazie partii 1. */
    fun locked(order: Orders.Order): String {
        val pre = Orders.prerequisite(order) ?: return L.f("o209_locked", Orders.unlockStageNumber(order))
        return L.f("o211_locked", Orders.BATCH2_UNLOCK_STARS, L[pre.nameKey])
    }

    fun objective(o: Orders.Objective): String = when (o.kind) {
        Orders.ObjectiveKind.BASE_HP -> L.f("o209_obj_base", "${o.value}%")
        Orders.ObjectiveKind.WITHIN -> L.f("o209_obj_within", clock(o.value))
        Orders.ObjectiveKind.KILLS -> L.f("o209_obj_kills", o.value)
        Orders.ObjectiveKind.LOSSES -> L.f("o209_obj_losses", o.value)
        Orders.ObjectiveKind.COMMANDER_ALIVE -> L["o211_obj_commander"]
        Orders.ObjectiveKind.GOLD_LEFT -> L.f("o211_obj_gold", o.value)
        Orders.ObjectiveKind.FIELDED_MAX -> L.f("o211_obj_fielded", o.value)
    }

    private val KINDS = intArrayOf(OrderRules.MELEE, OrderRules.RANGED, OrderRules.HEAVY, OrderRules.ELITE,
        OrderRules.COMMANDER, OrderRules.TURRETS, OrderRules.BARRAGE, OrderRules.RALLY)
    private val KIND_KEYS = arrayOf("o209_k_melee", "o209_k_ranged", "o209_k_heavy", "o209_k_elite",
        "o209_k_commander", "o209_k_turrets", "o209_k_barrage", "o209_k_rally")

    /** „piechota, dowódca, ostrzał” albo „wszystkie” (bez ograniczeń). */
    fun allowed(mask: Int, unitsOnly: Boolean = false): String {
        if (mask == OrderRules.ALL) return L["o209_all"]
        val out = StringBuilder()
        for (i in KINDS.indices) {
            if (unitsOnly && i >= 4) break
            if (mask and KINDS[i] == 0) continue
            if (out.isNotEmpty()) out.append(", ")
            out.append(L[KIND_KEYS[i]])
        }
        return out.toString()
    }

    fun forces(order: Orders.Order): String = L.f("o209_forces", allowed(order.playerAllowed))

    fun enemy(order: Orders.Order): String =
        if (order.enemyAllowed == OrderRules.ALL) "" else L.f("o209_enemy", allowed(order.enemyAllowed, unitsOnly = true))

    /** Epoka (lub zakres), pogoda i modyfikatory w jednym wierszu. */
    fun conditions(order: Orders.Order): String {
        val out = StringBuilder(L[Defs.eraKeys[order.era]])
        if (order.maxEra > order.era) out.append(" → ").append(L[Defs.eraKeys[order.maxEra]])
        if (order.weather != Weather.CLEAR) out.append(" · ").append(L[order.weather.key])
        for (m in order.modifiers) out.append(" · ").append(L[m.key])
        if (order.incomePct != 100) out.append(" · ").append(L.f("o211_income", "${order.incomePct}%"))
        return out.toString()
    }
}

/**
 * Dzień 62: Rozkazy sztabu — lista rozkazów (dzień 66: 18, dwie partie po jednym na epokę) i karta wybranego rozkazu
 * z celem, ograniczeniami, warunkami i nagrodą. Jedna kolumna (telefon, zewnętrzny Fold),
 * przewijana; karta rozkazu też się przewija przy dużym tekście. Bez powiadomień na ekranie.
 */
class OrdersScreen(g: Game, initial: Int = -1) : Screen(g) {
    override val crashName get() = "OrdersScreen"
    override val blocksNotices: Boolean get() = true
    private companion object {
        const val CARD0 = 100
        const val PLAY = -2
        const val RANKING = -3
        val CARD_BODY = Color.rgb(36, 46, 66)
        val CARD_LOCKED = Color.rgb(32, 34, 44)
    }

    private val bg = MenuBg()
    private val chrome = Chrome(this)
    private val back = btn(-1)
    private val cards = Array(Orders.COUNT) { btn(CARD0 + it) }
    private val play = btn(PLAY)
    private val ranking = btn(RANKING)
    private val area = RectF()
    private val header = RectF()
    private val card = RectF()
    private val detail = RectF()
    private var dt = 0f
    private val cue = ScrollCue()

    /** Wybrany rozkaz (karta) albo −1 (lista). */
    internal var selected = -1
        private set
    private var scroll = 0f
    private var maxScroll = 0f
    private var cardH = 0f
    private var headerText = ""
    private val subs = Array(Orders.COUNT) { "" }

    // Karta rozkazu: wiersze (tekst, rozmiar, kolor, ikona) policzone w layout().
    private val lineText = ArrayList<String>()
    private val lineSize = ArrayList<Float>()
    private val lineColor = ArrayList<Int>()
    private val lineIcon = ArrayList<Int>()
    private val lineGap = ArrayList<Float>()
    private var detailScroll = 0f
    private var detailMax = 0f
    private var detailText = ""

    init {
        if (initial in 0 until Orders.COUNT && unlocked(initial)) selected = initial
    }

    internal val selectedForTests: Int get() = selected
    internal fun openForTests(i: Int) { selected = i; detailScroll = 0f; layout() }

    private fun unlocked(i: Int) = Orders.unlocked(Orders.all[i], g.profile.stars, g.profile.orderStars)

    override fun isDragScreen() = true

    override fun layout() {
        chrome.layoutBack(back)
        val pr = g.profile
        val s = ui.textScale
        val aw = min(640 * dp, w - g.insetL - g.insetR - 24 * dp)
        val left = (w + g.insetL - g.insetR - aw) / 2
        header.set(left, chrome.top + 6 * dp, left + aw, chrome.top + 6 * dp + max(40f, 34f * s) * dp)
        headerText = L.f("o209_stars", pr.orderStarTotal(), Orders.COUNT * Orders.MAX_STARS) + " · " +
            L.f("o209_points", pr.orderPointTotal())
        ranking.label = L["o209_ranking"]
        ranking.iconId = Icons.TROPHY
        ranking.visible = selected < 0 && g.online.available
        val rw = min(130 * dp, aw * 0.36f)
        ranking.set(header.right - rw, header.centerY() - 24 * dp, rw, 48 * dp)
        area.set(left, header.bottom + 8 * dp, left + aw, h - g.insetB - 10 * dp)
        cardH = max(68f, 60f * s) * dp
        for (i in cards.indices) {
            val o = Orders.all[i]
            val b = cards[i]
            b.label = L[o.nameKey]
            subs[i] = if (unlocked(i)) L[Defs.eraKeys[o.era]] + " · " + OrderTexts.goal(o)
                else OrderTexts.locked(o)
            // TalkBack: nazwa + podpis + gwiazdki, jak na innych listach.
            b.sub = subs[i] + if (unlocked(i)) ", " + L["stars"] + " " + pr.orderStars[i] + "/3" else ""
        }
        val content = Orders.COUNT * (cardH + 8 * dp)
        maxScroll = max(0f, content - area.height())
        scroll = scroll.coerceIn(0f, maxScroll)
        placeCards()
        layoutDetail()
    }

    private fun placeCards() {
        for (i in cards.indices) {
            val y = area.top - scroll + i * (cardH + 8 * dp)
            cards[i].set(area.left, y, area.width(), cardH)
            cards[i].visible = selected < 0 && cards[i].r.bottom > area.top && cards[i].r.top < area.bottom
        }
    }

    private fun addLine(text: String, size: Float, color: Int, icon: Int = -1, gap: Float = 4f) {
        lineText.add(text); lineSize.add(size); lineColor.add(color); lineIcon.add(icon); lineGap.add(gap)
    }

    private fun addWrapped(text: String, size: Float, color: Int, width: Float, icon: Int = -1, gap: Float = 4f) {
        if (text.isEmpty()) return
        val out = arrayOfNulls<String>(12)
        val n = HintFrame.wrapInto(ui, text, width, size, out)
        // Kolejne wiersze zawinięcia: wcięte tylko pod ikoną (-2), bez ikony od lewego brzegu.
        for (k in 0 until n) addLine(out[k] ?: "", size, color,
            if (k == 0 || icon == -1) icon else -2, if (k == 0) gap else 0f)
    }

    private fun layoutDetail() {
        play.visible = selected >= 0
        lineText.clear(); lineSize.clear(); lineColor.clear(); lineIcon.clear(); lineGap.clear()
        if (selected < 0) return
        val o = Orders.all[selected]
        val pr = g.profile
        detail.set(area.left, header.top, area.right, area.bottom)
        play.label = L["play"]
        play.iconId = Icons.PLAY
        play.highlight = true
        val pw = min(240 * dp, detail.width() - 40 * dp)
        play.set(detail.centerX() - pw / 2, detail.bottom - 24 * dp - 52 * dp, pw, 52 * dp)
        val tw = detail.width() - 40 * dp - 26 * dp
        addWrapped(OrderTexts.conditions(o), 13f, Ui.GOLD, tw + 26 * dp)
        addWrapped(L[o.briefKey], 14f, Ui.TEXT, tw + 26 * dp, gap = 8f)
        addLine(L["o209_main"], 13f, Ui.DIM, gap = 10f)
        addWrapped(OrderTexts.goal(o), 14f, Ui.TEXT, tw, Icons.FLAG)
        addLine(L["o209_bonus"], 13f, Ui.DIM, gap = 10f)
        for (obj in o.bonus) addWrapped(OrderTexts.objective(obj), 14f, Ui.TEXT, tw, Icons.STAR)
        addWrapped(OrderTexts.forces(o), 13f, Ui.TEXT, tw, Icons.HELMET, gap = 10f)
        addWrapped(OrderTexts.enemy(o), 13f, Ui.TEXT, tw, Icons.SKULL)
        val left = Orders.medalsFor(pr.orderStars[selected], Orders.MAX_STARS)
        addWrapped(if (left > 0) L.f("o209_reward", L.plural("medals_n", left.toLong())) else L["o209_reward_done"],
            13f, Ui.GOLD, tw, Icons.MEDAL, gap = 10f)
        detailText = accessibilitySentences(L[o.nameKey], *lineText.toTypedArray())
        var hgt = 0f
        for (i in lineText.indices) hgt += lineGap[i] * dp + lineSize[i] * dp * ui.textScale * 1.3f
        val top = detail.top + 30 * dp + max(22f, 18f * ui.textScale) * dp + max(18f, 16f * ui.textScale) * dp
        val visible = play.r.top - 10 * dp - top
        detailMax = max(0f, hgt - visible)
        detailScroll = detailScroll.coerceIn(0f, detailMax)
    }

    override fun onDrag(dx: Float, dy: Float) {
        if (selected >= 0) {
            detailScroll = (detailScroll - dy).coerceIn(0f, detailMax)
        } else {
            scroll = (scroll - dy).coerceIn(0f, maxScroll)
            placeCards()
        }
    }

    override fun update(dt: Float) { this.dt = dt }

    override fun render(c: Canvas) {
        bg.draw(c, w, h, dt)
        chrome.dim(c, 125)
        if (selected >= 0) renderDetail(c) else renderList(c)
        ui.button(c, back, 16f)
        chrome.title(c, L["o209_title"])
        chrome.badges(c, g.profile.medals)
        drawHeldTooltip(c)
    }

    private fun renderList(c: Canvas) {
        ui.card(c, header, Color.rgb(52, 46, 38), 9f)
        val textRight = if (ranking.visible) ranking.r.left - 8 * dp else header.right - 12 * dp
        ui.textO(c, headerText, header.left + 12 * dp, header.centerY() + 5 * dp * ui.textScale,
            ui.fit(headerText, 15f, textRight - header.left - 12 * dp, 10f), Ui.GOLD)
        if (ranking.visible) ui.button(c, ranking, 13f)
        c.save()
        c.clipRect(g.insetL, area.top, w - g.insetR, area.bottom)
        val pr = g.profile
        for (i in cards.indices) {
            val r = cards[i].r
            if (r.bottom < area.top || r.top > area.bottom) continue
            val open = unlocked(i)
            val o = Orders.all[i]
            ui.card(c, r, if (open) CARD_BODY else CARD_LOCKED, 11f, if (cards[i].pressed) Ui.GOLD else 0)
            val mr = min(r.height() * 0.34f, 22 * dp)
            ui.medallion(c, r.left + 12 * dp + mr, r.centerY(), mr, Ui.ERA[o.era],
                if (open) OrderTexts.goalIcon(o) else Icons.LOCK)
            val tx = r.left + 24 * dp + mr * 2
            val starsW = 3 * 20 * dp
            val textW = r.right - tx - starsW - 18 * dp
            val name = cards[i].label
            ui.textO(c, name, tx, r.centerY() - 3 * dp, ui.fit(name, 16f, textW, 11f),
                if (open) Ui.TEXT else Ui.DIM)
            ui.text(c, subs[i], tx, r.centerY() + 15 * dp * ui.textScale,
                ui.fit(subs[i], 12f, textW, 10f), if (open) Ui.DIM else Ui.DIM)
            if (open) ui.stars(c, r.right - 12 * dp - starsW / 2, r.centerY(), 20 * dp, pr.orderStars[i], 3, 1f)
        }
        c.restore()
        cue.vertical(c, area.right - 14 * dp, area.top, area.bottom, scroll, maxScroll, dp)
    }

    private fun renderDetail(c: Canvas) {
        val o = Orders.all[selected]
        ui.panel(c, detail)
        // Nazwa rozkazu w panelu (druga wstęga nachodziłaby na tytuł ekranu).
        val name = L[o.nameKey]
        ui.textO(c, name, detail.centerX(), detail.top + 30 * dp,
            ui.fit(name, 20f, detail.width() - 48 * dp, 11f), Ui.GOLD, Paint.Align.CENTER)
        val starsY = detail.top + 30 * dp + max(22f, 18f * ui.textScale) * dp
        ui.stars(c, detail.centerX(), starsY, 24 * dp, g.profile.orderStars[selected], 3, 1.05f)
        val top = starsY + max(18f, 16f * ui.textScale) * dp
        val bottom = play.r.top - 10 * dp
        c.save()
        c.clipRect(detail.left + 8 * dp, top, detail.right - 8 * dp, bottom)
        var y = top - detailScroll
        val x0 = detail.left + 20 * dp
        for (i in lineText.indices) {
            val size = lineSize[i]
            val lh = size * dp * ui.textScale * 1.3f
            y += lineGap[i] * dp + lh
            if (y < top - 4 * dp || y - lh > bottom + 4 * dp) continue
            val icon = lineIcon[i]
            val tx = if (icon == -1) x0 else x0 + 26 * dp
            if (icon >= 0) Icons.draw(c, icon, x0 + 10 * dp, y - lh * 0.35f, 18 * dp, dp = dp)
            ui.textO(c, lineText[i], tx, y, ui.fit(lineText[i], size, detail.right - 20 * dp - tx, 10f), lineColor[i])
        }
        c.restore()
        cue.vertical(c, detail.right - 24 * dp, top, bottom, detailScroll, detailMax, dp)
        ui.button(c, play, 18f)
    }

    // ------------------------------------------------ czytnik ekranu (standardowe etykiety)

    override fun accessibilityTitle(): String =
        if (selected >= 0) L[Orders.all[selected].nameKey] else L["o209_title"]

    override fun accessibilityInfoCount(): Int = 1

    override fun accessibilityInfo(i: Int): String = if (selected >= 0) detailText else headerText

    override fun accessibilityInfoBounds(i: Int, out: RectF) {
        out.set(if (selected >= 0) detail else header)
    }

    override fun accessibilityCanScroll(direction: Int): Boolean = if (selected >= 0)
        (if (direction > 0) detailScroll < detailMax - 0.5f else detailScroll > 0.5f)
        else (if (direction > 0) scroll < maxScroll - 0.5f else scroll > 0.5f)

    override fun accessibilityScroll(direction: Int): Boolean {
        if (!accessibilityCanScroll(direction)) return false
        return if (selected >= 0) {
            val moved = accessibilityStep(detailScroll, detailMax, play.r.top - detail.top, direction)
            detailScroll = scrollResult
            moved
        } else {
            val moved = accessibilityStep(scroll, maxScroll, area.height(), direction)
            scroll = scrollResult
            placeCards()
            moved
        }
    }

    override fun onClick(b: Button) {
        when (b.id) {
            -1 -> onBack()
            PLAY -> if (selected >= 0) {
                g.setScreen(BattleScreen(g, Orders.config(Orders.all[selected], System.nanoTime())))
            }
            RANKING -> if (g.online.available && g.online.state == OnlineState.CONNECTED)
                g.online.showUi(Profile.BOARD_ORDERS) else g.online.signIn()
            else -> {
                val i = b.id - CARD0
                if (i in 0 until Orders.COUNT && unlocked(i)) {
                    selected = i
                    detailScroll = 0f
                    layout()
                }
            }
        }
    }

    override fun onBack(): Boolean {
        if (selected >= 0) {
            selected = -1
            layout()
            return true
        }
        g.pop()
        return true
    }
}
