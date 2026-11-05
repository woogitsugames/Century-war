package com.centurywar.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Noc 4: wydarzenie dnia nie może przeczyć modyfikatorom dnia (od EVENTS_V2_DAY). */
class DailyEventConflictsTest {
    private fun fingerprint(day: Long): String {
        val c = Daily.config(day, Bonuses())
        return listOf(day, c.seed, c.aiPower.toBits(), c.startEra, c.maxEra,
            c.modifiers.map { it.name }.sorted(), c.aiPersonality, c.variantsEnabled,
            c.weather, c.dailyEvent).joinToString("|")
    }

    private fun hash(text: String): Long {
        var h = -0x340d631b7bdddcdbL
        for (ch in text) { h = h xor ch.code.toLong(); h *= 0x100000001b3L }
        return h
    }

    @Test fun pastDaysKeepTheirConfiguration() {
        // Odcisk dni RELEASE_DAY-30 .. EVENTS_V2_DAY-1 policzony przed poprawką nocy 4
        // (w tym przypięta powtórka RELEASE_DAY + 3 i stare dni złotych powtórek).
        val text = (Daily.RELEASE_DAY - 30 until Daily.EVENTS_V2_DAY)
            .joinToString("\n") { fingerprint(it) }
        assertEquals(PAST_HASH, hash(text))
    }

    @Test fun noConflictsForThreeYears() {
        val clashes = ArrayList<String>()
        for (day in Daily.EVENTS_V2_DAY until Daily.EVENTS_V2_DAY + 365 * 3) {
            val c = Daily.config(day, Bonuses())
            val e = c.dailyEvent ?: continue
            val hit = Daily.EVENT_CONFLICTS[e].orEmpty().filter { it in c.modifiers }
            if (hit.isNotEmpty()) clashes.add("dzień $day: $e z $hit")
        }
        assertTrue("Kolizje:\n" + clashes.joinToString("\n"), clashes.isEmpty())
    }

    @Test fun onlyClashingDaysChange() {
        // Dzień bez kolizji zachowuje pierwsze losowanie; zmienione dni to tylko dawne kolizje.
        var changed = 0
        for (day in Daily.EVENTS_V2_DAY until Daily.EVENTS_V2_DAY + 365 * 3) {
            val c = Daily.config(day, Bonuses())
            val e = c.dailyEvent ?: continue
            // Zlecenie 184: do EVENTS_V3_DAY pula to zamrożona dziewiątka.
            // Dzień 58: do EVENTS_V4_DAY pula to zamrożona jedenastka.
            val pool = when {
                day < Daily.EVENTS_V3_DAY -> DailyEvent.entries.take(9)
                day < Daily.EVENTS_V4_DAY -> DailyEvent.entries.take(11)
                else -> DailyEvent.entries
            }
            val first = pool[Rng(day * 131_071L + 907L).nextInt(pool.size)]
            if (first != e) {
                assertTrue("dzień $day", Daily.conflicts(first, c.modifiers))
                changed++
            } else {
                assertTrue("dzień $day", !Daily.conflicts(first, c.modifiers))
            }
        }
        assertTrue(changed > 0)
    }

    private companion object {
        const val PAST_HASH = -9173915592681953882L
    }
}
