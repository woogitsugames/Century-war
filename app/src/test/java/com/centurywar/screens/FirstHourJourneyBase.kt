package com.centurywar.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import com.centurywar.engine.Button
import com.centurywar.engine.Game
import com.centurywar.engine.Host
import com.centurywar.engine.L
import com.centurywar.engine.Quality
import com.centurywar.engine.Screen
import com.centurywar.engine.TouchEvent
import com.centurywar.game.Ai
import com.centurywar.game.Campaign
import com.centurywar.game.Mode
import com.centurywar.game.Research
import com.centurywar.game.SnapshotStore
import com.centurywar.meta.BattleHistory
import com.centurywar.meta.Profile
import com.centurywar.meta.ReplayStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.Locale

/**
 * Noc 5, „Pierwsza godzina gracza”: zupełnie nowy profil po zmianach z 2–3.11 (dowódca, stopnie
 * wież, nagrana muzyka, idempotentne rozliczenie, TalkBack w menu, nowe wydarzenia dnia i karty
 * Endless, panel wież 161).
 *
 * Ścieżka: menu → samouczek (palcem, jak [NewPlayerJourneyTest]) → etapy kampanii 2–8 (AI gracza
 * bez badań na starcie, czyli profil „nowy” z BalanceReport, z prawdziwymi zakupami badań
 * w menu) → pierwsze badanie → potyczka → Endless → wyzwanie dnia → „Co nowego” (Ustawienia →
 * O grze). Telefon 1600×720 d=2 i Fold zewnętrzny 904×2316 d=2,7; pl i en.
 *
 * Każda klatka bitwy sprawdza, czy naraz nie stoją dwie nakładki (samouczek, dymek, zapowiedź
 * epoki, baner awansu), czy dymek nie zasłania przycisków HUD i czy baner powiadomień nie wchodzi
 * na bitwę. Każdy ekran poza bitwą: Wstecz (przycisk i systemowy), baner vs tytuł/Wstecz/dymek,
 * opisy TalkBack. Oś czasu: build/screenshots/fh_<urządzenie>_<język>_os_czasu.txt.
 * Testy: [FirstHourJourneyTest], [FirstHourJourneyOddTest], [FirstHourJourneyFoldTest],
 * [FirstHourJourneyFoldOddTest] (noc 9: jedna ścieżka urządzenie/język na klasę).
 */
abstract class FirstHourJourneyBase {
    @get:Rule val folder = TemporaryFolder()

    protected val out = File("build/screenshots").apply { mkdirs() }

    protected class Device(val w: Int, val h: Int, val density: Float, val insetT: Int)

    protected val devices = linkedMapOf(
        "phone" to Device(1600, 720, 2f, 0),
        "foldOuter" to Device(904, 2316, 2.7f, 88),
    )

    protected val host = object : Host {
        override fun vibrate(ms: Long) {}
        override fun requestText(title: String, current: String, onResult: (String) -> Unit) {}
        override fun applyRenderScale(scale: Float) {}
        override fun applyFrameRate(fps: Int) {}
        override fun exit() {}
    }

