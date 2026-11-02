package com.centurywar.meta

import com.centurywar.game.Mode
import com.centurywar.game.SaveGuard
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
@Config(sdk = [35])
class HistoryRecovery154Test {
    @get:Rule val folder = TemporaryFolder()

    @Before fun setUp() { SaveGuard.resetWarningForTests() }
    @After fun tearDown() { SaveGuard.resetWarningForTests() }

    private fun record(score: Long) = BattleHistory.Record(
        date = 1_700_000_000_000L + score,
        mode = Mode.SKIRMISH.ordinal,
        result = BattleHistory.WIN,
        score = score,
    )

    @Test fun randomAndTruncatedHistoryRecoverFromBackup() {
        val file = File(folder.root, "history.json")
        val first = BattleHistory(file)
        first.add(record(10))
        first.add(record(20))
        first.awaitWrites()
        first.close()
        assertTrue(File(folder.root, "history.json.bak").isFile)

        for (bad in listOf("{\"v\":1,\"entries\":[", "\u0000\u0001\u0002\u00ff")) {
            file.writeText(bad)
            val recovered = BattleHistory(file)
            recovered.load()
            assertEquals(listOf(20L, 10L), recovered.snapshot().map { it.score })
            assertTrue(SaveGuard.takeWarning())
            assertFalse(SaveGuard.takeWarning())
            assertTrue(file.readText().contains("\"score\":20"))
            recovered.close()
            SaveGuard.resetWarningForTests()
        }
    }

    @Test fun failedWriteAndFutureFileDoNotClearHistory() {
        val blocked = folder.newFile("blocked")
        val failed = BattleHistory(File(blocked, "history.json"))
        failed.add(record(33))
        failed.awaitWrites()
        assertEquals(1, failed.snapshot().size)
        assertTrue(SaveGuard.takeWarning())
        failed.close()

        SaveGuard.resetWarningForTests()
        val futureFile = File(folder.root, "future-history.json")
        futureFile.writeText("{\"v\":999,\"entries\":[]}")
        val future = BattleHistory(futureFile)
        future.load()
        future.add(record(55))
        future.awaitWrites()
        assertEquals("{\"v\":999,\"entries\":[]}", futureFile.readText())
        assertTrue(SaveGuard.takeWarning())
        future.close()
    }

    @Test fun interruptedTmpRecoveryDoesNotDropEntries() {
        val file = File(folder.root, "history.json")
        val history = BattleHistory(file)
        history.add(record(44))
        history.awaitWrites()
        history.close()
        val tmp = File(folder.root, "history.json.tmp")
        assertTrue(file.renameTo(tmp))
        val recovered = BattleHistory(file)
        recovered.load()
        assertEquals(44L, recovered.snapshot().single().score)
        assertTrue(file.exists())
        assertFalse(tmp.exists())
        recovered.close()
    }
}
