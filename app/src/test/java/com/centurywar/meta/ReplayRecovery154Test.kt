package com.centurywar.meta

import com.centurywar.game.Battle
import com.centurywar.game.BattleConfig
import com.centurywar.game.BattleController
import com.centurywar.game.Mode
import com.centurywar.game.ReplayRecord
import com.centurywar.game.SaveGuard
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ReplayRecovery154Test {
    @get:Rule val folder = TemporaryFolder()

    @Before fun setUp() { SaveGuard.resetWarningForTests() }
    @After fun tearDown() { SaveGuard.resetWarningForTests() }

    private fun record(): ByteArray {
        val config = BattleConfig(Mode.SKIRMISH, 2026L, 1f)
        val battle = Battle(config)
        val commands = BattleController(battle)
        commands.surrender()
        return ReplayRecord.encode(config, commands.log(),
            ReplayRecord.Meta(config.mode, config.stageIndex, battle.winner,
                battle.score(), battle.time, battle.wave, 1_793_461_200_000L, "Tester"))
    }

    private fun load(store: ReplayStore, id: String): ByteArray? {
        val done = CountDownLatch(1)
        var result: ByteArray? = null
        store.loadAsync(id) { result = it; done.countDown() }
        assertTrue(done.await(10, TimeUnit.SECONDS))
        return result
    }

    @Test fun malformedReplayIsNotDeliveredToPlaybackOrRemovedFromProfile() {
        val dir = folder.newFolder()
        val store = ReplayStore(dir)
        val original = record()
        store.saveAsync("good", original)
        assertTrue(store.awaitWrites(10_000L))
        val truncated = File(dir, "broken.cwr")
        truncated.writeBytes(original.copyOf(32))
        val random = File(dir, "random.cwr")
        random.writeBytes(ByteArray(128) { (it * 97).toByte() })

        assertNull(load(store, "broken"))
        assertNull(load(store, "random"))
        assertTrue(SaveGuard.takeWarning())
        assertFalse(SaveGuard.takeWarning())
        assertTrue(load(store, "good")!!.contentEquals(original))

        val done = CountDownLatch(1)
        var ids: List<String> = emptyList()
        store.listAsync { entries -> ids = entries.map { it.id }; done.countDown() }
        assertTrue(done.await(10, TimeUnit.SECONDS))
        assertEquals(listOf("good"), ids)
    }

    @Test fun unwritableReplayFolderDoesNotCrashAsyncWriter() {
        val blocked = folder.newFile("not-a-folder")
        val store = ReplayStore(blocked)
        store.saveAsync("one", record())
        assertTrue(store.awaitWrites(10_000L))
        assertFalse(File(blocked, "one.cwr").exists())
        assertTrue(SaveGuard.takeWarning())
    }
}
