package com.centurywar.meta

import com.centurywar.game.Bonuses
import com.centurywar.game.Campaign
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import kotlin.math.pow

class ResearchDef(val key: String, val max: Int, val baseCost: Int, val growth: Float) {
    fun cost(level: Int): Int = (baseCost * growth.pow(level)).toInt()
}

class ScoreEntry(val name: String, val score: Long, val day: Long)

/**
 * Trwały postęp gracza: medale, badania, gwiazdki kampanii, rekordy i ustawienia.
 * Zapis to ręcznie zbudowany JSON w pamięci aplikacji. Zapis jest atomowy
 * (plik tymczasowy + rename), więc przerwanie w trakcie zapisu nie psuje postępu.
 */
class Profile(private val file: File) {

    companion object {
        const val CURRENT_VERSION = 3
        const val HINT_COUNT = 17
        const val HINT_VALID_MASK = 131071L
        private val hintWriter = java.util.concurrent.Executors.newSingleThreadExecutor { task ->
            Thread(task, "cw-hints").apply { isDaemon = true }
        }
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

        val research = listOf(
            ResearchDef("r_elite", 1, 120, 1f),
            ResearchDef("r_rally", 1, 90, 1f),
            ResearchDef("r_income", 12, 40, 1.38f),
            ResearchDef("r_unitDmg", 15, 45, 1.33f),
            ResearchDef("r_unitHp", 15, 45, 1.33f),
            ResearchDef("r_startGold", 10, 30, 1.35f),
            ResearchDef("r_baseHp", 10, 40, 1.36f),
            ResearchDef("r_turretDmg", 10, 40, 1.36f),
            ResearchDef("r_train", 10, 50, 1.38f),
            ResearchDef("r_xp", 10, 50, 1.4f),
            ResearchDef("r_cap", 6, 150, 1.6f),
            ResearchDef("r_cd", 8, 70, 1.42f),
            ResearchDef("r_slots", 2, 260, 2.5f),
        )
        const val BOARD_ENDLESS = "endless"
        const val BOARD_CAMPAIGN = "campaign"
        const val BOARD_SKIRMISH = "skirmish"
        fun dailyBoard(day: Long) = "daily_$day"

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
    var tutorialDone = false
    /** Jednorazowe podpowiedzi: bit oznacza już pokazany komunikat. */
    var hintsSeen = 0L
    var hintsEnabled = true

    // ustawienia
    var quality = 0          // 0 auto, 1 low, 2 med, 3 high
    var fpsCap = 0           // 0 = częstotliwość ekranu, 30, 60
    var vibration = true
    var sound = true
    var musicVolume = 0.5f
        set(value) { field = if (value.isFinite()) value.coerceIn(0f, 1f) else 0.5f }
    var showFps = false
    var lang = ""            // "" = systemowy, inaczej kod z L.CODES ("pl", "en", "de"...)
    var skirmishDiff = 1
    var notifications = true // przypomnienia po pierwszym zwycięstwie
    var notifyHour = 18     // godzina lokalna 0..23

    // statystyki
    var battles = 0
    var wins = 0
    var totalKills = 0L
    var playSeconds = 0L

    // Trwały postęp osiągnięć; brak tych pól w starym JSON oznacza zero.
    val achievementCounters = HashMap<String, Long>()
    val unlockedAchievements = HashSet<String>()
    val achievementDailySeeds = HashSet<Long>()
    var achievementDoctrineMask = 0

    val missionState = MissionState()
    val boards = HashMap<String, MutableList<ScoreEntry>>()
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
        hintWriter.execute { save() }
        return true
    }

    fun replayHints() { hintsSeen = 0L; save() }

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

    fun bonuses() = Bonuses(
        startGold = level("r_startGold") * 20f,
        incomeMul = 1f + level("r_income") * 0.06f,
        unitHpMul = 1f + level("r_unitHp") * 0.04f,
        unitDmgMul = 1f + level("r_unitDmg") * 0.04f,
        baseHpMul = 1f + level("r_baseHp") * 0.08f,
        turretDmgMul = 1f + level("r_turretDmg") * 0.06f,
        trainMul = 1f + level("r_train") * 0.05f,
        xpMul = 1f + level("r_xp") * 0.05f,
        popCap = 12 + level("r_cap"),
        abilityCdMul = 1f - level("r_cd") * 0.05f,
        turretSlots = 2 + level("r_slots"),
        eliteUnlocked = level("r_elite") > 0,
        rallyUnlocked = level("r_rally") > 0,
    )

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
    fun submit(board: String, score: Long, day: Long): Boolean {
        val list = boards.getOrPut(board) { ArrayList() }
        val prevBest = list.maxOfOrNull { it.score } ?: -1L
        list.add(ScoreEntry(name, score, day))
        list.sortByDescending { it.score }
        while (list.size > 20) list.removeAt(list.size - 1)
        return score > prevBest
    }

