package com.centurywar.game

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Dzień 58 (paczka 206): cztery karty Endless, dwa zestawy tygodnia, dwa wydarzenia dnia. */
class Content206Test {
    private val newCards = listOf(EndlessCard.PIKE_SQUARE, EndlessCard.VANGUARD,
        EndlessCard.BOSS_BOUNTY, EndlessCard.WAR_HORNS)
    private val newEvents = setOf(DailyEvent.SCHOLARS_DAY, DailyEvent.FURY_DAY)

    private fun endless(seed: Long = 206L) = Battle(BattleConfig(Mode.ENDLESS, seed, 1f))

    // ------------------------------------------------------------------ karty

    @Test fun newCardsAreAppendedAfterWager() {
        val last = EndlessCard.WAGER.ordinal
        assertEquals(newCards, EndlessCard.entries.drop(last + 1))
        assertEquals(listOf("ec_pike_square", "ec_vanguard", "ec_boss_bounty", "ec_war_horns"),
            newCards.map { it.textKey })
        for (card in newCards) assertTrue(card.name, EndlessCards.persistent(card))
        assertEquals(EndlessRarity.COMMON, EndlessCard.PIKE_SQUARE.rarity)
        assertEquals(EndlessRarity.UNCOMMON, EndlessCard.VANGUARD.rarity)
        assertEquals(EndlessRarity.RARE, EndlessCard.BOSS_BOUNTY.rarity)
        assertEquals(EndlessRarity.UNCOMMON, EndlessCard.WAR_HORNS.rarity)
    }

    @Test fun newCardsStopAtEightStacks() {
        val b = endless()
        val p = b.player
        for (card in newCards) {
            repeat(EndlessCards.MAX_PERSISTENT_STACKS) {
                assertTrue(card.name, EndlessCards.canOffer(card, p))
                b.applyEndlessCard(p, card)
            }
            assertFalse(card.name, EndlessCards.canOffer(card, p))
            assertEquals(EndlessCards.MAX_PERSISTENT_STACKS, p.cardStacks[card.ordinal])
        }
    }

    /** Utrata HP pierwszej jednostki strony [victimSide] od pierwszego trafienia drugiej strony. */
    private fun hitLoss(
        victimRole: Role, attackerRole: Role, victimSide: Int, card: EndlessCard?, stacks: Int,
        wave: Int = 0, waveTimer: Float = 900f,
    ): Float {
        val b = endless()
        if (card != null) repeat(stacks) { b.applyEndlessCard(b.player, card) }
        val victimOwner = b.sides[victimSide]
        val attackerOwner = b.sides[1 - victimSide]
        assertTrue(b.spawn(victimOwner, victimOwner.unit(victimRole)))
        assertTrue(b.spawn(attackerOwner, attackerOwner.unit(attackerRole)))
        val victim = b.units.first { it.alive && it.side == victimSide }
        val attacker = b.units.first { it.alive && it.side != victimSide }
        victim.x = Defs.LANE / 2f
        val gap = if (attackerRole == Role.RANGED) 120f else 15f
        attacker.x = victim.x + (if (victimSide == 0) gap else -gap)
        victim.cd = 1000f
        attacker.cd = 0f
        b.wave = wave
        b.waveTimer = waveTimer
        val hp = victim.hp
        var steps = 0
        while (victim.hp == hp && steps < 600) { b.step(); steps++ }
        val loss = hp - victim.hp
        assertTrue("$attackerRole musi trafić $victimRole", loss > 0f)
        return loss
    }

