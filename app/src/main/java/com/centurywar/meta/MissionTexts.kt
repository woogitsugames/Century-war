package com.centurywar.meta

import com.centurywar.engine.L
import java.util.Locale

/** Nazwy zadań są w głównej tabeli 10 języków. */
object MissionTexts {
    fun label(item: MissionItem): String {
        val key = "ms_" + item.type.name.lowercase(Locale.ROOT)
        return if (item.type == MissionType.MODIFIER_WINS) {
            L.f(key, L[item.modifier?.key ?: ""], item.target)
        } else L.f(key, item.target)
    }
}
