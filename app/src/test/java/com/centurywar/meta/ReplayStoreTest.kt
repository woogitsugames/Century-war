package com.centurywar.meta

import com.centurywar.game.Battle
import com.centurywar.game.BattleConfig
import com.centurywar.game.BattleController
import com.centurywar.game.Mode
import com.centurywar.game.ReplayRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ReplayStoreTest {
    @get:Rule val temporary = TemporaryFolder()

    private fun record(date: Long): ByteArray {
        val cfg = BattleConfig(Mode.SKIRMISH, 9L, 1f)
        val battle = Battle(cfg)
        val ctrl = BattleController(battle)
        ctrl.surrender()
        return ReplayRecord.encode(cfg, ctrl.log(),
            ReplayRecord.Meta(cfg.mode, -1, battle.winner, battle.score(),
                battle.time, battle.wave, date, "Tester"))
    }

    private fun listing(store: ReplayStore): List<ReplayStore.Entry> {
        var value: List<ReplayStore.Entry>? = null
        val done = CountDownLatch(1)
        store.listAsync { value = it; done.countDown() }
        assertTrue("Nie udało się odczytać listy powtórek", done.await(20, TimeUnit.SECONDS))
        return value!!
    }

    @Test fun retentionKeepsLast20PlusPinnedAndCanDelete() {
        val dir = temporary.newFolder()
        val store = ReplayStore(dir)
        repeat(10) {
            val id = "test_$it"
            store.saveAsync(id, record(it.toLong()))
            store.pinAsync(id)
        }
        repeat(35) {
            store.saveAsync("new_$it", record((100 + it).toLong()))
        }
        val all = listing(store)
        assertEquals(30, all.size)
        assertEquals(10, all.count { it.pinned })
        assertTrue(all.any { it.id == "new_34" && !it.pinned })
        assertFalse(all.any { it.id == "new_0" })
        store.deleteAsync("test_0")
        assertFalse(listing(store).any { it.id == "test_0" })
        assertNotNull(dir.resolve("new_34.cwr").takeIf { it.isFile })
    }

    @Test fun corruptFilesAreNotListedAndReadIsAsynchronous() {
        val dir = temporary.newFolder()
        val store = ReplayStore(dir)
        store.saveAsync("valid", record(100L))
        dir.resolve("corrupt.cwr").writeBytes(byteArrayOf(1, 2, 3))
        assertEquals(1, listing(store).size)
        var loaded: ByteArray? = null
        val done = CountDownLatch(1)
        store.loadAsync("valid") { loaded = it; done.countDown() }
        assertTrue(done.await(10, TimeUnit.SECONDS))
        assertNotNull(loaded)
        assertTrue(ReplayRecord.decode(loaded!!).playable)
    }
}