    @Test fun pikeSquareReducesOnlyHeavyMeleeHitsOnInfantryWithFloor() {
        val heavy = hitLoss(Role.MELEE, Role.HEAVY, 0, EndlessCard.PIKE_SQUARE, 0)
        assertEquals(heavy * 0.8f, hitLoss(Role.MELEE, Role.HEAVY, 0, EndlessCard.PIKE_SQUARE, 1),
            heavy * 0.002f)
        // 8 stosów: 1 - 0,2 × 2,8 = 0,44, ale dolny limit to 0,6.
        assertEquals(heavy * 0.6f, hitLoss(Role.MELEE, Role.HEAVY, 0, EndlessCard.PIKE_SQUARE, 8),
            heavy * 0.002f)
        // Inne role wroga i inne role gracza bez zmian.
        val blade = hitLoss(Role.MELEE, Role.MELEE, 0, EndlessCard.PIKE_SQUARE, 0)
        assertEquals(blade, hitLoss(Role.MELEE, Role.MELEE, 0, EndlessCard.PIKE_SQUARE, 1), 0f)
        val heavyOnHeavy = hitLoss(Role.HEAVY, Role.HEAVY, 0, EndlessCard.PIKE_SQUARE, 0)
        assertEquals(heavyOnHeavy, hitLoss(Role.HEAVY, Role.HEAVY, 0, EndlessCard.PIKE_SQUARE, 1), 0f)
    }

    @Test fun vanguardBoostsInfantryOnlyAgainstRangedFoes() {
        val vsArcher = hitLoss(Role.RANGED, Role.MELEE, 1, EndlessCard.VANGUARD, 0)
        assertEquals(vsArcher * 1.3f, hitLoss(Role.RANGED, Role.MELEE, 1, EndlessCard.VANGUARD, 1),
            vsArcher * 0.002f)
        assertEquals(vsArcher * 1.48f, hitLoss(Role.RANGED, Role.MELEE, 1, EndlessCard.VANGUARD, 2),
            vsArcher * 0.002f)
        val vsBlade = hitLoss(Role.MELEE, Role.MELEE, 1, EndlessCard.VANGUARD, 0)
        assertEquals(vsBlade, hitLoss(Role.MELEE, Role.MELEE, 1, EndlessCard.VANGUARD, 1), 0f)
        // Strzelcy gracza nie dostają premii.
        val arrow = hitLoss(Role.RANGED, Role.RANGED, 1, EndlessCard.VANGUARD, 0)
        assertEquals(arrow, hitLoss(Role.RANGED, Role.RANGED, 1, EndlessCard.VANGUARD, 1), 0f)
    }

    @Test fun warHornsBoostOnlyTheStartOfAWave() {
        // Fala 1 trwa 27,75 s; waveTimer 25,75 to 2 s od początku, 7,75 to 20 s.
        val early = hitLoss(Role.MELEE, Role.MELEE, 1, EndlessCard.WAR_HORNS, 0, wave = 1, waveTimer = 25.75f)
        assertEquals(early * 1.2f, hitLoss(Role.MELEE, Role.MELEE, 1, EndlessCard.WAR_HORNS, 1,
            wave = 1, waveTimer = 25.75f), early * 0.002f)
        val late = hitLoss(Role.MELEE, Role.MELEE, 1, EndlessCard.WAR_HORNS, 0, wave = 1, waveTimer = 7.75f)
        assertEquals(late, hitLoss(Role.MELEE, Role.MELEE, 1, EndlessCard.WAR_HORNS, 1,
            wave = 1, waveTimer = 7.75f), 0f)
        // Przed pierwszą falą (wave = 0) rogi milczą.
        val before = hitLoss(Role.MELEE, Role.MELEE, 1, EndlessCard.WAR_HORNS, 0)
        assertEquals(before, hitLoss(Role.MELEE, Role.MELEE, 1, EndlessCard.WAR_HORNS, 1), 0f)
    }

    @Test fun waveElapsedFollowsTheWaveTimer() {
        val b = endless()
        assertTrue(b.waveElapsed() < 0f)
        b.waveTimer = 0f
        b.step()
        assertEquals(1, b.wave)
        assertEquals(0f, b.waveElapsed(), 0.001f)
        repeat(60) { b.step() }
        assertTrue(b.waveElapsed() in 0.5f..1.5f)
    }

