package com.centurywar.meta

import com.centurywar.engine.Icons
import com.centurywar.engine.L
import com.centurywar.game.Bonuses
import com.centurywar.game.Daily
import com.centurywar.game.Modifier

/**
 * R10-07: powitanie po przerwie. Gracz, który wraca po co najmniej [MIN_GAP_DAYS] dniach UTC
 * bez wizyty w menu (licząc od [Profile.lastPlayDay]), dostaje raz [BONUS] medali i kilka
 * banerów „co nowego”. Premia nie liczy się do żadnego rankingu (to tylko medale profilu).
 * Czysta logika bez Androida; banery buduje [com.centurywar.engine.Game.checkWelcomeBack].
 */
object WelcomeBack {
    /** Przerwa liczona w dniach UTC (jak seria logowań i wyzwanie dnia). */
    const val MIN_GAP_DAYS = 3L
    /** Decyzja właściciela 4.11: jednorazowo 30 medali. */
    const val BONUS = 30

    /** Tekst banera z ikoną (stała z [Icons]). */
    class Notice(val text: String, val icon: Int)

    /**
     * Banery po przyznaniu premii, w kolejności: powitanie z premią, „Co nowego” (gdy są
     * nieprzeczytane notatki), dzisiejsze wyzwanie dnia (gdy próba jest wolna), następny cel.
     * Każdy tekst mieści się w 2 wierszach banera także na zewnętrznym ekranie Folda.
     */
    fun notices(p: Profile, nowMillis: Long, whatsNewUnread: Boolean): List<Notice> {
        val out = ArrayList<Notice>(4)
        out += Notice(L.f("wb193_hello", L.plural("medals_n", BONUS.toLong())), Icons.MEDAL)
        if (whatsNewUnread) out += Notice(L.f("wb193_news", L["menu175_whats_new"]), Icons.BOOK)
        val today = Daily.dayNumber(nowMillis)
        if (p.lastRankedDaily != today) {
            val rules = dailyRules(today)
            if (rules.isNotEmpty()) out += Notice(L.f("wb193_daily", rules), Icons.CALENDAR)
        }
        val goal = NextGoals.pick(p, nowMillis, null)
        if (goal.kind != NextGoalKind.NONE) out += Notice(goal.text, Icons.FLAG)
        return out
    }

    /** Wydarzenie dnia, a bez niego modyfikatory (stała kolejność jak w przypomnieniu). */
    fun dailyRules(day: Long, lang: Int = L.lang): String {
        val cfg = Daily.config(day, Bonuses())
        cfg.dailyEvent?.let { return L.text(it.key, lang) }
        return Modifier.values().filter { it in cfg.modifiers }.joinToString(", ") { L.text(it.key, lang) }
    }
}
