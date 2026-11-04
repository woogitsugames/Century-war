package com.centurywar.testloop

import android.content.Intent
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** R11-04: dane z eksportowanej intencji TEST_LOOP są sprawdzane przed użyciem. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TestLoopIntentTest {
    private val pkg = "com.centurywar.game"
    private val data = File("/data/user/0/$pkg")
    private val files = File(data, "files")
    private val loop = File(data, "cache/testloop")
    private val protectedDirs = listOf(data, files, File(data, "cache"))

    private fun allowed(scheme: String?, authority: String?, path: String?) =
        TestLoopIntent.allowedResultUri(scheme, authority, path, pkg, protectedDirs, loop)

    @Test fun scenarioNumbers() {
        val i = Intent(TestLoopIntent.ACTION)
        assertTrue(TestLoopIntent.isTestLoop(i))
        assertFalse(TestLoopIntent.isTestLoop(Intent(Intent.ACTION_MAIN)))
        assertFalse(TestLoopIntent.isTestLoop(null))
        assertEquals("Brak numeru = scenariusz 1", 1, TestLoopIntent.scenario(i))
        assertEquals(3, TestLoopIntent.scenario(Intent(i).putExtra("scenario", 3)))
        assertEquals("Zły typ dodatku", 1, TestLoopIntent.scenario(Intent(i).putExtra("scenario", "x")))
        assertTrue((1..4).all { TestLoopIntent.validScenario(it) })
        assertFalse(TestLoopIntent.validScenario(5))
        assertFalse(TestLoopIntent.validScenario(-1))
    }

    @Test fun resultUriFromTestLabIsAccepted() {
        assertTrue(allowed("content", "com.google.android.apps.testlooper.fileprovider", "/results/1.json"))
        assertTrue(allowed("file", null, "/sdcard/game-loop/results.json"))
        assertTrue(allowed("file", null, File(loop, "result.json").path))
    }

    @Test fun ownFilesAndProvidersAreRejected() {
        assertFalse(allowed("content", "$pkg.replays", "/r/1.cwr"))
        assertFalse(allowed("content", pkg, "/x"))
        assertFalse(allowed("content", null, "/x"))
        assertFalse(allowed("file", null, File(files, "profile.json").path))
        assertFalse(allowed("file", null, "$loop/../../files/profile.json"))
        assertFalse(allowed("file", null, data.path))
        assertFalse(allowed("file", null, ""))
        assertFalse(allowed("http", "example.com", "/x"))
        assertFalse(allowed(null, null, null))
    }

    @Test fun testLoopProcessName() {
        assertTrue(TestLoopProcess.isTestLoop("$pkg:testloop"))
        assertFalse(TestLoopProcess.isTestLoop(pkg))
        assertFalse(TestLoopProcess.isTestLoop(null))
    }
}
