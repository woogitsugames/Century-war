package com.centurywar.game

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.zip.CRC32

/** Samodzielny plik powtórki, niezależny od zapisu przerwanej bitwy. */
object ReplayRecord {
    const val VERSION = 1
    private const val MAGIC = 0x43575231 // CWR1
    private const val MAX_BYTES = 1 shl 20
    private const val MAX_LOG_BYTES = 600_000

    data class Meta(
        val mode: Mode,
        val stage: Int,
        val winner: Int,
        val score: Long,
        val time: Float,
        val wave: Int,
        val dateMillis: Long,
        val nick: String,
    )

    data class Record(
        val cfg: BattleConfig,
        val log: Replay.Log?,
        val meta: Meta,
        val replayVersion: Int,
        val fingerprint: Int,
        val playable: Boolean,
    )

    /** Data podawana przez aplikację; sim nie korzysta z zegara systemowego. */
    fun encode(cfg: BattleConfig, log: Replay.Log, meta: Meta): ByteArray {
        require(meta.mode == cfg.mode && meta.stage == cfg.stageIndex)
        validateMeta(meta)
        val commandBytes = Replay.encode(log).toByteArray(Charsets.US_ASCII)
        require(commandBytes.size <= MAX_LOG_BYTES)
        val payload = ByteArrayOutputStream(commandBytes.size + 160)
        DataOutputStream(payload).use { o ->
            BattleSnapshot.writeConfig(o, cfg)
            o.writeInt(commandBytes.size)
            o.write(commandBytes)
            o.writeByte(meta.mode.ordinal)
            o.writeInt(meta.stage)
            o.writeInt(meta.winner)
            o.writeLong(meta.score)
            o.writeFloat(meta.time)
            o.writeInt(meta.wave)
            o.writeLong(meta.dateMillis)
            o.writeUTF(meta.nick)
        }
        val data = payload.toByteArray()
        require(data.size + 24 <= MAX_BYTES)
        val crc = CRC32().apply { update(data) }.value.toInt()
        return ByteArrayOutputStream(data.size + 24).also { bytes ->
            DataOutputStream(bytes).use { o ->
                o.writeInt(MAGIC)
                o.writeInt(VERSION)
                o.writeInt(Replay.VERSION)
                o.writeInt(BattleSnapshot.defsFingerprint)
                o.writeInt(data.size)
                o.writeInt(crc)
                o.write(data)
            }
        }.toByteArray()
    }

    /** Nieaktualny balans lub Replay.VERSION nie usuwa metadanych z listy. */
    fun decode(bytes: ByteArray): Record {
        require(bytes.size in 24..MAX_BYTES) { "Nieprawidłowy rozmiar powtórki" }
        return try {
            val h = DataInputStream(ByteArrayInputStream(bytes))
            require(h.readInt() == MAGIC && h.readInt() == VERSION) { "Nieobsługiwany format powtórki" }
            val replayVersion = h.readInt()
            val fingerprint = h.readInt()
            val length = h.readInt()
            val crc = h.readInt()
            require(replayVersion > 0 && length == bytes.size - 24) { "Uszkodzony nagłówek powtórki" }
            require(CRC32().apply { update(bytes, 24, length) }.value.toInt() == crc) { "Niepoprawny CRC" }
            val input = DataInputStream(ByteArrayInputStream(bytes, 24, length))
            val cfg = requireNotNull(BattleSnapshot.readConfig(input)) { "Niepoprawna konfiguracja" }
            val logLength = input.readInt()
            require(logLength in 1..MAX_LOG_BYTES && logLength <= input.available()) { "Niepoprawna długość dziennika" }
            val logBytes = ByteArray(logLength)
            input.readFully(logBytes)
            val mode = Mode.values().getOrNull(input.readUnsignedByte())
                ?: throw IllegalArgumentException("Nieznany tryb")
            val meta = Meta(
                mode, input.readInt(), input.readInt(), input.readLong(),
                input.readFloat(), input.readInt(), input.readLong(), input.readUTF(),
            )
            validateMeta(meta)
            require(meta.mode == cfg.mode && meta.stage == cfg.stageIndex)
            require(input.available() == 0) { "Nadmiarowe dane powtórki" }
            val log = if (replayVersion == Replay.VERSION)
                Replay.decode(String(logBytes, Charsets.US_ASCII)) else null
            Record(cfg, log, meta, replayVersion, fingerprint,
                log != null && fingerprint == BattleSnapshot.defsFingerprint)
        } catch (e: IllegalArgumentException) {
            throw e
        } catch (e: Exception) {
            throw IllegalArgumentException("Uszkodzony zapis powtórki", e)
        }
    }

    private fun validateMeta(m: Meta) {
        require(m.stage in -1..10_000 && m.winner in -1..1 &&
            m.time.isFinite() && m.time in 0f..(Replay.MAX_STEPS * Battle.STEP) &&
            m.wave in 0..1_000_000 && m.dateMillis >= 0L &&
            m.nick.length <= 32 && m.nick.none { it.isISOControl() }) {
            "Nieprawidłowe metadane powtórki"
        }
    }
}
