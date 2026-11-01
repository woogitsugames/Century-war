package com.centurywar.meta

import com.centurywar.game.Battle
import com.centurywar.game.BattleConfig
import com.centurywar.game.Campaign
import com.centurywar.game.ChallengeRuleSets
import com.centurywar.game.DailyEvent
import com.centurywar.game.Defs
import com.centurywar.game.Mode
import com.centurywar.game.Modifier
import com.centurywar.game.Weather
import com.centurywar.game.Weekly

enum class AchievementCategory { COMBAT, CAMPAIGN, ENDLESS, RESEARCH, DAILY, TACTICS }

enum class AchievementMetric {
    WINS, BATTLES, STARS, STAGES, WAVES, KILLS, RESEARCH_LEVELS, MAXED_RESEARCH,
    DAILY_DAYS, INSANE_WINS, DOCTRINES, ORBITAL_REACHED, NO_TURRET_WINS,
    ELITE_SAVED_WINS, PERFECT_WINS, ARMORY_TEN, TURRETS_BUILT,
    BARRAGES_CAST, ELITES_TRAINED, EVOLUTIONS, FAST_WINS,
    STEAM_VARIANTS, SILICON_VARIANTS, RAIN_WINS, WIND_WINS, NIGHT_WINS,
    BARRAGE_EIGHT, BOSS_KNOCKBACK, BOSS_AURA, BOSS_SHIELD, BOSS_RECRUITER,
    BOSS_RAGE, WEEKLY_GOLD, CUSTOM_THREE, REPLAY_RECORD,
    CARD_MEDICS_30, CARD_SPARK_30, CARD_GUARD_30, CARD_RECALL_30,
    CARD_NEW_50, WEEKLY_RULESETS, HARD_PERFECT, DAILY_EVENTS,
    MODIFIER_VICTORIES, DOCTRINE_VICTORIES,
    HARD_CAMPAIGN,
}

data class AchievementDef(
    val id: String,
    val threshold: Long,
    val medalReward: Int,
    val category: AchievementCategory,
    val hidden: Boolean,
    val pgsKey: String,
    val metric: AchievementMetric,
)

/**
 * Osiągnięcia są rozliczane po bitwie lub zakupie badania, nigdy w kroku symulacji.
 * Kolejność definicji jest stała, a ponowne rozliczenie tej samej bitwy jest ignorowane.
 */
object Achievements {
    private fun def(
        id: String, metric: AchievementMetric, threshold: Long, reward: Int,
        category: AchievementCategory, hidden: Boolean = false,
    ) = AchievementDef(id, threshold, reward, category, hidden, "cw_$id", metric)

