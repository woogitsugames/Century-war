package com.centurywar.engine

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * D63: pomiary do przeglądu dźwięku (testy JVM). Głośność wg ITU-R BS.1770 (filtr K dla
 * dowolnej częstotliwości próbkowania, współczynniki jak w libebur128), szczyty, kliknięcia,
 * pasma widma do porównania epok, zapis WAV 16 bit.
 */
internal object AudioMeter {
    private class Biquad(val b0: Double, val b1: Double, val b2: Double, val a1: Double, val a2: Double) {
        var z1 = 0.0
        var z2 = 0.0
        fun step(x: Double): Double {
            val y = b0 * x + z1
            z1 = b1 * x - a1 * y + z2
            z2 = b2 * x - a2 * y
            return y
        }
    }

    private fun kFilter(rate: Int): Array<Biquad> {
        var f0 = 1681.974450955533
        val g = 3.999843853973347
        var q = 0.7071752369554196
        var k = tan(PI * f0 / rate)
        val vh = Math.pow(10.0, g / 20.0)
        val vb = Math.pow(vh, 0.4996667741545416)
        var a0 = 1.0 + k / q + k * k
        val shelf = Biquad((vh + vb * k / q + k * k) / a0, 2.0 * (k * k - vh) / a0,
            (vh - vb * k / q + k * k) / a0, 2.0 * (k * k - 1.0) / a0, (1.0 - k / q + k * k) / a0)
        f0 = 38.13547087602444
        q = 0.5003270373238773
        k = tan(PI * f0 / rate)
        a0 = 1.0 + k / q + k * k
        val hp = Biquad(1.0, -2.0, 1.0, 2.0 * (k * k - 1.0) / a0, (1.0 - k / q + k * k) / a0)
        return arrayOf(shelf, hp)
    }

    /** Kwadraty sygnału po filtrze K (mono). */
    fun kSquares(pcm: FloatArray, rate: Int): DoubleArray {
        val f = kFilter(rate)
        val out = DoubleArray(pcm.size)
        for (i in pcm.indices) {
            val y = f[1].step(f[0].step(pcm[i].toDouble()))
            out[i] = y * y
        }
        return out
    }

    /** Maksimum głośności chwilowej (okno 400 ms, krok 10 ms; krótki klip uzupełniony ciszą). */
    fun momentaryMax(pcm: FloatArray, rate: Int): Double {
        val sq = kSquares(pcm, rate)
        val win = (rate * 0.4).toInt()
        val hop = (rate * 0.01).toInt()
        var best = 0.0
        var start = -win + hop
        while (start < sq.size) {
            var sum = 0.0
            val a = max(0, start)
            val b = minOf(sq.size, start + win)
            for (i in a until b) sum += sq[i]
            best = max(best, sum / win)
            start += hop
        }
        return lufs(best)
    }

    /** Głośność zintegrowana stereo (bramki −70 LUFS i −10 LU), jak `ffmpeg ebur128`. */
    fun integrated(left: FloatArray, right: FloatArray, rate: Int): Double {
        val l = kSquares(left, rate)
        val r = kSquares(right, rate)
        val win = (rate * 0.4).toInt()
        val hop = (rate * 0.1).toInt()
        val blocks = ArrayList<Double>()
        var s = 0
        while (s + win <= l.size) {
            var sum = 0.0
            for (i in s until s + win) sum += l[i] + r[i]
            blocks.add(sum / win)
            s += hop
        }
        val abs = blocks.filter { lufs(it) > -70.0 }
        if (abs.isEmpty()) return -99.0
        val rel = lufs(abs.average()) - 10.0
        val gated = abs.filter { lufs(it) > rel }
        return if (gated.isEmpty()) -99.0 else lufs(gated.average())
    }

    fun lufs(meanSquare: Double): Double = if (meanSquare <= 1e-12) -99.0 else -0.691 + 10.0 * log10(meanSquare)

    fun db(x: Double): Double = if (x <= 1e-9) -99.0 else 20.0 * log10(x)

    fun rms(pcm: FloatArray): Double {
        var s = 0.0
        for (v in pcm) s += v.toDouble() * v
        return sqrt(s / max(1, pcm.size))
    }

    fun peak(pcm: FloatArray, from: Int = 0, to: Int = pcm.size): Double {
        var p = 0.0
        for (i in max(0, from) until minOf(pcm.size, to)) p = max(p, abs(pcm[i].toDouble()))
        return p
    }

    /**
     * Wskaźnik kliknięcia: największa druga różnica względem jej 95. percentyla. Szum ataku ma
     * wysoki percentyl, czysty ton niski; skok fazy lub obcięcie daje pojedynczy pik ≫ 1.
     */
    fun clickScore(pcm: FloatArray, skip: Int = 0): Double {
        if (pcm.size < 8 + skip) return 0.0
        val d = DoubleArray(pcm.size)
        // Wejście z ciszy i wyjście w ciszę też liczą się jako skok.
        for (i in 0 until pcm.size + 2) {
            val x0 = if (i < pcm.size) pcm[i].toDouble() else 0.0
            val x1 = if (i - 1 in pcm.indices) pcm[i - 1].toDouble() else 0.0
            val x2 = if (i - 2 in pcm.indices) pcm[i - 2].toDouble() else 0.0
            if (i < d.size) d[i] = if (i < skip) 0.0 else abs(x0 - 2 * x1 + x2)
        }
        val sorted = d.sortedArray()
        val p95 = max(sorted[(sorted.size * 0.95).toInt()], 1e-4)
        return sorted.last() / p95
    }

