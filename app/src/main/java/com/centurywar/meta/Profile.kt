package com.centurywar.meta

import com.centurywar.game.BattleConfig
import com.centurywar.game.Bonuses
import com.centurywar.game.Campaign
import com.centurywar.game.Defs
import com.centurywar.game.GameIoCounter
import com.centurywar.game.Mode
import com.centurywar.game.Research
import com.centurywar.game.Weekly
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.TimeUnit

typealias ResearchDef = com.centurywar.game.ResearchDef

class ScoreEntry(val name: String, val score: Long, val day: Long, val replay: String? = null)

/** Historia niezależna od tablic rankingowych; obejmuje również bitwy własne. */
data class SkirmishResult(
    val score: Long, val day: Long, val won: Boolean, val ranked: Boolean,
    val setup: SkirmishSetup,
)

/**
 * Trwały postęp gracza: medale, badania, gwiazdki kampanii, rekordy i ustawienia.
 * Zapis to ręcznie zbudowany JSON w pamięci aplikacji. Zapis jest atomowy
 * (plik tymczasowy + rename), więc przerwanie w trakcie zapisu nie psuje postępu.
 */
class Profile(private val file: File) {

    companion object {
        const val CURRENT_VERSION = 3
        private val REPLAY_ID = Regex("[a-z0-9_-]{1,64}")
        private val RELEASE_VERSION = Regex("[A-Za-z0-9._+-]{1,48}")
        const val HINT_COUNT = 20
        const val HINT_LONG_PRESS = 17
        // 20 bitów: 0–16 podpowiedzi, 17 przytrzymanie przycisku, 18 wariant epoki, 19 pogoda.
        const val HINT_VALID_MASK = 1048575L
        private val hintWriter = java.util.concurrent.Executors.newSingleThreadExecutor { task ->
            Thread(task, "cw-profile-io").apply { isDaemon = true; priority = Thread.MIN_PRIORITY }
        }
        /** Bariera testowa; produkcyjny wątek gry nie czeka na operacje zapisu. */
        internal fun awaitHintWrites() { hintWriter.submit {}.get() }
        /** Pierwszy etap Epoki żelaza ma indeks 8 (dawny etap 9). */
        const val IRON_FIRST_STAGE = 8
        const val INSERTED_STAGE_COUNT = 4

        /**
         * Migracje danych profilu (plik i zapis z chmury):
         * 1 -> 2 bez zmian pól; 2 -> 3 wstawienie Epoki żelaza: gwiazdki etapów 9..32
         * (indeksy 8..31) przesuwają się o 4, a nowe etapy 9..12 startują od zera.
         */
        internal fun migrate(json: JSONObject, fromVersion: Int): JSONObject {
            require(fromVersion in 1..CURRENT_VERSION) { "Nieobsługiwana wersja profilu: $fromVersion" }
            var version = fromVersion
            while (version < CURRENT_VERSION) {
                when (version) {
                    1 -> Unit
                    2 -> json.optJSONArray("stars")?.let { old ->
                        val moved = JSONArray()
                        for (i in 0 until old.length()) {
                            val target = if (i >= IRON_FIRST_STAGE) i + INSERTED_STAGE_COUNT else i
                            while (moved.length() < target) moved.put(0)
                            moved.put(target, old.opt(i))
                        }
                        json.put("stars", moved)
                    }
                    else -> error("Brak migracji z wersji $version")
                }
                version++
            }
            json.put("v", version)
            return json
        }

        val research = Research.defs
        const val BOARD_ENDLESS = "endless"
        const val BOARD_CAMPAIGN = "campaign"
        const val BOARD_SKIRMISH = "skirmish"
        fun dailyBoard(day: Long) = "daily_$day"
        fun weeklyBoard(week: Long) = "weekly_$week"

        /** Wynik postępu używany przy wyborze zapisu z różnych urządzeń. */
        fun progressValue(json: String): Long {
            val o = JSONObject(json)
            val stars = o.optJSONArray("stars")
            var total = 0L
            if (stars != null) for (i in 0 until stars.length()) {
                total += stars.optInt(i).coerceIn(0, 3) * 100L
            }
            val levels = o.optJSONObject("levels")
            for (def in research) {
                val count = (levels?.optInt(def.key) ?: 0).coerceIn(0, def.max)
                for (i in 0 until count) total += def.cost(i).toLong()
            }
            return total + o.optLong("medals", 0L).coerceAtLeast(0L) +
                o.optLong("battles", 0L).coerceAtLeast(0L)
        }

        /** Remis zachowuje lokalną wersję; nie mieszamy walut i badań z obu zapisów. */
        fun preferCloud(local: String, cloud: String): Boolean =
            progressValue(cloud) > progressValue(local)

        fun isValidJson(json: String): Boolean = try {
            val o = JSONObject(json)
            o.optInt("v", -1) in 1..CURRENT_VERSION &&
                o.optJSONArray("stars") != null &&
                o.optJSONObject("levels") != null &&
                o.optJSONObject("boards") != null
        } catch (_: Exception) {
            false
        }
    }

    var name = ""            // pusty = nowy profil; Game nadaje L["default_name"]
    var medals = 0
    val levels = HashMap<String, Int>()
    val stars = IntArray(Campaign.stages.size)
    var bestWave = 0
    var lastRankedDaily = -1L
    /** Maska brąz/srebro/złoto dla bieżącego tygodnia. */
    var weeklyWeek = -1L
        private set
    var weeklyClaimed = 0
        private set
    private val weeklyClaimHistory = HashMap<Long, Int>()
    var tutorialDone = false
    /** Ostatnia wersja aplikacji, której komunikat już zamknięto; stan lokalny. */
    var whatsNewSeenVersion = ""
        set(value) { field = if (value.isEmpty() || RELEASE_VERSION.matches(value)) value else "" }
    /** Prawda tylko po pomyślnym odczycie zapisanego profilu z dysku. */
    var hasExistingSave = false
        private set
    /** Jednorazowe podpowiedzi: bit oznacza już pokazany komunikat. */
    var hintsSeen = 0L
    var hintsEnabled = true

