package com.centurywar.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.centurywar.engine.Button
import com.centurywar.engine.Game
import com.centurywar.engine.Host
import com.centurywar.engine.L
import com.centurywar.engine.Quality
import com.centurywar.engine.RowNote
import com.centurywar.game.BattleConfig
import com.centurywar.game.Mode
import com.centurywar.meta.BattleHistory
import com.centurywar.meta.Profile
import com.centurywar.meta.ReplayStore
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Noc 8 („Drobne napisy”): geometria podpisów [RowNote] w Ustawieniach i Dostępności,
 * podpis „Od nowa” w wynikach bitwy i tytuł „Historia zmian”.
 *
 * Urządzenia: Fold zewnętrzny 904×2316 d=2,7 i telefon 1600×720 d=2; wszystkie 10 języków,
 * skala tekstu 100% i 130%, prawdziwe teksty oraz sztucznie długie (wstrzyknięte do tabeli [L]).
 * Zrzuty kontrolne: build/screenshots/noc8_*.png, dziennik noc8_napisy.txt.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class Noc8NapisyTest {
    private val out = File("build/screenshots").apply { mkdirs() }
    private val log = StringBuilder()

    private class Device(val w: Int, val h: Int, val density: Float, val insets: IntArray)

    private val devices = linkedMapOf(
        "foldOuter" to Device(904, 2316, 2.7f, intArrayOf(0, 88, 0, 0)),
        "phone720" to Device(1600, 720, 2f, intArrayOf(0, 0, 0, 0)),
    )

    private val host = object : Host {
        override fun vibrate(ms: Long) {}
        override fun requestText(title: String, current: String, onResult: (String) -> Unit) {}
        override fun applyRenderScale(scale: Float) {}
        override fun applyFrameRate(fps: Int) {}
        override fun exit() {}
    }

    private val tiny = Canvas(Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888))

    private class TextCanvas(bmp: Bitmap) : Canvas(bmp) {
        class Drawn(val s: String, val r: RectF, val sizePx: Float)
        val texts = ArrayList<Drawn>()

        override fun drawText(text: String, x: Float, y: Float, paint: Paint) {
            if (paint.style == Paint.Style.FILL && text.isNotBlank()) {
                val w = paint.measureText(text)
                val left = when (paint.textAlign) {
                    Paint.Align.CENTER -> x - w / 2f
                    Paint.Align.RIGHT -> x - w
                    else -> x
                }
                val fm = paint.fontMetrics
                texts.add(Drawn(text, RectF(left, y + fm.ascent * 0.8f, left + w, y + fm.descent * 0.6f), paint.textSize))
            }
            super.drawText(text, x, y, paint)
        }
    }

    private fun game(d: Device, lang: String, textScale: Int = 100): Game {
        val f = File.createTempFile("noc8", ".json").apply { delete() }
        val profile = Profile(f).apply {
            this.lang = lang; tutorialDone = true; hintsEnabled = false; eraIntrosEnabled = false
            this.textScale = textScale
        }
        return Game(host, profile, Quality.LOW).apply {
            density = d.density
            resize(d.w, d.h, d.w, d.h)
            setInsets(d.insets[0], d.insets[1], d.insets[2], d.insets[3])
            history = BattleHistory(File.createTempFile("noc8h", ".json").apply { delete() })
            historyLoaded = true
            replays = ReplayStore(File.createTempFile("noc8r", "").apply { delete(); mkdirs() })
        }
    }

    private fun tick(g: Game, n: Int = 1, dt: Float = 1f / 30f) = repeat(n) { g.frame(tiny, dt) }
    private fun field(o: Any, name: String) = o.javaClass.getDeclaredField(name).apply { isAccessible = true }

    private fun render(g: Game, file: String?): List<TextCanvas.Drawn> {
        g.finishScreenTransition()
        val bmp = Bitmap.createBitmap(g.width, g.height, Bitmap.Config.ARGB_8888)
        try {
            val c = TextCanvas(bmp)
            g.frame(c, 0f)
            if (file != null) File(out, "noc8_$file.png").outputStream().use {
                check(bmp.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
            return ArrayList(c.texts)
        } finally { bmp.recycle() }
    }

    @Suppress("UNCHECKED_CAST")
    private fun table(): HashMap<String, Array<out String>> {
        val f = L::class.java.getDeclaredField("table\$delegate").apply { isAccessible = true }
        return (f.get(L) as Lazy<HashMap<String, Array<out String>>>).value
    }

    private val longPl = "Ten podpis jest celowo bardzo długi, żeby sprawdzić zawijanie i wielokropek w każdym wierszu ustawień"

    /** Sprawdza wiersze podpisu narysowane na ekranie: w wierszu, przed przyciskiem, co najmniej 9 dp. */
    private fun checkNote(where: String, g: Game, texts: List<TextCanvas.Drawn>, note: RowNote,
                          btn: Button, rowTop: Float, rowBottom: Float, errors: StringBuilder) {
        val dp = g.ui.dp
        val parts = if (note.second.isEmpty()) listOf(note.first) else listOf(note.first, note.second)
        for (p in parts) {
            val t = texts.lastOrNull { it.s == p }
            if (t == null) { errors.append("$where: brak podpisu '$p'\n"); continue }
            val size = t.sizePx / dp
            log.append("$where: ${"%.1f".format(size)} dp, ${note.lines} wiersz(e) '$p'\n")
            if (size < RowNote.MIN_DP - 0.01f) errors.append("$where: podpis ${"%.2f".format(size)} dp '$p'\n")
            if (t.r.right > btn.r.left + 0.5f) errors.append("$where: podpis wchodzi pod przycisk ${t.r} / ${btn.r} '$p'\n")
            if (t.r.top < rowTop - 0.5f || t.r.bottom > rowBottom + 0.5f)
                errors.append("$where: podpis poza wierszem ${t.r} / $rowTop..$rowBottom '$p'\n")
        }
    }

    @Test
    fun rowNoteGeometry() {
        val old = L.lang
        val tab = table()
        val saved = mapOf("screen_shake_note" to tab.getValue("screen_shake_note"),
            "access_reduce_shake_note" to tab.getValue("access_reduce_shake_note"))
        val errors = StringBuilder()
        try {
            for (long in arrayOf(false, true)) {
                if (long) {
                    // Sztucznie długi tekst we wszystkich językach (jedna wersja na język, z dopiskiem kodu).
                    val arr = Array(L.CODES.size) { "$longPl, $longPl (${L.CODES[it]})" }
                    for (k in saved.keys) tab[k] = arr
                }
                for ((tag, d) in devices) for (scale in intArrayOf(100, 130)) for (lang in L.CODES) {
                    val where = "$tag/$lang/$scale${if (long) "/dlugi" else ""}"
                    val g = game(d, lang, scale)
                    tick(g, 3, 0.1f)
                    val st = SettingsScreen(g)
                    g.setScreen(st); tick(g, 4, 0.1f)
                    val shakeBtn = field(st, "shakeBtn").get(st) as Button
                    // Na 1600×720 wiersz wstrząsów jest pod krawędzią listy: przewijamy krokami po 20 dp.
                    var steps = 0
                    while (!shakeBtn.visible && steps++ < 100) {
                        field(st, "scroll").setFloat(st, field(st, "scroll").getFloat(st) + 20f * g.ui.dp)
                        st.layout(); tick(g)
                    }
                    var texts = render(g, if (scale == 100 && (lang == "fr" || lang == "es" || long && lang == "pl")) "${tag}_${lang}${if (long) "_dlugi" else ""}_ustawienia" else null)
                    if (shakeBtn.visible) {
                        val note = field(st, "shakeNote").get(st) as RowNote
                        checkNote("$where/ustawienia", g, texts, note, shakeBtn,
                            shakeBtn.r.top - 3 * g.ui.dp, shakeBtn.r.bottom + 3 * g.ui.dp, errors)
                        val shown = if (note.second.isEmpty()) note.first else note.first + " " + note.second
                        assertTrue("$where: podpis ucięty bez wielokropka '$shown'",
                            shown == L["screen_shake_note"] || shown.endsWith("…"))
                        // Prawdziwe teksty przy skali 100% mieszczą się w całości (elipsa to tylko zabezpieczenie).
                        if (!long && scale == 100 && shown.endsWith("…")) errors.append("$where: elipsa w '$shown'\n")
                        // Bufor: kolejna klatka bez zmian nie tworzy nowych napisów.
                        val first = note.first
                        render(g, null)
                        assertSame("$where: bufor podpisu", first, note.first)
                    } else errors.append("$where: wiersz wstrząsów niewidoczny\n")

                    val acc = AccessibilityScreen(g)
                    g.setScreen(acc); tick(g, 4, 0.1f)
                    val shake = field(acc, "shake").get(acc) as Button
                    var guard = 0
                    while (!shake.visible && guard++ < 200) { acc.onDrag(0f, -20f * g.ui.dp); tick(g) }
                    texts = render(g, if (scale == 100 && (lang == "fr" || long && lang == "pl")) "${tag}_${lang}${if (long) "_dlugi" else ""}_dostepnosc" else null)
                    if (shake.visible) {
                        val note = field(acc, "shakeNote").get(acc) as RowNote
                        checkNote("$where/dostepnosc", g, texts, note, shake,
                            shake.r.top - 6 * g.ui.dp, shake.r.bottom + 6 * g.ui.dp, errors)
                        // Nic nie znika po cichu: albo cały tekst, albo „…” na końcu.
                        val shown = if (note.second.isEmpty()) note.first else note.first + " " + note.second
                        assertTrue("$where: podpis ucięty bez wielokropka '$shown'",
                            shown == L["access_reduce_shake_note"] || shown.endsWith("…"))
                        if (!long && scale == 100 && shown.endsWith("…")) errors.append("$where: elipsa w '$shown'\n")
                        if (long && tag == "foldOuter") assertTrue("$where: długi podpis bez wielokropka", shown.endsWith("…"))
                    } else errors.append("$where: wiersz „Mniej wstrząsów” niewidoczny\n")
                    g.history?.close()
                }
            }
        } finally {
            for ((k, v) in saved) tab[k] = v
            L.lang = old
            File(out, "noc8_napisy.txt").writeText(log.toString())
        }
        assertTrue("Podpisy wierszy:\n$errors", errors.isEmpty())
    }

    /** Czysta logika [RowNote]: dwa wiersze, gdy jest wysokość; inaczej jeden z „…”. */
    @Test
    fun rowNoteFallbacks() {
        val g = game(devices.getValue("phone720"), "pl")
        val ui = g.ui
        val dp = ui.dp
        val n = RowNote()
        n.layout(ui, longPl, 150 * dp, 40 * dp)
        assertTrue(n.lines == 2 && n.second.endsWith("…"))
        assertTrue(ui.measure(n.first, n.size, true) <= 150 * dp && ui.measure(n.second, n.size, true) <= 150 * dp)
        n.layout(ui, longPl, 150 * dp, 14 * dp)
        assertTrue(n.lines == 1 && n.first.endsWith("…") && n.size >= RowNote.MIN_DP)
        assertTrue(ui.measure(n.first, n.size, true) <= 150 * dp)
        n.layout(ui, "Krótki", 150 * dp, 14 * dp)
        assertTrue(n.lines == 1 && n.first == "Krótki" && n.size == RowNote.NOTE_DP)
        n.layout(ui, "Średnio długi podpis do zawinięcia w wierszu", 150 * dp, 40 * dp)
        assertTrue(n.lines == 2 && !n.second.endsWith("…") && n.size >= RowNote.MIN_DP)
        g.history?.close()
    }

    /** Wyniki bitwy: „Od nowa” ≥ 10 sp; układ przycisków zmienia się tylko tam, gdzie był za mały. */
    @Test
    fun resultsRestartLabel() {
        val old = L.lang
        val errors = StringBuilder()
        try {
            for ((tag, d) in devices) for (lang in L.CODES) {
                val g = game(d, lang)
                tick(g, 3, 0.1f)
                val bs = BattleScreen(g, BattleConfig(Mode.SKIRMISH, 161L, 1f))
                g.setScreen(bs); tick(g, 2)
                repeat(60 * 20) { bs.battle.step() }
                bs.battle.surrender()
                var n = 0
                while (!bs.finished && n++ < 200) tick(g, 1, 0.1f)
                tick(g, 16, 0.25f)
                val texts = render(g, if (lang == "fr" || lang == "de") "${tag}_${lang}_wyniki" else null)
                val t = texts.lastOrNull { it.s == bs.restartEnd.label }
                val size = (t?.sizePx ?: 0f) / g.ui.dp
                // Dawny warunek równych przycisków (tylko „Powtórka”), do porównania układu.
                val usable = bs.resultPanel.width() - 40 * g.ui.dp - 10 * g.ui.dp
                val oldTight = g.ui.fit(L["replay"], 15f, usable * 0.44f - 50 * g.ui.dp * 0.88f - 16 * g.ui.dp) < 15f * 0.75f
                log.append("$tag/$lang: dawniej ${if (oldTight) "równe" else "26/44/30"}, „${bs.restartEnd.label}” ${"%.1f".format(size)} dp, przycisk ${"%.0f".format(bs.restartEnd.r.width() / g.ui.dp)} dp\n")
                if (size < 10f) errors.append("$tag/$lang: „${bs.restartEnd.label}” ${"%.1f".format(size)} dp\n")
                g.history?.close()
            }
        } finally {
            L.lang = old
            File(out, "noc8_wyniki.txt").writeText(log.toString())
        }
        assertTrue("Podpis Od nowa:\n$errors", errors.isEmpty())
    }

    /** Tytuł „Historia zmian” ≥ 10 sp we wszystkich językach; tytuły ≥ 10 sp bez zmian rozmiaru. */
    @Test
    fun historyTitle() {
        val old = L.lang
        val errors = StringBuilder()
        try {
            for ((tag, d) in devices) for (lang in L.CODES) {
                val g = game(d, lang)
                tick(g, 3, 0.1f)
                g.setScreen(WhatsNewScreen(g)); tick(g, 4, 0.1f)
                val texts = render(g, if (lang == "fr" || lang == "pt") "${tag}_${lang}_historia" else null)
                val title = L["wn_history"]
                val t = texts.lastOrNull { it.s == title }
                val size = (t?.sizePx ?: 0f) / g.ui.dp
                log.append("$tag/$lang: „$title” ${"%.1f".format(size)} dp\n")
                if (size < 10f - 0.05f) errors.append("$tag/$lang: „$title” ${"%.1f".format(size)} dp\n")
                if (t != null && (t.r.left < 0f || t.r.right > g.width)) errors.append("$tag/$lang: tytuł poza ekranem ${t.r}\n")
                g.history?.close()
            }
        } finally {
            L.lang = old
            File(out, "noc8_tytuly.txt").writeText(log.toString())
        }
        assertTrue("Tytuł historii:\n$errors", errors.isEmpty())
    }
}
