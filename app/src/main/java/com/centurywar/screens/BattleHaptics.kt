package com.centurywar.screens

import com.centurywar.engine.Game
import com.centurywar.engine.HapticKind
import com.centurywar.game.Battle
import com.centurywar.game.FxBuffer

/** Jedno mapowanie zdarzeń bitwy na haptykę, bez alokacji w klatce. */
internal class BattleHaptics(private val g: Game, private val battle: Battle) {
    private var previousBaseHp = battle.player.baseHp

    /** Wywoływać tylko po wykonaniu kroku symulacji (bufor FX nie jest wtedy stary). */
    fun onSimulationStep() {
        val fx = battle.fx
        val hp = battle.player.baseHp
        var hitBase = false
        var best: HapticKind? = null
        for (i in 0 until fx.count) {
            if (fx.type[i] == FxBuffer.BASE_HIT && fx.side[i] == 0) hitBase = true
            val kind = kindForFx(fx.type[i], fx.side[i], fx.value[i])
            if (kind != null && (best == null || kind.priority > best.priority)) best = kind
        }
        // Nie wibruj przy drobnych trafieniach ani pasywnej presji oblężenia.
        if (hitBase && previousBaseHp - hp >= battle.player.baseMaxHp * 0.025f &&
            (best == null || HapticKind.HEAVY_HIT.priority > best.priority)) {
            best = HapticKind.HEAVY_HIT
        }
        previousBaseHp = hp
        if (best != null) g.haptic(best)
    }

    fun onBarrage() = g.haptic(HapticKind.BARRAGE)
    fun onEvolve() = g.haptic(HapticKind.EVOLVE)
    fun onUpgrade() = g.haptic(HapticKind.TAP)
    fun onEnd(won: Boolean) = g.haptic(if (won) HapticKind.VICTORY else HapticKind.DEFEAT)

    companion object {
        /** Wydarzenia własnego ostrzału, awansu epoki i pojawienia się bossa. */
        internal fun kindForFx(type: Int, side: Int, value: Float): HapticKind? = when {
            type == FxBuffer.BARRAGE && side == 0 -> HapticKind.BARRAGE
            type == FxBuffer.EVOLVE && side == 0 && value == 0f -> HapticKind.EVOLVE
            type == FxBuffer.EVOLVE && side == 1 && value >= 2f -> HapticKind.BOSS
            else -> null
        }
    }
}
