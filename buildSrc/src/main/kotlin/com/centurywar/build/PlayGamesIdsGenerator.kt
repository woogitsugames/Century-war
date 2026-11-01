package com.centurywar.build

import java.io.StringReader
import java.util.Properties

/** Jedyny schemat ID PGS; wartości pochodzą wyłącznie z pliku properties. */
object PlayGamesIdsGenerator {
    val leaderboardKeys = listOf(
        "leaderboard_endless", "leaderboard_campaign",
        "leaderboard_skirmish", "leaderboard_daily",
    )

    val achievementNames = listOf(
        "win_1",
        "win_10",
        "win_100",
        "win_500",
        "battles_25",
        "battles_200",
        "battles_1000",
        "stars_24",
        "stars_48",
        "stars_96",
        "stages_16",
        "stages_32",
        "wave_10",
        "wave_25",
        "wave_50",
        "wave_100",
        "orbital",
        "without_turrets",
        "elite_survivors",
        "perfect_1",
        "perfect_10",
        "kills_100",
        "kills_1000",
        "kills_10000",
        "kills_100000",
        "research_10",
        "research_50",
        "research_110",
        "research_maxed",
        "daily_1",
        "daily_7",
        "daily_30",
        "insane_win",
        "all_doctrines",
        "armory_10",
        "turrets_50",
        "barrages_100",
        "elites_100",
        "evolutions_50",
        "fast_win",
        "steam_twins",
        "silicon_twins",
        "rain_victory",
        "wind_victory",
        "night_victory",
        "barrage_eight",
        "boss_knockback",
        "boss_aura",
        "boss_shield",
        "boss_recruiter",
        "boss_rage",
        "weekly_gold",
        "custom_three",
        "replay_record",
        "card_medics_30",
        "card_spark_30",
        "card_guard_30",
        "card_recall_30",
        "card_new_50",
        "weekly_rules_3",
        "weekly_rules_6",
        "hard_perfect",
        "daily_events_6",
        "modifier_victories_10",
        "doctrine_victories_13",
        "campaign_hard",
    )

    val requiredKeys = listOf("app_id") + leaderboardKeys +
        achievementNames.map { "achievement_" + it }

    private val appIdPattern = Regex("[1-9][0-9]*")
    // Play Console przydziela niejawne identyfikatory alfanumeryczne (np. CgkI...).
    private val serviceIdPattern = Regex("[A-Za-z][A-Za-z0-9_-]{11,127}")

    fun validAppId(value: String): Boolean = appIdPattern.matches(value)
    fun validServiceId(value: String): Boolean =
        !value.contains("REPLACE_ME", ignoreCase = true) && serviceIdPattern.matches(value)

    /** Niekompletny debug jest całkowicie lokalny; release zawsze kończy się błędem. */
    fun render(source: String?, release: Boolean): String {
        val props = Properties()
        if (source != null) props.load(StringReader(source))
        val values = requiredKeys.associateWith { props.getProperty(it)?.trim().orEmpty() }
        val unexpected = props.stringPropertyNames() - requiredKeys.toSet()
        require(unexpected.isEmpty()) {
            "Nieznane klucze w play_games_ids.properties: " + unexpected.sorted().joinToString(", ")
        }
        val invalid = requiredKeys.filter { key ->
            val value = values.getValue(key)
            value.isNotEmpty() && !value.contains("REPLACE_ME", ignoreCase = true) &&
                (if (key == "app_id") !validAppId(value) else !validServiceId(value))
        }
        require(invalid.isEmpty()) {
            "Niepoprawny format Play Games ID: " + invalid.joinToString(", ")
        }
        val missing = requiredKeys.filter {
            values.getValue(it).isEmpty() || values.getValue(it).contains("REPLACE_ME", ignoreCase = true)
        }
        if (missing.isNotEmpty()) {
            require(!release) {
                "Brak konfiguracji Play Games dla release: " + missing.joinToString(", ")
            }
            return xml(null)
        }
        val serviceIds = requiredKeys.drop(1).map { values.getValue(it) }
        require(serviceIds.size == serviceIds.toSet().size) {
            "Zduplikowane identyfikatory rankingów lub osiągnięć Play Games."
        }
        return xml(values)
    }

    private fun xml(values: Map<String, String>?): String = buildString {
        appendLine("<?xml version=\"1.0\" encoding=\"utf-8\"?>")
        appendLine("<resources>")
        val pairs = listOf("game_services_project_id" to "app_id") +
            requiredKeys.drop(1).map { it to it }
        for ((resourceName, key) in pairs) {
            append("    <string name=\"").append(resourceName)
                .append("\" translatable=\"false\">")
                .append(values?.get(key) ?: "REPLACE_ME")
                .appendLine("</string>")
        }
        append("    <bool name=\"play_games_configured\">")
            .append(values != null).appendLine("</bool>")
        appendLine("</resources>")
    }
}
