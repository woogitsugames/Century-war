package com.centurywar.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Odbiornik alarmu, restartu telefonu i zmian zegara/strefy. Nie uruchamia gry. */
class DailyReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            DailyReminders.ACTION_REMIND -> DailyReminders.onAlarm(
                context, intent.getLongExtra(DailyReminders.EXTRA_EXPECTED_AT, -1L),
            )
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> DailyReminders.refresh(context)
        }
    }
}