    /** Złoto gracza po zabiciu bossa jednym trafieniem własnej jednostki. */
    private fun goldAfterBossKill(bounty: Int): Float {
        val b = endless()
        repeat(bounty) { b.applyEndlessCard(b.player, EndlessCard.BOSS_BOUNTY) }
        b.wave = 9
        b.nextBossToSpawn = b.wave + 1
        b.waveTimer = 0f
        b.step()
        b.pendingCards?.let { b.chooseEndlessCard(0) }
        val boss = b.currentBoss()!!
        assertTrue(b.spawn(b.player, b.player.unit(Role.HEAVY)))
        val ally = b.units.last { it.alive && it.side == 0 }
        boss.x = b.player.baseFront + 300f
        boss.cd = 1000f
        boss.hp = 0.5f
        ally.x = boss.x - 12f
        ally.cd = 0f
        b.waveTimer = 900f
        val before = b.bossesDefeated
        var steps = 0
        while (b.bossesDefeated == before && steps < 240) { b.step(); steps++ }
        assertEquals(before + 1, b.bossesDefeated)
        return b.player.gold
    }

    @Test fun bossBountyPaysGoldPerBoss() {
        val plain = goldAfterBossKill(0)
        val scale = Defs.costScale(0)
        assertEquals(120f * scale, goldAfterBossKill(1) - plain, 0.05f)
        assertEquals(120f * scale * 1.6f, goldAfterBossKill(2) - plain, 0.05f)
    }

    @Test fun thousandOffersStayValidWithNewCards() {
        val rng = Rng(20_600L)
        val picks = Array(3) { EndlessCard.ECONOMY }
        val seen = HashSet<EndlessCard>()
        for (state in 0 until 10) {
            val b = endless(206L + state)
            val p = b.player
            if (state % 2 == 1) p.slots = 0
            if (state >= 5) for (card in EndlessCard.entries) {
                repeat(EndlessCards.MAX_PERSISTENT_STACKS) { b.applyEndlessCard(p, card) }
            }
            repeat(100) {
                EndlessCards.draw(rng, p, picks, fog = state % 3 == 0)
                assertEquals(3, picks.toSet().size)
                for (card in picks) assertTrue(EndlessCards.canOffer(card, p, state % 3 == 0))
                seen.addAll(picks)
            }
        }
        assertTrue(seen.containsAll(newCards))
    }

    @Test fun newCardsSurviveSnapshotBitForBit() {
        val a = endless(2_060L)
        a.playerAi = Ai(a, 0)
        for (card in newCards) a.applyEndlessCard(a.player, card)
        repeat(60 * 90) { if (!a.over) a.step(); a.fx.count = 0 }
        val bytes = BattleSnapshot.encode(a)
        val restored = BattleSnapshot.decode(bytes)!!.battle
        for (card in newCards) assertEquals(1, restored.player.cardStacks[card.ordinal])
        assertArrayEquals(bytes, BattleSnapshot.encode(restored))
        repeat(600) { a.step(); restored.step(); a.fx.count = 0; restored.fx.count = 0 }
        assertArrayEquals(BattleSnapshot.encode(a), BattleSnapshot.encode(restored))
        assertEquals(a.score(), restored.score())
    }

    @Test fun olderSnapshotFormatsStillResume() {
        assertEquals(22, BattleSnapshot.VERSION)
        val a = endless(2_061L)
        a.playerAi = Ai(a, 0)
        repeat(60 * 60) { a.step(); a.fx.count = 0 }
        for (version in listOf(16, 17, 18, 19, 20, 21)) {
            val legacy = BattleSnapshot.encode(a, rankedDaily = false, camX = 0f,
                battleId = 9L, formatVersion = version)
            val restored = BattleSnapshot.decode(legacy)
            assertNotNull("zrzut v$version musi się wczytać", restored)
            val b = restored!!.battle
            for (card in newCards) assertEquals(0, b.player.cardStacks[card.ordinal])
            val copy = BattleSnapshot.decode(BattleSnapshot.encode(a))!!.battle
            repeat(300) { copy.step(); b.step(); copy.fx.count = 0; b.fx.count = 0 }
            assertEquals("v$version", copy.player.baseHp, b.player.baseHp, 0f)
            assertEquals("v$version", copy.wave, b.wave)
        }
        // v21 oznaczony jako v20 ma o 4 stosy kart za dużo, więc nie przechodzi.
        val mislabeled = BattleSnapshot.encode(a).copyOf().also { it[7] = 20 }
        assertNull(BattleSnapshot.decode(mislabeled))
    }

    // ------------------------------------------------------------------ tydzień

