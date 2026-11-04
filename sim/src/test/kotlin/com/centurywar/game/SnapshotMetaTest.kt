package com.centurywar.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.ByteBuffer
import java.util.zip.CRC32

/** R10-01: opis przerwanej bitwy (battle.meta) i powód odrzucenia zrzutu. */
class SnapshotMetaTest {

    @get:Rule val tmp = TemporaryFolder()

    private fun battle(): Battle = Battle(BattleConfig(Mode.ENDLESS, 41L, 1f)).also { b ->
        b.playerAi = Ai(b, 0)
        repeat(60 * 30) { b.step() }
    }

    private fun meta(id: Long = 77L, consolation: Int = 75) = BattleMeta(
        battleId = id, mode = Mode.ENDLESS, stageIndex = 0, weekly = -1L, dailyDay = -1L,
        rankedDaily = false, wave = 23, timeS = 1234, kills = 456, consolation = consolation,
        savedAtMillis = 1_700_000_000_000L,
    )

    /** Podmienia 4 bajty danych od [offset] i poprawia CRC (model zmiany balansu). */
    private fun patchPayload(bytes: ByteArray, offset: Int, value: Int): ByteArray {
        val out = bytes.copyOf()
        ByteBuffer.wrap(out).putInt(16 + offset, value)
        val crc = CRC32().apply { update(out, 16, out.size - 16) }.value.toInt()
        ByteBuffer.wrap(out).putInt(12, crc)
        return out
    }

    private fun withVersion(bytes: ByteArray, version: Int): ByteArray =
        bytes.copyOf().also { ByteBuffer.wrap(it).putInt(4, version) }

    @Test
    fun metaRoundTripsWithStableHeaderAndSmallSize() {
        val m = meta()
        val bytes = m.encode()
        assertTrue("opis ma być mały (${bytes.size} B)", bytes.size in 40..80)
        assertEquals(BattleMeta.MAGIC, ByteBuffer.wrap(bytes).getInt(0))
        assertEquals(1, ByteBuffer.wrap(bytes).getInt(4))
        val r = BattleMeta.decode(bytes)!!
        assertEquals(m.battleId, r.battleId); assertEquals(Mode.ENDLESS, r.mode)
        assertEquals(m.stageIndex, r.stageIndex); assertEquals(m.weekly, r.weekly)
        assertEquals(m.dailyDay, r.dailyDay); assertEquals(m.rankedDaily, r.rankedDaily)
        assertEquals(m.wave, r.wave); assertEquals(m.timeS, r.timeS); assertEquals(m.kills, r.kills)
        assertEquals(m.consolation, r.consolation); assertEquals(m.savedAtMillis, r.savedAtMillis)
    }

    @Test
    fun corruptMetaIsIgnored() {
        val bytes = meta().encode()
        for (i in bytes.indices) {
            val bad = bytes.copyOf().also { it[i] = (it[i].toInt() xor 0x40).toByte() }
            assertNull("bajt $i", BattleMeta.decode(bad))
        }
        assertNull(BattleMeta.decode(bytes.copyOf(bytes.size - 1)))
        assertNull(BattleMeta.decode(ByteArray(0)))
    }

    @Test
    fun probeNamesEveryReason() {
        val good = BattleSnapshot.encode(battle(), battleId = 77L)
        assertEquals(SnapshotReject.OK, BattleSnapshot.probe(good))
        val balance = patchPayload(good, 0, BattleSnapshot.defsFingerprint + 1)
        assertNull(BattleSnapshot.decode(balance))
        assertEquals(SnapshotReject.BALANCE_CHANGED, BattleSnapshot.probe(balance))
        // Wersja spoza obsługiwanych (starsza niż najstarsza wznawiana).
        val older = withVersion(good, BattleSnapshot.LEGACY_VERSION_WITHOUT_ID - 1)
        assertNull(BattleSnapshot.decode(older))
        assertEquals(SnapshotReject.OLDER_FORMAT, BattleSnapshot.probe(older))
        val newer = withVersion(good, BattleSnapshot.VERSION + 1)
        assertNull(BattleSnapshot.decode(newer))
        assertEquals(SnapshotReject.NEWER_FORMAT, BattleSnapshot.probe(newer))
        val cut = good.copyOf(good.size - 3)
        assertEquals(SnapshotReject.CORRUPT, BattleSnapshot.probe(cut))
        val crc = good.copyOf().also { it[good.size - 20] = (it[good.size - 20].toInt() xor 1).toByte() }
        assertEquals(SnapshotReject.CORRUPT, BattleSnapshot.probe(crc))
        assertEquals(SnapshotReject.CORRUPT, BattleSnapshot.probe(good.copyOf(10)))
        assertEquals(SnapshotReject.CORRUPT, BattleSnapshot.probe(withVersion(good, 0).also { it[0] = 0 }))
        // Identyfikator z końca danych da się odczytać także ze zrzutu, którego nie wznowimy.
        assertEquals(77L, BattleSnapshot.storedBattleId(balance))
    }

