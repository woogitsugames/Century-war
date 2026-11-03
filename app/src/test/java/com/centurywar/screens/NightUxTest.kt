package com.centurywar.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import com.centurywar.engine.Game
import com.centurywar.engine.Host
import com.centurywar.engine.L
import com.centurywar.engine.Quality
import com.centurywar.engine.TouchEvent
import com.centurywar.game.BattleConfig
import com.centurywar.game.Bonuses
import com.centurywar.game.Daily
import com.centurywar.game.DailyEvent
import com.centurywar.game.EndlessCard
import com.centurywar.game.Mode
import com.centurywar.meta.Achievements
import com.centurywar.meta.BattleHistory
import com.centurywar.meta.Profile
import com.centurywar.meta.ReplayStore
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Noc 3, przegląd UX po fali 7 (160 cykl życia, 161 panel wież, 164 TalkBack) i nowej zawartości 170
 * (wydarzenia dnia, karty Endless, osiągnięcia, „Co nowego”).
 *
 * Urządzenia: telefon 1600×720 d=2, 2400×1080 d=2,7 z wycięciem, niski 1200×720 d=2, Fold zewnętrzny
 * i wewnętrzny; pl, en, de, a dla tekstów 170 kontrolnie tr i uk. Płótno zapisuje napisy jak
 * [PrereleasePolishTest]: wykrywa ucięte, nachodzące i drobne teksty oraz cele dotyku < 48 dp.
 * Zrzuty: build/screenshots/noc3_<urządzenie>_<język>_<nr>_<ekran>.png, dziennik noc3_audit.txt.
 * Twarde asercje ([requireNight]) pilnują tylko rzeczy naprawionych w tej nocy.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class NightUxTest {
    private val out = File("build/screenshots").apply { mkdirs() }

    private class Device(val w: Int, val h: Int, val density: Float, val insets: IntArray)

    private val devices = linkedMapOf(
        "phone720" to Device(1600, 720, 2f, intArrayOf(0, 0, 0, 0)),
        "phone20x9" to Device(2400, 1080, 2.7f, intArrayOf(96, 0, 0, 0)),
        "low1200" to Device(1200, 720, 2f, intArrayOf(0, 0, 0, 0)),
        "foldOuter" to Device(904, 2316, 2.7f, intArrayOf(0, 88, 0, 0)),
        "foldInner" to Device(2184, 1968, 2.7f, intArrayOf(0, 70, 0, 0)),
    )

    private val host = object : Host {
        override fun vibrate(ms: Long) {}
        override fun requestText(title: String, current: String, onResult: (String) -> Unit) {}
        override fun applyRenderScale(scale: Float) {}
        override fun applyFrameRate(fps: Int) {}
        override fun exit() {}
    }

    private val audit = StringBuilder()
    private val regressions = StringBuilder()
    private fun requireNight(ok: Boolean, msg: String) { if (!ok) regressions.append(msg).append('\n') }

    private val tiny = Canvas(Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888))

    private class TextCanvas(bmp: Bitmap) : Canvas(bmp) {
        class Drawn(val s: String, val r: RectF, val clip: RectF, val sizePx: Float)
        val texts = ArrayList<Drawn>()
        private val m = android.graphics.Matrix()
        private val clipI = Rect()

        override fun drawText(text: String, x: Float, y: Float, paint: Paint) {
            if (paint.style == Paint.Style.FILL && text.isNotBlank() && android.graphics.Color.alpha(paint.color) > 60) {
                val w = paint.measureText(text)
                val left = when (paint.textAlign) {
                    Paint.Align.CENTER -> x - w / 2f
                    Paint.Align.RIGHT -> x - w
                    else -> x
                }
                val fm = paint.fontMetrics
                val r = RectF(left, y + fm.ascent * 0.8f, left + w, y + fm.descent * 0.6f)
                @Suppress("DEPRECATION") getMatrix(m)
                m.mapRect(r)
                getClipBounds(clipI)
                val clip = RectF(clipI)
                m.mapRect(clip)
                texts.add(Drawn(text, r, clip, paint.textSize * m.mapRadius(1f)))
            }
            super.drawText(text, x, y, paint)
        }
    }

    private fun game(d: Device, lang: String, leftHanded: Boolean = false): Game {
        val f = File.createTempFile("noc3", ".json").apply { delete() }
        val profile = Profile(f).apply {
            this.lang = lang; tutorialDone = true; hintsEnabled = false; eraIntrosEnabled = false
            leftHandedHud = leftHanded
        }
        return Game(host, profile, Quality.LOW).apply {
            density = d.density
            resize(d.w, d.h, d.w, d.h)
            setInsets(d.insets[0], d.insets[1], d.insets[2], d.insets[3])
            history = BattleHistory(File.createTempFile("noc3h", ".json").apply { delete() })
            historyLoaded = true
            replays = ReplayStore(File.createTempFile("noc3r", "").apply { delete(); mkdirs() })
        }
    }

    private fun tick(g: Game, n: Int = 1, dt: Float = 1f / 30f) = repeat(n) { g.frame(tiny, dt) }
    private fun field(o: Any, name: String) = o.javaClass.getDeclaredField(name).apply { isAccessible = true }

    private var counter = 0
    private var lastTexts: List<TextCanvas.Drawn> = emptyList()

    private fun shot(g: Game, prefix: String, name: String, saveFile: Boolean = true) {
        g.finishScreenTransition()
        val bmp = Bitmap.createBitmap(g.width, g.height, Bitmap.Config.ARGB_8888)
        try {
            val c = TextCanvas(bmp)
            g.frame(c, 0f)
            val where = "$prefix/${"%02d".format(counter)}_$name"
            if (saveFile) File(out, "noc3_${prefix}_${"%02d".format(counter)}_$name.png").outputStream().use {
                check(bmp.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
            counter++
            lastTexts = ArrayList(c.texts)
            auditTexts(g, where, c.texts)
            auditButtons(g, where)
        } finally { bmp.recycle() }
    }

    private fun auditTexts(g: Game, where: String, texts: List<TextCanvas.Drawn>) {
        val dp = g.ui.dp
        val s = g.current
        val busyField = s is BattleScreen || s is MenuScreen
        val banner = RectF(g.noticeBanner)
        val drawn = if (banner.isEmpty) texts else texts.filter { !RectF.intersects(it.r, banner) }
        val visible = drawn.filter { RectF.intersects(it.r, it.clip) && it.r.right > 0f && it.r.left < g.width &&
            it.r.bottom > 0f && it.r.top < g.height }
        for (t in visible) {
            val cutH = t.r.left < maxOf(0f, t.clip.left) - 1.5f * dp || t.r.right > minOf(g.width.toFloat(), t.clip.right) + 1.5f * dp
            if (cutH) audit.append("$where: UCIĘTY TEKST '${t.s}' ${t.r} (klip ${t.clip})\n")
            if (t.sizePx / dp < 8.5f) audit.append("$where: DROBNY TEKST ${"%.1f".format(t.sizePx / dp)} dp '${t.s}'\n")
        }
        if (busyField) return
        for (i in visible.indices) for (j in i + 1 until visible.size) {
            val a = visible[i]; val b = visible[j]
            if (a.s == b.s) continue
            val ix = minOf(a.r.right, b.r.right) - maxOf(a.r.left, b.r.left)
            val iy = minOf(a.r.bottom, b.r.bottom) - maxOf(a.r.top, b.r.top)
            if (ix > 3 * dp && iy > 0.35f * minOf(a.r.height(), b.r.height()))
                audit.append("$where: NAKŁADKA TEKSTU '${a.s}' / '${b.s}'\n")
        }
    }

    private fun auditButtons(g: Game, where: String) {
        val s = g.current ?: return
        val dp = g.ui.dp
        val l = g.insetL; val t = g.insetT; val r = g.width - g.insetR; val b = g.height - g.insetB
        for (btn in s.buttons) {
            if (!btn.visible || btn.r.width() <= 0f || btn.r.height() <= 0f) continue
            val id = "${s.javaClass.simpleName}#${btn.id} '${btn.label}'"
            val rr = btn.r
            if ((rr.left < l - 0.5f || rr.top < t - 0.5f || rr.right > r + 0.5f || rr.bottom > b + 0.5f) &&
                rr.right > 0f && rr.left < g.width && rr.bottom > 0f && rr.top < g.height)
                audit.append("$where: POZA BEZPIECZNYM OBSZAREM $id $rr\n")
            val hw = (rr.width() + 2 * btn.hitSlop) / dp
            val hh = (rr.height() + 2 * btn.hitSlop) / dp
            if (btn.enabled && (hw < 47.5f || hh < 47.5f))
                audit.append("$where: MAŁY CEL $id ${"%.0f".format(hw)}x${"%.0f".format(hh)} dp\n")
            if (btn.enabled) {
                val first = s.buttons.firstOrNull { it.enabled && it.contains(rr.centerX(), rr.centerY()) }
                if (first != null && first !== btn) audit.append("$where: ZASŁONIĘTY $id przez #${first.id} '${first.label}'\n")
            }
        }
    }

    private fun firstDay(event: DailyEvent): Long {
        var day = Daily.EVENTS_V2_DAY
        while (Daily.config(day, Bonuses()).dailyEvent != event) day++
        return day
    }

    /** Panel wież po 161: dwa warianty (zwykły i niski) oraz układ leworęczny. */
    private fun turrets(g: Game, prefix: String) {
        val bs = BattleScreen(g, BattleConfig(Mode.SKIRMISH, 161L, 1f))
        bs.battle.player.slots = 4
        bs.battle.player.gold = 1_000_000f
        // Dwie wieże kupione (przyciski stopnia i sprzedaży widoczne), dwa sloty puste.
        bs.battle.buyTurret(bs.battle.player, 0)
        bs.battle.buyTurret(bs.battle.player, 2)
        g.setScreen(bs)
        tick(g, 2)
        bs.showTurrets = true
        tick(g)
        shot(g, prefix, "wieze")
        // Noc 3: podpisy przycisków wież (np. „Verkaufen 55”) czytelne na każdym ekranie.
        val labels = (bs.slotBuy + bs.slotTier + bs.slotSell).filter { it.visible && it.label.isNotEmpty() }.map { it.label }.toSet()
        for (t in lastTexts) if (t.s in labels)
            requireNight(t.sizePx / g.ui.dp >= 9.3f, "$prefix: podpis wieży ${"%.1f".format(t.sizePx / g.ui.dp)} dp '${t.s}'")
    }

    private fun endlessCards(g: Game, prefix: String) {
        val es = BattleScreen(g, BattleConfig(Mode.ENDLESS, 2026L, 1f))
        g.setScreen(es)
        val e = es.battle
        repeat(5) { e.waveTimer = 0f; e.step() }
        tick(g, 2)
        if (e.pendingCards != null) {
            e.pendingCards = arrayOf(EndlessCard.QUARTERMASTER, EndlessCard.MASTER_MASONS, EndlessCard.ECONOMY)
            tick(g)
            shot(g, prefix, "endless_karty")
            val dp = g.ui.dp
            val portraitNarrow = (g.width - g.insetL - g.insetR) / g.density < 480f && g.height / dp >= 600f
            if (portraitNarrow) {
                // Noc 3: na wąskim pionowym ekranie karty stoją jedna pod drugą, opis bez dzielenia słów.
                requireNight(es.doctrineBtn.all { it.r.width() >= 250 * dp },
                    "$prefix: karty Endless w wąskim rzędzie (${"%.0f".format(es.doctrineBtn[0].r.width() / dp)} dp)")
                @Suppress("UNCHECKED_CAST")
                val lines = field(es, "choiceDescLines").get(es) as Array<Array<String?>>
                val counts = field(es, "choiceDescCount").get(es) as IntArray
                for (i in 0..2) for (k in 0 until counts[i]) requireNight(lines[i][k]?.endsWith("-") != true,
                    "$prefix: opis karty dzieli słowo '${lines[i][k]}'")
                for (i in 0..2) for (k in 0 until counts[i]) requireNight(lines[i][k]?.let { it.startsWith("%") || it.startsWith(":") } != true,
                    "$prefix: wiersz opisu karty zaczyna się od znaku '${lines[i][k]}'")
            }
            for (b in es.doctrineBtn) requireNight(b.r.left >= g.insetL && b.r.right <= g.width - g.insetR &&
                b.r.top >= g.insetT && b.r.bottom <= g.height - g.insetB, "$prefix: karta poza ekranem ${b.r}")
            for (card in arrayOf(EndlessCard.QUARTERMASTER, EndlessCard.MASTER_MASONS)) {
                val desc = L[card.textKey + "_d"]
                val name = L[card.textKey]
                // Nazwa i opis są na kadrze (choćby w zawiniętych wierszach).
                val joined = lastTexts.joinToString(" ") { it.s }
                requireNight(joined.contains(name), "$prefix: brak nazwy karty '$name'")
                val firstWord = desc.split(' ').first()
                requireNight(joined.contains(firstWord), "$prefix: brak opisu karty '$desc'")
            }
        }
    }

    private fun daily(g: Game, prefix: String) {
        for (event in arrayOf(DailyEvent.SIEGE_WORKS, DailyEvent.FORCED_MARCH, DailyEvent.SPOILS_OF_WAR)) {
            g.setScreen(ModeScreen(g, Mode.DAILY, previewDailyDay = firstDay(event)))
            tick(g, 6, 0.1f)
            shot(g, prefix, "dzien_${event.name.lowercase()}")
        }
    }

    private fun achievements(g: Game, prefix: String) {
        val a = AchievementsScreen(g)
        g.setScreen(a); tick(g, 4, 0.1f)
        val idx = Achievements.all.indexOfFirst { it.id == "daily_events_9" }
        val rowStep = 96 * g.ui.dp
        field(a, "scroll").setFloat(a, (idx * rowStep - 20 * g.ui.dp).coerceIn(0f, field(a, "maxScroll").getFloat(a)))
        tick(g)
        shot(g, prefix, "osiagniecia_170")
        // Noc 3: opisy osiągnięć nie maleją do ~8 sp i nie wchodzą na kolumnę nagrody.
        val panel = field(a, "panel").get(a) as RectF
        val rewardLeft = panel.right - 12 * g.ui.dp - 23 * g.ui.dp - 12 * g.ui.dp
        val descs = Achievements.all.map { L["ach_" + it.id + "_d"] }.toSet()
        for (t in lastTexts) if (t.s in descs || descs.any { d -> d.startsWith(t.s) || d.endsWith(t.s) }) {
            if (t.s.length < 6) continue
            requireNight(t.sizePx / g.ui.dp >= 10.4f, "$prefix: opis osiągnięcia ${"%.1f".format(t.sizePx / g.ui.dp)} dp '${t.s}'")
            requireNight(t.r.right <= rewardLeft + 1f, "$prefix: opis osiągnięcia wchodzi na nagrodę '${t.s}'")
        }
    }

    private fun tour(prefix: String, g: Game, extended: Boolean) {
        counter = 0
        tick(g, 5, 0.1f)
        if (g.current !is MenuScreen) g.setScreen(MenuScreen(g))
        tick(g, 3, 0.1f)
        daily(g, prefix)
        achievements(g, prefix)
        g.setScreen(MenuScreen(g)); tick(g, 2)
        g.push(WhatsNewScreen(g)); tick(g, 4, 0.1f)
        (g.current as WhatsNewScreen).onDrag(0f, -100000f); tick(g)
        shot(g, prefix, "co_nowego")
        g.pop()
        endlessCards(g, prefix)
        if (!extended) return
        g.setScreen(MenuScreen(g)); tick(g, 2)
        val st = SettingsScreen(g)
        g.push(st); tick(g, 4, 0.1f); shot(g, prefix, "ustawienia")
        field(st, "scroll").setFloat(st, field(st, "maxScroll").getFloat(st)); st.layout(); tick(g, 2)
        shot(g, prefix, "ustawienia_dol")
        val acc = AccessibilityScreen(g)
        g.push(acc); tick(g, 4, 0.1f); shot(g, prefix, "dostepnosc")
        acc.onDrag(0f, -100000f); tick(g, 2); shot(g, prefix, "dostepnosc_dol")
        turrets(g, prefix)
        // Wyniki bitwy po kapitulacji.
        val bs = g.current as BattleScreen
        bs.showTurrets = false
        repeat(60 * 20) { bs.battle.step() }
        bs.battle.surrender()
        var n = 0
        while (!bs.finished && n++ < 200) tick(g, 1, 0.1f)
        tick(g, 16, 0.25f)
        shot(g, prefix, "wyniki")
        val replay = lastTexts.firstOrNull { it.s == bs.replayEnd.label }
        requireNight(replay != null && replay.sizePx / g.ui.dp >= 9.5f,
            "$prefix: podpis powtórki ${replay?.let { "%.1f".format(it.sizePx / g.ui.dp) }} dp")
        g.setScreen(MenuScreen(g)); tick(g, 2)
    }

    /** Noc 3: spójne nazwy wyzwania dnia i powtórki w poprawionych tekstach. */
    @Test
    fun consistentTerms() {
        val old = L.lang
        try {
            L.setCode("de")
            for (k in arrayOf("ach_daily_events_6_d", "ach_daily_events_9_d", "comm125_codex"))
                assertTrue(k, !L[k].contains("Tagesherausforderung"))
            assertTrue(!L["battle84_kept"].contains("Kills"))
            assertTrue(L["battle84_watch_replay"].contains(L["replay"]))
            L.setCode("tr")
            for (k in arrayOf("ach_daily_events_6_d", "ach_daily_events_9_d"))
                assertTrue(k, L[k].contains("günlük mücadele"))
            L.setCode("uk")
            for (k in arrayOf("ach_daily_events_6_d", "ach_daily_events_9_d", "notification_body"))
                assertTrue(k, L[k].contains("иклик дня"))
        } finally { L.lang = old }
    }

    /** Dziennik: jak często nowe wydarzenia dnia trafiają na sprzeczne modyfikatory (do decyzji). */
    @Test
    fun dailyEventConflictsReport() {
        val sb = StringBuilder()
        val counts = HashMap<String, Int>()
        for (day in Daily.EVENTS_V2_DAY until Daily.EVENTS_V2_DAY + 365 * 2) {
            val cfg = Daily.config(day, Bonuses())
            val e = cfg.dailyEvent ?: continue
            counts[e.name] = (counts[e.name] ?: 0) + 1
            val m = cfg.modifiers
            val clash = when (e) {
                DailyEvent.SIEGE_WORKS -> m.filter { it == com.centurywar.game.Modifier.NO_TURRETS || it == com.centurywar.game.Modifier.EXPENSIVE_TOWERS }
                DailyEvent.FORCED_MARCH -> m.filter { it == com.centurywar.game.Modifier.FAST_UNITS }
                else -> emptyList()
            }
            if (clash.isNotEmpty()) {
                counts[e.name + "+" + clash.joinToString("+")] = (counts[e.name + "+" + clash.joinToString("+")] ?: 0) + 1
                if (sb.length < 4000) sb.append("dzień $day: ${e.name} z ${clash.joinToString()}\n")
            }
        }
        File(out, "noc3_wydarzenia.txt").writeText(counts.toSortedMap().toString() + "\n" + sb)
    }

    @Test
    fun nightTour() {
        val old = L.lang
        try {
            for ((tag, d) in devices) {
                for (lang in arrayOf("pl", "en", "de")) {
                    val g = game(d, lang)
                    tour("${tag}_$lang", g, extended = true)
                    g.history?.close()
                }
                for (lang in arrayOf("tr", "uk")) {
                    val g = game(d, lang)
                    tour("${tag}_$lang", g, extended = false)
                    g.history?.close()
                }
                // Leworęczny HUD: panel wież (pl).
                val g = game(d, "pl", leftHanded = true)
                counter = 50
                turrets(g, "${tag}_pl_lewo")
                g.history?.close()
            }
        } finally {
            L.lang = old
            File(out, "noc3_audit.txt").writeText(audit.toString())
        }
        assertTrue("Regresje nocy 3:\n$regressions", regressions.isEmpty())
    }
}
