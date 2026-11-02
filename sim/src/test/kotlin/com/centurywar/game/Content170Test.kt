package com.centurywar.game

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Zlecenie 170: nowe wydarzenia dnia i karty Endless. */
class Content170Test {
    private val newEvents = setOf(DailyEvent.SIEGE_WORKS, DailyEvent.FORCED_MARCH, DailyEvent.SPOILS_OF_WAR)

    private fun eventOf(day: Long) = Daily.config(day, Bonuses()).dailyEvent

    @Test fun newEventsAreAppendedAtTheEnd() {
        assertEquals(6, DailyEvent.SIEGE_WORKS.ordinal)
        assertEquals(7, DailyEvent.FORCED_MARCH.ordinal)
        assertEquals(8, DailyEvent.SPOILS_OF_WAR.ordinal)
    }

    @Test fun daysBeforeEventsV2KeepTheFrozenPool() {
        // Pierwsze dwa cykle (w tym przypięta powtórka RELEASE_DAY + 3) bez nowych dni.
        for (day in Daily.RELEASE_DAY - 40 until Daily.EVENTS_V2_DAY) {
            val event = eventOf(day) ?: continue
            assertFalse("dzień $day", event in newEvents)
        }
        // Ten sam algorytm co przed zleceniem 170 dla dnia RELEASE_DAY + 3.
        val pinned = Daily.RELEASE_DAY + 3
        val legacy = DailyEvent.entries.take(6)[Rng(pinned * 131_071L + 907L).nextInt(6)]
        assertEquals(legacy, eventOf(pinned))
    }

    @Test fun laterDaysDrawEveryNewEvent() {
        val seen = HashSet<DailyEvent>()
        var day = Daily.EVENTS_V2_DAY
        while (day < Daily.EVENTS_V2_DAY + 4 * 400) {
            eventOf(day)?.let { seen.add(it) }
            day++
        }
        assertTrue("wylosowane: $seen", seen.containsAll(newEvents))
        assertEquals(DailyEvent.entries.toSet(), seen)
    }

    private fun battleWith(event: DailyEvent): Battle {
        var day = Daily.EVENTS_V2_DAY
        while (eventOf(day) != event) day++
        return Battle(Daily.config(day, Bonuses()))
    }

    @Test fun newEventsAffectBothSidesSymmetrically() {
        val works = battleWith(DailyEvent.SIEGE_WORKS)
        for (s in works.sides) {
            assertEquals(0.6f, s.turretCostMul, 1e-4f)
            assertEquals(1.25f, s.turretRateMul, 1e-4f)
        }
        val march = battleWith(DailyEvent.FORCED_MARCH)
        for (s in march.sides) assertEquals(1.25f, s.speedMul, 1e-4f)
        assertEquals(march.player.trainMul / Daily.normalizedBonuses().trainMul, 1.2f, 1e-4f)
        val spoils = battleWith(DailyEvent.SPOILS_OF_WAR)
        for (s in spoils.sides) assertEquals(1.75f, s.killGoldMul, 1e-4f)
    }

    @Test fun newEventSurvivesSnapshotAndReplay() {
        for (event in newEvents) {
            val b = battleWith(event)
            b.playerAi = Ai(b, 0)
            repeat(60 * 30) { b.step(); b.fx.count = 0 }
            val restored = BattleSnapshot.decode(BattleSnapshot.encode(b, rankedDaily = true))
            assertNotNull(restored)
            assertEquals(event, restored!!.battle.cfg.dailyEvent)
            assertArrayEquals(BattleSnapshot.encode(b, rankedDaily = true),
                BattleSnapshot.encode(restored.battle, rankedDaily = true))
            val log = Replay.Log(10, emptyList(), dailyEvent = event)
            assertEquals(event, Replay.decode(Replay.encode(log)).dailyEvent)
        }
    }

    @Test fun deterministicForSameDay() {
        val a = battleWith(DailyEvent.FORCED_MARCH).also { it.playerAi = Ai(it, 0) }
        val b = Battle(a.cfg).also { it.playerAi = Ai(it, 0) }
        repeat(60 * 60) { a.step(); b.step(); a.fx.count = 0; b.fx.count = 0 }
        assertArrayEquals(BattleSnapshot.encode(a), BattleSnapshot.encode(b))
    }

    @Test fun newCardsApplyTheirEffects() {
        val b = Battle(BattleConfig(Mode.ENDLESS, 5L, 1f))
        val p = b.player
        val xp = p.xpMul
        b.applyEndlessCard(p, EndlessCard.QUARTERMASTER)
        assertEquals(xp * 1.2f, p.xpMul, 1e-4f)
        assertEquals(1, p.cardStacks[EndlessCard.QUARTERMASTER.ordinal])
        val cost = p.turretCost()
        b.applyEndlessCard(p, EndlessCard.MASTER_MASONS)
        assertTrue("wieża tańsza: $cost -> ${p.turretCost()}", p.turretCost() < cost)
        assertTrue(EndlessCards.persistent(EndlessCard.QUARTERMASTER))
        assertTrue(EndlessCards.persistent(EndlessCard.MASTER_MASONS))
    }

    @Test fun masonsNeedTurretSlots() {
        val b = Battle(BattleConfig(Mode.ENDLESS, 6L, 1f))
        b.player.slots = 0
        assertFalse(EndlessCards.canOffer(EndlessCard.MASTER_MASONS, b.player))
        assertTrue(EndlessCards.canOffer(EndlessCard.QUARTERMASTER, b.player))
    }

    @Test fun newCardsAreDrawable() {
        val b = Battle(BattleConfig(Mode.ENDLESS, 9L, 1f))
        val rng = Rng(99L)
        val picks = Array(3) { EndlessCard.ECONOMY }
        val seen = HashSet<EndlessCard>()
        repeat(2000) { EndlessCards.draw(rng, b.player, picks); seen.addAll(picks) }
        assertTrue(EndlessCard.QUARTERMASTER in seen)
        assertTrue(EndlessCard.MASTER_MASONS in seen)
    }
}