    /** Głośność K-ważona uśredniona po samym zdarzeniu (bez dopełnienia ciszą). */
    fun eventLoudness(pcm: FloatArray, rate: Int): Double {
        val sq = kSquares(pcm, rate)
        return lufs(sq.average())
    }

    /** Poziom ostatnich [ms] przed końcowym wyciszeniem względem RMS całości (obcięty ogon > −10). */
    fun tail(pcm: FloatArray, rate: Int, ms: Int = 40, fadeMs: Int = 15): Double {
        val end = pcm.size - rate * fadeMs / 1000
        val start = end - rate * ms / 1000
        var s = 0.0
        for (i in start.coerceAtLeast(0) until end) s += pcm[i].toDouble() * pcm[i]
        return db(sqrt(s / (end - start))) - db(rms(pcm))
    }

    /** Odtworzenie z inną prędkością (jak SoundPool rate): interpolacja liniowa. */
    fun resample(pcm: FloatArray, speed: Float): FloatArray {
        if (speed == 1f) return pcm
        val n = (pcm.size / speed).toInt()
        val out = FloatArray(n)
        for (i in 0 until n) {
            val pos = i * speed.toDouble()
            val j = pos.toInt()
            val f = (pos - j).toFloat()
            val a = pcm[j.coerceAtMost(pcm.size - 1)]
            val b = pcm[(j + 1).coerceAtMost(pcm.size - 1)]
            out[i] = a + (b - a) * f
        }
        return out
    }

    /** Energie w pasmach tercjowych 100 Hz–8 kHz, dB względem średniej (kształt widma). */
    fun bands(pcm: FloatArray, rate: Int): DoubleArray {
        var n = 1
        while (n < pcm.size) n = n shl 1
        val re = DoubleArray(n)
        val im = DoubleArray(n)
        for (i in pcm.indices) re[i] = pcm[i].toDouble()
        fft(re, im)
        val edges = DoubleArray(20) { 100.0 * Math.pow(2.0, it / 3.0) }
        val out = DoubleArray(edges.size - 1)
        for (b in out.indices) {
            val lo = (edges[b] * n / rate).toInt().coerceAtLeast(1)
            val hi = (edges[b + 1] * n / rate).toInt().coerceAtMost(n / 2).coerceAtLeast(lo + 1)
            var e = 0.0
            for (k in lo until hi) e += re[k] * re[k] + im[k] * im[k]
            out[b] = 10.0 * log10(e + 1e-12)
        }
        val mean = out.average()
        for (b in out.indices) out[b] -= mean
        return out
    }

    /** Odległość kształtów widma w dB (RMS różnic pasm). */
    fun spectralDistance(a: DoubleArray, b: DoubleArray): Double {
        var s = 0.0
        for (i in a.indices) s += (a[i] - b[i]) * (a[i] - b[i])
        return sqrt(s / a.size)
    }

    private fun fft(re: DoubleArray, im: DoubleArray) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) {
                var t = re[i]; re[i] = re[j]; re[j] = t
                t = im[i]; im[i] = im[j]; im[j] = t
            }
        }
        var len = 2
        while (len <= n) {
            val ang = -2 * PI / len
            val wr = Math.cos(ang)
            val wi = Math.sin(ang)
            var i = 0
            while (i < n) {
                var cr = 1.0
                var ci = 0.0
                for (k in 0 until len / 2) {
                    val ur = re[i + k]
                    val ui = im[i + k]
                    val vr = re[i + k + len / 2] * cr - im[i + k + len / 2] * ci
                    val vi = re[i + k + len / 2] * ci + im[i + k + len / 2] * cr
                    re[i + k] = ur + vr; im[i + k] = ui + vi
                    re[i + k + len / 2] = ur - vr; im[i + k + len / 2] = ui - vi
                    val nr = cr * wr - ci * wi
                    ci = cr * wi + ci * wr
                    cr = nr
                }
                i += len
            }
            len = len shl 1
        }
    }

    fun writeWav(file: File, rate: Int, vararg channels: FloatArray) {
        val n = channels[0].size
        val ch = channels.size
        val bytes = n * 2 * ch
        val b = ByteBuffer.allocate(44 + bytes).order(ByteOrder.LITTLE_ENDIAN)
        b.put("RIFF".toByteArray(Charsets.US_ASCII)); b.putInt(36 + bytes)
        b.put("WAVEfmt ".toByteArray(Charsets.US_ASCII)); b.putInt(16)
        b.putShort(1); b.putShort(ch.toShort())
        b.putInt(rate); b.putInt(rate * 2 * ch)
        b.putShort((2 * ch).toShort()); b.putShort(16)
        b.put("data".toByteArray(Charsets.US_ASCII)); b.putInt(bytes)
        for (i in 0 until n) for (c in channels) b.putShort((c[i].coerceIn(-1f, 1f) * 32767f).toInt().toShort())
        file.parentFile?.mkdirs()
        file.writeBytes(b.array())
    }
}
