package com.centurywar.screens

import com.centurywar.engine.L
import com.centurywar.game.Research
import com.centurywar.game.ResearchDef
import com.centurywar.meta.Profile

/** Zlecenie 141: badanie odblokowujące elitę i teksty z jego faktyczną nazwą i kosztem. */
internal object EliteUnlock {
    val def: ResearchDef = Research.defs.first { it.key == "r_elite" }

    fun owned(p: Profile): Boolean = p.level(def.key) > 0
    fun cost(p: Profile): Int = def.cost(p.level(def.key))
    /** Gracz nie ma elity, a stać go na badanie. */
    fun affordable(p: Profile): Boolean = !owned(p) && p.canBuy(def)

    /** „Zablokowane…” albo „Stać cię!…”; składane tylko przy otwarciu dymka. */
    fun lockedText(p: Profile): String =
        L.f(if (p.canBuy(def)) "elite141_afford" else "elite141_locked", L[def.key], cost(p))

    fun text(key: String, p: Profile): String = L.f(key, L[def.key], cost(p))
}