    @Test fun twoNewRuleSetsWithNewCards() {
        val sets = ChallengeRuleSets.all.drop(9)
        assertEquals(listOf("week206_blitz", "week206_pikes"), sets.map { it.key })
        assertEquals(EndlessCard.WAR_HORNS, sets[0].weeklyCard)
        assertEquals(EndlessCard.PIKE_SQUARE, sets[1].weeklyCard)
        assertEquals(setOf(Modifier.GLASS_CANNON, Modifier.FAST_UNITS), setOf(sets[0].first, sets[0].second))
        assertEquals(setOf(Modifier.ARMORED_FOE, Modifier.RICH_START), setOf(sets[1].first, sets[1].second))
        // Żadna para nie powtarza wcześniejszego zestawu (w żadnej kolejności).
        val older = ChallengeRuleSets.all.take(9).map { setOf(it.first, it.second) }
        for (set in sets) assertFalse(set.key, setOf(set.first, set.second) in older)
        val first = Weekly.firstRuleSetWeek()
        for ((i, set) in sets.withIndex()) {
            val week = first + (9 + i) * Weekly.RULE_SET_EVERY
            assertEquals(set, Weekly.ruleSet(week))
            val cfg = Weekly.config(week)
            assertEquals(set.weeklyCard, cfg.weeklyCard)
            assertEquals(1, Battle(cfg).player.cardStacks[set.weeklyCard.ordinal])
        }
    }

    @Test fun dailyChallengeNeverUsesNewRuleSets() {
        val newKeys = ChallengeRuleSets.all.drop(9).map { it.key }.toSet()
        for (day in Daily.RELEASE_DAY until Daily.RELEASE_DAY + 730L) {
            val rule = Daily.ruleSet(day) ?: continue
            assertFalse("Dzień $day: ${rule.key}", rule.key in newKeys)
        }
    }

    // ------------------------------------------------------------------ dni

    private fun eventOf(day: Long) = Daily.config(day, Bonuses()).dailyEvent

    /** Niezależny zapis losowania wydarzeń sprzed dnia 58 (pule 6, 9 i 11). */
    private fun archivedEvent(day: Long): DailyEvent? {
        val cfg = Daily.config(day, Bonuses())
        if (day < Daily.RELEASE_DAY || (day - Daily.RELEASE_DAY) % 4L != 3L) return null
        val pool = DailyEvent.entries.take(when {
            day < Daily.EVENTS_V2_DAY -> 6
            day < Daily.EVENTS_V3_DAY -> 9
            else -> 11
        })
        val rng = Rng(day * 131_071L + 907L)
        val first = pool[rng.nextInt(pool.size)]
        if (day < Daily.EVENTS_V2_DAY || !Daily.conflicts(first, cfg.modifiers)) return first
        val ok = pool.filter { !Daily.conflicts(it, cfg.modifiers) }
        return ok[rng.nextInt(ok.size)]
    }

    @Test fun newEventsAreAppendedAtTheEnd() {
        assertEquals(11, DailyEvent.SCHOLARS_DAY.ordinal)
        assertEquals(12, DailyEvent.FURY_DAY.ordinal)
        assertEquals(Daily.RELEASE_DAY + 19L, Daily.EVENTS_V4_DAY)
        // Pierwszy dzień wydarzenia po EVENTS_V3_DAY (cykl co 4 dni od premiery + 3).
        assertEquals(3L, (Daily.EVENTS_V4_DAY - Daily.RELEASE_DAY) % 4L)
        assertEquals(Daily.EVENTS_V3_DAY + 4L, Daily.EVENTS_V4_DAY)
    }

    @Test fun daysBeforeEventsV4KeepTheirEvents() {
        for (day in Daily.RELEASE_DAY - 40 until Daily.EVENTS_V4_DAY) {
            assertEquals("Dzień $day", archivedEvent(day), eventOf(day))
        }
        // Lista z HEAD sprzed zmiany (dni wydarzeń po premierze, D6).
        assertEquals(listOf(DailyEvent.MIRROR_ARMY, DailyEvent.MIRROR_ARMY, DailyEvent.FORTIFIED_BASES),
            listOf(3L, 7L, 11L).map { eventOf(Daily.RELEASE_DAY + it) })
    }

