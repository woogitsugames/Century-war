package com.centurywar.meta

import com.centurywar.engine.L
import java.util.Locale

/** Nazwy zadań są w głównej tabeli 10 języków. */
object MissionTexts {
    fun label(item: MissionItem): String {
        val key = "ms_" + item.type.name.lowercase(Locale.ROOT)
        return when (item.type) {
            MissionType.MODIFIER_WINS ->
                L.plural(key, item.target.toLong(), L[item.modifier?.key ?: ""], item.target)
            // Numer fali i wartość pokonanych wrogów nie są liczebnikami rzeczownika.
            MissionType.WAVES, MissionType.KILL_VALUE -> L.f(key, item.target)
            else -> L.plural(key, item.target.toLong())
        }
    }
}
