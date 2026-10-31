package com.centurywar.meta

import com.centurywar.game.AiPersonality
import com.centurywar.game.Battle
import com.centurywar.game.Doctrine
import com.centurywar.game.EndlessCard
import com.centurywar.game.Mode
import com.centurywar.game.Modifier
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Ostatnie bitwy są lokalne i nigdy nie są dołączane do profilu Play Games.
 * Odczyt wykonujemy przed uruchomieniem pętli gry, zapis tylko w jednym wątku I/O.
 */
class BattleHistory(private val file: File) {
    companion object {
        const val LIMIT = 100
        const val WIN = 0
        const val LOSS = 1
        const val SURRENDER = 2
        private const val VERSION = 1
        private const val MAX_COUNTER = 100_000_000
        private val TIPS = setOf("tip_era", "tip_eco", "tip_turret", "tip_ability")

        fun capture(b: Battle, timeMillis: Long, medals: Int, tip: String, surrendered: Boolean): Record {
            val p = b.player
            var doctrineMask = 0
            for (doctrine in p.doctrines) doctrineMask = doctrineMask or (1 shl doctrine.ordinal)
            var modifiersMask = 0
            for (modifier in b.cfg.modifiers) modifiersMask = modifiersMask or (1 shl modifier.ordinal)
            return Record(
                date = timeMillis,
                mode = b.cfg.mode.ordinal,
                stage = b.cfg.stageIndex,
                result = if (surrendered) SURRENDER else if (b.winner == 0) WIN else LOSS,
                stars = b.stars(),
                score = b.score(),
                seconds = b.time.toInt().coerceAtLeast(0),
                kills = p.kills,
                lost = (p.spawned - p.aliveCount).coerceAtLeast(0),
                era = p.era,
                advanced = p.erasAdvanced,
                roles = p.spawnedByRole.copyOf(),
                doctrines = doctrineMask,
                cards = p.cardStacks.copyOf(),
                turrets = p.turretsBuilt,
                barrages = p.barragesUsed,
                rallies = p.ralliesUsed,
                gold = p.goldEarned.toInt().coerceAtLeast(0),
                baseDamage = p.baseDamageTaken.toInt().coerceAtLeast(0),
                wave = b.wave,
                bosses = b.bossesDefeated,
                aiPower = (b.cfg.aiPower * 100f).toInt().coerceIn(0, 10_000),
                personality = b.ai?.personality?.ordinal ?: -1,
                modifiers = modifiersMask,
                medals = medals,
                tipKey = tip.takeIf { it in TIPS } ?: "",
            )
        }

        /** Agregacja wyłącznie lokalnego okna 100 bitew. Liczniki całego życia są w Profile. */
        fun aggregate(records: List<Record>, nowMillis: Long): Metrics {
            val roles = IntArray(4)
            val doctrines = IntArray(Doctrine.entries.size)
            val aiBattles = IntArray(AiPersonality.entries.size)
            val aiWins = IntArray(AiPersonality.entries.size)
            val days = IntArray(14)
            val dayWins = IntArray(14)
            val endlessNewest = ArrayList<Long>(20)
            val today = dayNumber(nowMillis)
            for (record in records) {
                for (i in roles.indices) roles[i] = (roles[i].toLong() +
                    record.roles.getOrElse(i) { 0 }.coerceAtLeast(0).toLong())
                    .coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                for (i in doctrines.indices) if ((record.doctrines and (1 shl i)) != 0) doctrines[i]++
                if (record.personality in aiBattles.indices) {
                    aiBattles[record.personality]++
                    if (record.result == WIN) aiWins[record.personality]++
                }
                val ago = today - dayNumber(record.date)
                if (ago in 0L..13L) {
                    val dayIndex = 13 - ago.toInt()
                    days[dayIndex]++
                    if (record.result == WIN) dayWins[dayIndex]++
                }
                if (record.mode == Mode.ENDLESS.ordinal && endlessNewest.size < 20) {
                    endlessNewest.add(record.score)
                }
            }
            val favoriteRole = roles.indices.maxByOrNull { roles[it] }?.takeIf { roles[it] > 0 } ?: -1
            val favoriteDoctrine = doctrines.indices.maxByOrNull { doctrines[it] }
                ?.takeIf { doctrines[it] > 0 } ?: -1
            val endlessScores = LongArray(endlessNewest.size) { endlessNewest[endlessNewest.lastIndex - it] }
            return Metrics(roles, doctrines, favoriteRole, favoriteDoctrine, aiBattles, aiWins, days, dayWins, endlessScores)
        }

        fun dayNumber(ms: Long): Long = Instant.ofEpochMilli(ms.coerceAtLeast(0L))
            .atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()

        fun percent(wins: Int, total: Int): Int =
            if (total <= 0) 0 else ((wins.toLong().coerceIn(0L, total.toLong()) * 100L) / total).toInt()

        private fun count(o: JSONObject, key: String): Int = o.optLong(key, 0L)
            .coerceIn(0L, MAX_COUNTER.toLong()).toInt()

        private fun readArray(o: JSONObject, key: String, size: Int): IntArray {
            val a = o.optJSONArray(key) ?: return IntArray(size)
            return IntArray(size) { i -> if (i < a.length()) a.optLong(i, 0L).coerceIn(0L, MAX_COUNTER.toLong()).toInt() else 0 }
        }

        private fun parse(o: JSONObject): Record? {
            // Bez tych pól wpis nie jest bitwą, a błędny typ nie staje się milcząco zerem.
            if (o.opt("date") !is Number || o.opt("mode") !is Number ||
                o.opt("result") !is Number) return null
            val mode = o.optInt("mode", -1)
            val result = o.optInt("result", -1)
            if (mode !in Mode.entries.indices || result !in WIN..SURRENDER) return null
            val tip = o.optString("tip", "").takeIf { it in TIPS } ?: ""
            val replay = o.optString("replay", "").take(80)
                .filter { it.isLetterOrDigit() || it == '-' || it == '_' || it == '.' }
            return Record(
                date = o.optLong("date", 0L).coerceIn(0L, 4_102_444_800_000L),
                mode = mode,
                stage = o.optInt("stage", -1).coerceIn(-1, 10_000),
                result = result,
                stars = o.optInt("stars", 0).coerceIn(0, 3),
                score = o.optLong("score", 0L).coerceIn(0L, 10_000_000_000_000L),
                seconds = count(o, "seconds"),
                kills = count(o, "kills"),
                lost = count(o, "lost"),
                era = o.optInt("era", 0).coerceIn(0, 128),
                advanced = count(o, "advanced"),
                roles = readArray(o, "roles", 4),
                doctrines = o.optInt("doctrines", 0) and ((1 shl Doctrine.entries.size) - 1),
                cards = readArray(o, "cards", EndlessCard.entries.size),
                turrets = count(o, "turrets"),
                barrages = count(o, "barrages"),
                rallies = count(o, "rallies"),
                gold = count(o, "gold"),
                baseDamage = count(o, "damage"),
                wave = count(o, "wave"),
                bosses = count(o, "bosses"),
                aiPower = o.optInt("ai", 0).coerceIn(0, 10_000),
                personality = o.optInt("personality", -1).takeIf { it in AiPersonality.entries.indices } ?: -1,
                modifiers = o.optInt("modifiers", 0) and ((1 shl Modifier.entries.size) - 1),
                medals = count(o, "medals"),
                tipKey = tip,
                replayId = replay,
            )
        }
    }

