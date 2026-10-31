package com.centurywar.engine

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import com.centurywar.game.RestoredBattle
import com.centurywar.game.SnapshotStore
import com.centurywar.meta.AchievementDef
import com.centurywar.meta.OnlineAchievements
import com.centurywar.meta.OnlineLeaderboards
import com.centurywar.meta.Profile
import com.centurywar.meta.ReplayStore
import com.centurywar.meta.BattleHistory
import com.centurywar.meta.Missions
import com.centurywar.screens.BattleScreen
import com.centurywar.screens.MenuScreen
import com.centurywar.screens.ReplayScreen

/** Rzeczy, których wątek gry potrzebuje od Activity (wątek UI). */
interface Host {
    fun vibrate(ms: Long)
    fun requestText(title: String, current: String, onResult: (String) -> Unit)
    fun applyRenderScale(scale: Float)
    fun applyFrameRate(fps: Int)
    fun exit()
    fun onWantsBackChanged(wantsBack: Boolean) {}
    fun onBattleWon() {}
    fun onNotificationSettingsChanged() {}
    fun requestNotifyHour(current: Int, onResult: (Int) -> Unit) {}
}

/**
 * Stan całej aplikacji po stronie wątku gry: aktywny ekran, profil, jakość grafiki,
 * kolejka dotyku. Zmiany rozmiaru (np. rozłożenie Folda) przychodzą przez [resize]
 * i każdy ekran przelicza wtedy swój układ, bez restartu Activity.
 */
class Game(val host: Host, val profile: Profile, private val detectedQuality: Int) {
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
    var quality = Quality(Quality.HIGH)
        private set
    var online: OnlineLeaderboards = OnlineLeaderboards.Local
    var onlineAchievements: OnlineAchievements = OnlineAchievements.NoOp
    /** Null w testach; na urządzeniu ustawia MainActivity. */
    var sound: Sound? = null
    /** Muzyka działa niezależnie od efektów SoundPool. */
    var music: Music? = null

    fun applyMusicVolume() { music?.setVolume(profile.musicVolume) }

