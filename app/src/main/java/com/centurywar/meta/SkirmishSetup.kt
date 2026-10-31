package com.centurywar.meta

import com.centurywar.game.AiPersonality
import com.centurywar.game.BattleConfig
import com.centurywar.game.Bonuses
import com.centurywar.game.Defs
import com.centurywar.game.Mode
import com.centurywar.game.Modifier

/** Ustawienia potyczki zapisywane w profilu, niezależnie od logiki symulacji. */
data class SkirmishSetup(
    val startEra: Int = 0,
    val maxEra: Int = Defs.ERA_COUNT - 1,
    /** -1 = osobowość deterministycznie losowana z seeda. */
    val personality: Int = -1,
    val powerPercent: Int = 105,
    val modifierMask: Int = 0,
) {
    companion object {
        val PRESET_POWER = intArrayOf(80, 105, 130, 160)

        fun defaults(diff: Int = 1) = SkirmishSetup(
            powerPercent = PRESET_POWER[diff.coerceIn(0, PRESET_POWER.lastIndex)],
        )

        fun fromConfig(cfg: BattleConfig): SkirmishSetup {
            var mask = 0
            for (m in cfg.modifiers) mask = mask or (1 shl m.ordinal)
            return SkirmishSetup(
                cfg.startEra, cfg.maxEra, AiPersonality.values().indexOf(cfg.aiPersonality),
                kotlin.math.round(cfg.aiPower * 100f).toInt(), mask,
            ).sanitized()
        }

        /** Sprawdzamy oryginalną konfigurację, nie przyciętą kopię danych profilu. */
        fun isRanked(cfg: BattleConfig): Boolean =
            cfg.mode == Mode.SKIRMISH &&
                cfg.startEra == 0 && cfg.maxEra == Defs.ERA_COUNT - 1 &&
                cfg.aiPersonality == null && cfg.modifiers.isEmpty() &&
                PRESET_POWER.any { kotlin.math.abs(cfg.aiPower - it / 100f) < 0.0001f }

        /** Medale za pełną potyczkę pozostają identyczne ze starszą wersją gry.
         * Własna bitwa ma dodatkowy limit za czas i liczbę epok: maks. 57 medali na
         * 10 min przy całym zakresie i mocy 160%, proporcjonalnie mniej za krótszy zakres.
         * To ogranicza farmienie jednej krótkiej epoki lub szybkiej kapitulacji.
         */
        fun reward(cfg: BattleConfig, won: Boolean, seconds: Float, kills: Int): Int {
            val time = if (seconds.isFinite()) seconds.coerceAtLeast(0f) else 0f
            val effort = (time / 240f).coerceIn(0f, 1f)
            val loss = (4f * effort).toInt() + kills.coerceAtLeast(0) / 12
            val preset = PRESET_POWER.indexOfFirst { kotlin.math.abs(cfg.aiPower - it / 100f) < 0.0001f }
            if (isRanked(cfg)) return if (won) 15 + preset * 14 else loss

            val power = if (cfg.aiPower.isFinite()) cfg.aiPower.coerceIn(0.6f, 2f) else 0.6f
            val fraction = (cfg.maxEra - cfg.startEra + 1).coerceIn(1, Defs.ERA_COUNT).toFloat() / Defs.ERA_COUNT
            val base = (15f + (power * 100f - 80f) * 14f / 25f).coerceIn(1f, 57f)
            val intended = ((if (won) base else loss.toFloat()) * fraction).toInt()
            val fogFactor = if (Modifier.FOG_OF_WAR in cfg.modifiers) 0.5f else 1f
            val cap = (time * (57f / 600f) * fraction *
                (power.coerceAtMost(1.6f) / 1.6f) * fogFactor).toInt()
            return minOf(intended, cap).coerceAtLeast(0)
        }
    }

    fun sanitized(): SkirmishSetup {
        val first = startEra.coerceIn(0, Defs.ERA_COUNT - 1)
        val last = maxEra.coerceIn(first, Defs.ERA_COUNT - 1)
        val p = personality.coerceIn(-1, AiPersonality.values().lastIndex)
        val power = ((powerPercent.coerceIn(60, 200) + 2) / 5 * 5).coerceIn(60, 200)
        val legalBits = (1 shl Modifier.values().size) - 1
        var mask = modifierMask and legalBits
        while (Integer.bitCount(mask) > 3) {
            // Usuwamy najwyższy bit, zachowując pierwsze trzy ustawione opcje.
            mask = mask and (Integer.highestOneBit(mask).inv())
        }
        return SkirmishSetup(first, last, p, power, mask)
    }

    fun modifiers(): Set<Modifier> {
        val selected = LinkedHashSet<Modifier>()
        for (m in Modifier.values()) if (modifierMask and (1 shl m.ordinal) != 0) selected.add(m)
        return selected
    }

    fun isRanked(): Boolean = startEra == 0 && maxEra == Defs.ERA_COUNT - 1 &&
        personality == -1 && modifierMask == 0 && powerPercent in PRESET_POWER

    fun toConfig(seed: Long, bonuses: Bonuses) = sanitized().let { s ->
        BattleConfig(
            Mode.SKIRMISH, seed, s.powerPercent / 100f, s.startEra, s.maxEra,
            s.modifiers(), bonuses, aiPersonality = AiPersonality.values().getOrNull(s.personality),
        )
    }
}
