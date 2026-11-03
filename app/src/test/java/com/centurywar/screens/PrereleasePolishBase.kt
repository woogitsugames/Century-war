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
import com.centurywar.engine.Screen
import com.centurywar.engine.TouchEvent
import com.centurywar.game.Ai
import com.centurywar.game.Battle
import com.centurywar.game.BattleConfig
import com.centurywar.game.Campaign
import com.centurywar.game.Defs
import com.centurywar.game.Doctrine
import com.centurywar.game.EraArt
import com.centurywar.game.Mode
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
 * Tura 10 „Szlif przed premierą”: przegląd wszystkich ekranów na urządzeniach właściciela
 * (telefon 720p i 20:9, oba ekrany Folda) w językach pl, en i de, na świeżym profilu.
 *
 * Każdy kadr przechodzi przez płótno zapisujące napisy: wykrywa teksty ucięte przez krawędź
 * ekranu lub obszaru przycinania i teksty nachodzące na siebie. Przyciski sprawdza jak
 * NewPlayerJourneyTest (cel dotyku, bezpieczny obszar, zasłonięcie, angielski tekst).
 * Zrzuty: build/screenshots/pre10_<urządzenie>_<język>_<nr>_<ekran>.png, raport pre10_audit_<urządzenie>.txt.
 * Testy (noc 9: jedno urządzenie na klasę): [PrereleasePolishTest], [PrereleasePolishPhone20x9Test],
 * [PrereleasePolishFoldOuterTest], [PrereleasePolishFoldInnerTest].
 * Twarde asercje dotyczą tylko błędów naprawionych w turze 10 (lista [regressions]).
 */
abstract class PrereleasePolishBase {
    protected val out = File("build/screenshots").apply { mkdirs() }

    protected class Device(val w: Int, val h: Int, val density: Float, val insets: IntArray)

    protected val devices = linkedMapOf(
        "phone720" to Device(1600, 720, 2f, intArrayOf(0, 0, 0, 0)),
        "phone20x9" to Device(2400, 1080, 2.7f, intArrayOf(96, 0, 0, 0)),
        "foldOuter" to Device(904, 2316, 2.7f, intArrayOf(0, 88, 0, 0)),
        "foldInner" to Device(2184, 1968, 2.7f, intArrayOf(0, 70, 0, 0)),
    )

    protected val host = object : Host {
        override fun vibrate(ms: Long) {}
        override fun requestText(title: String, current: String, onResult: (String) -> Unit) {}
        override fun applyRenderScale(scale: Float) {}
        override fun applyFrameRate(fps: Int) {}
        override fun exit() {}
    }

    protected val audit = StringBuilder()
    protected val regressions = StringBuilder()
    protected fun require10(ok: Boolean, msg: String) { if (!ok) regressions.append(msg).append('\n') }

