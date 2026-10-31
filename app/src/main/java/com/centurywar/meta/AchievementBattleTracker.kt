package com.centurywar.meta

import com.centurywar.game.Battle
import com.centurywar.game.Defs
import com.centurywar.game.EndlessBosses
import kotlin.math.abs

/**
 * Obserwuje gotową symulację bez modyfikowania jej stanu, seeda ani logu komend.
 * Bufory powstają tylko przy otwarciu ekranu; obserwacja kroku nie alokuje obiektów.
 */
class AchievementBattleTracker(private val battle: Battle) {
    private val steamEra = Defs.eraKeys.indexOf("era_steam")
    private val siliconEra = Defs.eraKeys.indexOf("era_silicon")
    private val activeBefore = BooleanArray(battle.projs.size)
    private val aimed = BooleanArray(battle.projs.size)
    private val hitSlots = BooleanArray(battle.units.size)
    private var salvoHits = 0

    var steamMask = 0
        private set
    var siliconMask = 0
        private set
    var bossMask = 0
        private set
    var barrageEight = false
        private set

    init { observeBattleState() }

    /** Wywołać przed komendą celowanego ostrzału. */
    fun beforeAimedBarrage() {
        for (i in battle.projs.indices) activeBefore[i] = battle.projs[i].active
    }

    /** Wywołać po przyjęciu komendy przez BattleController. */
    fun afterAimedBarrage(cast: Boolean) {
        if (!cast) return
        hitSlots.fill(false)
        salvoHits = 0
        for (i in battle.projs.indices) {
            val p = battle.projs[i]
            aimed[i] = !activeBefore[i] && p.active && p.side == 0 &&
                p.shooterRole == null && p.turretEra < 0 && p.splash > 0f
        }
    }

    /** Odczyt po KAŻDYM kroku, zanim pula pocisków i jednostek zostanie ponownie użyta. */
    fun afterStep() {
        observeBattleState()
        if (barrageEight) return
        for (i in aimed.indices) {
            if (!aimed[i]) continue
            val p = battle.projs[i]
            if (p.active) continue
            aimed[i] = false
            if (p.t < p.dur || p.splash <= 0f) continue
            for (j in battle.units.indices) {
                val u = battle.units[j]
                if (u.side != 1 || u.hitStep != battle.stepCount || hitSlots[j]) continue
                if (abs(u.x - p.tx) > p.splash + u.radius) continue
                hitSlots[j] = true
                salvoHits++
                if (salvoHits >= 8) {
                    barrageEight = true
                    return
                }
            }
        }
    }

    private fun observeBattleState() {
        val player = battle.player
        if (player.eraVariant in 0..1) {
            if (player.era == steamEra) steamMask = steamMask or (1 shl player.eraVariant)
            if (player.era == siliconEra) siliconMask = siliconMask or (1 shl player.eraVariant)
        }
        if (!battle.endless || battle.bossesDefeated <= 0) return
        for (u in battle.units) {
            if (u.side == 1 && u.isBoss && !u.alive && u.hp <= 0f &&
                u.dying >= 0f && u.bossKind in 1..EndlessBosses.MAX_KIND
            ) bossMask = bossMask or (1 shl (u.bossKind - 1))
        }
    }
}