    fun top(board: String): List<ScoreEntry> = boards[board] ?: emptyList()

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
        o.put("tutorialDone", tutorialDone)
        o.put("hintsSeen", hintsSeen and HINT_VALID_MASK)
        o.put("hintsEnabled", hintsEnabled)
        o.put("quality", quality)
        o.put("fpsCap", fpsCap)
        o.put("vibration", vibration)
        o.put("sound", sound)
        o.put("musicVolume", musicVolume.toDouble())
        o.put("showFps", showFps)
        o.put("lang", lang)
        o.put("skirmishDiff", skirmishDiff)
        o.put("notifications", notifications)
        o.put("notifyHour", notifyHour.coerceIn(0, 23))
        o.put("battles", battles)
        o.put("wins", wins)
        o.put("totalKills", totalKills)
        o.put("playSeconds", playSeconds)
        o.put("achievementCounters", JSONObject(achievementCounters as Map<*, *>))
        o.put("unlockedAchievements", JSONArray().apply { unlockedAchievements.sorted().forEach { put(it) } })
        o.put("achievementDailySeeds", JSONArray().apply { achievementDailySeeds.sorted().forEach { put(it) } })
        o.put("achievementDoctrineMask", achievementDoctrineMask)
        o.put("missions", missionState.toJson())
        val b = JSONObject()
        for ((k, list) in boards) {
            b.put(k, JSONArray().apply {
                list.sortedByDescending { it.score }.take(20).forEach {
                    put(JSONObject().put("n", it.name).put("s", it.score).put("d", it.day))
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
        val o = JSONObject(json)
        val version = o.optInt("v", 1)
        require(version in 1..CURRENT_VERSION) { "Nieobsługiwana wersja profilu: $version" }
        restore(migrate(o, version), version >= CURRENT_VERSION)
    }

    /**
     * Synchronizowane: zapis bywa wołany z wątku gry i z wątku UI (onPause). Bez blokady dwa
     * równoległe zapisy pisały do tego samego pliku .tmp i mogły zostawić sklejony, uszkodzony JSON.
     */
    @Synchronized
    fun save() {
        if (newerProfileVersion) return
        try {
            val json = toJson()
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
            if (saved) onSaved?.invoke(json)
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
            for (key in arrayOf("tutorialDone", "vibration", "sound", "showFps")) {
                if (json.opt(key) !is Boolean) error("Niepoprawna flaga: $key")
            }
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
                    val entry = ScoreEntry(e.optString("n"), score, e.optLong("d"))
                    var pos = 0
                    while (pos < best.size && best[pos].score >= score) pos++
                    best.add(pos, entry)
                    if (best.size > 20) best.removeAt(20)
                }
                newBoards[key] = best
            }
        }

        val newCounters = HashMap<String, Long>()
        obj("achievementCounters")?.let { c ->
            val known = AchievementMetric.values().map { it.name }.toSet()
            val it = c.keys()
            while (it.hasNext()) {
                val key = it.next()
                if (key in known) newCounters[key] = c.optLong(key, 0L).coerceAtLeast(0L)
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

        // Zmieniamy aktualny stan dopiero po pomyślnym przetworzeniu całego dokumentu.
        name = json.optString("name", "")
        medals = json.optInt("medals", 0).coerceAtLeast(0)
        levels.clear(); levels.putAll(newLevels)
        newStars.copyInto(stars)
        bestWave = json.optInt("bestWave", 0).coerceAtLeast(0)
        lastRankedDaily = json.optLong("lastRankedDaily", -1L).coerceAtLeast(-1L)
        tutorialDone = json.optBoolean("tutorialDone", false)
        hintsSeen = json.optLong("hintsSeen", 0L).coerceIn(0L, HINT_VALID_MASK)
        hintsEnabled = json.optBoolean("hintsEnabled", true)
        quality = json.optInt("quality", 0).let { if (it in 0..3) it else 0 }
        fpsCap = json.optInt("fpsCap", 0).let { if (it == 0 || it == 30 || it == 60) it else 0 }
        vibration = json.optBoolean("vibration", true)
        sound = json.optBoolean("sound", true)
        showFps = json.optBoolean("showFps", false)
        lang = json.optString("lang", "")
        skirmishDiff = json.optInt("skirmishDiff", 1).coerceIn(0, 3)
        battles = json.optInt("battles", 0).coerceAtLeast(0)
        wins = json.optInt("wins", 0).coerceAtLeast(0)
        totalKills = json.optLong("totalKills", 0L).coerceAtLeast(0L)
        playSeconds = json.optLong("playSeconds", 0L).coerceAtLeast(0L)
        wins = wins.coerceAtMost(battles)
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
    }

    private fun clearInMemory() {
        name = ""; medals = 0; levels.clear(); stars.fill(0)
        bestWave = 0; lastRankedDaily = -1L; tutorialDone = false
        hintsSeen = 0L; hintsEnabled = true
        quality = 0; fpsCap = 0; vibration = true; sound = true
        showFps = false; lang = ""; skirmishDiff = 1
        battles = 0; wins = 0; totalKills = 0L; playSeconds = 0L
        musicVolume = 0.5f; notifications = true; notifyHour = 18
        achievementCounters.clear(); unlockedAchievements.clear()
        achievementDailySeeds.clear(); achievementDoctrineMask = 0
        missionState.reset()
        boards.clear()
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
                newerProfileVersion = false
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
        tutorialDone = false; hintsSeen = 0L; hintsEnabled = true
        battles = 0; wins = 0; totalKills = 0; playSeconds = 0; boards.clear()
        achievementCounters.clear(); unlockedAchievements.clear()
        achievementDailySeeds.clear(); achievementDoctrineMask = 0
        missionState.reset()
        name = keepName
        save()
    }
}
