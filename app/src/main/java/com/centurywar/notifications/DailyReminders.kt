package com.centurywar.notifications

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.centurywar.MainActivity
import com.centurywar.game.Bonuses
import com.centurywar.game.Daily

/** Jedno nieprecyzyjne przypomnienie dziennie; żadnych usług działających stale w tle. */
object DailyReminders {
    const val PERMISSION_REQUEST_CODE = 1515
    const val ACTION_REMIND = "com.centurywar.game.action.DAILY_REMINDER"
    const val EXTRA_EXPECTED_AT = "expected_at"

    private const val CHANNEL_ID = "daily_challenge"
    private const val PREFS = "daily_reminder"
    private const val LAST_DENIAL = "notification_permission_denied_at"
    private const val LAST_SHOWN_DAY = "last_reminder_local_day"
    private const val ALARM_REQUEST_CODE = 1515
    private const val NOTIFICATION_ID = 1515

    /** Nie pyta o uprawnienie na starcie. Może tylko odtworzyć istniejący harmonogram. */
    fun refresh(context: Context) {
        val profile = DailyReminderProfileReader.read(context)
        createChannel(context, profile?.lang ?: "")
        schedule(context, profile)
    }

    /** Wywołanie dopiero po zapisaniu zwycięstwa lub zmianie ustawień po zwycięstwie. */
    fun afterVictory(activity: Activity) {
        val profile = DailyReminderProfileReader.read(activity)
        if (profile == null || !profile.notifications || profile.wins <= 0) {
            refresh(activity)
            return
        }
        createChannel(activity, profile.lang)
        if (Build.VERSION.SDK_INT >= 33 &&
            activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            val lastDenied = prefs(activity).getLong(LAST_DENIAL, -1L)
            if (DailyReminderLogic.canAskPermission(System.currentTimeMillis(), lastDenied)) {
                activity.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), PERMISSION_REQUEST_CODE)
            }
        } else {
            schedule(activity, profile)
        }
    }

    fun permissionResult(activity: Activity, granted: Boolean) {
        val editor = prefs(activity).edit()
        if (granted) editor.remove(LAST_DENIAL)
        else editor.putLong(LAST_DENIAL, System.currentTimeMillis())
        editor.apply()
        refresh(activity)
    }

    /** Alarm może być dostarczony z opóźnieniem przez Doze; nocą nie wysyłamy zaległych alertów. */
    fun onAlarm(context: Context, expectedAt: Long) {
        val now = System.currentTimeMillis()
        val profile = DailyReminderProfileReader.read(context)
        schedule(context, profile) // Najpierw uzbrój następny dzień, również gdy dzisiaj grano.
        if (profile == null || !canPost(context)) return
        val localDay = DailyReminderLogic.localDay(now)
        val store = prefs(context)
        if (!DailyReminderLogic.shouldShow(
                profile, Daily.dayNumber(now), localDay,
                store.getLong(LAST_SHOWN_DAY, Long.MIN_VALUE), expectedAt, now,
            )
        ) return

        createChannel(context, profile.lang)
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val cfg = Daily.config(Daily.dayNumber(now), Bonuses())
        val text = DailyReminderTexts.body(cfg.modifiers, profile.lang)
        val launch = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val contentIntent = PendingIntent.getActivity(
            context, 0, launch, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(DailyReminderTexts.title(profile.lang))
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(contentIntent)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()
        try {
            manager.notify(NOTIFICATION_ID, notification)
            store.edit().putLong(LAST_SHOWN_DAY, localDay).apply()
        } catch (_: SecurityException) {
            // Użytkownik mógł cofnąć zgodę między sprawdzeniem a publikacją.
        }
    }

    private fun createChannel(context: Context, lang: String) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID, DailyReminderTexts.channelName(lang), NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = DailyReminderTexts.channelDescription(lang)
            enableVibration(false)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun canPost(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        val manager = context.getSystemService(NotificationManager::class.java) ?: return false
        return manager.areNotificationsEnabled() &&
            manager.getNotificationChannel(CHANNEL_ID)?.importance != NotificationManager.IMPORTANCE_NONE
    }

    private fun schedule(context: Context, profile: DailyReminderProfile?) {
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        if (profile == null || !profile.notifications || profile.wins <= 0 || !canPost(context)) {
            cancel(context, alarm)
            return
        }
        val at = DailyReminderLogic.nextTrigger(System.currentTimeMillis(), profile.notifyHour)
        val pending = alarmIntent(context, at, PendingIntent.FLAG_UPDATE_CURRENT) ?: return
        try {
            alarm.cancel(pending)
            alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
        } catch (_: SecurityException) {
            // Bez dostępu do alarmów gra nadal działa, nie żądamy alarmów dokładnych.
        }
    }

    private fun cancel(context: Context, alarm: AlarmManager) {
        val pending = alarmIntent(context, 0L, PendingIntent.FLAG_NO_CREATE) ?: return
        alarm.cancel(pending)
        pending.cancel()
    }

    private fun alarmIntent(context: Context, at: Long, flags: Int): PendingIntent? {
        val intent = Intent(context, DailyReminderReceiver::class.java).apply {
            action = ACTION_REMIND
            putExtra(EXTRA_EXPECTED_AT, at)
        }
        return PendingIntent.getBroadcast(context, ALARM_REQUEST_CODE, intent, flags or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
