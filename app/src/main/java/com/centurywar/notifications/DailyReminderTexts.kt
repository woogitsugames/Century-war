package com.centurywar.notifications

import com.centurywar.game.Modifier
import java.util.Locale

/** Osobny słownik PL/EN, niezależny od tabeli napisów ekranu gry. */
object DailyReminderTexts {
    fun polish(lang: String): Boolean = when (lang) {
        "pl" -> true
        "en" -> false
        else -> Locale.getDefault().language == "pl"
    }

    fun channelName(lang: String): String =
        if (polish(lang)) "Wyzwanie dnia" else "Daily challenge"

    fun title(lang: String): String =
        if (polish(lang)) "Century War — wyzwanie dnia" else "Century War — daily challenge"

    fun channelDescription(lang: String): String =
        if (polish(lang)) "Ciche przypomnienie o wyzwaniu dnia" else "Quiet daily challenge reminder"

    fun body(modifiers: Set<Modifier>, lang: String): String {
        val pl = polish(lang)
        val names = Modifier.values().filter { it in modifiers }.joinToString(", ") { modifierName(it, pl) }
        return if (pl) "Nowe wyzwanie dnia czeka! Modyfikatory: $names"
        else "A new daily challenge awaits! Modifiers: $names"
    }

    fun settingNotifications(lang: String): String =
        if (polish(lang)) "Powiadomienia" else "Notifications"

    fun settingHour(lang: String): String =
        if (polish(lang)) "Godzina przypomnienia" else "Reminder hour"

    private fun modifierName(modifier: Modifier, pl: Boolean): String = when (modifier) {
        Modifier.DOUBLE_INCOME -> if (pl) "Podwójny przychód" else "Double income"
        Modifier.FRAGILE_BASES -> if (pl) "Kruche bazy" else "Fragile bases"
        Modifier.FAST_UNITS -> if (pl) "Szybkie wojska" else "Fast troops"
        Modifier.NO_TURRETS -> if (pl) "Bez wież" else "No turrets"
        Modifier.RANGED_HORDE -> if (pl) "Horda strzelców" else "Ranged horde"
        Modifier.GLASS_CANNON -> if (pl) "Szklane działa" else "Glass cannons"
        Modifier.RICH_START -> if (pl) "Bogaty start" else "Rich start"
        Modifier.SLOW_XP -> if (pl) "Wolny rozwój" else "Slow progress"
        Modifier.ARMORED_FOE -> if (pl) "Opancerzony wróg" else "Armored foe"
        Modifier.FOG_OF_WAR -> if (pl) "Mgła wojny" else "Fog of war"
    }
}
