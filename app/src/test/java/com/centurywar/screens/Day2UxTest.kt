package com.centurywar.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.centurywar.BuildConfig
import com.centurywar.engine.Button
import com.centurywar.engine.Game
import com.centurywar.engine.Host
import com.centurywar.engine.L
import com.centurywar.engine.Quality
import com.centurywar.game.Bonuses
import com.centurywar.game.Campaign
import com.centurywar.meta.BattleHistory
import com.centurywar.meta.Profile
import com.centurywar.meta.ReplayStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.nio.file.Files

/**
 * Dzień 2: medale za bitwę i za osiągnięcia osobno na wynikach, przycisk „Co nowego” w menu
 * po aktualizacji. Geometria (bez kolizji, w bezpiecznym obszarze), rozmiar tekstu i 10 języków.
 * Zrzuty podglądu: build/screenshots/day2_*.png (nie są wzorcami).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class Day2UxTest {
    private val out = File("build/screenshots").apply { mkdirs() }

    internal class Device(val w: Int, val h: Int, val density: Float, val insets: IntArray)

    private val host = object : Host {
        override fun vibrate(ms: Long) {}
        override fun requestText(title: String, current: String, onResult: (String) -> Unit) {}
        override fun applyRenderScale(scale: Float) {}
        override fun applyFrameRate(fps: Int) {}
        override fun exit() {}
    }

    /** Płótno zapisujące napisy (prostokąt na ekranie i rozmiar w px). */
    internal class TextCanvas(bmp: Bitmap) : Canvas(bmp) {
        class Drawn(val s: String, val r: RectF, val sizePx: Float)
        val texts = ArrayList<Drawn>()
        private val m = android.graphics.Matrix()

        override fun drawText(text: String, x: Float, y: Float, paint: Paint) {
            record(text, x, y, paint)
            super.drawText(text, x, y, paint)
        }

        override fun drawText(text: CharArray, index: Int, count: Int, x: Float, y: Float, paint: Paint) {
            record(String(text, index, count), x, y, paint)
            super.drawText(text, index, count, x, y, paint)
        }

        private fun record(text: String, x: Float, y: Float, paint: Paint) {
            if (paint.style != Paint.Style.FILL || text.isBlank() || android.graphics.Color.alpha(paint.color) <= 60) return
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
            texts.add(Drawn(text, r, paint.textSize * m.mapRadius(1f)))
        }
    }

    private val resultDevices = linkedMapOf(
        "phone720" to Device(1600, 720, 2f, intArrayOf(0, 0, 0, 0)),
        "phone20x9" to Device(2400, 1080, 2.7f, intArrayOf(96, 0, 0, 0)),
        "foldOuter" to Device(904, 2316, 2.7f, intArrayOf(0, 88, 0, 0)),
        "foldInner" to Device(2184, 1968, 2.7f, intArrayOf(0, 70, 0, 0)),
    )

    private fun profileFile(): File = File(Files.createTempDirectory("cw-day2").toFile(), "profile.json")

    private fun game(d: Device, profile: Profile): Game = Game(host, profile, Quality.LOW).apply {
        density = d.density
        resize(d.w, d.h, d.w, d.h)
        setInsets(d.insets[0], d.insets[1], d.insets[2], d.insets[3])
        history = BattleHistory(File.createTempFile("day2h", ".json").apply { delete() })
        historyLoaded = true
        replays = ReplayStore(File.createTempFile("day2r", "").apply { delete(); mkdirs() })
    }

    private fun render(g: Game, name: String?): TextCanvas {
        g.finishScreenTransition()
        val bmp = Bitmap.createBitmap(g.width, g.height, Bitmap.Config.ARGB_8888)
        val c = TextCanvas(bmp)
        g.frame(c, 0f)
        if (name != null) File(out, "day2_$name.png").outputStream().use {
            assertTrue(bmp.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
        bmp.recycle()
        return c
    }

    private fun field(o: Any, name: String) = o.javaClass.getDeclaredField(name).apply { isAccessible = true }

    private val tiny = Canvas(Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888))

    private fun overlap(a: RectF, b: RectF, dp: Float): Boolean {
        val ix = minOf(a.right, b.right) - maxOf(a.left, b.left)
        val iy = minOf(a.bottom, b.bottom) - maxOf(a.top, b.top)
        return ix > 1 * dp && iy > 1 * dp
    }

    /** Pierwsza wygrana etapu 1 nowym profilem: nagroda bitwy i osiągnięcia (np. pierwsza wygrana). */
    @Test
    fun resultsShowBattleAndAchievementMedalsSeparately() {
        val oldLang = L.lang
        try {
            for ((tag, d) in resultDevices) for (lang in L.CODES) {
                L.setCode(lang)
                val p = Profile(profileFile()).apply {
                    this.lang = lang; tutorialDone = true; hintsEnabled = false; eraIntrosEnabled = false
                }
                val g = game(d, p)
                val bs = BattleScreen(g, Campaign.config(Campaign.stages[0], Bonuses(), 175L))
                g.setScreen(bs)
                bs.battle.time = 200f
                bs.battle.enemy.baseHp = 0f
                repeat(40) { g.frame(tiny, 1f / 30f) }
                val where = "$tag/$lang"
                assertTrue("$where: bitwa wygrana", bs.battle.over && bs.battle.winner == 0)
                assertTrue("$where: nagroda za bitwę", bs.reward > 0)
                assertTrue("$where: osiągnięcia dały medale", bs.achievementMedals > 0)
                assertEquals("$where: suma", bs.totalMedalsGained, bs.reward + bs.achievementMedals)
                val save = lang in setOf("pl", "en", "de") || (tag == "foldOuter" && lang in setOf("fr", "uk", "pt"))
                val c = render(g, if (save) "results_${tag}_$lang" else null)
                val dp = g.ui.dp
                val battleText = "+" + L.plural("medals_n", bs.reward.toLong())
                val achText = "+" + L.plural("medals_n", bs.achievementMedals.toLong())
                val panel = field(bs, "resultPanel").get(bs) as RectF
                val restart = (field(bs, "restartEnd").get(bs) as Button).r
                val wanted = listOf(L["res175_battle"], battleText, L["res175_achievements"], achText)
                val drawn = wanted.map { s -> c.texts.lastOrNull { it.s == s } }
                for ((i, t) in drawn.withIndex()) {
                    assertTrue("$where: brak napisu '${wanted[i]}'", t != null)
                    t!!
                    assertTrue("$where: '${t.s}' poza panelem ${t.r} / $panel",
                        t.r.left >= panel.left + 10 * dp && t.r.right <= panel.right - 10 * dp)
                    assertTrue("$where: '${t.s}' wchodzi na przyciski", t.r.bottom <= restart.top)
                    assertTrue("$where: '${t.s}' ma ${t.sizePx / dp} sp", t.sizePx / dp >= 10f - 0.05f)
                }
                assertFalse("$where: suma zamiast osobnych kwot",
                    c.texts.any { it.s == "+" + L.plural("medals_n", bs.totalMedalsGained.toLong()) })
                // Napisy panelu wyników nie nachodzą na siebie.
                val inPanel = c.texts.filter { RectF.intersects(it.r, panel) && it.r.top > panel.top + 60 * dp }
                for (i in inPanel.indices) for (j in i + 1 until inPanel.size) {
                    val a = inPanel[i]; val b = inPanel[j]
                    assertFalse("$where: '${a.s}' nachodzi na '${b.s}'", overlap(a.r, b.r, dp))
                }
                assertEquals("$where: obcięte wiersze", 0, bs.resultRowsClipped)
                // Etykieta i kwota osiągnięć w jednym wierszu, pod nagrodą za bitwę.
                assertTrue("$where: osiągnięcia pod bitwą", drawn[2]!!.r.top >= drawn[0]!!.r.bottom - 1f)
            }
        } finally {
            L.lang = oldLang
        }
    }

    /** Bez osiągnięć wynik wygląda jak dawniej: jeden wiersz „Nagroda”. */
    @Test
    fun resultsWithoutAchievementsKeepSingleRewardRow() {
        val p = Profile(profileFile()).apply { lang = "pl"; tutorialDone = true; hintsEnabled = false; eraIntrosEnabled = false }
        L.setCode("pl")
        val g = game(resultDevices.getValue("phone720"), p)
        val first = BattleScreen(g, Campaign.config(Campaign.stages[0], Bonuses(), 176L))
        g.setScreen(first)
        first.battle.time = 200f
        first.battle.enemy.baseHp = 0f
        repeat(40) { g.frame(tiny, 1f / 30f) }
        // Druga identyczna wygrana nie odblokowuje już tych samych osiągnięć.
        val bs = BattleScreen(g, Campaign.config(Campaign.stages[0], Bonuses(), 177L))
        g.setScreen(bs)
        bs.battle.time = 200f
        bs.battle.enemy.baseHp = 0f
        repeat(40) { g.frame(tiny, 1f / 30f) }
        if (bs.achievementMedals != 0) return // osiągnięcie za serię: przypadek z dwoma wierszami pokrywa test wyżej
        val c = render(g, null)
        assertTrue(c.texts.any { it.s == L["reward"] })
        assertFalse(c.texts.any { it.s == L["res175_achievements"] || it.s == L["res175_battle"] })
    }

    // ------------------------------------------------------------------ „Co nowego”

    private val menuDevices = linkedMapOf(
        "phone720" to Device(1600, 720, 2f, intArrayOf(0, 0, 0, 0)),
        "phone20x9" to Device(2400, 1080, 2.7f, intArrayOf(96, 0, 0, 0)),
        "foldOuter" to Device(904, 2316, 2.7f, intArrayOf(0, 88, 0, 0)),
        "foldInner" to Device(2184, 1968, 2.7f, intArrayOf(0, 70, 0, 0)),
        "portrait720x1280" to Device(720, 1280, 2f, intArrayOf(16, 28, 24, 36)),
        "portrait1080x1600" to Device(1080, 1600, 3f, intArrayOf(24, 42, 36, 54)),
    )

    /** Profil „po aktualizacji”: zapis z dysku z notatkami starszej wersji. */
    private fun updatedProfile(lang: String, name: String = "Gracz", f: File = profileFile()): Profile {
        Profile(f).apply { this.lang = lang; tutorialDone = true; this.name = name; hintsSeen = Profile.HINT_VALID_MASK; save() }
        Profile.awaitHintWrites()
        return Profile(f).also { it.load(); it.whatsNewSeenVersion = "0.0.9" }
    }

    private fun checkMenuBadge(g: Game, menu: MenuScreen, where: String, name: String?) {
        val dp = g.ui.dp
        val b = menu.whatsNew
        assertTrue("$where: przycisk „Co nowego” widoczny", b.visible)
        val safe = RectF(g.insetL, g.insetT, g.width - g.insetR, g.height - g.insetB)
        assertTrue("$where: w bezpiecznym obszarze ${b.r}", safe.contains(b.r))
        assertTrue("$where: cel dotyku 48 dp",
            b.r.width() + 2 * b.hitSlop >= 47.5f * dp && b.r.height() + 2 * b.hitSlop >= 47.5f * dp)
        val touch = RectF(b.r.left - b.hitSlop, b.r.top - b.hitSlop, b.r.right + b.hitSlop, b.r.bottom + b.hitSlop)
        for (o in menu.buttons) {
            if (o === b || !o.visible || o.r.width() <= 0f) continue
            assertFalse("$where: „Co nowego” nachodzi na przycisk #${o.id} '${o.label}' ${o.r}",
                RectF.intersects(touch, o.r))
        }
        // Baner powiadomień w menu (np. seria logowań).
        g.showNotice("Seria logowania 3/7 · otrzymano 30 medali")
        val c = render(g, name)
        val banner = RectF(g.noticeBanner)
        assertFalse("$where: brak banera w teście", banner.isEmpty)
        assertFalse("$where: „Co nowego” pod banerem $banner", RectF.intersects(touch, banner))
        // Przy długim imieniu w pionie przycisk jest samą ikoną (podpis pusty).
        val label = if (b.label.isEmpty()) null else c.texts.lastOrNull { it.s == b.label }
        if (b.label.isNotEmpty()) {
            assertTrue("$where: brak podpisu", label != null)
            assertTrue("$where: podpis ${label!!.sizePx / dp} sp", label.sizePx / dp >= 10f - 0.05f)
            assertTrue("$where: podpis mieści się w przycisku",
                label.r.left >= b.r.left - 1f && label.r.right <= b.r.right + 1f)
        } else assertTrue("$where: sama ikona tylko w pionie", menu.portraitMenu)
        // Napisy menu (tabliczka gracza, logo, podpisy Kodeksu i Ustawień) nie wchodzą na przycisk.
        for (t in c.texts) {
            if (t === label || RectF.intersects(t.r, banner)) continue
            assertFalse("$where: napis '${t.s}' na przycisku", overlap(t.r, b.r, dp))
        }
        val plateRight = g.insetL + 12 * dp + 48 * dp
        assertTrue("$where: tabliczka gracza", b.r.left > plateRight || b.r.top > g.insetT + 58 * dp)
    }

    @Test
    fun whatsNewButtonAfterUpdateOnAllDevices() {
        val oldLang = L.lang
        try {
            for ((tag, d) in menuDevices) for (lang in L.CODES) {
                L.setCode(lang)
                val p = updatedProfile(lang, when (lang) { "de" -> "WWWWWWWWWWWWWWWW"; "uk" -> "Bardzo Długie Im"; else -> "Gracz" })
                val g = game(d, p)
                val menu = g.current
                assertTrue("$tag: start w menu, bez okna nowości", menu is MenuScreen)
                menu as MenuScreen
                menu.update(0f)
                val save = lang in setOf("pl", "en", "de")
                checkMenuBadge(g, menu, "$tag/$lang", if (save) "menu_${tag}_$lang" else null)
                if (menu.portraitMenu) {
                    // Przewinięta lista nie wchodzi pod przycisk (przycisk leży nad listą).
                    menu.onDrag(0f, -100000f)
                    assertTrue("$tag/$lang: lista pod przyciskiem", menu.portraitTop >= menu.whatsNew.r.bottom)
                }
            }
        } finally {
            L.lang = oldLang
        }
    }

    @Test
    fun whatsNewButtonOpensHistoryAndDisappearsAfterReading() {
        L.setCode("pl")
        val file = profileFile()
        val p = updatedProfile("pl", f = file)
        val g = game(menuDevices.getValue("phone720"), p)
        val menu = g.current as MenuScreen
        menu.update(0f)
        assertTrue(menu.whatsNew.visible)
        assertEquals(L["menu175_whats_new_desc"], menu.accessibilityLabel(menu.whatsNew))
        // Dotyk w środek przycisku.
        val r = menu.whatsNew.r
        for (action in intArrayOf(com.centurywar.engine.TouchEvent.DOWN, com.centurywar.engine.TouchEvent.UP))
            menu.handle(com.centurywar.engine.TouchEvent().apply { this.action = action; x = r.centerX(); y = r.centerY() })
        g.finishScreenTransition()
        assertTrue("historia zmian", g.current is WhatsNewScreen)
        assertEquals(BuildConfig.VERSION_NAME, p.whatsNewSeenVersion)
        g.back()
        g.finishScreenTransition()
        assertTrue(g.current === menu)
        menu.update(0f)
        assertFalse("po przeczytaniu znika", menu.whatsNew.visible)
        Profile.awaitHintWrites()
        // Kolejne uruchomienie: bez przycisku.
        val again = Profile(file).also { it.load() }
        assertEquals(BuildConfig.VERSION_NAME, again.whatsNewSeenVersion)
        val g2 = game(menuDevices.getValue("phone720"), again)
        (g2.current as MenuScreen).update(0f)
        assertFalse((g2.current as MenuScreen).whatsNew.visible)
    }

    @Test
    fun freshInstallHasNoWhatsNewButton() {
        L.setCode("pl")
        val g = game(menuDevices.getValue("phone720"), Profile(profileFile()).also { it.load() })
        val menu = g.current as MenuScreen
        menu.update(0f)
        assertFalse(menu.whatsNew.visible)
    }

    // ------------------------------------------------------------------ górny pasek (Fold)

    private fun topBarTexts(d: Device, lang: String, endless: Boolean, wave: Int, leftHanded: Boolean,
                            name: String?, time: Float = 300f): Triple<Game, BattleScreen, TextCanvas> {
        L.setCode(lang)
        val p = Profile(profileFile()).apply {
            this.lang = lang; tutorialDone = true; hintsEnabled = false; eraIntrosEnabled = false
            leftHandedHud = leftHanded
        }
        val g = game(d, p)
        val cfg = if (endless) com.centurywar.game.BattleConfig(com.centurywar.game.Mode.ENDLESS, 5L, 1f)
            else Campaign.config(Campaign.stages[3], Bonuses(), 5L)
        val bs = BattleScreen(g, cfg)
        g.setScreen(bs)
        if (endless) bs.battle.wave = wave else bs.battle.time = time
        return Triple(g, bs, render(g, name))
    }

    /** Zewnętrzny Fold: „Presja oblężnicza” nie wchodzi na HP baz, numer fali co najmniej 10 sp. */
    @Test
    fun foldOuterTopBarSiegeAndWaveLabels() {
        val oldLang = L.lang
        val fold = resultDevices.getValue("foldOuter")
        try {
            for (lang in L.CODES) for (left in listOf(false, true)) {
                val tag = "$lang${if (left) "_left" else ""}"
                val save = !left && lang in setOf("pl", "de") || left && lang == "pl"
                val (g, bs, c) = topBarTexts(fold, lang, endless = false, wave = 0, leftHanded = left,
                    name = if (save) "topbar_foldOuter_siege_$tag" else null)
                val dp = g.ui.dp
                assertTrue(bs.compactTop)
                val siege = c.texts.last { it.s.startsWith(L["siege_pressure"].substringBefore('%').trim()) }
                assertTrue("$tag: presja ${siege.sizePx / dp} sp", siege.sizePx / dp >= 10f - 0.05f)
                val hp = c.texts.filter { it.s == bs.battle.player.baseHp.toInt().toString() ||
                    it.s == bs.battle.enemy.baseHp.toInt().toString() }
                assertEquals("$tag: dwie liczby HP", 2, hp.size)
                val barTexts = c.texts.filter { it.r.top < bs.topBar.bottom && it !== siege }
                for (t in barTexts) assertFalse("$tag: presja nachodzi na '${t.s}'", overlap(siege.r, t.r, dp))
                val safe = RectF(g.insetL, g.insetT, g.width - g.insetR, g.height - g.insetB)
                assertTrue("$tag: presja poza ekranem", safe.contains(siege.r))
                assertTrue("$tag: presja w pasku", siege.r.bottom <= bs.topBar.bottom)
                assertFalse(bs.topBarView.sideLabelDrawn.isEmpty)

                for (wave in intArrayOf(23, 123)) {
                    val (g2, bs2, c2) = topBarTexts(fold, lang, endless = true, wave = wave, leftHanded = left,
                        name = if (save && wave == 23) "topbar_foldOuter_endless_$tag" else null)
                    val label = L.f("wave", wave)
                    val t = c2.texts.last { it.s == label }
                    assertTrue("$tag: fala ${t.sizePx / g2.ui.dp} sp", t.sizePx / g2.ui.dp >= 10f - 0.05f)
                    for (o in c2.texts.filter { it.r.top < bs2.topBar.bottom && it !== t })
                        assertFalse("$tag: fala nachodzi na '${o.s}'", overlap(t.r, o.r, g2.ui.dp))
                    assertTrue(RectF(g2.insetL, g2.insetT, g2.width - g2.insetR, g2.height.toFloat()).contains(t.r))
                }
            }
        } finally {
            L.lang = oldLang
        }
    }

    /** Pozostałe ekrany: presja i fala zostają pod minimapą jak dotąd (bez bocznego pola). */
    @Test
    fun otherDevicesKeepTopBarLabels() {
        val oldLang = L.lang
        try {
            for ((tag, d) in resultDevices) {
                if (tag == "foldOuter") continue
                for (lang in L.CODES) for (endless in listOf(false, true)) {
                    val (_, bs, _) = topBarTexts(d, lang, endless, 123, false, null)
                    assertTrue("$tag/$lang: boczne pole użyte", bs.topBarView.sideLabelDrawn.isEmpty)
                }
            }
        } finally {
            L.lang = oldLang
        }
    }

    /**
     * Dzień 4 (zgoda właściciela): na zewnętrznym Foldzie, gdy presja trafia do bocznego pola,
     * pod minimapą wraca zegar bitwy — co najmniej 10 sp, bez kolizji, też przy większym tekście.
     * Zrzut podglądu: build/screenshots/day2_day4_foldOuter_siege_clock_*.png.
     */
    @Test
    fun foldOuterSiegeKeepsClockUnderMini() {
        val oldLang = L.lang
        val fold = resultDevices.getValue("foldOuter")
        try {
            for (lang in L.CODES) for (left in listOf(false, true)) for (time in floatArrayOf(300f, 754f)) {
                val tag = "$lang${if (left) "_left" else ""}_${time.toInt()}"
                val save = !left && time == 300f && lang in setOf("pl", "en", "de")
                val (g, bs, c) = topBarTexts(fold, lang, endless = false, wave = 0, leftHanded = left,
                    name = if (save) "day4_foldOuter_siege_clock_$lang" else null, time = time)
                val dp = g.ui.dp
                assertTrue("$tag: presja", bs.battle.siegePressure() > 1f)
                assertFalse("$tag: presja w bocznym polu", bs.topBarView.sideLabelDrawn.isEmpty)
                val sec = time.toInt()
                val clockText = "${sec / 60}:${(sec % 60).toString().padStart(2, '0')}"
                val clock = c.texts.singleOrNull { it.s == clockText }
                    ?: throw AssertionError("$tag: brak zegara '$clockText' w ${c.texts.map { it.s }}")
                assertTrue("$tag: zegar ${clock.sizePx / dp} sp", clock.sizePx / dp >= 10f - 0.05f)
                assertTrue("$tag: zegar pod minimapą", clock.r.top >= bs.mini.bottom - 1 * dp &&
                    clock.r.bottom <= bs.topBar.bottom)
                assertEquals("$tag: zegar na środku minimapy", bs.mini.centerX(), clock.r.centerX(), 2 * dp)
                for (t in c.texts.filter { it.r.top < bs.topBar.bottom && it !== clock })
                    assertFalse("$tag: zegar nachodzi na '${t.s}'", overlap(clock.r, t.r, dp))
            }
        } finally {
            L.lang = oldLang
        }
    }

    /** Pozostałe ekrany: przy presji pod minimapą nadal napis presji (bez zegara). */
    @Test
    fun otherDevicesShowSiegeInsteadOfClock() {
        val oldLang = L.lang
        try {
            for ((tag, d) in resultDevices) {
                if (tag == "foldOuter") continue
                for (lang in listOf("pl", "en", "de")) {
                    val (_, bs, c) = topBarTexts(d, lang, endless = false, wave = 0, leftHanded = false, name = null)
                    assertTrue(bs.topBarView.sideLabelDrawn.isEmpty)
                    assertTrue("$tag/$lang: zegar", c.texts.none { it.s == "5:00" })
                    assertTrue("$tag/$lang: presja", c.texts.any { it.s == L.f("siege_pressure", bs.battle.siegePressure()) })
                }
            }
        } finally {
            L.lang = oldLang
        }
    }
}