    val all: List<AchievementDef> = listOf(
        def("win_1", AchievementMetric.WINS, 1, 15, AchievementCategory.COMBAT),
        def("win_10", AchievementMetric.WINS, 10, 45, AchievementCategory.COMBAT),
        def("win_100", AchievementMetric.WINS, 100, 180, AchievementCategory.COMBAT),
        def("win_500", AchievementMetric.WINS, 500, 700, AchievementCategory.COMBAT),
        def("battles_25", AchievementMetric.BATTLES, 25, 35, AchievementCategory.COMBAT),
        def("battles_200", AchievementMetric.BATTLES, 200, 160, AchievementCategory.COMBAT),
        def("battles_1000", AchievementMetric.BATTLES, 1000, 650, AchievementCategory.COMBAT),
        def("stars_24", AchievementMetric.STARS, 24, 75, AchievementCategory.CAMPAIGN),
        def("stars_48", AchievementMetric.STARS, 48, 170, AchievementCategory.CAMPAIGN),
        def("stars_96", AchievementMetric.STARS, Campaign.stages.size * 3L, 500, AchievementCategory.CAMPAIGN),
        def("stages_16", AchievementMetric.STAGES, 16, 85, AchievementCategory.CAMPAIGN),
        def("stages_32", AchievementMetric.STAGES, Campaign.stages.size.toLong(), 250, AchievementCategory.CAMPAIGN),
        def("wave_10", AchievementMetric.WAVES, 10, 40, AchievementCategory.ENDLESS),
        def("wave_25", AchievementMetric.WAVES, 25, 120, AchievementCategory.ENDLESS),
        def("wave_50", AchievementMetric.WAVES, 50, 280, AchievementCategory.ENDLESS),
        def("wave_100", AchievementMetric.WAVES, 100, 650, AchievementCategory.ENDLESS, true),
        def("orbital", AchievementMetric.ORBITAL_REACHED, 1, 120, AchievementCategory.TACTICS),
        def("without_turrets", AchievementMetric.NO_TURRET_WINS, 1, 70, AchievementCategory.TACTICS),
        def("elite_survivors", AchievementMetric.ELITE_SAVED_WINS, 1, 90, AchievementCategory.TACTICS),
        def("perfect_1", AchievementMetric.PERFECT_WINS, 1, 85, AchievementCategory.TACTICS),
        def("perfect_10", AchievementMetric.PERFECT_WINS, 10, 350, AchievementCategory.TACTICS, true),
        def("kills_100", AchievementMetric.KILLS, 100, 25, AchievementCategory.COMBAT),
        def("kills_1000", AchievementMetric.KILLS, 1000, 95, AchievementCategory.COMBAT),
        def("kills_10000", AchievementMetric.KILLS, 10000, 350, AchievementCategory.COMBAT),
        def("kills_100000", AchievementMetric.KILLS, 100000, 900, AchievementCategory.COMBAT, true),
        def("research_10", AchievementMetric.RESEARCH_LEVELS, 10, 55, AchievementCategory.RESEARCH),
        def("research_50", AchievementMetric.RESEARCH_LEVELS, 50, 180, AchievementCategory.RESEARCH),
        def("research_110", AchievementMetric.RESEARCH_LEVELS, 110, 550, AchievementCategory.RESEARCH),
        def("research_maxed", AchievementMetric.MAXED_RESEARCH, 1, 100, AchievementCategory.RESEARCH),
        def("daily_1", AchievementMetric.DAILY_DAYS, 1, 20, AchievementCategory.DAILY),
        def("daily_7", AchievementMetric.DAILY_DAYS, 7, 100, AchievementCategory.DAILY),
        def("daily_30", AchievementMetric.DAILY_DAYS, 30, 350, AchievementCategory.DAILY),
        def("insane_win", AchievementMetric.INSANE_WINS, 1, 130, AchievementCategory.TACTICS),
        def("all_doctrines", AchievementMetric.DOCTRINES, com.centurywar.game.Doctrine.values().size.toLong(), 250, AchievementCategory.TACTICS),
        def("armory_10", AchievementMetric.ARMORY_TEN, 1, 110, AchievementCategory.TACTICS),
        def("turrets_50", AchievementMetric.TURRETS_BUILT, 50, 130, AchievementCategory.TACTICS),
        def("barrages_100", AchievementMetric.BARRAGES_CAST, 100, 160, AchievementCategory.COMBAT),
        def("elites_100", AchievementMetric.ELITES_TRAINED, 100, 210, AchievementCategory.COMBAT),
        def("evolutions_50", AchievementMetric.EVOLUTIONS, 50, 170, AchievementCategory.TACTICS),
        def("fast_win", AchievementMetric.FAST_WINS, 1, 95, AchievementCategory.TACTICS, true),
        def("steam_twins", AchievementMetric.STEAM_VARIANTS, 2, 140, AchievementCategory.TACTICS),
        def("silicon_twins", AchievementMetric.SILICON_VARIANTS, 2, 180, AchievementCategory.TACTICS),
        def("rain_victory", AchievementMetric.RAIN_WINS, 1, 55, AchievementCategory.TACTICS),
        def("wind_victory", AchievementMetric.WIND_WINS, 1, 55, AchievementCategory.TACTICS),
        def("night_victory", AchievementMetric.NIGHT_WINS, 1, 65, AchievementCategory.TACTICS),
        def("barrage_eight", AchievementMetric.BARRAGE_EIGHT, 1, 160, AchievementCategory.COMBAT, true),
        def("boss_knockback", AchievementMetric.BOSS_KNOCKBACK, 1, 70, AchievementCategory.ENDLESS),
        def("boss_aura", AchievementMetric.BOSS_AURA, 1, 90, AchievementCategory.ENDLESS),
        def("boss_shield", AchievementMetric.BOSS_SHIELD, 1, 110, AchievementCategory.ENDLESS),
        def("boss_recruiter", AchievementMetric.BOSS_RECRUITER, 1, 130, AchievementCategory.ENDLESS),
        def("boss_rage", AchievementMetric.BOSS_RAGE, 1, 150, AchievementCategory.ENDLESS),
        def("weekly_gold", AchievementMetric.WEEKLY_GOLD, 1, 200, AchievementCategory.ENDLESS),
        def("custom_three", AchievementMetric.CUSTOM_THREE, 1, 105, AchievementCategory.TACTICS),
        def("replay_record", AchievementMetric.REPLAY_RECORD, 1, 90, AchievementCategory.TACTICS),
        // Nowe karty, cykliczne reguły i zróżnicowane taktyki (zlecenie 130).
        def("card_medics_30", AchievementMetric.CARD_MEDICS_30, 1, 140, AchievementCategory.ENDLESS),
        def("card_spark_30", AchievementMetric.CARD_SPARK_30, 1, 180, AchievementCategory.ENDLESS),
        def("card_guard_30", AchievementMetric.CARD_GUARD_30, 1, 180, AchievementCategory.ENDLESS),
        def("card_recall_30", AchievementMetric.CARD_RECALL_30, 1, 240, AchievementCategory.ENDLESS),
        def("card_new_50", AchievementMetric.CARD_NEW_50, 1, 330, AchievementCategory.ENDLESS, true),
        def("weekly_rules_3", AchievementMetric.WEEKLY_RULESETS, 3, 190, AchievementCategory.ENDLESS),
        def("weekly_rules_6", AchievementMetric.WEEKLY_RULESETS, 6, 480, AchievementCategory.ENDLESS),
        def("hard_perfect", AchievementMetric.HARD_PERFECT, 1, 175, AchievementCategory.CAMPAIGN),
        def("daily_events_6", AchievementMetric.DAILY_EVENTS, 6, 300, AchievementCategory.DAILY),
        def("modifier_victories_10", AchievementMetric.MODIFIER_VICTORIES, 10, 230, AchievementCategory.TACTICS),
        def("doctrine_victories_13", AchievementMetric.DOCTRINE_VICTORIES,
            com.centurywar.game.Doctrine.values().size.toLong(), 400, AchievementCategory.TACTICS),
        def("campaign_hard", AchievementMetric.HARD_CAMPAIGN, Campaign.stages.size.toLong(), 600, AchievementCategory.CAMPAIGN),
    )

