package com.centurywar.meta

import com.centurywar.game.Campaign
import com.centurywar.game.GameIoCounter
import com.centurywar.game.SaveGuard
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * R2-04: lokalny dziennik testu (`files/playtest.json`). Liczniki, których nie ma w profilu:
 * dni i sesje gry, czas do końca samouczka i pierwszej wygranej, serie porażek, kapitulacje,
 * odwiedzone ekrany i wydajność ostatnich 5 sesji. Plik nie należy do profilu, kopii Androida
 * ani Saved Games i nigdy nie jest wysyłany sam: trafia tylko do „Raportu dla testera”, który
 * gracz udostępnia ręcznie.
 *
 * D45 (R13 TOP 5 poz. 2): lejek kampanii, czyli dla każdego etapu próby, wygrane, kapitulacje,
 * łączny czas bitew i liczba bitew z przywołanym dowódcą. Pole `stages` jest opcjonalne: starszy
 * dziennik wczytuje się bez zmiany [VERSION], a uszkodzony lejek zeruje tylko lejek.
 *
 * Mutacje wykonuje wątek gry (albo UI w onPause, gdy pętla stoi); bez alokacji poza zapisem.
 * Zapis pliku robi osobny wątek I/O. Uszkodzony plik oznacza start od zera, bez ostrzeżenia.
 */
class PlaytestLog(private val file: File) {
    companion object {
        const val FILE_NAME = "playtest.json"
        private const val VERSION = 1
        /** Powrót po dłuższej przerwie w tle zaczyna nową sesję. */
        const val SESSION_GAP_MS = 30L * 60L * 1000L
        const val PERF_SESSIONS = 5
        /** Wartość jeszcze nieznana (np. brak pierwszej wygranej). */
        const val NOT_YET = -1L
        /** Zdarzyło się przed założeniem dziennika (starszy profil). */
        const val BEFORE_LOG = -2L

        const val SCREEN_CODEX = 0
        const val SCREEN_RESEARCH = 1
        const val SCREEN_MISSIONS = 2
        const val SCREEN_REPLAYS = 3
        const val SCREEN_ACCESSIBILITY = 4
        const val SCREEN_SKIRMISH = 5
        const val SCREEN_ENDLESS = 6
        const val SCREEN_DAILY = 7
        const val SCREEN_WEEKLY = 8
        /** Nazwy do raportu (po angielsku, czyta je zespół). */
        val SCREEN_NAMES = arrayOf("Codex", "Research", "Missions", "Replays", "Accessibility",
            "Skirmish", "Endless", "Daily", "Weekly")
        private val SCREEN_MASK = (1 shl SCREEN_NAMES.size) - 1

        /** Ankieta testera (R11, pozycja 7): jeszcze nie pytano. */
        const val SURVEY_NONE = 0
        /** Gracz dał ocenę (pytanie o kłopot mógł pominąć). */
        const val SURVEY_ANSWERED = 1
        /** Gracz pominął ankietę: nie pytamy ponownie. */
        const val SURVEY_SKIPPED = 2
        /** Odpowiedzi „co najbardziej przeszkadza” do raportu (po angielsku, czyta je zespół). */
        val SURVEY_ISSUES = arrayOf("too hard", "battles too long", "unclear rules", "nothing, it's good")
        private const val MAX_COUNT = 10_000_000
        /** Liczba etapów kampanii w lejku (stała tablica, bez alokacji przy rozliczeniu). */
        val STAGE_COUNT = Campaign.stages.size
    }

    var firstDay = -1L
        private set
    var lastDay = -1L
        private set
    var daysPlayed = 0
        private set
    var sessions = 0
        private set
    /** Czas na pierwszym planie (od onResume do onPause), w sekundach. */
    var foregroundSeconds = 0L
        private set
    /** Czas bitew (`Profile.playSeconds`) w chwili ukończenia samouczka. */
    var tutorialSeconds = NOT_YET
        private set
    /** Czas bitew w chwili pierwszej wygranej. */
    var firstWinSeconds = NOT_YET
        private set
    var lossStreak = 0
        private set
    var maxLossStreak = 0
        private set
    var surrenders = 0
        private set
    var screensVisited = 0
        private set
    /** Ankieta testera: [SURVEY_NONE], [SURVEY_ANSWERED] albo [SURVEY_SKIPPED]. */
    var surveyState = SURVEY_NONE
        private set
    /** Ocena 1–5; 0 = brak. */
    var surveyRating = 0
        private set
    /** Indeks w [SURVEY_ISSUES]; −1 = brak odpowiedzi. */
    var surveyIssue = -1
        private set
    /** Dzień UTC odpowiedzi albo pominięcia; −1 = brak. */
    var surveyDay = -1L
        private set

