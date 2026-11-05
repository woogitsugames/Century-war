package com.centurywar.crash

import com.centurywar.engine.Game
import com.centurywar.engine.GameModes
import com.centurywar.engine.L
import com.centurywar.game.Mode
import com.centurywar.meta.BattleHistory
import com.centurywar.meta.PlaytestLog
import java.time.LocalDate

/**
 * R2-04: „Raport dla testera” jako zwykły tekst. Etykiety po angielsku (czyta je zespół).
 * Bez nicku, bez UUID i sekretu rankingu, bez konta Play Games: tylko liczniki, tryby,
 * wydajność i ustawienia. Gra nic nie wysyła sama; tekst trafia do systemowego okna
 * udostępniania dopiero po działaniu gracza.
 */
object PlaytestReportText {
    const val MAX_CHARS = 6_000
    val SECTIONS = arrayOf("[Tester survey]", "[Progress]", "[Campaign stages]", "[Play time]", "[Modes]",
        "[Performance]", "[Settings]")
    /** D45: najwyżej tyle wierszy lejka (od najwyższego etapu), reszta jako „(+N lower stages)”. */
    const val MAX_STAGE_LINES = 15
    private val QUALITY = arrayOf("auto", "low", "medium", "high")
    private val PALETTES = arrayOf("standard", "deutan/protan", "tritan", "high contrast")
    private val RESULTS = arrayOf("win", "loss", "surrender")

    class RecentBattle(val mode: Int, val stage: Int, val result: Int, val seconds: Int, val era: Int, val aiPower: Int)

    /** Migawka zebrana na wątku gry; budowanie tekstu może iść na dowolnym wątku. */
    class Input(
        val unlockedStage: Int, val stageCount: Int, val totalStars: Int,
        val campaignDifficulty: String, val tutorialDone: Boolean,
        val tutorialSeconds: Long, val firstWinSeconds: Long,
        val bestWave: Int, val winStreak: Int, val bestWinStreak: Int,
        val lossStreak: Int, val maxLossStreak: Int, val loginStreak: Int,
        val battles: Int, val wins: Int, val surrenders: Int, val playSeconds: Long,
        val modeBattles: IntArray, val modeWins: IntArray, val modeSeconds: LongArray,
        val firstDay: Long, val lastDay: Long, val daysPlayed: Int, val sessions: Int,
        val foregroundSeconds: Long, val screensVisited: Int,
        val recent: List<RecentBattle>, val perf: List<PlaytestLog.PerfSession>,
        val qualitySetting: Int, val qualityLevel: Int, val reduction: Int, val gpuReduction: Int,
        val maxReductionSteps: Int, val fpsCap: Int, val palette: Int, val textScale: Int,
        val leftHanded: Boolean, val talkBack: Boolean, val reduceShake: Boolean,
        val reduceFlash: Boolean, val slowSpeed: Boolean, val language: String,
        /** R10-04: tryb gry Androida ([GameModes]). */
        val gameMode: Int = GameModes.UNSUPPORTED,
        /** R11 (pozycja 7): ankieta testera z [PlaytestLog]. */
        val surveyState: Int = PlaytestLog.SURVEY_NONE,
        val surveyRating: Int = 0,
        val surveyIssue: Int = -1,
        val surveyDay: Long = -1L,
        /** D45 (R13 TOP 5 poz. 2): lejek kampanii z [PlaytestLog], etapy z próbami. */
        val stages: List<PlaytestLog.StageStats> = emptyList(),
    )

