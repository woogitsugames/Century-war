package com.centurywar.screens

import com.centurywar.engine.Button
import com.centurywar.engine.L
import com.centurywar.game.Defs
import com.centurywar.game.Role
import kotlin.math.ceil

/**
 * Dzień 5: teksty czytnika ekranu dla HUD bitwy. Wywoływane wyłącznie na wątku gry.
 * Funkcje [key] i zaokrąglenia nie alokują, bo struktura drzewa jest porównywana co klatkę;
 * teksty powstają dopiero przy przebudowie drzewa (najwyżej co sekundę).
 */
internal object BattleAccessibility {
    const val BARRAGE_CLUSTER = 0
    const val BARRAGE_FRONT = 1
    const val BARRAGE_BASE = 2

    /** Złoto do pełnych 10 (poniżej 100 dokładnie), żeby opis nie zmieniał się co klatkę. */
    fun roundGold(gold: Float): Int {
        val v = gold.toInt().coerceAtLeast(0)
        return if (v < 100) v else v / 10 * 10
    }

    /** Sekundy odnowienia zaokrąglone w górę do 5 s. */
    fun roundSeconds(cd: Float): Int = if (cd <= 0f) 0 else (ceil(cd / 5.0).toInt() * 5)

    /** Procent do 5%; żywa baza nigdy nie pokazuje 0%. */
    fun percent5(value: Float, max: Float): Int {
        if (max <= 0f || value <= 0f) return 0
        val pct = value / max * 100f
        return (Math.round(pct / 5f) * 5).coerceIn(5, 100)
    }

    fun goldText(s: BattleScreen): String = L.f("a178_gold", roundGold(s.battle.player.gold))

    fun xpText(s: BattleScreen): String {
        val p = s.battle.player
        return when {
            p.era >= s.cfg.maxEra -> L.f("rank", p.rank)
            s.battle.canEvolve(p) -> L["a178_xp_ready"]
            else -> L.f("a178_xp", xpPercent(s), L[Defs.eraKeys[p.era + 1]])
        }
    }

    private fun xpPercent(s: BattleScreen): Int {
        val p = s.battle.player
        if (p.era >= s.cfg.maxEra) return 100
        val need = Defs.evolveXp[p.era]
        return if (need <= 0f) 100 else ((p.xp / need * 100f).toInt() / 5 * 5).coerceIn(0, 100)
    }

    fun basesText(s: BattleScreen): String {
        val p = s.battle.player
        val e = s.battle.enemy
        val mine = percent5(p.baseHp, p.baseMaxHp)
        return if (s.battle.endless) L.f("a178_base", mine)
            else L.f("a178_bases", mine, percent5(e.baseHp, e.baseMaxHp))
    }

    fun waveText(s: BattleScreen): String = L.f("a178_wave", s.battle.wave, L[s.battle.nextWaveKey])

    fun armyText(s: BattleScreen): String = L.f("a178_army", s.battle.player.aliveCount, s.battle.player.cap)

    fun upgradeText(s: BattleScreen): String {
        val i = s.showUpgrade
        if (i !in 0..3) return L["unit_upgrade_title"]
        return L["unit_upgrade_title"] + ": " + unitName(s, i) + ", " + s.upgradeTierText
    }

    fun resultText(s: BattleScreen): String {
        val title = s.resultTitle.ifBlank { L[if (s.battle.winner == 0) "a178_end_win" else "a178_end_loss"] }
        return if (s.rewardText.isBlank()) title else "$title. ${s.rewardText}"
    }

    fun unitName(s: BattleScreen, i: Int): String {
        val p = s.battle.player
        val def = p.unit(s.roles[i])
        return L[def.tierNameKeys[(p.unitTier[i] - 1).coerceIn(0, def.tierNameKeys.size - 1)]]
    }

    private fun queuedCount(s: BattleScreen, i: Int): Int {
        val p = s.battle.player
        val id = p.unit(s.roles[i]).id
        var n = 0
        for (k in 0 until p.queueSize) if (p.queue[k] == id) n++
        return n
    }

    private fun training(s: BattleScreen, i: Int): Boolean {
        val p = s.battle.player
        return p.queueSize > 0 && p.queue[0] == p.unit(s.roles[i]).id
    }

    private fun locked(s: BattleScreen, i: Int): Boolean =
        s.roles[i] == Role.ELITE && !s.battle.player.eliteUnlocked

