package com.centurywar.game

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Zlecenie 184 (dzień 6): cztery karty Endless, trzy zestawy tygodnia, dwa wydarzenia dnia. */
class Content184Test {
    private val newCards = listOf(EndlessCard.SAPPERS, EndlessCard.SHIELD_WALL,
        EndlessCard.WAR_CHEST, EndlessCard.WAGER)
    private val newEvents = setOf(DailyEvent.HEAVY_DAY, DailyEvent.TOWER_DUEL)

    private fun endless(seed: Long = 184L) = Battle(BattleConfig(Mode.ENDLESS, seed, 1f))

    // ------------------------------------------------------------------ karty

    @Test fun newCardsAreAppendedAfterMasterMasons() {
        val last = EndlessCard.MASTER_MASONS.ordinal
        assertEquals(newCards, EndlessCard.entries.drop(last + 1))
        assertEquals(listOf("ec_sappers", "ec_shield_wall", "ec_war_chest", "ec_wager"),
            newCards.map { it.textKey })
        assertTrue(EndlessCards.persistent(EndlessCard.SAPPERS))
        assertTrue(EndlessCards.persistent(EndlessCard.SHIELD_WALL))
        assertTrue(EndlessCards.persistent(EndlessCard.WAR_CHEST))
        assertFalse(EndlessCards.persistent(EndlessCard.WAGER))
        assertTrue(EndlessCard.WAR_CHEST in EndlessCards.economicSynergyCards)
    }

    @Test fun limitsOfNewCards() {
        val b = endless()
        val p = b.player
        for (card in listOf(EndlessCard.SAPPERS, EndlessCard.SHIELD_WALL, EndlessCard.WAR_CHEST)) {
            repeat(EndlessCards.MAX_PERSISTENT_STACKS) {
                assertTrue(card.name, EndlessCards.canOffer(card, p))
                b.applyEndlessCard(p, card)
            }
            assertFalse(card.name, EndlessCards.canOffer(card, p))
        }
        assertTrue(EndlessCards.canOffer(EndlessCard.WAGER, p))
        b.applyEndlessCard(p, EndlessCard.WAGER)
        assertFalse("Zakład tylko raz", EndlessCards.canOffer(EndlessCard.WAGER, p))
        b.applyEndlessCard(p, EndlessCard.WAGER)
        assertEquals(1, p.cardStacks[EndlessCard.WAGER.ordinal])
    }

    /** Obrażenia jednego trafienia bossa przez jednostkę gracza danej roli. */
    private fun bossHit(role: Role, sappers: Int, shieldBoss: Boolean = false): Float {
        val b = endless()
        repeat(sappers) { b.applyEndlessCard(b.player, EndlessCard.SAPPERS) }
        b.wave = if (shieldBoss) 29 else 9
        b.nextBossToSpawn = b.wave + 1
        b.waveTimer = 0f
        b.step()
        b.pendingCards?.let { b.chooseEndlessCard(0) }
        val boss = b.currentBoss()!!
        assertEquals(shieldBoss, boss.hasBossAbility(EndlessBosses.SHIELD))
        assertTrue(b.spawn(b.player, b.player.unit(role)))
        val ally = b.units.last { it.alive && it.side == 0 }
        boss.x = b.player.baseFront + 300f
        boss.cd = 1000f
        ally.x = boss.x - 12f
        ally.cd = 0f
        b.waveTimer = 900f
        val hp = boss.hp
        var steps = 0
        while (boss.hp == hp && steps < 240) { b.step(); steps++ }
        val loss = hp - boss.hp
        assertTrue("Jednostka $role musi trafić bossa", loss > 0f)
        return loss
    }

    @Test fun sappersBoostOnlyHeavyHitsOnBosses() {
        val heavy = bossHit(Role.HEAVY, 0)
        assertEquals(heavy * 1.25f, bossHit(Role.HEAVY, 1), heavy * 0.002f)
        assertEquals(heavy * 1.4f, bossHit(Role.HEAVY, 2), heavy * 0.002f)
        val melee = bossHit(Role.MELEE, 0)
        assertEquals(melee, bossHit(Role.MELEE, 1), 0f)
    }

