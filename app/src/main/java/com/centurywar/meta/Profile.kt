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
            o.optInt("v", -1) == 1 &&
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

    // ustawienia
    var quality = 0          // 0 auto, 1 low, 2 med, 3 high
    var fpsCap = 0           // 0 = częstotliwość ekranu, 30, 60
    var vibration = true
    var sound = true
    var showFps = false
    var lang = ""            // "" = systemowy, inaczej kod z L.CODES ("pl", "en", "de"...)
    var skirmishDiff = 1

    // statystyki
    var battles = 0
    var wins = 0
    var totalKills = 0L
    var playSeconds = 0L

    val boards = HashMap<String, MutableList<ScoreEntry>>()

    /** Samouczek odpali się ponownie przy następnym starcie etapu 1 kampanii. */
    fun replayTutorial() { tutorialDone = false; save() }

    fun level(key: String) = levels[key] ?: 0

    fun canBuy(def: ResearchDef): Boolean = level(def.key) < def.max && medals >= def.cost(level(def.key))

    fun buy(def: ResearchDef): Boolean {
        if (!canBuy(def)) return false
        medals -= def.cost(level(def.key))
        levels[def.key] = level(def.key) + 1
        save()
        return true
    }

    fun bonuses() = Bonuses(
        startGold = level("r_startGold") * 40f,
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

    /** Ten sam format JSON v1, którego używa plik profilu. */
    @Synchronized
    fun toJson(): String {
        val o = JSONObject()
        o.put("v", 1)
        o.put("name", name)
        o.put("medals", medals)
        o.put("levels", JSONObject(levels as Map<*, *>))
        o.put("stars", JSONArray().apply { stars.forEach { put(it) } })
        o.put("bestWave", bestWave)
        o.put("lastRankedDaily", lastRankedDaily)
        o.put("tutorialDone", tutorialDone)
        o.put("quality", quality)
        o.put("fpsCap", fpsCap)
        o.put("vibration", vibration)
        o.put("sound", sound)
        o.put("showFps", showFps)
        o.put("lang", lang)
        o.put("skirmishDiff", skirmishDiff)
        o.put("battles", battles)
        o.put("wins", wins)
        o.put("totalKills", totalKills)
        o.put("playSeconds", playSeconds)
        val b = JSONObject()
        for ((k, list) in boards) {
            b.put(k, JSONArray().apply {
                list.forEach { put(JSONObject().put("n", it.name).put("s", it.score).put("d", it.day)) }
            })
        }
        o.put("boards", b)
        return o.toString()
    }

    /** Parsuje dane przed zmianą stanu, aby uszkodzony snapshot nie naruszył profilu. */
    @Synchronized
    fun fromJson(json: String) {
        val o = JSONObject(json)
        val newLevels = HashMap<String, Int>()
        o.optJSONObject("levels")?.let { l ->
            l.keys().forEach { newLevels[it] = l.optInt(it) }
        }
        val newStars = IntArray(stars.size)
        o.optJSONArray("stars")?.let { a ->
            for (i in 0 until minOf(a.length(), newStars.size)) newStars[i] = a.optInt(i)
        }
        val newBoards = HashMap<String, MutableList<ScoreEntry>>()
        o.optJSONObject("boards")?.let { b ->
            b.keys().forEach { k ->
                val a = b.getJSONArray(k)
                val list = ArrayList<ScoreEntry>()
                for (i in 0 until a.length()) {
                    val e = a.getJSONObject(i)
                    list.add(ScoreEntry(e.optString("n"), e.optLong("s"), e.optLong("d")))
                }
                newBoards[k] = list
            }
        }

        for (i in newStars.indices) newStars[i] = newStars[i].coerceIn(0, 3)
        for (d in research) newLevels[d.key]?.let { newLevels[d.key] = it.coerceIn(0, d.max) }

        name = o.optString("name", name)
        medals = o.optInt("medals", 0)
        levels.clear()
        levels.putAll(newLevels)
        newStars.copyInto(stars)
        bestWave = o.optInt("bestWave", 0)
        lastRankedDaily = o.optLong("lastRankedDaily", -1L)
        tutorialDone = o.optBoolean("tutorialDone", false)
        quality = o.optInt("quality", 0)
        fpsCap = o.optInt("fpsCap", 0)
        vibration = o.optBoolean("vibration", true)
        sound = o.optBoolean("sound", true)
        showFps = o.optBoolean("showFps", false)
        lang = o.optString("lang", "")
        skirmishDiff = o.optInt("skirmishDiff", 1)
        battles = o.optInt("battles", 0)
        wins = o.optInt("wins", 0)
        totalKills = o.optLong("totalKills", 0L)
        playSeconds = o.optLong("playSeconds", 0L)
        medals = medals.coerceAtLeast(0)
        bestWave = bestWave.coerceAtLeast(0)
        lastRankedDaily = lastRankedDaily.coerceAtLeast(-1L)
        if (quality !in 0..3) quality = 0
        if (fpsCap != 0 && fpsCap != 30 && fpsCap != 60) fpsCap = 0
        skirmishDiff = skirmishDiff.coerceIn(0, 3)
        battles = battles.coerceAtLeast(0)
        wins = wins.coerceIn(0, battles)
        totalKills = totalKills.coerceAtLeast(0L)
        playSeconds = playSeconds.coerceAtLeast(0L)
        boards.clear()
        boards.putAll(newBoards)
    }


    /**
     * Synchronizowane: zapis bywa wołany z wątku gry i z wątku UI (onPause). Bez blokady dwa
     * równoległe zapisy pisały do tego samego pliku .tmp i mogły zostawić sklejony, uszkodzony JSON.
     */
    @Synchronized
    fun save() {
        try {
            val json = toJson()
            val tmp = File(file.parentFile, file.name + ".tmp")
            FileOutputStream(tmp).use { out ->
                out.write(json.toByteArray(Charsets.UTF_8))
                out.flush()
                // fsync przed rename: po utracie zasilania nie zostanie pusty plik profilu.
                try { out.fd.sync() } catch (_: Exception) { }
            }
            val saved = if (tmp.renameTo(file)) true else {
                file.delete()
                tmp.renameTo(file)
            }
            if (saved) onSaved?.invoke(json)
        } catch (_: Exception) {
            // Brak miejsca itp. — gra działa dalej, zapis spróbuje ponownie przy kolejnej okazji.
        }
    }

    @Synchronized
    fun load() {
        // Awaryjny zapis (delete + rename) mógł zostać przerwany po usunięciu głównego pliku.
        val tmp = File(file.parentFile, file.name + ".tmp")
        val src = if (file.exists()) file else if (tmp.exists()) tmp else return
        try {
            fromJson(src.readText())
        } catch (_: Exception) {
            // Uszkodzony plik — zaczynamy od czystego profilu, ale nie nadpisujemy go od razu.
            try { src.copyTo(File(file.parentFile, file.name + ".corrupt"), overwrite = true) } catch (_: Exception) { }
        }
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
        tutorialDone = false; battles = 0; wins = 0; totalKills = 0; playSeconds = 0; boards.clear()
        name = keepName
        save()
    }
}
