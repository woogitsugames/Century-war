package com.centurywar.engine

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.os.Build
import android.view.MotionEvent
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView

/**
 * Powierzchnia gry z własnym wątkiem renderowania.
 *
 *  - `lockHardwareCanvas()` rysuje przez GPU (Canvas akcelerowany sprzętowo).
 *  - `unlockCanvasAndPost` blokuje do vsync, więc pętla naturalnie chodzi w tempie
 *    ekranu (60/90/120 Hz). Opcjonalny limit 30/60 FPS oszczędza baterię.
 *  - `setFixedSize` zmniejsza bufor na słabszych telefonach; skalowanie do
 *    rozdzielczości ekranu robi kompozytor sprzętowo.
 */
@SuppressLint("ViewConstructor")
class GameView(context: Context, private val game: Game) : SurfaceView(context), SurfaceHolder.Callback, Runnable {

    @Volatile private var running = false
    @Volatile private var hasSurface = false
    @Volatile private var bufW = 0
    @Volatile private var bufH = 0
    @Volatile private var sizeChanged = false
    @Volatile var frameCapFps = 0
    @Volatile private var thread: Thread? = null
    private var scale = 1f

    init {
        holder.addCallback(this)
        isFocusable = true
        keepScreenOn = true
    }

    fun setRenderScale(s: Float) {
        if (width == 0 || height == 0) { scale = s; return }
        scale = s
        if (s >= 0.999f) holder.setSizeFromLayout()
        else holder.setFixedSize((width * s).toInt().coerceAtLeast(16), (height * s).toInt().coerceAtLeast(16))
    }

    fun setFrameRate(fps: Int) {
        frameCapFps = fps
        if (Build.VERSION.SDK_INT >= 30 && hasSurface) {
            try {
                holder.surface.setFrameRate(fps.toFloat(), Surface.FRAME_RATE_COMPATIBILITY_DEFAULT)
            } catch (_: Exception) { }
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // Nowy rozmiar widoku (obrót, Fold): przelicz skalę bufora.
        game.post { game.viewResized(w, h) }
        setRenderScale(game.quality.renderScale(w, h))
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        hasSurface = true
        setFrameRate(frameCapFps)
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) {
        bufW = w; bufH = h; sizeChanged = true
        startThread()
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        hasSurface = false
        stopThread()
    }

    fun onResume() { if (hasSurface) startThread() }
    fun onPause() = stopThread()

    private fun startThread() {
        if (running) return
        running = true
        // Najpierw przypisanie, potem start: pętla porównuje się z polem [thread].
        val t = Thread(this, "game-loop").apply { priority = Thread.MAX_PRIORITY }
        thread = t
        t.start()
    }

    private fun stopThread() {
        running = false
        val t = thread ?: return
        thread = null
        try { t.join(1500) } catch (_: InterruptedException) { }
    }

    override fun run() {
        var last = System.nanoTime()
        val me = Thread.currentThread()
        // Sprawdzamy też tożsamość wątku: jeśli join() w stopThread przekroczył limit, a zaraz
        // potem startThread ustawił running = true, stary wątek nie może dalej rysować równolegle.
        while (running && thread === me) {
            if (!hasSurface) break
            if (sizeChanged) {
                sizeChanged = false
                game.resize(bufW, bufH, width.coerceAtLeast(1), height.coerceAtLeast(1))
            }
            val frameStart = System.nanoTime()
            val dt = (frameStart - last) / 1_000_000_000f
            last = frameStart
            var c: Canvas? = null
            try {
                c = try { holder.lockHardwareCanvas() } catch (_: Exception) { holder.lockCanvas() }
                if (c != null) game.frame(c, dt)
            } catch (e: Exception) {
                android.util.Log.e("CenturyWar", "frame error", e)
            } finally {
                if (c != null) try { holder.unlockCanvasAndPost(c) } catch (_: Exception) { }
            }
            val cap = frameCapFps
            if (cap > 0) {
                val target = 1_000_000_000L / cap
                val spent = System.nanoTime() - frameStart
                if (spent < target) {
                    try { Thread.sleep((target - spent) / 1_000_000L) } catch (_: InterruptedException) { }
                }
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        val sx = if (width > 0) bufW.toFloat() / width else 1f
        val sy = if (height > 0) bufH.toFloat() / height else 1f
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = e.actionIndex
                game.pushTouch(TouchEvent.DOWN, e.getX(i) * sx, e.getY(i) * sy, e.getPointerId(i))
            }
            MotionEvent.ACTION_MOVE -> for (i in 0 until e.pointerCount) {
                game.pushTouch(TouchEvent.MOVE, e.getX(i) * sx, e.getY(i) * sy, e.getPointerId(i))
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val i = e.actionIndex
                game.pushTouch(TouchEvent.UP, e.getX(i) * sx, e.getY(i) * sy, e.getPointerId(i))
            }
            MotionEvent.ACTION_CANCEL -> game.pushTouch(TouchEvent.CANCEL, 0f, 0f, e.getPointerId(0))
        }
        return true
    }
}
