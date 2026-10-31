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
    private var wrapWidth = -1f
    private val lines = arrayOfNulls<String>(2)
    private var lineCount = 0
    private val panel = RectF()
    private val highlight = RectF()
    private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    /** Wyłączenie i wszystkie stany modalne ukrywają dymek, nie zatrzymując bitwy. */
    fun update(dt: Float, blocked: Boolean = false) {
        val b = battle
        val suppressed = blocked || b?.pendingChoice != null || b?.pendingCards != null
        if (activeId >= 0) {
            elapsed += dt
            if (suppressed || !g.profile.hintsEnabled || elapsed >= DISPLAY_TIME) dismiss()
        }
        if (b == null) return
        if (suppressed || activeId >= 0 || !g.profile.hintsEnabled || b.over) return
        sinceCheck += dt
        if (sinceCheck < CHECK_EVERY) return
        sinceCheck = 0f
        if (b.time - lastShownAt < GAP) return
        var best: HintDef? = null
        for (def in DEFINITIONS) {
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
            else -> false
        }
    }

    private fun activate(id: Int, key: String) {
        if (!g.profile.markHintSeen(id)) return
        activeId = id
        elapsed = 0f
        wrapWidth = -1f
        lineCount = 0
        okBtn.label = L["hint_ok"]
        okBtn.visible = true
        if (battle != null) lastShownAt = battle.time
        body = when (id) {
            BOSS -> L[if (battle?.currentBoss()?.bossKind == 2) "hint_boss_aura" else key]
            MODIFIERS -> modifierDescription()
            else -> L[key]
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
            else -> null
        }
    }

    fun draw(c: Canvas) {
        if (activeId < 0 || !g.profile.hintsEnabled) return
        val dp = g.ui.dp
        val sl = g.insetL + 9 * dp
        val sr = g.width - g.insetR - 9 * dp
        val st = g.insetT + 13 * dp
        val sb = g.height - g.insetB - 9 * dp
        val pw = min(610 * dp, sr - sl)
        if (pw < 100 * dp) return
        val ph = min(139 * dp, sb - st)
        val target = focus()
        if (target != null && target.width() > 0f && target.height() > 0f) {
            highlight.set(target)
            highlight.inset(-3 * dp, -3 * dp)
            border.color = Ui.OUTLINE
            border.strokeWidth = 5 * dp
            c.drawRoundRect(highlight, 9 * dp, 9 * dp, border)
            border.color = Ui.GOLD
            border.strokeWidth = 2.5f * dp
            c.drawRoundRect(highlight, 9 * dp, 9 * dp, border)
        }
        val cx = if (target == null || target.width() <= 0f) g.width * 0.5f else target.centerX()
        val left = (cx - pw / 2).coerceIn(sl, sr - pw)
        val freeAbove = if (target == null) 0f else target.top - st
        val freeBelow = if (target == null) 0f else sb - target.bottom
        val top = when {
            target == null -> st + (sb - st - ph) * 0.4f
            freeAbove >= ph + 12 * dp || freeAbove > freeBelow ->
                (target.top - ph - 12 * dp).coerceIn(st, sb - ph)
            else -> (target.bottom + 12 * dp).coerceIn(st, sb - ph)
        }
        panel.set(left, top, left + pw, top + ph)
        HintFrame.chrome(c, g.ui, panel, L["hint_title"], dp)
        val textWidth = pw - 36 * dp
        if (textWidth != wrapWidth) {
            wrapWidth = textWidth
            lineCount = HintFrame.wrapInto(g.ui, body, textWidth, 13.5f, lines)
        }
        var y = panel.top + 57 * dp
        for (i in 0 until lineCount) {
            g.ui.text(c, lines[i] ?: "", panel.left + 18 * dp, y, 13.5f, Ui.TEXT)
            y += 20 * dp
        }
        val bw = min(100 * dp, pw * 0.28f)
        okBtn.set(panel.right - bw - 12 * dp, panel.bottom - 39 * dp, bw, 32 * dp)
        g.ui.button(c, okBtn, 13f)
    }
}
