package com.centurywar.meta

import com.centurywar.game.Bonuses
import com.centurywar.game.Campaign
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
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
    }

    var name = "Dowódca"
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
    var musicVolume = 0.5f
        set(value) { field = if (value.isFinite()) value.coerceIn(0f, 1f) else 0.5f }
    var showFps = false
    var lang = ""            // "" = systemowy, "pl", "en"
    var skirmishDiff = 1

    // statystyki
    var battles = 0
    var wins = 0
    var totalKills = 0L
    var playSeconds = 0L

    val boards = HashMap<String, MutableList<ScoreEntry>>()

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

    /** Dodaje wynik do lokalnego rankingu; zwraca true, jeśli to rekord gracza na tej tablicy. */
    fun submit(board: String, score: Long, day: Long): Boolean {
        val list = boards.getOrPut(board) { ArrayList() }
        val prevBest = list.filter { it.name == name }.maxOfOrNull { it.score } ?: -1L
        list.add(ScoreEntry(name, score, day))
        list.sortByDescending { it.score }
        while (list.size > 20) list.removeAt(list.size - 1)
        return score > prevBest
    }

    fun top(board: String): List<ScoreEntry> = boards[board] ?: emptyList()

    // ---------------------------------------------------------------- zapis

    fun save() {
        try {
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
            o.put("musicVolume", musicVolume.toDouble())
            o.put("showFps", showFps)
            o.put("lang", lang)
            o.put("skirmishDiff", skirmishDiff)
            o.put("battles", battles)
            o.put("wins", wins)
            o.put("totalKills", totalKills)
            o.put("playSeconds", playSeconds)
            val b = JSONObject()
            for ((k, list) in boards) {
                // Stare tablice dzienne (starsze niż tydzień) nie są potrzebne.
                b.put(k, JSONArray().apply {
                    list.forEach { put(JSONObject().put("n", it.name).put("s", it.score).put("d", it.day)) }
                })
            }
            o.put("boards", b)
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(o.toString())
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
        } catch (_: Exception) {
            // Brak miejsca itp. — gra działa dalej, zapis spróbuje ponownie przy kolejnej okazji.
        }
    }

    fun load() {
        if (!file.exists()) return
        try {
            val o = JSONObject(file.readText())
            name = o.optString("name", name)
            medals = o.optInt("medals", 0)
            o.optJSONObject("levels")?.let { l -> l.keys().forEach { levels[it] = l.optInt(it) } }
            o.optJSONArray("stars")?.let { a -> for (i in 0 until minOf(a.length(), stars.size)) stars[i] = a.optInt(i) }
            bestWave = o.optInt("bestWave", 0)
            lastRankedDaily = o.optLong("lastRankedDaily", -1L)
            tutorialDone = o.optBoolean("tutorialDone", false)
            quality = o.optInt("quality", 0)
            fpsCap = o.optInt("fpsCap", 0)
            vibration = o.optBoolean("vibration", true)
            sound = o.optBoolean("sound", true)
            musicVolume = o.optDouble("musicVolume", 0.5).toFloat()
            showFps = o.optBoolean("showFps", false)
            lang = o.optString("lang", "")
            skirmishDiff = o.optInt("skirmishDiff", 1)
            battles = o.optInt("battles", 0)
            wins = o.optInt("wins", 0)
            totalKills = o.optLong("totalKills", 0L)
            playSeconds = o.optLong("playSeconds", 0L)
            o.optJSONObject("boards")?.let { b ->
                b.keys().forEach { k ->
                    val a = b.getJSONArray(k)
                    val list = ArrayList<ScoreEntry>()
                    for (i in 0 until a.length()) {
                        val e = a.getJSONObject(i)
                        list.add(ScoreEntry(e.optString("n"), e.optLong("s"), e.optLong("d")))
                    }
                    boards[k] = list
                }
            }
        } catch (_: Exception) {
            // Uszkodzony plik — zaczynamy od czystego profilu, ale nie nadpisujemy go od razu.
            file.copyTo(File(file.parentFile, file.name + ".corrupt"), overwrite = true)
        }
    }

    fun pruneDailyBoards(today: Long) {
        boards.keys.filter { it.startsWith("daily_") && (it.removePrefix("daily_").toLongOrNull() ?: 0L) < today - 7 }
            .forEach { boards.remove(it) }
    }

    fun reset() {
        val keepName = name
        medals = 0; levels.clear(); stars.fill(0); bestWave = 0; lastRankedDaily = -1L
        tutorialDone = false; battles = 0; wins = 0; totalKills = 0; playSeconds = 0; boards.clear()
        name = keepName
        save()
    }
}
