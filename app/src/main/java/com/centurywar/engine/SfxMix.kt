package com.centurywar.engine

/** Prognoza sumy szczytów PCM i miękka kompresja głosów SoundPool. */
internal class SfxMix(val capacity: Int = 10, seed: Int = 0x13579BDF) {
    companion object {
        const val SAMPLE_PEAK = 0.76f
        const val KNEE = 0.52f
        const val CEILING = 0.90f
    }

    private val ends = LongArray(capacity)
    private val streams = IntArray(capacity)
    private val gains = FloatArray(capacity)
    private val pans = FloatArray(capacity)
    private val priorities = IntArray(capacity)
    private var state = if (seed == 0) 0x13579BDF else seed
    private var compression = 1f
    var displacedStream = 0
        private set

    init { require(capacity > 0) }

    /** Rezerwuje miejsce bez alokacji; zwraca -1, gdy wszystkie głosy są ważniejsze. */
    fun reserve(now: Long, durationMs: Long, gain: Float, pan: Float, priority: Int): Int {
        var free = -1
        var victim = -1
        for (i in 0 until capacity) {
            if (ends[i] != 0L && ends[i] <= now) {
                ends[i] = 0L
                streams[i] = 0
                gains[i] = 0f
            }
            if (ends[i] == 0L) {
                if (free < 0) free = i
            } else if (priorities[i] <= priority && (victim < 0 ||
                    priorities[i] < priorities[victim] ||
                    (priorities[i] == priorities[victim] && ends[i] < ends[victim]))) {
                victim = i
            }
        }
        val slot = if (free >= 0) free else victim
        if (slot < 0) {
            displacedStream = 0
            recompute()
            return -1
        }
        displacedStream = streams[slot]
        streams[slot] = 0
        gains[slot] = gain.coerceIn(0f, 1f)
        pans[slot] = pan.coerceIn(-1f, 1f)
        priorities[slot] = priority
        ends[slot] = now + durationMs.coerceAtLeast(1L)
        recompute()
        return slot
    }

    fun attach(slot: Int, streamId: Int) { streams[slot] = streamId }
    fun cancel(slot: Int) {
        streams[slot] = 0
        ends[slot] = 0L
        gains[slot] = 0f
        recompute()
    }

    fun stream(slot: Int): Int = streams[slot]
    fun left(slot: Int): Float = gains[slot] * compression * (1f - pans[slot].coerceAtLeast(0f))
    fun right(slot: Int): Float = gains[slot] * compression * (1f + pans[slot].coerceAtMost(0f))

    /** Kolejne wywołania nie korzystają z RNG symulacji ani nie tworzą obiektów. */
    fun pitchJitter(): Float {
        var x = state
        x = x xor (x shl 13)
        x = x xor (x ushr 17)
        x = x xor (x shl 5)
        state = x
        return 0.984f + ((x ushr 16) and 0xffff) * (0.032f / 65535f)
    }

    private fun recompute() {
        var total = 0f
        for (i in 0 until capacity) if (ends[i] != 0L) total += gains[i] * SAMPLE_PEAK
        if (total <= KNEE) {
            compression = 1f
        } else {
            val range = CEILING - KNEE
            val excess = total - KNEE
            val limited = KNEE + range * excess / (range + excess)
            compression = limited / total
        }
    }
}

/** Limity wspólne dla odmian tego samego zdarzenia, bez blokady odległych efektów UI. */
internal class SfxRateGate(count: Int) {
    private val lastSound = LongArray(count) { -1_000_000_000L }
    private val lastFamily = LongArray(4) { -1_000_000_000L }

    fun allowed(id: Int, now: Long, gapMs: Long): Boolean {
        if (now - lastSound[id] < gapMs.coerceAtLeast(0L)) return false
        val family = family(id)
        if (family < 0) return true
        val gap = when (family) { 0 -> 22L; 1 -> 28L; 2 -> 75L; else -> 95L }
        return now - lastFamily[family] >= gap
    }

    fun played(id: Int, now: Long) {
        lastSound[id] = now
        val family = family(id)
        if (family >= 0) lastFamily[family] = now
    }

    private fun family(id: Int): Int = when (id) {
        SoundSynth.HIT, in SoundSynth.HIT_WOOD..SoundSynth.HIT_ENERGY -> 0
        SoundSynth.SHOT, SoundSynth.ARROW, SoundSynth.LASER,
        in SoundSynth.SHOT_SLING..SoundSynth.SHOT_PLASMA,
        in SoundSynth.TURRET_PRIMITIVE..SoundSynth.TURRET_ENERGY -> 1
        SoundSynth.DEATH, in SoundSynth.DEATH_HUMAN..SoundSynth.DEATH_MACHINE -> 2
        SoundSynth.EXPLOSION,
        in SoundSynth.BARRAGE_STONE..SoundSynth.BARRAGE_ORBITAL -> 3
        else -> -1
    }
}