    // Lejek kampanii: indeks = etap (0 = etap 1). Czas w sekundach bitwy.
    private val stageTries = IntArray(STAGE_COUNT)
    private val stageWins = IntArray(STAGE_COUNT)
    private val stageSurrenders = IntArray(STAGE_COUNT)
    private val stageSeconds = IntArray(STAGE_COUNT)
    private val stageCommander = IntArray(STAGE_COUNT)

    // Wydajność 5 ostatnich sesji; [perfHead] to bieżąca sesja.
    private val perfReduction = IntArray(PERF_SESSIONS)
    private val perfP95Tenths = IntArray(PERF_SESSIONS)
    private val perfDropped = LongArray(PERF_SESSIONS)
    private val perfThermal = IntArray(PERF_SESSIONS)
    private var perfHead = 0
    private var perfCount = 0
    private var droppedBaseline = 0L

    /** Dziennik nie istniał albo był uszkodzony (stan od zera). */
    var fresh = true
        private set
    private var resumedAtMs = 0L
    private var pausedAtMs = 0L

    private val writer = Executors.newSingleThreadExecutor { task ->
        Thread(task, "century-playtest").apply { isDaemon = true; priority = Thread.MIN_PRIORITY }
    }

    /** Odczyt tylko na wątku I/O (start aplikacji) albo w testach. */
    fun load() {
        GameIoCounter.diskAccess()
        val text = try {
            if (file.isFile && file.length() in 1L..200_000L) file.readText(Charsets.UTF_8) else null
        } catch (_: Exception) { null }
        synchronized(this) {
            resetCounters()
            if (text == null) return
            try {
                parse(JSONObject(text))
                fresh = false
            } catch (_: Exception) {
                // Uszkodzony dziennik testu nie jest postępem gracza: zaczynamy od zera, bez ostrzeżenia.
                resetCounters()
            }
        }
    }

    private fun resetCounters() {
        firstDay = -1L; lastDay = -1L; daysPlayed = 0; sessions = 0; foregroundSeconds = 0L
        tutorialSeconds = NOT_YET; firstWinSeconds = NOT_YET
        lossStreak = 0; maxLossStreak = 0; surrenders = 0; screensVisited = 0
        surveyState = SURVEY_NONE; surveyRating = 0; surveyIssue = -1; surveyDay = -1L
        perfReduction.fill(0); perfP95Tenths.fill(0); perfDropped.fill(0L); perfThermal.fill(0)
        perfHead = 0; perfCount = 0
        resetStages()
        fresh = true
    }

    private fun resetStages() {
        stageTries.fill(0); stageWins.fill(0); stageSurrenders.fill(0); stageSeconds.fill(0); stageCommander.fill(0)
    }

    private fun parse(o: JSONObject) {
        if (o.getInt("v") != VERSION) throw IllegalStateException("version")
        firstDay = o.getLong("firstDay")
        lastDay = o.getLong("lastDay")
        if (firstDay < -1L || lastDay < -1L || (firstDay >= 0L && lastDay < firstDay)) throw IllegalStateException("days")
        daysPlayed = count(o, "daysPlayed")
        sessions = count(o, "sessions")
        foregroundSeconds = o.getLong("foreground").coerceIn(0L, 3_155_760_000L)
        tutorialSeconds = o.getLong("tutorialSeconds").coerceAtLeast(BEFORE_LOG)
        firstWinSeconds = o.getLong("firstWinSeconds").coerceAtLeast(BEFORE_LOG)
        lossStreak = count(o, "lossStreak")
        maxLossStreak = maxOf(count(o, "maxLossStreak"), lossStreak)
        surrenders = count(o, "surrenders")
        screensVisited = o.getInt("screens") and SCREEN_MASK
        // Pola ankiety są opcjonalne: dziennik sprzed ankiety czyta się bez zmiany wersji.
        surveyState = o.optInt("survey", SURVEY_NONE).let { if (it in SURVEY_NONE..SURVEY_SKIPPED) it else SURVEY_NONE }
        surveyRating = o.optInt("surveyRating", 0).let { if (it in 1..5) it else 0 }
        surveyIssue = o.optInt("surveyIssue", -1).let { if (it in SURVEY_ISSUES.indices) it else -1 }
        surveyDay = o.optLong("surveyDay", -1L).coerceAtLeast(-1L)
        if (surveyState == SURVEY_ANSWERED && surveyRating == 0) surveyState = SURVEY_SKIPPED
        val perf = o.getJSONArray("perf")
        // Zapis od najstarszej do najnowszej sesji; najnowsza staje się bieżącą głową.
        val n = minOf(perf.length(), PERF_SESSIONS)
        val start = perf.length() - n
        for (i in 0 until n) {
            val p = perf.getJSONObject(start + i)
            perfReduction[i] = p.getInt("r").coerceIn(0, 100)
            perfP95Tenths[i] = p.getInt("p95").coerceIn(0, 1_000_000)
            perfDropped[i] = p.getLong("dropped").coerceAtLeast(0L)
            perfThermal[i] = p.optInt("thermal", 0).coerceIn(0, 10)
        }
        perfCount = n
        perfHead = if (n == 0) 0 else n - 1
        parseStages(o)
    }

