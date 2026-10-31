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

    val ui get() = g.ui
    val dp get() = g.ui.dp
    val w get() = g.width.toFloat()
    val h get() = g.height.toFloat()

    /** Wywoływane po każdej zmianie rozmiaru (obrót, rozłożenie Folda, multi-window). */
    open fun layout() {}
    open fun update(dt: Float) {}
    abstract fun render(c: Canvas)
    open fun onClick(b: Button) {}
    /** true = obsłużone; false = gra cofa do poprzedniego ekranu / wychodzi. */
    open fun onBack(): Boolean = false
    open fun onDrag(dx: Float, dy: Float) {}
    open fun onTap(x: Float, y: Float) {}
    open fun onPause() {}

    fun btn(id: Int, label: String = "", sub: String = ""): Button =
        Button(id).also { it.label = label; it.sub = sub; buttons.add(it) }

    fun handle(e: TouchEvent) {
        when (e.action) {
            TouchEvent.DOWN -> {
                if (activePointer != -1) return
                activePointer = e.pointer
                downX = e.x; downY = e.y; lastX = e.x; lastY = e.y; dragging = false
                for (b in buttons) b.pressed = b.contains(e.x, e.y) && b.enabled
            }
            TouchEvent.MOVE -> {
                if (e.pointer != activePointer) return
                if (!dragging && (kotlin.math.abs(e.x - downX) > 10 * dp || kotlin.math.abs(e.y - downY) > 10 * dp)) {
                    dragging = buttons.none { it.pressed } || isDragScreen()
                    if (dragging) for (b in buttons) b.pressed = false
                }
                if (dragging) onDrag(e.x - lastX, e.y - lastY)
                for (b in buttons) if (b.pressed && !b.contains(e.x, e.y)) b.pressed = false
                lastX = e.x; lastY = e.y
            }
            TouchEvent.UP, TouchEvent.CANCEL -> {
                if (e.pointer != activePointer) return
                activePointer = -1
                if (e.action == TouchEvent.UP && !dragging) {
                    var hit = false
                    for (b in buttons.toList()) {
                        if (b.pressed && b.contains(e.x, e.y) && b.enabled) {
                            hit = true
                            b.pressed = false
                            g.haptic()
                            onClick(b)
                            break
                        }
                    }
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