    /** Utrata HP piechura gracza od pierwszego trafienia wroga danej roli. */
    private fun allyLoss(foeRole: Role, allyRole: Role, walls: Int): Float {
        val b = endless()
        repeat(walls) { b.applyEndlessCard(b.player, EndlessCard.SHIELD_WALL) }
        assertTrue(b.spawn(b.player, b.player.unit(allyRole)))
        assertTrue(b.spawn(b.enemy, b.enemy.unit(foeRole)))
        val ally = b.units.first { it.alive && it.side == 0 }
        val foe = b.units.first { it.alive && it.side == 1 }
        ally.x = Defs.LANE / 2f
        foe.x = ally.x + (if (foeRole == Role.RANGED) 120f else 15f)
        ally.cd = 1000f
        foe.cd = 0f
        b.waveTimer = 900f
        val hp = ally.hp
        var steps = 0
        while (ally.hp == hp && steps < 600) { b.step(); steps++ }
        val loss = hp - ally.hp
        assertTrue("Wróg $foeRole musi trafić", loss > 0f)
        return loss
    }

    @Test fun shieldWallReducesOnlyProjectilesWithFloor() {
        val arrow = allyLoss(Role.RANGED, Role.MELEE, 0)
        assertEquals(arrow * 0.9f, allyLoss(Role.RANGED, Role.MELEE, 1), arrow * 0.002f)
        // 8 stosów: 1 - 0,1 × 2,8 = 0,72, ale dolny limit to 0,75.
        assertEquals(arrow * 0.75f, allyLoss(Role.RANGED, Role.MELEE, 8), arrow * 0.002f)
        val blade = allyLoss(Role.MELEE, Role.MELEE, 0)
        assertEquals(blade, allyLoss(Role.MELEE, Role.MELEE, 1), 0f)
        // Strzelcy gracza nie są osłonięci.
        val archer = allyLoss(Role.RANGED, Role.RANGED, 0)
        assertEquals(archer, allyLoss(Role.RANGED, Role.RANGED, 1), 0f)
    }

    private fun goldAfterWave(chest: Boolean, gold: Float): Float {
        val b = endless()
        if (chest) b.applyEndlessCard(b.player, EndlessCard.WAR_CHEST)
        b.player.gold = gold
        b.waveTimer = 0f
        b.step()
        assertEquals(1, b.wave)
        return b.player.gold
    }

    @Test fun warChestPaysInterestWithCap() {
        val small = goldAfterWave(true, 100f) - goldAfterWave(false, 100f)
        assertEquals(4f, small, 0.05f)
        val cap = EndlessCards.WAR_CHEST_CAP * Defs.costScale(0)
        val big = goldAfterWave(true, 50_000f) - goldAfterWave(false, 50_000f)
        assertEquals(cap, big, 0.5f)
    }

    @Test fun wagerHardensNextWavesAndRaisesWaveScore() {
        val plain = endless()
        val bet = endless()
        bet.applyEndlessCard(bet.player, EndlessCard.WAGER)
        for (b in listOf(plain, bet)) { b.waveTimer = 0f; b.step() }
        assertEquals(plain.enemy.hpMul * 1.1f, bet.enemy.hpMul, 1e-4f)
        assertEquals(plain.wave, bet.wave)
        val kills = bet.player.kills * 12L + (bet.time / 2f).toLong() + bet.bossesDefeated * 1500L
        assertEquals(bet.wave * 312L + kills, bet.score())
        assertEquals(bet.wave * 250L, plain.score() - (plain.player.kills * 12L +
            (plain.time / 2f).toLong() + plain.bossesDefeated * 1500L))
        // Premia ×1,25 (312/250 = 1,248).
        assertEquals(1.25, EndlessCards.WAGER_WAVE_SCORE / 250.0, 0.003)
    }

