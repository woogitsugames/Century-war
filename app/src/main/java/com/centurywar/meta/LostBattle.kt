package com.centurywar.meta

import com.centurywar.engine.L
import com.centurywar.game.Battle
import com.centurywar.game.BattleConfig
import com.centurywar.game.BattleMeta
import com.centurywar.game.Campaign
import com.centurywar.game.Mode
import kotlin.math.min

/**
 * R10-01: bitwa przerwana aktualizacją gry. Zrzutu z poprzedniej wersji (inny format albo
 * balans) nie da się wznowić, więc gracz dostaje medale jak za kapitulację, a dzisiejsze
 * rankingowe wyzwanie dnia odzyskuje próbę. Czysta logika bez Androida.
 */
object LostBattle {

    /**
     * Nagroda, jaką bitwa dałaby teraz za porażkę (gałąź porażki [com.centurywar.screens.BattleScreen]).
     * Oblężenie tygodnia bez premii progu (próg rozlicza się tylko w rozegranej bitwie);
     * rankingowe wyzwanie dnia = 0, bo zamiast medali wraca próba dnia.
     */
    fun lossReward(cfg: BattleConfig, battle: Battle, rankedDaily: Boolean): Int {
        // Nagroda bazowa rośnie z czasem bitwy (pełna po 4 min symulacji).
        val effort = (battle.time / 240f).coerceIn(0f, 1f)
        val kills = battle.player.kills
        return when (cfg.mode) {
            Mode.CAMPAIGN -> {
                val stage = cfg.stageIndex
                val base = ((5 + stage * 2) * effort).toInt() + min(20, kills / 8)
                (base * (Campaign.difficulty(cfg)?.medalMultiplier ?: 1f)).toInt()
            }
            Mode.SKIRMISH -> SkirmishSetup.reward(cfg, false, battle.time, kills)
            Mode.ENDLESS -> if (cfg.weekly >= 0L) (battle.wave * 3 + kills / 10) / 2
                else battle.wave * 3 + kills / 10
            Mode.DAILY -> if (rankedDaily) 0 else (5 * effort).toInt()
        }
    }

    /** Opis bitwy zapisywany obok zrzutu (wątek gry, tylko przy zapisie zrzutu). */
    fun meta(cfg: BattleConfig, battle: Battle, rankedDaily: Boolean, battleId: Long, nowMillis: Long): BattleMeta =
        BattleMeta(
            battleId = battleId, mode = cfg.mode, stageIndex = cfg.stageIndex, weekly = cfg.weekly,
            dailyDay = if (cfg.mode == Mode.DAILY) cfg.seed else -1L,
            rankedDaily = rankedDaily && cfg.mode == Mode.DAILY,
            wave = battle.wave.coerceAtLeast(0), timeS = battle.time.toInt().coerceAtLeast(0),
            kills = battle.player.kills.coerceAtLeast(0),
            consolation = lossReward(cfg, battle, rankedDaily).coerceAtLeast(0),
            savedAtMillis = nowMillis,
        )

    /** Wynik rozliczenia: medale i czy wróciła dzisiejsza próba wyzwania dnia. */
    class Settlement(val medals: Int, val dailyRestored: Boolean)

    /**
     * Przyznaje rekompensatę dokładnie raz (przez [Profile.settleBattle] i jej identyfikator).
     * Null, gdy ta bitwa była już rozliczona: wtedy ani medali, ani komunikatu.
     */
    fun settle(profile: Profile, meta: BattleMeta, today: Long, onPersisted: () -> Unit = {}): Settlement? {
        var medals = 0
        var daily = false
        val settled = profile.settleBattle(meta.battleId, onPersisted) {
            medals = meta.consolation.coerceAtLeast(0)
            profile.medals = (profile.medals.toLong() + medals).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            if (meta.rankedDaily && meta.dailyDay == today && profile.lastRankedDaily == meta.dailyDay) {
                profile.lastRankedDaily = -1L
                daily = true
            }
        }
        return if (settled) Settlement(medals, daily) else null
    }

    /** Tryb i postęp, np. „Kampania, etap 7” albo „Oblężenie bez końca, fala 23”. */
    fun progressLabel(meta: BattleMeta): String = when (meta.mode) {
        Mode.CAMPAIGN -> L.f("lost188_campaign", meta.stageIndex + 1)
        Mode.ENDLESS -> L.f(if (meta.weekly >= 0L) "lost188_weekly" else "lost188_endless", meta.wave)
        Mode.DAILY -> L["daily"]
        Mode.SKIRMISH -> L["skirmish"]
    }

    /**
     * Teksty banerów (najwyżej 2 wiersze każdy): najpierw co się stało, potem rekompensata.
     * Brak drugiego banera, gdy nie ma ani medali, ani zwrotu próby.
     */
    fun notices(meta: BattleMeta, result: Settlement): List<String> {
        val first = L["lost188_title"] + ". " + L.f("lost188_body", progressLabel(meta))
        val second = when {
            result.dailyRestored -> L["lost188_daily"]
            result.medals > 0 -> L.f("lost188_medals", L.plural("medals_n", result.medals.toLong()))
            else -> null
        }
        return if (second == null) listOf(first) else listOf(first, second)
    }
}
