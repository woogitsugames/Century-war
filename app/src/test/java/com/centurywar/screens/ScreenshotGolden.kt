package com.centurywar.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import java.io.File
import kotlin.math.abs
import kotlin.math.max

/**
 * Wzorce są wersjonowane. Zwykły test nigdy ich nie nadpisuje.
 * Porównanie ignoruje drobne różnice rasteryzacji i jawnie zamaskowaną animację.
 */
internal object ScreenshotGolden {
    const val CHANNEL_TOLERANCE = 20
    const val MAX_CHANGED_FRACTION = 0.005

    data class Comparison(
        val sameSize: Boolean,
        val inspectedPixels: Long,
        val changedPixels: Long,
        val maskedPixels: Long,
        val maxChangedFraction: Double,
    ) {
        val changedFraction: Double
            get() = if (inspectedPixels == 0L) 1.0 else changedPixels.toDouble() / inspectedPixels
        val passes: Boolean
            get() = sameSize && inspectedPixels > 0L && changedFraction <= maxChangedFraction
    }

    /** Środkowy pas pola bitwy może zawierać animowane jednostki, ale nie HUD. */
    private fun masked(name: String, y: Int, height: Int): Boolean =
        name.endsWith("_battle_hud") &&
            y >= (height * 0.22f).toInt() && y < (height * 0.68f).toInt()

    private fun delta(a: Int, b: Int): Int = max(
        max(abs(Color.red(a) - Color.red(b)), abs(Color.green(a) - Color.green(b))),
        max(abs(Color.blue(a) - Color.blue(b)), abs(Color.alpha(a) - Color.alpha(b))),
    )

    fun compare(
        expected: Bitmap,
        actual: Bitmap,
        name: String,
        channelTolerance: Int = CHANNEL_TOLERANCE,
        maxChangedFraction: Double = MAX_CHANGED_FRACTION,
    ): Comparison {
        require(channelTolerance in 0..255)
        require(maxChangedFraction in 0.0..1.0)
        val width = minOf(expected.width, actual.width)
        val height = minOf(expected.height, actual.height)
        val oldLine = IntArray(width)
        val newLine = IntArray(width)
        var changed = 0L
        var inspected = 0L
        var ignored = 0L
        for (y in 0 until height) {
            if (masked(name, y, actual.height)) {
                ignored += width
                continue
            }
            expected.getPixels(oldLine, 0, width, 0, y, width, 1)
            actual.getPixels(newLine, 0, width, 0, y, width, 1)
            for (x in 0 until width) {
                inspected++
                if (delta(oldLine[x], newLine[x]) > channelTolerance) changed++
            }
        }
        return Comparison(
            sameSize = expected.width == actual.width && expected.height == actual.height,
            inspectedPixels = inspected,
            changedPixels = changed,
            maskedPixels = ignored,
            maxChangedFraction = maxChangedFraction,
        )
    }

    /** Czerwony = regresja, granatowy = maska, ciemny = zgodny piksel. */
    fun renderDiff(
        expected: Bitmap,
        actual: Bitmap,
        name: String,
        channelTolerance: Int = CHANNEL_TOLERANCE,
    ): Bitmap {
        val width = max(expected.width, actual.width)
        val height = max(expected.height, actual.height)
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val oldLine = IntArray(expected.width)
        val newLine = IntArray(actual.width)
        val diffLine = IntArray(width)
        for (y in 0 until height) {
            if (y < expected.height) expected.getPixels(oldLine, 0, expected.width, 0, y, expected.width, 1)
            if (y < actual.height) actual.getPixels(newLine, 0, actual.width, 0, y, actual.width, 1)
            for (x in 0 until width) {
                diffLine[x] = when {
                    x >= expected.width || x >= actual.width ||
                        y >= expected.height || y >= actual.height -> Color.RED
                    masked(name, y, actual.height) -> Color.rgb(27, 44, 82)
                    delta(oldLine[x], newLine[x]) > channelTolerance -> Color.rgb(255, 25, 65)
                    else -> {
                        val p = newLine[x]
                        val gray = (Color.red(p) + Color.green(p) + Color.blue(p)) / 12
                        Color.rgb(gray, gray, gray)
                    }
                }
            }
            output.setPixels(diffLine, 0, width, 0, y, width, 1)
        }
        return output
    }

    fun verify(name: String) {
        require(Regex("(phone720|foldOpen)_reg100_[a-z0-9_]+").matches(name)) {
            "Nieprawidłowa nazwa zrzutu regresyjnego: $name"
        }
        val actualFile = File("build/screenshots/$name.png")
        check(actualFile.isFile) { "Brak bieżącego zrzutu: $actualFile" }
        val expectedFile = File("src/test/resources/screens/$name.png")
        val update = System.getProperty("cw.screens.update") == "true"
        if (update) {
            check((System.getProperty("cw.screens.reason")?.trim()?.length ?: 0) >= 8) {
                "Aktualizacja zrzutów wymaga -PscreenReason (min. 8 znaków)."
            }
            expectedFile.parentFile.mkdirs()
            actualFile.copyTo(expectedFile, overwrite = true)
            println("Zaktualizowano wzorzec $name")
            return
        }
        if (!expectedFile.isFile) {
            throw AssertionError(
                "Brak wzorca $expectedFile. Uruchom :app:updateScreenshots " +
                    "-PscreenReason='uzasadnienie zmiany' i zatwierdź obrazy w Git.",
            )
        }
        val expected = BitmapFactory.decodeFile(expectedFile.absolutePath)
            ?: throw AssertionError("Uszkodzony wzorzec PNG: $expectedFile")
        val actual = BitmapFactory.decodeFile(actualFile.absolutePath)
            ?: throw AssertionError("Uszkodzony bieżący PNG: $actualFile")
        try {
            val result = compare(expected, actual, name)
            if (result.passes) return
            val folder = File("build/screenshots/diffs").apply { mkdirs() }
            val diffFile = File(folder, "$name.diff.png")
            val diff = renderDiff(expected, actual, name)
            try {
                diffFile.outputStream().use { stream ->
                    check(diff.compress(Bitmap.CompressFormat.PNG, 100, stream))
                }
            } finally {
                diff.recycle()
            }
            val summary = "$name: wzorzec ${expected.width}x${expected.height}, " +
                "wynik ${actual.width}x${actual.height}, zmienione ${result.changedPixels}/" +
                "${result.inspectedPixels} (${"%.3f".format(java.util.Locale.ROOT, result.changedFraction * 100)}%), " +
                "limit ${MAX_CHANGED_FRACTION * 100}%, tolerancja kanału $CHANNEL_TOLERANCE, " +
                "zamaskowane ${result.maskedPixels}; diff: ${diffFile.path}"
            File(folder, "$name.txt").writeText(summary + "\n")
            throw AssertionError(summary)
        } finally {
            expected.recycle()
            actual.recycle()
        }
    }
}
