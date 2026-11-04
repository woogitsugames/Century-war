package com.centurywar.engine

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import com.centurywar.BuildConfig
import com.centurywar.crash.CrashReports
import com.centurywar.game.BattleMeta
import com.centurywar.game.Daily
import com.centurywar.game.Mode
import com.centurywar.game.RestoredBattle
import com.centurywar.game.SnapshotStore
import com.centurywar.meta.AchievementDef
import com.centurywar.meta.OnlineAchievements
import com.centurywar.meta.OnlineLeaderboards
import com.centurywar.meta.LostBattle
import com.centurywar.meta.Profile
import com.centurywar.meta.ReplayStore
import com.centurywar.meta.BattleHistory
import com.centurywar.meta.ScoreClient
import com.centurywar.meta.Missions
import com.centurywar.meta.PlaytestLog
import com.centurywar.meta.WelcomeBack
import com.centurywar.meta.WhatsNewCatalog
import com.centurywar.screens.AccessibilityScreen
import com.centurywar.screens.BattleScreen
import com.centurywar.screens.CodexScreen
import com.centurywar.screens.MenuScreen
import com.centurywar.screens.MissionsScreen
import com.centurywar.screens.ReplayListScreen
import com.centurywar.screens.ResearchScreen
import com.centurywar.screens.SkirmishSetupScreen
import com.centurywar.screens.WhatsNewScreen
import com.centurywar.screens.ReplayScreen

/** Rzeczy, których wątek gry potrzebuje od Activity (wątek UI). */
interface Host {
    fun vibrate(ms: Long)
    /** Starsze implementacje Host zachowują krótkie kliknięcie. */
    fun haptic(kind: HapticKind) { vibrate(12L) }
    fun requestText(title: String, current: String, onResult: (String) -> Unit)
    fun applyRenderScale(scale: Float)
    fun applyFrameRate(fps: Int)
    fun exit()
    fun onWantsBackChanged(wantsBack: Boolean) {}
    fun onBattleWon() {}
    /** R10-04 L1 (wątek gry): wejście do menu, np. odłożone pytanie o raport awarii. */
    fun onMenuShown() {}
    fun onNotificationSettingsChanged() {}
    fun requestNotifyHour(current: Int, onResult: (Int) -> Unit) {}
    fun requestGlobalRankingConsent(onAccepted: () -> Unit) {}
    fun openExternalUrl(url: String) {}
    fun showApacheLicense() {}
    fun shareProblemReport() {}
    /** R2-04: „Raport dla testera” (okno z opisem, potem systemowe udostępnianie). */
    fun sharePlaytestReport() {}
    fun shareReplay(id: String) {}
    fun requestProfileExport() {}
    fun requestProfileImport() {}
    fun requestProfileRestorePrevious() {}
}

/**
 * Stan całej aplikacji po stronie wątku gry: aktywny ekran, profil, jakość grafiki,
 * kolejka dotyku. Zmiany rozmiaru (np. rozłożenie Folda) przychodzą przez [resize]
 * i każdy ekran przelicza wtedy swój układ, bez restartu Activity.
 */