    @Test fun laterDaysDrawNewEventsWithoutConflicts() {
        val seen = HashSet<DailyEvent>()
        var day = Daily.EVENTS_V4_DAY
        while (day < Daily.EVENTS_V4_DAY + 4 * 500) {
            val cfg = Daily.config(day, Bonuses())
            cfg.dailyEvent?.let {
                seen.add(it)
                assertFalse("Dzień $day: $it ${cfg.modifiers}", Daily.conflicts(it, cfg.modifiers))
                if (it == DailyEvent.SCHOLARS_DAY) assertFalse(Modifier.SLOW_XP in cfg.modifiers)
                if (it == DailyEvent.FURY_DAY) assertFalse(Modifier.GLASS_CANNON in cfg.modifiers)
            }
            day++
        }
        assertTrue("wylosowane: $seen", seen.containsAll(newEvents))
        assertTrue("wylosowane: $seen", seen.containsAll(DailyEvent.entries))
    }

    private fun dayWith(event: DailyEvent): Long {
        var day = Daily.EVENTS_V4_DAY
        while (eventOf(day) != event) day++
        return day
    }

    /** Ta sama konfiguracja dnia bez wydarzenia (porównanie mnożników). */
    private fun withoutEvent(cfg: BattleConfig) = BattleConfig(cfg.mode, cfg.seed, cfg.aiPower,
        cfg.startEra, cfg.maxEra, cfg.modifiers, cfg.playerBonuses, aiPersonality = cfg.aiPersonality,
        variantsEnabled = cfg.variantsEnabled, weather = cfg.weather)

    @Test fun scholarsDayRaisesExperienceForBothSides() {
        val cfg = Daily.config(dayWith(DailyEvent.SCHOLARS_DAY), Bonuses())
        val b = Battle(cfg)
        val plain = Battle(withoutEvent(cfg))
        for (i in 0..1) {
            assertEquals(plain.sides[i].xpMul * 1.5f, b.sides[i].xpMul, 1e-4f)
            assertEquals(plain.sides[i].dmgMul, b.sides[i].dmgMul, 0f)
        }
    }

    @Test fun furyDayRaisesUnitDamageForBothSides() {
        val cfg = Daily.config(dayWith(DailyEvent.FURY_DAY), Bonuses())
        val b = Battle(cfg)
        val plain = Battle(withoutEvent(cfg))
        for (i in 0..1) {
            assertEquals(plain.sides[i].dmgMul * 1.2f, b.sides[i].dmgMul, 1e-4f)
            assertEquals(plain.sides[i].xpMul, b.sides[i].xpMul, 0f)
            assertEquals(plain.sides[i].turretDmgMul, b.sides[i].turretDmgMul, 0f)
        }
    }

    @Test fun newEventsSurviveSnapshotAndReplay() {
        for (event in newEvents) {
            val b = Battle(Daily.config(dayWith(event), Bonuses()))
            b.playerAi = Ai(b, 0)
            repeat(60 * 30) { b.step(); b.fx.count = 0 }
            val restored = BattleSnapshot.decode(BattleSnapshot.encode(b, rankedDaily = true))!!
            assertEquals(event, restored.battle.cfg.dailyEvent)
            assertArrayEquals(BattleSnapshot.encode(b, rankedDaily = true),
                BattleSnapshot.encode(restored.battle, rankedDaily = true))
            val log = Replay.Log(10, emptyList(), dailyEvent = event)
            assertEquals(event, Replay.decode(Replay.encode(log)).dailyEvent)
        }
    }

    // ------------------------------------------------------------------ AI

    @Test fun newCardsHavePersonalityFans() {
        val favored = listOf(
            AiPersonality.ASSAULT to EndlessCard.WAR_HORNS,
            AiPersonality.ASSAULT to EndlessCard.VANGUARD,
            AiPersonality.ECONOMIST to EndlessCard.BOSS_BOUNTY,
            AiPersonality.BALANCED to EndlessCard.PIKE_SQUARE,
        )
        for (seed in 1L..20L) for ((personality, card) in favored) {
            val b = endless(seed)
            val offers = arrayOf(EndlessCard.ELITE_SCHOOL, card)
            assertEquals("seed=$seed $personality", card,
                offers[Ai(b, 0, personality).chooseCard(offers)])
        }
    }
}
