package com.centurywar.notifications

import com.centurywar.game.Modifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

class DailyReminderLogicTest {
    private val brussels = ZoneId.of("Europe/Brussels")

    @Test fun nextAlarmUsesLocalHourAndTomorrowAfterDeadline() {
        val morning = ZonedDateTime.of(2026, 10, 31, 12, 0, 0, 0, brussels).toInstant().toEpochMilli()
        val evening = ZonedDateTime.of(2026, 10, 31, 18, 0, 0, 0, brussels).toInstant().toEpochMilli()
        val tomorrow = ZonedDateTime.of(2026, 11, 1, 18, 0, 0, 0, brussels).toInstant().toEpochMilli()
        assertEquals(evening, DailyReminderLogic.nextTrigger(morning, 18, brussels))
        assertEquals(tomorrow, DailyReminderLogic.nextTrigger(evening, 18, brussels))
    }

    @Test fun nextAlarmKeepsLocalHourThroughDstChanges() {
        val zones = listOf(
            ZonedDateTime.of(2026, 3, 28, 19, 0, 0, 0, brussels),
            ZonedDateTime.of(2026, 10, 24, 19, 0, 0, 0, brussels),
        )
        for (time in zones) {
            val next = DailyReminderLogic.nextTrigger(time.toInstant().toEpochMilli(), 18, brussels)
            val local = Instant.ofEpochMilli(next).atZone(brussels)
            assertEquals(18, local.hour)
            assertEquals(time.toLocalDate().plusDays(1), local.toLocalDate())
        }
    }

    @Test fun permissionDenialHasFourteenDayCooldown() {
        val denied = 1_700_000_000_000L
        assertFalse(DailyReminderLogic.canAskPermission(denied + 13L * 86_400_000, denied))
        assertTrue(DailyReminderLogic.canAskPermission(denied + 14L * 86_400_000, denied))
        assertTrue(DailyReminderLogic.canAskPermission(denied, -1))
    }

    @Test fun noReminderAfterRankedAttemptOrDuplicate() {
        val hour = 1_700_000_000_000L
        val utcDay = 20000L
        val localDay = 19999L
        val profile = DailyReminderProfile(wins = 1)
        fun allowed(p: DailyReminderProfile = profile, lastSent: Long = -1, now: Long = hour): Boolean =
            DailyReminderLogic.shouldShow(p, utcDay, localDay, lastSent, hour, now)
        assertTrue(allowed())
        assertFalse(allowed(profile.copy(lastRankedDaily = utcDay)))
        assertFalse(allowed(profile.copy(wins = 0)))
        assertFalse(allowed(profile.copy(notifications = false)))
        assertFalse(allowed(lastSent = localDay))
        assertFalse(allowed(now = hour + DailyReminderLogic.MAX_ALARM_DELAY_MS + 1))
        assertFalse(allowed(now = hour - 1))
    }

    @Test fun notificationBodyIsLocalizedAndOrdered() {
        val mods = setOf(Modifier.FOG_OF_WAR, Modifier.FRAGILE_BASES)
        assertEquals(
            "Nowe wyzwanie dnia czeka! Modyfikatory: Kruche bazy, Mgła wojny",
            DailyReminderTexts.body(mods, "pl"),
        )
        assertEquals(
            "A new daily challenge awaits! Modifiers: Fragile bases, Fog of war",
            DailyReminderTexts.body(mods, "en"),
        )
    }
}
