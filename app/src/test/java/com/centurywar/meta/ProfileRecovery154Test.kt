package com.centurywar.meta

import com.centurywar.game.SaveGuard
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class ProfileRecovery154Test {
    @get:Rule val folder = TemporaryFolder()

    @Before fun setUp() { SaveGuard.resetWarningForTests() }
    @After fun tearDown() { SaveGuard.resetWarningForTests() }

    private fun existing(): File = File(folder.root, "profile.json")

    @Test fun truncatedMainRestoresLastCompleteBackupAndKeepsCorruption() {
        val file = existing()
        val profile = Profile(file)
        profile.name = "Zachowany postęp"
        profile.medals = 1234
        profile.stars[0] = 3
        profile.save()
        Profile.awaitHintWrites()
        profile.medals = 8765
        profile.save()
        Profile.awaitHintWrites()
        assertEquals(8765, JSONObject(File(folder.root, "profile.json.bak").readText()).getInt("medals"))

        file.writeText("{\"v\":3,\"medals\":")
        val restored = Profile(file).apply { load() }
        assertEquals("Zachowany postęp", restored.name)
        assertEquals(8765, restored.medals)
        assertEquals(3, restored.stars[0])
        assertTrue(SaveGuard.takeWarning())
        assertFalse(SaveGuard.takeWarning())
        assertTrue(File(folder.root, "profile.json.corrupt").isFile)
        Profile.awaitHintWrites()
        assertEquals(8765, JSONObject(file.readText()).getInt("medals"))
    }

    @Test fun randomMainFallsBackToBackupWithoutResettingResearch() {
        val file = existing()
        Profile(file).apply {
            name = "Badacz"
            medals = 500
            levels[Profile.research.first().key] = 1
            save()
        }
        Profile.awaitHintWrites()
        file.writeBytes(ByteArray(307) { index -> (index * 71).toByte() })

        val restored = Profile(file).apply { load() }
        assertEquals("Badacz", restored.name)
        assertEquals(500, restored.medals)
        assertEquals(1, restored.level(Profile.research.first().key))
        assertTrue(SaveGuard.takeWarning())
        Profile.awaitHintWrites()
    }

    @Test fun savingIntoNonDirectoryReportsFailureWithoutThrowing() {
        val blockedParent = folder.newFile("blocked")
        val file = File(blockedParent, "profile.json")
        Profile(file).apply { name = "Wojownik"; medals = 31; save() }
        Profile.awaitHintWrites()
        assertFalse(file.exists())
        assertTrue(SaveGuard.takeWarning())
        assertFalse(SaveGuard.takeWarning())
    }
}