    private fun syncMusicScreen(s: Screen) {
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
    /** Bitwa przerwana przez zabicie aplikacji: MenuScreen pyta, czy ją wznowić. */
    var resumeOffer: RestoredBattle? = null

    fun sfx(id: Int, vol: Float = 1f, gap: Long = 45L, pitch: Float = 1f) {
        if (profile.sound) sound?.play(id, vol, gap, pitch)
    }

    private var screen: Screen? = null
    private val stack = ArrayList<Screen>()
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

    // monitor wydajności
    private var frameAvg = 16f
    private var slowTime = 0f
    var fps = 60f
        private set
    private val fpsPaint = Paint().apply { color = Color.YELLOW }
    /** Gotowe teksty powiadomień (budowane przy zdarzeniu, nie w renderze) i ich ikony. */
    private val noticeQueue = ArrayDeque<String>()
    private val noticeIcons = ArrayDeque<Int>()
    private var noticeIcon = Icons.MEDAL
    private val achievementBanner = RectF()
    private var achievementMessage = ""
    private var achievementSeconds = 0f

    /** Kolejka powiadomień; tekst jest przygotowany przy odblokowaniu, nie w renderze. */
    fun showAchievements(unlocked: List<AchievementDef>) {
        for (a in unlocked) showNotice(L["achievement_unlocked"] + ": " + L["ach_" + a.id] + " (+" + a.medalReward + ")", Icons.CROWN)
    }

    fun showNotice(message: String, icon: Int = Icons.MEDAL) {
        if (noticeQueue.size >= 8) return // zalew powiadomień: resztę widać na ekranie osiągnięć
        noticeQueue.addLast(message); noticeIcons.addLast(icon)
    }

    /**
     * Seria logowań: odświeża misje i przyznaje dzienną nagrodę (raz na dzień UTC). Wołane przy
     * starcie i po powrocie aplikacji z tła (MainActivity.onResume przez [post]), bo proces
     * potrafi przeżyć w tle północ i nagroda kolejnego dnia nie zostałaby przyznana.
     */
    fun checkDailyLogin(nowMillis: Long = System.currentTimeMillis()) {
        val m = Missions(profile)
        m.refresh(nowMillis)
        val reward = m.claimLogin(nowMillis)
        if (reward > 0) showNotice(L["ms_streak"] + " " + m.state.loginStreak + "/7: " + L.f("ms_login_paid", L.plural("medals_n", reward.toLong())), Icons.CALENDAR)
    }

    private fun drawAchievementNotice(c: Canvas, dt: Float) {
        achievementSeconds -= dt
        if (achievementSeconds <= 0f && noticeQueue.isNotEmpty()) {
            achievementMessage = noticeQueue.removeFirst()
            noticeIcon = noticeIcons.removeFirst()
            achievementSeconds = 2.9f
        }
        if (achievementSeconds <= 0f) return
        val pw = minOf(470 * ui.dp, width - insetL - insetR - 20 * ui.dp)
        val px = (width + insetL - insetR - pw) / 2f
        val py = insetT + 72 * ui.dp
        achievementBanner.set(px, py, px + pw, py + 50 * ui.dp)
        ui.card(c, achievementBanner, Color.rgb(42, 74, 64), 12f, Ui.GOLD)
        Icons.draw(c, noticeIcon, px + 23 * ui.dp, py + 25 * ui.dp, 26 * ui.dp, dp = ui.dp)
        val size = ui.fit(achievementMessage, 14f, pw - 61 * ui.dp)
        ui.textO(c, achievementMessage, px + 45 * ui.dp, py + 30 * ui.dp, size, Ui.TEXT)
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

    fun effectiveQualityLevel() = if (profile.quality == 0) detectedQuality else profile.quality

    fun setQuality(level: Int) {
        quality = Quality(level)
        ui.fill.isAntiAlias = quality.antialias
        host.applyRenderScale(quality.renderScale(viewW, viewH))
    }

    fun applyFrameRate() = host.applyFrameRate(profile.fpsCap)

    /** Wywoływane z wątku gry, gdy zmienił się bufor powierzchni. */
    fun resize(bufW: Int, bufH: Int, vW: Int, vH: Int) {
        width = bufW; height = bufH; viewW = vW; viewH = vH
        renderScale = bufW.toFloat() / vW.coerceAtLeast(1)
        scaleInsets()
        // Rozmiar UI w dp, ale na bardzo dużych ekranach (rozłożony Fold, tablet)
        // lekko powiększamy, żeby interfejs nie wyglądał na zagubiony.
        val minDp = minOf(vW, vH) / density
        val boost = if (minDp > 560f) (minDp / 560f).coerceAtMost(1.35f) else 1f
        ui.dp = density * renderScale * boost * (if (minDp < 340f) minDp / 340f else 1f)
        if (screen == null) setScreen(MenuScreen(this))
        screen?.layout()
        for (s in stack) s.layout()
    }

    /** Ostatnie wcięcia w pikselach widoku: przeliczamy je przy każdej zmianie skali bufora. */
    private val rawInsets = IntArray(4)

    fun setInsets(l: Int, t: Int, r: Int, b: Int) {
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
        screen?.onPause()
        stack.clear()
        screen = s
        syncMusicScreen(s)
        s.layout()
        refreshBackHandling()
    }

    fun push(s: Screen) {
        screen?.let { stack.add(it) }
        screen = s
        syncMusicScreen(s)
        s.layout()
        refreshBackHandling()
    }

    fun pop() {
        if (stack.isEmpty()) { setScreen(MenuScreen(this)); return }
        screen = stack.removeAt(stack.size - 1)
        screen?.let { syncMusicScreen(it); it.layout() }
        refreshBackHandling()
    }

    val current get() = screen

    fun back() {
        val s = screen ?: return
        if (s.onBack()) { refreshBackHandling(); return }
        if (stack.isNotEmpty()) pop() else if (s !is MenuScreen) setScreen(MenuScreen(this)) else host.exit()
        refreshBackHandling()
    }

    fun haptic(ms: Long = 12) { if (profile.vibration) host.vibrate(ms) }

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
        val s0 = screen
        for (e in frameEvents) {
            if (e.action == TouchEvent.CANCEL + 10) { back() } else screen?.handle(e)
        }
        synchronized(inputLock) { inputPool.addAll(frameEvents) }
        frameEvents.clear()
        if (s0 !== screen) screen?.layout()

        screen?.update(dt)
        refreshBackHandling()
        screen?.render(c)
        drawAchievementNotice(c, dt)

        monitor(dt)
        if (profile.showFps) {
            fpsPaint.textSize = 12 * ui.dp
            c.drawText("${fps.toInt()} fps  q${quality.level}  ${(renderScale * 100).toInt()}%", insetL + 8 * ui.dp, height - 8 * ui.dp, fpsPaint)
        }
    }

    fun requestBack() = pushTouch(TouchEvent.CANCEL + 10, 0f, 0f, -1)

    private fun monitor(dt: Float) {
        val ms = dt * 1000f
        frameAvg += (ms - frameAvg) * 0.05f
        fps = 1000f / frameAvg.coerceAtLeast(1f)
        // Auto-jakość: jeśli przez 4 s średnio < 45 FPS, schodzimy o poziom niżej.
        if (profile.quality == 0 && quality.level > Quality.LOW && profile.fpsCap != 30) {
            if (frameAvg > 22f) slowTime += dt else slowTime = 0f
            if (slowTime > 4f) {
                slowTime = 0f
                setQuality(quality.level - 1)
            }
        }
    }

    fun now() = SystemClock.uptimeMillis()
}