    /**
     * Stan, który zmienia znaczenie przycisku i ma być słyszalny od razu (bez limitu 1 s).
     * Bez alokacji: wywoływane w każdej klatce przy porównaniu struktury.
     */
    fun key(s: BattleScreen, b: Button): Int {
        val p = s.battle.player
        return when (b.id) {
            in BattleScreen.UNIT0..BattleScreen.UNIT0 + 3 -> {
                val i = b.id - BattleScreen.UNIT0
                (if (locked(s, i)) 1 else 0) or (if (training(s, i)) 2 else 0) or (queuedCount(s, i) shl 2) or
                    (p.unitTier[i] shl 8) or (if (s.unitUpgrade[i].visible) 1 shl 12 else 0)
            }
            BattleScreen.TURRET -> p.slots
            BattleScreen.ARMORY -> p.armory
            BattleScreen.STANCE -> if (p.hold) 1 else 0
            BattleScreen.SPEED -> s.speed * 8 + if (s.speed == 0) 0 else s.effectiveSpeed.toInt().coerceIn(1, s.speed)
            BattleScreen.RALLY -> if (p.rallyT > 0f) 1 else 0
            BattleScreen.COMMANDER -> if (p.commanderCd < 0f) 1 else if (p.commanderCd > 0f) 2 else 0
            BattleScreen.EVOLVE -> p.era * 1000 + p.rank
            BattleScreen.UNIT_UPGRADE_BUY -> s.showUpgrade * 8 + (if (s.showUpgrade in 0..3) p.unitTier[s.showUpgrade] else 0)
            in BattleScreen.DOCTRINE0..BattleScreen.DOCTRINE0 + 2 -> {
                val i = b.id - BattleScreen.DOCTRINE0
                val cards = s.battle.pendingCards
                val doctrines = s.battle.pendingChoice
                if (cards != null && i < cards.size) 1000 + cards[i].ordinal
                else if (doctrines != null && i < doctrines.size) doctrines[i].ordinal else -1
            }
            in BattleScreen.VARIANT0..BattleScreen.VARIANT0 + 1 -> s.battle.pendingVariant ?: -1
            in BattleScreen.SLOT_BUY0 until BattleScreen.SLOT_BUY0 + Defs.MAX_TURRET_SLOTS ->
                slotKey(s, b.id - BattleScreen.SLOT_BUY0)
            in BattleScreen.SLOT_SELL0 until BattleScreen.SLOT_SELL0 + Defs.MAX_TURRET_SLOTS ->
                slotKey(s, b.id - BattleScreen.SLOT_SELL0)
            in BattleScreen.SLOT_TIER0 until BattleScreen.SLOT_TIER0 + Defs.MAX_TURRET_SLOTS ->
                slotKey(s, b.id - BattleScreen.SLOT_TIER0)
            else -> 0
        }
    }

    private fun slotKey(s: BattleScreen, i: Int): Int {
        val p = s.battle.player
        return (p.turretEra[i] + 1) * 64 + (p.turretVariant[i] + 1) * 8 + p.turretTier[i]
    }

    /** Opis przycisku (nazwa i koszt). */
    fun description(s: BattleScreen, b: Button): String {
        val p = s.battle.player
        val t = s.tut
        if (t != null && (b === t.skipBtn || b === t.nextBtn)) return s.accessibilityLabel(b)
        if (b === s.hintOkBtn) {
            val text = s.hintAccessibilityText
            return if (text.isBlank()) s.accessibilityLabel(b) else text + " " + s.accessibilityLabel(b)
        }
        return when (b.id) {
            in BattleScreen.UNIT0..BattleScreen.UNIT0 + 3 -> {
                val i = b.id - BattleScreen.UNIT0
                if (locked(s, i)) unitName(s, i)
                else L.f("a178_unit", unitName(s, i), s.battle.unitCost(p, s.roles[i]))
            }
            BattleScreen.TURRET -> L["turrets"]
            BattleScreen.ARMORY ->
                if (p.armory < 15) L.f("a178_unit", L["armory"], s.battle.armoryCost(p)) else L["armory"]
            BattleScreen.BARRAGE -> L["tip53_barrage_title"]
            BattleScreen.RALLY -> L["rally"]
            BattleScreen.STANCE -> L["a178_stance"]
            BattleScreen.SPEED -> L["tip53_speed_title"]
            BattleScreen.COMMANDER ->
                if (p.commanderCd == 0f) L.f("a178_unit", L["comm125_name"], s.battle.commanderCost(p))
                else L["comm125_name"]
            BattleScreen.EVOLVE ->
                if (p.era >= s.cfg.maxEra) L.f("rank", p.rank)
                else L["evolve"] + ": " + L[Defs.eraKeys[p.era + 1]]
            BattleScreen.PAUSE -> L["a164_pause"]
            in BattleScreen.DOCTRINE0..BattleScreen.DOCTRINE0 + 2 -> s.choiceAccessibilityText(b.id - BattleScreen.DOCTRINE0)
            in BattleScreen.VARIANT0..BattleScreen.VARIANT0 + 1 -> s.variantAccessibilityText(b.id - BattleScreen.VARIANT0)
            BattleScreen.UNIT_UPGRADE_BUY -> {
                val i = s.showUpgrade
                val cost = if (i in 0..3) s.battle.unitUpgradeCost(p, s.roles[i]) else -1
                if (cost < 0) L["unit_upgrade_max"] else L.f("a178_unit", L["upgrade"], cost)
            }
            BattleScreen.UNIT_UPGRADE_CLOSE, BattleScreen.CLOSE -> L["back"]
            in BattleScreen.SLOT_BUY0 until BattleScreen.SLOT_BUY0 + Defs.MAX_TURRET_SLOTS,
            in BattleScreen.SLOT_SELL0 until BattleScreen.SLOT_SELL0 + Defs.MAX_TURRET_SLOTS,
            in BattleScreen.SLOT_TIER0 until BattleScreen.SLOT_TIER0 + Defs.MAX_TURRET_SLOTS -> {
                val slot = (b.id % 10) + 1
                L["turrets"] + " " + slot + ": " + b.label
            }
            else -> s.accessibilityLabel(b)
        }
    }

