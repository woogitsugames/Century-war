package com.centurywar.meta

import android.content.Context
import com.centurywar.R

/** Jedno mapowanie logicznych kluczy katalogu na identyfikatory zasobów Play Games. */
internal object PlayGamesIds {
    val leaderboardResources = intArrayOf(
        R.string.leaderboard_endless,
        R.string.leaderboard_campaign,
        R.string.leaderboard_skirmish,
        R.string.leaderboard_daily,
    )

    val achievementResources: Map<String, Int> = linkedMapOf(
        "cw_win_1" to R.string.achievement_win_1,
        "cw_win_10" to R.string.achievement_win_10,
        "cw_win_100" to R.string.achievement_win_100,
        "cw_win_500" to R.string.achievement_win_500,
        "cw_battles_25" to R.string.achievement_battles_25,
        "cw_battles_200" to R.string.achievement_battles_200,
        "cw_battles_1000" to R.string.achievement_battles_1000,
        "cw_stars_24" to R.string.achievement_stars_24,
        "cw_stars_48" to R.string.achievement_stars_48,
        "cw_stars_96" to R.string.achievement_stars_96,
        "cw_stages_16" to R.string.achievement_stages_16,
        "cw_stages_32" to R.string.achievement_stages_32,
        "cw_wave_10" to R.string.achievement_wave_10,
        "cw_wave_25" to R.string.achievement_wave_25,
        "cw_wave_50" to R.string.achievement_wave_50,
        "cw_wave_100" to R.string.achievement_wave_100,
        "cw_orbital" to R.string.achievement_orbital,
        "cw_without_turrets" to R.string.achievement_without_turrets,
        "cw_elite_survivors" to R.string.achievement_elite_survivors,
        "cw_perfect_1" to R.string.achievement_perfect_1,
        "cw_perfect_10" to R.string.achievement_perfect_10,
        "cw_kills_100" to R.string.achievement_kills_100,
        "cw_kills_1000" to R.string.achievement_kills_1000,
        "cw_kills_10000" to R.string.achievement_kills_10000,
        "cw_kills_100000" to R.string.achievement_kills_100000,
        "cw_research_10" to R.string.achievement_research_10,
        "cw_research_50" to R.string.achievement_research_50,
        "cw_research_110" to R.string.achievement_research_110,
        "cw_research_maxed" to R.string.achievement_research_maxed,
        "cw_daily_1" to R.string.achievement_daily_1,
        "cw_daily_7" to R.string.achievement_daily_7,
        "cw_daily_30" to R.string.achievement_daily_30,
        "cw_insane_win" to R.string.achievement_insane_win,
        "cw_all_doctrines" to R.string.achievement_all_doctrines,
        "cw_armory_10" to R.string.achievement_armory_10,
        "cw_turrets_50" to R.string.achievement_turrets_50,
        "cw_barrages_100" to R.string.achievement_barrages_100,
        "cw_elites_100" to R.string.achievement_elites_100,
        "cw_evolutions_50" to R.string.achievement_evolutions_50,
        "cw_fast_win" to R.string.achievement_fast_win,
        "cw_steam_twins" to R.string.achievement_steam_twins,
        "cw_silicon_twins" to R.string.achievement_silicon_twins,
        "cw_rain_victory" to R.string.achievement_rain_victory,
        "cw_wind_victory" to R.string.achievement_wind_victory,
        "cw_night_victory" to R.string.achievement_night_victory,
        "cw_barrage_eight" to R.string.achievement_barrage_eight,
        "cw_boss_knockback" to R.string.achievement_boss_knockback,
        "cw_boss_aura" to R.string.achievement_boss_aura,
        "cw_boss_shield" to R.string.achievement_boss_shield,
        "cw_boss_recruiter" to R.string.achievement_boss_recruiter,
        "cw_boss_rage" to R.string.achievement_boss_rage,
        "cw_weekly_gold" to R.string.achievement_weekly_gold,
        "cw_custom_three" to R.string.achievement_custom_three,
        "cw_replay_record" to R.string.achievement_replay_record,
        "cw_card_medics_30" to R.string.achievement_card_medics_30,
        "cw_card_spark_30" to R.string.achievement_card_spark_30,
        "cw_card_guard_30" to R.string.achievement_card_guard_30,
        "cw_card_recall_30" to R.string.achievement_card_recall_30,
        "cw_card_new_50" to R.string.achievement_card_new_50,
        "cw_weekly_rules_3" to R.string.achievement_weekly_rules_3,
        "cw_weekly_rules_6" to R.string.achievement_weekly_rules_6,
        "cw_hard_perfect" to R.string.achievement_hard_perfect,
        "cw_daily_events_6" to R.string.achievement_daily_events_6,
        "cw_modifier_victories_10" to R.string.achievement_modifier_victories_10,
        "cw_doctrine_victories_13" to R.string.achievement_doctrine_victories_13,
    )

    private val appIdPattern = Regex("[1-9][0-9]*")
    private val serviceIdPattern = Regex("[A-Za-z][A-Za-z0-9_-]{11,127}")

    private fun validId(value: String): Boolean =
        !value.contains("REPLACE_ME", ignoreCase = true) &&
            serviceIdPattern.matches(value.trim())

    /** Walidacja parametrów niezależna od Androida. */
    fun isConfiguredValues(
        projectId: String, leaderboardIds: List<String>, achievementIds: List<String>,
    ): Boolean {
        if (!appIdPattern.matches(projectId.trim()) ||
            leaderboardIds.size != leaderboardResources.size ||
            achievementIds.size != achievementResources.size) return false
        val ids = leaderboardIds + achievementIds
        return ids.all(::validId) && ids.size == ids.toSet().size
    }

    fun isConfigured(context: Context): Boolean =
        context.resources.getBoolean(R.bool.play_games_configured) &&
            isConfiguredValues(
                context.getString(R.string.game_services_project_id),
                leaderboardResources.map { context.getString(it) },
                achievementResources.values.map { context.getString(it) },
            )

    fun achievementId(context: Context, key: String): String? =
        achievementResources[key]?.let { context.getString(it) }?.takeIf(::validId)
}
