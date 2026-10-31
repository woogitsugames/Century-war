package com.centurywar.engine

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.SystemClock
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicIntegerArray

/**
 * Niskolatencyjne efekty SoundPool. Synteza, zapis WAV i ładowanie próbek odbywają się
 * wyłącznie na wątku roboczym; play() nie wykonuje I/O i nie tworzy obiektów.
 */
class Sound(private val ctx: Context) {
    companion object {
        const val HIT = SoundSynth.HIT
        const val SHOT = SoundSynth.SHOT
        const val ARROW = SoundSynth.ARROW
        const val EXPLOSION = SoundSynth.EXPLOSION
        const val DEATH = SoundSynth.DEATH
        const val LASER = SoundSynth.LASER
        const val EVOLVE = SoundSynth.EVOLVE
        const val COIN = SoundSynth.COIN
        const val CLICK = SoundSynth.CLICK
        const val VICTORY = SoundSynth.VICTORY
        const val DEFEAT = SoundSynth.DEFEAT
        const val THUMP = SoundSynth.THUMP
        const val HIT_WOOD = SoundSynth.HIT_WOOD
        const val HIT_BRONZE = SoundSynth.HIT_BRONZE
        const val HIT_STEEL = SoundSynth.HIT_STEEL
        const val HIT_ENERGY = SoundSynth.HIT_ENERGY
        const val DEATH_HUMAN = SoundSynth.DEATH_HUMAN
        const val DEATH_BEAST = SoundSynth.DEATH_BEAST
        const val DEATH_MACHINE = SoundSynth.DEATH_MACHINE
        const val SHOT_SLING = SoundSynth.SHOT_SLING
        const val SHOT_BOW = SoundSynth.SHOT_BOW
        const val SHOT_CROSSBOW = SoundSynth.SHOT_CROSSBOW
        const val SHOT_MUSKET = SoundSynth.SHOT_MUSKET
        const val SHOT_CANNON = SoundSynth.SHOT_CANNON
        const val SHOT_RIFLE = SoundSynth.SHOT_RIFLE
        const val SHOT_GUN = SoundSynth.SHOT_GUN
        const val SHOT_LASER = SoundSynth.SHOT_LASER
        const val SHOT_PLASMA = SoundSynth.SHOT_PLASMA
        const val TURRET_PRIMITIVE = SoundSynth.TURRET_PRIMITIVE
        const val TURRET_MEDIEVAL = SoundSynth.TURRET_MEDIEVAL
        const val TURRET_POWDER = SoundSynth.TURRET_POWDER
        const val TURRET_MODERN = SoundSynth.TURRET_MODERN
        const val TURRET_ENERGY = SoundSynth.TURRET_ENERGY
        const val BARRAGE_STONE = SoundSynth.BARRAGE_STONE
        const val BARRAGE_FIRE = SoundSynth.BARRAGE_FIRE
        const val BARRAGE_CANNON = SoundSynth.BARRAGE_CANNON
        const val BARRAGE_ROCKET = SoundSynth.BARRAGE_ROCKET
        const val BARRAGE_ORBITAL = SoundSynth.BARRAGE_ORBITAL
        const val EVOLVE_PRIMITIVE = SoundSynth.EVOLVE_PRIMITIVE
        const val EVOLVE_MEDIEVAL = SoundSynth.EVOLVE_MEDIEVAL
        const val EVOLVE_INDUSTRIAL = SoundSynth.EVOLVE_INDUSTRIAL
        const val EVOLVE_FUTURE = SoundSynth.EVOLVE_FUTURE
        const val MEDALS = SoundSynth.MEDALS
        const val ACHIEVEMENT = SoundSynth.ACHIEVEMENT
        const val CARD_COMMON = SoundSynth.CARD_COMMON
        const val CARD_RARE = SoundSynth.CARD_RARE
        const val CARD_EPIC = SoundSynth.CARD_EPIC
        const val CARD_LEGENDARY = SoundSynth.CARD_LEGENDARY
        const val RESEARCH = SoundSynth.RESEARCH
        const val STAR = SoundSynth.STAR
        const val ERA_UNLOCK = SoundSynth.ERA_UNLOCK
        const val BOSS = SoundSynth.BOSS
        private const val COUNT = SoundSynth.COUNT
    }