    /**
     * Lejek jest opcjonalny (dziennik sprzed d45 go nie ma). Uszkodzony lejek nie unieważnia
     * reszty dziennika: zerujemy tylko lejek. Wpis: `[etap, próby, wygrane, kapitulacje, sekundy, dowódca]`.
     */
    private fun parseStages(o: JSONObject) {
        val arr = o.optJSONArray("stages") ?: return
        try {
            for (k in 0 until arr.length()) {
                val e = arr.getJSONArray(k)
                val i = e.getInt(0)
                if (i !in 0 until STAGE_COUNT) continue
                val tries = clampCount(e.getLong(1))
                stageTries[i] = tries
                stageWins[i] = clampCount(e.getLong(2)).coerceAtMost(tries)
                stageSurrenders[i] = clampCount(e.getLong(3)).coerceAtMost(tries - stageWins[i])
                stageSeconds[i] = clampCount(e.getLong(4))
                stageCommander[i] = clampCount(e.getLong(5)).coerceAtMost(tries)
            }
        } catch (_: Exception) {
            resetStages()
        }
    }

    private fun clampCount(v: Long): Int = v.coerceIn(0L, MAX_COUNT.toLong()).toInt()

    private fun count(o: JSONObject, key: String): Int =
        o.getLong(key).coerceIn(0L, MAX_COUNT.toLong()).toInt()

    /** Stary profil z ukończonym samouczkiem lub wygraną: czasów sprzed dziennika nie znamy. */
    @Synchronized
    fun adoptProfile(tutorialDone: Boolean, wins: Int) {
        if (!fresh) return
        if (tutorialDone && tutorialSeconds == NOT_YET) tutorialSeconds = BEFORE_LOG
        if (wins > 0 && firstWinSeconds == NOT_YET) firstWinSeconds = BEFORE_LOG
    }

    private fun markDay(nowMs: Long) {
        val day = Missions.utcDay(nowMs.coerceAtLeast(0L))
        if (firstDay < 0L) firstDay = day
        // Zegar cofnięty przez gracza nie liczy dnia drugi raz.
        if (lastDay < 0L || day > lastDay) {
            lastDay = day
            daysPlayed = (daysPlayed + 1).coerceAtMost(MAX_COUNT)
        }
    }

    /** Uruchomienie gry: zawsze nowa sesja. */
    @Synchronized
    fun startSession(nowMs: Long, droppedTotal: Long) {
        sessions = (sessions + 1).coerceAtMost(MAX_COUNT)
        markDay(nowMs)
        if (perfCount > 0) perfHead = (perfHead + 1) % PERF_SESSIONS
        perfCount = (perfCount + 1).coerceAtMost(PERF_SESSIONS)
        perfReduction[perfHead] = 0
        perfP95Tenths[perfHead] = 0
        perfDropped[perfHead] = 0L
        perfThermal[perfHead] = 0
        droppedBaseline = droppedTotal
        resumedAtMs = nowMs
        pausedAtMs = 0L
        fresh = false
    }

    /** Powrót z tła: po ponad 30 minutach nowa sesja, wcześniej ta sama. */
    @Synchronized
    fun resumed(nowMs: Long, droppedTotal: Long) {
        if (pausedAtMs > 0L && nowMs - pausedAtMs > SESSION_GAP_MS) {
            startSession(nowMs, droppedTotal)
            return
        }
        if (pausedAtMs > 0L || resumedAtMs == 0L) {
            markDay(nowMs)
            resumedAtMs = nowMs
        }
        pausedAtMs = 0L
    }

