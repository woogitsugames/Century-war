package com.centurywar.engine

import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.log10
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** „Oddech” muzyki bitwy: harmonogram, wstrzymania i płynność (zegar wstrzykiwany krokiem). */
class MusicBreathTest {
    private val dt = 0.04f

    private class Breath(val start: Float, val end: Float, val minDb: Float)

    /** Przebieg [seconds] s z [weight](t) i [blocked](t); zwraca oddechy i największy skok dB na krok. */
    private fun simulate(b: MusicBreath, seconds: Float, active: Boolean = true,
                         weight: (Float) -> Float = { 0f }, blocked: (Float) -> Boolean = { false }):
        Pair<List<Breath>, Float> {
        val out = ArrayList<Breath>()
        var t = 0f
        var prevDb = 20f * log10(b.gain)
        var maxJump = 0f
        var start = -1f
        var minDb = 0f
        val steps = (seconds / dt).toInt()
        repeat(steps) {
            b.update(dt, active, weight(t), blocked(t))
            t += dt
            val db = 20f * log10(b.gain)
            maxJump = maxOf(maxJump, abs(db - prevDb))
            prevDb = db
            if (db < -0.1f) {
                if (start < 0f) { start = t; minDb = 0f }
                minDb = minOf(minDb, db)
            } else if (start >= 0f) {
                out += Breath(start, t, minDb); start = -1f
            }
        }
        return out to maxJump
    }

    @Test fun scheduleEveryTwoAndHalfMinutesForTwentyToFortySeconds() {
        val (breaths, jump) = simulate(MusicBreath(), 15 * 60f)
        assertTrue("kilka oddechów w 15 min: ${breaths.size}", breaths.size in 4..6)
        assertEquals("pierwszy po ok. 2,5 min", 150f, breaths[0].start, 1f)
        for (i in breaths.indices) {
            val b = breaths[i]
            assertTrue("czas oddechu ${b.end - b.start}", b.end - b.start in 20f..40f)
            assertTrue("głębokość ${b.minDb}", -b.minDb in 12f..18f)
            if (i > 0) {
                val gap = b.start - breaths[i - 1].end
                assertTrue("pełna muzyka między oddechami: $gap", gap in 140f..160f)
            }
        }
        // Płynność: najwyżej 0,3 dB na krok 40 ms (kosinusowe S w dB, bez trzasków).
        assertTrue("skok $jump dB", jump < 0.3f)
    }

    @Test fun fadesLastAtLeastTwoSeconds() {
        val b = MusicBreath()
        simulate(b, 150f)
        // Od pierwszego zejścia do -1 dB i do dna mija ≥ 2 s; tak samo powrót.
        var t = 0f
        var at1 = -1f
        var atBottom = -1f
        while (t < 10f) {
            b.update(dt, true, 0f, false); t += dt
            val db = 20f * log10(b.gain)
            if (at1 < 0f && db < -1f) at1 = t
            if (atBottom < 0f && db <= MusicBreath.DEPTH_DB + 0.05f) atBottom = t
        }
        assertTrue("zejście $at1..$atBottom", atBottom - at1 >= 2f)
        assertTrue(MusicBreath.FADE_S >= 2f)
    }

    @Test fun strongBattlePostponesBreathByFifteenSeconds() {
        val b = MusicBreath()
        // Silne starcie od 140 do 160 s: oddech czeka, rusza 15 s po terminie (pierwsze sprawdzenie bez walki).
        val (breaths, _) = simulate(b, 200f, weight = { t -> if (t in 140f..160f) 1f else 0f })
        assertEquals(1, breaths.size)
        assertEquals("przesunięty o kilkanaście sekund", 165f, breaths[0].start, 0.5f)
    }

    @Test fun strongBattleDuringBreathBringsMusicBack() {
        val b = MusicBreath()
        simulate(b, 160f)
        assertTrue(b.breathing)
        val (_, jump) = simulate(b, 6f, weight = { 1f })
        assertEquals("muzyka wraca przy silnym starciu", 1f, b.gain, 1e-4f)
        assertTrue(jump < 0.3f)
    }

    @Test fun noBreathDuringResultStingerAndIntervalRestartsAfterIt() {
        val b = MusicBreath()
        // Przerywnik od 140 do 175 s: w tym czasie nic, potem pełne 150 s grania.
        val (breaths, _) = simulate(b, 400f, blocked = { t -> t in 140f..175f })
        assertTrue(breaths.none { it.start in 140f..175f })
        assertEquals(175f + 150f, breaths[0].start, 0.5f)
    }

