package com.centurywar.crash

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import org.robolectric.RuntimeEnvironment
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowActivityManager.ApplicationExitInfoBuilder
import java.io.File

/** R2-05: przyczyny zakończenia procesu z ApplicationExitInfo trafiają do lokalnego dziennika. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ExitReasonsTest {
    private lateinit var context: Context
    private lateinit var dir: File
    private lateinit var store: CrashStore

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        dir = File(context.cacheDir, "exit-test").apply { deleteRecursively(); mkdirs() }
        store = CrashStore(dir)
    }

    @After
    fun tearDown() { dir.deleteRecursively() }

    private fun prefs() = context.getSharedPreferences("crash_report_offer", Context.MODE_PRIVATE)

    private fun add(reason: Int, ts: Long, importance: Int = ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND,
                    trace: String? = null) {
        val am = context.getSystemService(ActivityManager::class.java)
        val b = ApplicationExitInfoBuilder.newBuilder()
            .setProcessName(context.packageName)
            .setReason(reason)
            .setImportance(importance)
            .setTimestamp(ts)
            .setPss(123_456L)
            .setRss(234_567L)
            .setDescription("opis testowy")
        if (trace != null) b.setTraceInputStream(trace.byteInputStream())
        shadowOf(am).addApplicationExitInfo(b.build())
    }

    private val anrTrace = buildString {
        append("----- pid 123 at 2026-11-03 -----\nCmd line: com.centurywar.game\n\n")
        append("\"main\" prio=5 tid=1 Blocked\n  at com.centurywar.MainActivity.onPause(MainActivity.kt:390)\n  at android.app.Activity.performPause(Activity.java:1)\n\n")
        append("\"OkHttp Dispatcher\" prio=5 tid=20 Waiting\n  at java.lang.Object.wait(Native method)\n  at okhttp3.Secret.path(Secret.kt:1)\n\n")
        append("\"game-loop\" prio=10 tid=17 Runnable\n  at com.centurywar.engine.Game.frame(Game.kt:600)\n\n")
        append("\"Binder:123_1\" prio=5 tid=3 Native\n  at android.os.Binder.x(Binder.java:1)\n\n")
    }

    @Test
    fun anrIsSavedAsFatalWithMainAndGameThreadTrace() {
        add(ApplicationExitInfo.REASON_ANR, 5_000L, trace = anrTrace)
        assertEquals(1, ExitReasons.collect(context, store, prefs()))
        val file = store.newest(fatalOnly = true)
        assertNotNull("ANR zapisany jako fatal (pytanie o raport przy starcie)", file)
        val text = store.read(file!!.name)!!
        assertTrue(text.contains("Przyczyna: ANR"))
        assertTrue(text.contains("PSS: 123456 kB"))
        assertTrue(text.contains("\"main\" prio=5"))
        assertTrue(text.contains("MainActivity.onPause"))
        assertTrue(text.contains("\"game-loop\""))
        assertTrue(text.contains("Game.frame"))
        // Wątki innych bibliotek pomijamy.
        assertFalse(text.contains("OkHttp"))
        assertFalse(text.contains("Binder:123_1"))
        assertEquals(5_000L, prefs().getLong(ExitReasons.LAST_EXIT_TS, 0L))
    }

    @Test
    fun userRequestedExitSelfAndJavaCrashAreSkipped() {
        add(ApplicationExitInfo.REASON_USER_REQUESTED, 1_000L)
        add(ApplicationExitInfo.REASON_EXIT_SELF, 2_000L)
        add(ApplicationExitInfo.REASON_CRASH, 3_000L)
        add(ApplicationExitInfo.REASON_SIGNALED, 4_000L,
            importance = ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED)
        assertEquals(0, ExitReasons.collect(context, store, prefs()))
        assertNull(store.newest())
        // Znacznik idzie naprzód także dla pominiętych wpisów.
        assertEquals(4_000L, prefs().getLong(ExitReasons.LAST_EXIT_TS, 0L))
    }

    @Test
    fun lowMemoryIsSavedButDoesNotTriggerCrashPrompt() {
        add(ApplicationExitInfo.REASON_LOW_MEMORY, 7_000L,
            importance = ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED)
        add(ApplicationExitInfo.REASON_SIGNALED, 8_000L)
        assertEquals(2, ExitReasons.collect(context, store, prefs()))
        assertNull(store.newest(fatalOnly = true))
        assertNotNull(store.newest())
    }

    @Test
    fun secondStartDoesNotDuplicateReports() {
        add(ApplicationExitInfo.REASON_CRASH_NATIVE, 9_000L)
        assertEquals(1, ExitReasons.collect(context, store, prefs()))
        assertEquals(0, ExitReasons.collect(context, store, prefs()))
        assertEquals(1, dir.listFiles()!!.size)
        add(ApplicationExitInfo.REASON_ANR, 10_000L, trace = anrTrace)
        assertEquals(1, ExitReasons.collect(context, store, prefs()))
        assertEquals(2, dir.listFiles()!!.size)
    }

    @Test
    fun anrTraceIsCappedAndMissingTraceIsTolerated() {
        val long = "\"main\" prio=5 tid=1 Blocked\n" + "  at a.b.C.d(C.kt:1)\n".repeat(2_000)
        assertEquals(ExitReasons.TRACE_CHARS, ExitReasons.anrThreads(long.byteInputStream()).length)
        add(ApplicationExitInfo.REASON_ANR, 11_000L)
        assertEquals(1, ExitReasons.collect(context, store, prefs()))
        assertTrue(store.read(store.newest()!!.name)!!.contains("Brak śladu."))
    }

    @Test
    @Config(sdk = [29])
    fun api29DoesNothing() {
        assertEquals(0, ExitReasons.collect(context, store, prefs()))
        assertEquals(0L, prefs().getLong(ExitReasons.LAST_EXIT_TS, 0L))
        assertNull(store.newest())
    }
}
