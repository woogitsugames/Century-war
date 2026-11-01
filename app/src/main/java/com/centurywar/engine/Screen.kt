package com.centurywar.engine

import android.graphics.Canvas

/** Bazowa klasa ekranu. Wszystkie metody wywołuje wątek gry, nigdy wątek UI. */
abstract class Screen(val g: Game) {
    val buttons = ArrayList<Button>()
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var dragging = false
    private var activePointer = -1
    private var touchCandidate: Button? = null
    private var touchElapsed = 0f
    private var touchMoved = false
    private var heldButton: Button? = null
    private val heldTooltip = TooltipBubble()

    /**
     * Stała nazwa do dziennika awarii. R8 zaciemnia nazwy klas, więc nie czytamy javaClass;
     * ekrany gry nadpisują ją literałem.
     */
    open val crashName: String get() = "OtherScreen"
    /** Gdy true, powiadomienia (seria logowań, osiągnięcia) czekają w kolejce na inny ekran lub koniec akcji. */
    open val blocksNotices: Boolean get() = false

    val ui get() = g.ui
    val dp get() = g.ui.dp
    val w get() = g.width.toFloat()
    val h get() = g.height.toFloat()

    /** Wywoływane po każdej zmianie rozmiaru (obrót, rozłożenie Folda, multi-window). */
    open fun layout() {}
    /** Odświeża także teksty buforowane przez ekran po zmianie języka. */
    open fun onLanguageChanged() { layout() }
    open fun update(dt: Float) {}
    abstract fun render(c: Canvas)
    open fun onClick(b: Button) {}

    /** Nadpisz dla dynamicznych opisów; domyślnie odczytuje opis przycisku. */
    open fun onLongPress(b: Button): Boolean {
        if (b.tooltipTitle.isEmpty()) return false
        heldTooltip.set(b.tooltipTitle, b.tooltipBody)
        return true
    }

    /** Zegar gestu z czasu klatek, bez alokacji i bez zegara systemowego. */
    fun advanceTouch(dt: Float) {
        val candidate = touchCandidate ?: return
        if (activePointer < 0 || touchMoved || heldButton != null ||
            !candidate.visible || !candidate.contains(lastX, lastY)) return
        touchElapsed += dt
        if (touchElapsed >= 0.45f) {
            // Rozpoznanie tylko raz w ramach gestu, także gdy ekran odrzuci przytrzymanie.
            touchMoved = true
            if (onLongPress(candidate)) heldButton = candidate
        }
    }

    /** Rysuj na końcu renderowania, ponad HUD i panelami. */
    protected fun drawHeldTooltip(c: Canvas) {
        val b = heldButton ?: return
        ui.tooltip(c, b.r, heldTooltip, g.insetL, g.insetT, w - g.insetR, h - g.insetB)
    }

    protected fun clearHeldTooltip() { heldButton = null }
    protected val isTooltipHeld get() = heldButton != null
    /** true = obsłużone; false = gra cofa do poprzedniego ekranu / wychodzi. */
    open fun onBack(): Boolean = false
    open fun onDrag(dx: Float, dy: Float) {}
    open fun onTap(x: Float, y: Float) {}
    /** true oznacza, że ekran przejął surowe zdarzenie dotyku. */
    open fun onTouch(e: TouchEvent): Boolean = false
    open fun onPause() {}

    /**
     * Czy tap na widocznym, ale wyłączonym przycisku pomocniczym ma zostać pochłonięty
     * zamiast trafić do [onTap].
     */
    protected open fun swallowsInactiveTap(b: Button): Boolean = true

    /**
     * Czyści stan gestu (wciśnięcia, palec, przeciąganie). Gra wywołuje to przy zmianie
     * ekranu, bo zdarzenia UP/CANCEL mogły trafić do innego ekranu.
     */
    open fun resetTouch() {
        activePointer = -1
        dragging = false
        touchCandidate = null
        touchElapsed = 0f
        touchMoved = false
        heldButton = null
        for (b in buttons) b.pressed = false
    }

    fun btn(id: Int, label: String = "", sub: String = ""): Button =
        Button(id).also { it.label = label; it.sub = sub; buttons.add(it) }

    fun handle(e: TouchEvent) {
        if (onTouch(e)) return
        when (e.action) {
            TouchEvent.DOWN -> {
                if (activePointer != -1) return
                activePointer = e.pointer
                downX = e.x; downY = e.y; lastX = e.x; lastY = e.y; dragging = false
                touchCandidate = null; touchElapsed = 0f; touchMoved = false; heldButton = null
                for (b in buttons) {
                    val inside = b.contains(e.x, e.y)
                    if (touchCandidate == null && inside) touchCandidate = b
                    b.pressed = inside && b.enabled
                }
            }
            TouchEvent.MOVE -> {
                if (e.pointer != activePointer) return
                if (kotlin.math.abs(e.x - downX) > 10 * dp ||
                    kotlin.math.abs(e.y - downY) > 10 * dp) {
                    touchMoved = true
                    if (!dragging) dragging = buttons.none { it.pressed } || isDragScreen()
                    if (dragging) for (b in buttons) b.pressed = false
                }
                if (dragging) onDrag(e.x - lastX, e.y - lastY)
                for (b in buttons) if (b.pressed && !b.contains(e.x, e.y)) b.pressed = false
                lastX = e.x; lastY = e.y
            }
            TouchEvent.UP, TouchEvent.CANCEL -> {
                // CANCEL anuluje cały gest (niezależnie od id palca), inaczej activePointer mógłby
                // utknąć i ekran przestałby reagować na dotyk.
                if (activePointer == -1 || (e.action == TouchEvent.UP && e.pointer != activePointer)) return
                activePointer = -1
                val consumed = heldButton != null
                val inactiveTarget = touchCandidate
                heldButton = null
                touchCandidate = null
                if (e.action == TouchEvent.UP && !dragging && !consumed) {
                    var hit = false
                    for (i in 0 until buttons.size) {
                        val b = buttons[i]
                        if (b.pressed && b.contains(e.x, e.y) && b.enabled) {
                            hit = true
                            b.pressed = false
                            g.haptic()
                            g.sfx(Sound.CLICK, 0.6f)
                            onClick(b)
                            break
                        }
                    }
                    // Wyłączony przycisk (np. informacja o kolejce) nie jest tapem pola bitwy.
                    if (!hit && inactiveTarget != null && !inactiveTarget.enabled &&
                        inactiveTarget.contains(e.x, e.y) && swallowsInactiveTap(inactiveTarget)) hit = true
                    if (!hit) onTap(e.x, e.y)
                }
                for (b in buttons) b.pressed = false
                dragging = false
            }
        }
    }

    /** Ekrany przewijane pozwalają zacząć przeciąganie także na przycisku. */
    open fun isDragScreen() = false
}

class TouchEvent {
    companion object {
        const val DOWN = 0
        const val MOVE = 1
        const val UP = 2
        const val CANCEL = 3
    }
    var action = 0
    var x = 0f
    var y = 0f
    var pointer = 0
}
