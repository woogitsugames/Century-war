package com.centurywar.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow
import kotlin.math.sqrt

/** Całkowita utrata jednego typu czopków; macierze Machado 2009 dla liniowego sRGB. */
internal enum class VisionDefect { PROTANOPIA, DEUTERANOPIA, TRITANOPIA }

/** Symulacja wyłącznie do testów i PNG: nie modyfikuje renderera ani symulacji bitwy. */
internal object ColorVisionSim {
    private val matrices = arrayOf(
        doubleArrayOf(
            0.152286, 1.052583, -0.204868,
            0.114503, 0.786281, 0.099216,
            -0.003882, -0.048116, 1.051998,
        ),
        doubleArrayOf(
            0.367322, 0.860646, -0.227968,
            0.280085, 0.672501, 0.047413,
            -0.011820, 0.042940, 0.968881,
        ),
        doubleArrayOf(
            1.255528, -0.076749, -0.178779,
            -0.078411, 0.930809, 0.147602,
            0.004733, 0.691367, 0.303900,
        ),
    )

    private fun channel(c: Int, shift: Int) = (c ushr shift) and 255

    private fun linear(c: Int): Double {
        val v = c / 255.0
        return if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    }

    private fun encoded(v: Double): Int {
        val c = v.coerceIn(0.0, 1.0)
        val srgb = if (c <= 0.0031308) 12.92 * c else 1.055 * c.pow(1.0 / 2.4) - 0.055
        return (srgb * 255.0 + 0.5).toInt().coerceIn(0, 255)
    }

    fun filter(color: Int, defect: VisionDefect): Int {
        val m = matrices[defect.ordinal]
        val r = linear(channel(color, 16))
        val g = linear(channel(color, 8))
        val b = linear(channel(color, 0))
        val rr = encoded(m[0] * r + m[1] * g + m[2] * b)
        val gg = encoded(m[3] * r + m[4] * g + m[5] * b)
        val bb = encoded(m[6] * r + m[7] * g + m[8] * b)
        return (color and -0x1000000) or (rr shl 16) or (gg shl 8) or bb
    }

    private fun luminance(c: Int) =
        linear(channel(c, 16)) * 0.2126 +
            linear(channel(c, 8)) * 0.7152 + linear(channel(c, 0)) * 0.0722

    /** Kontrast WCAG 2.2 dla nieprzezroczystych RGB; kolejność kolorów dowolna. */
    fun contrast(a: Int, b: Int): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    private fun lab(c: Int): DoubleArray {
        val r = linear(channel(c, 16))
        val g = linear(channel(c, 8))
        val b = linear(channel(c, 0))
        val x = (r * 0.4124564 + g * 0.3575761 + b * 0.1804375) / 0.95047
        val y = r * 0.2126729 + g * 0.7151522 + b * 0.0721750
        val z = (r * 0.0193339 + g * 0.1191920 + b * 0.9503041) / 1.08883
        fun f(t: Double) = if (t > 0.008856) t.pow(1.0 / 3.0) else 7.787 * t + 16.0 / 116.0
        val fx = f(x)
        val fy = f(y)
        val fz = f(z)
        return doubleArrayOf(116.0 * fy - 16.0, 500.0 * (fx - fy), 200.0 * (fy - fz))
    }

    /** Odległość CIE 1976 ΔE po symulacji zaburzenia widzenia barw. */
    fun deltaE(a: Int, b: Int, defect: VisionDefect): Double {
        val x = lab(filter(a, defect))
        val y = lab(filter(b, defect))
        return sqrt((x[0] - y[0]).pow(2) + (x[1] - y[1]).pow(2) + (x[2] - y[2]).pow(2))
    }
}

// Ui.TEXT/DIM/BTN powstają przez Color.rgb: bez Robolectric zwraca on 0 i kontrast liczy się dla czerni.
@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [35])
class ColorBlindTest {
    private fun withPalettes(action: (Int, PaletteColors) -> Unit) {
        val original = Palette.selected
        try {
            for (id in 0 until Palette.COUNT) {
                Palette.select(id)
                action(id, Palette.active)
            }
        } finally {
            Palette.select(original)
        }
    }

    @Test
    fun teamsAndHealthBarsSurviveAllColorBlindSimulations() = withPalettes { id, p ->
        assertEquals(p.player, Ui.PLAYER)
        assertEquals(p.enemy, Ui.ENEMY)
        for (defect in VisionDefect.entries) {
            // Paski HP i minimapa dziedziczą barwy drużyn; przy teamMarkers mają też wzory.
            val distance = ColorVisionSim.deltaE(p.player, p.enemy, defect)
            assertTrue("Drużyny: paleta " + id + ", " + defect + ", ΔE=" + distance, distance >= 20.0)
        }
    }

    @Test
    fun positiveAndNegativeStatesStayDistinct() = withPalettes { id, p ->
        for (defect in VisionDefect.entries) {
            val labels = ColorVisionSim.deltaE(p.good, p.bad, defect)
            val buttons = ColorVisionSim.deltaE(p.buttonGood, p.buttonBad, defect)
            assertTrue("Statusy: paleta " + id + ", " + defect + ", ΔE=" + labels, labels >= 15.0)
            assertTrue("Przyciski: paleta " + id + ", " + defect + ", ΔE=" + buttons, buttons >= 10.0)
        }
    }

    @Test
    fun normalUiTextMeetsWcagAa() = withPalettes { id, p ->
        fun check(label: String, fg: Int, bg: Int) {
            val ratio = ColorVisionSim.contrast(fg, bg)
            assertTrue("Kontrast " + label + " w palecie " + id + ": " + ratio, ratio >= 4.5)
        }
        check("tekst / panel", Ui.TEXT, p.panel)
        check("przygaszony tekst / panel", Ui.DIM, p.panel)
        check("tekst / przycisk", Ui.TEXT, Ui.BTN)
        check("tekst pomocniczy / przycisk", Ui.DIM, Ui.BTN)
        check("złoty podpis / przycisk", Ui.GOLD, Ui.BTN)
        check("złoty nagłówek / wstęga", Ui.GOLD, Ui.RIBBON)
        check("ciemny tekst / wyróżniony przycisk", Ui.OUTLINE, Ui.BTN_HI)
        check("tekst / aktywny przycisk", Ui.TEXT, p.buttonGood)
        check("tekst / negatywny przycisk", Ui.TEXT, p.buttonBad)
        check("stan pozytywny / panel", p.good, p.panel)
        check("stan negatywny / panel", p.bad, p.panel)
    }

    @Test
    fun statusButtonsRemainLegibleThroughAllSimulations() = withPalettes { id, p ->
        for (defect in VisionDefect.entries) {
            for ((label, background) in arrayOf("dobry" to p.buttonGood, "zły" to p.buttonBad)) {
                val ratio = ColorVisionSim.contrast(
                    ColorVisionSim.filter(Ui.TEXT, defect),
                    ColorVisionSim.filter(background, defect),
                )
                assertTrue("Tekst " + label + " / " + defect + " / paleta " + id + ": " + ratio,
                    ratio >= 4.5)
            }
        }
    }

    @Test
    fun standardKeepsBluePlayerAndRedEnemy() {
        val old = Palette.selected
        try {
            Palette.select(Palette.STANDARD)
            val p = Palette.active
            assertTrue((p.player and 255) > ((p.player ushr 16) and 255))
            assertTrue(((p.enemy ushr 16) and 255) > ((p.enemy ushr 8) and 255))
        } finally { Palette.select(old) }
    }
}