    /** Wątek gry: odczyt profilu, dziennika testu i historii (kopia listy). */
    fun collect(g: Game): Input {
        val p = g.profile
        val log = g.playtest
        val recent = ArrayList<RecentBattle>(10)
        g.history?.snapshot()?.let { list ->
            for (r in list) {
                if (recent.size >= 10) break
                recent.add(RecentBattle(r.mode, r.stage, r.result, r.seconds, r.era, r.aiPower))
            }
        }
        return Input(
            unlockedStage = p.unlockedStage(), stageCount = p.stars.size, totalStars = p.totalStars(),
            campaignDifficulty = p.campaignDifficulty.name, tutorialDone = p.tutorialDone,
            tutorialSeconds = log?.tutorialSeconds ?: PlaytestLog.NOT_YET,
            firstWinSeconds = log?.firstWinSeconds ?: PlaytestLog.NOT_YET,
            bestWave = p.bestWave, winStreak = p.winStreak, bestWinStreak = p.bestWinStreak,
            lossStreak = log?.lossStreak ?: 0, maxLossStreak = log?.maxLossStreak ?: 0,
            loginStreak = p.missionState.loginStreak,
            battles = p.battles, wins = p.wins, surrenders = log?.surrenders ?: 0,
            playSeconds = p.playSeconds,
            modeBattles = p.modeBattles.copyOf(), modeWins = p.modeWins.copyOf(),
            modeSeconds = p.modeSeconds.copyOf(),
            firstDay = log?.firstDay ?: -1L, lastDay = log?.lastDay ?: -1L,
            daysPlayed = log?.daysPlayed ?: 0, sessions = log?.sessions ?: 0,
            foregroundSeconds = log?.foregroundSeconds ?: 0L, screensVisited = log?.screensVisited ?: 0,
            recent = recent, perf = log?.perfSessions() ?: emptyList(),
            qualitySetting = p.quality, qualityLevel = g.effectiveQualityLevel(),
            reduction = g.quality.reduction, gpuReduction = g.quality.gpuReduction,
            maxReductionSteps = g.quality.maxReductionSteps, fpsCap = p.fpsCap,
            palette = p.palette, textScale = p.textScale, leftHanded = p.leftHandedHud,
            talkBack = g.touchExplorationActive, reduceShake = p.reduceShake,
            reduceFlash = p.reduceFlash, slowSpeed = g.slowSpeedEnabled(),
            language = L.CODES.getOrElse(L.lang) { "?" },
            gameMode = g.gameMode,
            surveyState = log?.surveyState ?: PlaytestLog.SURVEY_NONE,
            surveyRating = log?.surveyRating ?: 0,
            surveyIssue = log?.surveyIssue ?: -1,
            surveyDay = log?.surveyDay ?: -1L,
            stages = log?.stageStats() ?: emptyList(),
        )
    }

    private fun day(d: Long): String = if (d < 0L) "-" else
        try { LocalDate.ofEpochDay(d).toString() } catch (_: Exception) { "-" }

    private fun duration(seconds: Long): String {
        val s = seconds.coerceAtLeast(0L)
        val h = s / 3600L
        val m = (s % 3600L) / 60L
        return if (h > 0L) "${h}h ${m}m" else "${m}m ${s % 60L}s"
    }

    private fun milestone(seconds: Long): String = when {
        seconds == PlaytestLog.BEFORE_LOG -> "before this log"
        seconds < 0L -> "not yet"
        else -> "after " + duration(seconds) + " of battle time"
    }

    private fun yes(b: Boolean) = if (b) "yes" else "no"

    private fun modeName(mode: Int): String = Mode.entries.getOrNull(mode)?.name ?: "UNKNOWN"

    /** `S19: 4 tries, 1 win, 0 surr, 52m, cmd 3`: etap od 1, czas bitew w pełnych minutach. */
    private fun appendStage(out: StringBuilder, s: PlaytestLog.StageStats) {
        out.append('S').append(s.stage + 1).append(": ")
            .append(s.tries).append(if (s.tries == 1) " try, " else " tries, ")
            .append(s.wins).append(if (s.wins == 1) " win, " else " wins, ")
            .append(s.surrenders).append(" surr, ")
            .append(s.seconds.coerceAtLeast(0) / 60).append("m, cmd ")
            .append(s.commander).append('\n')
    }