    protected val tiny = Canvas(Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888))

    /** Twarde problemy (asercja na końcu) i dziennik obserwacji. */
    protected val problems = StringBuilder()
    protected val audit = StringBuilder()
    protected val timeline = StringBuilder()
    protected var tag = ""
    /** Czas gracza: suma czasu klatek (w tym bitwy ×1). */
    protected var clock = 0f
    protected val reported = HashSet<String>()

    protected fun problem(msg: String) {
        if (reported.add(msg)) problems.append("$tag: ").append(msg).append('\n')
    }

    protected fun note(g: Game, msg: String) {
        val m = (clock / 60f).toInt(); val s = (clock % 60f).toInt()
        timeline.append(String.format(Locale.ROOT, "[%02d:%02d] ", m, s)).append(msg)
            .append("  (medale ").append(g.profile.medals).append(", gwiazdki ").append(g.profile.totalStars())
            .append(")\n")
    }

    // ------------------------------------------------------------------ narzędzia

    protected fun game(d: Device, lang: String, dir: File? = null): Game {
        val f = if (dir != null) File(dir, "profile.json") else File.createTempFile("fhp", ".json").apply { delete() }
        val profile = Profile(f).apply { if (dir != null) load(); this.lang = lang }
        return Game(host, profile, Quality.LOW).apply {
            density = d.density
            resize(d.w, d.h, d.w, d.h)
            setInsets(0, d.insetT, 0, 0)
            history = BattleHistory(File.createTempFile("fhh", ".json").apply { delete() })
            historyLoaded = true
            replays = ReplayStore(File.createTempFile("fhr", "").apply { delete(); mkdirs() })
            if (dir != null) snapshots = SnapshotStore(File(dir, "battle.snap"))
        }
    }

    protected fun tick(g: Game, dt: Float = 1f / 30f, n: Int = 1) = repeat(n) {
        val bs = g.current as? BattleScreen
        val t0 = bs?.battle?.time ?: 0f
        g.frame(tiny, dt)
        // W bitwie liczy się czas symulacji (gracz gra na ×1), poza nią czas klatek.
        clock += if (bs != null && !bs.finished && bs.battle.time > t0) bs.battle.time - t0 else dt
        recordAchievements(g)
    }

    // ------------------------------------------------------------------ R6-01: osiągnięcia

    /** Czas gracza (s) pierwszego zauważenia każdego odblokowanego osiągnięcia. */
    protected val achievementTimes = LinkedHashMap<String, Float>()

    protected fun recordAchievements(g: Game) {
        val unlocked = g.profile.unlockedAchievements
        if (unlocked.size == achievementTimes.size) return
        for (id in com.centurywar.meta.Achievements.all) if (id.id in unlocked && id.id !in achievementTimes) {
            achievementTimes[id.id] = clock
            note(g, "osiągnięcie: ${id.id} „${L["ach_" + id.id]}” (+${id.medalReward})")
        }
    }

    /**
     * R6-01 (Play Games LU-AC-GAC): co najmniej 4 osiągnięcia z puli pierwszej godziny przed 60:00.
     * Zwraca tabelę „osiągnięcie;minuta” do osi czasu.
     */
    protected fun checkFirstHourAchievements(where: String): String {
        val within = FIRST_HOUR_POOL.filter { (achievementTimes[it] ?: Float.MAX_VALUE) < 60f * 60f }
        if (within.size < 4) problem("$where: przed 60:00 tylko ${within.size} osiągnięcia z puli " +
            "pierwszej godziny ($within; wszystkie: $achievementTimes)")
        val sb = StringBuilder("\nosiągnięcie;czas odblokowania (mm:ss)\n")
        for ((id, t) in achievementTimes) sb.append(id).append(';')
            .append(String.format(Locale.ROOT, "%02d:%02d", (t / 60f).toInt(), (t % 60f).toInt()))
            .append(if (id in FIRST_HOUR_POOL) ";pula 1. godziny" else "").append('\n')
        sb.append("przed 60:00 z puli: ${within.size} ($within)\n")
        return sb.toString()
    }

    protected val FIRST_HOUR_POOL = listOf("win_1", "kills_100", "first_evolve", "first_turret", "first_research", "stages_3")

    protected fun tap(g: Game, b: Button, where: String) {
        if (!b.visible || b.r.isEmpty) { problem("$where: przycisk #${b.id} '${b.label}' niewidoczny"); tick(g); return }
        val x = b.r.centerX(); val y = b.r.centerY()
        g.pushTouch(TouchEvent.DOWN, x, y, 0)
        g.pushTouch(TouchEvent.UP, x, y, 0)
        tick(g)
    }

    protected fun tapAt(g: Game, x: Float, y: Float) {
        g.pushTouch(TouchEvent.DOWN, x, y, 0)
        g.pushTouch(TouchEvent.UP, x, y, 0)
        tick(g)
    }

    protected fun shot(g: Game, name: String) {
        g.finishScreenTransition()
        val bmp = Bitmap.createBitmap(g.width, g.height, Bitmap.Config.ARGB_8888)
        try {
            g.frame(Canvas(bmp), 0f)
            File(out, "fh_${tag}_$name.png").outputStream().use { check(bmp.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } finally { bmp.recycle() }
    }

    protected fun field(o: Any, name: String) = o.javaClass.getDeclaredField(name).apply { isAccessible = true }

    protected fun hintsOf(s: Screen): HintOverlay? =
        runCatching { field(s, "hints").get(s) as? HintOverlay }.getOrNull()

    protected fun eraIntro(bs: BattleScreen): EraIntroOverlay? = field(bs, "eraIntro").get(bs) as EraIntroOverlay?

    protected fun button(s: Screen, id: Int): Button = s.buttons.first { it.id == id }

    protected fun name(s: Screen?) = s?.javaClass?.simpleName ?: "null"

    protected fun titleOf(s: Screen): String? = when (s) {
        is ResearchScreen -> L["research"]
        is CampaignScreen -> L["campaign"]
        is SettingsScreen -> L["settings"]
        else -> null
    }

    // ------------------------------------------------------------------ kontrole ekranów

    /** Ekran poza bitwą: baner, dymek, Wstecz i opisy TalkBack. */
    protected fun checkScreen(g: Game, where: String) {
        val s = g.current ?: return
        val banner = RectF(g.noticeBanner)
        val hints = hintsOf(s)
        val hintPanel = if (hints != null && hints.activeId >= 0) RectF(hints.panelRect) else RectF()
        if (!banner.isEmpty) {
            val back = s.buttons.firstOrNull { it.id == -1 && it.visible }
            if (back != null && RectF.intersects(banner, back.r)) problem("$where: baner zasłania Wstecz")
            titleOf(s)?.let { t ->
                val r = RectF().also { Chrome(s).titleBounds(t, it) }
                if (RectF.intersects(banner, r)) problem("$where: baner zasłania tytuł '$t'")
            }
            if (!hintPanel.isEmpty && RectF.intersects(banner, hintPanel))
                problem("$where: baner powiadomienia nachodzi na dymek podpowiedzi #${hints?.activeId}")
            if (s is MenuScreen) for (b in s.buttons) if (b.visible && b.enabled && b.id in 1..9 && RectF.intersects(banner, b.r))
                problem("$where: baner zasłania kafel menu #${b.id} '${b.label}'")
        }
        if (!hintPanel.isEmpty) {
            for (b in s.buttons) if (b.visible && b.enabled && b.id != HintOverlay.OK_ID && b.id == -1 &&
                RectF.intersects(hintPanel, b.r)) problem("$where: dymek #${hints?.activeId} zasłania Wstecz")
            if (reported.add("audit $where ${hints?.activeId}")) audit.append("$tag/$where: dymek #${hints?.activeId} $hintPanel\n")
        }
        // TalkBack: każdy widoczny, aktywny przycisk ma opis.
        for (b in s.buttons) if (b.visible && b.enabled && !b.r.isEmpty && b.tag == null &&
            s.accessibilityLabel(b).isBlank()) problem("$where: przycisk #${b.id} bez opisu TalkBack")
    }

    /** Bitwa: nakładki pojedynczo, dymki bez zasłaniania HUD, baner nie wchodzi na pole bitwy. */
    protected fun checkBattle(g: Game, bs: BattleScreen, where: String) {
        val tut = bs.tut
        val hints = hintsOf(bs)
        val intro = eraIntro(bs)?.active == true
        // Zapowiedź epoki przykrywa samouczek w całości (noc 5): wtedy jego przyciski znikają.
        val tutShown = tut != null && tut.showing && !tut.finished && !intro
        if (intro && tut != null && (tut.skipBtn.visible || tut.nextBtn.visible))
            problem("$where: przyciski samouczka nad zapowiedzią epoki")
        val hintShown = hints != null && hints.activeId >= 0
        val bannerShown = bs.evolveBanner.goBtn.visible
        val count = listOf(tutShown, hintShown, intro, bannerShown).count { it }
        if (count > 1) problem("$where: naraz ${count} nakładki (samouczek=$tutShown dymek=$hintShown " +
            "epoka=$intro baner awansu=$bannerShown, dymek #${hints?.activeId}, krok ${tut?.step})")
        if (!g.noticeBanner.isEmpty) problem("$where: baner powiadomień nad bitwą")
        if (hintShown) {
            val p = hints!!.panelRect
            for (b in bs.buttons) if (b.visible && b.id != HintOverlay.OK_ID && b.id >= 0 && !b.r.isEmpty &&
                RectF.intersects(p, b.r)) {
                // Nieaktywne pola informacyjne (np. licznik kolejki #90) tylko do dziennika.
                if (b.enabled) problem("$where: dymek #${hints.activeId} zasłania przycisk #${b.id} '${b.label}'")
                else if (reported.add("info $where ${hints.activeId} ${b.id}"))
                    audit.append("$tag/$where: dymek #${hints.activeId} nad nieaktywnym polem #${b.id}\n")
            }
        }
        if (tutShown && !tut!!.panel.isEmpty) {
            val p = tut.panel
            if (bs.commanderBtn.visible && RectF.intersects(p, bs.commanderBtn.r))
                problem("$where: dymek samouczka zasłania Wodza")
            if (bs.showTurrets) for (i in 0 until bs.battle.player.slots) {
                for (b in listOf(bs.slotBuy[i], bs.slotTier[i])) if (b.visible && RectF.intersects(p, b.r)) {
                    // Cel kroku (slot 1) musi być odsłonięty; dalsze sloty dymek może przykryć,
                    // bo stoi tuż pod celem (świadomy układ TutorialOverlay, raport nocy 5).
                    if (i == 0) problem("$where: dymek samouczka (krok ${tut.step}) zasłania przycisk wieży #${b.id} '${b.label}'")
                    else if (reported.add("slot $where $i")) audit.append("$tag/$where: dymek samouczka nad slotem ${i + 1} (nie celem)\n")
                }
            }
            if (!tut.hole.isEmpty && RectF.intersects(p, tut.hole))
                problem("$where: dymek samouczka zasłania podświetlony cel (krok ${tut.step})")
            if (bs.evolveBanner.goBtn.visible) problem("$where: baner awansu nad samouczkiem")
        }
    }

    /** Dymek w bitwie: zapisz, obejrzyj ~1,5 s i zamknij OK (gracz czyta i potwierdza). */
    protected val battleHintsSeen = ArrayList<Int>()

    protected fun handleOverlays(g: Game, bs: BattleScreen, where: String): Boolean {
        val intro = eraIntro(bs)
        if (intro?.active == true) {
            note(g, "$where: zapowiedź epoki ${bs.battle.player.era + 1} (dotknięcie zamyka)")
            tick(g, 0.1f, 8)
            tapAt(g, g.width / 2f, g.height / 2f)
            tick(g, 0.1f, 3)
            if (intro.active) problem("$where: zapowiedź epoki nie znika po dotknięciu")
            return true
        }
        val hints = hintsOf(bs) ?: return false
        if (hints.activeId >= 0) {
            val id = hints.activeId
            if (battleHintsSeen.isEmpty() && bs.tut != null && bs.battle.time < tutorialEnd + HintOverlayDelay - 0.1f)
                problem("$where: dymek #$id już ${"%.1f".format(bs.battle.time - tutorialEnd)} s po samouczku")
            battleHintsSeen.add(id)
            note(g, "$where: dymek #$id „${hintBody(hints).take(70)}”")
            tick(g, 0.1f, 15)
            checkBattle(g, bs, where)
            if (hints.okBtn.visible) tap(g, hints.okBtn, "$where/OK dymka")
            if (hints.activeId == id) problem("$where: OK nie zamyka dymka #$id")
            return true
        }
        return false
    }

    protected var tutorialEnd = 0f
    protected val HintOverlayDelay = BattleScreen.AFTER_TUTORIAL_HINT_DELAY

    protected fun hintBody(h: HintOverlay): String = field(h, "body").get(h) as String

    protected class Outcome(val won: Boolean, val reward: Int, val medals: Int, val stars: Int, val minutes: Float)

    /** AI gracza (jak BalanceReport) gra bitwę przez BattleController, aż do wyników. */
    protected fun playWithAi(g: Game, bs: BattleScreen, where: String, limitSec: Float = 18 * 60f): Outcome {
        val b = bs.battle
        b.playerAi = Ai(b, 0)
        if (bs.paused) bs.paused = false
        var guard = 0
        while (!bs.finished && guard++ < 20000) {
            if (handleOverlays(g, bs, where)) continue
            tick(g, 1f / 3f)
            checkBattle(g, bs, where)
            if (!b.over && b.time > limitSec) surrender(g, bs, where)
        }
        assertTrue("$tag/$where: bitwa nie skończyła się", bs.finished)
        tick(g, 0.25f, 16)
        checkResults(g, bs, where)
        return Outcome(b.winner == 0, bs.reward, bs.totalMedalsGained, b.stars(), b.time / 60f)
    }

    /** Kapitulacja z menu pauzy (Wstecz → „Poddaj się”), tak jak zrobiłby to gracz. */
    protected fun surrender(g: Game, bs: BattleScreen, where: String) {
        note(g, "$where: gracz kończy długą bitwę kapitulacją z menu pauzy")
        g.requestBack(); tick(g)
        if (!bs.paused) problem("$where: Wstecz w bitwie nie otwiera pauzy")
        tap(g, bs.pauseMenu[2], "$where/poddaj")
        tick(g, 0.1f, 3)
        if (!bs.battle.over) problem("$where: „Poddaj się” nie kończy bitwy")
    }

    protected fun checkResults(g: Game, bs: BattleScreen, where: String) {
        if (!bs.continueBtn.visible || !bs.continueBtn.enabled) problem("$where: wyniki bez aktywnego „Dalej”")
        bs.tut?.let { if (it.skipBtn.visible || it.nextBtn.visible) problem("$where: przyciski samouczka nad wynikami") }
        if (!g.noticeBanner.isEmpty) problem("$where: baner nad wynikami")
        for (b in bs.buttons) if (b.visible && b.enabled && !b.r.isEmpty && bs.accessibilityLabel(b).isBlank())
            problem("$where: wyniki: przycisk #${b.id} bez opisu TalkBack")
    }

    /** Systemowe Wstecz aż do menu; każdy krok musi zmienić ekran (brak martwych końców). */
    protected fun backToMenu(g: Game, where: String) {
        var n = 0
        while (g.current !is MenuScreen && n++ < 8) {
            val before = g.current
            val stack = g.stackDepth()
            checkScreen(g, "$where/${name(before)}")
            if (before !is BattleScreen && before?.buttons?.any { it.id == -1 && it.visible && it.enabled } != true &&
                before !is WhatsNewScreen) problem("$where: ${name(before)} bez widocznego przycisku Wstecz")
            g.requestBack()
            tick(g, 0.1f, 4)
            if (g.current === before && g.stackDepth() == stack) problem("$where: Wstecz na ${name(before)} nic nie robi")
        }
        if (g.current !is MenuScreen) problem("$where: nie wróciłem do menu (${name(g.current)})")
        tick(g, 0.1f, 4)
    }

    protected fun Game.stackDepth(): Int = (field(this, "stack").get(this) as Collection<*>).size

    /** Ekran po kilku sekundach: kontrola co klatkę, gdy kolejka banerów się przewija. */
    protected fun dwell(g: Game, where: String, seconds: Float) {
        var t = 0f
        while (t < seconds) { tick(g, 0.1f); t += 0.1f; checkScreen(g, where) }
    }

    // ------------------------------------------------------------------ samouczek

    /** [stop]: przerwij przed końcem samouczka (np. w kroku, w którym proces zostanie zabity). */
    protected fun playTutorial(g: Game, bs: BattleScreen, stop: () -> Boolean = { false }, buyBetween: () -> Boolean = { true }) {
        val tut = requireNotNull(bs.tut) { "pierwsza bitwa bez samouczka" }
        val b = bs.battle
        val seen = HashSet<Int>()
        var guard = 0
        var lastShown = -1
        var multi = 0
        while (!tut.finished && !b.over && !stop() && guard++ < 20000) {
            checkBattle(g, bs, "samouczek")
            val intro = eraIntro(bs)
            if (intro?.active == true) {
                note(g, "samouczek: zapowiedź epoki ${b.player.era + 1}")
                tapAt(g, g.width / 2f, g.height / 2f); tick(g, 0.2f, 3); continue
            }
            if (b.pendingVariant != null) { tap(g, bs.variantBtn[0], "samouczek/wariant"); continue }
            if (b.pendingChoice != null) { tap(g, bs.doctrineBtn[0], "samouczek/doktryna"); continue }
            if (tut.showing) {
                val step = tut.step
                if (seen.add(step)) {
                    if (lastShown == TutorialOverlay.S_TURRET && bs.showTurrets)
                        problem("samouczek: panel wież otwarty po kroku wieży (zasłania jednostki)")
                    if (lastShown >= 0 && step < lastShown) problem("samouczek: krok $step po kroku $lastShown")
                    lastShown = step
                    note(g, "samouczek: krok $step")
                    tick(g, 1f / 30f, 20)
                    checkBattle(g, bs, "samouczek/krok $step")
                    if (step == TutorialOverlay.S_TURRET || step == TutorialOverlay.S_DONE) shot(g, "tut_krok_$step")
                }
                when (step) {
                    TutorialOverlay.S_MELEE -> tap(g, bs.unitBtn[0], "samouczek/piechota")
                    TutorialOverlay.S_RANGED -> tap(g, bs.unitBtn[1], "samouczek/strzelec")
                    TutorialOverlay.S_EVOLVE -> if (b.canEvolve(b.player)) tap(g, bs.evolveBtn, "samouczek/awans") else tick(g, 0.25f, 4)
                    TutorialOverlay.S_TURRET -> if (!bs.showTurrets) tap(g, bs.turretBtn, "samouczek/wieże") else {
                        checkBattle(g, bs, "samouczek/panel wież")
                        if (seen.add(1000)) shot(g, "tut_panel_wiez")
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
            } else {
                // Między krokami gracz dokupuje wojsko i czeka na kolejną wskazówkę.
                val hints = hintsOf(bs)
                if (hints != null && hints.activeId >= 0) { multi++; problem("samouczek: dymek #${hints.activeId} w trakcie samouczka") }
                val role = if (b.player.queueSize % 2 == 0) 0 else 1
                val unit = bs.unitBtn[role]
                if (buyBetween() && unit.enabled && b.player.queueSize < 2 && tut.allows(unit)) tap(g, unit, "samouczek/kup") else tick(g, 0.3f)
            }
        }
        if (stop()) return
        assertTrue("$tag: samouczek nie skończył się (krok ${tut.step}, ${b.time}s)", tut.finished || b.over)
        tutorialEnd = b.time
        note(g, "samouczek ukończony po ${"%.1f".format(b.time / 60f)} min bitwy (kroki: ${seen.filter { it < 100 }.sorted()})")
    }

    // ------------------------------------------------------------------ etapy

    protected class StageLog(val stage: Int, val attempt: Int, val o: Outcome)
    protected val stageLogs = ArrayList<StageLog>()

    protected fun startStage(g: Game, index: Int, where: String): BattleScreen? {
        if (g.current !is CampaignScreen) {
            if (g.current !is MenuScreen) backToMenu(g, where)
            tap(g, button(g.current!!, 1), "$where/kampania")
            tick(g, 0.2f, 8)
        }
        val cs = g.current as? CampaignScreen ?: run { problem("$where: brak mapy kampanii (${name(g.current)})"); return null }
        dwell(g, "$where/mapa", 1f)
        val hints = hintsOf(cs)
        if (hints != null && hints.activeId >= 0) {
            note(g, "mapa kampanii: dymek #${hints.activeId} „${hintBody(hints).take(70)}”")
            dwell(g, "$where/mapa z dymkiem", 1f)
            shot(g, "mapa_dymek_${hints.activeId}")
            tap(g, hints.okBtn, "$where/OK dymka mapy")
        }
        val intro = field(cs, "eraIntro").get(cs) as EraIntroOverlay?
        if (intro?.active == true) {
            note(g, "mapa kampanii: zapowiedź nowej epoki (dotknięcie zamyka)")
            shot(g, "mapa_epoka_${index + 1}")
            tick(g, 0.1f, 6)
            tapAt(g, g.width / 2f, g.height / 2f); tick(g, 0.1f, 4)
            if (intro.active) problem("$where: zapowiedź epoki na mapie nie znika po dotknięciu")
        }
        val tile = cs.buttons.first { it.tag === Campaign.stages[index] }
        if (!tile.visible || !tile.enabled) {
            // Noc 5: mapa po bitwie sama przewija się do następnego etapu.
            problem("$where: etap ${index + 1} poza kadrem mapy po wejściu ${tile.r}")
            // Gracz przewija mapę do następnego etapu.
            cs.onDrag(-(tile.r.centerX() - g.width / 2f), -(tile.r.centerY() - g.height / 2f)); tick(g, 0.1f, 4)
        }
        if (!tile.enabled) { problem("$where: etap ${index + 1} zablokowany po wygraniu poprzedniego"); return null }
        tap(g, tile, "$where/etap ${index + 1}")
        tick(g, 0.2f, 6)
        val start = cs.buttons.firstOrNull { it.id == -2 && it.visible }
        if (start == null) { problem("$where: brak przycisku startu etapu ${index + 1}"); return null }
        tap(g, start, "$where/start etapu")
        tick(g, 0.1f, 2)
        return g.current as? BattleScreen
    }

    protected fun campaign(g: Game, lastStage: Int) {
        for (index in 1..lastStage) {
            if (clock >= stopAtClock) { note(g, "koniec ścieżki po ${"%.1f".format(clock / 60f)} min"); return }
            var won = false
            var attempt = 0
            while (!won && attempt < 3) {
                attempt++
                val where = "etap ${index + 1}" + if (attempt > 1) " (podejście $attempt)" else ""
                val bs = startStage(g, index, where) ?: return
                battleHintsSeen.clear()
                if (bs.battle.commanderAvailable) note(g, "$where: Wódz dostępny w HUD")
                // Wariant ostrożny: pierwsza próba etapu 2 kończy się kapitulacją po 75 s
                // (kapitulacja przed 60 s nie liczy się do serii porażek R2-02).
                val forcedLoss = cautious && index == 1 && attempt == 1
                val o = playWithAi(g, bs, where, if (forcedLoss) 75f else 18 * 60f)
                stageLogs.add(StageLog(index, attempt, o))
                note(g, "$where: ${if (o.won) "WYGRANA" else "porażka"} ${"%.1f".format(o.minutes)} min, " +
                    "${o.stars}★, nagroda ${o.reward}, łącznie +${o.medals}; dymki ${battleHintsSeen}")
                if (index == 7 || attempt == 1 && index == 1) shot(g, "wyniki_etap${index + 1}")
                won = o.won
                if (o.won && o.reward <= 0) problem("$where: wygrana bez nagrody")
                if (o.won) checkGoal(g, bs, where)
                // R2-01: raz na ścieżkę gracz dotyka celu zamiast „Dalej”; Wstecz wraca do menu.
                if (o.won && index == 2 && !goalTapped) { goalTapped = true; tapGoal(g, bs, where); continue }
                val help = bs.helpOffer
                if (!o.won && attempt >= 2 && help == BattleScreen.HELP_NONE &&
                    g.profile.campaignDifficulty != com.centurywar.game.CampaignDifficulty.EASY)
                    problem("$where: druga porażka z rzędu bez propozycji pomocy")
                tap(g, bs.continueBtn, "$where/dalej")
                tick(g, 0.2f, 6)
                // R2-02: po 2. porażce Dalej prowadzi do Badań (nad mapą) albo okna etapu z Łatwym.
                if (help != BattleScreen.HELP_NONE) {
                    note(g, "$where: propozycja pomocy „${bs.helpText}”")
                    if (help == BattleScreen.HELP_RESEARCH) {
                        if (g.current !is ResearchScreen) problem("$where: propozycja badania nie otwiera Badań (${name(g.current)})")
                        else { dwell(g, "$where/badania z propozycji", 0.5f); g.requestBack(); tick(g, 0.2f, 4) }
                    } else {
                        val cs = g.current as? CampaignScreen
                        if (cs == null || !cs.easySuggested) problem("$where: propozycja Łatwego bez okna etapu (${name(g.current)})")
                        else { dwell(g, "$where/okno etapu z Łatwym", 0.5f); g.requestBack(); tick(g, 0.2f, 4) }
                    }
                }
                if (g.current !is CampaignScreen) problem("$where: „Dalej” nie prowadzi na mapę (${name(g.current)})")
                dwell(g, "$where/mapa po bitwie", 3.5f)
                if (!cautious && index == 1 && attempt == 1) firstResearch(g, "po etapie 2")
                if (cautious && researchBought == 0 && clock >= 30f * 60f) firstResearch(g, "po 30. minucie")
            }
            if (!won) { note(g, "etap ${index + 1}: 3 porażki z rzędu — gracz utknął"); audit.append("etap ${index + 1}: 3 porażki\n"); return }
        }
    }

    // ------------------------------------------------------------------ R2-01: następny cel

    protected var goalTapped = false

    /** Obszar dotyku przycisku (z hitSlop i przesunięciem). */
    protected fun hitRect(b: Button) = RectF(b.r.left - b.hitSlop + b.hitOffsetX, b.r.top - b.hitSlop + b.hitOffsetY,
        b.r.right + b.hitSlop + b.hitOffsetX, b.r.bottom + b.hitSlop + b.hitOffsetY)

    /** Po wygranej: niepusty cel, cel dotyku ≥ 48 dp, bez nachodzenia na przyciski wyników i baner. */
    protected fun checkGoal(g: Game, bs: BattleScreen, where: String) {
        val goal = bs.resultGoalBtn
        if (bs.resultGoalKind == com.centurywar.meta.NextGoalKind.NONE || bs.tipText.isEmpty()) {
            problem("$where: wygrana bez następnego celu"); return
        }
        if (!goal.visible || goal.r.isEmpty) { problem("$where: cel „${bs.tipText}” bez celu dotyku"); return }
        val hit = hitRect(goal)
        val dp = g.ui.dp
        if (hit.height() < 47.5f * dp) problem("$where: cel dotyku ${hit.height() / dp} dp")
        for (b in listOf(bs.continueBtn, bs.restartEnd, bs.replayEnd, bs.resultStatsTab, bs.resultChartTab))
            if (b.visible && RectF.intersects(hit, b.r)) problem("$where: cel nachodzi na przycisk #${b.id} '${b.label}'")
        if (!g.noticeBanner.isEmpty && RectF.intersects(g.noticeBanner, hit)) problem("$where: baner nad celem")
        if (bs.accessibilityLabel(goal) != bs.tipText) problem("$where: opis TalkBack celu ≠ tekst celu")
        if (reported.add("goal $where")) audit.append("$tag/$where: cel ${bs.resultGoalKind} „${bs.tipText}”\n")
    }

    protected fun tapGoal(g: Game, bs: BattleScreen, where: String) {
        val kind = bs.resultGoalKind
        note(g, "$where: gracz dotyka celu „${bs.tipText}” ($kind)")
        shot(g, "wyniki_cel")
        tap(g, bs.resultGoalBtn, "$where/cel")
        tick(g, 0.2f, 6)
        val expected = when (kind) {
            com.centurywar.meta.NextGoalKind.MISSIONS -> MissionsScreen::class.java
            com.centurywar.meta.NextGoalKind.RESEARCH -> ResearchScreen::class.java
            com.centurywar.meta.NextGoalKind.STAGE -> CampaignScreen::class.java
            com.centurywar.meta.NextGoalKind.WEEKLY_TIER -> ModeScreen::class.java
            com.centurywar.meta.NextGoalKind.NONE -> null
            com.centurywar.meta.NextGoalKind.HARD_STAGE -> CampaignScreen::class.java
        }
        if (g.current?.javaClass != expected) problem("$where: cel $kind otwiera ${name(g.current)}")
        dwell(g, "$where/ekran celu", 1f)
        val hints = hintsOf(g.current!!)
        if (hints != null && hints.activeId >= 0) tap(g, hints.okBtn, "$where/OK dymka celu")
        backToMenu(g, "$where/po celu")
        val menu = g.current as? MenuScreen ?: return
        tick(g, 0.1f, 2)
        if (menu.goalBtn.visible) {
            note(g, "menu: cel „${menu.goalText}” (${menu.goalKind})")
            shot(g, "menu_cel")
            val hit = hitRect(menu.goalBtn)
            if (hit.height() < 47.5f * g.ui.dp) problem("menu: cel dotyku ${hit.height() / g.ui.dp} dp")
            if (!g.noticeBanner.isEmpty && RectF.intersects(g.noticeBanner, menu.goalBtn.r)) problem("menu: baner nad celem")
            for (b in menu.buttons) if (b !== menu.goalBtn && b.visible && b.id in 1..9 && RectF.intersects(b.r, menu.goalBtn.r))
                problem("menu: cel nachodzi na kafel #${b.id}")
        } else if (reported.add("menu goal hidden")) audit.append("$tag: menu bez linii celu (brak miejsca lub celu)\n")
    }

    // ------------------------------------------------------------------ meta

    protected var researchBought = 0

    protected fun firstResearch(g: Game, where: String) {
        backToMenu(g, where)
        val menu = g.current as MenuScreen
        val cheapest = Research.defs.minOf { it.cost(0) }
        note(g, "$where: menu — najtańsze badanie kosztuje $cheapest")
        if (menu.portraitMenu) menu.onDrag(0f, -100000f)
        tick(g)
        tap(g, button(menu, 5), "$where/badania")
        tick(g, 0.2f, 8)
        val rs = g.current as? ResearchScreen ?: run { problem("$where: brak ekranu badań"); return }
        dwell(g, "$where/badania", 1f)
        val hints = hintsOf(rs)
        if (hints != null && hints.activeId >= 0) {
            note(g, "badania: dymek „${hintBody(hints).take(70)}”")
            shot(g, "badania_dymek")
            tap(g, hints.okBtn, "$where/OK dymka badań")
        }
        var bought = 0
        var guard = 0
        while (guard++ < 12) {
            val card = rs.buttons.filter { it.tag != null && it.visible && it.enabled }
                .minByOrNull { (it.tag as com.centurywar.game.ResearchDef).let { d -> d.cost(g.profile.level(d.key)) } }
                ?: break
            val d = card.tag as com.centurywar.game.ResearchDef
            val before = g.profile.medals
            val level = g.profile.level(d.key)
            tap(g, card, "$where/kup ${d.key}")
            tick(g, 0.1f, 2)
            if (g.profile.level(d.key) != level + 1) { problem("$where: dotknięcie dostępnego badania ${d.key} nie kupuje"); break }
            bought++
            note(g, "badania: kupione ${L[d.key]} za ${d.cost(level)}" +
                if (g.profile.medals > before - d.cost(level)) " (+${g.profile.medals - before + d.cost(level)} za osiągnięcie)" else "")
            dwell(g, "$where/badania po zakupie", 0.5f)
        }
        if (researchBought == 0 && bought == 0)
            problem("$where: po ${g.profile.battles} bitwach brak badania w zasięgu (medale ${g.profile.medals}, najtańsze $cheapest)")
        researchBought += bought
        shot(g, "badania_po_zakupie")
        backToMenu(g, where)
    }

    protected fun skirmish(g: Game) {
        val menu = g.current as MenuScreen
        tap(g, button(menu, 4), "potyczka")
        tick(g, 0.2f, 6)
        dwell(g, "potyczka/ustawienia", 0.5f)
        val setup = g.current as? SkirmishSetupScreen ?: run { problem("brak ekranu potyczki"); return }
        tap(g, button(setup, -2), "potyczka/start")
        val bs = g.current as? BattleScreen ?: run { problem("potyczka: start nie otwiera bitwy"); return }
        battleHintsSeen.clear()
        val o = playWithAi(g, bs, "potyczka")
        note(g, "potyczka: ${if (o.won) "WYGRANA" else "porażka"} ${"%.1f".format(o.minutes)} min, nagroda ${o.reward}; dymki $battleHintsSeen")
        tap(g, bs.continueBtn, "potyczka/dalej")
        tick(g, 0.2f, 6)
        backToMenu(g, "potyczka")
    }

    protected fun endless(g: Game) {
        tap(g, button(g.current!!, 2), "endless")
        tick(g, 0.2f, 8)
        val ms = g.current as? ModeScreen ?: run { problem("brak ekranu Endless"); return }
        dwell(g, "endless/ekran", 1f)
        hintsOf(ms)?.let { h ->
            if (h.activeId >= 0) { note(g, "Endless: dymek „${hintBody(h).take(70)}”"); shot(g, "endless_dymek"); tap(g, h.okBtn, "endless/OK") }
        }
        tap(g, button(ms, -2), "endless/start")
        val bs = g.current as? BattleScreen ?: run { problem("Endless: start nie otwiera bitwy"); return }
        battleHintsSeen.clear()
        var cards = 0
        val b = bs.battle
        b.playerAi = Ai(b, 0)
        var guard = 0
        // Karta co piątą falę: gracz widzi wybór, AI wybiera przez BattleController.
        while (!bs.finished && guard++ < 20000) {
            if (b.pendingCards != null) {
                cards++
                if (cards == 1) { tick(g); shot(g, "endless_karty"); note(g, "Endless: pierwszy wybór kart (fala ${b.wave})") }
                tap(g, bs.doctrineBtn[0], "endless/karta")
                continue
            }
            if (handleOverlays(g, bs, "Endless")) continue
            tick(g, 1f / 3f)
            checkBattle(g, bs, "Endless")
            if (!b.over && b.time > 15 * 60f) surrender(g, bs, "Endless")
        }
        assertTrue("$tag: Endless nie skończył się (czas ${b.time}, fala ${b.wave}, karty ${b.pendingCards?.size}, " +
            "wybór ${b.pendingChoice != null}, wariant ${b.pendingVariant}, pauza ${bs.paused}, epoka ${eraIntro(bs)?.active}, " +
            "dymek ${hintsOf(bs)?.activeId}, karta0 ${bs.doctrineBtn[0].visible} ${bs.doctrineBtn[0].r}, kart $cards)\n$timeline", bs.finished)
        tick(g, 0.25f, 16)
        checkResults(g, bs, "Endless")
        note(g, "Endless: fala ${b.wave}, ${"%.1f".format(b.time / 60f)} min, kart $cards, nagroda ${bs.reward}; dymki $battleHintsSeen")
        shot(g, "endless_wyniki")
        g.requestBack(); tick(g, 0.2f, 6)
        if (g.current !is ModeScreen) problem("Endless: Wstecz z wyników nie wraca do ekranu trybu (${name(g.current)})")
        backToMenu(g, "endless")
    }

    protected fun daily(g: Game) {
        tap(g, button(g.current!!, 3), "wyzwanie dnia")
        tick(g, 0.2f, 8)
        val ms = g.current as? ModeScreen ?: run { problem("brak ekranu wyzwania dnia"); return }
        dwell(g, "dzień/ekran", 1f)
        hintsOf(ms)?.let { h ->
            if (h.activeId >= 0) { note(g, "wyzwanie dnia: dymek „${hintBody(h).take(70)}”"); shot(g, "dzien_dymek"); tap(g, h.okBtn, "dzień/OK") }
        }
        shot(g, "dzien_ekran")
        tap(g, button(ms, -2), "dzień/start")
        val bs = g.current as? BattleScreen ?: run { problem("wyzwanie dnia: start nie otwiera bitwy"); return }
        note(g, "wyzwanie dnia: wydarzenie ${bs.battle.cfg.dailyEvent}, modyfikatory ${bs.battle.cfg.modifiers}")
        battleHintsSeen.clear()
        val o = playWithAi(g, bs, "wyzwanie dnia")
        note(g, "wyzwanie dnia: ${if (o.won) "WYGRANA" else "porażka"} ${"%.1f".format(o.minutes)} min, nagroda ${o.reward}; dymki $battleHintsSeen")
        tap(g, bs.continueBtn, "dzień/dalej"); tick(g, 0.2f, 6)
        backToMenu(g, "dzień")
    }

    protected fun whatsNew(g: Game) {
        val menu = g.current as MenuScreen
        if (menu.portraitMenu) menu.onDrag(0f, -100000f)
        tick(g)
        tap(g, button(menu, 7), "ustawienia")
        tick(g, 0.2f, 6)
        val st = g.current as? SettingsScreen ?: run { problem("brak Ustawień"); return }
        var about = button(st, 19)
        if (!about.visible || about.r.bottom > g.height) {
            field(st, "scroll").setFloat(st, field(st, "maxScroll").getFloat(st)); st.layout(); tick(g, 0.1f, 2)
            about = button(st, 19)
        }
        tap(g, about, "ustawienia/o grze")
        tick(g, 0.2f, 6)
        val ab = g.current as? AboutScreen ?: run { problem("brak ekranu O grze"); return }
        var hist = button(ab, 5)
        var drags = 0
        // Gracz przewija O grze krótkimi ruchami, aż zobaczy „Historię zmian”.
        while (!hist.visible && drags++ < 40) { ab.onDrag(0f, -20f * g.ui.dp); tick(g); hist = button(ab, 5) }
        tap(g, hist, "o grze/co nowego")
        tick(g, 0.2f, 6)
        val wn = g.current as? WhatsNewScreen ?: run { problem("„Co nowego” się nie otwiera"); return }
        shot(g, "co_nowego")
        note(g, "„Co nowego”: Ustawienia → O grze → Historia zmian")
        val close = button(wn, 1)
        tap(g, close, "co nowego/wstecz")
        tick(g, 0.2f, 4)
        if (g.current !is AboutScreen) problem("Wstecz z „Co nowego” nie wraca do O grze (${name(g.current)})")
        backToMenu(g, "co nowego")
    }

    // ------------------------------------------------------------------ ścieżka

    protected fun journey(d: Device, lang: String, lastStage: Int) {
        clock = 0f
        stageLogs.clear()
        researchBought = 0
        goalTapped = false
        timeline.setLength(0)
        achievementTimes.clear()
        val g = game(d, lang)
        L.setCode(lang)
        assertEquals("$tag: pierwsze uruchomienie bez menu", MenuScreen::class.java, g.current?.javaClass)
        tick(g, 0.1f, 5)
        note(g, "pierwsze uruchomienie: menu, Kampania „${button(g.current!!, 1).sub}”")
        checkScreen(g, "menu start")
        hintsOf(g.current!!)?.let { if (it.activeId >= 0) problem("menu startowe z dymkiem #${it.activeId}") }
        shot(g, "01_menu")
        tap(g, button(g.current!!, 1), "graj")
        val bs = g.current as? BattleScreen ?: error("$tag: „Graj” nie otwiera bitwy")
        if (!bs.cfg.tutorial || bs.tut == null) problem("pierwsza bitwa bez samouczka")
        tick(g, 1f / 30f, 4)
        playTutorial(g, bs)
        // Po samouczku gracz gra dalej sam (AI), aż do wyniku.
        battleHintsSeen.clear()
        val o = playWithAi(g, bs, "etap 1 (samouczek)")
        stageLogs.add(StageLog(0, 1, o))
        note(g, "etap 1: ${if (o.won) "WYGRANA" else "porażka"} ${"%.1f".format(o.minutes)} min, ${o.stars}★, nagroda ${o.reward}, łącznie +${o.medals}; dymki $battleHintsSeen")
        shot(g, "wyniki_samouczek")
        if (!o.won) problem("samouczek przegrany przez AI gracza")
        if (!g.profile.tutorialDone) problem("po samouczku tutorialDone = false")
        tap(g, bs.continueBtn, "samouczek/dalej")
        tick(g, 0.2f, 6)
        if (g.current !is CampaignScreen) problem("po samouczku „Dalej” nie prowadzi na mapę (${name(g.current)})")
        dwell(g, "mapa po samouczku", 3.5f)
        shot(g, "mapa_po_samouczku")
        val cheapest = Research.defs.minOf { it.cost(0) }
        if (g.profile.medals < cheapest) audit.append("po samouczku: ${g.profile.medals} medali < $cheapest\n")
        campaign(g, lastStage)
        backToMenu(g, "po kampanii")
        if (researchBought == 0) firstResearch(g, "po kampanii")
        else firstResearch(g, "kolejne badania")
        skirmish(g)
        endless(g)
        daily(g)
        whatsNew(g)
        note(g, "koniec: ${g.profile.battles} bitew, odblokowany etap ${g.profile.unlockedStage() + 1}, badań kupionych $researchBought")

        // Nagrody: wygrana etapu daje więcej niż porażka tego etapu; wygrane rosną z etapem.
        val wins = stageLogs.filter { it.o.won }
        for (l in stageLogs) if (!l.o.won) {
            val w = wins.firstOrNull { it.stage == l.stage }
            if (w != null && l.o.reward >= w.o.reward) problem("etap ${l.stage + 1}: porażka (${l.o.reward}) ≥ wygrana (${w.o.reward})")
        }
        for (i in 1 until wins.size) {
            val a = wins[i - 1]; val b = wins[i]
            if (b.stage > a.stage && b.o.stars >= a.o.stars && b.o.reward < a.o.reward)
                problem("nagroda etapu ${b.stage + 1} (${b.o.reward}) mniejsza niż etapu ${a.stage + 1} (${a.o.reward})")
        }
        val table = StringBuilder("\netap;podejście;wynik;min;gwiazdki;nagroda;medale łącznie\n")
        for (l in stageLogs) table.append("${l.stage + 1};${l.attempt};${if (l.o.won) "W" else "P"};" +
            "${"%.1f".format(l.o.minutes)};${l.o.stars};${l.o.reward};${l.o.medals}\n")
        val achievements = checkFirstHourAchievements("pierwsza godzina")
        File(out, "fh_${tag}_os_czasu.txt").writeText(timeline.toString() + table + achievements)
        g.history?.close()
    }

    /**
     * R6-01, wariant ostrożny: gracz pomija samouczek, raz przegrywa etap 2 (kapitulacja po 75 s)
     * i nie kupuje badań przed 30. minutą. Nadal ≥ 4 osiągnięcia z puli przed 60:00.
     */
    protected fun cautiousJourney(d: Device, lang: String) {
        clock = 0f
        stageLogs.clear()
        researchBought = 0
        goalTapped = true // bez dotykania celu: ścieżka ostrożna idzie zawsze przez „Dalej”
        cautious = true
        timeline.setLength(0)
        achievementTimes.clear()
        try {
            val g = game(d, lang)
            L.setCode(lang)
            tick(g, 0.1f, 5)
            tap(g, button(g.current!!, 1), "graj")
            val bs = g.current as? BattleScreen ?: error("$tag: „Graj” nie otwiera bitwy")
            val tut = requireNotNull(bs.tut) { "pierwsza bitwa bez samouczka" }
            var guard = 0
            while (!tut.showing && guard++ < 600) tick(g, 0.1f)
            tap(g, tut.skipBtn, "samouczek/pomiń")
            if (!tut.finished && tut.skipBtn.visible) tap(g, tut.skipBtn, "samouczek/pomiń — potwierdź")
            if (!tut.finished) problem("„Pomiń samouczek” nie kończy samouczka")
            note(g, "samouczek pominięty po ${"%.1f".format(bs.battle.time)} s")
            tutorialEnd = bs.battle.time
            battleHintsSeen.clear()
            val o = playWithAi(g, bs, "etap 1 (pominięty samouczek)")
            stageLogs.add(StageLog(0, 1, o))
            note(g, "etap 1: ${if (o.won) "WYGRANA" else "porażka"} ${"%.1f".format(o.minutes)} min, nagroda ${o.reward}")
            tap(g, bs.continueBtn, "etap 1/dalej")
            tick(g, 0.2f, 6)
            dwell(g, "mapa po etapie 1", 3.5f)
            stopAtClock = 62f * 60f
            campaign(g, lastStage = 7)
            val achievements = checkFirstHourAchievements("wariant ostrożny")
            if (achievementTimes.containsKey("first_research") && achievementTimes.getValue("first_research") < 30f * 60f)
                problem("wariant ostrożny: badanie kupione przed 30. minutą")
            note(g, "koniec wariantu ostrożnego: ${g.profile.battles} bitew, badań kupionych $researchBought")
            File(out, "fh_${tag}_os_czasu.txt").writeText(timeline.toString() + achievements)
            g.history?.close()
        } finally {
            cautious = false
            stopAtClock = Float.MAX_VALUE
        }
    }

    /** Wariant ostrożny: pierwsza próba etapu 2 to porażka, badania dopiero po 30. minucie. */
    protected var cautious = false
    protected var stopAtClock = Float.MAX_VALUE

    protected fun cautiousFirstHourOn(device: String, lang: String) {
        val old = L.lang
        try {
            tag = "${device}_${lang}_ostrozny"
            reported.clear()
            cautiousJourney(devices.getValue(device), lang)
        } finally {
            L.lang = old
            File(out, "fh_audit_${device}_${lang}_ostrozny.txt").writeText(audit.toString() + "\n--- PROBLEMY ---\n" + problems)
        }
        assertTrue("Pierwsza godzina (wariant ostrożny) — problemy:\n$problems", problems.isEmpty())
    }

    /**
     * Ścieżka na jednym urządzeniu z [devices] dla języków z pozycji o parzystości [langParity]
     * (domyślnie pl = 0, en = 1; `CW_TEXT_LANGS` zmienia listę). Noc 9: dawna pętla
     * „wszystkie urządzenia × wszystkie języki” jest rozłożona na cztery klasy testowe, żeby
     * procesy Gradle dostawały po jednej ścieżce. Dziennik: fh_audit_<urządzenie>_<języki>.txt.
     */
    protected fun firstHourOn(device: String, langParity: Int) {
        val old = L.lang
        // Noc 7: CW_TEXT_LANGS=de,uk,... zmienia języki ścieżki (domyślnie pl i en).
        val langs = (System.getenv("CW_TEXT_LANGS")?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }
            ?.takeIf { it.isNotEmpty() } ?: listOf("pl", "en")).filterIndexed { i, _ -> i % 2 == langParity }
        try {
            val d = devices.getValue(device)
            for (lang in langs) {
                tag = "${device}_$lang"
                reported.clear()
                journey(d, lang, lastStage = 7)
            }
        } finally {
            L.lang = old
            if (langs.isNotEmpty()) File(out, "fh_audit_${device}_${langs.joinToString("_")}.txt")
                .writeText(audit.toString() + "\n--- PROBLEMY ---\n" + problems)
        }
        assertTrue("Pierwsza godzina — problemy:\n$problems", problems.isEmpty())
    }

    // ------------------------------------------------------------------ przerwanie samouczka

    protected fun flush(g: Game) {
        repeat(3) {
            Profile.awaitHintWrites()
            assertTrue(g.snapshots!!.awaitWrites(5_000L))
        }
    }

    protected fun copyDir(from: File, name: String): File {
        val to = folder.newFolder(name)
        from.listFiles()?.filter { it.isFile }?.forEach { it.copyTo(File(to, it.name)) }
        return to
    }
}
