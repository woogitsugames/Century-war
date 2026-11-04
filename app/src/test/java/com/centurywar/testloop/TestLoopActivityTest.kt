package com.centurywar.testloop

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Looper
import com.centurywar.MainActivity
import com.centurywar.meta.OnlineLeaderboards
import com.centurywar.meta.Profile
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

/**
 * R11-04: pętla gry przez intencję TEST_LOOP (Robolectric nie tworzy powierzchni, więc klatki
 * podaje test). Izolacja profilu gracza, plik wyników, złośliwe intencje i zwykły start.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@LooperMode(LooperMode.Mode.PAUSED)
class TestLoopActivityTest {
    @get:Rule val folder = TemporaryFolder()
    private val app get() = RuntimeEnvironment.getApplication()

    @Before fun shortTimes() {
        // Bitwa 1,8 s zamiast 3 min; limit z zapasem na wolny komputer CI.
        TestLoopActivity.timingOverride = GameLoopTiming(scale = 0.01f, limitS = 120f)
        TestLoopActivity.loopDir(app).deleteRecursively()
    }

    @After fun restore() { TestLoopActivity.timingOverride = null }

    private fun intent(scenario: Int?, data: Uri?): Intent =
        Intent(TestLoopIntent.ACTION).setClassName(app, TestLoopActivity::class.java.name).apply {
            if (data != null) setDataAndType(data, "application/javascript") else type = "application/javascript"
            if (scenario != null) putExtra(TestLoopIntent.EXTRA_SCENARIO, scenario)
        }

    /** Uruchamia pętlę i podaje klatki, aż Activity sama się zamknie. */
    private fun runLoop(intent: Intent, check: (TestLoopActivity) -> Unit = {}): TestLoopActivity {
        val controller = Robolectric.buildActivity(TestLoopActivity::class.java, intent).setup()
        val activity = controller.get()
        val canvas = Canvas(Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888))
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(150)
        var resized = false
        var checked = false
        while (!activity.isFinishing && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            if (activity.gameReady && !activity.runnerFinished) {
                if (!resized) {
                    activity.game.density = 2f
                    activity.game.resize(1600, 720, 1600, 720)
                    activity.game.finishScreenTransition()
                    resized = true
                }
                if (!checked) { check(activity); checked = true }
                activity.game.frame(canvas, 1f / 60f)
            } else Thread.sleep(2)
        }
        assertTrue("Pętla nie wywołała finish()", activity.isFinishing)
        controller.pause().stop().destroy()
        shadowOf(Looper.getMainLooper()).idle()
        return activity
    }

    private fun digest(dir: File): Map<String, String> {
        val md = MessageDigest.getInstance("SHA-256")
        return dir.walkTopDown().filter { it.isFile }.associate { f ->
            f.relativeTo(dir).path to md.digest(f.readBytes()).joinToString("") { "%02x".format(it) }
        }
    }

    @Test fun scenarioWritesJsonResultAndFinishes() {
        val out = folder.newFile("results.json")
        runLoop(intent(1, Uri.fromFile(out)))
        val o = JSONObject(out.readText())
        assertEquals(1, o.getInt("v"))
        assertEquals(1, o.getInt("scenario"))
        assertEquals("ok", o.getString("result"))
        assertTrue(o.has("startupMs"))
        assertTrue(o.getJSONObject("frames").getInt("count") > 0)
        assertEquals(o.toString(), TestLoopActivity.lastResult)
    }

    @Test fun missingScenarioMeansFirstAndMissingUriLogsOnly() {
        runLoop(intent(null, null))
        val o = JSONObject(TestLoopActivity.lastResult!!)
        assertEquals(1, o.getInt("scenario"))
        assertEquals("ok", o.getString("result"))
    }

    @Test fun playerFilesStayByteForByteUnchanged() {
        val files = app.filesDir
        val profileFile = File(files, "profile.json")
        Profile(profileFile).apply { medals = 500; tutorialDone = true; save(); awaitWrites(5_000L) }
        File(files, "battle.snap").writeBytes(ByteArray(64) { it.toByte() })
        val before = digest(files)
        val savedProfile = profileFile.readBytes()
        val out = folder.newFile("results4.json")
        // Bitwa z przerwaniem (zrzut) i bitwa AI: najwięcej zapisów.
        runLoop(intent(4, Uri.fromFile(out))) { activity ->
            assertTrue(activity.game.testLoop)
            assertNull("Bez wysyłki wyników", activity.game.scores)
            assertSame(OnlineLeaderboards.Local, activity.game.online)
        }
        assertEquals("ok", JSONObject(out.readText()).getString("result"))
        // Złośliwy adres wyników: własny profil gracza. Pętla go nie nadpisze.
        runLoop(intent(2, Uri.fromFile(profileFile)))
        assertEquals("ok", JSONObject(TestLoopActivity.lastResult!!).getString("result"))
        assertEquals(before, digest(files))
        assertArrayEquals(savedProfile, profileFile.readBytes())
        val loop = TestLoopActivity.loopDir(app)
        assertTrue("Profil pętli w katalogu pętli", File(loop, "profile.json").isFile)
    }

    @Test fun unknownScenarioEndsWithErrorWithoutTouchingFiles() {
        val files = app.filesDir
        File(files, "profile.json").writeText("{\"medals\":500}")
        val before = digest(files)
        runLoop(intent(99, Uri.fromFile(File(files, "profile.json"))))
        val o = JSONObject(TestLoopActivity.lastResult!!)
        assertEquals("error", o.getString("result"))
        assertEquals("unknown_scenario", o.getString("reason"))
        assertEquals(before, digest(files))
    }

    @Test fun ordinaryStartNeverEntersTestLoop() {
        val loop = TestLoopActivity.loopDir(app)
        loop.deleteRecursively()
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30)
        while (!activity.gameReady && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(5)
        }
        assertTrue(activity.gameReady)
        assertFalse(activity.testLoopActive)
        assertFalse("Zwykły start nie tworzy katalogu pętli", loop.exists())
        controller.pause().stop().destroy()
    }

    @Test fun mainIntentToTestLoopActivityJustFinishes() {
        val controller = Robolectric.buildActivity(TestLoopActivity::class.java,
            Intent(Intent.ACTION_MAIN).setClassName(app, TestLoopActivity::class.java.name)).setup()
        assertTrue(controller.get().isFinishing)
        assertFalse(controller.get().gameReady)
        controller.pause().stop().destroy()
    }
}