    protected val tiny = Canvas(Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888))

    /** Płótno zapisujące prostokąty napisów (we współrzędnych ekranu) i ich przycięcie. */
    protected class TextCanvas(bmp: Bitmap) : Canvas(bmp) {
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
                // Wysokość liter bez zapasu na akcenty: mniej fałszywych nakładek między wierszami.
                val r = RectF(left, y + fm.ascent * 0.8f, left + w, y + fm.descent * 0.6f)
                @Suppress("DEPRECATION") getMatrix(m)
                m.mapRect(r)
                getClipBounds(clipI)
                val clip = RectF(clipI)
                m.mapRect(clip)
                val scale = m.mapRadius(1f)
                texts.add(Drawn(text, r, clip, paint.textSize * scale))
            }
            super.drawText(text, x, y, paint)
        }
    }

    protected fun game(d: Device, lang: String): Game {
        val f = File.createTempFile("pre10", ".json").apply { delete() }
        val profile = Profile(f).apply { this.lang = lang; tutorialDone = true; hintsEnabled = false; eraIntrosEnabled = false }
        return Game(host, profile, Quality.LOW).apply {
            density = d.density
            resize(d.w, d.h, d.w, d.h)
            setInsets(d.insets[0], d.insets[1], d.insets[2], d.insets[3])
            history = BattleHistory(File.createTempFile("pre10h", ".json").apply { delete() })
            historyLoaded = true
            replays = ReplayStore(File.createTempFile("pre10r", "").apply { delete(); mkdirs() })
        }
    }

    protected fun tick(g: Game, n: Int = 1, dt: Float = 1f / 30f) = repeat(n) { g.frame(tiny, dt) }

    protected fun field(o: Any, name: String) = o.javaClass.getDeclaredField(name).apply { isAccessible = true }

    protected var counter = 0
    /** Napisy ostatniego kadru (dla asercji szlifu 2). */
    protected var lastTexts: List<TextCanvas.Drawn> = emptyList()

    /** Kadr + audyt napisów i przycisków. */
    protected fun shot(g: Game, prefix: String, name: String, saveFile: Boolean = true) {
        g.finishScreenTransition()
        val bmp = Bitmap.createBitmap(g.width, g.height, Bitmap.Config.ARGB_8888)
        try {
            val c = TextCanvas(bmp)
            g.frame(c, 0f)
            val where = "$prefix/${"%02d".format(counter)}_$name"
            if (saveFile) File(out, "pre10_${prefix}_${"%02d".format(counter)}_$name.png").outputStream().use {
                check(bmp.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
            counter++
            lastTexts = ArrayList(c.texts)
            auditTexts(g, where, c.texts)
            polish2(g, where, c.texts)
            val cur = g.current
            if (cur is ModeScreen || (cur is CampaignScreen && field(cur, "selected").get(cur) != null))
                require10(g.noticeBanner.isEmpty, "$where: baner powiadomienia na oknie z tytułem na panelu")
            auditButtons(g, where)
        } finally { bmp.recycle() }
    }

    protected fun auditTexts(g: Game, where: String, texts: List<TextCanvas.Drawn>) {
        val dp = g.ui.dp
        val s = g.current
        // Pole bitwy (liczby obrażeń, nazwy jednostek) i mapa kampanii mają własne nakładki.
        val busyField = s is BattleScreen || s is ReplayScreen || s is MenuScreen
        var drawn: List<TextCanvas.Drawn> = texts
        // Okno etapu przykrywa mapę: liczą się napisy narysowane od wstęgi „Etap N”.
        if (s is CampaignScreen && field(s, "selected").get(s) != null) {
            val title = field(s, "infoTitle").get(s) as String
            val i = texts.indexOfLast { it.s == title }
            if (i >= 0) drawn = texts.subList(i, texts.size)
        }
        // Baner powiadomienia leży nad ekranem z założenia (mapa, menu); nie jest kolizją układu.
        val banner = RectF(g.noticeBanner)
        if (!banner.isEmpty) drawn = drawn.filter { !RectF.intersects(it.r, banner) }
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
            if (ix > 3 * dp && iy > 0.35f * minOf(a.r.height(), b.r.height())) {
                audit.append("$where: NAKŁADKA TEKSTU '${a.s}' / '${b.s}'\n")
                // Naprawione w turze 10: okno etapu („brak” pod „Trudność”), karta dnia/tygodnia.
                require10(s !is CampaignScreen && s !is ModeScreen && s !is MissionsScreen,
                    "$where: tekst '${a.s}' nachodzi na '${b.s}'")
            }
        }
        if (s is ModeScreen) for (t in visible) {
            require10(t.sizePx / dp >= 8.5f, "$where: drobny tekst ${"%.1f".format(t.sizePx / dp)} dp '${t.s}'")
            require10(t.r.right <= g.width + 1f && t.r.left >= -1f, "$where: tekst '${t.s}' poza ekranem")
        }
    }

    /**
     * Tura 10, szlif 2 (zatwierdzone poprawki 17–23): kodeks, rekordy, Endless na Foldzie, baner w menu.
     */
    protected fun polish2(g: Game, where: String, texts: List<TextCanvas.Drawn>) {
        val dp = g.ui.dp
        val s = g.current ?: return
        fun sp(t: TextCanvas.Drawn) = t.sizePx / dp / g.ui.textScale
        when (s) {
            is CodexScreen -> {
                // Zakładki kategorii i epok nie maleją do drobnego pisma (także częściowo schowane).
                @Suppress("UNCHECKED_CAST")
                val labels = ((field(s, "categories").get(s) as Array<com.centurywar.engine.Button>).map { it.label } +
                    (field(s, "tabs").get(s) as Array<com.centurywar.engine.Button>).map { it.label }).toSet()
                val ct = field(s, "categoryRailTop").getFloat(s); val et = field(s, "eraRailTop").getFloat(s)
                fun onRail(t: TextCanvas.Drawn) = t.r.centerY() in ct..(ct + 52 * dp) || t.r.centerY() in et..(et + 48 * dp)
                for (t in texts) if (t.s in labels && onRail(t) && RectF.intersects(t.r, t.clip))
                    require10(sp(t) >= 11.5f, "$where: zakładka kodeksu '${t.s}' ma ${"%.1f".format(sp(t))} sp")
                val none = texts.count { it.s == L["cx62_no_variants"] }
                require10(none <= 1, "$where: „${L["cx62_no_variants"]}” narysowane $none razy")
            }
            is RankingsScreen -> {
                @Suppress("UNCHECKED_CAST")
                val tabs = field(s, "tabs").get(s) as Array<com.centurywar.engine.Button>
                // Zakładka Oblężenia (szlif 2: „Oblężenie” zamiast „Oblężenie bez końca”); zewnętrzny
                // ekran Folda (pięć zakładek po ~55 dp) zostaje tylko w dzienniku audytu.
                val tab0 = tabs[0]
                if (g.width / dp >= 500f) for (t in texts) if (t.s == tab0.label && RectF.intersects(t.r, tab0.r))
                    require10(sp(t) >= 11f, "$where: zakładka rekordów '${t.s}' ma ${"%.1f".format(sp(t))} sp")
            }
            is ModeScreen -> if (field(s, "mode").get(s) == Mode.ENDLESS && g.height / dp >= 500f) {
                // Wysoki ekran: między ostatnim wierszem treści a przyciskiem Graj nie ma pustki.
                val start = field(s, "start").get(s) as com.centurywar.engine.Button
                val lowest = texts.filter { it.r.bottom < start.r.top }.maxOfOrNull { it.r.bottom } ?: 0f
                require10(start.r.top - lowest <= 72 * dp,
                    "$where: pusty panel Endless (${"%.0f".format((start.r.top - lowest) / dp)} dp nad Graj)")
            }
            is MenuScreen -> if (!g.noticeBanner.isEmpty)
                require10(g.noticeBanner.bottom <= s.noticeBottom + 0.5f,
                    "$where: baner powiadomienia bliżej niż 6 dp od kafelków menu")
            else -> Unit
        }
    }

    protected fun auditButtons(g: Game, where: String) {
        val s = g.current ?: return
        val dp = g.ui.dp
        val l = g.insetL; val t = g.insetT; val r = g.width - g.insetR; val b = g.height - g.insetB
        for (btn in s.buttons) {
            if (!btn.visible || btn.r.width() <= 0f || btn.r.height() <= 0f) continue
            val id = "${s.javaClass.simpleName}#${btn.id} '${btn.label}'"
            val rr = btn.r
            if ((rr.left < l - 0.5f || rr.top < t - 0.5f || rr.right > r + 0.5f || rr.bottom > b + 0.5f) &&
                rr.right > 0f && rr.left < g.width && rr.bottom > 0f && rr.top < g.height &&
                !(s is CampaignScreen && btn.tag != null))
                audit.append("$where: POZA BEZPIECZNYM OBSZAREM $id $rr\n")
            val hw = (rr.width() + 2 * btn.hitSlop) / dp
            val hh = (rr.height() + 2 * btn.hitSlop) / dp
            if (btn.enabled && (hw < 47.5f || hh < 47.5f)) {
                audit.append("$where: MAŁY CEL $id ${"%.0f".format(hw)}x${"%.0f".format(hh)} dp\n")
                require10(s !is MenuScreen && s !is HintSettingsScreen && s !is WhatsNewScreen,
                    "$where: cel $id ma ${"%.0f".format(hw)}x${"%.0f".format(hh)} dp")
            }
            if (btn.enabled) {
                val first = s.buttons.firstOrNull { it.enabled && it.contains(rr.centerX(), rr.centerY()) }
                if (first != null && first !== btn) audit.append("$where: ZASŁONIĘTY $id przez #${first.id} '${first.label}'\n")
            }
        }
    }

    protected fun tapAt(g: Game, x: Float, y: Float) {
        g.pushTouch(TouchEvent.DOWN, x, y, 0)
        g.pushTouch(TouchEvent.UP, x, y, 0)
        tick(g)
    }

    protected fun open(g: Game, s: Screen, prefix: String, name: String, frames: Int = 6) {
        g.push(s)
        tick(g, frames, 0.1f)
        shot(g, prefix, name)
    }

    protected fun waitReplays(g: Game) {
        var n = 0
        while (field(g.current!!, "loading").getBoolean(g.current!!) && n++ < 200) { Thread.sleep(10); tick(g) }
        tick(g, 2)
    }

    protected fun tour(prefix: String, g: Game) {
        counter = 0
        tick(g, 5, 0.1f)
        // Świeży profil: „Co nowego” nie wyskakuje, ale menu jest pierwszym ekranem.
        if (g.current !is MenuScreen) g.setScreen(MenuScreen(g))
        tick(g, 4, 0.1f)
        shot(g, prefix, "menu")
        val menu = g.current as MenuScreen
        if (menu.portraitMenu) { menu.onDrag(0f, -100000f); tick(g, 2); shot(g, prefix, "menu_dol") }
        // Kampania + info etapu.
        val cs = CampaignScreen(g)
        open(g, cs, prefix, "kampania")
        field(cs, "selected").set(cs, Campaign.stages[0])
        tick(g, 2)
        shot(g, prefix, "kampania_etap1")
        g.pop(); tick(g, 2)
        open(g, ModeScreen(g, Mode.ENDLESS), prefix, "endless")
        g.pop()
        open(g, ModeScreen(g, Mode.DAILY), prefix, "dzien")
        g.pop()
        open(g, ModeScreen(g, Mode.DAILY, true), prefix, "tydzien")
        g.pop()
        val setup = SkirmishSetupScreen(g)
        open(g, setup, prefix, "potyczka")
        field(setup, "scroll").setFloat(setup, field(setup, "maxScroll").getFloat(setup))
        tick(g, 2)
        shot(g, prefix, "potyczka_dol")
        g.pop()
        open(g, ResearchScreen(g), prefix, "badania")
        g.pop()
        val ms = MissionsScreen(g)
        open(g, ms, prefix, "misje")
        // Szlif 2: nagłówek „Misje tygodniowe” stoi na własnym podkładzie.
        val strip = field(ms, "headerStrip").get(ms) as RectF
        val weeklyTitle = lastTexts.firstOrNull { it.s == L["ms_weekly"] }
        require10(weeklyTitle != null && strip.contains(weeklyTitle.r.centerX(), weeklyTitle.r.centerY()),
            "$prefix: nagłówek misji bez podkładu")
        require10(ms.dailyResetText.isNotEmpty() && ms.weeklyResetText.isNotEmpty() &&
            ms.dailyResetText != ms.weeklyResetText, "$prefix: misje bez czasu odnowienia")
        g.pop()
        open(g, RankingsScreen(g), prefix, "rankingi")
        g.replace(StatsScreen(g)); tick(g, 6, 0.1f); shot(g, prefix, "statystyki_pusto")
        (g.current as StatsScreen).let { st -> st.onClick(st.buttons.first { it.id == 11 }) }
        tick(g, 2); shot(g, prefix, "historia_pusto")
        g.replace(ReplayListScreen(g))
        waitReplays(g)
        shot(g, prefix, "powtorki_pusto")
        // Szlif 2: lista w trakcie wczytywania mówi o powtórkach w liczbie mnogiej.
        (g.current as ReplayListScreen).let { rl ->
            field(rl, "loading").setBoolean(rl, true)
            shot(g, prefix, "powtorki_wczytywanie", saveFile = prefix.startsWith("phone720"))
            require10(lastTexts.any { it.s == L["replay151_list_loading"] } && L["replay151_list_loading"] != L["replay_loading"],
                "$prefix: lista powtórek bez tekstu „${L["replay151_list_loading"]}”")
            field(rl, "loading").setBoolean(rl, false)
        }
        g.replace(AchievementsScreen(g)); tick(g, 6, 0.1f); shot(g, prefix, "osiagniecia")
        g.pop()
        open(g, GlobalRankingsScreen(g), prefix, "ranking_globalny")
        g.pop()
        val cx = CodexScreen(g, 0)
        open(g, cx, prefix, "kodeks")
        for (cat in 1 until 10) { cx.showCategory(cat); tick(g, 2); shot(g, prefix, "kodeks_$cat", saveFile = cat in setOf(1, 2, 5, 9)) }
        g.pop()
        val st = SettingsScreen(g)
        open(g, st, prefix, "ustawienia")
        field(st, "scroll").setFloat(st, field(st, "maxScroll").getFloat(st)); st.layout(); tick(g, 2)
        shot(g, prefix, "ustawienia_dol")
        val acc = AccessibilityScreen(g)
        open(g, acc, prefix, "dostepnosc")
        // Przewijanie wywołuje layout i aktualizuje widoczność dolnych kontrolek.
        acc.onDrag(0f, -100000f); tick(g, 2)
        shot(g, prefix, "dostepnosc_dol")
        require10(acc.buttons.first { it.id == 42 }.visible &&
            acc.buttons.first { it.id == 43 }.visible,
            "$prefix: przewinięcie nie odsłoniło dolnych przełączników")
        require10(lastTexts.any { it.s == L["access_reduce_flash"] } &&
            lastTexts.any { it.s == L["access_left_handed_hud"] },
            "$prefix: kadr dostępności nie pokazuje dolnych etykiet")
        g.pop()
        open(g, HintSettingsScreen(g), prefix, "podpowiedzi"); g.pop()
        open(g, BannerScreen(g), prefix, "sztandar"); g.pop()
        open(g, PlayGamesScreen(g), prefix, "play_games"); g.pop()
        open(g, AboutScreen(g), prefix, "o_grze")
        open(g, WhatsNewScreen(g), prefix, "co_nowego"); g.pop()
        open(g, PrivacySummaryScreen(g), prefix, "prywatnosc"); g.pop()
        g.pop(); g.pop()

        // Bitwa: HUD, panele, nakładki, pauza, wyniki i wykres.
        g.profile.eraIntrosEnabled = true
        val steam = Defs.eraOf(EraArt.STEAM)
        val bs = BattleScreen(g, BattleConfig(Mode.SKIRMISH, 7L, 1f, startEra = 1, maxEra = 7))
        g.setScreen(bs)
        tick(g, 3)
        shot(g, prefix, "bitwa_epoka")
        g.profile.eraIntrosEnabled = false
        tapAt(g, g.width / 2f, g.height / 2f); tick(g, 6, 0.2f)
        val b = bs.battle
        b.playerAi = Ai(b, 0)
        b.player.gold = 900f
        tick(g, 40 * 10, 1f / 30f)
        b.playerAi = null
        if (b.pendingVariant != null) { shot(g, prefix, "bitwa_wariant"); bs.onClick(bs.variantBtn[0]); tick(g) }
        if (b.pendingChoice != null) bs.onClick(bs.doctrineBtn[0])
        tick(g, 2)
        shot(g, prefix, "bitwa_hud")
        bs.showTurrets = true; tick(g); shot(g, prefix, "bitwa_wieze"); bs.showTurrets = false
        bs.showUpgrade = 0; tick(g); shot(g, prefix, "bitwa_ulepszenie"); bs.showUpgrade = -1
        b.pendingChoice = arrayOf(Doctrine.ECONOMY, Doctrine.FURY, Doctrine.ARSENAL)
        tick(g); shot(g, prefix, "bitwa_doktryny"); b.pendingChoice = null
        bs.onClick(bs.pauseBtn); tick(g); shot(g, prefix, "bitwa_pauza")
        bs.paused = false
        b.surrender()
        var n = 0
        while (!bs.finished && n++ < 200) tick(g, 1, 0.1f)
        tick(g, 16, 0.25f)
        shot(g, prefix, "wyniki")
        bs.onClick(bs.resultChartTab); tick(g, 4, 0.1f); shot(g, prefix, "wyniki_wykres")

        val vs = BattleScreen(g, BattleConfig(Mode.SKIRMISH, 9900L, 1f, startEra = steam, maxEra = steam))
        g.setScreen(vs); tick(g, 3)
        if (vs.battle.pendingVariant != null) shot(g, prefix, "bitwa_wariant_start")

        val es = BattleScreen(g, BattleConfig(Mode.ENDLESS, 2026L, 1f))
        g.setScreen(es)
        val e = es.battle
        repeat(5) { e.waveTimer = 0f; e.step() }
        tick(g, 2)
        if (e.pendingCards != null) shot(g, prefix, "endless_karty")
        g.setScreen(MenuScreen(g)); tick(g, 3)
    }

    /**
     * Przegląd na jednym urządzeniu z [devices] (pl, en, de); raport pre10_audit_<urządzenie>.txt.
     * Noc 9: dawna pętla po czterech urządzeniach jest rozłożona na cztery klasy testowe.
     */
    protected fun prereleaseTourOn(device: String) {
        val old = L.lang
        try {
            for (lang in arrayOf("pl", "en", "de")) {
                val g = game(devices.getValue(device), lang)
                tour("${device}_$lang", g)
                g.history?.close()
            }
        } finally {
            L.lang = old
            File(out, "pre10_audit_$device.txt").writeText(audit.toString())
        }
        assertTrue("Regresje szlifu przed premierą:\n$regressions", regressions.isEmpty())
    }
}
