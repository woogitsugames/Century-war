package com.centurywar.meta

import com.centurywar.engine.L
import com.centurywar.game.Bonuses
import com.centurywar.game.Daily
import com.centurywar.game.Weekly
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** R12 (pozycja 5): oznaczenia zasad sprzed premiery tylko w wersji testowej i przed premierą. */
class PreReleaseRulesTest {
    private val release = Daily.RELEASE_DAY

    @Test fun activeOnlyInTestBuildBeforeReleaseDay() {
        assertTrue(PreReleaseRules.active(true, release - 1L))
        assertTrue(PreReleaseRules.active(true, release - 400L))
        assertFalse(PreReleaseRules.active(true, release))
        assertFalse(PreReleaseRules.active(true, release + 1L))
        // Produkcja (TESTER_SURVEY = false) nigdy nie pokazuje napisu.
        assertFalse(PreReleaseRules.active(false, release - 1L))
        assertFalse(PreReleaseRules.active(false, release + 10L))
    }

    @Test fun testVersionMatchesTesterSurveyCondition() {
        // Ten sam warunek co testerSurveyRelease w app/build.gradle.kts.
        assertTrue(PreReleaseRules.isTestVersionName("0.1.0"))
        assertTrue(PreReleaseRules.isTestVersionName("0.9.0"))
        assertTrue(PreReleaseRules.isTestVersionName("0.1.0-dev"))
        assertTrue(PreReleaseRules.isTestVersionName("1.0.0-rc1"))
        assertFalse(PreReleaseRules.isTestVersionName("1.0.0"))
        assertFalse(PreReleaseRules.isTestVersionName("1.2.3"))
        assertFalse(PreReleaseRules.isTestVersionName("10.0.0"))
        // Produkcja 1.0.0 w dniu przed premierą: brak napisu przy każdym osiągnięciu.
        val prod = PreReleaseRules.isTestVersionName("1.0.0")
        for (a in Achievements.all) assertFalse(a.id, PreReleaseRules.achievementNote(a, false, prod, release - 1L))
    }

    @Test fun notesOnlyAchievementsOfDailyEventsAndWeeklyRuleSets() {
        val noted = Achievements.all
            .filter { PreReleaseRules.achievementNote(it, false, true, release - 1L) }
            .map { it.id }.toSet()
        assertEquals(setOf("daily_events_6", "daily_events_9", "weekly_rules_3", "weekly_rules_6"), noted)
        assertTrue(noted.contains("weekly_rules_6")) // „Sześć oblicz wojny”
        // Po premierze i po zdobyciu: bez dopisku.
        for (a in Achievements.all) {
            assertFalse(a.id, PreReleaseRules.achievementNote(a, false, true, release))
            assertFalse(a.id, PreReleaseRules.achievementNote(a, true, true, release - 1L))
        }
    }

    @Test fun beforeReleaseThoseMetricsReallyCannotGrow() {
        // Dowód, że oznaczenie mówi prawdę: przed premierą brak wydarzeń dnia i zestawów tygodnia.
        for (day in release - 120L until release) {
            assertNull("dzień $day", Daily.config(day, Bonuses()).dailyEvent)
            assertNull("tydzień dnia $day", Weekly.ruleSet(Weekly.weekNumber(day)))
        }
    }

    @Test fun textsExistInAllLanguages() {
        for (key in listOf("t203_ach_release", "t203_rules_note", "t203_rules_note_short")) {
            val row = L.raw(key)
            assertTrue(key, row != null && row.size == L.CODES.size && row.all { it.isNotBlank() })
        }
    }
}
