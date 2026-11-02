package com.centurywar.engine

import android.content.Context
import android.graphics.Rect
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeProvider
import com.centurywar.screens.AccessibilityScreen
import com.centurywar.screens.BattleScreen
import com.centurywar.screens.HintSettingsScreen
import com.centurywar.screens.MenuScreen
import com.centurywar.screens.SettingsScreen

/**
 * Wirtualne kontrolki dla SurfaceView. Migawka powstaje tylko na wątku gry;
 * wątek UI czyta wyłącznie niezmienne dane, a kliknięcie wraca do kolejki gry.
 */
internal class GameAccessibility(private val view: GameView, private val game: Game) {
    private companion object {
        const val NONE = Int.MIN_VALUE
        const val FALLBACK_ID = 0
        const val BUTTONS = 1
        const val PAUSE = 2
        const val BATTLE = 3
        const val OTHER = 4
        const val ACCESS_POINTER = 164
    }

    private data class Node(
        val id: Int,
        val button: Button?,
        val description: String,
        val rawLabel: String,
        val rawSub: String,
        val rawTooltip: String,
        val checkable: Boolean,
        val checked: Boolean,
        val selected: Boolean,
        val enabled: Boolean,
        val bounds: Rect,
    )

    private data class Tree(
        val screen: Screen,
        val kind: Int,
        val title: String,
        val language: Int,
        val viewW: Int,
        val viewH: Int,
        val bufferW: Int,
        val bufferH: Int,
        val nodes: List<Node>,
    )

    private val manager = view.context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
    @Volatile private var tree: Tree? = null
    private var focused = NONE
    private var hovered = NONE
    internal var revision = 0
        private set

    val provider: AccessibilityNodeProvider = object : AccessibilityNodeProvider() {
        override fun createAccessibilityNodeInfo(virtualViewId: Int): AccessibilityNodeInfo? {
            val snapshot = tree
            if (virtualViewId == View.NO_ID) {
                return AccessibilityNodeInfo.obtain(view).apply {
                    packageName = view.context.packageName
                    className = "android.view.SurfaceView"
                    contentDescription = snapshot?.title ?: L["a164_loading"]
                    isFocusable = false
                    snapshot?.nodes?.forEach { addChild(view, it.id) }
                }
            }
            val node = snapshot?.nodes?.firstOrNull { it.id == virtualViewId } ?: return null
            return AccessibilityNodeInfo.obtain(view, virtualViewId).apply {
                packageName = view.context.packageName
                className = if (node.button == null) "android.view.View" else "android.widget.Button"
                contentDescription = node.description
                isEnabled = node.enabled
                isClickable = node.button != null && node.enabled
                isFocusable = true
                isVisibleToUser = true
                isCheckable = node.checkable
                isChecked = node.checked
                isSelected = node.selected
                isAccessibilityFocused = focused == virtualViewId
                setParent(view)
                val bounds = Rect(node.bounds)
                setBoundsInParent(bounds)
                val location = IntArray(2)
                view.getLocationOnScreen(location)
                bounds.offset(location[0], location[1])
                setBoundsInScreen(bounds)
                if (isClickable) addAction(AccessibilityNodeInfo.ACTION_CLICK)
                if (focused == virtualViewId) addAction(AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS)
                else addAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)
            }
        }

        override fun findFocus(focus: Int): AccessibilityNodeInfo? =
            if (focus == AccessibilityNodeInfo.FOCUS_ACCESSIBILITY && focused != NONE)
                createAccessibilityNodeInfo(focused) else null