    @Test fun thousandOffersInVariousStatesNeverEmpty() {
        val rng = Rng(18_400L)
        val picks = Array(3) { EndlessCard.ECONOMY }
        val seen = HashSet<EndlessCard>()
        for (state in 0 until 10) {
            val b = endless(184L + state)
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
        val a = endless(1_840L)
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
        val a = endless(1_841L)
        a.playerAi = Ai(a, 0)
        repeat(60 * 60) { a.step(); a.fx.count = 0 }
        for (version in listOf(16, 17, 18, 19)) {
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
        // v20 oznaczony jako v19 ma o 4 stosy kart za dużo, więc nie przechodzi.
        val mislabeled = BattleSnapshot.encode(a).copyOf().also { it[7] = 19 }
        assertNull(BattleSnapshot.decode(mislabeled))
    }

    // ------------------------------------------------------------------ tydzień

    @Test fun nineRuleSetsRotateEveryTwentySevenWeeks() {
        assertEquals(9, ChallengeRuleSets.COUNT)
        assertEquals(9, ChallengeRuleSets.all.size)
        assertEquals(listOf("week184_night", "week184_mercs", "week184_bastion"),
            ChallengeRuleSets.all.drop(6).map { it.key })
        val first = Weekly.firstRuleSetWeek()
        val period = ChallengeRuleSets.COUNT * Weekly.RULE_SET_EVERY
        assertEquals(27L, period)
        for (i in 0 until ChallengeRuleSets.COUNT) {
            val week = first + i * Weekly.RULE_SET_EVERY
            val rule = Weekly.ruleSet(week)!!
            assertEquals(ChallengeRuleSets.all[i], rule)
            assertEquals(rule, Weekly.ruleSet(week + period))
            assertEquals(rule, Weekly.ruleSet(week + 3 * period))
            val cfg = Weekly.config(week)
            assertEquals(setOf(rule.first, rule.second), cfg.modifiers)
            // Karta tygodnia jest ważna na starcie (canOffer ją przyjmuje).
            assertEquals(rule.key, 1, Battle(cfg).player.cardStacks[cfg.weeklyCard!!.ordinal])
        }
    }

    @Test fun nightWatchCardFallsBackWithoutTurretSlotsAndFog() {
        val night = ChallengeRuleSets.all.first { it.key == "week184_night" }
        assertEquals(EndlessCard.VIGILANCE, night.weeklyCard)
        assertEquals(EndlessCard.VIGILANCE, Weekly.startCard(EndlessCard.VIGILANCE,
            setOf(Modifier.FOG_OF_WAR, Modifier.ARMORED_FOE)))
        assertEquals(EndlessCard.VIGILANCE, Weekly.startCard(EndlessCard.VIGILANCE,
            setOf(Modifier.FOG_OF_WAR, Modifier.NO_TURRETS)))
        assertEquals(EndlessCard.FORTRESS, Weekly.startCard(EndlessCard.VIGILANCE,
            setOf(Modifier.NO_TURRETS, Modifier.ARMORED_FOE)))
        assertEquals(EndlessCard.PLUNDER, Weekly.startCard(EndlessCard.PLUNDER,
            setOf(Modifier.NO_TURRETS)))
        // Bez slotów i bez mgły Czujność nie przeszłaby canOffer, Twierdza tak.
        val b = Battle(BattleConfig(Mode.ENDLESS, 5L, 1f))
        b.player.slots = 0
        assertFalse(EndlessCards.canOffer(EndlessCard.VIGILANCE, b.player, fog = false))
        assertTrue(EndlessCards.canOffer(EndlessCard.FORTRESS, b.player, fog = false))
    }

    @Test fun randomWeeksNeverCopyNewRuleSetPairs() {
        val first = Weekly.firstRuleSetWeek()
        val newPairs = ChallengeRuleSets.all.drop(6).map { setOf(it.first, it.second) }
        for (week in first until first + 540L) {
            if (Weekly.ruleSet(week) != null) continue
            assertFalse("Tydzień $week", Weekly.config(week).modifiers in newPairs)
        }
    }

    @Test fun dailyRuleSetsStayTheOriginalSix() {
        val old = ChallengeRuleSets.all.take(6).map { it.key }.toSet()
        for (day in Daily.RELEASE_DAY until Daily.RELEASE_DAY + 730L) {
            val rule = Daily.ruleSet(day) ?: continue
            assertTrue("Dzień $day: ${rule.key}", rule.key in old)
        }
    }

    // ------------------------------------------------------------------ dni

    private fun eventOf(day: Long) = Daily.config(day, Bonuses()).dailyEvent

    /** Niezależny zapis losowania wydarzeń sprzed zlecenia 184. */
    private fun archivedEvent(day: Long): DailyEvent? {
        val cfg = Daily.config(day, Bonuses())
        if (day < Daily.RELEASE_DAY || (day - Daily.RELEASE_DAY) % 4L != 3L) return null
        val pool = DailyEvent.entries.take(if (day < Daily.EVENTS_V2_DAY) 6 else 9)
        val rng = Rng(day * 131_071L + 907L)
        val first = pool[rng.nextInt(pool.size)]
        if (day < Daily.EVENTS_V2_DAY || !Daily.conflicts(first, cfg.modifiers)) return first
        val ok = pool.filter { !Daily.conflicts(it, cfg.modifiers) }
        return ok[rng.nextInt(ok.size)]
    }

    @Test fun newEventsAreAppendedAtTheEnd() {
        assertEquals(9, DailyEvent.HEAVY_DAY.ordinal)
        assertEquals(10, DailyEvent.TOWER_DUEL.ordinal)
        assertEquals(Daily.RELEASE_DAY + 15L, Daily.EVENTS_V3_DAY)
    }

    @Test fun daysBeforeEventsV3KeepTheirEvents() {
        for (day in Daily.RELEASE_DAY - 40 until Daily.EVENTS_V3_DAY) {
            assertEquals("Dzień $day", archivedEvent(day), eventOf(day))
        }
        // Lista z HEAD sprzed zmiany (dni wydarzeń po premierze).
        assertEquals(listOf(DailyEvent.MIRROR_ARMY, DailyEvent.MIRROR_ARMY, DailyEvent.FORTIFIED_BASES),
            listOf(3L, 7L, 11L).map { eventOf(Daily.RELEASE_DAY + it) })
    }

    @Test fun laterDaysDrawNewEventsWithoutConflicts() {
        val seen = HashSet<DailyEvent>()
        var day = Daily.EVENTS_V3_DAY
        while (day < Daily.EVENTS_V3_DAY + 4 * 500) {
            val cfg = Daily.config(day, Bonuses())
            cfg.dailyEvent?.let {
                seen.add(it)
                assertFalse("Dzień $day: $it ${cfg.modifiers}", Daily.conflicts(it, cfg.modifiers))
                if (it == DailyEvent.TOWER_DUEL) {
                    assertFalse(Modifier.NO_TURRETS in cfg.modifiers)
                    assertFalse(Modifier.EXPENSIVE_TOWERS in cfg.modifiers)
                }
            }
            day++
        }
        assertTrue("wylosowane: $seen", seen.containsAll(newEvents))
    }

    private fun battleWith(event: DailyEvent): Battle {
        var day = Daily.EVENTS_V3_DAY
        while (eventOf(day) != event) day++
        return Battle(Daily.config(day, Bonuses()))
    }

    @Test fun heavyDayDiscountsHeavyUnitsForBothSides() {
        val b = battleWith(DailyEvent.HEAVY_DAY)
        for (s in b.sides) {
            val heavy = s.unit(Role.HEAVY).cost
            assertEquals(heavy * 4 / 5, b.unitCost(s, Role.HEAVY))
            assertEquals(s.unit(Role.MELEE).cost, b.unitCost(s, Role.MELEE))
        }
        val p = b.player
        p.gold = 10_000f
        assertTrue(b.queueUnit(p, Role.HEAVY))
        assertEquals(10_000f - p.unit(Role.HEAVY).cost * 4 / 5, p.gold, 0.01f)
        assertTrue(b.cancelLast(p))
        assertEquals(10_000f, p.gold, 0.01f)
    }

    @Test fun towerDuelAddsOneSlotForBothSides() {
        val b = battleWith(DailyEvent.TOWER_DUEL)
        assertEquals(Daily.normalizedBonuses().turretSlots + 1, b.player.slots)
        assertEquals(3, b.enemy.slots)
        assertFalse(Modifier.NO_TURRETS in b.cfg.modifiers)
    }

    @Test fun newEventsSurviveSnapshotAndReplay() {
        for (event in newEvents) {
            val b = battleWith(event)
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
        val favored = mapOf(
            AiPersonality.SIEGER to EndlessCard.SAPPERS,
            AiPersonality.ECONOMIST to EndlessCard.WAR_CHEST,
            AiPersonality.ENGINEER to EndlessCard.SHIELD_WALL,
        )
        for (seed in 1L..20L) for ((personality, card) in favored) {
            val b = endless(seed)
            val foil = if (personality == AiPersonality.SIEGER) EndlessCard.LAST_STAND
                else EndlessCard.ELITE_SCHOOL
            val offers = arrayOf(foil, card)
            assertEquals("seed=$seed $personality", card,
                offers[Ai(b, 0, personality).chooseCard(offers)])
        }
        // Zakład: poza profilem szturmowym AI wybiera zwykłą kartę.
        for (seed in 1L..20L) for (personality in AiPersonality.entries) {
            if (personality == AiPersonality.ASSAULT) continue
            val b = endless(seed)
            val offers = arrayOf(EndlessCard.WAGER, EndlessCard.ELITE_SCHOOL)
            assertEquals("seed=$seed $personality", EndlessCard.ELITE_SCHOOL,
                offers[Ai(b, 0, personality).chooseCard(offers)])
        }
    }
}
