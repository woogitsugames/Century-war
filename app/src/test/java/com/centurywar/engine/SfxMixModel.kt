package com.centurywar.engine

/**
 * D63: model [Sound.play] do pomiaru miksu w JVM — ta sama bramka odstępów ([SfxRateGate]),
 * budżet „quiet”, wzmocnienie, jitter wysokości i limiter głosów ([SfxMix]); SoundPool zastępuje
 * miksowanie stereo z liniowym przepróbkowaniem. Zegar ([now]) ustawia test.
 */
internal class SfxMixModel(private val rate: Int, seconds: Int, private val volume: Float) : SfxSink {
    val left = FloatArray(rate * seconds)
    val right = FloatArray(rate * seconds)
    var now = 0L
    var played = 0
    var rejected = 0
    private val cache = HashMap<Int, FloatArray>()
    private val gate = SfxRateGate(SoundSynth.COUNT)
    private val mixer = SfxMix(10, 12345)
    private val variantCounter = IntArray(SoundSynth.COUNT)
    private val counts = IntArray(SoundSynth.COUNT)
    private var budget = 0f
    private var lastBudgetAt = 0L
    private val voiceData = arrayOfNulls<FloatArray>(mixer.capacity)
    private val voicePos = DoubleArray(mixer.capacity)
    private val voiceSpeed = DoubleArray(mixer.capacity)
    private var cursor = 0
    private var nextStream = 1

    private fun sample(id: Int, v: Int) = cache.getOrPut(id * 4 + v) { SoundSynth.render(id, v) }

    override fun sfx(id: Int, vol: Float, gap: Long, pitch: Float, pan: Float, priority: Int) {
        if (id !in 0 until SoundSynth.COUNT || vol <= 0.02f) return
        if (!gate.allowed(id, now, gap)) { rejected++; return }
        budget = (budget - (now - lastBudgetAt).coerceIn(0L, 500L) * 0.02f).coerceAtLeast(0f)
        lastBudgetAt = now
        val quiet = if (priority >= 3) 1f else (1f - budget / 3.4f).coerceIn(0.26f, 1f)
        val gain = (vol * volume * quiet * 0.42f).coerceIn(0f, 0.38f)
        if (gain < 0.01f) return
        val speed = (pitch * mixer.pitchJitter()).coerceIn(0.5f, 2f)
        val duration = (SoundSynth.duration(id) * 1_000f / speed).toLong().coerceAtLeast(1L)
        val slot = mixer.reserve(now, duration, gain, pan, priority)
        if (slot < 0) { rejected++; return }
        mixer.attach(slot, nextStream++)
        voiceData[slot] = sample(id, variantCounter[id] % SoundSynth.VARIANTS)
        voicePos[slot] = 0.0
        voiceSpeed[slot] = speed.toDouble()
        gate.played(id, now)
        variantCounter[id]++
        counts[id]++
        played++
        budget = (budget + gain * (if (priority >= 3) 0.65f else 1f)).coerceAtMost(5f)
    }

    /** Miksuje do końca klatki [frame] (przy [fps]) z bieżącymi wzmocnieniami głosów. */
    fun renderUntil(frame: Int, fps: Int) {
        val end = minOf(left.size, (frame.toLong() * rate / fps).toInt())
        for (s in 0 until mixer.capacity) {
            val data = voiceData[s] ?: continue
            val l = mixer.left(s)
            val r = mixer.right(s)
            var pos = voicePos[s]
            val sp = voiceSpeed[s]
            var i = cursor
            while (i < end) {
                val j = pos.toInt()
                if (j >= data.size - 1) { voiceData[s] = null; break }
                val f = (pos - j).toFloat()
                val x = data[j] + (data[j + 1] - data[j]) * f
                left[i] += x * l
                right[i] += x * r
                pos += sp
                i++
            }
            voicePos[s] = pos
        }
        cursor = end
    }

    fun histogram(names: Array<String>): String {
        val sb = StringBuilder()
        val order = counts.indices.sortedByDescending { counts[it] }
        for (id in order) if (counts[id] > 0) sb.append(names[id]).append('=').append(counts[id]).append(' ')
        return sb.toString().trim()
    }
}