    fun byId(id: String): AchievementDef? = all.firstOrNull { it.id == id }

    fun isUnlocked(profile: Profile, id: String): Boolean = id in profile.unlockedAchievements

    /** Surowy postęp; UI może ograniczyć go do progu z definicji. */
    fun progress(profile: Profile, definition: AchievementDef): Long = when (definition.metric) {
        AchievementMetric.WINS -> profile.wins.toLong()
        AchievementMetric.BATTLES -> profile.battles.toLong()
        AchievementMetric.STARS -> profile.totalStars().toLong()
        AchievementMetric.STAGES -> profile.stars.count { it > 0 }.toLong()
        AchievementMetric.HARD_CAMPAIGN -> profile.hardStagesCleared().toLong()
        AchievementMetric.WAVES -> profile.bestWave.toLong()
        AchievementMetric.KILLS -> profile.totalKills
        AchievementMetric.RESEARCH_LEVELS -> Profile.research.sumOf { profile.level(it.key).toLong() }
        AchievementMetric.MAXED_RESEARCH -> Profile.research.count {
            profile.level(it.key) >= it.max
        }.toLong()
        AchievementMetric.DOCTRINES -> Integer.bitCount(profile.achievementDoctrineMask).toLong()
        AchievementMetric.STEAM_VARIANTS, AchievementMetric.SILICON_VARIANTS ->
            java.lang.Long.bitCount((profile.achievementCounters[definition.metric.name] ?: 0L) and 3L).toLong()
        AchievementMetric.WEEKLY_RULESETS, AchievementMetric.DAILY_EVENTS ->
            java.lang.Long.bitCount((profile.achievementCounters[definition.metric.name] ?: 0L) and 63L).toLong()
        AchievementMetric.MODIFIER_VICTORIES ->
            java.lang.Long.bitCount((profile.achievementCounters[definition.metric.name] ?: 0L) and
                ((1L shl Modifier.values().size) - 1L)).toLong()
        AchievementMetric.DOCTRINE_VICTORIES ->
            java.lang.Long.bitCount((profile.achievementCounters[definition.metric.name] ?: 0L) and
                ((1L shl com.centurywar.game.Doctrine.values().size) - 1L)).toLong()
        else -> profile.achievementCounters[definition.metric.name] ?: 0L
    }

