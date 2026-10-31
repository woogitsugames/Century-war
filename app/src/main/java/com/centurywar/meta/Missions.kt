package com.centurywar.meta

import com.centurywar.game.Battle
import com.centurywar.game.Mode
import com.centurywar.game.Modifier
import com.centurywar.game.Rng
import com.centurywar.game.Role
import org.json.JSONArray
import org.json.JSONObject

/** Zadania rozliczane po bitwie lub po udanym zakupie badania. */
enum class MissionType(val dailyGoal: Int, val weeklyGoal: Int, val bonus: Int = 0) {
    BATTLES(2, 12),
    WINS(1, 6, 2),
    KILLS(45, 300),
    UNITS(18, 110),
    ELITES(2, 16, 2),
    RANGED(6, 42),
    HEAVY(4, 30),
    EVOLUTIONS(2, 12),
    RANKS(2, 12),
    DOCTRINES(2, 14),
    WAVES(9, 22, 3),
    MODIFIER_WINS(1, 3, 3),
    RESEARCH(1, 5, 3),
    CAMPAIGN_WINS(1, 5, 2),
    SKIRMISH_WINS(1, 4, 2),
    DAILY_BATTLES(1, 3, 2),
    ENDLESS_BATTLES(1, 4, 2),
    HEALTHY_WINS(1, 4, 3),
    STARS(3, 18),
    ARMORY(2, 12),
    TURRETS(2, 14),
    BARRAGES(1, 9),
    RALLIES(1, 8),
    MINUTES(12, 100),
    SCORE(20_000, 160_000),
    THREE_STARS(1, 3, 3),
    GOLD(2_000, 20_000),
    KILL_VALUE(500, 8_000),
    HARD_WINS(1, 4, 3),
}

/** Stałe nagrody i cele pozostają niezmienione do końca okresu. */
class MissionItem(
    val id: String,
    val type: MissionType,
    val target: Int,
    val reward: Int,
    val modifier: Modifier? = null,
    var progress: Int = 0,
    var claimed: Boolean = false,
) {
    val completed: Boolean get() = progress >= target

    internal fun add(value: Int) {
        if (value > 0 && !claimed) progress = (progress.toLong() + value).coerceAtMost(target.toLong()).toInt()
    }

    internal fun reach(value: Int) {
        if (!claimed) progress = maxOf(progress, value).coerceAtMost(target)
    }

    internal fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("type", type.name).put("goal", target)
        .put("reward", reward).put("modifier", modifier?.name ?: "")
        .put("progress", progress).put("claimed", claimed)

    companion object {
        internal fun fromJson(json: JSONObject): MissionItem? {
            val type = try { MissionType.valueOf(json.optString("type")) } catch (_: IllegalArgumentException) { return null }
            val id = json.optString("id")
            val goal = json.optInt("goal")
            val reward = json.optInt("reward")
            if (id.isBlank() || goal !in 1..1_000_000 || reward !in 0..10_000) return null
            val modifier = Modifier.values().firstOrNull { it.name == json.optString("modifier") }
            if (type == MissionType.MODIFIER_WINS && modifier == null) return null
            return MissionItem(id, type, goal, reward, modifier, json.optInt("progress").coerceIn(0, goal), json.optBoolean("claimed"))
        }
    }
}

/** Stan zapisywany w profilu; domyślne wartości wspierają stare pliki JSON. */
class MissionState {
    var dailyDay = Long.MIN_VALUE
    var weeklyWeek = Long.MIN_VALUE
    var lastSeenDay = Long.MIN_VALUE
    var loginDay = Long.MIN_VALUE
    var loginClaimedDay = Long.MIN_VALUE
    var loginStreak = 0
    var loginRewardToday = 0
    val daily = ArrayList<MissionItem>(3)
    val weekly = ArrayList<MissionItem>(3)

    fun reset() {
        dailyDay = Long.MIN_VALUE; weeklyWeek = Long.MIN_VALUE; lastSeenDay = Long.MIN_VALUE
        loginDay = Long.MIN_VALUE; loginClaimedDay = Long.MIN_VALUE; loginStreak = 0; loginRewardToday = 0
        daily.clear(); weekly.clear()
    }

    fun toJson(): JSONObject = JSONObject()
        .put("day", dailyDay).put("week", weeklyWeek).put("seen", lastSeenDay)
        .put("loginDay", loginDay).put("loginClaimed", loginClaimedDay).put("streak", loginStreak)
        .put("loginRewardToday", loginRewardToday)
        .put("daily", JSONArray().apply { daily.forEach { put(it.toJson()) } })
        .put("weekly", JSONArray().apply { weekly.forEach { put(it.toJson()) } })