    /** Przejście w tło: dolicza czas na pierwszym planie. */
    @Synchronized
    fun paused(nowMs: Long) {
        if (resumedAtMs > 0L && nowMs > resumedAtMs) {
            foregroundSeconds = (foregroundSeconds + (nowMs - resumedAtMs) / 1000L).coerceAtMost(3_155_760_000L)
        }
        resumedAtMs = 0L
        pausedAtMs = nowMs.coerceAtLeast(1L)
    }

    @Synchronized
    fun screenVisited(bit: Int) {
        if (bit in SCREEN_NAMES.indices) screensVisited = screensVisited or (1 shl bit)
    }

    /** Pierwsze zauważone ukończenie samouczka (wygrana etapu 1 albo pominięcie). */
    @Synchronized
    fun checkTutorial(tutorialDone: Boolean, playSeconds: Long) {
        if (tutorialDone && tutorialSeconds == NOT_YET) tutorialSeconds = playSeconds.coerceAtLeast(0L)
    }

    /**
     * Rozliczenie bitwy (raz, z `settleRewards`). [stage] (indeks etapu kampanii, −1 = brak),
     * [battleSeconds] i [commander] (dowódca gracza przywołany w tej bitwie) zasilają lejek
     * kampanii; bez alokacji.
     */
    @Synchronized
    fun battleSettled(campaign: Boolean, won: Boolean, surrendered: Boolean,
                      playSeconds: Long, tutorialDone: Boolean, nowMs: Long,
                      stage: Int = -1, battleSeconds: Int = 0, commander: Boolean = false) {
        if (surrendered) surrenders = (surrenders + 1).coerceAtMost(MAX_COUNT)
        if (campaign && stage in 0 until STAGE_COUNT) {
            stageTries[stage] = (stageTries[stage] + 1).coerceAtMost(MAX_COUNT)
            if (won) stageWins[stage] = (stageWins[stage] + 1).coerceAtMost(stageTries[stage])
            else if (surrendered) stageSurrenders[stage] = (stageSurrenders[stage] + 1).coerceAtMost(MAX_COUNT)
            stageSeconds[stage] = (stageSeconds[stage].toLong() + battleSeconds.coerceAtLeast(0))
                .coerceAtMost(MAX_COUNT.toLong()).toInt()
            if (commander) stageCommander[stage] = (stageCommander[stage] + 1).coerceAtMost(stageTries[stage])
        }
        if (campaign) {
            if (won) lossStreak = 0
            else {
                lossStreak = (lossStreak + 1).coerceAtMost(MAX_COUNT)
                if (lossStreak > maxLossStreak) maxLossStreak = lossStreak
            }
        }
        if (won && firstWinSeconds == NOT_YET) firstWinSeconds = playSeconds.coerceAtLeast(0L)
        checkTutorial(tutorialDone, playSeconds)
        markDay(nowMs)
    }

    /** Okno 30 s bitwy: najgorsze p95, największa redukcja jakości, zgubione kroki. Bez alokacji. */
    @Synchronized
    fun perfSample(p95Ms: Float, reduction: Int, droppedTotal: Long, thermal: Int) {
        if (perfCount == 0) { perfCount = 1; perfHead = 0; droppedBaseline = droppedTotal }
        val i = perfHead
        val p95 = if (p95Ms.isFinite()) (p95Ms * 10f).toInt().coerceIn(0, 1_000_000) else 0
        if (p95 > perfP95Tenths[i]) perfP95Tenths[i] = p95
        if (reduction > perfReduction[i]) perfReduction[i] = reduction.coerceAtMost(100)
        if (thermal > perfThermal[i]) perfThermal[i] = thermal.coerceAtMost(10)
        perfDropped[i] = (droppedTotal - droppedBaseline).coerceAtLeast(0L)
    }

    /** Ocena 1–5 z ankiety testera; po niej nie pytamy ponownie. */
    @Synchronized
    fun surveyRated(rating: Int, nowMs: Long) {
        if (rating !in 1..5) return
        surveyState = SURVEY_ANSWERED
        surveyRating = rating
        surveyDay = Missions.utcDay(nowMs.coerceAtLeast(0L))
    }