    private fun add(profile: Profile, metric: AchievementMetric, amount: Long = 1L) {
        if (amount <= 0L) return
        val old = (profile.achievementCounters[metric.name] ?: 0L).coerceAtLeast(0L)
        profile.achievementCounters[metric.name] =
            if (amount > Long.MAX_VALUE - old) Long.MAX_VALUE else old + amount
    }

    private fun rememberVariants(profile: Profile, metric: AchievementMetric, mask: Int) {
        if (mask == 0) return
        val key = metric.name
        profile.achievementCounters[key] =
            (((profile.achievementCounters[key] ?: 0L).toInt() or mask) and 3).toLong()
    }

    /** Maski rozliczane tylko po zakończonej bitwie; brak nowych pól w symulacji. */
    private fun rememberMask(profile: Profile, metric: AchievementMetric, mask: Int) {
        if (mask == 0) return
        val key = metric.name
        profile.achievementCounters[key] = (profile.achievementCounters[key] ?: 0L) or mask.toLong()
    }

    private fun markOnce(profile: Profile, metric: AchievementMetric) {
        profile.achievementCounters[metric.name] = 1L
    }

    private fun defeatedBossMask(battle: Battle): Int {
        if (battle.bossesDefeated <= 0) return 0
        var mask = 0
        for (u in battle.units) {
            if (u.side == 1 && u.isBoss && !u.alive && u.hp <= 0f &&
                u.bossKind in 1..5) mask = mask or (1 shl (u.bossKind - 1))
        }
        return mask
    }

    private fun unlockReady(profile: Profile, online: OnlineAchievements): List<AchievementDef> {
        val unlocked = ArrayList<AchievementDef>()
        for (a in all) {
            if (a.id in profile.unlockedAchievements || progress(profile, a) < a.threshold) continue
            profile.unlockedAchievements.add(a.id)
            profile.medals = (profile.medals.toLong() + a.medalReward).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            unlocked.add(a)
            // Utrata połączenia z PGS nie może cofnąć lokalnego odblokowania.
            try { online.unlock(a.pgsKey) } catch (_: Exception) { }
        }
        return unlocked
    }

