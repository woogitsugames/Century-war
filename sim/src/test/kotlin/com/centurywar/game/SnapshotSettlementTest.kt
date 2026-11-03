package com.centurywar.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.util.zip.CRC32

/** A156-10: identyfikator rozliczenia w zrzucie (v17) i migracja zrzutu v16. */
class SnapshotSettlementTest {

    @get:Rule val tmp = TemporaryFolder()

    private fun battle(seed: Long = 77L): Battle =
        Battle(BattleConfig(Mode.SKIRMISH, seed, 1f)).also { b ->
            b.playerAi = Ai(b, 0)
            repeat(60 * 45) { b.step() }
        }

    /** Zrzut w dawnym formacie 16 (bez identyfikatora i bez kart Endless zlecenia 170). */
    private fun legacyV16(b: Battle): ByteArray =
        BattleSnapshot.encode(b, rankedDaily = false, camX = 12f, battleId = 99L, formatVersion = 16)

    /** Zrzut zapisany grą sprzed zlecenia 170 (v17: identyfikator, krótsza lista kart). */
    @Test
    fun legacyV17SnapshotWithoutNewCardsResumes() {
        val a = battle()
        val legacy = BattleSnapshot.encode(a, rankedDaily = false, camX = 12f, battleId = 55L, formatVersion = 17)
        val restored = BattleSnapshot.decode(legacy)
        assertNotNull("zrzut v17 musi się wczytać po aktualizacji", restored)
        assertEquals(55L, restored!!.battleId)
        val b = restored.battle
        repeat(600) { a.step(); b.step() }
        assertEquals(a.player.baseHp, b.player.baseHp, 0f)
        assertEquals(a.enemy.baseHp, b.enemy.baseHp, 0f)
    }

    /** Dzień 2: v19 zapisuje obrażenia z presji oblężniczej (gwiazdki), v18 wczytuje się z 0. */
    @Test
    fun siegeDamageRoundTripsAndV18LoadsAsZero() {
        val a = Battle(Campaign.config(Campaign.stages[2], Bonuses(), 2_002L)).also { b ->
            b.playerAi = Ai(b, 0)
            // Presja w etapach 2–7 zaczyna się po 4 minutach; 6 minut wystarcza na blokadę.
            repeat(60 * 360) { if (!b.over) b.step() }
        }
        assertFalse(a.over)
        assertTrue("presja musi już odebrać HP", a.player.siegeDamageTaken > 0f && a.enemy.siegeDamageTaken > 0f)
        val b = BattleSnapshot.decode(BattleSnapshot.encode(a, battleId = 7L))!!.battle
        assertEquals(a.player.siegeDamageTaken, b.player.siegeDamageTaken, 0f)
        assertEquals(a.enemy.siegeDamageTaken, b.enemy.siegeDamageTaken, 0f)
        assertTrue(BattleSnapshot.encode(a, battleId = 7L).contentEquals(BattleSnapshot.encode(b, battleId = 7L)))

        val legacy = BattleSnapshot.encode(a, rankedDaily = false, camX = 0f, battleId = 7L, formatVersion = 18)
        val old = BattleSnapshot.decode(legacy)
        assertNotNull("zrzut v18 musi się wczytać po aktualizacji", old)
        assertEquals(0f, old!!.battle.player.siegeDamageTaken, 0f)
        assertEquals(0f, old.battle.enemy.siegeDamageTaken, 0f)
        // Pole jest tylko statystyczne: dalszy przebieg identyczny jak oryginał.
        repeat(600) { a.step(); old.battle.step() }
        assertEquals(a.player.baseHp, old.battle.player.baseHp, 0f)
        assertEquals(a.enemy.baseHp, old.battle.enemy.baseHp, 0f)
        // v19 oznaczony jako v18 ma o 8 bajtów za dużo, więc nie przechodzi.
        val mislabeled = BattleSnapshot.encode(b, battleId = 7L).copyOf().also { it[7] = 18 }
        assertNull(BattleSnapshot.decode(mislabeled))
    }

