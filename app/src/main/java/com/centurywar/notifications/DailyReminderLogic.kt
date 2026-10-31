package com.centurywar.notifications

import java.time.Instant
import java.time.ZoneId

/** Dane wystarczające do decyzji o alarmie, bez wczytywania całego profilu gry. */
internal data class DailyReminderProfile(
    val notifications: Boolean = true,
    val notifyHour: Int = 18,
    val wins: Int = 0,
    val lastRankedDaily: Long = -1L,
    val lang: String = "",
)

/** Reguły niezależne od Androida; alarm działa według lokalnej godziny, wyzwanie według UTC. */
internal object DailyReminderLogic {
    const val DENIAL_COOLDOWN_MS = 14L * 24 * 60 * 60 * 1000
    const val MAX_ALARM_DELAY_MS = 4L * 60 * 60 * 1000

    fun nextTrigger(nowMillis: Long, hour: Int, zone: ZoneId = ZoneId.systemDefault()): Long {
        val now = Instant.ofEpochMilli(nowMillis).atZone(zone)
        val h = hour.coerceIn(0, 23)
        var next = now.toLocalDate().atTime(h, 0).atZone(zone)
        if (!next.isAfter(now)) next = now.toLocalDate().plusDays(1).atTime(h, 0).atZone(zone)
        return next.toInstant().toEpochMilli()
    }

    fun localDay(nowMillis: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
        Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate().toEpochDay()

    fun canAskPermission(nowMillis: Long, lastDeniedMillis: Long): Boolean =
        lastDeniedMillis <= 0L || nowMillis - lastDeniedMillis >= DENIAL_COOLDOWN_MS

    fun shouldShow(
        profile: DailyReminderProfile,
        utcDay: Long,
        localDay: Long,
        previouslyShownLocalDay: Long,
        expectedAlarmMillis: Long,
        nowMillis: Long,
    ): Boolean = profile.notifications && profile.wins > 0 &&
        profile.lastRankedDaily != utcDay && previouslyShownLocalDay != localDay &&
        expectedAlarmMillis > 0 && nowMillis >= expectedAlarmMillis &&
        nowMillis - expectedAlarmMillis <= MAX_ALARM_DELAY_MS
}
