package com.centurywar.engine

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.SystemClock
import com.centurywar.game.RestoredBattle
import com.centurywar.game.SnapshotStore
import com.centurywar.meta.OnlineLeaderboards
import com.centurywar.meta.Profile
import com.centurywar.screens.MenuScreen

/** Rzeczy, których wątek gry potrzebuje od Activity (wątek UI). */
interface Host {
    fun vibrate(ms: Long)
    fun requestText(title: String, current: String, onResult: (String) -> Unit)
    fun applyRenderScale(scale: Float)
    fun applyFrameRate(fps: Int)
    fun exit()
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
    /** Null w testach; na urządzeniu ustawia MainActivity. */
    var sound: Sound? = null
    /** Zrzut przerwanej bitwy (filesDir/battle.snap). Null w testach. */
    var snapshots: SnapshotStore? = null
    /** Bitwa przerwana przez zabicie aplikacji: MenuScreen pyta, czy ją wznowić. */
    var resumeOffer: RestoredBattle? = null

    fun sfx(id: Int, vol: Float = 1f, gap: Long = 45L, pitch: Float = 1f) {
        if (profile.sound) sound?.play(id, vol, gap, pitch)
    }

    private var screen: Screen? = null
    private val stack = ArrayList<Screen>()
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

    init {
        applyLanguage()
        setQuality(effectiveQualityLevel())
    }

    fun applyLanguage() {
        L.polish = when (profile.lang) {
            "pl" -> true
            "en" -> false
            else -> java.util.Locale.getDefault().language == "pl"
        }
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
        // Rozmiar UI w dp, ale na bardzo dużych ekranach (rozłożony Fold, tablet)
        // lekko powiększamy, żeby interfejs nie wyglądał na zagubiony.
        val minDp = minOf(vW, vH) / density
        val boost = if (minDp > 560f) (minDp / 560f).coerceAtMost(1.35f) else 1f
        ui.dp = density * renderScale * boost * (if (minDp < 340f) minDp / 340f else 1f)
        if (screen == null) setScreen(MenuScreen(this))
        screen?.layout()
        for (s in stack) s.layout()
    }

    fun setInsets(l: Int, t: Int, r: Int, b: Int) {
        val s = renderScale
        insetL = l * s; insetT = t * s; insetR = r * s; insetB = b * s
        screen?.layout()
    }

    fun setScreen(s: Screen) {
        screen?.onPause()
        stack.clear()
        screen = s
        s.layout()
    }

    fun push(s: Screen) {
        screen?.let { stack.add(it) }
        screen = s
        s.layout()
    }

    fun pop() {
        if (stack.isEmpty()) { setScreen(MenuScreen(this)); return }
        screen = stack.removeAt(stack.size - 1)
        screen?.layout()
    }

    val current get() = screen

    fun back() {
        val s = screen ?: return
        if (s.onBack()) return
        if (stack.isNotEmpty()) pop() else if (s !is MenuScreen) setScreen(MenuScreen(this)) else host.exit()
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
        screen?.render(c)

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
