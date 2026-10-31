package com.centurywar.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplayRecordTest {
    private val cfg = BattleConfig(
        Mode.CAMPAIGN, 87921L, 1.13f, 0, 1,
        setOf(Modifier.DOUBLE_INCOME, Modifier.RANGED_HORDE),
        Bonuses(startGold = 310f, unitHpMul = 1.4f, eliteUnlocked = false),
        stageIndex = 0, tutorial = true, aiPersonality = AiPersonality.SIEGER,
    )

    private fun example(): Pair<Replay.Log, ReplayRecord.Meta> {
        val battle = Battle(cfg)
        val ctrl = BattleController(battle)
        ctrl.queue(Role.MELEE)
        repeat(90) { if (!battle.over) { battle.step(); battle.fx.count = 0 } }
        if (!battle.over) ctrl.surrender()
        val meta = ReplayRecord.Meta(cfg.mode, cfg.stageIndex, battle.winner,
            battle.score(), battle.time, battle.wave, 1793461200000L, "Dowódca")
        return ctrl.log() to meta
    }

    private fun rejected(bytes: ByteArray) {
        var ok = false
        try { ReplayRecord.decode(bytes) } catch (_: IllegalArgumentException) { ok = true }
        assertTrue("Uszkodzone lub obce dane muszą być odrzucone", ok)
    }

    @Test fun roundTripKeepsWholeConfigLogAndMetadata() {
        val (log, meta) = example()
        val bytes = ReplayRecord.encode(cfg, log, meta)
        assertTrue(bytes.size < 8192)
        val record = ReplayRecord.decode(bytes)
        assertTrue(record.playable)
        assertEquals(Replay.VERSION, record.replayVersion)
        assertEquals(BattleSnapshot.defsFingerprint, record.fingerprint)
        assertEquals(cfg.mode, record.cfg.mode)
        assertEquals(cfg.seed, record.cfg.seed)
        assertEquals(cfg.aiPersonality, record.cfg.aiPersonality)
        assertEquals(cfg.modifiers, record.cfg.modifiers)
        assertEquals(cfg.playerBonuses.unitHpMul, record.cfg.playerBonuses.unitHpMul, 0f)
        assertEquals(cfg.playerBonuses.eliteUnlocked, record.cfg.playerBonuses.eliteUnlocked)
        assertEquals(meta, record.meta)
        assertEquals(log, record.log)
        assertEquals(Replay.verify(cfg, log), Replay.verify(record.cfg, record.log!!))
    }

    @Test fun rejectsCorruptionTruncationForeignVersionAndExtraBytes() {
        val (log, meta) = example()
        val bytes = ReplayRecord.encode(cfg, log, meta)
        rejected(byteArrayOf(1, 2, 3))
        rejected(ByteArray(28) { 5 })
        rejected(bytes.copyOf(bytes.size - 1))
        rejected(bytes + 0)
        rejected(bytes.copyOf().also { it[0] = 0 })
        rejected(bytes.copyOf().also { it[7] = (ReplayRecord.VERSION + 1).toByte() })
        rejected(bytes.copyOf().also { it[bytes.size - 1] = (it.last() + 1).toByte() })
        rejected(bytes.copyOf().also { it[19] = -1 }) // nieprawidłowa długość
        rejected(bytes.copyOf().also { it[23] = (it[23].toInt() xor 1).toByte() }) // CRC32
    }

    @Test fun incompatibleBalanceAndReplayVersionRemainListable() {
        val (log, meta) = example()
        val bytes = ReplayRecord.encode(cfg, log, meta)
        val otherBalance = bytes.copyOf().also { it[15] = (it[15] + 1).toByte() }
        val read = ReplayRecord.decode(otherBalance)
        assertFalse(read.playable)
        assertNotNull(read.log)
        assertEquals(meta, read.meta)
        val otherReplay = bytes.copyOf().also { it[11] = (Replay.VERSION + 1).toByte() }
        val future = ReplayRecord.decode(otherReplay)
        assertFalse(future.playable)
        assertNull(future.log)
        assertEquals(meta, future.meta)
    }

    @Test fun metadataLimitsAreCheckedBeforeSerialization() {
        val (log, meta) = example()
        var rejected = false
        try { ReplayRecord.encode(cfg, log, meta.copy(nick = "x".repeat(40))) }
        catch (_: IllegalArgumentException) { rejected = true }
        assertTrue(rejected)
    }
}