    /** Stan przycisku (API 30+: stateDescription, wcześniej dopisywany do opisu). */
    fun state(s: BattleScreen, b: Button): String {
        val p = s.battle.player
        return when (b.id) {
            in BattleScreen.UNIT0..BattleScreen.UNIT0 + 3 -> {
                val i = b.id - BattleScreen.UNIT0
                if (locked(s, i)) return L["a178_locked"]
                val parts = ArrayList<String>(3)
                if (training(s, i)) parts.add(L["a178_training"])
                val queued = queuedCount(s, i)
                if (queued > 0) parts.add(L.f("a178_queued", queued))
                if (!b.enabled) parts.add(when {
                    p.queueSize >= minOf(p.queueMax, com.centurywar.game.Battle.QUEUE_CAP) -> L["a178_queue_full"]
                    p.gold < s.battle.unitCost(p, s.roles[i]) -> L["a178_no_gold"]
                    else -> L["a178_locked"]
                })
                parts.joinToString(", ")
            }
            BattleScreen.TURRET -> when {
                p.slots <= 0 -> L["a178_locked"]
                s.showTurrets -> L["a178_open"]
                else -> ""
            }
            BattleScreen.ARMORY -> {
                val level = L.f("a178_level", p.armory)
                if (!b.enabled && p.armory < 15) level + ", " + L["a178_no_gold"] else level
            }
            BattleScreen.BARRAGE -> cooldown(s, 0)
            BattleScreen.RALLY -> if (p.rallyT > 0f) L["comm145_active"] else cooldown(s, 1)
            BattleScreen.STANCE -> L[if (p.hold) "hold" else "advance"]
            BattleScreen.SPEED -> b.label
            BattleScreen.COMMANDER -> when {
                p.commanderCd < 0f -> L["comm145_active"]
                p.commanderCd > 0f -> L.f("a178_cooldown", roundSeconds(p.commanderCd))
                b.enabled -> L["a178_ready"]
                p.gold < s.battle.commanderCost(p) -> L["a178_no_gold"]
                else -> L["a178_queue_full"]
            }
            BattleScreen.EVOLVE -> when {
                p.era >= s.cfg.maxEra -> ""
                s.battle.canEvolve(p) -> L["a178_ready"]
                else -> L.f("battle84_missing_xp",
                    ceil((Defs.evolveXp[p.era] - p.xp).coerceAtLeast(0f).toDouble()).toInt())
            }
            BattleScreen.UNIT_UPGRADE_BUY,
            in BattleScreen.SLOT_BUY0 until BattleScreen.SLOT_BUY0 + Defs.MAX_TURRET_SLOTS,
            in BattleScreen.SLOT_TIER0 until BattleScreen.SLOT_TIER0 + Defs.MAX_TURRET_SLOTS ->
                if (!b.enabled) L["a178_no_gold"] else ""
            else -> ""
        }
    }

    private fun cooldown(s: BattleScreen, i: Int): String {
        val cd = s.battle.player.abilityCd[i]
        return if (s.battle.abilityReady(s.battle.player, i)) L["a178_ready"]
            else if (cd > 0f) L.f("a178_cooldown", roundSeconds(cd))
            else L["a178_locked"]
    }
}