    @Test
    fun battleIdRoundTripsAndDoesNotChangeSimulation() {
        val a = battle()
        val restored = BattleSnapshot.decode(BattleSnapshot.encode(a, true, 5f, battleId = -4242L))
        assertNotNull(restored)
        assertEquals(-4242L, restored!!.battleId)
        assertTrue(restored.rankedDaily)
        // Dalsza symulacja jest identyczna niezależnie od identyfikatora.
        val other = BattleSnapshot.decode(BattleSnapshot.encode(a, true, 5f, battleId = 1L))!!.battle
        repeat(600) { restored.battle.step(); other.step() }
        assertTrue(BattleSnapshot.encode(restored.battle).contentEquals(BattleSnapshot.encode(other)))
        assertEquals(restored.battle.time, other.time, 0f)
        assertEquals(restored.battle.player.gold, other.player.gold, 0f)
        assertEquals(restored.battle.enemy.baseHp, other.enemy.baseHp, 0f)
    }

    @Test
    fun legacyV16SnapshotStillResumesWithoutId() {
        val a = battle(78L)
        val legacy = legacyV16(a)
        val restored = BattleSnapshot.decode(legacy)
        assertNotNull("zrzut v16 musi nadal się wczytać po aktualizacji", restored)
        assertEquals(0L, restored!!.battleId)
        assertEquals(12f, restored.camX, 0f)
        assertEquals(a.stepCount, restored.battle.stepCount)
        // Dodatkowe bajty za danymi v16 nie mogą przejść jako poprawny zapis.
        val v17 = BattleSnapshot.encode(a, battleId = 5L)
        val mislabeled = v17.copyOf().also { it[7] = 16 }
        assertNull(BattleSnapshot.decode(mislabeled))
    }

    @Test
    fun storeDiscardsSnapshotOfAlreadySettledBattle() {
        val file = tmp.newFolder().resolve("battle.snap")
        val bytes = BattleSnapshot.encode(battle(79L), battleId = 31L)
        assertTrue(SnapshotStore(file).saveNow(bytes, 31L))
        // Inna rozliczona bitwa: zrzut zostaje do wznowienia.
        assertNotNull(SnapshotStore(file).loadBattle(settledBattleId = 30L))
        assertTrue(file.exists())
        // Ta sama bitwa już rozliczona: zrzut znika i nie jest oferowany.
        assertNull(SnapshotStore(file).loadBattle(settledBattleId = 31L))
        assertFalse(file.exists())
        assertFalse(java.io.File(file.parentFile, "battle.snap.bak").exists())
        assertNull(SnapshotStore(file).loadBattle())
    }

    @Test
    fun settledDeleteNeverRemovesSnapshotOfNewerBattle() {
        val file = tmp.newFolder().resolve("battle.snap")
        val store = SnapshotStore(file)
        val first = BattleSnapshot.encode(battle(80L), battleId = 1L)
        val second = BattleSnapshot.encode(battle(81L), battleId = 2L)
        store.saveAsync(first, 1L)
        // Nowa bitwa zdążyła się zapisać, zanim dotarło potwierdzenie zapisu profilu bitwy 1.
        store.saveAsync(second, 2L)
        assertFalse(store.deleteSettledAsync(1L))
        assertTrue(store.awaitWrites(5_000L))
        assertEquals(2L, SnapshotStore(file).loadBattle()!!.battleId)

        assertTrue(store.deleteSettledAsync(2L))
        assertTrue(store.awaitWrites(5_000L))
        assertFalse(file.exists())
        assertFalse(store.deleteSettledAsync(0L))
    }

    @Test
    fun loadedBattleOwnsFileForSettledDelete() {
        val file = tmp.newFolder().resolve("battle.snap")
        assertTrue(SnapshotStore(file).saveNow(BattleSnapshot.encode(battle(82L), battleId = 9L), 9L))
        // Nowy proces: bitwa wczytana z dysku i rozliczona bez kolejnego autozapisu.
        val store = SnapshotStore(file)
        assertEquals(9L, store.loadBattle()!!.battleId)
        assertTrue(store.deleteSettledAsync(9L))
        assertTrue(store.awaitWrites(5_000L))
        assertFalse(file.exists())
    }
}
