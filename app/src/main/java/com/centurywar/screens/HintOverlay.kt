package com.centurywar.screens

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.centurywar.engine.Button
import com.centurywar.engine.Game
import com.centurywar.engine.Icons
import com.centurywar.engine.L
import com.centurywar.engine.Ui
import com.centurywar.game.Battle
import com.centurywar.game.Defs
import com.centurywar.game.EndlessWaveTheme
import com.centurywar.game.Mode
import com.centurywar.game.Modifier
import com.centurywar.game.Role
import com.centurywar.game.Weather
import kotlin.math.max
import kotlin.math.min

/**
 * Jednorazowe dymki, niezależne od symulacji i logu komend.
 * Warunki bitwy sprawdzane co ćwierć sekundy; render i update nie tworzą obiektów.
 */
class HintOverlay(
    private val g: Game,
    private val battle: Battle? = null,
    private val targets: Targets? = null,
    private val rankedDaily: Boolean = false,
) {
    class Targets(
        val upgrades: Array<Button>,
        val armory: Button,
        val rally: Button,
        val stance: Button,
        val evolve: Button,
        val turrets: Button,
        val gold: RectF,
        val xp: RectF,
        val minimap: RectF,
        val field: RectF,
        val weather: RectF,
        val variants: Array<Button>,
    )

    private class HintDef(val id: Int, val key: String, val priority: Int)

    companion object {
        const val UPGRADE = 0
        const val CAP = 1
        const val RALLY = 2
        const val HOLD = 3
        const val VETERAN = 4
        const val SIEGE = 5
        const val MODIFIERS = 6
        const val FOG = 7
        const val CARD = 8
        const val THEME = 9
        const val BOSS = 10
        const val OLD_TURRET = 11
        const val DAILY = 12
        const val RESEARCH = 13
        const val MISSIONS = 14
        const val DAILY_MENU = 15
        const val ENDLESS_MENU = 16
        const val VARIANT = 18
        const val WEATHER = 19
        const val OK_ID = 920

        private val DEFINITIONS = arrayOf(
            HintDef(UPGRADE, "hint_upgrade", 72),
            HintDef(CAP, "hint_cap", 84),
            HintDef(RALLY, "hint_rally", 90),
            HintDef(HOLD, "hint_hold", 75),
            HintDef(VETERAN, "hint_veteran", 70),
            HintDef(SIEGE, "hint_siege", 99),
            HintDef(MODIFIERS, "hint_modifiers", 80),
            HintDef(FOG, "hint_fog", 78),
            HintDef(CARD, "hint_card", 88),
            HintDef(THEME, "hint_theme", 60),
            HintDef(BOSS, "hint_boss_push", 98),
            HintDef(OLD_TURRET, "hint_old_turret", 82),
            HintDef(DAILY, "hint_daily_battle", 96),
            HintDef(VARIANT, "hint_variant54", 110),
            HintDef(WEATHER, "hint_weather54", 86),
        )
        private val SCREEN_KEYS = arrayOf(
            "hint_research", "hint_missions", "hint_daily_menu", "hint_endless_menu",
        )
        private val ROLES = Role.entries
        private const val CHECK_EVERY = 0.25f
        private const val GAP = 20f
        private const val DISPLAY_TIME = 8f
    }

    val okBtn = Button(OK_ID).apply {
        visible = false
        iconId = Icons.CHECK
        highlight = true
        label = L["hint_ok"]
    }

    var activeId = -1
        private set
    private var elapsed = 0f
    private var sinceCheck = 0f
    private var lastShownAt = -GAP
    private var upgradeIndex = 0
    private var screenFocus: RectF? = null
    private var body = ""
    private val lines = arrayOfNulls<String>(24)
    private var lineCount = 0
    private var compact = false
    private var hintWidth = 0f
    private var hintHeight = 0f
    private var hintFont = 12.5f
    private var layoutDp = -1f
    private var layoutScale = -1f
    private var layoutSafeWidth = -1f
    private val panel = RectF()
    private val connector = RectF()
    private val highlight = RectF()
    private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val pointer = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    /** Wyłączenie i wszystkie stany modalne ukrywają dymek, nie zatrzymując bitwy. */
    fun update(dt: Float, blocked: Boolean = false) {
        val b = battle
        val suppressed = blocked || b?.pendingChoice != null || b?.pendingCards != null
        if (b?.pendingVariant != null && activeId >= 0 && activeId != VARIANT) dismiss()
        if (activeId >= 0) {
            elapsed += dt
            if (suppressed || !g.profile.hintsEnabled || elapsed >= DISPLAY_TIME) dismiss()
            else prepareLayout()
        }
        if (b == null) return
        if (suppressed || activeId >= 0 || !g.profile.hintsEnabled || b.over) return
        sinceCheck += dt
        if (sinceCheck < CHECK_EVERY) return
        sinceCheck = 0f
        // Okno wariantu zatrzymuje bitwę: pokaż objaśnienie od razu, nie po 20 s.
        if (b.pendingVariant == null && b.time - lastShownAt < GAP) return
        var best: HintDef? = null
        for (def in DEFINITIONS) {
            if (b.pendingVariant != null && def.id != VARIANT) continue
            if (g.profile.hintsSeen and (1L shl def.id) != 0L) continue
            if (best != null && def.priority <= best.priority) continue
            if (eligible(def.id)) best = def
        }
        if (best != null) activate(best.id, best.key)
    }

    /** Dymek tylko przy pierwszym wejściu na ekran, używa tej samej maski co bitwa. */
    fun showScreen(id: Int, focus: RectF) {
        if (battle != null || id !in RESEARCH..ENDLESS_MENU || activeId >= 0 ||
            !g.profile.hintsEnabled || g.profile.hintsSeen and (1L shl id) != 0L) return
        screenFocus = focus
        activate(id, SCREEN_KEYS[id - RESEARCH])
    }

    private fun nearEnemyCount(): Int {
        val b = battle ?: return 0
        val limit = b.player.baseFront + Defs.LANE * 0.33f
        var count = 0
        for (unit in b.units) if (unit.alive && unit.side == 1 && unit.x <= limit) count++
        return count
    }

    private fun eligible(id: Int): Boolean {
        val b = battle ?: return false
        val p = b.player
        return when (id) {
            UPGRADE -> {
                var found = false
                for (i in 0..3) {
                    if (b.canUpgradeUnit(p, ROLES[i])) { upgradeIndex = i; found = true; break }
                }
                found
            }
            CAP -> p.aliveCount >= p.cap && p.gold >= b.armoryCost(p) * 1.3f && p.armory < 15
            RALLY -> p.rallyUnlocked && b.abilityReady(p, 1) && nearEnemyCount() >= 3
            HOLD -> !p.hold && b.enemy.aliveCount >= p.aliveCount + 3 && nearEnemyCount() >= 2
            VETERAN -> p.era >= b.cfg.maxEra && p.rank > 0
            SIEGE -> !b.endless && b.time >= Battle.SIEGE_START && b.siegePressure() > 1f
            MODIFIERS -> b.cfg.modifiers.isNotEmpty() && b.time < 60f
            FOG -> b.fog && b.time < 80f
            CARD -> b.endless && b.wave > 0 && (b.wave + 1) % Battle.ENDLESS_CHOICE_EVERY == 0 &&
                b.waveTimer <= 8f && b.pendingCards == null
            THEME -> b.endless && b.wave >= 1 && b.waveTimer < 8f &&
                b.nextWaveTheme != EndlessWaveTheme.MIXED
            BOSS -> b.endless && b.currentBoss() != null
            OLD_TURRET -> p.era > b.cfg.startEra && p.turretEra.any { it >= 0 && it < p.era }
            DAILY -> b.cfg.mode == Mode.DAILY && rankedDaily && b.time < 18f
            VARIANT -> b.pendingVariant != null
            WEATHER -> b.weather != Weather.CLEAR
            else -> false
        }
    }

    private fun activate(id: Int, key: String) {
        if (!g.profile.markHintSeen(id)) return
        activeId = id
        elapsed = 0f
        layoutDp = -1f
        lineCount = 0
        okBtn.label = L["hint_ok"]
        okBtn.visible = true
        if (battle != null) lastShownAt = battle.time
        body = when (id) {
            BOSS -> L[if (battle?.currentBoss()?.bossKind == 2) "hint_boss_aura" else key]
            MODIFIERS -> modifierDescription()
            else -> L[key]
        }
        prepareLayout()
    }

    /**
     * Szerokość zależy od realnego tekstu. Łamanie i pomiary odbywają się przy
     * pokazaniu wskazówki lub zmianie skali, nie podczas rysowania klatek.
     */
    private fun prepareLayout() {
        val dp = g.ui.dp
        val scale = g.ui.textScale
        val safeW = g.width - g.insetL - g.insetR - 18f * dp
        if (dp == layoutDp && scale == layoutScale && safeW == layoutSafeWidth) return
        layoutDp = dp
        layoutScale = scale
        layoutSafeWidth = safeW
        hintWidth = 0f
        hintHeight = 0f
        if (safeW < 110f * dp || body.isEmpty()) return

        val maxW = min(600f * dp, safeW)
        val buttonW = 51f * dp
        val padding = 12f * dp
        hintFont = 12.5f
        var measured = g.ui.measure(body, hintFont)
        // Rozszerzone opisy wariantów próbujemy najpierw zmieścić w jednym wierszu.
        if (activeId == VARIANT && measured > maxW - buttonW - 3f * padding) {
            hintFont = max(10.5f / scale, hintFont * (maxW - buttonW - 3f * padding) / measured)
            measured = g.ui.measure(body, hintFont)
        }
        compact = measured <= maxW - buttonW - 3f * padding
        if (compact) {
            lines[0] = body
            lineCount = 1
            hintWidth = (measured + buttonW + 3f * padding).coerceIn(min(152f * dp, maxW), maxW)
            hintHeight = max(36f, 21f + hintFont * scale) * dp
        } else {
            lineCount = HintFrame.wrapInto(g.ui, body, maxW - 2f * padding, hintFont, lines)
            var longest = 0f
            for (i in 0 until lineCount) longest = max(longest, g.ui.measure(lines[i] ?: "", hintFont))
            hintWidth = (longest + 2f * padding).coerceIn(min(188f * dp, maxW), maxW)
            hintHeight = (53f + hintFont * scale * 0.5f + 4f * scale + (lineCount - 1) * 17f * scale) * dp
        }
    }

    /** Nazwy i zwięzłe objaśnienia wszystkich modyfikatorów, w kolejności enum. */
    private fun modifierDescription(): String {
        val b = battle ?: return L["hint_modifiers"]
        val sb = StringBuilder(L["hint_modifiers"])
        var count = 0
        for (mod in Modifier.entries) {
            if (mod !in b.cfg.modifiers) continue
            sb.append(if (count == 0) ": " else "; ")
            sb.append(L[mod.key]).append(" — ").append(L[mod.key + "_d"])
            count++
        }
        return sb.toString()
    }

    /** Dotknięcie wskazanej akcji lub innego aktywnego przycisku usuwa dymek. */
    fun onAction() { if (activeId >= 0) dismiss() }

    fun onClick(b: Button): Boolean {
        if (b !== okBtn || activeId < 0) return false
        dismiss()
        return true
    }

    fun dismiss() {
        activeId = -1
        okBtn.visible = false
        screenFocus = null
    }

    /** Prostokąt celu jest referencją do bieżącego layoutu, nie kopią. */
    private fun focus(): RectF? {
        if (battle == null) return screenFocus
        val t = targets ?: return null
        return when (activeId) {
            UPGRADE -> t.upgrades[upgradeIndex].r
            CAP -> t.armory.r
            RALLY -> t.rally.r
            HOLD -> t.stance.r
            VETERAN -> t.evolve.r
            SIEGE -> t.xp
            MODIFIERS, DAILY -> t.gold
            FOG -> t.field
            CARD, THEME, BOSS -> if (t.minimap.width() > 0f) t.minimap else t.field
            OLD_TURRET -> t.turrets.r
            VARIANT -> if (t.variants[0].visible) t.variants[0].r else null
            WEATHER -> t.weather
            else -> null
        }
    }

    fun draw(c: Canvas) {
        if (activeId < 0 || !g.profile.hintsEnabled || hintWidth <= 0f) return
        val dp = g.ui.dp
        val safeL = g.insetL + 4f * dp
        val safeR = g.width - g.insetR - 4f * dp
        val safeT = g.insetT + 4f * dp
        val safeB = g.height - g.insetB - 4f * dp
        val target = focus()
        if (!HintFrame.place(target, safeL, safeT, safeR, safeB,
                hintWidth, hintHeight, 4f * dp, panel, connector)) return

        // Wskaźnik kończy się na krawędzi celu; nie przesłania samych przycisków.
        if (target != null && target.width() > 0f && target.height() > 0f) {
            highlight.set(target)
            highlight.inset(-3f * dp, -3f * dp)
            border.color = Ui.OUTLINE
            border.strokeWidth = 5f * dp
            c.drawRoundRect(highlight, 9f * dp, 9f * dp, border)
            border.color = Ui.GOLD
            border.strokeWidth = 2.5f * dp
            c.drawRoundRect(highlight, 9f * dp, 9f * dp, border)
            pointer.color = Ui.GOLD
            pointer.strokeWidth = 2.2f * dp
            val x0 = connector.left
            val y0 = connector.top
            val x1 = connector.right
            val y1 = connector.bottom
            c.drawLine(x0, y0, x1, y1, pointer)
            val head = 5f * dp
            if (kotlin.math.abs(x1 - x0) > kotlin.math.abs(y1 - y0)) {
                val dir = if (x1 > x0) 1f else -1f
                c.drawLine(x1, y1, x1 - dir * head, y1 - head * 0.6f, pointer)
                c.drawLine(x1, y1, x1 - dir * head, y1 + head * 0.6f, pointer)
            } else {
                val dir = if (y1 > y0) 1f else -1f
                c.drawLine(x1, y1, x1 - head * 0.6f, y1 - dir * head, pointer)
                c.drawLine(x1, y1, x1 + head * 0.6f, y1 - dir * head, pointer)
            }
        }
        g.ui.panel(c, panel)
        if (compact) {
            val baseline = panel.centerY() + hintFont * g.ui.textScale * dp * 0.35f
            g.ui.text(c, lines[0] ?: "", panel.left + 12f * dp, baseline, hintFont, Ui.TEXT)
            okBtn.set(panel.right - 59f * dp, panel.centerY() - 14f * dp, 49f * dp, 28f * dp)
        } else {
            var y = panel.top + (14f + hintFont * g.ui.textScale * 0.5f) * dp
            for (i in 0 until lineCount) {
                g.ui.text(c, lines[i] ?: "", panel.left + 12f * dp, y, hintFont, Ui.TEXT)
                y += 17f * dp * g.ui.textScale
            }
            okBtn.set(panel.right - 65f * dp, panel.bottom - 34f * dp, 53f * dp, 28f * dp)
        }
        g.ui.button(c, okBtn, 12f)
    }
}