    private fun store(dir: File = tmp.root) = SnapshotStore(File(dir, "battle.snap"))

    @Test
    fun metaIsWrittenNextToSnapshotAndDeletesRemoveBoth() {
        val dir = tmp.newFolder()
        val s = store(dir)
        val snap = BattleSnapshot.encode(battle(), battleId = 77L)
        assertTrue(s.saveNow(snap, 77L, meta().encode()))
        val metaFile = File(dir, "battle.meta")
        assertTrue(metaFile.isFile)
        assertEquals(77L, s.readMeta()!!.battleId)
        s.delete()
        assertFalse(File(dir, "battle.snap").exists()); assertFalse(metaFile.exists())

        assertTrue(s.saveNow(snap, 77L, meta().encode()))
        s.deleteAsync(); assertTrue(s.awaitWrites(5_000))
        assertFalse(File(dir, "battle.snap").exists()); assertFalse(metaFile.exists())

        assertTrue(s.saveNow(snap, 77L, meta().encode()))
        assertTrue(s.deleteSettledAsync(77L)); assertTrue(s.awaitWrites(5_000))
        assertFalse(File(dir, "battle.snap").exists()); assertFalse(metaFile.exists())

        // Zapis bez opisu nie zostawia starego opisu innej bitwy.
        assertTrue(s.saveNow(snap, 77L, meta().encode()))
        assertTrue(s.saveNow(snap, 77L))
        assertFalse(metaFile.exists())
    }

    @Test
    fun lostByUpdateOnlyForOlderFormatOrBalance() {
        val good = BattleSnapshot.encode(battle(), battleId = 77L)
        val cases = listOf(
            patchPayload(good, 0, BattleSnapshot.defsFingerprint xor 0x5A5A) to true,
            // Starszy format: poprawne CRC, nieobsługiwany numer wersji w nagłówku.
            withVersion(good, BattleSnapshot.LEGACY_VERSION_WITHOUT_ID - 1) to true,
            withVersion(good, BattleSnapshot.VERSION + 1) to false,
            good.copyOf(good.size - 5) to false,
        )
        for ((i, case) in cases.withIndex()) {
            val (bytes, lost) = case
            val dir = tmp.newFolder()
            File(dir, "battle.snap").writeBytes(bytes)
            File(dir, "battle.meta").writeBytes(meta().encode())
            val s = store(dir)
            assertNull(s.loadBattle(0L))
            if (lost) {
                assertNotNull("przypadek $i", s.lostByUpdate)
                assertEquals(75, s.lostByUpdate!!.consolation)
                // Rozliczenie usuwa zrzut i opis (właściciel = bitwa z opisu).
                assertTrue(s.deleteSettledAsync(77L)); assertTrue(s.awaitWrites(5_000))
                assertFalse(File(dir, "battle.snap").exists()); assertFalse(File(dir, "battle.meta").exists())
            } else {
                assertNull("przypadek $i", s.lostByUpdate)
                assertTrue("plik zostaje do diagnostyki", File(dir, "battle.snap").exists())
            }
        }
    }

    @Test
    fun settledOrMismatchedMetaGivesNoCompensation() {
        val good = BattleSnapshot.encode(battle(), battleId = 77L)
        val changed = patchPayload(good, 0, BattleSnapshot.defsFingerprint + 7)
        // Już rozliczona (proces zginął po zapisie profilu): pliki znikają, bez rekompensaty.
        val dir = tmp.newFolder()
        File(dir, "battle.snap").writeBytes(changed)
        File(dir, "battle.meta").writeBytes(meta().encode())
        val s = store(dir)
        assertNull(s.loadBattle(77L))
        assertNull(s.lostByUpdate)
        assertFalse(File(dir, "battle.snap").exists()); assertFalse(File(dir, "battle.meta").exists())
        // Opis innej bitwy niż zrzut jest ignorowany.
        val dir2 = tmp.newFolder()
        File(dir2, "battle.snap").writeBytes(changed)
        File(dir2, "battle.meta").writeBytes(meta(id = 78L).encode())
        val s2 = store(dir2)
        assertNull(s2.loadBattle(0L))
        assertNull(s2.lostByUpdate)
        // Brak opisu (zrzut sprzed R10-01): bez rekompensaty.
        val dir3 = tmp.newFolder()
        File(dir3, "battle.snap").writeBytes(changed)
        val s3 = store(dir3)
        assertNull(s3.loadBattle(0L))
        assertNull(s3.lostByUpdate)
    }

    @Test
    fun compatibleSnapshotStillResumesAndIgnoresMeta() {
        val dir = tmp.newFolder()
        val s = store(dir)
        assertTrue(s.saveNow(BattleSnapshot.encode(battle(), battleId = 77L), 77L, meta().encode()))
        val fresh = store(dir)
        val restored = fresh.loadBattle(0L)
        assertNotNull(restored)
        assertEquals(77L, restored!!.battleId)
        assertNull(fresh.lostByUpdate)
    }
}
