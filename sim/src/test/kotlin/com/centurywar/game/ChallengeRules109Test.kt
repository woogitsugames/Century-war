package com.centurywar.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Regresje rotacji wyzwań i niezmienności dawnych wyników rankingu. */
class ChallengeRules109Test {
    /** Złota powtórka wyzwania dnia sprzed premiery: pełna bitwa z komendami gracza. */
    private class GoldenDaily(val day: Long, val replay: String, val score: Long, val winner: Int, val steps: Int)

    private fun goldenDaily(): GoldenDaily {
        val text = javaClass.getResourceAsStream("/golden/daily-historical.json")!!
            .bufferedReader(Charsets.UTF_8).use { it.readText() }
        fun field(name: String): String =
            Regex("\"$name\"\\s*:\\s*\"?([-A-Za-z0-9_]+)").find(text)!!.groupValues[1]
        val expected = text.substring(text.indexOf("\"expected\""))
        fun expectedInt(name: String): Int =
            Regex("\"$name\"\\s*:\\s*(-?\\d+)").find(expected)!!.groupValues[1].toInt()
        return GoldenDaily(field("seed").toLong(), field("replay"),
            expectedInt("score").toLong(), expectedInt("winner"), expectedInt("steps"))
    }

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
            if (day >= Daily.RULES_V2_DAY) assertEquals("Dzień $day", 2, c.modifiers.size)
            assertFalse(Modifier.NO_TURRETS in c.modifiers &&
                Modifier.EXPENSIVE_TOWERS in c.modifiers)
            Daily.ruleSet(day)?.let {
                themes.add(it.key)
                assertEquals(setOf(it.first, it.second), c.modifiers)
                assertEquals(Weather.entries[Rng(day * 65537L + 109L).nextInt(Weather.entries.size)], c.weather)
            }
        }
        assertEquals("Każdy nowy zestaw powinien pojawić się w roku", ChallengeRuleSets.COUNT, themes.size)
    }

    @Test
    fun dailyGenerationIsDeterministicAndIndependentOfResearch() {
        for (day in Daily.RULES_V2_DAY until Daily.RULES_V2_DAY + 100L) {
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
    fun pinnedDaysAtReleaseKeepOriginalReleasedGenerator() {
        val pool = Modifier.values()
        for (day in Daily.RELEASE_DAY until Daily.RULES_V2_DAY) {
            val rng = Rng(day * 7919L + 17L)
            val expectedMods = HashSet<Modifier>()
            val count = 2 + rng.nextInt(2)
            while (expectedMods.size < count) expectedMods.add(pool[rng.nextInt(pool.size)])
            val start = rng.nextInt(6)
            val max = minOf(Defs.ERA_COUNT - 1, start + 3)
            val power = 1.1f + rng.nextFloat() * 0.25f
            val expectedWeather = Weather.entries[
                Rng(day * 65537L + 109L).nextInt(Weather.entries.size)]
            val event = if ((day - Daily.RELEASE_DAY) % 4L == 3L)
                DailyEvent.entries[Rng(day * 131_071L + 907L).nextInt(DailyEvent.entries.size)]
            else null
            val actual = Daily.config(day, Bonuses())
            assertNull(Daily.ruleSet(day))
            assertEquals(expectedMods, actual.modifiers)
            assertEquals(start.coerceAtLeast(if (event == DailyEvent.GOLD_RUSH) 2 else 0), actual.startEra)
            assertEquals(max.coerceAtLeast(actual.startEra), actual.maxEra)
            assertEquals(if (event == DailyEvent.MIRROR_ARMY) 1f else power, actual.aiPower, 0f)
            assertEquals(expectedWeather, actual.weather)
            assertEquals(event, actual.dailyEvent)
        }
    }

    @Test
    fun archivedReplayStillVerifiesAgainstDailyRankingConfiguration() {
        // Złota powtórka dnia sprzed premiery: wiele komend i tysiące kroków symulacji.
        val golden = goldenDaily()
        assertTrue(golden.day < Daily.RELEASE_DAY)
        val log = Replay.decode(golden.replay)
        assertTrue("Powtórka musi zawierać komendy gracza", log.commands.size > 3)
        val fromArchive = Replay.verify(archivedDaily(golden.day), golden.replay)
        val fromRanking = Replay.verify(Daily.config(golden.day, Bonuses()), golden.replay)
        assertTrue(fromRanking.steps > 1000)
        assertEquals(golden.score, fromRanking.score)
        assertEquals(golden.winner, fromRanking.winner)
        assertEquals(golden.steps, fromRanking.steps)
        assertEquals(fromArchive.score, fromRanking.score)
        assertEquals(fromArchive.winner, fromRanking.winner)
        assertEquals(fromArchive.steps, fromRanking.steps)
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
        assertEquals(releaseWeek + 3L, Weekly.firstRuleSetWeek())
        for (week in releaseWeek - 52L..releaseWeek + 2L) {
            assertNull(Weekly.ruleSet(week))
            assertSameAsArchived(week)
        }

        val names = HashSet<String>()
        val every = Weekly.RULE_SET_EVERY
        var week = Weekly.firstRuleSetWeek()
        repeat(ChallengeRuleSets.COUNT) {
            val rules = Weekly.ruleSet(week)!!
            val cfg = Weekly.config(week)
            names.add(rules.key)
            assertEquals(setOf(rules.first, rules.second), cfg.modifiers)
            assertEquals(rules.weeklyCard, cfg.weeklyCard)
            assertEquals(week, cfg.weekly)
            assertEquals(1, Battle(cfg).player.cardStacks[rules.weeklyCard.ordinal])
            week += every
        }
        assertEquals(ChallengeRuleSets.COUNT, names.size)
    }

    private fun assertSameAsArchived(week: Long) {
        val archived = archivedWeekly(week)
        val current = Weekly.config(week)
        assertEquals("Tydzień $week", archived.modifiers, current.modifiers)
        assertEquals("Tydzień $week", archived.weeklyCard, current.weeklyCard)
        assertEquals("Tydzień $week", archived.startEra, current.startEra)
        assertEquals("Tydzień $week", archived.maxEra, current.maxEra)
        assertEquals("Tydzień $week", archived.seed, current.seed)
    }

    @Test
    fun weeklyMixesRandomWeeksWithRuleSetsWithoutQuickRepeats() {
        val first = Weekly.firstRuleSetWeek()
        val lastSeen = HashMap<String, Long>()
        var themed = 0
        var random = 0
        for (week in first until first + 156L) {
            val rules = Weekly.ruleSet(week)
            val cfg = Weekly.config(week)
            val iter = cfg.modifiers.iterator()
            val a = iter.next()
            val b = iter.next()
            val asSet = ChallengeRuleSets.forModifiers(a, b) ?: ChallengeRuleSets.forModifiers(b, a)
            if (rules != null) {
                themed++
                assertEquals(rules, asSet)
                assertEquals(rules.weeklyCard, cfg.weeklyCard)
                val previous = lastSeen.put(rules.key, week)
                if (previous != null) {
                    assertTrue("Powtórka ${rules.key} po ${week - previous} tygodniach",
                        week - previous >= 4L)
                }
            } else {
                random++
                // Losowy tydzień nie podszywa się pod zestaw tematyczny.
                assertNull("Tydzień $week", asSet)
                val archived = archivedWeekly(week)
                if (archived.modifiers == cfg.modifiers) assertSameAsArchived(week)
            }
        }
        assertEquals(52, themed)
        assertEquals(104, random)
        // Większość losowych tygodni to dokładnie dawne losowanie (bez kolizji z zestawami).
        var unchanged = 0
        for (week in first until first + 156L) {
            if (Weekly.ruleSet(week) == null && archivedWeekly(week).modifiers == Weekly.config(week).modifiers)
                unchanged++
        }
        assertTrue("Niezmienione losowe tygodnie: $unchanged", unchanged >= 80)
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