    fun build(version: String, model: String, android: String, input: Input): String {
        val out = StringBuilder(3072)
        out.append("Century War - tester report\n")
        out.append("Version: ").append(version).append('\n')
        out.append("Device: ").append(model).append('\n')
        out.append("Android: ").append(android).append('\n')
        out.append("Language: ").append(input.language).append("\n\n")

        // Ankieta na początku: krótka, a przy obcięciu raportu do MAX_CHARS nie może zniknąć.
        out.append(SECTIONS[0]).append('\n')
        when (input.surveyState) {
            PlaytestLog.SURVEY_ANSWERED -> {
                out.append("Rating: ").append(input.surveyRating).append("/5 (").append(day(input.surveyDay)).append(")\n")
                out.append("Biggest issue: ")
                    .append(PlaytestLog.SURVEY_ISSUES.getOrElse(input.surveyIssue) { "skipped" }).append('\n')
            }
            PlaytestLog.SURVEY_SKIPPED -> out.append("Skipped (").append(day(input.surveyDay)).append(")\n")
            else -> out.append("Not asked yet\n")
        }
        out.append('\n')

        out.append(SECTIONS[1]).append('\n')
        out.append("Campaign: stage ").append(input.unlockedStage + 1).append(" of ").append(input.stageCount)
            .append(", stars ").append(input.totalStars).append(" of ").append(input.stageCount * 3)
            .append(", difficulty ").append(input.campaignDifficulty).append('\n')
        out.append("Tutorial done: ").append(yes(input.tutorialDone))
            .append(" (").append(milestone(input.tutorialSeconds)).append(")\n")
        out.append("First win: ").append(milestone(input.firstWinSeconds)).append('\n')
        out.append("Best Endless wave: ").append(input.bestWave).append('\n')
        out.append("Win streak: ").append(input.winStreak).append(" (best ").append(input.bestWinStreak).append(")\n")
        out.append("Campaign loss streak: ").append(input.lossStreak)
            .append(" (max ").append(input.maxLossStreak).append(")\n")
        out.append("Login streak: ").append(input.loginStreak).append("\n\n")

        // D45: lejek kampanii, od najwyższego etapu, najwyżej MAX_STAGE_LINES wierszy.
        out.append(SECTIONS[2]).append('\n')
        val tried = input.stages.filter { it.tries > 0 }.sortedByDescending { it.stage }
        if (tried.isEmpty()) out.append("-\n")
        for (k in 0 until minOf(tried.size, MAX_STAGE_LINES)) appendStage(out, tried[k])
        if (tried.size > MAX_STAGE_LINES) out.append("(+").append(tried.size - MAX_STAGE_LINES).append(" lower stages)\n")
        out.append('\n')

        out.append(SECTIONS[3]).append('\n')
        out.append("First day (UTC): ").append(day(input.firstDay))
            .append(", last day: ").append(day(input.lastDay)).append('\n')
        out.append("Days played: ").append(input.daysPlayed).append(", sessions: ").append(input.sessions).append('\n')
        out.append("Time in app: ").append(duration(input.foregroundSeconds)).append('\n')
        out.append("Battle time: ").append(duration(input.playSeconds)).append('\n')
        out.append("Battles: ").append(input.battles).append(", wins: ").append(input.wins)
            .append(", surrenders: ").append(input.surrenders).append("\n\n")

        out.append(SECTIONS[4]).append('\n')
        for (i in Mode.entries.indices) {
            out.append(Mode.entries[i].name).append(": battles ").append(input.modeBattles.getOrElse(i) { 0 })
                .append(", wins ").append(input.modeWins.getOrElse(i) { 0 })
                .append(", time ").append(duration(input.modeSeconds.getOrElse(i) { 0L })).append('\n')
        }
        out.append("Screens visited: ")
        var any = false
        for (i in PlaytestLog.SCREEN_NAMES.indices) {
            if (input.screensVisited and (1 shl i) == 0) continue
            if (any) out.append(", ")
            out.append(PlaytestLog.SCREEN_NAMES[i]); any = true
        }
        if (!any) out.append("-")
        out.append('\n')
        out.append("Recent battles (newest first):\n")
        if (input.recent.isEmpty()) out.append("  -\n")
        for (r in input.recent) {
            out.append("  ").append(modeName(r.mode))
            if (r.stage >= 0) out.append(" stage ").append(r.stage + 1)
            out.append(", ").append(RESULTS.getOrElse(r.result) { "?" })
                .append(", ").append(duration(r.seconds.toLong()))
                .append(", era ").append(r.era + 1)
                .append(", AI ").append(r.aiPower).append("%\n")
        }
        out.append('\n')

        out.append(SECTIONS[5]).append('\n')
        out.append("Quality: ").append(QUALITY.getOrElse(input.qualitySetting) { "?" })
            .append(" (level ").append(input.qualityLevel)
            .append(", reduction now ").append(input.reduction).append('/').append(input.maxReductionSteps)
            .append(", GPU ").append(input.gpuReduction).append(")\n")
        out.append("Last sessions (newest first, 30 s battle windows):\n")
        if (input.perf.isEmpty()) out.append("  -\n")
        for ((i, s) in input.perf.withIndex()) {
            out.append("  #").append(i + 1).append(": worst p95 ")
                .append((s.worstP95Ms * 10f).toInt() / 10f).append(" ms, max reduction ")
                .append(s.maxReduction).append(", dropped steps ").append(s.droppedSteps)
                .append(", thermal ").append(s.maxThermal).append('\n')
        }
        out.append('\n')

        out.append(SECTIONS[6]).append('\n')
        out.append("FPS cap: ").append(if (input.fpsCap == 0) "display" else input.fpsCap.toString()).append('\n')
        out.append("Game mode: ").append(GameModes.reportName(input.gameMode)).append('\n')
        out.append("Palette: ").append(PALETTES.getOrElse(input.palette) { "?" }).append('\n')
        out.append("Text size: ").append(input.textScale).append("%\n")
        out.append("Left-handed HUD: ").append(yes(input.leftHanded)).append('\n')
        out.append("TalkBack: ").append(yes(input.talkBack)).append('\n')
        out.append("Reduce shake/flash: ").append(yes(input.reduceShake)).append('/')
            .append(yes(input.reduceFlash)).append('\n')
        out.append("Battle speed x1/2: ").append(yes(input.slowSpeed)).append('\n')
        return out.toString().take(MAX_CHARS)
    }
}