    @Test fun stingerDuringBreathReturnsSmoothly() {
        val b = MusicBreath()
        simulate(b, 165f)
        assertTrue(b.gain < 0.3f)
        val (_, jump) = simulate(b, 5f, blocked = { true })
        assertEquals(1f, b.gain, 1e-4f)
        assertTrue(jump < 0.3f)
    }

    @Test fun menuNeverBreathes() {
        val b = MusicBreath()
        val (breaths, _) = simulate(b, 20 * 60f, active = false)
        assertTrue(breaths.isEmpty())
        assertEquals(1f, b.gain, 0f)
    }

    // --- Mikser nagrań ---

    private class Fake(val name: String) : TrackPlayer {
        var level = -1f
        var released = false
        var finished = false
        override fun start() {}
        override fun setVolume(value: Float) { level = value }
        override fun release() { released = true }
        override val isPlaying: Boolean get() = !released && !finished
    }

    private class Factory : TrackPlayerFactory {
        val all = ArrayList<Fake>()
        override fun open(name: String, looping: Boolean): TrackPlayer = Fake(name).also { all += it }
        fun live(name: String) = all.lastOrNull { it.name == name && !it.released }
    }

    private fun catalog(vararg names: String) = MusicCatalog.fromFileNames(names.map { "$it.ogg" }.toTypedArray())

    /** Krok miksu; zwraca największy względny skok głośności pętli na krok. */
    private fun RecordedMixer.run(f: Factory, seconds: Float, target: Int, intensity: Float = 0f,
                                  duck: Float = 1f, track: String = "era1_calm"): Float {
        var t = 0f
        var maxJump = 0f
        var prev = f.live(track)?.level ?: -1f
        while (t < seconds - 1e-4f) {
            update(dt, target, intensity, 1f, duck); t += dt
            val now = f.live(track)?.level ?: -1f
            if (prev > 0.05f && now > 0f) maxJump = maxOf(maxJump, abs(20f * log10(now / prev)))
            prev = now
        }
        return maxJump
    }

    @Test fun mixerBreathesInBattleOnly() {
        val f = Factory()
        val m = RecordedMixer(catalog("menu", "era1_calm"), f)
        m.run(f, 149f, 1) // wejście z ciszy (START_FADE_S) nie jest oddechem
        assertEquals("pełna głośność przed oddechem", 1f, f.live("era1_calm")!!.level, 1e-3f)
        var jump = m.run(f, 12f, 1)
        val db = 20f * log10(f.live("era1_calm")!!.level)
        assertEquals("pętla w tle", MusicBreath.DEPTH_DB, db, 0.5f)
        jump = maxOf(jump, m.run(f, 30f, 1))
        assertEquals("płynny powrót", 1f, f.live("era1_calm")!!.level, 1e-3f)
        assertTrue("skok $jump dB na klatkę", jump < 0.3f)
        // Menu: 10 min bez oddechu.
        val menu = RecordedMixer(catalog("menu"), f)
        menu.run(f, 600f, Music.MENU, track = "menu")
        assertEquals(1f, f.live("menu")!!.level, 1e-4f)
        assertTrue(!menu.breath.breathing)
    }

    @Test fun mixerPostponesOnHighIntensity() {
        val f = Factory()
        val m = RecordedMixer(catalog("era1_calm", "era1_battle"), f)
        m.run(f, 140f, 1)
        m.run(f, 30f, 1, intensity = 1f)
        assertTrue("silne starcie: bez oddechu", !m.breath.breathing)
        // Starcie mija: miks schodzi z histerezą, oddech rusza w ciągu kilkunastu-kilkudziesięciu sekund.
        var t = 0f
        while (!m.breath.breathing && t < 60f) { m.update(dt, 1, 0f, 1f, 1f); t += dt }
        assertTrue("oddech po starciu: $t s", m.breath.breathing && t in 5f..40f)
    }

