package com.centurywar.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import com.centurywar.engine.Game
import com.centurywar.engine.Host
import com.centurywar.engine.Quality
import com.centurywar.game.Ai
import com.centurywar.game.Campaign
import com.centurywar.game.RestoredBattle
import com.centurywar.game.SnapshotStore
import com.centurywar.meta.Profile
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A156-10: idempotentne rozliczenie bitwy z wstrzykiwanymi awariami.
 *
 * „Zabicie procesu” modelujemy obrazem katalogu danych: kopiujemy pliki z dysku dokładnie
 * w chwili przerwania, a „ponowne uruchomienie” to nowe obiekty Profile/SnapshotStore/Game
 * na tej kopii, z tą samą procedurą startu co MainActivity (load → loadBattle(lastSettled)).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BattleSettlement156Test {
    @get:Rule val folder = TemporaryFolder()

    private val canvas = Canvas(Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888))
    private val host = object : Host {
        override fun vibrate(ms: Long) {}
        override fun requestText(title: String, current: String, onResult: (String) -> Unit) {}
        override fun applyRenderScale(scale: Float) {}
        override fun applyFrameRate(fps: Int) {}
        override fun exit() {}
    }

    /** Jedno „uruchomienie” aplikacji na katalogu [dir]. */
    private inner class Run(val dir: File) {
        val profile = Profile(File(dir, "profile.json")).also { it.load() }
        val store = SnapshotStore(File(dir, "battle.snap"))
        val restored: RestoredBattle? = store.loadBattle(profile.lastSettledBattle)
        val game = Game(host, profile, Quality.LOW).also { g ->
            profile.tutorialDone = true
            profile.hintsEnabled = false
            profile.eraIntrosEnabled = false
            g.snapshots = store
            g.density = 2f
            g.resize(1600, 720, 1600, 720)
        }

        fun flush() {
            // Rozliczenie: zapis profilu → potwierdzenie → usunięcie zrzutu na wątku zrzutów.
            repeat(3) {
                Profile.awaitHintWrites()
                assertTrue(store.awaitWrites(5_000L))
            }
        }
    }

    private fun stage() = Campaign.stages[2]

    /** Bitwa grana przez AI ~4 min symulacji, potem pauza (autozapis zrzutu). */
    private fun playAndPause(run: Run): BattleScreen {
        val bs = BattleScreen(run.game, Campaign.config(stage(), run.profile.bonuses(), 5L))
        run.game.setScreen(bs)
        run.game.frame(canvas, 1f / 60f)
        val b = bs.battle
        b.playerAi = Ai(b, 0)
        while (!b.over && b.time < 150f) b.step()
        while (!b.over && (b.pendingChoice != null || b.pendingCards != null || b.pendingVariant != null)) b.step()
        assertFalse("bitwa testowa nie może się skończyć przed pauzą", b.over)
        bs.onPause()
        run.flush()
        assertTrue(File(run.dir, "battle.snap").exists())
        bs.paused = false
        return bs
    }

    private fun finish(run: Run, bs: BattleScreen) {
        bs.paused = false
        bs.battle.surrender()
        run.game.frame(canvas, 1f / 60f)
        assertTrue(bs.finished)
    }

    private fun image(from: File, name: String): File {
        val to = folder.newFolder(name)
        from.listFiles()?.filter { it.isFile }?.forEach { it.copyTo(File(to, it.name)) }
        return to
    }

    private fun diskMedals(dir: File): Int {
        val f = File(dir, "profile.json")
        return if (f.exists()) JSONObject(f.readText()).optInt("medals", 0) else 0
    }

    private fun resume(run: Run): BattleScreen {
        val r = assertNotNullAnd(run.restored)
        val bs = BattleScreen(run.game, r.battle.cfg, r.rankedDaily, r)
        run.game.setScreen(bs)
        run.game.frame(canvas, 1f / 60f)
        return bs
    }

    private fun <T> assertNotNullAnd(v: T?): T {
        assertNotNull(v)
        return v!!
    }

    @Test
    fun normalSettlementPersistsProfileBeforeDeletingSnapshot() {
        val run = Run(folder.newFolder("normal"))
        val bs = playAndPause(run)
        val before = run.profile.medals
        finish(run, bs)
        run.flush()
        assertTrue(bs.totalMedalsGained > 0)
        assertEquals(before + bs.totalMedalsGained, diskMedals(run.dir))
        assertEquals(bs.battleId, JSONObject(File(run.dir, "profile.json").readText()).getLong("lastSettledBattle"))
        assertFalse("po trwałym zapisie profilu zrzut znika", File(run.dir, "battle.snap").exists())

        val again = Run(run.dir)
        assertNull(again.restored)
        assertEquals(before + bs.totalMedalsGained, again.profile.medals)
    }

    @Test
    fun crashBeforeProfileIsDurableKeepsBattleResumableAndPaysOnce() {
        val dir = folder.newFolder("crash-before-profile")
        val run = Run(dir)
        val bs = playAndPause(run)
        val before = run.profile.medals
        // Awaria zapisu profilu: plik tymczasowy SaveGuard nie może powstać.
        val blocker = File(dir, "profile.json.tmp").apply { mkdirs() }
        finish(run, bs)
        run.flush()
        assertTrue(bs.totalMedalsGained > 0)
        assertTrue("bez trwałego profilu zrzut musi zostać", File(dir, "battle.snap").exists())
        assertEquals(before, diskMedals(dir))
        val crashed = image(dir, "crash-before-profile-image")
        File(crashed, "profile.json.tmp").deleteRecursively()
        blocker.deleteRecursively()

        // Restart: nagrody nie ma na dysku, bitwę można wznowić i rozliczyć dokładnie raz.
        val second = Run(crashed)
        assertEquals(before, second.profile.medals)
        assertEquals(bs.battleId, second.restored?.battleId)
        val resumed = resume(second)
        assertEquals(bs.battleId, resumed.battleId)
        finish(second, resumed)
        second.flush()
        assertTrue(resumed.totalMedalsGained > 0)
        assertEquals(before + resumed.totalMedalsGained, diskMedals(crashed))
        assertFalse(File(crashed, "battle.snap").exists())

        val third = Run(crashed)
        assertNull("rozliczona bitwa nie wraca do wznowienia", third.restored)
        assertEquals(before + resumed.totalMedalsGained, third.profile.medals)
    }

    @Test
    fun crashAfterProfileBeforeSnapshotDeleteNeverPaysTwice() {
        val dir = folder.newFolder("crash-after-profile")
        val run = Run(dir)
        val bs = playAndPause(run)
        val before = run.profile.medals
        val crashImage = folder.newFolder("crash-after-profile-image")
        val captured = AtomicBoolean(false)
        // Obraz dysku tuż po atomowym zapisie profilu, zanim ruszy usuwanie zrzutu.
        run.profile.onSaved = {
            val f = File(dir, "profile.json")
            if (!captured.get() && JSONObject(f.readText()).optLong("lastSettledBattle") == bs.battleId) {
                dir.listFiles()?.filter { it.isFile }?.forEach { it.copyTo(File(crashImage, it.name), true) }
                captured.set(true)
            }
        }
        finish(run, bs)
        run.flush()
        run.profile.onSaved = null
        assertTrue(captured.get())
        val paid = bs.totalMedalsGained
        assertTrue(paid > 0)
        assertTrue("obraz awarii zawiera jeszcze zrzut", File(crashImage, "battle.snap").exists())
        assertEquals(before + paid, diskMedals(crashImage))

        // Kontrola: bez znacznika rozliczenia stary zrzut byłby znów do wznowienia.
        val unguarded = image(crashImage, "unguarded")
        val stale = SnapshotStore(File(unguarded, "battle.snap")).loadBattle()
        assertNotNull(stale)
        // Nawet jeśli ktoś wznowi taki zrzut, rozliczenie tej samej bitwy nic nie dodaje.
        val replayRun = Run(unguarded)
        val staleScreen = BattleScreen(replayRun.game, stale!!.battle.cfg, stale.rankedDaily, stale)
        replayRun.game.setScreen(staleScreen)
        replayRun.game.frame(canvas, 1f / 60f)
        assertEquals(bs.battleId, staleScreen.battleId)
        finish(replayRun, staleScreen)
        replayRun.flush()
        assertEquals(0, staleScreen.totalMedalsGained)
        assertEquals(before + paid, replayRun.profile.medals)
        assertEquals(before + paid, diskMedals(unguarded))
        assertFalse("zbędny zrzut rozliczonej bitwy jest sprzątany", File(unguarded, "battle.snap").exists())

        // Właściwy restart: start gry odrzuca zrzut już rozliczonej bitwy.
        val second = Run(crashImage)
        assertNull(second.restored)
        assertFalse(File(crashImage, "battle.snap").exists())
        assertEquals(before + paid, second.profile.medals)
    }

    @Test
    fun crashWhileProfileWriteIsQueuedLeavesNoPartialReward() {
        val dir = folder.newFolder("crash-queued")
        val run = Run(dir)
        val bs = playAndPause(run)
        val before = run.profile.medals
        // Wstrzymany wątek I/O profilu (wspólny wykonawca): rozliczenie czeka w kolejce.
        val gate = java.util.concurrent.CountDownLatch(1)
        val blockerProfile = Profile(File(folder.newFolder("blocker"), "profile.json"))
        blockerProfile.onSaved = { gate.await() }
        blockerProfile.save()
        val image: File
        try {
            finish(run, bs)
            assertTrue(bs.totalMedalsGained > 0)
            image = image(dir, "crash-queued-image")
        } finally {
            gate.countDown()
        }
        run.flush()
        // W obrazie awarii nie ma ani części nagrody, ani znacznika; zrzut zostaje.
        assertEquals(before, diskMedals(image))
        assertTrue(File(image, "battle.snap").exists())
        val second = Run(image)
        assertEquals(bs.battleId, second.restored?.battleId)
        assertEquals(before, second.profile.medals)
        assertNotEquals(bs.battleId, second.profile.lastSettledBattle)
    }

    @Test
    fun savesRequestedDuringSettlementAreDeferredToOneSnapshot() {
        val dir = folder.newFolder("deferred")
        val p = Profile(File(dir, "profile.json"))
        var writes = 0
        p.onSaved = { writes++ }
        assertTrue(p.settleBattle(7L) {
            p.medals += 40
            p.save() // np. Achievements.onBattleEnd
            p.saveAsync()
            Profile.awaitHintWrites()
            assertFalse("połowa nagrody nie może trafić na dysk", File(dir, "profile.json").exists())
        })
        Profile.awaitHintWrites()
        assertEquals(1, writes)
        assertEquals(40, diskMedals(dir))
        assertFalse(p.settleBattle(7L) { p.medals += 40 })
        assertEquals(40, p.medals)
        // Identyfikator 0 (brak) nigdy nie blokuje rozliczenia.
        assertTrue(p.settleBattle(0L) { p.medals += 1 })
        assertEquals(41, p.medals)
        Profile.awaitHintWrites()
    }

    @Test
    fun profileWithoutSettlementFieldLoadsAsZeroAndFieldStaysLocal() {
        val dir = folder.newFolder("compat")
        val p = Profile(File(dir, "profile.json"))
        p.medals = 12
        p.settleBattle(55L) {}
        Profile.awaitHintWrites()
        val json = JSONObject(File(dir, "profile.json").readText())
        assertEquals(55L, json.getLong("lastSettledBattle"))
        json.remove("lastSettledBattle")
        File(dir, "profile.json").writeText(json.toString())
        val old = Profile(File(dir, "profile.json")).also { it.load() }
        assertEquals(12, old.medals)
        assertEquals(0L, old.lastSettledBattle)
        // Chmura nie przenosi znacznika i nie nadpisuje lokalnego.
        assertFalse(JSONObject(p.toCloudJson()).has("lastSettledBattle"))
        val cloud = JSONObject(p.toCloudJson()).put("lastSettledBattle", 99L).toString()
        p.fromJson(cloud)
        assertEquals(55L, p.lastSettledBattle)
    }
}