    /**
     * Zastępuje aktualizację battles/wins/totalKills/playSeconds w BattleScreen.
     * Wywołać raz po aktualizacji gwiazdek, najlepszej fali i nagrody za bitwę.
     */
    fun onBattleEnd(
        profile: Profile, battle: Battle, cfg: BattleConfig, won: Boolean,
        online: OnlineAchievements = OnlineAchievements.NoOp,
        events: AchievementBattleTracker? = null,
    ): List<AchievementDef> {
        if (battle.achievementsRecorded || !battle.over || cfg !== battle.cfg || won != (battle.winner == 0)) {
            return emptyList()
        }
        battle.achievementsRecorded = true
        events?.afterStep()

        if (profile.battles < Int.MAX_VALUE) profile.battles++
        if (won && profile.wins < Int.MAX_VALUE) profile.wins++
        val kills = battle.player.kills.toLong().coerceAtLeast(0L)
        profile.totalKills = if (kills > Long.MAX_VALUE - profile.totalKills) Long.MAX_VALUE else profile.totalKills + kills
        val elapsed = battle.time.toLong().coerceAtLeast(0L)
        profile.playSeconds = if (elapsed > Long.MAX_VALUE - profile.playSeconds) Long.MAX_VALUE else profile.playSeconds + elapsed
        if (cfg.mode == Mode.ENDLESS && cfg.weekly < 0L && battle.wave > profile.bestWave) profile.bestWave = battle.wave

        val player = battle.player
        if (cfg.mode == Mode.DAILY && cfg.seed >= 0L && (cfg.seed in profile.achievementDailySeeds || profile.achievementDailySeeds.size < 4096) && profile.achievementDailySeeds.add(cfg.seed)) {
            add(profile, AchievementMetric.DAILY_DAYS)
        }
        if (cfg.mode == Mode.SKIRMISH && won && cfg.aiPower >= 1.6f && Modifier.FOG_OF_WAR !in cfg.modifiers) {
            add(profile, AchievementMetric.INSANE_WINS)
        }
        if (won && player.slots > 0 && player.turretsBuilt == 0 && Modifier.NO_TURRETS !in cfg.modifiers) {
            add(profile, AchievementMetric.NO_TURRET_WINS)
        }
        if (won && player.elitesTrained > 0 && player.elitesLost == 0) {
            add(profile, AchievementMetric.ELITE_SAVED_WINS)
        }
        if (won && player.baseDamageTaken <= 0f) add(profile, AchievementMetric.PERFECT_WINS)
        if (won && cfg.mode == Mode.CAMPAIGN && cfg.stageIndex >= 0 &&
            cfg.aiPower >= 1.2f && player.baseDamageTaken <= 0f
        ) markOnce(profile, AchievementMetric.HARD_PERFECT)
        if (player.armory >= 10) add(profile, AchievementMetric.ARMORY_TEN)
        if (cfg.startEra < Defs.ERA_COUNT - 1 && player.era == Defs.ERA_COUNT - 1 &&
            player.erasAdvanced > 0
        ) add(profile, AchievementMetric.ORBITAL_REACHED)
        if (won && battle.time <= 300f) add(profile, AchievementMetric.FAST_WINS)

        // Warianty liczą się dopiero po wygranej; maski 1 i 2 przechodzą między bitwami.
        if (won) {
            val variant = player.eraVariant
            val eraKey = Defs.eraKeys.getOrNull(player.era)
            val currentMask = if (variant in 0..1) 1 shl variant else 0
            rememberVariants(profile, AchievementMetric.STEAM_VARIANTS,
                events?.steamMask ?: if (eraKey == "era_steam") currentMask else 0)
            rememberVariants(profile, AchievementMetric.SILICON_VARIANTS,
                events?.siliconMask ?: if (eraKey == "era_silicon") currentMask else 0)
            if (cfg.mode != Mode.ENDLESS) when (cfg.weather) {
                Weather.RAIN -> add(profile, AchievementMetric.RAIN_WINS)
                Weather.WIND -> add(profile, AchievementMetric.WIND_WINS)
                Weather.NIGHT -> add(profile, AchievementMetric.NIGHT_WINS)
                Weather.CLEAR -> {}
            }
            if (cfg.mode == Mode.SKIRMISH && cfg.modifiers.size == 3)
                add(profile, AchievementMetric.CUSTOM_THREE)
            val dailyEvent = cfg.dailyEvent
            if (cfg.mode == Mode.DAILY && dailyEvent != null)
                rememberMask(profile, AchievementMetric.DAILY_EVENTS, 1 shl dailyEvent.ordinal)
            var modifierMask = 0
            for (modifier in cfg.modifiers) modifierMask = modifierMask or (1 shl modifier.ordinal)
            rememberMask(profile, AchievementMetric.MODIFIER_VICTORIES, modifierMask)
            var winningDoctrineMask = 0
            for (doctrine in player.doctrines)
                winningDoctrineMask = winningDoctrineMask or (1 shl doctrine.ordinal)
            rememberMask(profile, AchievementMetric.DOCTRINE_VICTORIES, winningDoctrineMask)
        }
        if (events?.barrageEight == true) add(profile, AchievementMetric.BARRAGE_EIGHT)
        if (cfg.mode == Mode.ENDLESS) {
            val newCardMask = events?.newCardMask ?: AchievementBattleTracker.newCardMaskFor(battle)
            if (battle.wave >= 30) {
                if ((newCardMask and 1) != 0) markOnce(profile, AchievementMetric.CARD_MEDICS_30)
                if ((newCardMask and 2) != 0) markOnce(profile, AchievementMetric.CARD_SPARK_30)
                if ((newCardMask and 4) != 0) markOnce(profile, AchievementMetric.CARD_GUARD_30)
                if ((newCardMask and 8) != 0) markOnce(profile, AchievementMetric.CARD_RECALL_30)
            }
            if (battle.wave >= 50 && newCardMask != 0)
                markOnce(profile, AchievementMetric.CARD_NEW_50)
            val rule = if (cfg.weekly >= 0L) Weekly.ruleSet(cfg.weekly) else null
            if (rule != null && battle.wave >= Weekly.threshold(1) &&
                cfg.weeklyCard == rule.weeklyCard &&
                cfg.modifiers.size == 2 &&
                rule.first in cfg.modifiers && rule.second in cfg.modifiers
            ) {
                val offset = (cfg.weekly - Weekly.firstRuleSetWeek()) / Weekly.RULE_SET_EVERY
                val ruleIndex = (offset % ChallengeRuleSets.COUNT).toInt()
                rememberMask(profile, AchievementMetric.WEEKLY_RULESETS, 1 shl ruleIndex)
            }
            val bossMask = events?.bossMask ?: defeatedBossMask(battle)
            val bossMetrics = arrayOf(AchievementMetric.BOSS_KNOCKBACK, AchievementMetric.BOSS_AURA,
                AchievementMetric.BOSS_SHIELD, AchievementMetric.BOSS_RECRUITER, AchievementMetric.BOSS_RAGE)
            for (i in bossMetrics.indices) {
                if ((bossMask and (1 shl i)) != 0 &&
                    (profile.achievementCounters[bossMetrics[i].name] ?: 0L) == 0L
                ) add(profile, bossMetrics[i])
            }
            if (cfg.weekly >= 0L && battle.wave >= Weekly.threshold(2))
                add(profile, AchievementMetric.WEEKLY_GOLD)
        }

        add(profile, AchievementMetric.TURRETS_BUILT, player.turretsBuilt.toLong())
        add(profile, AchievementMetric.BARRAGES_CAST, player.barragesUsed.toLong())
        add(profile, AchievementMetric.ELITES_TRAINED, player.elitesTrained.toLong())
        add(profile, AchievementMetric.EVOLUTIONS, player.erasAdvanced.toLong())
        for (doctrine in player.doctrines) {
            profile.achievementDoctrineMask =
                profile.achievementDoctrineMask or (1 shl doctrine.ordinal)
        }

        val unlocked = unlockReady(profile, online)
        profile.save()
        return unlocked
    }