    fun fromJson(json: JSONObject?) {
        reset()
        if (json == null) return
        dailyDay = json.optLong("day", Long.MIN_VALUE)
        weeklyWeek = json.optLong("week", Long.MIN_VALUE)
        lastSeenDay = json.optLong("seen", Long.MIN_VALUE)
        loginDay = json.optLong("loginDay", Long.MIN_VALUE)
        loginClaimedDay = json.optLong("loginClaimed", Long.MIN_VALUE)
        loginStreak = json.optInt("streak", 0).coerceIn(0, 7)
        loginRewardToday = json.optInt("loginRewardToday", 0).coerceIn(0, 10_000)
        readItems(json.optJSONArray("daily"), daily)
        readItems(json.optJSONArray("weekly"), weekly)
    }

    private fun readItems(json: JSONArray?, output: MutableList<MissionItem>) {
        if (json == null) return
        for (i in 0 until minOf(json.length(), 3)) {
            json.optJSONObject(i)?.let { MissionItem.fromJson(it)?.let(output::add) }
        }
    }
}

/** Zarządza rotacją misji, postępem i jednorazowym odbiorem nagród. */
class Missions(private val profile: Profile) {
    companion object {
        const val DAY_MILLIS = 86_400_000L
        private val LOGIN_REWARDS = intArrayOf(5, 8, 12, 17, 23, 30, 40)

        fun utcDay(nowMillis: Long): Long = Math.floorDiv(nowMillis, DAY_MILLIS)
        fun utcWeek(nowMillis: Long): Long = Math.floorDiv(utcDay(nowMillis) + 3L, 7L)
        fun refresh(profile: Profile, nowMillis: Long): MissionState = Missions(profile).refresh(nowMillis)
    }

    val state: MissionState get() = profile.missionState

    /** Uruchamia się tylko po wejściu do ekranu, przy wyniku lub zakupie, nie w każdej klatce. */
    fun refresh(nowMillis: Long = System.currentTimeMillis()): MissionState {
        refreshInternal(nowMillis, true)
        return state
    }

    /** false oznacza cofnięcie zegara względem ostatniego zapisanego dnia UTC. */
    private fun refreshInternal(nowMillis: Long, persist: Boolean): Boolean {
        val day = utcDay(nowMillis)
        if (day < state.lastSeenDay) return false
        val week = Math.floorDiv(day + 3L, 7L)
        var changed = false
        if (state.lastSeenDay != day) {
            state.loginStreak = when {
                state.loginDay == Long.MIN_VALUE -> 1
                day - state.loginDay == 1L -> (state.loginStreak + 1).coerceAtMost(7)
                else -> (state.loginStreak - 2).coerceAtLeast(1)
            }
            state.loginDay = day
            state.loginRewardToday = 0
            state.lastSeenDay = day
            changed = true
        }
        if (state.weeklyWeek != week || state.weekly.size != 3) {
            state.weeklyWeek = week
            select(state.weekly, "w_$week", week * 7L - 3L, true, emptySet())
            changed = true
        }
        if (state.dailyDay != day || state.daily.size != 3) {
            state.dailyDay = day
            select(state.daily, "d_$day", day, false, state.weekly.mapTo(HashSet()) { it.type })
            changed = true
        }
        if (changed && persist) profile.save()
        return true
    }

    /** Rng używa numeru dnia UTC; numer poniedziałku zapewnia stały zestaw przez tydzień. */
    private fun select(
        output: MutableList<MissionItem>, prefix: String, seed: Long,
        weekly: Boolean, excluded: Set<MissionType>,
    ) {
        output.clear()
        val rng = Rng(seed)
        val available = MissionType.values().filter { type ->
            type !in excluded &&
                (type != MissionType.ELITES || profile.level("r_elite") > 0) &&
                (type != MissionType.RALLIES || profile.level("r_rally") > 0)
        }.toMutableList()
        val stars = profile.totalStars().coerceIn(0, 96)
        repeat(3) {
            val type = available.removeAt(rng.nextInt(available.size))
            val modifier = if (type == MissionType.MODIFIER_WINS) {
                val values = Modifier.values()
                values[rng.nextInt(values.size)]
            } else null
            val target = if (weekly) type.weeklyGoal else type.dailyGoal
            val reward = if (weekly) 34 + type.bonus * 4 + stars / 3 else 10 + type.bonus * 2 + stars / 12
            output.add(MissionItem("${prefix}_${type.name}", type, target, reward, modifier))
        }
    }

    /** Wynik przekazujemy dokładnie raz dla każdej zakończonej bitwy. */
    fun onBattleEnd(battle: Battle, nowMillis: Long = System.currentTimeMillis(), persist: Boolean = true, newStars: Int = 0) {
        if (!refreshInternal(nowMillis, false)) return
        for (item in state.daily) updateBattle(item, battle, newStars)
        for (item in state.weekly) updateBattle(item, battle, newStars)
        if (persist) profile.save()
    }

