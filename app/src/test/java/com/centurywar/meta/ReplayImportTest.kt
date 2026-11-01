package com.centurywar.meta

import com.centurywar.game.Battle
import com.centurywar.game.BattleConfig
import com.centurywar.game.BattleController
import com.centurywar.game.Mode
import com.centurywar.game.Replay
import com.centurywar.game.ReplayRecord
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ReplayImportTest {
    @get:Rule val temporary = TemporaryFolder()

    private fun recording(): ByteArray {
        val cfg = BattleConfig(Mode.SKIRMISH, 117L, 1f)
        val battle = Battle(cfg)
        val controller = BattleController(battle)
        repeat(180) { if (!battle.over) battle.step() }
        if (!battle.over) controller.surrender()
        assertTrue("Test wymaga rzeczywistego przebiegu bitwy", controller.log().steps >= 120)
        return ReplayRecord.encode(cfg, controller.log(),
            ReplayRecord.Meta(cfg.mode, cfg.stageIndex, battle.winner, battle.score(),
                battle.time, battle.wave, 1_792_000_000_000L, "Przyjaciel"))
    }

    private fun import(store: ReplayStore, bytes: ByteArray): String? {
        var result: String? = null
        val done = CountDownLatch(1)
        store.importAsync(bytes) { result = it; done.countDown() }
        assertTrue(done.await(20, TimeUnit.SECONDS))
        return result
    }

    private fun load(store: ReplayStore, id: String): ByteArray? {
        var result: ByteArray? = null
        val done = CountDownLatch(1)
        store.loadAsync(id) { result = it; done.countDown() }
        assertTrue(done.await(20, TimeUnit.SECONDS))
        return result
    }

    @Test fun exportImportKeepsExactReplayAndVerifiedBattle() {
        val store = ReplayStore(temporary.newFolder())
        val source = recording()
        store.saveAsync("original", source)
        val exported = load(store, "original")
        assertNotNull(exported)
        val importedId = import(store, ReplayImport.readLimited(
            ByteArrayInputStream(exported!!))!!)
        assertNotNull(importedId)
        assertTrue(ReplayStore.isImportedId(importedId!!))
        val received = load(store, importedId)
        assertArrayEquals(source, received)
        val first = ReplayRecord.decode(source)
        val second = ReplayRecord.decode(received!!)
        assertEquals(Replay.verify(first.cfg, first.log!!),
            Replay.verify(second.cfg, second.log!!))
        var imported = false
        val done = CountDownLatch(1)
        store.listAsync { entries -> imported = entries.any { it.id == importedId && it.imported }; done.countDown() }
        assertTrue(done.await(20, TimeUnit.SECONDS))
        assertTrue(imported)
    }

    @Test fun corruptAndObsoleteInputIsRejectedWithoutWritingAFile() {
        val store = ReplayStore(temporary.newFolder())
        assertNull(import(store, byteArrayOf(1, 2, 3)))
        val broken = recording().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
        assertNull(import(store, broken))
        val wrongVersion = recording().also { it[11] = (it[11].toInt() + 1).toByte() }
        assertNull(import(store, wrongVersion))
        val cfg = BattleConfig(Mode.SKIRMISH, 117L, 1f)
        val battle = Battle(cfg)
        val ctrl = BattleController(battle)
        ctrl.surrender()
        val valid = ReplayRecord.Meta(cfg.mode, cfg.stageIndex, battle.winner,
            battle.score(), battle.time, battle.wave, 1_792_000_000_000L, "Przyjaciel")
        val falseScore = ReplayRecord.encode(cfg, ctrl.log(),
            valid.copy(score = valid.score + 1000))
        assertNull("Poprawny CRC nie wystarcza: wynik musi zgadzać się z bitwą",
            import(store, falseScore))
        var count = -1
        val done = CountDownLatch(1)
        store.listAsync { count = it.size; done.countDown() }
        assertTrue(done.await(20, TimeUnit.SECONDS))
        assertEquals(0, count)
    }

    @Test fun inputStreamHasStrictStoreCompatibleLimit() {
        assertEquals(ReplayStore.MAX_RECORD_BYTES, ReplayImport.MAX_BYTES)
        assertNull(ReplayImport.readLimited(ByteArrayInputStream(ByteArray(0))))
        assertNull(ReplayImport.readLimited(ByteArrayInputStream(ByteArray(23))))
        assertNotNull(ReplayImport.readLimited(ByteArrayInputStream(ByteArray(24))))
        assertNotNull(ReplayImport.readLimited(
            ByteArrayInputStream(ByteArray(ReplayImport.MAX_BYTES))))
        assertNull(ReplayImport.readLimited(
            ByteArrayInputStream(ByteArray(ReplayImport.MAX_BYTES + 1))))
        assertFalse(ReplayStore.isImportedId("normal-1"))
    }

    @Test fun brokenInputStreamReturningZeroIsRejected() {
        val stalled = object : InputStream() {
            override fun read(): Int = 0
            override fun read(bytes: ByteArray, offset: Int, length: Int): Int = 0
        }
        assertNull(ReplayImport.readLimited(stalled))
    }
}
