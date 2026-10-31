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
        const val VERSION = SoundSynth.VERSION
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
        const val PURCHASE = SoundSynth.PURCHASE
        const val ERROR = SoundSynth.ERROR
        const val REWARD = SoundSynth.REWARD
        const val RANK_UP = SoundSynth.RANK_UP
        private const val COUNT = SoundSynth.COUNT
        private const val VARIANTS = SoundSynth.VARIANTS
        private const val SAMPLE_COUNT = COUNT * VARIANTS
        private const val MAX_GENERATION_BURST_MS = 750L
        private const val GENERATION_REST_MS = 120L
    }

    private val pool = SoundPool.Builder()
        .setMaxStreams(10)
        .setAudioAttributes(AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
        .build()
    private val ids = AtomicIntegerArray(SAMPLE_COUNT)
    private val ready = AtomicIntegerArray(SAMPLE_COUNT)
    /** 0 = niezamówiony, 1 = zamówiony, 2 = obsłużony, 3 = błąd. */
    private val requested = AtomicIntegerArray(SAMPLE_COUNT)
    private val wake = java.lang.Object()
    private val lastPlay = LongArray(COUNT)
    private val pending = PendingSfx(COUNT)
    @Volatile private var released = false
    @Volatile var enabled = true
    @Volatile var volume = 0.8f
    private var budget = 0f
    private var lastBudgetAt = 0L
    private val variantCounter = IntArray(COUNT)
    private val busyUntil = LongArray(10)
    private var busyCursor = 0

    init {
        pool.setOnLoadCompleteListener { _, sampleId, status ->
            synchronized(this) {
                // load() i wpis ID są pod tym samym zamkiem: callback nie wyprzedzi zapisu.
                if (!released) {
                    for (i in 0 until SAMPLE_COUNT) {
                        if (ids.get(i) != sampleId) continue
                        val soundId = i / VARIANTS
                        if (status == 0) {
                            ready.set(i, 1)
                            val now = SystemClock.uptimeMillis()
                            // Pierwsze zdarzenie odtwarzamy tylko, jeśli nadal jest aktualne.
                            if (pending.pop(soundId, now) && enabled) {
                                playLoaded(soundId, sampleId, pending.volume(soundId), pending.gap(soundId),
                                    pending.pitch(soundId), pending.pan(soundId), pending.priority(soundId), now)
                            }
                        } else {
                            requested.set(i, 3)
                            pending.clear(soundId)
                        }
                        break
                    }
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
        var queued = false
        val offset = id * VARIANTS
        for (variant in 0 until VARIANTS) {
            if (requested.compareAndSet(offset + variant, 0, 1)) queued = true
        }
        if (queued) synchronized(wake) { wake.notifyAll() }
    }

    /** Głośność i panorama są niezależne; wyższy priorytet chroni wybuchy przed trafieniami. */
    @Synchronized
    fun play(
        id: Int, vol: Float = 1f, minGapMs: Long = 45L,
        pitch: Float = 1f, pan: Float = 0f, priority: Int = 1,
    ) {
        if (released || !enabled || id !in 0 until COUNT || !vol.isFinite() ||
            vol <= 0.02f || !pitch.isFinite() || !pan.isFinite()) return
        val now = SystemClock.uptimeMillis()
        val index = id * VARIANTS
        val first = variantCounter[id] % VARIANTS
        var sample = index + first
        var sid = ids.get(sample)
        if (sid <= 0 || ready.get(sample) == 0) {
            sample = index + ((first + 1) % VARIANTS)
            sid = ids.get(sample)
            if (sid <= 0 || ready.get(sample) == 0) {
                pending.offer(id, vol, minGapMs, pitch, pan, priority, now)
                prewarm(id)
                return
            }
        }
        playLoaded(id, sid, vol, minGapMs, pitch, pan, priority, now)
    }

    /** Wywołuj wyłącznie pod zamkiem Sound: także callback ładowania może zagrać próbkę. */
    private fun playLoaded(
        id: Int, sid: Int, vol: Float, minGapMs: Long,
        pitch: Float, pan: Float, priority: Int, now: Long,
    ) {
        if (released || !enabled || now - lastPlay[id] < minGapMs) return
        lastPlay[id] = now
        variantCounter[id] = (variantCounter[id] + 1) and Int.MAX_VALUE

        // Krótkie okno obciążeń miksu: tłumimy drobne dźwięki zamiast ścinać wybuchy.
        budget = (budget - (now - lastBudgetAt).coerceIn(0L, 500L) * 0.02f).coerceAtLeast(0f)
        lastBudgetAt = now
        val quiet = if (priority >= 3) 1f else (1f - budget / 3.4f).coerceIn(0.26f, 1f)
        // Oszacowanie głosów już wybrzmiewających; nowy głos oddaje im zapas miksu.
        var overlapping = 0
        for (until in busyUntil) if (until > now) overlapping++
        val gain = (vol * volume * quiet * 0.42f / (1f + 0.55f * overlapping)).coerceIn(0f, 0.38f)
        if (gain < 0.01f) return
        budget = (budget + gain * (if (priority >= 3) 0.65f else 1f)).coerceAtMost(5f)
        val jitter = 1f + ((variantCounter[id] % 5) - 2) * 0.006f
        val lr = pan.coerceIn(-1f, 1f)
        val left = gain * (1f - lr.coerceAtLeast(0f))
        val right = gain * (1f + lr.coerceAtMost(0f))
        val speed = (pitch * jitter).coerceIn(0.5f, 2f)
        busyUntil[busyCursor] = now + (SoundSynth.duration(id) * 1_000f / speed).toLong()
        busyCursor = (busyCursor + 1) % busyUntil.size
        pool.play(sid, left, right, priority, 0, speed)
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
            var burstStart = SystemClock.elapsedRealtime()
            while (!released) {
                var next = -1
                synchronized(wake) {
                    for (i in 0 until SAMPLE_COUNT) if (requested.compareAndSet(i, 1, 2)) {
                        next = i
                        break
                    }
                    if (next < 0 && !released) wake.wait(4_000L)
                }
                if (next < 0) continue
                try {
                    val effect = next / VARIANTS
                    val variant = next % VARIANTS
                    val f = File(dir, "s${effect}_${variant}_v${SoundSynth.VERSION}.wav")
                    if (!f.isFile || f.length() != SoundSynth.wavBytes(effect).toLong()) {
                        writeWav(f, SoundSynth.render(effect, variant))
                    }
                    synchronized(this) {
                        if (released) return
                        val sid = pool.load(f.absolutePath, 1)
                        if (sid > 0) ids.set(next, sid)
                        else {
                            requested.set(next, 3)
                            pending.clear(next)
                        }
                    }
                } catch (e: Exception) {
                    synchronized(this) {
                        requested.set(next, 3)
                        pending.clear(next)
                    }
                    android.util.Log.w("CenturyWar", "sfx sample $next failed", e)
                }
                // Ograniczamy ciągłe obciążenie CPU przez generowanie na tanich telefonach.
                if (SystemClock.elapsedRealtime() - burstStart > MAX_GENERATION_BURST_MS) {
                    Thread.sleep(GENERATION_REST_MS)
                    burstStart = SystemClock.elapsedRealtime()
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
