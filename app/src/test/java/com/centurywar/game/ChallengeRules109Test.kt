package com.centurywar.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Regresje rotacji wyzwań i niezmienności dawnych wyników rankingu. */
class ChallengeRules109Test {
    private val surrenderReplay = "Bf8AAQAL" // Replay v5: poddanie w kroku zerowym.

    @Test
    fun all365DailyChallengesHaveAtLeast14DaysBetweenRepeatedRules() {
        val lastDay = HashMap<String, Long>()
        val themes = HashSet<String>()
        val start = Daily.RELEASE_DAY
        for (day in start until start + 365L) {
            val c = Daily.config(day, Bonuses())
            val signature = c.modifiers.map { it.ordinal }.sorted().joinToString(",") +
                "|" + c.weather.ordinal
            val previous = lastDay.put(signature, day)
            if (previous != null) {
                assertTrue("Powtórka zestawu po ${day - previous} dniach: $signature",
                    day - previous >= 14L)
            }
            assertEquals("Dzień $day", 2, c.modifiers.size)
            assertFalse(Modifier.NO_TURRETS in c.modifiers &&
                Modifier.EXPENSIVE_TOWERS in c.modifiers)
            Daily.ruleSet(day)?.let {
                themes.add(it.key)
                assertEquals(setOf(it.first, it.second), c.modifiers)
                assertEquals(it.weather, c.weather)
            }
        }
        assertEquals("Każdy nowy zestaw powinien pojawić się w roku", ChallengeRuleSets.COUNT, themes.size)
    }

    @Test
    fun dailyGenerationIsDeterministicAndIndependentOfResearch() {
        for (day in Daily.RELEASE_DAY until Daily.RELEASE_DAY + 100L) {
            val a = Daily.config(day, Bonuses(startGold = 999f))
            val b = Daily.config(day, Bonuses())
            assertEquals(a.seed, b.seed)
            assertEquals(a.modifiers, b.modifiers)
            assertEquals(a.weather, b.weather)
            assertEquals(a.dailyEvent, b.dailyEvent)
            assertEquals(a.startEra, b.startEra)
            assertEquals(a.maxEra, b.maxEra)
            assertEquals(a.aiPower, b.aiPower, 0f)
            assertEquals(14, a.playerBonuses.popCap)
            assertEquals(3, a.playerBonuses.turretSlots)
            assertEquals(0f, a.playerBonuses.startGold, 0f)
        }
    }

    /** Niezależny zapis generatora sprzed zmian, nie korzysta z nowej rotacji. */
    private fun archivedDaily(day: Long): BattleConfig {
        val modifiers = arrayOf(
            Modifier.DOUBLE_INCOME, Modifier.FRAGILE_BASES, Modifier.FAST_UNITS,
            Modifier.NO_TURRETS, Modifier.RANGED_HORDE, Modifier.GLASS_CANNON,
            Modifier.RICH_START, Modifier.SLOW_XP, Modifier.ARMORED_FOE, Modifier.FOG_OF_WAR,
        )
        val rng = Rng(day * 7919L + 17L)
        val mods = HashSet<Modifier>()
        val count = 2 + rng.nextInt(2)
        while (mods.size < count) mods.add(modifiers[rng.nextInt(modifiers.size)])
        val rolledStart = rng.nextInt(3)
        val start = if (rolledStart >= 2) rolledStart + 1 else rolledStart
        val rolledMax = minOf(7, rolledStart + 3)
        val max = if (rolledMax >= 2) rolledMax + 1 else rolledMax
        val power = 1.1f + rng.nextFloat() * 0.25f
        return BattleConfig(
            Mode.DAILY, day, power, start, max, mods,
            Bonuses(popCap = 14, turretSlots = 3),
            variantsEnabled = false, weather = Weather.CLEAR,
        )
    }

    @Test
    fun all365DaysBeforeReleaseKeepArchivedRules() {
        assertEquals(21153L, Daily.RELEASE_DAY)
        for (day in Daily.RELEASE_DAY - 365L until Daily.RELEASE_DAY) {
            val archived = archivedDaily(day)
            val actual = Daily.config(day, Bonuses())
            assertEquals("Dzień $day: modyfikatory", archived.modifiers, actual.modifiers)
            assertEquals("Dzień $day: początek", archived.startEra, actual.startEra)
            assertEquals("Dzień $day: koniec", archived.maxEra, actual.maxEra)
            assertEquals("Dzień $day: siła AI", archived.aiPower, actual.aiPower, 0f)
            assertEquals(archived.seed, actual.seed)
            assertEquals(archived.weather, actual.weather)
            assertEquals(archived.variantsEnabled, actual.variantsEnabled)
            assertEquals(archived.dailyEvent, actual.dailyEvent)
            assertNull(Daily.ruleSet(day))
        }
    }

