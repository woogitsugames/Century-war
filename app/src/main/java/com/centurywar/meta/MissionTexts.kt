package com.centurywar.meta

import com.centurywar.game.Modifier

/** Niezależna tabela PL/EN, bez modyfikowania silnika tłumaczeń ekranów. */
object MissionTexts {
    val sectionDaily = arrayOf("Misje dzienne", "Daily missions")
    val sectionWeekly = arrayOf("Misje tygodniowe", "Weekly missions")
    val loginStreak = arrayOf("Seria logowań", "Login streak")
    val claim = arrayOf("Odbierz", "Claim")
    val collected = arrayOf("Odebrano", "Claimed")
    val completed = arrayOf("Ukończono", "Completed")

    private val pl = mapOf(
        MissionType.BATTLES to "Rozegrane bitwy: {n}",
        MissionType.WINS to "Zwycięstwa: {n}",
        MissionType.KILLS to "Pokonani wrogowie: {n}",
        MissionType.UNITS to "Wyszkolone jednostki: {n}",
        MissionType.ELITES to "Wyszkolone jednostki elitarne: {n}",
        MissionType.RANGED to "Wyszkoleni strzelcy: {n}",
        MissionType.HEAVY to "Wyszkolone jednostki ciężkie: {n}",
        MissionType.EVOLUTIONS to "Awanse epoki: {n}",
        MissionType.RANKS to "Zdobyte rangi weteranów: {n}",
        MissionType.DOCTRINES to "Wybrane doktryny: {n}",
        MissionType.WAVES to "Osiągnij falę: {n}",
        MissionType.MODIFIER_WINS to "Zwycięstwa z modyfikatorem „{modifier}”: {n}",
        MissionType.RESEARCH to "Kupione poziomy badań: {n}",
        MissionType.CAMPAIGN_WINS to "Zwycięstwa w kampanii: {n}",
        MissionType.SKIRMISH_WINS to "Zwycięstwa w potyczkach: {n}",
        MissionType.DAILY_BATTLES to "Wyzwania dnia rozegrane: {n}",
        MissionType.ENDLESS_BATTLES to "Oblężenia rozegrane: {n}",
        MissionType.HEALTHY_WINS to "Wygrane przy minimum 75% HP bazy: {n}",
        MissionType.STARS to "Gwiazdki zdobyte w bitwach: {n}",
        MissionType.ARMORY to "Ulepszenia zbrojowni w bitwach: {n}",
        MissionType.TURRETS to "Zbudowane wieże: {n}",
        MissionType.BARRAGES to "Użyte ostrzały specjalne: {n}",
        MissionType.RALLIES to "Użyte Zrywy: {n}",
        MissionType.MINUTES to "Minuty na polu bitwy: {n}",
        MissionType.SCORE to "Punkty z bitew: {n}",
        MissionType.THREE_STARS to "Wygrane z trzema gwiazdkami: {n}",
        MissionType.GOLD to "Złoto zarobione w bitwach: {n}",
        MissionType.KILL_VALUE to "Wartość pokonanych wrogów: {n}",
        MissionType.HARD_WINS to "Wygrane z siłą wroga ≥ 120%: {n}",
    )

    private val en = mapOf(
        MissionType.BATTLES to "Battles played: {n}",
        MissionType.WINS to "Victories: {n}",
        MissionType.KILLS to "Enemies defeated: {n}",
        MissionType.UNITS to "Units trained: {n}",
        MissionType.ELITES to "Elite units trained: {n}",
        MissionType.RANGED to "Ranged units trained: {n}",
        MissionType.HEAVY to "Heavy units trained: {n}",
        MissionType.EVOLUTIONS to "Era advancements: {n}",
        MissionType.RANKS to "Veteran ranks gained: {n}",
        MissionType.DOCTRINES to "Doctrines chosen: {n}",
        MissionType.WAVES to "Reach wave: {n}",
        MissionType.MODIFIER_WINS to "Victories with modifier '{modifier}': {n}",
        MissionType.RESEARCH to "Research levels purchased: {n}",
        MissionType.CAMPAIGN_WINS to "Campaign victories: {n}",
        MissionType.SKIRMISH_WINS to "Skirmish victories: {n}",
        MissionType.DAILY_BATTLES to "Daily challenges played: {n}",
        MissionType.ENDLESS_BATTLES to "Endless sieges played: {n}",
        MissionType.HEALTHY_WINS to "Wins with at least 75% base HP: {n}",
        MissionType.STARS to "Stars earned in battles: {n}",
        MissionType.ARMORY to "Armory upgrades in battles: {n}",
        MissionType.TURRETS to "Turrets built: {n}",
        MissionType.BARRAGES to "Special barrages used: {n}",
        MissionType.RALLIES to "Rallies used: {n}",
        MissionType.MINUTES to "Minutes in battle: {n}",
        MissionType.SCORE to "Battle score points: {n}",
        MissionType.THREE_STARS to "Three-star victories: {n}",
        MissionType.GOLD to "Gold earned in battles: {n}",
        MissionType.KILL_VALUE to "Value of defeated enemies: {n}",
        MissionType.HARD_WINS to "Wins against 120%+ enemy power: {n}",
    )

    fun label(item: MissionItem, polish: Boolean): String {
        val template = (if (polish) pl else en)[item.type] ?: item.type.name
        return template.replace("{n}", item.target.toString())
            .replace("{modifier}", modifierName(item.modifier, polish))
    }

    fun modifierName(modifier: Modifier?, polish: Boolean): String = when (modifier) {
        Modifier.DOUBLE_INCOME -> if (polish) "Podwójny przychód" else "Double income"
        Modifier.FRAGILE_BASES -> if (polish) "Kruche bazy" else "Fragile bases"
        Modifier.FAST_UNITS -> if (polish) "Szybkie jednostki" else "Fast units"
        Modifier.NO_TURRETS -> if (polish) "Bez wież" else "No turrets"
        Modifier.RANGED_HORDE -> if (polish) "Horda strzelców" else "Ranged horde"
        Modifier.GLASS_CANNON -> if (polish) "Szklane działa" else "Glass cannon"
        Modifier.RICH_START -> if (polish) "Bogaty start" else "Rich start"
        Modifier.SLOW_XP -> if (polish) "Wolniejsze XP" else "Slow XP"
        Modifier.ARMORED_FOE -> if (polish) "Opancerzony wróg" else "Armored foe"
        Modifier.FOG_OF_WAR -> if (polish) "Mgła wojny" else "Fog of war"
        null -> ""
    }
}
