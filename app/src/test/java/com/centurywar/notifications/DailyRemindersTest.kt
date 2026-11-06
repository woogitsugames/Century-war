package com.centurywar.notifications

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DailyRemindersTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val alarms get() = shadowOf(context.getSystemService(Context.ALARM_SERVICE) as AlarmManager)
    private val notifications get() = context.getSystemService(NotificationManager::class.java)

    // Znacznik na tym samym executorze czeka na wszystkie wcześniejsze operacje obiektu.
    private fun awaitIo() {
        val field = DailyReminders::class.java.getDeclaredField("io").apply { isAccessible = true }
        (field.get(null) as ExecutorService).submit {}.get(10, TimeUnit.SECONDS)
    }

    private fun profile(enabled: Boolean = true, hour: Int = 18, lang: String = "pl") {
        File(context.filesDir, "profile.json").writeText(
            """{"notifications":$enabled,"notifyHour":$hour,"wins":1,"lang":"$lang"}"""
        )
    }

    @Before fun setup() {
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        context.getSharedPreferences("daily_reminder", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun refreshReplacesAlarmWithSelectedLocalHour() {
        profile(hour = 8)
        DailyReminders.refresh(context)
        awaitIo()
        assertEquals(1, alarms.scheduledAlarms.size)
        profile(hour = 19)
        val before = System.currentTimeMillis()
        DailyReminders.refresh(context)
        awaitIo()
        val after = System.currentTimeMillis()
        val scheduled = alarms.scheduledAlarms
        assertEquals(1, scheduled.size)
        assertEquals(DailyReminderLogic.nextTrigger(before, 19), scheduled.single().triggerAtTime)
        assertEquals(DailyReminderLogic.nextTrigger(after, 19), scheduled.single().triggerAtTime)
    }

    @Test fun disablingOrRevokingPermissionCancelsExistingAlarm() {
        profile()
        DailyReminders.refresh(context)
        awaitIo()
        assertEquals(1, alarms.scheduledAlarms.size)
        profile(enabled = false)
        DailyReminders.refresh(context)
        awaitIo()
        assertTrue(alarms.scheduledAlarms.isEmpty())
        profile()
        DailyReminders.refresh(context)
        awaitIo()
        assertEquals(1, alarms.scheduledAlarms.size)
        shadowOf(context).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        DailyReminders.refresh(context)
        awaitIo()
        assertTrue(alarms.scheduledAlarms.isEmpty())
    }

    @Test fun delayedAlarmDoesNotPostStaleNotification() {
        profile()
        DailyReminders.refresh(context)
        awaitIo()
        DailyReminders.onAlarm(context, System.currentTimeMillis() - DailyReminderLogic.MAX_ALARM_DELAY_MS - 60_000)
        assertTrue(shadowOf(notifications).allNotifications.isEmpty())
        assertEquals(1, alarms.scheduledAlarms.size)
    }

    @Test fun channelUsesGameLanguage() {
        profile(lang = "de")
        DailyReminders.refresh(context)
        awaitIo()
        assertEquals(DailyReminderTexts.channelName("de"), notifications.getNotificationChannel("daily_challenge").name.toString())
        assertEquals(DailyReminderTexts.channelDescription("de"), notifications.getNotificationChannel("daily_challenge").description)
    }

    @Test fun deniedPermissionIsNotRequestedAtNextVictory() {
        shadowOf(context).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        profile()
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        DailyReminders.permissionResult(activity, false)
        awaitIo()
        DailyReminders.afterVictory(activity, DailyReminderProfile(wins = 1))
        awaitIo()
        shadowOf(android.os.Looper.getMainLooper()).idle()
        assertNull(shadowOf(activity).lastRequestedPermission)
        assertTrue(alarms.scheduledAlarms.isEmpty())
    }
}