    @Test
    fun archivedReplayStillVerifiesAgainstDailyRankingConfiguration() {
        // Stały bajtowy zapis Replay v5, a nie powtórka wygenerowana po zmianie.
        val oldLog = Replay.decode(surrenderReplay)
        assertEquals(Replay.Kind.SURRENDER, oldLog.commands.single().kind)
        for (day in listOf(0L, Daily.RELEASE_DAY - 365L, Daily.RELEASE_DAY - 1L)) {
            val fromArchive = Replay.verify(archivedDaily(day), surrenderReplay)
            val fromRanking = Replay.verify(Daily.config(day, Bonuses()), surrenderReplay)
            assertEquals(fromArchive.score, fromRanking.score)
            assertEquals(fromArchive.winner, fromRanking.winner)
            assertEquals(0, fromRanking.steps)
        }
    }

    @Test
    fun newSpecialDayReplayContainsEventAndVerifies() {
        val day = Daily.RELEASE_DAY + 3L
        val cfg = Daily.config(day, Bonuses())
        assertNotNull(cfg.dailyEvent)
        val log = Replay.Log(
            0, listOf(Replay.Command(0, Replay.Kind.SURRENDER)),
            dailyEvent = cfg.dailyEvent,
        )
        val result = Replay.verify(cfg, Replay.encode(log))
        assertEquals(0, result.steps)
        assertEquals(1, result.winner)
    }

    @Test
    fun weeklyThemesCycleThroughAllSixCardsWithoutChangingOldWeeks() {
        val releaseWeek = Weekly.weekNumber(Daily.RELEASE_DAY)
        assertNull(Weekly.ruleSet(releaseWeek))
        val archivedWeek = archivedWeekly(releaseWeek)
        val currentWeek = Weekly.config(releaseWeek)
        assertEquals(archivedWeek.modifiers, currentWeek.modifiers)
        assertEquals(archivedWeek.weeklyCard, currentWeek.weeklyCard)
        assertEquals(archivedWeek.startEra, currentWeek.startEra)
        assertEquals(archivedWeek.maxEra, currentWeek.maxEra)

        val names = HashSet<String>()
        for (week in releaseWeek + 1L..releaseWeek + ChallengeRuleSets.COUNT.toLong()) {
            val rules = Weekly.ruleSet(week)!!
            val cfg = Weekly.config(week)
            names.add(rules.key)
            assertEquals(setOf(rules.first, rules.second), cfg.modifiers)
            assertEquals(rules.weeklyCard, cfg.weeklyCard)
            assertEquals(week, cfg.weekly)
            assertEquals(1, Battle(cfg).player.cardStacks[rules.weeklyCard.ordinal])
        }
        assertEquals(ChallengeRuleSets.COUNT, names.size)
    }

    /** Generator tygodniowy używany przed wdrożeniem nowych zestawów. */
    private fun archivedWeekly(week: Long): BattleConfig {
        val old = arrayOf(
            Modifier.DOUBLE_INCOME, Modifier.FRAGILE_BASES, Modifier.FAST_UNITS,
            Modifier.RANGED_HORDE, Modifier.GLASS_CANNON, Modifier.RICH_START,
            Modifier.SLOW_XP, Modifier.ARMORED_FOE, Modifier.FOG_OF_WAR,
        )
        val newer = old + arrayOf(Modifier.VETERANS, Modifier.EXPENSIVE_TOWERS, Modifier.MERCENARIES)
        val cards = arrayOf(
            EndlessCard.ECONOMY, EndlessCard.BULWARK, EndlessCard.FURY,
            EndlessCard.VETERAN, EndlessCard.FRONTLINE_PLATE, EndlessCard.DRILL_SERGEANT,
            EndlessCard.EXTRA_GOLD, EndlessCard.FORTRESS, EndlessCard.SHARPSHOOTERS,
            EndlessCard.MARCH,
        )
        val seed = week * 104_729L + 211L
        val rng = Rng(seed)
        val start = rng.nextInt(6)
        val max = minOf(Defs.ERA_COUNT - 1, start + 2 + rng.nextInt(2))
        val mods = linkedSetOf<Modifier>()
        val pool = if (week >= Weekly.weekNumber(Daily.RELEASE_DAY)) newer else old
        while (mods.size < 2) mods.add(pool[rng.nextInt(pool.size)])
        val card = cards[rng.nextInt(cards.size)]
        return BattleConfig(Mode.ENDLESS, seed, 1f, start, max, mods,
            Bonuses(popCap = 14, turretSlots = 3), weekly = week, weeklyCard = card)
    }
}