        override fun performAction(virtualViewId: Int, action: Int, arguments: Bundle?): Boolean {
            val snapshot = tree ?: return false
            val node = snapshot.nodes.firstOrNull { it.id == virtualViewId } ?: return false
            return when (action) {
                AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS -> {
                    if (focused != virtualViewId) {
                        if (focused != NONE) sendNodeEvent(focused, AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED)
                        focused = virtualViewId
                        sendNodeEvent(virtualViewId, AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED)
                    }
                    true
                }
                AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS -> {
                    if (focused != virtualViewId) false
                    else {
                        focused = NONE
                        sendNodeEvent(virtualViewId, AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED)
                        true
                    }
                }
                AccessibilityNodeInfo.ACTION_CLICK -> {
                    val button = node.button ?: return false
                    if (!node.enabled) return false
                    // Nie kieruj spóźnionego kliknięcia do nowego ekranu.
                    game.post(Runnable {
                        val screen = snapshot.screen
                        if (game.current !== screen || !button.enabled || !button.visible) return@Runnable
                        if (snapshot.kind == BATTLE) {
                            if (screen !is BattleScreen || screen.paused || screen.finished) return@Runnable
                        } else if (!eligible(screen, button)) return@Runnable
                        // Identyczna ścieżka DOWN/UP jak po dotknięciu przycisku.
                        val tap = TouchEvent()
                        tap.pointer = ACCESS_POINTER
                        tap.x = button.r.centerX() + button.hitOffsetX
                        tap.y = button.r.centerY() + button.hitOffsetY
                        tap.action = TouchEvent.DOWN
                        screen.handle(tap)
                        tap.action = TouchEvent.UP
                        screen.handle(tap)
                    })
                    true
                }
                else -> false
            }
        }
    }

    /** W normalnej pętli brak odczytu przycisków i brak alokacji bez usługi dostępności. */
    fun refreshIfEnabled() {
        if (manager?.isEnabled != true) return
        refresh()
    }

    /** Odczyt wyłącznie z wątku gry; również punkt wejścia testów bez aktywnego TalkBack. */
    internal fun refresh() {
        val screen = game.current ?: return
        val vw = view.width
        val vh = view.height
        if (vw <= 0 || vh <= 0 || game.width <= 1 || game.height <= 1) return
        val kind = when (screen) {
            is BattleScreen -> if (screen.paused && !screen.finished) PAUSE else BATTLE
            is MenuScreen, is SettingsScreen, is AccessibilityScreen, is HintSettingsScreen -> BUTTONS
            else -> OTHER
        }
        val title = when (screen) {
            is MenuScreen -> L["a164_menu"]
            is SettingsScreen -> L["settings"]
            is AccessibilityScreen -> L["accessibility"]
            is HintSettingsScreen -> L["hint_settings"]
            is BattleScreen -> if (kind == PAUSE) L["paused"] else L["a164_battle"]
            else -> L["a164_other"]
        }
        val before = tree
        if (before != null && matches(before, screen, kind, title, vw, vh)) return

        val nodes = ArrayList<Node>()
        if (kind == BUTTONS || kind == PAUSE) {
            val sx = vw.toFloat() / game.width
            val sy = vh.toFloat() / game.height
            for (i in screen.buttons.indices) {
                val b = screen.buttons[i]
                if (!eligible(screen, b)) continue
                nodes.add(Node(
                    i + 1, b, screen.accessibilityLabel(b),
                    b.label, b.sub, b.tooltipTitle,
                    screen.accessibilityCheckable(b), screen.accessibilityChecked(b),
                    b.selected, b.enabled, boundsFor(b, sx, sy, vw, vh)
                ))
            }
        } else {
            val pause = if (kind == BATTLE && screen is BattleScreen && !screen.finished) screen.pauseBtn else null
            nodes.add(Node(
                FALLBACK_ID, pause,
                if (kind == BATTLE) {
                    if (pause != null) L["a164_battle_pause"] else L["a164_battle"]
                } else title,
                "", "", "", false, false, false, pause != null || kind == OTHER,
                Rect(0, 0, vw, vh)
            ))
        }
        val next = Tree(screen, kind, title, L.lang, vw, vh, game.width, game.height, nodes)
        tree = next
        revision++
        if (focused != NONE && nodes.none { it.id == focused }) focused = NONE
        if (hovered != NONE && nodes.none { it.id == hovered }) hovered = NONE
        // Powiadomienie wysyłamy z UI; wiele szybkich zmian scala kontrola tożsamości.
        view.post {
            if (tree === next && view.isAttachedToWindow)
                view.sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)
        }
    }

    private fun eligible(s: Screen, b: Button): Boolean {
        if (!b.visible || b.r.width() <= 0f || b.r.height() <= 0f) return false
        return when (s) {
            is BattleScreen -> s.paused && !s.finished &&
                (s.pauseMenu.contains(b) || b === s.pauseVibrationBtn || b === s.pauseSpeedBtn)
            is MenuScreen -> !s.hasOpenModal || b.id == 20 || b.id == 21
            is SettingsScreen, is AccessibilityScreen, is HintSettingsScreen -> true
            else -> false
        }
    }

    private fun boundsFor(b: Button, sx: Float, sy: Float, vw: Int, vh: Int) = Rect(
        (b.r.left * sx).toInt().coerceIn(0, vw),
        (b.r.top * sy).toInt().coerceIn(0, vh),
        (b.r.right * sx).toInt().coerceIn(0, vw),
        (b.r.bottom * sy).toInt().coerceIn(0, vh)
    )

    /** Porównanie bez kolekcji i bez Rect: większość klatek tylko sprawdza stan. */
    private fun matches(t: Tree, s: Screen, kind: Int, title: String, vw: Int, vh: Int): Boolean {
        if (t.screen !== s || t.kind != kind || t.title != title || t.language != L.lang ||
            t.viewW != vw || t.viewH != vh || t.bufferW != game.width || t.bufferH != game.height) return false
        if (kind == OTHER || kind == BATTLE) {
            val pause = if (kind == BATTLE && s is BattleScreen && !s.finished) s.pauseBtn else null
            return t.nodes.size == 1 && t.nodes[0].button === pause
        }
        val sx = vw.toFloat() / game.width
        val sy = vh.toFloat() / game.height
        var count = 0
        for (i in s.buttons.indices) {
            val b = s.buttons[i]
            if (!eligible(s, b)) continue
            if (count >= t.nodes.size) return false
            val n = t.nodes[count++]
            if (n.id != i + 1 || n.button !== b ||
                n.rawLabel != b.label || n.rawSub != b.sub || n.rawTooltip != b.tooltipTitle ||
                n.checkable != s.accessibilityCheckable(b) ||
                n.checked != s.accessibilityChecked(b) ||
                n.selected != b.selected || n.enabled != b.enabled ||
                n.bounds.left != (b.r.left * sx).toInt().coerceIn(0, vw) ||
                n.bounds.top != (b.r.top * sy).toInt().coerceIn(0, vh) ||
                n.bounds.right != (b.r.right * sx).toInt().coerceIn(0, vw) ||
                n.bounds.bottom != (b.r.bottom * sy).toInt().coerceIn(0, vh)) return false
        }
        return count == t.nodes.size
    }

    fun onHover(e: MotionEvent): Boolean {
        if (manager?.isTouchExplorationEnabled != true) return false
        when (e.actionMasked) {
            MotionEvent.ACTION_HOVER_ENTER, MotionEvent.ACTION_HOVER_MOVE -> {
                val id = tree?.nodes?.firstOrNull { it.bounds.contains(e.x.toInt(), e.y.toInt()) }?.id ?: NONE
                if (id != hovered) {
                    if (hovered != NONE) sendNodeEvent(hovered, AccessibilityEvent.TYPE_VIEW_HOVER_EXIT)
                    hovered = id
                    if (id != NONE) sendNodeEvent(id, AccessibilityEvent.TYPE_VIEW_HOVER_ENTER)
                }
                return true
            }
            MotionEvent.ACTION_HOVER_EXIT -> {
                if (hovered != NONE) sendNodeEvent(hovered, AccessibilityEvent.TYPE_VIEW_HOVER_EXIT)
                hovered = NONE
                return true
            }
        }
        return false
    }

    private fun sendNodeEvent(id: Int, type: Int) {
        val node = tree?.nodes?.firstOrNull { it.id == id } ?: return
        val parent = view.parent ?: return
        val event = AccessibilityEvent.obtain(type)
        event.packageName = view.context.packageName
        event.className = if (node.button == null) "android.view.View" else "android.widget.Button"
        event.contentDescription = node.description
        event.setSource(view, id)
        parent.requestSendAccessibilityEvent(view, event)
    }
}