    private val pool = SoundPool.Builder()
        .setMaxStreams(10)
        .setAudioAttributes(AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
        .build()
    private val ids = AtomicIntegerArray(COUNT)
    private val ready = AtomicIntegerArray(COUNT)
    /** 0 = niezamówiony, 1 = zamówiony, 2 = obsłużony, 3 = błąd. */
    private val requested = AtomicIntegerArray(COUNT)
    private val wake = java.lang.Object()
    private val lastPlay = LongArray(COUNT)
    @Volatile private var released = false
    @Volatile var enabled = true
    @Volatile var volume = 0.8f
    private var budget = 0f
    private var lastBudgetAt = 0L
    private var pitchState = 0x41C64E6D

    init {
        pool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0 && !released) {
                for (i in 0 until COUNT) if (ids.get(i) == sampleId) {
                    ready.set(i, 1)
                    break
                }
            }
        }
        Thread({ worker() }, "sfx-gen").apply { priority = Thread.MIN_PRIORITY; isDaemon = true; start() }
        // Minimalne UI gotowe jeszcze przed pierwszym uruchomieniem bitwy.
        prewarm(CLICK); prewarm(COIN); prewarm(VICTORY); prewarm(DEFEAT)
        prewarm(MEDALS); prewarm(ACHIEVEMENT); prewarm(RESEARCH); prewarm(STAR)
    }

    /** Zamawia próbkę bez blokowania wątku gry syntezą lub plikami. */
    fun prewarm(id: Int) {
        if (released || id !in 0 until COUNT) return
        if (requested.compareAndSet(id, 0, 1)) synchronized(wake) { wake.notifyAll() }
    }

    /** Głośność i panorama są niezależne; wyższy priorytet chroni wybuchy przed trafieniami. */
    fun play(
        id: Int, vol: Float = 1f, minGapMs: Long = 45L,
        pitch: Float = 1f, pan: Float = 0f, priority: Int = 1,
    ) {
        if (released || !enabled || id !in 0 until COUNT || !vol.isFinite() || vol <= 0.02f) return
        val sid = ids.get(id)
        if (sid <= 0 || ready.get(id) == 0) {
            prewarm(id)
            return
        }
        val now = SystemClock.uptimeMillis()
        if (now - lastPlay[id] < minGapMs) return
        lastPlay[id] = now

        // Krótkie okno obciążeń miksu: tłumiemy drobne dźwięki zamiast ścinać wybuchy.
        budget = (budget - (now - lastBudgetAt).coerceIn(0L, 500L) * 0.02f).coerceAtLeast(0f)
        lastBudgetAt = now
        val quiet = if (priority >= 3) 1f else (1f - budget / 3.4f).coerceIn(0.26f, 1f)
        val gain = (vol * volume * quiet).coerceIn(0f, 0.9f)
        if (gain < 0.01f) return
        budget = (budget + gain * if (priority >= 3) 0.65f else 1f).coerceAtMost(5f)
        pitchState = pitchState * 1664525 + 1013904223
        val jitter = 1f + ((pitchState ushr 24) / 255f - 0.5f) * 0.1f
        val lr = pan.coerceIn(-1f, 1f)
        val left = gain * (1f - lr.coerceAtLeast(0f))
        val right = gain * (1f + lr.coerceAtMost(0f))
        pool.play(sid, left, right, priority, 0, (pitch * jitter).coerceIn(0.5f, 2f))
    }

    fun release() {
        synchronized(this) {
            if (released) return
            released = true
            pool.release()
        }
        synchronized(wake) { wake.notifyAll() }
    }

    private fun worker() {
        try {
            val dir = File(ctx.cacheDir, "sfx")
            if (!dir.exists() && !dir.mkdirs()) return
            // Cache starszej wersji nie zużywa miejsca po wprowadzeniu nowych banków.
            dir.listFiles()?.forEach {
                if (it.name.endsWith(".wav") && !it.name.endsWith("_v${SoundSynth.VERSION}.wav")) it.delete()
            }
            if (SoundSynth.totalWavBytes() > SoundSynth.MAX_CACHE_BYTES) return
            while (!released) {
                var next = -1
                synchronized(wake) {
                    for (i in 0 until COUNT) if (requested.compareAndSet(i, 1, 2)) {
                        next = i
                        break
                    }
                    if (next < 0 && !released) wake.wait(4_000L)
                }
                if (next < 0) continue
                try {
                    val f = File(dir, "s${next}_v${SoundSynth.VERSION}.wav")
                    if (!f.isFile || f.length() != SoundSynth.wavBytes(next).toLong()) {
                        writeWav(f, SoundSynth.render(next))
                    }
                    synchronized(this) {
                        if (released) return
                        ids.set(next, pool.load(f.absolutePath, 1))
                    }
                } catch (e: Exception) {
                    requested.set(next, 3)
                    android.util.Log.w("CenturyWar", "sfx sample $next failed", e)
                }
            }
        } catch (_: InterruptedException) {
            // release(): koniec pracy.
        } catch (e: Exception) {
            android.util.Log.w("CenturyWar", "sfx generator failed", e)
        }
    }

    private fun writeWav(f: File, data: FloatArray) {
        val bytes = data.size * 2
        val b = ByteBuffer.allocate(44 + bytes).order(ByteOrder.LITTLE_ENDIAN)
        b.put("RIFF".toByteArray(Charsets.US_ASCII))
        b.putInt(36 + bytes)
        b.put("WAVEfmt ".toByteArray(Charsets.US_ASCII))
        b.putInt(16)
        b.putShort(1); b.putShort(1)
        b.putInt(SoundSynth.SAMPLE_RATE); b.putInt(SoundSynth.SAMPLE_RATE * 2)
        b.putShort(2); b.putShort(16)
        b.put("data".toByteArray(Charsets.US_ASCII)); b.putInt(bytes)
        for (v in data) b.putShort((v.coerceIn(-1f, 1f) * 32767f).toInt().toShort())
        val tmp = File(f.parentFile, f.name + ".tmp")
        FileOutputStream(tmp).use { it.write(b.array()) }
        if (!tmp.renameTo(f)) {
            f.delete()
            if (!tmp.renameTo(f)) throw java.io.IOException("Nie udało się zapisać ${f.name}")
        }
    }
}