class Game(
    val host: Host,
    val profile: Profile,
    private val detectedQuality: Int,
    /**
     * Zegar ścienny (ms od epoki) dla logiki dni UTC menu: seria logowań, rozliczenie bitwy
     * utraconej przy aktualizacji, powitanie po przerwie (R10-07), plakietka misji i cel.
     * W grze zawsze czas systemowy; testy podają stały, by dzień nie zależał od północy UTC.
     * Parametr konstruktora, bo już `init` (seria logowań) liczy dzień.
     */
    internal var clock: () -> Long = { System.currentTimeMillis() },
) {
    val ui = Ui()
    var width = 1
    var height = 1
    /** Bezpieczne marginesy (wycięcia aparatu, zaokrąglenia ekranu) w pikselach bufora. */
    var insetL = 0f
    var insetR = 0f
    var insetT = 0f
    var insetB = 0f
    var density = 1f
    var renderScale = 1f
    var viewW = 1
    var viewH = 1
    /** Limit sterty z ActivityManager.memoryClass; testy mogą podstawić słaby telefon. */
    var bitmapCacheMemoryClassMb = 128
    var quality = Quality(Quality.HIGH)
        private set
    /**
     * R11-04: gra działa w pętli testowej (raport przedpremierowy Play, Test Lab). Jedno miejsce
     * blokady: bez rankingu online, wysyłki wyników i Play Games, nawet gdyby ktoś je ustawił.
     */
    var testLoop = false
        set(value) {
            field = value
            if (value) {
                online = OnlineLeaderboards.Local
                scores = null
                onlineAchievements = OnlineAchievements.NoOp
            }
        }
    var online: OnlineLeaderboards = OnlineLeaderboards.Local
        set(value) { field = if (testLoop) OnlineLeaderboards.Local else value }
    var scores: ScoreClient? = null
        set(value) { field = if (testLoop) null else value }
    var onlineAchievements: OnlineAchievements = OnlineAchievements.NoOp
        set(value) { field = if (testLoop) OnlineAchievements.NoOp else value }
    /** Null w testach; na urządzeniu ustawia MainActivity. */
    @Volatile var sound: Sound? = null
    /** Muzyka działa niezależnie od efektów SoundPool. */
    var music: Music? = null

    fun applyMusicVolume() { music?.setVolume(profile.musicVolume) }

    /** Jeden kontrakt odświeżenia ustawień po imporcie pliku i po synchronizacji PGS. */
    fun applyLoadedProfile() {
        onLanguageChanged()
        applyAccessibility()
        // Profil z pliku lub chmury: ustawienia telefonu znów obowiązują tam, gdzie gracz nie wybrał sam.
        systemA11y?.let { applySystemAccessibility(it) }
        setQuality(effectiveQualityLevel())
        applyFrameRate()
        applySfxVolume()
        applyMusicVolume()
        host.onNotificationSettingsChanged()
    }
    fun applySfxVolume() {
        sound?.volume = profile.sfxVolume
        sound?.enabled = profile.sound
    }

    private fun syncMusicScreen(s: Screen) {
        battleActive = s is BattleScreen
        if (s !is BattleScreen && s !is ReplayScreen) {
            music?.setEra(Music.MENU)
            music?.setIntensity(0f)
        }
    }
    /** Zrzut przerwanej bitwy (filesDir/battle.snap). Null w testach. */
    var snapshots: SnapshotStore? = null
    /** Powtórki i indeks są czytane wyłącznie przez wątek I/O. */
    var replays: ReplayStore? = null
    /** Plik historii jest lokalny i nigdy nie jest synchronizowany z chmurą. */
    var history: BattleHistory? = null
    /** Ustawiane na wątku gry po zakończeniu odczytu historii w tle. */
    var historyLoaded = false
    /** R2-04: lokalny dziennik testu (files/playtest.json). Null w testach bez dziennika. */
    var playtest: PlaytestLog? = null
    /** R11 (pozycja 7): ankieta testera w menu (tylko wersje testowe; testy mogą wyłączyć). */
    var testerSurveyEnabled = BuildConfig.TESTER_SURVEY
    private var playtestBattleSeconds = 0f
    /** Bitwa przerwana przez zabicie aplikacji: MenuScreen pyta, czy ją wznowić. */
    var resumeOffer: RestoredBattle? = null
    /**
     * R10-01: bitwa, której zrzutu nie da się wznowić po aktualizacji gry. Rozliczana raz,
     * przy pierwszym [MenuScreen.update] ([settleLostBattle]). Nigdy razem z [resumeOffer].
     */
    var lostBattle: BattleMeta? = null

    /**
     * R10-01: medale jak za kapitulację i zwrot dzisiejszej próby wyzwania dnia, dokładnie raz
     * (identyfikator bitwy w profilu). Pliki zrzutu i opisu znikają po trwałym zapisie profilu.
     * Komunikat idzie zwykłą kolejką banerów (bez okna, nigdy nad bitwą i wynikami).
     */
    fun settleLostBattle(today: Long = Daily.dayNumber(clock())) {
        val meta = lostBattle ?: return
        lostBattle = null
        val store = snapshots
        val result = LostBattle.settle(profile, meta, today,
            onPersisted = { store?.deleteSettledAsync(meta.battleId) })
        if (result == null) {
            store?.deleteSettledAsync(meta.battleId)
            return
        }
        val texts = LostBattle.notices(meta, result)
        showNotice(texts[0], Icons.CLOSE)
        if (texts.size > 1) showNotice(texts[1], if (result.dailyRestored) Icons.CHECK else Icons.MEDAL)
    }

    /**
     * R10-07: powitanie po przerwie. Wołane z [MenuScreen.update] po rozliczeniu bitwy
     * utraconej przy aktualizacji (najpierw jej banery, potem powitanie) i nigdy przy ofercie
     * wznowienia bitwy. Tani test dnia w każdej klatce; zapis profilu raz na dzień UTC.
     */
    fun checkWelcomeBack(nowMillis: Long = clock()) {
        if (resumeOffer != null || lostBattle != null) return
        val today = Daily.dayNumber(nowMillis)
        if (today <= profile.lastPlayDay) return
        if (!profile.claimWelcomeBack(today, WelcomeBack.MIN_GAP_DAYS, WelcomeBack.BONUS)) return
        if (width > 1) celebrateReward(width * 0.5f, height * 0.6f, WelcomeBack.BONUS, Sound.MEDALS)
        for (n in WelcomeBack.notices(profile, nowMillis, WhatsNewScreen.unread(this))) showNotice(n.text, n.icon)
    }

    /** Jedna operacja resetu na wątku gry: unieważnia też kolejkę snapshotów. */
    fun resetProgress() {
        snapshots?.deleteAsync()
        resumeOffer = null
        lostBattle = null
        profile.reset()
        history?.clear()
    }

    fun sfx(
        id: Int, vol: Float = 1f, gap: Long = 45L, pitch: Float = 1f,
        pan: Float = 0f, priority: Int = 1,
    ) {
        if (profile.sound && profile.sfxVolume > 0f) sound?.play(id, vol, gap, pitch, pan, priority)
    }

    private var screen: Screen? = null
    private val stack = ArrayList<Screen>()

    // Przenikanie ekranów bez buforów Bitmap i bez zmiany kroku symulacji.
    private var transitionFrom: Screen? = null
    private var transitionTime = 0f
    private var transitionDirection = 1f
    private val transitionDuration = 0.22f
    internal val transitionActive: Boolean get() = transitionFrom != null

    /** Używane przez testy zrzutów, które potrzebują docelowego, nieruchomego ekranu. */
    internal fun finishScreenTransition() { transitionFrom = null }

    private fun startScreenTransition(from: Screen?, to: Screen, direction: Float) {
        transitionFrom = null
        if (from == null || from === to || profile.reduceFlash || quality.level == Quality.LOW ||
            from is BattleScreen || to is BattleScreen ||
            from is ReplayScreen || to is ReplayScreen) return
        transitionFrom = from
        transitionTime = 0f
        transitionDirection = direction
    }

    /** Kierunkowe nasunięcie; bez alokacji klatek, renderowania bitmap i pracy w :sim. */
    private fun drawScreen(c: Canvas, dt: Float) {
        val previous = transitionFrom
        val active = screen
        if (previous == null || active == null) {
            active?.render(c)
            return
        }
        if (profile.reduceFlash || quality.level == Quality.LOW) {
            finishScreenTransition()
            active.render(c)
            return
        }
        transitionTime += dt
        if (transitionTime >= transitionDuration) {
            finishScreenTransition()
            active.render(c)
            return
        }
        val t = (transitionTime / transitionDuration).coerceIn(0f, 1f)
        val smooth = t * t * (3f - 2f * t)
        val shift = width * (1f - smooth) * transitionDirection
        previous.render(c)
        val saved = c.save()
        if (shift > 0f) c.clipRect(shift, 0f, width.toFloat(), height.toFloat())
        else c.clipRect(0f, 0f, width + shift, height.toFloat())
        c.translate(shift, 0f)
        active.render(c)
        c.restoreToCount(saved)
    }
    /** Publikowany tylko po zmianie; odczyt przez Activity odbywa się na wątku UI. */
    @Volatile var wantsBack = false
        private set

    private fun refreshBackHandling() {
        val s = screen
        val next = stack.isNotEmpty() || (s != null && (s !is MenuScreen || s.hasOpenModal))
        if (next != wantsBack) {
            wantsBack = next
            host.onWantsBackChanged(next)
        }
    }
    private val input = ArrayList<TouchEvent>()
    private val inputPool = ArrayList<TouchEvent>()
    private val inputLock = Any()
    private val tasks = ArrayList<Runnable>()
    private val frameEvents = ArrayList<TouchEvent>()

    // CPU nie obejmuje vsync i uśpienia; pomiary post/odstępu dopisuje GameView.
    val frameMetrics = FrameMetrics()
    private val governor = QualityGovernor()

    /**
     * R2-05: status termiczny (`PowerManager.THERMAL_STATUS_*`) od słuchacza w MainActivity
     * i zapas `getThermalHeadroom(10)` (NaN = brak danych, API < 30 albo poza bitwą).
     * Zapisywane z wątku UI, czytane w [monitor].
     */
    @Volatile var thermalStatus = 0
    @Volatile var thermalHeadroom = Float.NaN
    /** Czy bieżący ekran to bitwa; wątek UI odpytuje zapas termiczny tylko wtedy. */
    @Volatile var battleActive = false
        private set
    private var frameAvg = 16f
    var fps = 60f
        private set
    private var simulatedSteps = 0
    private var discardedSteps = 0
    private var overlayTimer = 0f
    private var overlayTop = ""
    private var overlayBottom = ""
    private val fpsPaint = Paint().apply { color = Color.YELLOW }

    fun reportSimSteps(steps: Int, discarded: Int) {
        simulatedSteps = steps
        discardedSteps = discarded
    }

    val cpuBudgetMs: Float get() = QualityGovernor.budgetMs(effectiveFpsCap())

    private fun refreshOverlay(dt: Float) {
        overlayTimer -= dt
        if (overlayTimer > 0f && overlayTop.isNotEmpty()) return
        overlayTimer = 0.5f
        val m = frameMetrics
        fun tenth(v: Float) = (v * 10f).toInt() / 10f
        overlayTop = "${fps.toInt()} FPS  U ${tenth(m.avgUpdateMs)}  R ${tenth(m.avgRenderMs)}  GPU/post ${tenth(m.avgPostMs)}  p95 ${tenth(m.p95Ms())} ms"
        val thermal = thermalStatus
        overlayBottom = "Q${quality.level}-${quality.reduction}  ${(renderScale * 100).toInt()}%  p${quality.maxParticles}  steps ${m.steps}  lost ${m.droppedSteps}" +
            (if (thermal > 0) "  T$thermal" else "")
    }
    /** Gotowe teksty powiadomień (budowane przy zdarzeniu, nie w renderze) i ich ikony. */
    private val noticeQueue = ArrayDeque<String>()
    private val noticeIcons = ArrayDeque<Int>()
    private var noticeIcon = Icons.MEDAL
    private val achievementBanner = RectF()
    private var achievementMessage = ""
    private var achievementSeconds = 0f
    internal val rewardFeedback = RewardFeedback()
    private var pendingLoginReward = 0

    /** Jednorazowe efekty nagrody; cząstki są obsługiwane przez stałą pulę. */
    fun celebrateReward(x: Float, y: Float, amount: Int, soundId: Int = Sound.REWARD) {
        if (amount <= 0) return
        sfx(soundId, 0.78f, 100L, priority = 3)
        haptic(HapticKind.EVOLVE)
        rewardFeedback.launch(this, x, y, amount)
    }

    /** Kolejka powiadomień; tekst jest przygotowany przy odblokowaniu, nie w renderze. */
    fun showAchievements(unlocked: List<AchievementDef>) {
        if (unlocked.isNotEmpty()) {
            val earned = unlocked.sumOf { it.medalReward }
            celebrateReward(width * 0.5f, height * 0.6f, earned, Sound.ACHIEVEMENT)
        }
        // R6-01: po pierwszej bitwie wpada naraz do 4 osiągnięć; ponad dwa banery (ok. 6 s)
        // łączymy: pierwsze z nazwą, reszta w jednym banerze z liczbą i medalami.
        if (unlocked.size <= 2) {
            for (a in unlocked) showNotice(achievementNotice(a), Icons.CROWN)
        } else {
            showNotice(achievementNotice(unlocked[0]), Icons.CROWN)
            val rest = unlocked.subList(1, unlocked.size)
            showNotice(L.f("ach185_more", rest.size, rest.sumOf { it.medalReward }), Icons.CROWN)
        }
    }

    private fun achievementNotice(a: AchievementDef): String =
        L["achievement_unlocked"] + ": " + L["ach_" + a.id] + " (+" + a.medalReward + ")"

    fun showNotice(message: String, icon: Int = Icons.MEDAL) {
        if (noticeQueue.size >= 8) return // zalew powiadomień: resztę widać na ekranie osiągnięć
        noticeQueue.addLast(message); noticeIcons.addLast(icon)
    }

    /**
     * Seria logowań: odświeża misje i przyznaje dzienną nagrodę (raz na dzień UTC). Wołane przy
     * starcie i po powrocie aplikacji z tła (MainActivity.onResume przez [post]), bo proces
     * potrafi przeżyć w tle północ i nagroda kolejnego dnia nie zostałaby przyznana.
     */
    fun checkDailyLogin(nowMillis: Long = clock()) {
        val m = Missions(profile)
        m.refresh(nowMillis)
        val reward = m.claimLogin(nowMillis)
        if (reward > 0) {
            // Podczas konstruktora Game dźwięk jeszcze nie jest gotowy.
            if (width <= 1 || sound == null) pendingLoginReward = reward
            else celebrateReward(width * 0.5f, height * 0.62f, reward, Sound.MEDALS)
            showNotice(L["ms_streak"] + " " + m.state.loginStreak + "/7 · " + L.f("ms_login_paid", L.plural("medals_n", reward.toLong())), Icons.FLAME)
        }
    }

    /** Ostatnio narysowany baner powiadomienia (pusty, gdy brak); do testów zasłaniania. */
    internal val noticeBanner: RectF get() = achievementBanner
    /** Do testów: bieżący i oczekujące teksty banerów. */
    internal fun noticeTexts(): List<String> =
        (if (achievementSeconds > 0f) listOf(achievementMessage) else emptyList()) + noticeQueue
    /** Czy jest widoczny albo oczekuje jakikolwiek baner (bez alokacji). */
    internal fun hasNotices(): Boolean = achievementSeconds > 0f || noticeQueue.isNotEmpty()
    /** Do testów: usuwa bieżący i oczekujące banery. */
    internal fun clearNotices() {
        noticeQueue.clear(); noticeIcons.clear(); achievementSeconds = 0f
    }

    /** Górna krawędź banera: poniżej Wstecz (insetT + 58 dp) i wstęgi tytułu z jej końcówkami. */
    internal fun noticeTopBelowTitle(): Float {
        val d = ui.dp
        val narrow = width - insetL - insetR < 560 * d
        val ribbonBottom = insetT + (if (narrow) 82f else 32f) * d + 20f * d * ui.textScale * 1.05f * 0.95f
        return maxOf(insetT + 72 * d, ribbonBottom + 8 * d)
    }

    private fun drawAchievementNotice(c: Canvas, dt: Float) {
        achievementSeconds -= dt
        // Powiadomienie nie może zasłaniać nawigacji ani okien modalnych.
        if (screen?.blocksNotices == true) { achievementBanner.setEmpty(); return }
        if (achievementSeconds <= 0f && noticeQueue.isNotEmpty() && screen?.blocksNotices != true) {
            achievementMessage = noticeQueue.removeFirst()
            noticeIcon = noticeIcons.removeFirst()
            achievementSeconds = 2.9f
        }
        if (achievementSeconds <= 0f) { achievementBanner.setEmpty(); return }
        val pw = minOf(470 * ui.dp, width - insetL - insetR - 20 * ui.dp)
        val px = (width + insetL - insetR - pw) / 2f
        val menu = screen as? MenuScreen
        // W menu baner mieści się między logo a przyciskami, także na 720p.
        // Tura 9: poza menu baner leży pod wstęgą tytułu (Chrome.title) i przyciskiem Wstecz,
        // także na wąskim ekranie, gdzie wstęga schodzi do drugiego rzędu (środek 82 dp).
        val py = menu?.noticeTop ?: noticeTopBelowTitle()
        val ph = if (menu == null) 50 * ui.dp
            else minOf(50 * ui.dp, (menu.noticeBottom - py).coerceAtLeast(24 * ui.dp))
        achievementBanner.set(px, py, px + pw, py + ph)
        ui.card(c, achievementBanner, Color.rgb(42, 74, 64), 12f, Ui.GOLD)
        val iconSize = minOf(26 * ui.dp, ph - 10 * ui.dp)
        Icons.draw(c, noticeIcon, px + 23 * ui.dp, py + ph / 2f, iconSize, dp = ui.dp)
        val avail = pw - 61 * ui.dp
        val size = ui.fit(achievementMessage, 14f, avail)
        if (size < 11.5f / ui.textScale && ph >= 44 * ui.dp && splitNotice(avail)) {
            // Tura 9: długi komunikat (np. dlaczego Play Games jest niedostępne) w dwóch
            // wierszach zamiast jednego drobnym pismem.
            val s2 = minOf(ui.fit(noticeLine1, 13f, avail), ui.fit(noticeLine2, 13f, avail))
            val lh = s2 * ui.dp * ui.textScale * 1.12f
            noticeTextSize = s2
            ui.textO(c, noticeLine1, px + 45 * ui.dp, py + ph / 2f - lh * 0.5f + 4.5f * ui.dp, s2, Ui.TEXT)
            ui.textO(c, noticeLine2, px + 45 * ui.dp, py + ph / 2f + lh * 0.5f + 4.5f * ui.dp, s2, Ui.TEXT)
        } else {
            noticeTextSize = size
            ui.textO(c, achievementMessage, px + 45 * ui.dp, py + ph / 2f + 5 * ui.dp, size, Ui.TEXT)
        }
    }

    private var noticeSplitFor: String? = null
    private var noticeSplitW = 0f
    private var noticeLine1 = ""
    private var noticeLine2 = ""
    /** Rozmiar (sp) ostatnio narysowanego tekstu banera; do testów czytelności. */
    internal var noticeTextSize = 0f
        private set

    /** Dzieli bieżący komunikat na dwa wiersze możliwie równej szerokości (raz na komunikat). */
    private fun splitNotice(avail: Float): Boolean {
        val m = achievementMessage
        if (m !== noticeSplitFor || avail != noticeSplitW) {
            noticeSplitFor = m; noticeSplitW = avail
            var best = -1
            var bestW = Float.MAX_VALUE
            var i = m.indexOf(' ')
            while (i > 0) {
                val w = maxOf(ui.measure(m.substring(0, i), 13f, true), ui.measure(m.substring(i + 1), 13f, true))
                if (w < bestW) { bestW = w; best = i }
                i = m.indexOf(' ', i + 1)
            }
            if (best < 0) { noticeLine1 = ""; noticeLine2 = "" }
            else { noticeLine1 = m.substring(0, best); noticeLine2 = m.substring(best + 1) }
        }
        return noticeLine1.isNotEmpty()
    }

    init {
        applyLanguage()
        applyAccessibility()
        if (profile.name.isBlank()) profile.name = L["default_name"]
        setQuality(effectiveQualityLevel())
        checkDailyLogin()
    }

    fun applyLanguage() {
        L.setCode(profile.lang)
    }

    /** Na wątku gry; przeładowuje napisy aktywnego ekranu i ekranów na stosie. */
    fun onLanguageChanged() {
        applyLanguage()
        screen?.onLanguageChanged()
        for (s in stack) s.onLanguageChanged()
    }

    /** Przelicza wygląd po zmianie profilu bez restartowania gry. */
    fun applyAccessibility() {
        Palette.select(profile.palette)
        ui.textScale = profile.textScale / 100f
        screen?.layout()
        for (s in stack) s.layout()
    }

    /** R2-03: TalkBack (eksploracja dotykiem) włączony w systemie; ustawia [applySystemAccessibility]. */
    var touchExplorationActive = false
    /** Ostatni odczyt ustawień telefonu; null, dopóki aktywność go nie przekazała (testy). */
    internal var systemA11y: SystemA11y? = null
        private set

    /**
     * R2-03 (wątek gry): rozmiar tekstu i ograniczenia ruchu według telefonu, o ile gracz nie
     * zmienił ich ręcznie. Profil zapisujemy tylko przy faktycznej zmianie.
     */
    internal fun applySystemAccessibility(s: SystemA11y) {
        systemA11y = s
        val talkBackChanged = touchExplorationActive != s.touchExploration
        touchExplorationActive = s.touchExploration
        // R10-04 L2: TalkBack przełączony w trakcie bitwy zmienia tempo od razu (bez ręcznego wyboru).
        if (talkBackChanged) {
            (screen as? BattleScreen)?.onSystemAccessibilityChanged()
            for (i in stack.indices) (stack[i] as? BattleScreen)?.onSystemAccessibilityChanged()
        }
        var changed = false
        if (!profile.textScaleManual) {
            val target = SystemAccessibility.textScaleFor(s.fontScale)
            if (profile.textScale != target) { profile.textScale = target; changed = true }
        }
        if (!profile.motionManual && s.animationsOff && (!profile.reduceShake || !profile.reduceFlash)) {
            profile.reduceShake = true
            profile.reduceFlash = true
            profile.motionAuto = true
            changed = true
        } else if (!profile.motionManual && !s.animationsOff && profile.motionAuto) {
            // R10-04 L3: animacje telefonu wróciły, więc wraca wybór gracza sprzed automatu.
            profile.reduceShake = false
            profile.reduceFlash = false
            profile.motionAuto = false
            changed = true
        }
        if (changed) {
            applyAccessibility()
            profile.saveAsync()
        }
    }

    /**
     * R2-03: tempo ×½ jest w cyklu tempa bitwy, a bitwa startuje w ×½. Ręczny wybór gracza
     * wygrywa; bez niego opcja albo włączony TalkBack (decyzje właściciela).
     */
    fun slowSpeedEnabled(): Boolean =
        if (profile.slowSpeedManual) profile.slowSpeedOption
        else profile.slowSpeedOption || touchExplorationActive

    fun effectiveQualityLevel() = GameModes.effectiveQuality(
        if (profile.quality == 0) detectedQuality else profile.quality, gameMode)

    /**
     * R10-04: tryb gry Androida ([GameModes]); ustawia MainActivity przy wznowieniu (API 31+).
     * Tryb baterii ogranicza efektywne FPS i jakość bez zmiany wyboru gracza w profilu.
     */
    var gameMode = GameModes.UNSUPPORTED
        private set

    /** Wątek gry. Zmiana trybu przelicza jakość i limit klatek; ten sam tryb nic nie robi. */
    fun applyGameMode(mode: Int) {
        if (mode == gameMode) return
        gameMode = mode
        setQuality(effectiveQualityLevel())
        applyFrameRate()
    }

    fun effectiveFpsCap(): Int = GameModes.effectiveFps(profile.fpsCap, gameMode)

    fun setQuality(level: Int) {
        if (profile.quality != 0) governor.reset()
        quality = Quality(
            level,
            if (profile.quality == 0) governor.reduction else 0,
            if (profile.quality == 0) governor.gpuReduction else 0
        )
        ui.fill.isAntiAlias = quality.antialias
        host.applyRenderScale(quality.renderScale(viewW, viewH))
    }

    fun applyFrameRate() = host.applyFrameRate(effectiveFpsCap())

    /** Density może się zmienić bez restartu Activity (Fold, ustawienia wyświetlacza). */
    fun updateDensity(value: Float) {
        if (!value.isFinite() || value <= 0f || density == value) return
        density = value
        if (width > 1 && height > 1 && viewW > 1 && viewH > 1) {
            resize(width, height, viewW, viewH)
        }
    }

    /** Wywoływane z wątku gry, gdy zmienił się bufor powierzchni. */
    fun resize(bufW: Int, bufH: Int, vW: Int, vH: Int) {
        finishScreenTransition()
        width = bufW; height = bufH; viewW = vW; viewH = vH
        renderScale = bufW.toFloat() / vW.coerceAtLeast(1)
        scaleInsets()
        // Rozmiar UI w dp, ale na bardzo dużych ekranach (rozłożony Fold, tablet)
        // lekko powiększamy, żeby interfejs nie wyglądał na zagubiony.
        val minDp = minOf(vW, vH) / density
        val boost = if (minDp > 560f) (minDp / 560f).coerceAtMost(1.35f) else 1f
        ui.dp = density * renderScale * boost * (if (minDp < 340f) minDp / 340f else 1f)
        if (screen == null) {
            val version = BuildConfig.VERSION_NAME
            // Dzień 2: po aktualizacji nie przerywamy startu oknem; menu pokazuje przycisk
            // „Co nowego” (MenuScreen.whatsNew), dopóki gracz nie otworzy historii zmian.
            if (!WhatsNewCatalog.shouldShow(profile.hasExistingSave, profile.whatsNewSeenVersion, version)) {
                // Pierwsza instalacja i wersje bez notatek: nic do przeczytania.
                if (profile.whatsNewSeenVersion != version) {
                    profile.whatsNewSeenVersion = version
                    profile.save()
                }
            }
            setScreen(MenuScreen(this))
        }
        screen?.layout()
        for (s in stack) s.layout()
    }

    /** Ostatnie wcięcia w pikselach widoku: przeliczamy je przy każdej zmianie skali bufora. */
    private val rawInsets = IntArray(4)

    fun setInsets(l: Int, t: Int, r: Int, b: Int) {
        finishScreenTransition()
        rawInsets[0] = l; rawInsets[1] = t; rawInsets[2] = r; rawInsets[3] = b
        scaleInsets()
        screen?.layout()
    }

    private fun scaleInsets() {
        val s = renderScale
        insetL = rawInsets[0] * s; insetT = rawInsets[1] * s; insetR = rawInsets[2] * s; insetB = rawInsets[3] * s
    }

    /**
     * Nowy rozmiar widoku (UI thread przez [post]). Skalę bufora liczymy od razu z nowych
     * wymiarów; wcześniej setQuality używało starych viewW/viewH i po złożeniu/rozłożeniu
     * Folda nadpisywało poprawną skalę wartością policzoną dla poprzedniego ekranu.
     */
    fun viewResized(vW: Int, vH: Int) {
        viewW = vW; viewH = vH
        setQuality(quality.level)
    }

    fun setScreen(s: Screen) {
        val previous = screen
        previous?.onPause()
        previous?.resetTouch()
        stack.clear()
        screen = s
        CrashReports.screenChanged(s.crashName)
        if (s is MenuScreen) host.onMenuShown()
        syncMusicScreen(s)
        s.layout()
        startScreenTransition(previous, s, 1f)
        refreshBackHandling()
        notePlaytestScreen(s)
    }

    fun push(s: Screen) {
        val previous = screen
        previous?.resetTouch()
        previous?.let { stack.add(it) }
        screen = s
        CrashReports.screenChanged(s.crashName)
        if (s is MenuScreen) host.onMenuShown()
        syncMusicScreen(s)
        s.layout()
        startScreenTransition(previous, s, 1f)
        refreshBackHandling()
        notePlaytestScreen(s)
    }

    /** Zmienia zakładkę Rekordów bez dokładania ekranów do stosu Wstecz. */
    fun replace(s: Screen) {
        val previous = screen
        previous?.onPause()
        previous?.resetTouch()
        screen = s
        CrashReports.screenChanged(s.crashName)
        if (s is MenuScreen) host.onMenuShown()
        syncMusicScreen(s)
        s.layout()
        startScreenTransition(previous, s, 1f)
        refreshBackHandling()
        notePlaytestScreen(s)
    }

    fun pop() {
        if (stack.isEmpty()) { setScreen(MenuScreen(this)); return }
        val previous = screen
        val restored = stack.removeAt(stack.size - 1)
        previous?.resetTouch()
        restored.resetTouch()
        screen = restored
        CrashReports.screenChanged(restored.crashName)
        if (restored is MenuScreen) host.onMenuShown()
        syncMusicScreen(restored)
        restored.layout()
        startScreenTransition(previous, restored, -1f)
        refreshBackHandling()
    }

    val current get() = screen

    fun back() {
        val s = screen ?: return
        if (s.onBack()) { refreshBackHandling(); return }
        if (stack.isNotEmpty()) pop() else if (s !is MenuScreen) setScreen(MenuScreen(this)) else host.exit()
        refreshBackHandling()
    }

    fun haptic(kind: HapticKind = HapticKind.TAP) {
        if (profile.vibrationLevel > 0) host.haptic(kind)
    }

    /** Zgodność ze starszymi ekranami: sygnał dotknięcia zamiast czasu trwania. */
    fun haptic(ms: Long) { haptic(HapticKind.TAP) }

    // ---------------------------------------------------------------- wątki

    fun pushTouch(action: Int, x: Float, y: Float, pointer: Int) {
        synchronized(inputLock) {
            val e = if (inputPool.isEmpty()) TouchEvent() else inputPool.removeAt(inputPool.size - 1)
            e.action = action; e.x = x; e.y = y; e.pointer = pointer
            // Scal kolejne MOVE tego samego palca: mniej pracy przy 240 Hz próbkowaniu dotyku.
            if (action == TouchEvent.MOVE && input.isNotEmpty()) {
                val last = input[input.size - 1]
                if (last.action == TouchEvent.MOVE && last.pointer == pointer) {
                    last.x = x; last.y = y; inputPool.add(e); return
                }
            }
            input.add(e)
        }
    }

    fun post(r: Runnable) = synchronized(inputLock) { tasks.add(r) }

    private val taskBuf = ArrayList<Runnable>()

    fun frame(c: Canvas, dtRaw: Float) {
        val dt = dtRaw.coerceIn(0f, 0.1f)
        synchronized(inputLock) {
            frameEvents.addAll(input); input.clear()
            taskBuf.addAll(tasks); tasks.clear()
        }
        for (t in taskBuf) t.run()
        taskBuf.clear()
        if (pendingLoginReward > 0 && width > 1 && sound != null) {
            celebrateReward(width * 0.5f, height * 0.62f, pendingLoginReward, Sound.MEDALS)
            pendingLoginReward = 0
        }
        val s0 = screen
        for (e in frameEvents) {
            if (e.action == TouchEvent.CANCEL + 10) back()
            // Podczas przejścia odrzucamy tylko DOWN/MOVE; UP/CANCEL muszą dojść, by nie
            // zostawić wciśniętego przycisku ani zajętego palca.
            else if (!transitionActive || e.action == TouchEvent.UP || e.action == TouchEvent.CANCEL)
                screen?.handle(e)
        }
        synchronized(inputLock) { inputPool.addAll(frameEvents) }
        frameEvents.clear()
        if (s0 !== screen) screen?.layout()

        simulatedSteps = 0
        discardedSteps = 0
        val updateStart = System.nanoTime()
        screen?.advanceTouch(dt)
        screen?.update(dt)
        refreshBackHandling()
        val renderStart = System.nanoTime()
        drawScreen(c, dt)
        drawAchievementNotice(c, dt)
        rewardFeedback.draw(c, this, dt)
        val renderEnd = System.nanoTime()
        frameMetrics.add(renderStart - updateStart, renderEnd - renderStart, simulatedSteps, discardedSteps)
        monitor(dt)
        playtestFrame(dt)
        if (profile.showFps) {
            refreshOverlay(dt)
            fpsPaint.textSize = 10 * ui.dp
            c.drawText(overlayTop, insetL + 8 * ui.dp, height - insetB - 22 * ui.dp, fpsPaint)
            c.drawText(overlayBottom, insetL + 8 * ui.dp, height - insetB - 8 * ui.dp, fpsPaint)
        }
    }

    fun requestBack() = pushTouch(TouchEvent.CANCEL + 10, 0f, 0f, -1)

    private fun monitor(dt: Float) {
        val ms = dt * 1000f
        frameAvg += (ms - frameAvg) * 0.05f
        fps = 1000f / frameAvg.coerceAtLeast(1f)
        if (governor.record(frameMetrics.avgCpuMs, dt, profile.quality == 0,
                effectiveFpsCap(), quality.maxReductionSteps,
                frameMetrics.avgFrameIntervalMs, frameMetrics.refreshHz,
                QualityGovernor.thermalSignal(thermalStatus, thermalHeadroom))) {
            setQuality(effectiveQualityLevel())
        }
    }

    fun now() = SystemClock.uptimeMillis()

    // ---------------------------------------------------------------- R2-04: dziennik testu

    /** Bit ekranu w [PlaytestLog.screensVisited]; -1 dla ekranów spoza listy. */
    internal fun playtestScreenBit(s: Screen): Int = when (s) {
        is CodexScreen -> PlaytestLog.SCREEN_CODEX
        is ResearchScreen -> PlaytestLog.SCREEN_RESEARCH
        is MissionsScreen -> PlaytestLog.SCREEN_MISSIONS
        is ReplayListScreen -> PlaytestLog.SCREEN_REPLAYS
        is AccessibilityScreen -> PlaytestLog.SCREEN_ACCESSIBILITY
        is SkirmishSetupScreen -> PlaytestLog.SCREEN_SKIRMISH
        is BattleScreen -> when (s.cfg.mode) {
            Mode.SKIRMISH -> PlaytestLog.SCREEN_SKIRMISH
            Mode.ENDLESS -> if (s.cfg.weekly >= 0L) PlaytestLog.SCREEN_WEEKLY else PlaytestLog.SCREEN_ENDLESS
            Mode.DAILY -> PlaytestLog.SCREEN_DAILY
            Mode.CAMPAIGN -> -1
        }
        else -> -1
    }

    private fun notePlaytestScreen(s: Screen) {
        val log = playtest ?: return
        log.screenVisited(playtestScreenBit(s))
        log.checkTutorial(profile.tutorialDone, profile.playSeconds)
    }

    /**
     * Co 30 s bitwy: p95 klatki, redukcja jakości, zgubione kroki i status termiczny.
     * Tylko pola prymitywne, bez alokacji i bez I/O (zapis przy onPause).
     */
    internal fun playtestFrame(dt: Float) {
        val log = playtest ?: return
        if (screen !is BattleScreen) return
        playtestBattleSeconds += dt
        if (playtestBattleSeconds < 30f) return
        playtestBattleSeconds -= 30f
        log.perfSample(frameMetrics.p95Ms(), quality.reduction, frameMetrics.totalDroppedSteps, thermalStatus)
    }

    /** Start aplikacji (MainActivity po utworzeniu gry). */
    fun playtestStarted(nowMs: Long) {
        playtest?.startSession(nowMs, frameMetrics.totalDroppedSteps)
    }

    /** Powrót z tła (wątek gry). */
    fun playtestResumed(nowMs: Long) {
        playtest?.resumed(nowMs, frameMetrics.totalDroppedSteps)
    }

    /** Przejście w tło: migawka i zapis w tle (wątek gry albo UI, gdy pętla stoi). */
    fun playtestPaused(nowMs: Long) {
        val log = playtest ?: return
        log.paused(nowMs)
        log.checkTutorial(profile.tutorialDone, profile.playSeconds)
        log.saveAsync()
    }

    /**
     * R2-05: system prosi o pamięć (`onTrimMemory`). Od Androida 14 przychodzą praktycznie
     * tylko UI_HIDDEN (20) i BACKGROUND (40); starsze RUNNING_* (5–15) traktujemy jak 20.
     * Ekrany zwalniają odtwarzalne bufory (Picture, bitmapy błysku), które odbudują same;
     * stan bitwy i SoundPool zostają. Od 40 muzyka zwalnia odtwarzacze, które nie grają.
     * Tylko wątek gry (albo UI, gdy pętla stoi).
     */
    fun trimMemory(level: Int) {
        if (level <= 0) return
        val normalized = if (level >= TRIM_BACKGROUND) TRIM_BACKGROUND else TRIM_UI_HIDDEN
        screen?.onTrimMemory(normalized)
        for (s in stack) s.onTrimMemory(normalized)
        val from = transitionFrom
        if (from != null && from !== screen && from !in stack) from.onTrimMemory(normalized)
        if (normalized >= TRIM_BACKGROUND) music?.trimMemory()
    }

    companion object {
        /** `ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN` i `TRIM_MEMORY_BACKGROUND`. */
        const val TRIM_UI_HIDDEN = 20
        const val TRIM_BACKGROUND = 40
    }
}