    /** Uruchamiać tylko po udanym Profile.buy(def); Profile.buy zapisuje wcześniejszy stan. */
    fun onResearchBought(
        profile: Profile, def: ResearchDef,
        online: OnlineAchievements = OnlineAchievements.NoOp,
    ): List<AchievementDef> {
        if (profile.level(def.key) <= 0 || Profile.research.none { it.key == def.key }) return emptyList()
        val unlocked = unlockReady(profile, online)
        if (unlocked.isNotEmpty()) profile.save()
        return unlocked
    }

    /** Odtworzenie własnego najlepszego wyniku przez co najmniej 10 s realnego oglądania. */
    fun onReplayWatched(
        profile: Profile, replayId: String?,
        online: OnlineAchievements = OnlineAchievements.NoOp,
    ): List<AchievementDef> {
        if (replayId.isNullOrBlank() || profile.boards.values.none {
                it.firstOrNull()?.replay == replayId
            }) return emptyList()
        val key = AchievementMetric.REPLAY_RECORD.name
        if ((profile.achievementCounters[key] ?: 0L) == 0L) profile.achievementCounters[key] = 1L
        val unlocked = unlockReady(profile, online)
        if (unlocked.isNotEmpty()) profile.save()
        return unlocked
    }

    /** Przy ponownym połączeniu PGS ponawiamy wszystkie odblokowania (operacja idempotentna). */
    fun syncUnlocked(profile: Profile, online: OnlineAchievements) {
        for (a in all) if (a.id in profile.unlockedAchievements) {
            try { online.unlock(a.pgsKey) } catch (_: Exception) { }
        }
    }
}