    private fun updateBattle(item: MissionItem, battle: Battle, newStars: Int) {
        val player = battle.player
        val won = battle.winner == 0
        val campaign = battle.cfg.mode == Mode.CAMPAIGN
        val skirmish = battle.cfg.mode == Mode.SKIRMISH
        val endless = battle.cfg.mode == Mode.ENDLESS
        when (item.type) {
            MissionType.BATTLES -> item.add(1)
            MissionType.WINS -> if (won) item.add(1)
            MissionType.KILLS -> item.add(player.kills)
            MissionType.UNITS -> item.add(player.spawned)
            MissionType.ELITES -> item.add(player.trainedByRole[Role.ELITE.ordinal])
            MissionType.RANGED -> item.add(player.trainedByRole[Role.RANGED.ordinal])
            MissionType.HEAVY -> item.add(player.trainedByRole[Role.HEAVY.ordinal])
            MissionType.EVOLUTIONS -> item.add((player.era - battle.cfg.startEra).coerceAtLeast(0))
            MissionType.RANKS -> item.add(player.rank)
            MissionType.DOCTRINES -> item.add(player.doctrines.size)
            MissionType.WAVES -> if (endless) item.reach(battle.wave)
            MissionType.MODIFIER_WINS -> if (won && item.modifier != null && item.modifier in battle.cfg.modifiers) item.add(1)
            MissionType.RESEARCH -> Unit // Zakup rozliczany osobno.
            MissionType.CAMPAIGN_WINS -> if (won && campaign) item.add(1)
            MissionType.SKIRMISH_WINS -> if (won && skirmish) item.add(1)
            MissionType.DAILY_BATTLES -> if (battle.cfg.mode == Mode.DAILY) item.add(1)
            MissionType.ENDLESS_BATTLES -> if (endless) item.add(1)
            MissionType.HEALTHY_WINS -> if (won && player.baseMaxHp > 0f && player.baseHp / player.baseMaxHp >= 0.75f) item.add(1)
            MissionType.STARS -> if (won && campaign) item.add(newStars)
            MissionType.ARMORY -> item.add(player.armory)
            MissionType.TURRETS -> item.add(player.turretsBuilt)
            MissionType.BARRAGES -> item.add(player.barragesUsed)
            MissionType.RALLIES -> item.add(player.ralliesUsed)
            MissionType.MINUTES -> item.add((battle.time / 60f).toInt())
            MissionType.SCORE -> item.add(battle.score().coerceIn(0L, Int.MAX_VALUE.toLong()).toInt())
            MissionType.THREE_STARS -> if (campaign && battle.stars() == 3) item.add(1)
            MissionType.GOLD -> item.add(player.goldEarned.toInt().coerceAtLeast(0))
            MissionType.KILL_VALUE -> item.add(player.killValue)
            MissionType.HARD_WINS -> if (won && battle.cfg.aiPower >= 1.2f) item.add(1)
        }
    }

    /** Wywoływane przez Profile.buy wyłącznie po udanym zakupie. */
    fun onResearchBought(nowMillis: Long = System.currentTimeMillis(), persist: Boolean = true) {
        if (!refreshInternal(nowMillis, false)) return
        for (item in state.daily) if (item.type == MissionType.RESEARCH) item.add(1)
        for (item in state.weekly) if (item.type == MissionType.RESEARCH) item.add(1)
        if (persist) profile.save()
    }

    /** Zwraca liczbę otrzymanych medali; 0 oznacza brak uprawnienia do odbioru. */
    fun claim(id: String, nowMillis: Long = System.currentTimeMillis()): Int {
        if (!refreshInternal(nowMillis, true)) return 0
        val item = state.daily.firstOrNull { it.id == id } ?: state.weekly.firstOrNull { it.id == id } ?: return 0
        if (!item.completed || item.claimed) return 0
        item.claimed = true
        profile.medals = (profile.medals.toLong() + item.reward).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        profile.save()
        return item.reward
    }

    /** Nagroda za logowanie może być odebrana tylko raz w danym dniu UTC. */
    fun claimLogin(nowMillis: Long = System.currentTimeMillis()): Int {
        if (!refreshInternal(nowMillis, true)) return 0
        if (state.loginDay != utcDay(nowMillis) || state.loginClaimedDay == state.loginDay) return 0
        val reward = LOGIN_REWARDS[state.loginStreak.coerceIn(1, 7) - 1] + profile.totalStars() / 16
        state.loginClaimedDay = state.loginDay
        state.loginRewardToday = reward
        profile.medals = (profile.medals.toLong() + reward).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        profile.save()
        return reward
    }

    fun pendingLoginReward(nowMillis: Long = System.currentTimeMillis()): Int {
        if (!refreshInternal(nowMillis, true)) return 0
        if (state.loginClaimedDay == state.loginDay) return 0
        return LOGIN_REWARDS[state.loginStreak.coerceIn(1, 7) - 1] + profile.totalStars() / 16
    }
}