    data class Record(
        val date: Long,
        val mode: Int,
        val stage: Int = -1,
        val result: Int = LOSS,
        val stars: Int = 0,
        val score: Long = 0L,
        val seconds: Int = 0,
        val kills: Int = 0,
        val lost: Int = 0,
        val era: Int = 0,
        val advanced: Int = 0,
        val roles: IntArray = IntArray(4),
        val doctrines: Int = 0,
        val cards: IntArray = IntArray(EndlessCard.entries.size),
        val turrets: Int = 0,
        val barrages: Int = 0,
        val rallies: Int = 0,
        val gold: Int = 0,
        val baseDamage: Int = 0,
        val wave: Int = 0,
        val bosses: Int = 0,
        val aiPower: Int = 0,
        val personality: Int = -1,
        val modifiers: Int = 0,
        val medals: Int = 0,
        val tipKey: String = "",
        val replayId: String = "",
    )

    data class Metrics(
        val roles: IntArray,
        val doctrines: IntArray,
        val favoriteRole: Int,
        val favoriteDoctrine: Int,
        val aiBattles: IntArray,
        val aiWins: IntArray,
        val days: IntArray,
        val dayWins: IntArray,
        val endlessScores: LongArray,
    )

    private val entries = ArrayList<Record>(LIMIT)
    private val writer = Executors.newSingleThreadExecutor { task ->
        Thread(task, "century-history").apply { isDaemon = true }
    }

