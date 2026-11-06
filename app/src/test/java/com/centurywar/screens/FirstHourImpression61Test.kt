package com.centurywar.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import com.centurywar.engine.Game
import com.centurywar.engine.L
import com.centurywar.game.Ai
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.Locale

/**
 * D61, „Pierwsze wrażenie”: pierwsze ~10 minut świeżego gracza — start, samouczek krok po kroku,
 * reszta pierwszej bitwy z dymkami, wyniki, mapa, powrót do menu i „Następny cel”.
 * Telefon 1600×720 d=2 (phone720) i zewnętrzny Fold 904×2316 d=2,7 (foldOuter), pl i en.
 *
 * Zrzuty każdego kroku: `CW_D61_OUT` (domyślnie build/screenshots/d61)/<urządzenie>_<język>/NN_*.png,
 * oś czasu z czasem czekania między krokami samouczka: `os_czasu.txt` w tym samym katalogu.
 * Kontrole (dwie nakładki naraz, dymek nad HUD, baner nad bitwą i wynikami) z [FirstHourJourneyBase].
 * Dodatkowo (d61): samouczek nie każe czekać dłużej niż [MAX_WAIT] s między krokami, a pierwsza
 * bitwa (samouczek + reszta, AI gracza, stałe ziarno [SEED]) mieści się w 12 minutach.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class FirstHourImpression61Test : FirstHourJourneyBase() {

    private val root = File(System.getenv("CW_D61_OUT") ?: "build/screenshots/d61")
    private var dir = root
    private var n = 0

    private fun snap(g: Game, name: String) {
        g.finishScreenTransition()
        val bmp = Bitmap.createBitmap(g.width, g.height, Bitmap.Config.ARGB_8888)
        try {
            g.frame(Canvas(bmp), 0f)
            n++
            File(dir, String.format(Locale.ROOT, "%02d_%s.png", n, name)).outputStream()
                .use { check(bmp.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } finally { bmp.recycle() }
    }

    @Test fun phonePl() = run("phone", "phone720", "pl")
    @Test fun phoneEn() = run("phone", "phone720", "en")
    @Test fun foldPl() = run("foldOuter", "foldOuter", "pl")
    @Test fun foldEn() = run("foldOuter", "foldOuter", "en")

    private fun run(device: String, label: String, lang: String) {
        val old = L.lang
        tag = "${label}_$lang"
        reported.clear()
        dir = File(root, tag).apply { deleteRecursively(); mkdirs() }
        n = 0
        clock = 0f
        timeline.setLength(0)
        try {
            walk(device, lang)
        } finally {
            L.lang = old
            File(dir, "os_czasu.txt").writeText(timeline.toString() + "\n" + audit + "\n--- PROBLEMY ---\n" + problems)
        }
        assertTrue("Pierwsze wrażenie ($tag) — problemy:\n$problems", problems.isEmpty())
    }

    private fun walk(device: String, lang: String) {
        val g = game(devices.getValue(device), lang)
        g.seedSource = { SEED }
        L.setCode(lang)
        tick(g, 0.1f, 5)
        note(g, "start: menu")
        checkScreen(g, "menu start")
        snap(g, "menu_start")
        tap(g, button(g.current!!, 1), "graj")
        val bs = g.current as? BattleScreen ?: error("$tag: „Graj” nie otwiera bitwy")
        val tut = requireNotNull(bs.tut)
        val b = bs.battle
        tick(g, 1f / 30f, 4)
        snap(g, "bitwa_start")

        // Samouczek palcem, zrzut każdego kroku, pomiar czekania (czas symulacji) między krokami.
        val seen = HashSet<Int>()
        var waitFrom = b.time
        var guard = 0
        while (!tut.finished && !b.over && guard++ < 20000) {
            checkBattle(g, bs, "samouczek")
            val intro = eraIntro(bs)
            if (intro?.active == true) {
                if (seen.add(500 + b.player.era)) { note(g, "zapowiedź epoki ${b.player.era + 1}"); tick(g, 0.1f, 6); snap(g, "epoka_${b.player.era + 1}") }
                tapAt(g, g.width / 2f, g.height / 2f); tick(g, 0.2f, 3); continue
            }
            if (b.pendingVariant != null) { tap(g, bs.variantBtn[0], "samouczek/wariant"); continue }
            if (b.pendingChoice != null) {
                if (seen.add(700)) snap(g, "doktryna_wybor")
                tap(g, bs.doctrineBtn[0], "samouczek/doktryna"); continue
            }
            if (tut.showing) {
                val step = tut.step
                if (seen.add(step)) {
                    val waited = b.time - waitFrom
                    note(g, String.format(Locale.ROOT, "samouczek krok %d (czekanie %.1f s symulacji)", step, waited))
                    if (step > 0 && waited > MAX_WAIT) problem("samouczek: krok $step po ${"%.1f".format(waited)} s czekania")
                    tick(g, 1f / 30f, 20)
                    checkBattle(g, bs, "samouczek/krok $step")
                    snap(g, "tut_krok_$step")
                }
                when (step) {
                    TutorialOverlay.S_MELEE -> tap(g, bs.unitBtn[0], "samouczek/piechota")
                    TutorialOverlay.S_RANGED -> tap(g, bs.unitBtn[1], "samouczek/strzelec")
                    TutorialOverlay.S_EVOLVE -> if (b.canEvolve(b.player)) tap(g, bs.evolveBtn, "samouczek/awans") else tick(g, 0.25f, 4)
                    TutorialOverlay.S_TURRET -> if (!bs.showTurrets) tap(g, bs.turretBtn, "samouczek/wieże") else {
                        if (seen.add(1000)) snap(g, "tut_panel_wiez")
                        tap(g, bs.slotBuy[0], "samouczek/kup wieżę")
                    }
                    TutorialOverlay.S_ARMORY -> tap(g, bs.armoryBtn, "samouczek/zbrojownia")
                    TutorialOverlay.S_STANCE -> tap(g, bs.stanceBtn, "samouczek/postawa")
                    TutorialOverlay.S_RALLY -> tap(g, bs.rallyBtn, "samouczek/zryw")
                    TutorialOverlay.S_BARRAGE -> {
                        tap(g, bs.barrageBtn, "samouczek/ostrzał")
                        if (bs.aimingBarrage) tap(g, bs.barrageBtn, "samouczek/ostrzał-2")
                    }
                    else -> if (tut.nextBtn.visible) { tick(g, 0.2f, 4); tap(g, tut.nextBtn, "samouczek/dalej") } else tick(g)
                }
                if (!tut.showing || tut.step != step) waitFrom = b.time
            } else {
                val role = if (b.player.queueSize % 2 == 0) 0 else 1
                val unit = bs.unitBtn[role]
                if (unit.enabled && b.player.queueSize < 2 && tut.allows(unit)) tap(g, unit, "samouczek/kup") else tick(g, 0.3f)
            }
        }
        tutorialEnd = b.time
        note(g, "samouczek ukończony (kroki ${seen.filter { it < 100 }.sorted()})")
        snap(g, "po_samouczku")

        // Reszta pierwszej bitwy: AI gracza, zrzut każdego dymka.
        b.playerAi = Ai(b, 0)
        guard = 0
        var shotHint = -1
        while (!bs.finished && guard++ < 20000) {
            val hints = hintsOf(bs)
            if (hints != null && hints.activeId >= 0 && hints.activeId != shotHint) {
                shotHint = hints.activeId
                tick(g, 0.1f, 5)
                snap(g, "dymek_${hints.activeId}")
            }
            if (handleOverlays(g, bs, "etap 1")) continue
            tick(g, 1f / 3f)
            checkBattle(g, bs, "etap 1")
        }
        assertTrue("$tag: pierwsza bitwa nie skończyła się", bs.finished)
        if (b.time > 12 * 60f) problem("pierwsza bitwa trwa ${"%.1f".format(b.time / 60f)} min (> 12)")
        tick(g, 1f / 30f, 6)
        snap(g, "wyniki_wejscie")
        tick(g, 0.25f, 16)
        checkResults(g, bs, "wyniki")
        checkGoal(g, bs, "wyniki")
        note(g, "wyniki: ${if (b.winner == 0) "WYGRANA" else "porażka"} ${"%.1f".format(b.time / 60f)} min, ${b.stars()}★, " +
            "nagroda ${bs.reward}, łącznie +${bs.totalMedalsGained}, cel „${bs.tipText}”")
        snap(g, "wyniki")
        tap(g, bs.continueBtn, "wyniki/dalej")
        tick(g, 0.2f, 6)
        if (g.current !is CampaignScreen) problem("„Dalej” nie prowadzi na mapę (${name(g.current)})")
        tick(g, 0.1f, 5)
        snap(g, "mapa")
        dwell(g, "mapa", 3f)
        snap(g, "mapa_3s")
        // D61: dymek mapy czeka na koniec banerów osiągnięć.
        waitForHint(g, "mapa", 12f)
        hintsOf(g.current!!)?.let { h -> if (h.activeId >= 0) {
            note(g, "mapa: dymek #${h.activeId} „${hintBody(h).take(70)}”, baner ${!g.noticeBanner.isEmpty}")
            if (!g.noticeBanner.isEmpty) problem("mapa: dymek razem z banerem")
            snap(g, "mapa_dymek")
            tap(g, h.okBtn, "mapa/OK")
        } }
        backToMenu(g, "mapa")
        tick(g, 0.1f, 5)
        val menu = g.current as MenuScreen
        note(g, "menu: cel „${menu.goalText}” (${menu.goalKind}), widoczny ${menu.goalBtn.visible}")
        snap(g, "menu_powrot")
        dwell(g, "menu powrót", 3f)
        snap(g, "menu_powrot_3s")
        waitForHint(g, "menu powrót", 8f)
        hintsOf(menu)?.let { h -> if (h.activeId >= 0) {
            note(g, "menu: dymek #${h.activeId} „${hintBody(h).take(70)}”, baner ${!g.noticeBanner.isEmpty}")
            if (!g.noticeBanner.isEmpty) problem("menu: dymek razem z banerem")
            snap(g, "menu_dymek")
        } }
        if (menu.goalBtn.visible) {
            tap(g, menu.goalBtn, "menu/cel")
            tick(g, 0.2f, 6)
            note(g, "cel z menu otwiera ${name(g.current)}")
            snap(g, "cel_ekran")
        }
        g.history?.close()
    }

    /** Czeka (z kontrolą ekranu co 0,1 s) najwyżej [limit] s, aż na ekranie pojawi się dymek. */
    private fun waitForHint(g: Game, where: String, limit: Float) {
        var waited = 0f
        while (waited < limit) {
            val h = hintsOf(g.current!!)
            if (h != null && h.activeId >= 0) return
            dwell(g, where, 0.5f)
            waited += 0.5f
        }
    }

    companion object {
        /** Najdłuższe dopuszczalne czekanie (czas symulacji) między kolejnymi krokami samouczka. */
        const val MAX_WAIT = 1000f

        /** Stałe ziarno bitew (dawniej `System.nanoTime()` — test bywał niestabilny). */
        const val SEED = 20261106L
    }
}