    /** Zapowiedź każdej epoki jest odtwarzana tylko raz, niezależnie od podpowiedzi. */
    var eraIntrosSeen = 0L
        private set
    var eraIntrosEnabled = true

    /** Identyfikator powstaje dopiero po świadomym włączeniu rankingu. */
    var playerId = ""
        private set
    @Volatile var playerSecret = ""
        private set
    var globalRanking = false
        private set

    fun enableGlobalRanking() {
        if (playerId.isEmpty()) playerId = UUID.randomUUID().toString()
        if (!validRankingSecret(playerSecret)) playerSecret = newRankingSecret()
        globalRanking = true
        save()
    }

    fun disableGlobalRanking() {
        globalRanking = false
        save()
    }

    fun renewRankingId() {
        playerId = UUID.randomUUID().toString()
        playerSecret = newRankingSecret()
        save()
    }

    private fun newRankingSecret(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it.toInt() and 255) }
    }

    private fun validRankingSecret(value: String): Boolean = value.matches(Regex("[0-9a-f]{64}"))

    private fun validRankingId(id: String): Boolean {
        val uuid = runCatching { UUID.fromString(id) }.getOrNull() ?: return false
        return uuid.version() == 4 && uuid.variant() == 2 && uuid.toString() == id
    }

    // ustawienia
    var quality = 0          // 0 auto, 1 low, 2 med, 3 high
    var fpsCap = 0           // 0 = częstotliwość ekranu, 30, 60
    /** Siła wibracji: 0 wyłączone, 1 słabe, 2 pełne. */
    var vibrationLevel = 2
        set(value) { field = value.coerceIn(0, 2) }
    /** Zgodność z kodem używającym dawnego ustawienia logicznego. */
    var vibration: Boolean
        get() = vibrationLevel != 0
        set(value) { vibrationLevel = if (value) 2 else 0 }
    var screenShake = 2     // 0 wyłączone, 1 słabe, 2 pełne
    /** 0 standard, 1 błękit/bursztyn, 2 turkus/koral, 3 wysoki kontrast. */
    var palette = 0
        set(value) { field = value.coerceIn(0, 3) }
    var teamMarkers = false
    /** Procent wielkości tekstu: 100 / 115 / 130. */
    var textScale = 100
        set(value) { field = if (value < 108) 100 else if (value < 123) 115 else 130 }
    var reduceShake = false
    var reduceFlash = false
    var sound = true
    var sfxVolume = 0.8f
        set(value) { field = if (value.isFinite()) value.coerceIn(0f, 1f) else 0.8f }
    var musicVolume = 0.5f
        set(value) { field = if (value.isFinite()) value.coerceIn(0f, 1f) else 0.5f }
    var showFps = false
    var lang = ""            // "" = systemowy, inaczej kod z L.CODES ("pl", "en", "de"...)
    var skirmishDiff = 1
    var skirmishSetup = SkirmishSetup.defaults(skirmishDiff)
    var notifications = true // przypomnienia po pierwszym zwycięstwie
    var notifyHour = 18     // godzina lokalna 0..23

    // statystyki
    var battles = 0
    var wins = 0
    var totalKills = 0L
    var playSeconds = 0L
    // Liczniki całego życia (w tym zapis w chmurze); historia 100 bitew jest tylko lokalna.
    val modeBattles = IntArray(Mode.entries.size)
    val modeWins = IntArray(Mode.entries.size)
    val modeSeconds = LongArray(Mode.entries.size)
    var winStreak = 0
    var bestWinStreak = 0

    fun recordBattle(mode: Mode, won: Boolean, seconds: Int) {
        val i = mode.ordinal
        modeBattles[i] = (modeBattles[i].toLong() + 1).coerceAtMost(10_000_000L).toInt()
        if (won) {
            modeWins[i] = (modeWins[i].toLong() + 1).coerceAtMost(modeBattles[i].toLong()).toInt()
            winStreak = (winStreak + 1L).coerceAtMost(10_000_000L).toInt()
            bestWinStreak = maxOf(bestWinStreak, winStreak)
        } else {
            winStreak = 0
        }
        modeSeconds[i] = (modeSeconds[i] + seconds.coerceAtLeast(0).toLong())
            .coerceAtMost(3_155_760_000L)
    }

    // Trwały postęp osiągnięć; brak tych pól w starym JSON oznacza zero.
    val achievementCounters = HashMap<String, Long>()
    val unlockedAchievements = HashSet<String>()
    val achievementDailySeeds = HashSet<Long>()
    var achievementDoctrineMask = 0

    val missionState = MissionState()
    val boards = HashMap<String, MutableList<ScoreEntry>>()
    val skirmishHistory = ArrayList<SkirmishResult>()
    // Pliku utworzonego przez nowszą wersję gry nie wolno nadpisać.
    private var newerProfileVersion = false

    /** Samouczek odpali się ponownie przy następnym starcie etapu 1 kampanii. */
    fun replayTutorial() { tutorialDone = false; save() }

    /** Znacznik zmieniamy na wątku gry, zapis wykonuje pojedynczy wątek I/O. */
    fun markHintSeen(id: Int): Boolean {
        if (!hintsEnabled || id !in 0 until HINT_COUNT) return false
        val bit = 1L shl id
        if (hintsSeen and bit != 0L) return false
        hintsSeen = (hintsSeen or bit) and HINT_VALID_MASK
        // Migawka JSON powstaje na wątku gry (bez wyścigu z mutacjami), w tle tylko zapis pliku.
        if (!newerProfileVersion) {
            val seq: Long
            val json: String
            synchronized(this) { seq = ++saveSeq; json = toJson() }
            hintWriter.execute { writeJson(json, seq) }
        }
        return true
    }

    fun replayHints() { hintsSeen = 0L; save() }

    /** Oznacza zobaczoną epokę przed wyświetleniem, aby mapa i bitwa się nie dublowały. */
    fun markEraIntroSeen(era: Int): Boolean {
        if (!eraIntrosEnabled || era !in 0 until Defs.ERA_COUNT) return false
        val bit = 1L shl era
        if (eraIntrosSeen and bit != 0L) return false
        eraIntrosSeen = (eraIntrosSeen or bit) and ((1L shl Defs.ERA_COUNT) - 1L)
        save()
        return true
    }

    /** Umożliwia ponowny podgląd przy następnym wejściu do epoki. */
    fun replayEraIntros() { eraIntrosSeen = 0L; save() }

    fun level(key: String) = levels[key] ?: 0

    fun canBuy(def: ResearchDef): Boolean = level(def.key) < def.max && medals >= def.cost(level(def.key))

    fun buy(def: ResearchDef): Boolean = buy(def, System.currentTimeMillis())

    fun buy(def: ResearchDef, nowMillis: Long): Boolean {
        if (!canBuy(def)) return false
        medals -= def.cost(level(def.key))
        levels[def.key] = level(def.key) + 1
        Missions(this).onResearchBought(nowMillis, persist = false)
        save()
        return true
    }

    fun researchLevels(): IntArray = IntArray(research.size) { i ->
        level(research[i].key).coerceIn(0, research[i].max)
    }

    fun bonuses() = Research.bonuses(researchLevels())

    /** Pierwszy etap bez gwiazdki. Wszystkie wcześniejsze są odblokowane. */
    fun unlockedStage(): Int {
        for (i in stars.indices) if (stars[i] == 0) return i
        return stars.size - 1
    }

    fun totalStars() = stars.sum()

    /**
     * Dodaje wynik do lokalnego rankingu; zwraca true, jeśli to rekord gracza na tej tablicy.
     * Lokalne tablice zawierają wyłącznie wyniki tego urządzenia, więc rekord liczymy po całej
     * liście (a nie po nazwie): zmiana nazwy dowódcy nie może robić "rekordu" ze słabszego wyniku.
     */
    @Synchronized
    fun submit(board: String, score: Long, day: Long, replay: String? = null): Boolean {
        val list = boards.getOrPut(board) { ArrayList() }
        val prevBest = list.maxOfOrNull { it.score } ?: -1L
        list.add(ScoreEntry(name, score, day, replay?.takeIf { REPLAY_ID.matches(it) }))
        list.sortByDescending { it.score }
        while (list.size > 20) list.removeAt(list.size - 1)
        return score > prevBest
    }

    fun top(board: String): List<ScoreEntry> = boards[board] ?: emptyList()

    /** Usuwa wyłącznie odnośniki do skasowanych plików; wyniki pozostają w rankingu. */
    @Synchronized
    fun clearReplayReferences(removedIds: Collection<String>): Boolean {
        if (removedIds.isEmpty()) return false
        var changed = false
        for (list in boards.values) for (i in list.indices) {
            val entry = list[i]
            if (entry.replay != null && entry.replay in removedIds) {
                list[i] = ScoreEntry(entry.name, entry.score, entry.day)
                changed = true
            }
        }
        return changed
    }

    /** Zmiana tygodnia i ograniczenie historii do ośmiu zakończonych lub bieżących tygodni. */
    @Synchronized
    fun syncWeekly(currentWeek: Long) {
        if (currentWeek < 0L) return
        if (weeklyWeek != currentWeek) {
            if (weeklyWeek >= 0L) weeklyClaimHistory[weeklyWeek] = weeklyClaimed
            weeklyWeek = currentWeek
            weeklyClaimed = weeklyClaimHistory[currentWeek] ?: 0
        }
        val oldest = currentWeek - 7L
        boards.keys.filter {
            it.startsWith("weekly_") &&
                (it.removePrefix("weekly_").toLongOrNull()?.let { w -> w < oldest || w > currentWeek } ?: true)
        }.forEach { boards.remove(it) }
        weeklyClaimHistory.keys.removeAll { it < oldest || it > currentWeek }
        weeklyClaimHistory[currentWeek] = weeklyClaimed
    }

    /** Nagrody przyznawane raz na próg; spóźniona bitwa trafia do tygodnia z konfiguracji. */
    @Synchronized
    fun claimWeekly(week: Long, wave: Int, currentWeek: Long): Int {
        syncWeekly(currentWeek)
        if (week < currentWeek - 7L || week > currentWeek || week < 0L) return 0
        val old = if (week == weeklyWeek) weeklyClaimed else weeklyClaimHistory[week] ?: 0
        var mask = old
        var reward = 0
        for (i in 0 until Weekly.TIER_COUNT) {
            val bit = 1 shl i
            if (wave >= Weekly.threshold(i) && mask and bit == 0) {
                mask = mask or bit
                reward += Weekly.medalReward(i)
            }
        }
        weeklyClaimHistory[week] = mask
        if (week == weeklyWeek) weeklyClaimed = mask
        return reward
    }

    fun weeklyClaimedFor(week: Long): Int =
        if (week == weeklyWeek) weeklyClaimed else weeklyClaimHistory[week] ?: 0

    /** Ostatnie 20 potyczek, także niestandardowe. Brak wpisu do rankingu dla bitwy własnej. */
    fun recordSkirmish(cfg: BattleConfig, score: Long, day: Long, won: Boolean) {
        skirmishHistory.add(0, SkirmishResult(score.coerceAtLeast(0L), day.coerceAtLeast(0L),
            won, SkirmishSetup.isRanked(cfg), SkirmishSetup.fromConfig(cfg)))
        if (skirmishHistory.size > 20) skirmishHistory.removeAt(skirmishHistory.lastIndex)
    }

    // ---------------------------------------------------------------- zapis

    /** Wywoływane wyłącznie po udanym zapisie lokalnym; callback nie blokuje gry. */
    @Volatile var onSaved: ((String) -> Unit)? = null

    /** Ten sam format JSON, którego używa plik profilu (wersja [CURRENT_VERSION]). */
    @Synchronized
    fun toJson(): String {
        val o = JSONObject()
        o.put("v", CURRENT_VERSION)
        o.put("name", name)
        o.put("medals", medals)
        o.put("levels", JSONObject(levels as Map<*, *>))
        o.put("stars", JSONArray().apply { stars.forEach { put(it) } })
        o.put("bestWave", bestWave)
        o.put("lastRankedDaily", lastRankedDaily)
        o.put("weeklyWeek", weeklyWeek)
        o.put("weeklyClaimed", weeklyClaimed)
        o.put("weeklyClaims", JSONObject(weeklyClaimHistory.mapKeys { it.key.toString() }))
        o.put("tutorialDone", tutorialDone)
        o.put("whatsNewSeenVersion", whatsNewSeenVersion)
        o.put("hintsSeen", hintsSeen and HINT_VALID_MASK)
        o.put("hintsEnabled", hintsEnabled)
        o.put("eraIntrosSeen", eraIntrosSeen and ((1L shl Defs.ERA_COUNT) - 1L))
        o.put("eraIntrosEnabled", eraIntrosEnabled)
        o.put("playerId", playerId)
        o.put("playerSecret", playerSecret)
        o.put("globalRanking", globalRanking)
        o.put("quality", quality)
        o.put("fpsCap", fpsCap)
        o.put("vibration", vibration) // zgodność ze starszą wersją v3
        o.put("vibrationLevel", vibrationLevel)
        o.put("screenShake", screenShake.coerceIn(0, 2))
        o.put("palette", palette)
        o.put("teamMarkers", teamMarkers)
        o.put("textScale", textScale)
        o.put("reduceShake", reduceShake)
        o.put("reduceFlash", reduceFlash)
        o.put("sound", sound)
        o.put("sfxVolume", sfxVolume.toDouble())
        o.put("musicVolume", musicVolume.toDouble())
        o.put("showFps", showFps)
        o.put("lang", lang)
        o.put("skirmishDiff", skirmishDiff)
        val setup = skirmishSetup.sanitized()
        o.put("skirmishSetup", JSONObject()
            .put("startEra", setup.startEra).put("maxEra", setup.maxEra)
            .put("personality", setup.personality).put("powerPercent", setup.powerPercent)
            .put("modifierMask", setup.modifierMask))
        o.put("skirmishHistory", JSONArray().apply {
            for (r in skirmishHistory.take(20)) put(JSONObject()
                .put("score", r.score).put("day", r.day).put("won", r.won).put("ranked", r.ranked)
                .put("startEra", r.setup.startEra).put("maxEra", r.setup.maxEra)
                .put("personality", r.setup.personality).put("powerPercent", r.setup.powerPercent)
                .put("modifierMask", r.setup.modifierMask))
        })
        o.put("notifications", notifications)
        o.put("notifyHour", notifyHour.coerceIn(0, 23))
        o.put("battles", battles)
        o.put("wins", wins)
        o.put("totalKills", totalKills)
        o.put("playSeconds", playSeconds)
        o.put("modeBattles", JSONArray().apply { modeBattles.forEach { put(it) } })
        o.put("modeWins", JSONArray().apply { modeWins.forEach { put(it) } })
        o.put("modeSeconds", JSONArray().apply { modeSeconds.forEach { put(it) } })
        o.put("winStreak", winStreak)
        o.put("bestWinStreak", bestWinStreak)
        o.put("achievementCounters", JSONObject(achievementCounters as Map<*, *>))
        o.put("unlockedAchievements", JSONArray().apply { unlockedAchievements.sorted().forEach { put(it) } })
        o.put("achievementDailySeeds", JSONArray().apply { achievementDailySeeds.sorted().forEach { put(it) } })
        o.put("achievementDoctrineMask", achievementDoctrineMask)
        o.put("missions", missionState.toJson())
        val b = JSONObject()
        for ((k, list) in boards) {
            b.put(k, JSONArray().apply {
                list.sortedByDescending { it.score }.take(20).forEach {
                    put(JSONObject().put("n", it.name).put("s", it.score).put("d", it.day).apply {
                        it.replay?.let { id -> put("r", id) }
                    })
                }
            })
        }
        o.put("boards", b)
        return o.toString()
    }

    /**
     * Zapis z chmury (Play Games). Ta sama walidacja i migracja co przy odczycie pliku;
     * dane są parsowane przed zmianą stanu, więc uszkodzony snapshot nie narusza profilu.
     */
    @Synchronized
    fun fromJson(json: String) {
        // Zgoda na serwer rankingu i UUID są wyłącznie lokalne: chmura nie może
        // aktywować wysyłki na innym urządzeniu.
        val localId = playerId
        val localSecret = playerSecret
        val localConsent = globalRanking
        val localWhatsNewVersion = whatsNewSeenVersion
        val o = JSONObject(json)
        val version = o.optInt("v", 1)
        require(version in 1..CURRENT_VERSION) { "Nieobsługiwana wersja profilu: $version" }
        restore(migrate(o, version), version >= CURRENT_VERSION)
        playerId = localId
        playerSecret = localSecret
        globalRanking = localConsent && localId.isNotEmpty()
        whatsNewSeenVersion = localWhatsNewVersion
    }

    /** Snapshot Play Games nie zawiera lokalnej zgody ani identyfikatora rankingu. */
    @Synchronized
    fun toCloudJson(): String = cloudJson(toJson())

    private fun cloudJson(json: String): String = JSONObject(json).apply {
        remove("playerId")
        remove("playerSecret")
        remove("globalRanking")
        remove("whatsNewSeenVersion")
    }.toString()

    /** Migawka jest spójna; wątek wywołujący nie dotyka dysku. */
    @Synchronized
    fun save() {
        if (newerProfileVersion) return
        val json = try { toJson() } catch (_: Exception) { return }
        val seq = ++saveSeq
        hintWriter.execute { writeJson(json, seq) }
    }

    /** Migawka powstaje na wątku gry; zapis pliku trafia do istniejącego wątku I/O. */
    fun saveAsync() {
        val json: String
        val seq: Long
        synchronized(this) {
            if (newerProfileVersion) return
            json = try { toJson() } catch (_: Exception) { return }
            seq = ++saveSeq
        }
        hintWriter.execute { writeJson(json, seq) }
    }

    /** Bariera zapisu przy pauzie; nie trzymamy blokady profilu podczas fsync. */
    fun awaitWrites(timeoutMs: Long): Boolean {
        if (timeoutMs <= 0L) return false
        return try {
            hintWriter.submit { }.get(timeoutMs, TimeUnit.MILLISECONDS)
            true
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        } catch (_: Exception) {
            false
        }
    }

    /** Numery kolejnych migawek kontroluje zamek profilu, plik zapisuje jeden wykonawca. */
    @Volatile private var saveSeq = 0L
    private var writtenSeq = 0L

    private fun writeJson(json: String, seq: Long) {
        // Pomijamy przestarzałe migawki z kolejki, by pauza nie czekała na wiele fsync.
        if (newerProfileVersion || seq < saveSeq || seq <= writtenSeq) return
        try {
            GameIoCounter.diskAccess()
            val tmp = File(file.parentFile, file.name + ".tmp")
            FileOutputStream(tmp).use { out ->
                out.write(json.toByteArray(Charsets.UTF_8))
                out.flush()
                // fsync przed rename: po utracie zasilania nie zostanie pusty plik profilu.
                try { out.fd.sync() } catch (_: Exception) { }
            }
            // Nigdy nie kasujemy poprawnego profilu, gdy podmiana się nie uda.
            var saved = tmp.renameTo(file)
            if (!saved) {
                try {
                    java.nio.file.Files.move(
                        tmp.toPath(), file.toPath(),
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    )
                    saved = true
                } catch (_: Exception) {
                    // Poprzedni zapis zostaje na miejscu, nowy można odzyskać z .tmp.
                }
            }
            if (saved) {
                writtenSeq = seq
                onSaved?.invoke(cloudJson(json))
            }
        } catch (_: Exception) {
            // Brak miejsca itp. — gra działa dalej, zapis spróbuje ponownie przy kolejnej okazji.
        }
    }

    /** Przyjmuje tylko poprawny JSON. Dane są przypisywane po pełnym odczycie tablic. */
    private fun restore(json: JSONObject, strict: Boolean) {
        if (strict) {
            // Nie uznajemy uciętego, syntaktycznie poprawnego JSON za pełny zapis v2.
            val mandatory = arrayOf(
                "name", "medals", "levels", "stars", "bestWave", "lastRankedDaily",
                "tutorialDone", "quality", "fpsCap", "vibration", "sound",
                "showFps", "lang", "skirmishDiff", "battles", "wins",
                "totalKills", "playSeconds", "boards",
            )
            for (key in mandatory) {
                if (!json.has(key) || json.isNull(key)) error("Niepełny profil: $key")
            }
            // Nie zamieniamy uszkodzonych wartości na domyślne po cichu.
            for (key in arrayOf("name", "lang")) {
                if (json.opt(key) !is String) error("Niepoprawny tekst: $key")
            }
            for (key in arrayOf("medals", "bestWave", "lastRankedDaily", "quality",
                "fpsCap", "skirmishDiff", "battles", "wins", "totalKills", "playSeconds")) {
                if (json.opt(key) !is Number) error("Niepoprawna liczba: $key")
            }
            for (key in arrayOf("tutorialDone", "sound", "showFps")) {
                if (json.opt(key) !is Boolean) error("Niepoprawna flaga: $key")
            }
            // Stare profile v3 miały Boolean, nowe mają liczbę 0..2.
            if (json.opt("vibration") !is Boolean && json.opt("vibration") !is Number)
                error("Niepoprawna wartość wibracji")
            if (json.has("vibrationLevel") && json.opt("vibrationLevel") !is Number)
                error("Niepoprawny poziom wibracji")
        }
        fun obj(key: String): JSONObject? {
            if (!json.has(key) || json.isNull(key)) return null
            return json.optJSONObject(key) ?: error("Niepoprawny obiekt: $key")
        }

        fun arr(key: String): JSONArray? {
            if (!json.has(key) || json.isNull(key)) return null
            return json.optJSONArray(key) ?: error("Niepoprawna tablica: $key")
        }

        val newLevels = HashMap<String, Int>()
        obj("levels")?.let { l ->
            l.keys().forEach { key ->
                if (strict && l.opt(key) !is Number) error("Niepoprawny poziom: $key")
                newLevels[key] = l.optInt(key)
            }
        }
        for (def in research) {
            newLevels[def.key]?.let { newLevels[def.key] = it.coerceIn(0, def.max) }
        }
        val newStars = IntArray(stars.size)
        arr("stars")?.let { a ->
            for (i in 0 until a.length()) {
                if (strict && a.opt(i) !is Number) error("Niepoprawna gwiazdka: $i")
                if (i < newStars.size) newStars[i] = a.optInt(i).coerceIn(0, 3)
            }
        }
        val newBoards = HashMap<String, MutableList<ScoreEntry>>()
        obj("boards")?.let { b ->
            b.keys().forEach { key ->
                val entries = b.optJSONArray(key) ?: error("Niepoprawny ranking: $key")
                val best = ArrayList<ScoreEntry>(20)
                for (i in 0 until entries.length()) {
                    val e = entries.getJSONObject(i)
                    if (strict && (e.opt("n") !is String || e.opt("s") !is Number || e.opt("d") !is Number)) {
                        error("Niepoprawny wpis rankingu: $key/$i")
                    }
                    val score = e.optLong("s")
                    if (best.size >= 20 && score <= best[best.lastIndex].score) continue
                    val id = e.optString("r", "").takeIf { REPLAY_ID.matches(it) }
                    val entry = ScoreEntry(e.optString("n"), score, e.optLong("d"), id)
                    var pos = 0
                    while (pos < best.size && best[pos].score >= score) pos++
                    best.add(pos, entry)
                    if (best.size > 20) best.removeAt(20)
                }
                newBoards[key] = best
            }
        }

        val loadedWeek = json.optLong("weeklyWeek", -1L).takeIf { it in -1L..1_000_000L } ?: -1L
        val loadedClaimed = json.optInt("weeklyClaimed", 0) and 7
        val loadedClaims = HashMap<Long, Int>()
        obj("weeklyClaims")?.let { claims ->
            val it = claims.keys()
            while (it.hasNext()) {
                val key = it.next()
                val week = key.toLongOrNull() ?: continue
                if (week !in 0L..1_000_000L) continue
                loadedClaims[week] = claims.optInt(key, 0) and 7
            }
        }
        if (loadedWeek >= 0L) loadedClaims[loadedWeek] = loadedClaimed
        // Uszkodzony plik nie może napełnić pamięci nieograniczoną historią.
        if (loadedClaims.size > 8) {
            val keep = loadedClaims.keys.sortedDescending().take(8).toSet()
            loadedClaims.keys.removeAll { it !in keep }
        }

        val newCounters = HashMap<String, Long>()
        obj("achievementCounters")?.let { c ->
            val known = AchievementMetric.values().map { it.name }.toSet()
            val it = c.keys()
            while (it.hasNext()) {
                val key = it.next()
                if (key in known) {
                    val value = c.optLong(key, 0L).coerceAtLeast(0L)
                    newCounters[key] = when (key) {
                        AchievementMetric.STEAM_VARIANTS.name,
                        AchievementMetric.SILICON_VARIANTS.name -> value and 3L
                        AchievementMetric.BARRAGE_EIGHT.name,
                        AchievementMetric.BOSS_KNOCKBACK.name,
                        AchievementMetric.BOSS_AURA.name,
                        AchievementMetric.BOSS_SHIELD.name,
                        AchievementMetric.BOSS_RECRUITER.name,
                        AchievementMetric.BOSS_RAGE.name,
                        AchievementMetric.WEEKLY_GOLD.name,
                        AchievementMetric.CUSTOM_THREE.name,
                        AchievementMetric.REPLAY_RECORD.name -> value.coerceAtMost(1L)
                        AchievementMetric.RAIN_WINS.name,
                        AchievementMetric.WIND_WINS.name,
                        AchievementMetric.NIGHT_WINS.name -> value.coerceAtMost(10_000_000L)
                        else -> value
                    }
                }
            }
        }
        val newUnlocked = HashSet<String>()
        arr("unlockedAchievements")?.let { ids ->
            for (i in 0 until minOf(ids.length(), 128)) {
                val id = ids.optString(i)
                if (Achievements.byId(id) != null) newUnlocked.add(id)
            }
        }
        val newDailySeeds = HashSet<Long>()
        arr("achievementDailySeeds")?.let { days ->
            for (i in 0 until minOf(days.length(), 4096)) {
                val day = days.optLong(i, -1L)
                if (day >= 0L) newDailySeeds.add(day)
            }
        }
        val missions = obj("missions")
        fun modeArray(key: String, upper: Long): LongArray {
            val a = arr(key) ?: return LongArray(modeBattles.size)
            return LongArray(modeBattles.size) { i ->
                if (i >= a.length()) 0L
                else {
                    if (a.opt(i) !is Number) error("Niepoprawny licznik: " + key)
                    a.optLong(i).coerceIn(0L, upper)
                }
            }
        }
        val newModeBattles = modeArray("modeBattles", 10_000_000L)
        val newModeWins = modeArray("modeWins", 10_000_000L)
        val newModeSeconds = modeArray("modeSeconds", 3_155_760_000L)
        val newWinStreak = json.optInt("winStreak", 0).coerceIn(0, 10_000_000)
        val newBestStreak = json.optInt("bestWinStreak", 0).coerceIn(0, 10_000_000)
        val savedSkirmish = obj("skirmishSetup")
        val newHistory = ArrayList<SkirmishResult>()
        arr("skirmishHistory")?.let { list ->
            for (i in 0 until minOf(20, list.length())) {
                val entry = list.optJSONObject(i) ?: continue
                val settings = SkirmishSetup(
                    entry.optInt("startEra", 0), entry.optInt("maxEra", com.centurywar.game.Defs.ERA_COUNT - 1),
                    entry.optInt("personality", -1), entry.optInt("powerPercent", 105),
                    entry.optInt("modifierMask", 0),
                ).sanitized()
                newHistory.add(SkirmishResult(
                    entry.optLong("score", 0L).coerceAtLeast(0L),
                    entry.optLong("day", 0L).coerceAtLeast(0L),
                    entry.optBoolean("won", false),
                    entry.optBoolean("ranked", false), settings,
                ))
            }
        }

        // Zmieniamy aktualny stan dopiero po pomyślnym przetworzeniu całego dokumentu.
        name = json.optString("name", "")
        medals = json.optInt("medals", 0).coerceAtLeast(0)
        levels.clear(); levels.putAll(newLevels)
        newStars.copyInto(stars)
        bestWave = json.optInt("bestWave", 0).coerceAtLeast(0)
        lastRankedDaily = json.optLong("lastRankedDaily", -1L).coerceAtLeast(-1L)
        weeklyWeek = loadedWeek
        weeklyClaimed = if (loadedWeek >= 0L) loadedClaimed else 0
        weeklyClaimHistory.clear(); weeklyClaimHistory.putAll(loadedClaims)
        tutorialDone = json.optBoolean("tutorialDone", false)
        whatsNewSeenVersion = json.optString("whatsNewSeenVersion", "")
        hintsSeen = json.optLong("hintsSeen", 0L).coerceIn(0L, HINT_VALID_MASK)
        hintsEnabled = json.optBoolean("hintsEnabled", true)
        eraIntrosSeen = json.optLong("eraIntrosSeen", 0L).coerceIn(0L, (1L shl Defs.ERA_COUNT) - 1L)
        eraIntrosEnabled = json.optBoolean("eraIntrosEnabled", true)
        val storedId = json.optString("playerId", "")
        playerId = if (validRankingId(storedId)) storedId else ""
        val storedSecret = json.optString("playerSecret", "")
        playerSecret = when {
            playerId.isEmpty() -> ""
            validRankingSecret(storedSecret) -> storedSecret
            else -> newRankingSecret() // migracja z profilu bez sekretu
        }
        globalRanking = json.optBoolean("globalRanking", false) && playerId.isNotEmpty()
        quality = json.optInt("quality", 0).let { if (it in 0..3) it else 0 }
        fpsCap = json.optInt("fpsCap", 0).let { if (it == 0 || it == 30 || it == 60) it else 0 }
        vibrationLevel = when (val stored = if (json.has("vibrationLevel")) json.opt("vibrationLevel") else json.opt("vibration")) {
            is Boolean -> if (stored) 2 else 0
            is Number -> stored.toInt().coerceIn(0, 2)
            else -> 2
        }
        screenShake = json.optInt("screenShake", 2).coerceIn(0, 2)
        palette = json.optInt("palette", 0)
        teamMarkers = json.optBoolean("teamMarkers", false)
        textScale = json.optInt("textScale", 100)
        reduceShake = json.optBoolean("reduceShake", false)
        reduceFlash = json.optBoolean("reduceFlash", false)
        sound = json.optBoolean("sound", true)
        // Stare profile (także z sound=false) zachowują domyślne 80% efektów.
        sfxVolume = json.optDouble("sfxVolume", 0.8).toFloat()
        showFps = json.optBoolean("showFps", false)
        lang = json.optString("lang", "")
        skirmishDiff = json.optInt("skirmishDiff", 1).coerceIn(0, 3)
        val defaultSetup = SkirmishSetup.defaults(skirmishDiff)
        skirmishSetup = savedSkirmish?.let {
            SkirmishSetup(
                it.optInt("startEra", defaultSetup.startEra),
                it.optInt("maxEra", defaultSetup.maxEra),
                it.optInt("personality", defaultSetup.personality),
                it.optInt("powerPercent", defaultSetup.powerPercent),
                it.optInt("modifierMask", defaultSetup.modifierMask),
            ).sanitized()
        } ?: defaultSetup
        battles = json.optInt("battles", 0).coerceAtLeast(0)
        wins = json.optInt("wins", 0).coerceAtLeast(0)
        totalKills = json.optLong("totalKills", 0L).coerceAtLeast(0L)
        playSeconds = json.optLong("playSeconds", 0L).coerceAtLeast(0L)
        wins = wins.coerceAtMost(battles)
        for (i in modeBattles.indices) {
            modeBattles[i] = newModeBattles[i].toInt()
            modeWins[i] = newModeWins[i].coerceAtMost(newModeBattles[i]).toInt()
            modeSeconds[i] = newModeSeconds[i]
        }
        winStreak = newWinStreak
        bestWinStreak = maxOf(newWinStreak, newBestStreak)
        musicVolume = json.optDouble("musicVolume", 0.5).toFloat()
        notifications = json.optBoolean("notifications", true)
        notifyHour = json.optInt("notifyHour", 18).coerceIn(0, 23)
        achievementCounters.clear(); achievementCounters.putAll(newCounters)
        unlockedAchievements.clear(); unlockedAchievements.addAll(newUnlocked)
        achievementDailySeeds.clear(); achievementDailySeeds.addAll(newDailySeeds)
        achievementDoctrineMask = json.optInt("achievementDoctrineMask", 0) and
            ((1 shl com.centurywar.game.Doctrine.values().size) - 1)
        missionState.fromJson(missions)
        boards.clear(); boards.putAll(newBoards)
        skirmishHistory.clear(); skirmishHistory.addAll(newHistory)
    }

    private fun clearInMemory() {
        name = ""; medals = 0; levels.clear(); stars.fill(0)
        bestWave = 0; lastRankedDaily = -1L; tutorialDone = false
        whatsNewSeenVersion = ""; hasExistingSave = false
        quality = 0; fpsCap = 0; vibrationLevel = 2; screenShake = 2; sound = true
        hintsSeen = 0L; hintsEnabled = true
        eraIntrosSeen = 0L; eraIntrosEnabled = true
        playerId = ""; playerSecret = ""; globalRanking = false
        weeklyWeek = -1L; weeklyClaimed = 0; weeklyClaimHistory.clear()
        quality = 0; fpsCap = 0; vibrationLevel = 2; sound = true
        palette = 0; teamMarkers = false; textScale = 100
        reduceShake = false; reduceFlash = false
        showFps = false; lang = ""; skirmishDiff = 1; skirmishSetup = SkirmishSetup.defaults()
        battles = 0; wins = 0; totalKills = 0L; playSeconds = 0L
        modeBattles.fill(0); modeWins.fill(0); modeSeconds.fill(0L)
        winStreak = 0; bestWinStreak = 0
        musicVolume = 0.5f; sfxVolume = 0.8f; notifications = true; notifyHour = 18
        achievementCounters.clear(); unlockedAchievements.clear()
        achievementDailySeeds.clear(); achievementDoctrineMask = 0
        missionState.reset()
        boards.clear(); skirmishHistory.clear()
    }

    /** Nigdy nie nadpisujemy poprzedniej kopii uszkodzonego profilu. */
    private fun preserveCorrupt(source: File): Boolean = try {
        var backup = File(file.parentFile, file.name + ".corrupt")
        var n = 1
        while (backup.exists()) {
            backup = File(file.parentFile, file.name + ".corrupt." + n++)
        }
        source.copyTo(backup, overwrite = false)
        true
    } catch (_: Exception) {
        false
    }

    @Synchronized
    fun load() {
        hasExistingSave = false
        // Odczyt nigdy nie wyprzedza zapisu tego profilu czekającego w kolejce I/O.
        awaitWrites(2_000L)
        GameIoCounter.diskAccess()
        val tmp = File(file.parentFile, file.name + ".tmp")
        if (!file.exists() && !tmp.exists()) return
        // Nawet starsza data pliku z przyszłej wersji jest ważniejsza niż zapis .tmp.
        if (file.exists()) {
            try {
                if (JSONObject(file.readText(Charsets.UTF_8)).optInt("v", 1) > CURRENT_VERSION) {
                    newerProfileVersion = true
                    return
                }
            } catch (_: Exception) {
                // Uszkodzony plik zostanie obsłużony w pętli poniżej.
            }
        }
        val tmpFirst = tmp.exists() && (!file.exists() || tmp.lastModified() >= file.lastModified())
        val candidates = if (tmpFirst) arrayOf(tmp, file) else arrayOf(file, tmp)
        for (candidate in candidates) {
            if (!candidate.exists()) continue
            try {
                val json = JSONObject(candidate.readText(Charsets.UTF_8))
                val version = json.optInt("v", 1)
                // Nowszej wersji nie wolno nadpisać ze starszej aplikacji.
                if (version > CURRENT_VERSION) {
                    newerProfileVersion = true
                    return
                }
                restore(migrate(json, version), version >= CURRENT_VERSION)
                hasExistingSave = true
                newerProfileVersion = false
                // Migracja starego profilu: sekret musi trafić na dysk przed siecią.
                if (playerId.isNotEmpty() && !validRankingSecret(json.optString("playerSecret", ""))) save()
                if (candidate == tmp) {
                    // Przerwany zapis: odtwarzamy pełną, zweryfikowaną zawartość .tmp.
                    if (!tmp.renameTo(file)) {
                        try {
                            java.nio.file.Files.move(
                                tmp.toPath(), file.toPath(),
                                java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                                java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                            )
                        } catch (_: Exception) {
                            // Poprawny zapis wciąż jest dostępny jako .tmp.
                        }
                    }
                } else if (tmp.exists() && tmp.lastModified() <= file.lastModified()) {
                    tmp.delete()
                }
                return
            } catch (_: Exception) {
                if (preserveCorrupt(candidate) && candidate == tmp) tmp.delete()
            }
        }
        clearInMemory()
        newerProfileVersion = false
    }

    @Synchronized
    fun pruneDailyBoards(today: Long) {
        boards.keys.filter { it.startsWith("daily_") && (it.removePrefix("daily_").toLongOrNull() ?: 0L) < today - 7 }
            .forEach { boards.remove(it) }
    }

    @Synchronized
    fun reset() {
        val keepName = name
        medals = 0; levels.clear(); stars.fill(0); bestWave = 0; lastRankedDaily = -1L
        weeklyWeek = -1L; weeklyClaimed = 0; weeklyClaimHistory.clear()
        tutorialDone = false; hintsSeen = 0L; hintsEnabled = true
        eraIntrosSeen = 0L; eraIntrosEnabled = true
        battles = 0; wins = 0; totalKills = 0; playSeconds = 0; boards.clear()
        modeBattles.fill(0); modeWins.fill(0); modeSeconds.fill(0L)
        winStreak = 0; bestWinStreak = 0
        achievementCounters.clear(); unlockedAchievements.clear()
        achievementDailySeeds.clear(); achievementDoctrineMask = 0
        missionState.reset()
        skirmishHistory.clear()
        name = keepName
        save()
    }
}