    /** Wywołanie tylko przy starcie aplikacji (nigdy z wątku renderowania). */
    @Synchronized
    fun load() {
        entries.clear()
        val tmp = File(file.parentFile, file.name + ".tmp")
        for (candidate in listOf(file, tmp)) {
            if (!candidate.exists() || candidate.length() > 1_000_000L) continue
            try {
                val root = JSONObject(candidate.readText(Charsets.UTF_8))
                if (root.optInt("v") != VERSION) continue
                val array = root.optJSONArray("entries") ?: continue
                for (i in 0 until array.length()) {
                    val o = array.optJSONObject(i) ?: continue
                    try {
                        val record = parse(o) ?: continue
                        if (entries.size < LIMIT) entries.add(record)
                    } catch (_: Exception) {
                        // Jeden uszkodzony wpis nie blokuje pozostałych.
                    }
                }
                return
            } catch (_: Exception) {
                // Uszkodzony główny plik: próbujemy ostatni plik tymczasowy.
                entries.clear()
            }
        }
    }

    @Synchronized
    fun snapshot(): List<Record> = ArrayList(entries)

    /** Jedno nowe zdarzenie po zakończeniu bitwy, bez dostępu do dysku w wątku gry. */
    @Synchronized
    fun add(record: Record) {
        entries.add(0, record)
        if (entries.size > LIMIT) entries.removeAt(entries.lastIndex)
        writer.execute { persist() }
    }

    /** Reset profilu usuwa także lokalną historię, nie dotykając stanu w chmurze. */
    @Synchronized
    fun clear() {
        entries.clear()
        writer.execute { persist() }
    }

    private fun persist() {
        val content = synchronized(this) { toJson() }
        try {
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, file.name + ".tmp")
            FileOutputStream(tmp).use {
                it.write(content.toByteArray(Charsets.UTF_8))
                it.flush()
                try { it.fd.sync() } catch (_: Exception) { }
            }
            if (!tmp.renameTo(file)) {
                Files.move(tmp.toPath(), file.toPath(),
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            }
        } catch (_: Exception) {
            // Brak miejsca lub niedostępny plik: zachowujemy bieżącą pamięć historii.
        }
    }

    /** Używane w testach, nigdy w wątku gry. */
    fun awaitWrites() { writer.submit { }.get(10, TimeUnit.SECONDS) }

    @Synchronized
    fun toJson(): String {
        val root = JSONObject()
        root.put("v", VERSION)
        val array = JSONArray()
        for (e in entries) {
            val o = JSONObject()
            o.put("date", e.date); o.put("mode", e.mode); o.put("stage", e.stage)
            o.put("result", e.result); o.put("stars", e.stars); o.put("score", e.score)
            o.put("seconds", e.seconds); o.put("kills", e.kills); o.put("lost", e.lost)
            o.put("era", e.era); o.put("advanced", e.advanced)
            o.put("roles", JSONArray().apply { e.roles.forEach { put(it) } })
            o.put("doctrines", e.doctrines)
            o.put("cards", JSONArray().apply { e.cards.forEach { put(it) } })
            o.put("turrets", e.turrets); o.put("barrages", e.barrages)
            o.put("rallies", e.rallies); o.put("gold", e.gold)
            o.put("damage", e.baseDamage); o.put("wave", e.wave); o.put("bosses", e.bosses)
            o.put("ai", e.aiPower); o.put("personality", e.personality)
            o.put("modifiers", e.modifiers); o.put("medals", e.medals)
            o.put("tip", e.tipKey)
            if (e.replayId.isNotEmpty()) o.put("replay", e.replayId)
            array.put(o)
        }
        root.put("entries", array)
        return root.toString()
    }

    fun close() { writer.shutdown() }
}