    @Test fun mixerSkipsBreathAroundStinger() {
        val f = Factory()
        val m = RecordedMixer(catalog("era1_calm", "victory"), f)
        m.run(f, 145f, 1)
        assertTrue(m.playStinger(true))
        m.run(f, 20f, 1)
        assertTrue("w trakcie przerywnika bez oddechu", !m.breath.breathing)
        f.live("victory")!!.finished = true
        m.run(f, 10f, 1)
        assertEquals("pętla wróciła w pełni", 1f, f.live("era1_calm")!!.level, 1e-3f)
        assertTrue("zegar od nowa", m.breath.untilNext > 140f)
    }

    @Test fun duckAndBreathDoNotStack() {
        val f = Factory()
        val m = RecordedMixer(catalog("era1_calm"), f)
        m.run(f, 160f, 1)
        val breathLevel = f.live("era1_calm")!!.level
        // Sygnał płytszy niż oddech: bez zmiany (głębsze z dwóch).
        m.run(f, 1f, 1, duck = 0.5f)
        assertEquals(breathLevel, f.live("era1_calm")!!.level, 1e-3f)
        // Sygnał głębszy niż oddech: obowiązuje sygnał, nie iloczyn.
        m.run(f, 1f, 1, duck = 0.05f)
        assertEquals(0.05f, f.live("era1_calm")!!.level, 0.01f)
        // Zegar oddechu nie zależy od sygnałów: koniec jak bez nich.
        val ref = RecordedMixer(catalog("era1_calm"), Factory())
        repeat((162f / dt).toInt()) { ref.update(dt, 1, 0f, 1f, 1f) }
        assertEquals(ref.breath.gain, m.breath.gain, 1e-3f)
    }

    @Test fun pauseAndYieldFreezeTheClock() {
        // Pauza i ustąpienie cudzej muzyce zatrzymują wątek nagrań (releaseAll, brak kroków);
        // stan oddechu należy do RecordedMusic, więc nowy mikser liczy dalej, a nie od nowa.
        val shared = MusicBreath()
        val f = Factory()
        val a = RecordedMixer(catalog("era1_calm"), f, breath = shared)
        a.run(f, 100f, 1)
        a.releaseAll()
        assertEquals(50f, shared.untilNext, 0.2f)
        val b = RecordedMixer(catalog("era1_calm"), f, breath = shared)
        b.run(f, 49f, 1)
        assertTrue(!shared.breathing)
        b.run(f, 2f, 1)
        assertTrue("oddech po łącznie 150 s grania", shared.breathing)
    }

    /** Odsłuch: MUSIC_PREVIEW=true, plik w app/build/music-preview (4 min, oddech co 60 s). */
    @Test fun exportBreathPreview() {
        assumeTrue("Eksport tylko przy MUSIC_PREVIEW=true", System.getenv("MUSIC_PREVIEW") == "true")
        val dir = File("build/music-preview")
        assertTrue(dir.isDirectory || dir.mkdirs())
        val rate = MusicSynth.SAMPLE_RATE
        val seconds = 240
        val synth = MusicSynth(5503)
        synth.setEra(3)
        synth.skipFade()
        val breath = MusicBreath(intervalS = 50f, holdS = 18f)
        val block = ShortArray(2048)
        val chunkS = block.size.toFloat() / rate
        val total = rate * seconds
        BufferedOutputStream(FileOutputStream(File(dir, "breath_feudal_4min.wav"))).use { out ->
            val h = java.nio.ByteBuffer.allocate(44).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            h.put("RIFF".toByteArray()).putInt(36 + total * 2).put("WAVEfmt ".toByteArray())
                .putInt(16).putShort(1).putShort(1).putInt(rate).putInt(rate * 2).putShort(2).putShort(16)
                .put("data".toByteArray()).putInt(total * 2)
            out.write(h.array())
            val bytes = ByteArray(block.size * 2)
            var left = total
            var t = 0f
            while (left > 0) {
                // Starcie narasta w środku (oddech przesuwa się), reszta spokojniej.
                val intensity = if (t in 95f..125f) 0.95f else 0.45f
                synth.setIntensity(intensity)
                breath.update(chunkS, true, RecordedMixer.battleWeight(intensity), false)
                synth.renderPcm(block, block.size, 0.8f * breath.gain)
                val n = minOf(left, block.size)
                for (i in 0 until n) {
                    bytes[i * 2] = block[i].toInt().toByte()
                    bytes[i * 2 + 1] = (block[i].toInt() shr 8).toByte()
                }
                out.write(bytes, 0, n * 2)
                left -= n
                t += chunkS
            }
        }
    }
}