    /** Odpowiedź na pytanie „co najbardziej przeszkadza” (tylko po ocenie). */
    @Synchronized
    fun surveyIssueChosen(issue: Int) {
        if (surveyState != SURVEY_ANSWERED || issue !in SURVEY_ISSUES.indices) return
        surveyIssue = issue
    }

    /** Pominięcie ankiety przed oceną: nie pytamy ponownie. */
    @Synchronized
    fun surveySkipped(nowMs: Long) {
        if (surveyState != SURVEY_NONE) return
        surveyState = SURVEY_SKIPPED
        surveyDay = Missions.utcDay(nowMs.coerceAtLeast(0L))
    }

    /** Wydajność sesji od najnowszej; [index] 0 = bieżąca. */
    class PerfSession(val maxReduction: Int, val worstP95Ms: Float, val droppedSteps: Long, val maxThermal: Int)

    @Synchronized
    fun perfSessions(): List<PerfSession> {
        val out = ArrayList<PerfSession>(perfCount)
        for (k in 0 until perfCount) {
            val i = ((perfHead - k) % PERF_SESSIONS + PERF_SESSIONS) % PERF_SESSIONS
            out.add(PerfSession(perfReduction[i], perfP95Tenths[i] / 10f, perfDropped[i], perfThermal[i]))
        }
        return out
    }

    /** Lejek jednego etapu kampanii; [stage] 0 = etap 1. */
    class StageStats(val stage: Int, val tries: Int, val wins: Int, val surrenders: Int,
                     val seconds: Int, val commander: Int)

    /** Etapy z co najmniej jedną próbą, rosnąco (kopia do raportu; alokuje, nie dla pętli gry). */
    @Synchronized
    fun stageStats(): List<StageStats> {
        val out = ArrayList<StageStats>()
        for (i in 0 until STAGE_COUNT) {
            if (stageTries[i] == 0) continue
            out.add(StageStats(i, stageTries[i], stageWins[i], stageSurrenders[i], stageSeconds[i], stageCommander[i]))
        }
        return out
    }

    @Synchronized
    fun toJson(): String {
        val o = JSONObject()
        o.put("v", VERSION)
        o.put("firstDay", firstDay); o.put("lastDay", lastDay); o.put("daysPlayed", daysPlayed)
        o.put("sessions", sessions); o.put("foreground", foregroundSeconds)
        o.put("tutorialSeconds", tutorialSeconds); o.put("firstWinSeconds", firstWinSeconds)
        o.put("lossStreak", lossStreak); o.put("maxLossStreak", maxLossStreak)
        o.put("surrenders", surrenders); o.put("screens", screensVisited)
        if (surveyState != SURVEY_NONE) {
            o.put("survey", surveyState); o.put("surveyRating", surveyRating)
            o.put("surveyIssue", surveyIssue); o.put("surveyDay", surveyDay)
        }
        val perf = JSONArray()
        for (k in perfCount - 1 downTo 0) {
            val i = ((perfHead - k) % PERF_SESSIONS + PERF_SESSIONS) % PERF_SESSIONS
            perf.put(JSONObject().put("r", perfReduction[i]).put("p95", perfP95Tenths[i])
                .put("dropped", perfDropped[i]).put("thermal", perfThermal[i]))
        }
        o.put("perf", perf)
        val stages = JSONArray()
        for (i in 0 until STAGE_COUNT) {
            if (stageTries[i] == 0) continue
            stages.put(JSONArray().put(i).put(stageTries[i]).put(stageWins[i]).put(stageSurrenders[i])
                .put(stageSeconds[i]).put(stageCommander[i]))
        }
        if (stages.length() > 0) o.put("stages", stages)
        return o.toString()
    }

    /** Migawka JSON na wątku wywołującym, zapis pliku w tle. */
    fun saveAsync() {
        val bytes = toJson().toByteArray(Charsets.UTF_8)
        try {
            writer.execute {
                try {
                    GameIoCounter.diskAccess()
                    SaveGuard.atomicWrite(file, bytes)
                } catch (_: Exception) {
                    // Dziennik testu jest pomocniczy: brak zapisu nie ostrzega gracza.
                }
            }
        } catch (_: Exception) { }
    }

    /** Ograniczona bariera dla onPause (ten sam limit co profil). */
    fun awaitWrites(timeoutMs: Long): Boolean {
        if (timeoutMs <= 0L) return false
        return try {
            writer.submit { }.get(timeoutMs, TimeUnit.MILLISECONDS)
            true
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        } catch (_: Exception) { false }
    }

    fun close() { writer.shutdown() }
}
