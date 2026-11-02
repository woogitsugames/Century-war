package com.centurywar.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import com.centurywar.engine.Button
import com.centurywar.engine.Game
import com.centurywar.engine.Host
import com.centurywar.engine.L
import com.centurywar.engine.Quality
import com.centurywar.engine.Screen
import com.centurywar.engine.TouchEvent
import com.centurywar.game.Battle
import com.centurywar.game.Campaign
import com.centurywar.meta.Profile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Tura 9: ścieżka świeżego profilu po polsku oczami nowego gracza — od menu przez samouczek,
 * wyniki, Badania, przegraną kampanię, Endless i Ustawienia. Na każdym ekranie audyt
 * przycisków: wycięcie/ramka, cel dotyku, zasłonięcie, puste etykiety, angielski tekst.
 * Zrzuty: build/screenshots/journey_<urządzenie>_<nr>_<ekran>.png, raport: journey_audit.txt.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class NewPlayerJourneyTest {
    private val out = File("build/screenshots").apply { mkdirs() }

    /**
     * szerokość, wysokość, gęstość, wcięcia l/t/r/b (wycięcie aparatu, pasek nawigacji).
     * Telefon 720×1280 w pionie pominięty: manifest wymusza sensorLandscape, a pion na telefonie
     * nie występuje (pion obsługuje tylko zewnętrzny ekran Folda).
     */
    private class Device(val w: Int, val h: Int, val density: Float, val insets: IntArray)

    private val devices = linkedMapOf(
        "phone720" to Device(1600, 720, 1.8f, intArrayOf(0, 0, 0, 0)),
        "phone20x9" to Device(2400, 1080, 2.7f, intArrayOf(96, 0, 0, 0)),
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
    /** Błędy, które Tura 9 naprawiła — muszą pozostać naprawione (sprawdzane na końcu). */
    private val regressions = StringBuilder()

    private fun require9(ok: Boolean, msg: String) { if (!ok) regressions.append(msg).append('\n') }
    private val tiny = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
    private val tinyCanvas = Canvas(tiny)

    /** Teksty angielskie, których polska wersja jest inna — wykrywa mieszanie języków. */
    private val englishOnly: Map<String, String> by lazy {
        @Suppress("UNCHECKED_CAST")
        val table = L.javaClass.getDeclaredField("table\$delegate").let {
            it.isAccessible = true
            (it.get(L) as Lazy<*>).value as Map<String, Array<out String>>
        }
        val m = HashMap<String, String>()
        val polish = HashSet<String>()
        for (v in table.values) v.getOrNull(L.PL)?.let { polish.add(it) }
        for ((k, v) in table) {
            val en = v.getOrNull(L.EN) ?: continue
            val pl = v.getOrNull(L.PL) ?: continue
            if (en.length >= 4 && pl.isNotEmpty() && en != pl && en.any { it.isLetter() } && en !in polish) m[en] = k
        }
        m
    }

    private fun game(d: Device): Game {
        val f = File.createTempFile("journey", ".json").apply { delete() }
        val profile = Profile(f).apply { lang = "pl" }
        return Game(host, profile, Quality.LOW).apply {
            density = d.density
            resize(d.w, d.h, d.w, d.h)
            setInsets(d.insets[0], d.insets[1], d.insets[2], d.insets[3])
        }
    }

    private fun tick(g: Game, dt: Float = 1f / 30f, n: Int = 1) = repeat(n) { g.frame(tinyCanvas, dt) }

    private fun shot(g: Game, tag: String, name: String) {
        g.finishScreenTransition()
        val bmp = Bitmap.createBitmap(g.width, g.height, Bitmap.Config.ARGB_8888)
        try {
            g.frame(Canvas(bmp), 0f)
            File(out, "journey_${tag}_$name.png").outputStream().use {
                check(bmp.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
        } finally { bmp.recycle() }
        auditScreen(g, "$tag/$name")
        checkHint(g, "$tag/$name")
    }

    /** Heurystyki nowego gracza; wyniki trafiają do raportu (twarde asercje są niżej). */
    private fun auditScreen(g: Game, where: String) {
        val s = g.current ?: return
        val dp = g.ui.dp
        val l = g.insetL; val t = g.insetT; val r = g.width - g.insetR; val b = g.height - g.insetB
        for (btn in s.buttons) {
            if (!btn.visible || btn.r.width() <= 0f || btn.r.height() <= 0f) continue
            val id = "${s.javaClass.simpleName}#${btn.id} '${btn.label}'"
            val rr = btn.r
            if (rr.left < l - 0.5f || rr.top < t - 0.5f || rr.right > r + 0.5f || rr.bottom > b + 0.5f) {
                // Kafelki przewijanej mapy kampanii mogą wystawać poza krawędź — to nie błąd.
                if (rr.right > 0f && rr.left < g.width && rr.bottom > 0f && rr.top < g.height &&
                    !(s is CampaignScreen && btn.tag != null))
                    audit.append("$where: POZA BEZPIECZNYM OBSZAREM $id $rr\n")
            }
            val hw = (rr.width() + 2 * btn.hitSlop) / dp
            val hh = (rr.height() + 2 * btn.hitSlop) / dp
            if (btn.enabled && (hw < 47.5f || hh < 47.5f)) audit.append("$where: MAŁY CEL $id ${"%.0f".format(hw)}x${"%.0f".format(hh)} dp\n")
            if (btn.tag == null && btn.label.isEmpty() && btn.iconId < 0 && btn.icon == null && btn.sub.isEmpty())
                audit.append("$where: PUSTA ETYKIETA $id\n")
            for (text in listOf(btn.label, btn.sub, btn.tooltipTitle, btn.tooltipBody)) {
                val k = englishOnly[text]
                if (k != null) audit.append("$where: ANGIELSKI '$text' ($k) w $id\n")
            }
            if (btn.id == HintOverlay.OK_ID || btn === (s as? BattleScreen)?.resultStatsTab ||
                btn === (s as? BattleScreen)?.resultChartTab)
                require9(hh >= 47.5f, "$where: cel $id ma ${"%.0f".format(hh)} dp wysokości (< 48)")
            if (s is MenuScreen && btn.iconId >= 0 && btn.sub.isNotEmpty() && btn.r.width() > btn.r.height() * 1.7f && btn.r.height() >= 42 * dp) {
                // Układ Ui.button: medalion po lewej, podpis od lewej; podpis nie może wyjść poza przycisk.
                val tx = rr.left + rr.height() * 0.88f + 8 * dp
                val avail = rr.right - 8 * dp - tx
                // MenuScreen rysuje kafelki z labelDp = 17.
                val size = g.ui.subTextSize(btn, 17f * 0.74f, avail)
                val iconW = if (btn.subIcon >= 0) 17f * 0.74f * dp * 1.25f + 3 * dp else 0f
                require9(iconW + g.ui.measure(btn.sub, size, true) <= avail + 1f,
                    "$where: podpis '${btn.sub}' wychodzi poza przycisk $id")
            }
            if (btn.enabled) {
                val first = s.buttons.firstOrNull { it.enabled && it.contains(rr.centerX(), rr.centerY()) }
                if (first != null && first !== btn) audit.append("$where: ZASŁONIĘTY $id przez #${first.id} '${first.label}'\n")
            }
        }
    }

    /** Opisy kart doktryn mieszczą się wewnątrz złotej lamówki karty. */
    private fun checkChoiceText(g: Game, tag: String, bs: BattleScreen) {
        val dp = g.ui.dp
        @Suppress("UNCHECKED_CAST")
        val lines = bs.javaClass.getDeclaredField("choiceDescLines").apply { isAccessible = true }.get(bs) as Array<Array<String?>>
        val counts = bs.javaClass.getDeclaredField("choiceDescCount").apply { isAccessible = true }.get(bs) as IntArray
        for (i in 0..2) {
            val room = bs.doctrineBtn[i].r.width() - 2 * (com.centurywar.engine.Ui.PANEL_TRIM_INSET + 4f) * dp
            for (k in 0 until counts[i]) {
                val line = lines[i][k] ?: continue
                require9(g.ui.measure(line, 12f) <= room + 0.5f, "$tag: opis doktryny '$line' wchodzi na lamówkę karty")
            }
        }
    }

    /** Dymek podpowiedzi: tekst i OK nie leżą na złotej lamówce. */
    private fun checkHint(g: Game, where: String) {
        val s = g.current ?: return
        val f = runCatching { s.javaClass.getDeclaredField("hints") }.getOrNull() ?: return
        val hints = f.apply { isAccessible = true }.get(s) as? HintOverlay ?: return
        if (hints.activeId < 0 || hints.panelRect.isEmpty || !hints.okBtn.visible) return
        val inner = g.ui.panelInner(hints.panelRect, android.graphics.RectF())
        val ok = hints.okBtn.r
        require9(ok.left >= inner.left && ok.right <= inner.right - 4 * g.ui.dp + 0.5f &&
            ok.top >= inner.top && ok.bottom <= inner.bottom + 0.5f,
            "$where: OK dymku na lamówce ($ok, wnętrze $inner)")
    }

    /** Wyniki: zakładki i powtórka czytelne, przyciski samouczka nie przechwytują dotyku. */
    private fun checkResults(g: Game, where: String, bs: BattleScreen) {
        val dp = g.ui.dp
        bs.tut?.let {
            require9(!it.skipBtn.visible && !it.nextBtn.visible, "$where: niewidoczne przyciski samouczka nadal łapią dotyk")
        }
        val r = bs.replayEnd
        if (r.visible) {
            val avail = r.r.width() - r.r.height() * 0.88f - 16 * dp
            val size = g.ui.fit(r.label, BattleResultsPanel.REPLAY_LABEL_DP, avail)
            require9(size >= BattleResultsPanel.REPLAY_LABEL_DP * 0.75f,
                "$where: '${r.label}' na przycisku powtórki ma tylko ${"%.1f".format(size)} sp")
        }
        for (tab in listOf(bs.resultStatsTab, bs.resultChartTab)) {
            val hit = s9first(bs, tab.r.centerX(), tab.r.top - 6 * dp)
            require9(hit === tab, "$where: górny skraj zakładki '${tab.label}' nie trafia w zakładkę")
        }
    }

    private fun s9first(s: Screen, x: Float, y: Float): Button? =
        s.buttons.firstOrNull { it.enabled && it.contains(x, y) }

    /** Prawdziwy dotyk w środek przycisku (DOWN/UP) i klatka. */
    private fun tap(g: Game, b: Button, where: String) {
        assertTrue("$where: przycisk #${b.id} '${b.label}' niewidoczny", b.visible && !b.r.isEmpty)
        val x = b.r.centerX(); val y = b.r.centerY()
        g.pushTouch(TouchEvent.DOWN, x, y, 0)
        g.pushTouch(TouchEvent.UP, x, y, 0)
        tick(g)
    }

    private fun tapAt(g: Game, x: Float, y: Float) {
        g.pushTouch(TouchEvent.DOWN, x, y, 0)
        g.pushTouch(TouchEvent.UP, x, y, 0)
        tick(g)
    }

    private fun button(s: Screen, id: Int): Button = s.buttons.first { it.id == id }

    private fun battleOf(s: BattleScreen) = s.battle

    private fun eraIntro(s: BattleScreen): EraIntroOverlay? =
        s.javaClass.getDeclaredField("eraIntro").apply { isAccessible = true }.get(s) as EraIntroOverlay?

    /** Samouczek prowadzony palcem: gracz robi dokładnie to, o co prosi dymek. */
    private fun playTutorial(g: Game, tag: String, bs: BattleScreen) {
        val tut = requireNotNull(bs.tut)
        val b = battleOf(bs)
        val shotSteps = mapOf(
            TutorialOverlay.S_MELEE to "03_tut_pierwsza_jednostka",
            TutorialOverlay.S_INCOME to "04_tut_dochod",
            TutorialOverlay.S_KILL to "05_tut_pierwsze_zabicie",
            TutorialOverlay.S_EVOLVE to "06_tut_awans",
            TutorialOverlay.S_TURRET to "07_tut_wieza",
            TutorialOverlay.S_BARRAGE to "08_tut_ostrzal",
            TutorialOverlay.S_DONE to "09_tut_gotowe",
        )
        val seen = HashSet<Int>()
        var guard = 0
        while (!tut.finished && !b.over && guard++ < 20000) {
            val intro = eraIntro(bs)
            if (intro?.active == true) {
                if (seen.add(-100 - b.player.era)) shot(g, tag, "02_epoka_${b.player.era}")
                tapAt(g, g.width / 2f, g.height / 2f)
                tick(g, 0.2f, 3)
                continue
            }
            if (b.pendingVariant != null) { tap(g, bs.variantBtn[0], "$tag/wariant"); continue }
            if (b.pendingChoice != null) {
                if (tut.showing && tut.step == TutorialOverlay.S_DOCTRINE && seen.add(TutorialOverlay.S_DOCTRINE))
                    shot(g, tag, "06b_tut_doktryna")
                    checkChoiceText(g, tag, bs)
                tap(g, bs.doctrineBtn[0], "$tag/doktryna")
                continue
            }
            if (tut.showing) {
                val step = tut.step
                if (seen.add(step)) {
                    tick(g, 1f / 30f, 20)
                    shotSteps[step]?.let { shot(g, tag, it) }
                    checkTutorialPanel(g, tag, bs, tut)
                }
                when (step) {
                    TutorialOverlay.S_MELEE -> tap(g, bs.unitBtn[0], "$tag/piechota")
                    TutorialOverlay.S_RANGED -> tap(g, bs.unitBtn[1], "$tag/strzelec")
                    TutorialOverlay.S_EVOLVE -> if (b.canEvolve(b.player)) tap(g, bs.evolveBtn, "$tag/awans") else tick(g, 0.25f, 4)
                    TutorialOverlay.S_TURRET -> if (!bs.showTurrets) tap(g, bs.turretBtn, "$tag/wieże") else {
                        if (seen.add(1000)) shot(g, tag, "07b_tut_wieza_panel")
                        tap(g, bs.slotBuy[0], "$tag/kup wieżę")
                    }
                    TutorialOverlay.S_ARMORY -> tap(g, bs.armoryBtn, "$tag/zbrojownia")
                    TutorialOverlay.S_STANCE -> tap(g, bs.stanceBtn, "$tag/postawa")
                    TutorialOverlay.S_RALLY -> tap(g, bs.rallyBtn, "$tag/zryw")
                    TutorialOverlay.S_BARRAGE -> {
                        tap(g, bs.barrageBtn, "$tag/ostrzał")
                        if (bs.aimingBarrage) {
                            if (seen.add(1001)) shot(g, tag, "08b_tut_ostrzal_celowanie")
                            tap(g, bs.barrageBtn, "$tag/ostrzał-2")
                        }
                    }
                    else -> if (tut.nextBtn.visible) { tick(g, 0.2f, 4); tap(g, tut.nextBtn, "$tag/dalej") } else tick(g)
                }
            } else {
                // Gracz między krokami: kupuje wojsko, gdy go stać, i raz zagląda do pauzy.
                if (seen.contains(TutorialOverlay.S_KILL) && seen.add(2000)) {
                    tap(g, bs.pauseBtn, "$tag/pauza w samouczku")
                    shot(g, tag, "05b_tut_pauza")
                    require9(!tut.skipBtn.visible, "$tag: „Pomiń samouczek” aktywny pod menu pauzy")
                    tap(g, bs.pauseMenu[0], "$tag/wznów")
                }
                val role = if (b.player.queueSize % 2 == 0) 0 else 1
                val unit = bs.unitBtn[role]
                if (unit.enabled && b.player.queueSize < 2 && tut.allows(unit)) tap(g, unit, "$tag/kup") else tick(g, 0.3f)
            }
        }
        assertTrue("$tag: samouczek nie skończył się (krok ${tut.step}, bitwa ${b.time}s)", tut.finished || b.over)
    }

    /** Dymek samouczka mieści się w bezpiecznym obszarze i nie zasłania swojego celu ani przycisków. */
    private fun checkTutorialPanel(g: Game, tag: String, bs: BattleScreen, tut: TutorialOverlay) {
        val p = tut.panel
        if (p.isEmpty) return
        val where = "$tag/krok ${tut.step}"
        if (p.left < g.insetL - 0.5f || p.top < g.insetT - 0.5f || p.right > g.width - g.insetR + 0.5f || p.bottom > g.height - g.insetB + 0.5f)
            audit.append("$where: DYMEK SAMOUCZKA POZA BEZPIECZNYM OBSZAREM $p\n")
        if (!tut.hole.isEmpty && android.graphics.RectF.intersects(p, tut.hole))
            audit.append("$where: DYMEK ZASŁANIA PODŚWIETLONY CEL\n")
        if (tut.skipBtn.visible) for (h in listOf(bs.stanceBtn, bs.speedBtn, bs.evolveBtn, bs.pauseBtn) + bs.unitBtn) {
            if (h.visible && android.graphics.RectF.intersects(tut.skipBtn.r, h.r))
                require9(false, "$where: „Pomiń samouczek” zasłania przycisk HUD #${h.id} '${h.label}'")
        }
        for (btn in listOf(tut.nextBtn, tut.skipBtn)) if (btn.visible) {
            if (btn.r.height() < 47.5f * g.ui.dp) audit.append("$where: MAŁY ${btn.label}\n")
        }
    }

    private fun waitResults(g: Game, bs: BattleScreen) {
        var n = 0
        while (!bs.finished && n++ < 200) tick(g, 0.1f)
        tick(g, 0.25f, 16) // animacje wyników
    }

    private fun backToMenu(g: Game, tag: String) {
        var n = 0
        while (g.current !is MenuScreen && n++ < 6) {
            val s = requireNotNull(g.current)
            val back = s.buttons.firstOrNull { it.id == -1 && it.visible }
            if (back != null) tap(g, back, "$tag/wstecz") else { g.requestBack(); tick(g) }
            tick(g, 0.1f, 3)
        }
        assertTrue("$tag: nie wróciłem do menu (${g.current?.javaClass?.simpleName})", g.current is MenuScreen)
    }

    private fun journey(tag: String, d: Device) {
        val g = game(d)
        assertEquals("$tag: start bez menu", MenuScreen::class.java, g.current?.javaClass)
        tick(g, 0.1f, 5)
        shot(g, tag, "01_menu_start")
        val menu = g.current as MenuScreen
        tap(g, button(menu, 1), "$tag/graj")
        val bs = g.current as BattleScreen
        assertTrue("$tag: pierwsza bitwa bez samouczka", bs.cfg.tutorial)
        tick(g, 1f / 30f, 4)
        playTutorial(g, tag, bs)
        val b = bs.battle
        // Wygrana: dobijamy bazę wroga.
        tick(g, 0.3f, 10)
        shot(g, tag, "10_bitwa_po_samouczku")
        b.enemy.baseHp = 0f
        waitResults(g, bs)
        shot(g, tag, "11_wyniki_wygrana")
        checkResults(g, "$tag/wygrana", bs)
        tap(g, bs.continueBtn, "$tag/dalej po wygranej")
        tick(g, 0.2f, 10)
        shot(g, tag, "12_kampania_po_pierwszej")
        backToMenu(g, tag)
        tick(g, 0.2f, 8)
        shot(g, tag, "13_menu_po_pierwszej")

        tap(g, button(g.current!!, 5), "$tag/badania")
        tick(g, 0.2f, 8)
        shot(g, tag, "14_badania")
        run {
            val rs = g.current as ResearchScreen
            val focus = rs.javaClass.getDeclaredField("hintMedalsRect").apply { isAccessible = true }.get(rs) as android.graphics.RectF
            require9(focus.width() / g.ui.dp < 130f, "$tag: podświetlenie medali szersze niż plakietka (${focus.width() / g.ui.dp} dp)")
            val medals = g.profile.medals
            g.profile.medals = 3
            tick(g, 0.1f, 2)
            val card = rs.buttons.first { it.tag != null && it.visible }
            require9(!card.enabled, "$tag: badanie powinno być za drogie")
            tapAt(g, card.r.centerX(), card.r.centerY())
            require9(rs.lastNotice.contains("3") && rs.lastNotice.isNotEmpty(),
                "$tag: dotknięcie za drogiego badania nic nie mówi ('${rs.lastNotice}')")
            g.profile.medals = medals
        }
        backToMenu(g, tag)

        tap(g, button(g.current!!, 1), "$tag/kampania")
        tick(g, 0.2f, 8)
        val cs = g.current as CampaignScreen
        shot(g, tag, "15_kampania_mapa")
        run {
            val locked = cs.buttons.firstOrNull { it.tag != null && it.visible && !it.enabled }
            if (locked != null) {
                tapAt(g, locked.r.centerX(), locked.r.centerY())
                require9(cs.lastNotice == L.f("cp143_locked", g.profile.unlockedStage() + 1),
                    "$tag: zablokowany etap nie mówi, jak go odblokować ('${cs.lastNotice}')")
                tick(g, 0.2f, 2)
            }
        }
        val tile = cs.buttons.first { it.tag === Campaign.stages[1] }
        if (!tile.visible) error("$tag: etap 2 niewidoczny na mapie")
        tap(g, tile, "$tag/etap 2")
        tick(g, 0.2f, 6)
        shot(g, tag, "16_kampania_etap2")
        tap(g, button(cs, -2), "$tag/start etapu 2")
        val bs2 = g.current as BattleScreen
        tick(g, 0.2f, 10)
        if (eraIntro(bs2)?.active == true) { tapAt(g, g.width / 2f, g.height / 2f); tick(g, 0.2f, 4) }
        tick(g, 0.3f, 40)
        shot(g, tag, "17_etap2_bitwa")
        bs2.battle.player.baseHp = 0f
        waitResults(g, bs2)
        shot(g, tag, "18_wyniki_przegrana")
        checkResults(g, "$tag/przegrana", bs2)
        tap(g, bs2.continueBtn, "$tag/dalej po przegranej")
        tick(g, 0.2f, 8)
        shot(g, tag, "19_po_przegranej")
        backToMenu(g, tag)

        tap(g, button(g.current!!, 2), "$tag/endless")
        tick(g, 0.2f, 8)
        shot(g, tag, "20_endless")
        tap(g, button(g.current!!, -2), "$tag/endless start")
        tick(g, 0.2f, 6)
        val bs3 = g.current as BattleScreen
        if (eraIntro(bs3)?.active == true) { tapAt(g, g.width / 2f, g.height / 2f); tick(g, 0.2f, 4) }
        tick(g, 0.3f, 20)
        shot(g, tag, "21_endless_bitwa")
        tap(g, bs3.pauseBtn, "$tag/pauza")
        shot(g, tag, "22_endless_pauza")
        g.setScreen(MenuScreen(g))
        tick(g, 0.2f, 4)
        tap(g, button(g.current!!, 7), "$tag/ustawienia")
        tick(g, 0.2f, 6)
        shot(g, tag, "23_ustawienia")
    }

    @Test
    fun freshProfileJourney() {
        val old = L.lang
        try {
            for ((tag, d) in devices) journey(tag, d)
        } finally {
            L.lang = old
            File(out, "journey_audit.txt").writeText(audit.toString())
            println(audit)
        }
        assertTrue("Regresje UX nowego gracza:\n$regressions", regressions.isEmpty())
    }
}
