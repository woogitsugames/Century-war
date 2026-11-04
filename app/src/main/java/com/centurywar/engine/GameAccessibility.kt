package com.centurywar.engine

import android.content.Context
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeProvider
import com.centurywar.BuildConfig
import com.centurywar.R
import com.centurywar.game.Defs
import com.centurywar.screens.AboutScreen
import com.centurywar.screens.AccessibilityScreen
import com.centurywar.screens.BattleAccessibility
import com.centurywar.screens.BattleScreen
import com.centurywar.screens.HintSettingsScreen
import com.centurywar.screens.MenuScreen
import com.centurywar.screens.SettingsScreen

/**
 * Wirtualne kontrolki dla SurfaceView. Migawka powstaje tylko na wątku gry;
 * wątek UI czyta wyłącznie niezmienne dane, a kliknięcie wraca do kolejki gry.
 *
 * Dzień 5: w bitwie każdy przycisk HUD jest osobnym węzłem o stałym ID
 * ([BTN_BASE] + [Button.id]), a stan bitwy czytają węzły informacyjne. Strukturę
 * sprawdzamy co klatkę bez alokacji; teksty z liczbami przebudowujemy najwyżej co
 * [TEXT_INTERVAL_MS] i tylko wtedy, gdy faktycznie się zmieniły.
 */
internal class GameAccessibility(private val view: GameView, private val game: Game) {
    internal companion object {
        const val NONE = Int.MIN_VALUE
        const val FALLBACK_ID = 0
        const val BUTTONS = 1
        const val PAUSE = 2
        const val OTHER = 4
        const val RESULTS = 5
        const val ERA_INTRO = 6
        const val CHOICE = 7
        const val TURRETS = 8
        const val UPGRADE = 9
        const val BATTLE_HUD = 10
        /** R11-01: ekran z [Screen.accessibilityTitle]: przyciski, informacje, przewijanie. */
        const val SCREEN = 11
        const val ACCESS_POINTER = 164

        /** Węzeł przycisku bitwy = BTN_BASE + Button.id (stałe, niezależne od kolejności przycisków). */
        const val BTN_BASE = 10_000
        const val INFO_GOLD = 20_001
        const val INFO_XP = 20_002
        const val INFO_BASES = 20_003
        const val INFO_WAVE = 20_004
        const val INFO_ARMY = 20_005
        const val INFO_TUTORIAL = 20_006
        const val INFO_UPGRADE = 20_007
        const val INFO_RESULT = 20_008
        const val INFO_ERA = 20_009
        /** Żywy region z komunikatami bitwy. */
        const val STATUS = 20_100
        /** R11-01: informacja [i] ekranu = INFO_SCREEN_BASE + i (stałe id). */
        const val INFO_SCREEN_BASE = 30_000

        const val TEXT_INTERVAL_MS = 1000L
        const val STATUS_INTERVAL_MS = 3000L

        private const val ACT_UPGRADE = 1
        private const val ACT_BARRAGE = 2
        private const val ACT_DISMISS = 3

        private const val PRIO_NORMAL = 1
        private const val PRIO_HIGH = 2

        private val NO_ACTIONS = arrayOf<Action>()
        /** Kolejne punkty próbne w obszarze dotyku (środek najpierw, jak dotychczas). */
        private val TAP_FX = floatArrayOf(0.5f, 0.3f, 0.5f, 0.15f, 0.85f, 0.15f, 0.85f, 0.5f)
        private val TAP_FY = floatArrayOf(0.5f, 0.75f, 0.85f, 0.85f, 0.85f, 0.15f, 0.15f, 0.15f)
    }

    private class Action(val id: Int, val label: String, val kind: Int, val arg: Int)

    private class Node(
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
        val state: String = "",
        val key: Int = 0,
        val info: Boolean = false,
        val live: Boolean = false,
        val actions: Array<Action> = NO_ACTIONS,
        /** Akcja wykonywana przez ACTION_CLICK zamiast dotknięcia przycisku. */
        val click: Action? = null,
    ) {
        val clickable get() = !info && (button != null || click != null)
    }

    private class Tree(
        val screen: Screen,
        val kind: Int,
        val title: String,
        val language: Int,
        val viewW: Int,
        val viewH: Int,
        val bufferW: Int,
        val bufferH: Int,
        val nodes: List<Node>,
        val paneTitle: String? = null,
        val status: String = "",
        val builtAt: Long = 0L,
        val scrollForward: Boolean = false,
        val scrollBackward: Boolean = false,
    )

    private val manager = view.context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
    @Volatile private var tree: Tree? = null
    @Volatile private var focused = NONE
    @Volatile private var hovered = NONE
    internal var revision = 0
        private set
    /** Zegar przebudów (testy podstawiają własny). */
    internal var clock: () -> Long = { SystemClock.uptimeMillis() }

    val provider: AccessibilityNodeProvider = object : AccessibilityNodeProvider() {
        override fun createAccessibilityNodeInfo(virtualViewId: Int): AccessibilityNodeInfo? {
            val snapshot = tree
            if (virtualViewId == View.NO_ID) {
                return AccessibilityNodeInfo.obtain(view).apply {
                    packageName = view.context.packageName
                    className = "android.view.SurfaceView"
                    contentDescription = snapshot?.title ?: L["a164_loading"]
                    isFocusable = false
                    if (Build.VERSION.SDK_INT >= 28 && snapshot?.paneTitle != null) paneTitle = snapshot.paneTitle
                    // R11-01: listy i mapa kampanii przewijają się akcjami czytnika.
                    if (snapshot != null && (snapshot.scrollForward || snapshot.scrollBackward)) {
                        isScrollable = true
                        if (snapshot.scrollForward) addAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                        if (snapshot.scrollBackward) addAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
                    }
                    snapshot?.nodes?.forEach { addChild(view, it.id) }
                }
            }
            val node = snapshot?.nodes?.firstOrNull { it.id == virtualViewId } ?: return null
            return AccessibilityNodeInfo.obtain(view, virtualViewId).apply {
                packageName = view.context.packageName
                className = classNameOf(node)
                if (node.state.isEmpty()) contentDescription = node.description
                else if (Build.VERSION.SDK_INT >= 30) {
                    contentDescription = node.description
                    stateDescription = node.state
                } else contentDescription = node.description + ", " + node.state
                isEnabled = node.enabled
                isClickable = node.clickable && node.enabled
                isFocusable = true
                isVisibleToUser = true
                isCheckable = node.checkable
                isChecked = node.checked
                isSelected = node.selected
                isAccessibilityFocused = focused == virtualViewId
                if (node.live) liveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
                setParent(view)
                val bounds = Rect(node.bounds)
                setBoundsInParent(bounds)
                val location = IntArray(2)
                view.getLocationOnScreen(location)
                bounds.offset(location[0], location[1])
                setBoundsInScreen(bounds)
                if (isClickable) {
                    val click = node.click
                    if (click != null) addAction(AccessibilityNodeInfo.AccessibilityAction(
                        AccessibilityNodeInfo.ACTION_CLICK, click.label))
                    else addAction(AccessibilityNodeInfo.ACTION_CLICK)
                }
                if (node.enabled) for (a in node.actions)
                    addAction(AccessibilityNodeInfo.AccessibilityAction(a.id, a.label))
                if (focused == virtualViewId) addAction(AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS)
                else addAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)
            }
        }

        override fun findFocus(focus: Int): AccessibilityNodeInfo? =
            if (focus == AccessibilityNodeInfo.FOCUS_ACCESSIBILITY && focused != NONE)
                createAccessibilityNodeInfo(focused) else null

        override fun performAction(virtualViewId: Int, action: Int, arguments: Bundle?): Boolean {
            val snapshot = tree ?: return false
            if (virtualViewId == View.NO_ID) return scroll(snapshot, action)
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
                    if (!node.clickable || !node.enabled) return false
                    val click = node.click
                    if (click != null) {
                        postAction(snapshot, node.button, click)
                        return true
                    }
                    val button = node.button ?: return false
                    // R10-04 L6: false tylko, gdy przycisk zniknął albo jest wyłączony.
                    if (!button.visible || !button.enabled) return false
                    // Nie kieruj spóźnionego kliknięcia do nowego ekranu.
                    game.post(Runnable {
                        if (!stillValid(snapshot, button)) return@Runnable
                        tap(snapshot.screen, button)
                    })
                    true
                }
                else -> {
                    if (!node.enabled) return false
                    val custom = node.actions.firstOrNull { it.id == action } ?: return false
                    postAction(snapshot, node.button, custom)
                    true
                }
            }
        }
    }

    /** R11-01: przewijanie z węzła głównego; samo przesunięcie odbywa się na wątku gry. */
    private fun scroll(snapshot: Tree, action: Int): Boolean {
        val direction = when (action) {
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD -> if (snapshot.scrollForward) 1 else return false
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD -> if (snapshot.scrollBackward) -1 else return false
            else -> return false
        }
        game.post(Runnable {
            val screen = snapshot.screen
            if (game.current === screen && screen.accessibilityCanScroll(direction)) screen.accessibilityScroll(direction)
        })
        return true
    }

    private fun classNameOf(node: Node): String = when {
        node.info -> "android.widget.TextView"
        node.clickable -> "android.widget.Button"
        else -> "android.view.View"
    }

    /** Akcje niestandardowe wracają do wątku gry i sprawdzają, czy stan się nie zmienił. */
    private fun postAction(snapshot: Tree, button: Button?, action: Action) {
        game.post(Runnable {
            if (!stillValid(snapshot, button)) return@Runnable
            val screen = snapshot.screen
            when (action.kind) {
                ACT_UPGRADE -> if (screen is BattleScreen) {
                    val arrow = screen.unitUpgrade[action.arg]
                    if (arrow.visible && arrow.enabled) tap(screen, arrow)
                }
                ACT_BARRAGE -> if (screen is BattleScreen) screen.accessibilityBarrage(action.arg)
                ACT_DISMISS -> {
                    // Zapowiedź epoki zamyka dowolne dotknięcie (ta sama ścieżka co palcem).
                    tapAt(screen, game.width * 0.5f, game.height * 0.5f)
                }
            }
        })
    }

    /** Wątek gry: migawka nadal opisuje bieżący ekran i ten sam rodzaj drzewa. */
    private fun stillValid(snapshot: Tree, button: Button?): Boolean {
        val screen = snapshot.screen
        if (game.current !== screen) return false
        if (button != null && (!button.visible || !button.enabled)) return false
        if (screen is BattleScreen) {
            if (battleKind(screen) != snapshot.kind) return false
            if (snapshot.kind == PAUSE) return button == null || eligible(screen, button)
            return button == null || battleEligible(screen, button)
        }
        // R11-01: informacja zamykająca okno (zapowiedź epoki) nie ma przycisku.
        if (button == null) return snapshot.kind == SCREEN
        return eligible(screen, button)
    }

    /**
     * Identyczna ścieżka DOWN/UP jak po dotknięciu przycisku. Punkt leży w obszarze dotyku
     * przycisku i nie jest przykryty przez wcześniejszy przycisk (np. strzałkę ulepszenia).
     * R10-04 L6: gdy cały obszar dotyku jest przykryty, ostatecznie [Screen.onClick], żeby
     * kliknięcie z czytnika nie przepadło. Zwraca false tylko dla niewidocznego lub wyłączonego.
     */
    internal fun tap(screen: Screen, button: Button): Boolean {
        if (!button.visible || !button.enabled) return false
        val l = button.r.left - button.hitSlop + button.hitOffsetX
        val t = button.r.top - button.hitSlop + button.hitOffsetY
        val w = button.r.width() + 2 * button.hitSlop
        val h = button.r.height() + 2 * button.hitSlop
        for (i in TAP_FX.indices) {
            val x = l + w * TAP_FX[i]
            val y = t + h * TAP_FY[i]
            if (firstHit(screen, x, y) === button) {
                tapAt(screen, x, y)
                return true
            }
        }
        if (BuildConfig.DEBUG) Log.d("CWAccess", "Przycisk ${button.id} przykryty, onClick bez dotyku")
        game.haptic()
        game.sfx(Sound.CLICK, 0.6f)
        screen.onClick(button)
        return true
    }

    private fun firstHit(screen: Screen, x: Float, y: Float): Button? {
        for (b in screen.buttons) if (b.enabled && b.contains(x, y)) return b
        return null
    }

    private fun tapAt(screen: Screen, x: Float, y: Float) {
        val tap = TouchEvent()
        tap.pointer = ACCESS_POINTER
        tap.x = x
        tap.y = y
        tap.action = TouchEvent.DOWN
        screen.handle(tap)
        tap.action = TouchEvent.UP
        screen.handle(tap)
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
        if (screen is BattleScreen) {
            refreshBattle(screen, vw, vh)
            return
        }
        val kind = when (screen) {
            // R2-04: O grze (z „Raportem dla testera”) też ma węzły przycisków.
            is MenuScreen, is SettingsScreen, is AccessibilityScreen, is HintSettingsScreen, is AboutScreen -> BUTTONS
            else -> if (screen.accessibilityTitle() != null) SCREEN else OTHER
        }
        val title = when (screen) {
            is MenuScreen -> L["a164_menu"]
            is SettingsScreen -> L["settings"]
            is AccessibilityScreen -> L["accessibility"]
            is HintSettingsScreen -> L["hint_settings"]
            is AboutScreen -> L["about"]
            else -> screen.accessibilityTitle() ?: L["a164_other"]
        }
        refreshList(screen, kind, title, vw, vh)
    }

    /** Menu, ustawienia i pauza: węzły o id = indeks przycisku + 1 (bez zmian od zlecenia 164). */
    private fun refreshList(screen: Screen, kind: Int, title: String, vw: Int, vh: Int) {
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
        } else if (kind == SCREEN) {
            buildScreen(screen, nodes, vw, vh)
        } else {
            nodes.add(Node(FALLBACK_ID, null, title, "", "", "", false, false, false, true, Rect(0, 0, vw, vh)))
        }
        val scrollable = kind == SCREEN
        publish(Tree(screen, kind, title, L.lang, vw, vh, game.width, game.height, nodes, builtAt = clock(),
            scrollForward = scrollable && screen.accessibilityCanScroll(1),
            scrollBackward = scrollable && screen.accessibilityCanScroll(-1)),
            before, structural = true, changed = null)
    }

    private val infoArea = RectF()

    /** Prostokąt informacji [i] w pikselach widoku (pusty obszar = cały widok); wynik w [infoArea]. */
    private fun infoRect(s: Screen, i: Int, sx: Float, sy: Float, vw: Int, vh: Int) {
        infoArea.setEmpty()
        s.accessibilityInfoBounds(i, infoArea)
        if (infoArea.width() <= 0f || infoArea.height() <= 0f) {
            infoArea.set(0f, 0f, vw.toFloat(), vh.toFloat())
            return
        }
        infoArea.set(
            (infoArea.left * sx).toInt().coerceIn(0, vw).toFloat(),
            (infoArea.top * sy).toInt().coerceIn(0, vh).toFloat(),
            (infoArea.right * sx).toInt().coerceIn(0, vw).toFloat(),
            (infoArea.bottom * sy).toInt().coerceIn(0, vh).toFloat())
    }

    /** R11-01: informacje i przyciski ekranu w kolejności czytania. */
    private fun buildScreen(s: Screen, nodes: ArrayList<Node>, vw: Int, vh: Int) {
        val sx = vw.toFloat() / game.width
        val sy = vh.toFloat() / game.height
        val ordered = ArrayList<Pair<Float, Node>>()
        for (i in 0 until s.accessibilityInfoCount()) {
            val text = s.accessibilityInfo(i)
            if (text.isEmpty()) continue
            infoRect(s, i, sx, sy, vw, vh)
            val bounds = Rect(infoArea.left.toInt(), infoArea.top.toInt(), infoArea.right.toInt(), infoArea.bottom.toInt())
            val node = if (s.accessibilityInfoDismiss(i))
                Node(INFO_SCREEN_BASE + i, null, text, "", "", "", false, false, false, true, bounds,
                    click = Action(AccessibilityNodeInfo.ACTION_CLICK, L["tut_next"], ACT_DISMISS, 0))
            else Node(INFO_SCREEN_BASE + i, null, text, "", "", "", false, false, false, true, bounds, info = true)
            ordered.add(s.accessibilityInfoOrder(i) to node)
        }
        for (i in s.buttons.indices) {
            val b = s.buttons[i]
            if (!eligible(s, b)) continue
            ordered.add(s.accessibilityOrder(b, i) to Node(
                i + 1, b, s.accessibilityLabel(b),
                b.label, b.sub, b.tooltipTitle,
                s.accessibilityCheckable(b), s.accessibilityChecked(b),
                b.selected, b.enabled, boundsFor(b, sx, sy, vw, vh), key = s.accessibilityKey(b)
            ))
        }
        ordered.sortBy { it.first }
        for (p in ordered) nodes.add(p.second)
    }

    private fun publish(next: Tree, before: Tree?, structural: Boolean, changed: IntArray?) {
        tree = next
        revision++
        if (before == null || before.screen !== next.screen || before.kind != next.kind) {
            focused = NONE
            hovered = NONE
        } else {
            if (focused != NONE && next.nodes.none { it.id == focused }) focused = NONE
            if (hovered != NONE && next.nodes.none { it.id == hovered }) hovered = NONE
        }
        val statusChanged = before != null && before.screen === next.screen && before.status != next.status
        // Powiadomienie wysyłamy z UI; wiele szybkich zmian scala kontrola tożsamości.
        view.post {
            if (tree !== next || !view.isAttachedToWindow) return@post
            if (structural) view.sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)
            else if (changed != null) for (id in changed) sendContentChanged(next, id)
            if (statusChanged && (structural || changed == null || STATUS !in changed))
                sendContentChanged(next, STATUS)
        }
    }

    private fun eligible(s: Screen, b: Button): Boolean {
        if (!b.visible || b.r.width() <= 0f || b.r.height() <= 0f) return false
        return when (s) {
            is BattleScreen -> s.paused && !s.finished &&
                (s.pauseMenu.contains(b) || b === s.pauseVibrationBtn || b === s.pauseSpeedBtn)
            is MenuScreen -> !s.hasOpenModal || b.id == 20 || b.id == 21
            is SettingsScreen, is AccessibilityScreen, is HintSettingsScreen, is AboutScreen -> true
            else -> s.accessibilityTitle() != null && s.accessibilityEligible(b)
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
        if (kind == OTHER) return t.nodes.size == 1 && t.nodes[0].button == null
        val sx = vw.toFloat() / game.width
        val sy = vh.toFloat() / game.height
        if (kind == SCREEN) return screenMatches(t, s, sx, sy, vw, vh)
        var count = 0
        for (i in s.buttons.indices) {
            val b = s.buttons[i]
            if (!eligible(s, b)) continue
            if (count >= t.nodes.size) return false
            val n = t.nodes[count++]
            if (n.id != i + 1 || !buttonMatches(n, s, b, sx, sy, vw, vh)) return false
        }
        return count == t.nodes.size
    }

    private fun buttonMatches(n: Node, s: Screen, b: Button, sx: Float, sy: Float, vw: Int, vh: Int): Boolean =
        n.button === b &&
            n.rawLabel == b.label && n.rawSub == b.sub && n.rawTooltip == b.tooltipTitle &&
            n.checkable == s.accessibilityCheckable(b) &&
            n.checked == s.accessibilityChecked(b) &&
            n.selected == b.selected && n.enabled == b.enabled &&
            n.bounds.left == (b.r.left * sx).toInt().coerceIn(0, vw) &&
            n.bounds.top == (b.r.top * sy).toInt().coerceIn(0, vh) &&
            n.bounds.right == (b.r.right * sx).toInt().coerceIn(0, vw) &&
            n.bounds.bottom == (b.r.bottom * sy).toInt().coerceIn(0, vh)

    /** R11-01: porównanie drzewa ekranu bez kolekcji (kolejność węzłów nie ma znaczenia). */
    private fun screenMatches(t: Tree, s: Screen, sx: Float, sy: Float, vw: Int, vh: Int): Boolean {
        if (t.scrollForward != s.accessibilityCanScroll(1) || t.scrollBackward != s.accessibilityCanScroll(-1)) return false
        val infoCount = s.accessibilityInfoCount()
        var infos = 0
        var buttonNodes = 0
        for (k in t.nodes.indices) {
            val n = t.nodes[k]
            val b = n.button
            if (b == null) {
                val i = n.id - INFO_SCREEN_BASE
                if (i < 0 || i >= infoCount || s.accessibilityInfo(i) != n.description) return false
                infoRect(s, i, sx, sy, vw, vh)
                if (n.bounds.left != infoArea.left.toInt() || n.bounds.top != infoArea.top.toInt() ||
                    n.bounds.right != infoArea.right.toInt() || n.bounds.bottom != infoArea.bottom.toInt()) return false
                infos++
            } else {
                val index = n.id - 1
                if (index < 0 || index >= s.buttons.size || s.buttons[index] !== b || !eligible(s, b) ||
                    n.key != s.accessibilityKey(b) || !buttonMatches(n, s, b, sx, sy, vw, vh)) return false
                buttonNodes++
            }
        }
        var expectedInfos = 0
        for (i in 0 until infoCount) if (s.accessibilityInfo(i).isNotEmpty()) expectedInfos++
        if (expectedInfos != infos) return false
        var expectedButtons = 0
        for (i in s.buttons.indices) if (eligible(s, s.buttons[i])) expectedButtons++
        return expectedButtons == buttonNodes
    }

    // ---------------------------------------------------------------- bitwa (Dzień 5)

    private fun battleKind(s: BattleScreen): Int = when {
        s.finished -> RESULTS
        s.paused -> PAUSE
        s.eraIntroActive -> ERA_INTRO
        s.battle.pendingVariant != null || s.battle.pendingChoice != null || s.battle.pendingCards != null -> CHOICE
        s.showUpgrade >= 0 -> UPGRADE
        s.showTurrets -> TURRETS
        else -> BATTLE_HUD
    }

    private fun battleTitle(s: BattleScreen, kind: Int): String = when (kind) {
        PAUSE -> L["paused"]
        CHOICE -> when {
            s.battle.pendingVariant != null -> L["choose_variant"]
            s.battle.pendingCards != null -> L["choose_card"]
            else -> L["choose_doctrine"]
        }
        else -> L["a178_battle"]
    }

    /** Samouczek: tylko przyciski, które bieżący krok przepuszcza. */
    private fun battleEligible(s: BattleScreen, b: Button): Boolean {
        if (!b.visible || b.r.width() <= 0f || b.r.height() <= 0f) return false
        val t = s.tut
        if (t != null && !t.finished && b !== t.skipBtn && b !== t.nextBtn && !t.allows(b)) return false
        return true
    }

    private var lastTextCheck = Long.MIN_VALUE

    private fun refreshBattle(s: BattleScreen, vw: Int, vh: Int) {
        val now = clock()
        trackEvents(s, now)
        val kind = battleKind(s)
        val title = battleTitle(s, kind)
        if (kind == PAUSE) {
            refreshList(s, PAUSE, title, vw, vh)
            return
        }
        val before = tree
        val same = before != null && before.screen === s && before.kind == kind && before.title == title &&
            before.language == L.lang && before.viewW == vw && before.viewH == vh &&
            before.bufferW == game.width && before.bufferH == game.height &&
            planMatches(before, s, kind, vw, vh)
        val statusSame = before != null && before.status == statusText
        if (same && statusSame && now - lastTextCheck < TEXT_INTERVAL_MS) return
        lastTextCheck = now
        val nodes = buildPlan(s, kind, vw, vh)
        val pane = if (kind == CHOICE) title else null
        val next = Tree(s, kind, title, L.lang, vw, vh, game.width, game.height, nodes, pane, statusText, now)
        if (same && before != null) {
            val changed = changedIds(before.nodes, nodes) ?: return
            publish(next, before, structural = false, changed = changed)
        } else publish(next, before, structural = true, changed = null)
    }

    /** Id węzłów z innym tekstem lub stanem; null = brak zmian. */
    private fun changedIds(old: List<Node>, new: List<Node>): IntArray? {
        var n = 0
        val ids = IntArray(new.size)
        for (i in new.indices) {
            val a = old[i]
            val b = new[i]
            var differs = a.description != b.description || a.state != b.state || a.actions.size != b.actions.size
            if (!differs) for (k in a.actions.indices) if (a.actions[k].label != b.actions[k].label) differs = true
            if (differs) ids[n++] = b.id
        }
        return if (n == 0) null else ids.copyOf(n)
    }

    // Plan drzewa przechodzi te same przyciski w trybie porównania (bez alokacji) albo budowy.
    private var building: ArrayList<Node>? = null
    private var cmpNodes: List<Node>? = null
    private var cmpIndex = 0
    private var cmpOk = true
    private var planSx = 1f
    private var planSy = 1f
    private var planVw = 0
    private var planVh = 0

    private fun planMatches(t: Tree, s: BattleScreen, kind: Int, vw: Int, vh: Int): Boolean {
        building = null
        cmpNodes = t.nodes
        cmpIndex = 0
        cmpOk = true
        plan(s, kind, vw, vh)
        val ok = cmpOk && cmpIndex == t.nodes.size
        cmpNodes = null
        return ok
    }

    private fun buildPlan(s: BattleScreen, kind: Int, vw: Int, vh: Int): List<Node> {
        val list = ArrayList<Node>(24)
        building = list
        cmpNodes = null
        plan(s, kind, vw, vh)
        building = null
        return list
    }

    private fun plan(s: BattleScreen, kind: Int, vw: Int, vh: Int) {
        planSx = vw.toFloat() / game.width
        planSy = vh.toFloat() / game.height
        planVw = vw
        planVh = vh
        val t = s.tut
        if (t != null && !t.finished && kind != RESULTS && kind != ERA_INTRO) {
            if (t.showing || t.skipConfirmationVisible) {
                emitInfo(s, INFO_TUTORIAL, if (t.panel.width() > 0f) t.panel else s.goldRect)
            }
            emitButton(s, t.skipBtn)
            emitButton(s, t.nextBtn)
        }
        if (kind != RESULTS && kind != ERA_INTRO) emitButton(s, s.hintOkBtn)
        when (kind) {
            RESULTS -> {
                emitInfo(s, INFO_RESULT, if (s.resultPanel.width() > 0f) s.resultPanel else s.fieldRect)
                emitButton(s, s.continueBtn)
                emitButton(s, s.restartEnd)
                emitButton(s, s.replayEnd)
                emitButton(s, s.resultStatsTab)
                emitButton(s, s.resultChartTab)
                emitButton(s, s.resultGoalBtn)
            }
            ERA_INTRO -> emitInfo(s, INFO_ERA, null)
            CHOICE -> {
                if (s.battle.pendingVariant != null) for (b in s.variantBtn) emitButton(s, b)
                else for (b in s.doctrineBtn) emitButton(s, b)
            }
            UPGRADE -> {
                emitInfo(s, INFO_UPGRADE, if (s.upgradePanel.width() > 0f) s.upgradePanel else s.goldRect)
                emitButton(s, s.upgradeBuy)
                emitButton(s, s.upgradeClose)
            }
            TURRETS -> {
                for (i in 0 until Defs.MAX_TURRET_SLOTS) {
                    emitButton(s, s.slotBuy[i])
                    emitButton(s, s.slotTier[i])
                    emitButton(s, s.slotSell[i])
                }
                emitButton(s, s.closeBtn)
                planHud(s)
            }
            else -> planHud(s)
        }
        emitInfo(s, STATUS, s.fieldRect)
    }

    private fun planHud(s: BattleScreen) {
        // R10-03: węzeł znaku „baza atakowana” istnieje tylko, gdy znak jest widoczny (≥ 2,5 s).
        emitButton(s, s.baseAlertBtn)
        emitInfo(s, INFO_GOLD, s.goldRect)
        emitInfo(s, INFO_XP, s.xpRect)
        emitInfo(s, INFO_BASES, s.mini)
        if (s.battle.endless) emitInfo(s, INFO_WAVE, s.mini)
        emitInfo(s, INFO_ARMY, s.armyRect)
        for (b in s.unitBtn) emitButton(s, b)
        for (b in s.actionBtns) emitButton(s, b)
        emitButton(s, s.evolveBtn)
        emitButton(s, s.evolveBanner.goBtn)
        emitButton(s, s.evolveBanner.waitBtn)
        emitButton(s, s.pauseBtn)
    }

    private fun emitButton(s: BattleScreen, b: Button) {
        if (!battleEligible(s, b)) return
        val id = BTN_BASE + b.id
        val l = ((b.r.left - b.hitSlop + b.hitOffsetX) * planSx).toInt().coerceIn(0, planVw)
        val t = ((b.r.top - b.hitSlop + b.hitOffsetY) * planSy).toInt().coerceIn(0, planVh)
        val r = ((b.r.right + b.hitSlop + b.hitOffsetX) * planSx).toInt().coerceIn(0, planVw)
        val bt = ((b.r.bottom + b.hitSlop + b.hitOffsetY) * planSy).toInt().coerceIn(0, planVh)
        val key = BattleAccessibility.key(s, b)
        val cmp = cmpNodes
        if (cmp != null) {
            if (!cmpOk) return
            if (cmpIndex >= cmp.size) { cmpOk = false; return }
            val n = cmp[cmpIndex++]
            if (n.id != id || n.button !== b || n.enabled != b.enabled || n.selected != b.selected ||
                n.key != key || n.bounds.left != l || n.bounds.top != t ||
                n.bounds.right != r || n.bounds.bottom != bt) cmpOk = false
            return
        }
        val list = building ?: return
        var actions = NO_ACTIONS
        var click: Action? = null
        val upgradeIndex = b.id - BattleScreen.UNIT0
        if (b.id in BattleScreen.UNIT0..BattleScreen.UNIT0 + 3 && s.unitUpgrade[upgradeIndex].visible) {
            actions = arrayOf(Action(R.id.cw_a11y_upgrade,
                L.f("a178_upgrade_action", BattleAccessibility.unitName(s, upgradeIndex)), ACT_UPGRADE, upgradeIndex))
        } else if (b === s.barrageBtn) {
            actions = arrayOf(
                Action(R.id.cw_a11y_barrage_cluster, L["a178_barrage_cluster"], ACT_BARRAGE, BattleAccessibility.BARRAGE_CLUSTER),
                Action(R.id.cw_a11y_barrage_front, L["a178_barrage_front"], ACT_BARRAGE, BattleAccessibility.BARRAGE_FRONT),
                Action(R.id.cw_a11y_barrage_base, L["a178_barrage_base"], ACT_BARRAGE, BattleAccessibility.BARRAGE_BASE),
            )
            // Dotknięcie przycisku włączyłoby celowanie palcem; czytnik od razu strzela.
            click = actions[0]
        }
        list.add(Node(
            id, b, BattleAccessibility.description(s, b), "", "", "",
            false, false, b.selected, b.enabled, Rect(l, t, r, bt),
            state = BattleAccessibility.state(s, b), key = key, actions = actions, click = click,
        ))
    }

    private fun emitInfo(s: BattleScreen, id: Int, rect: RectF?) {
        val area = when {
            rect == null -> null
            rect.width() > 0f && rect.height() > 0f -> rect
            else -> s.goldRect
        }
        var l = 0
        var t = 0
        var r = planVw
        var bt = planVh
        if (area != null) {
            // Paski HP leżą nad minimapą, a liczby pod nią.
            val extraTop = if (area === s.mini) 14 * s.dp else 0f
            val extraBottom = if (area === s.mini) 16 * s.dp else 0f
            l = (area.left * planSx).toInt().coerceIn(0, planVw)
            t = ((area.top - extraTop) * planSy).toInt().coerceIn(0, planVh)
            r = (area.right * planSx).toInt().coerceIn(0, planVw)
            bt = ((area.bottom + extraBottom) * planSy).toInt().coerceIn(0, planVh)
        }
        val cmp = cmpNodes
        if (cmp != null) {
            if (!cmpOk) return
            if (cmpIndex >= cmp.size) { cmpOk = false; return }
            val n = cmp[cmpIndex++]
            if (n.id != id || n.bounds.left != l || n.bounds.top != t ||
                n.bounds.right != r || n.bounds.bottom != bt) cmpOk = false
            return
        }
        val list = building ?: return
        val text = when (id) {
            INFO_GOLD -> BattleAccessibility.goldText(s)
            INFO_XP -> BattleAccessibility.xpText(s)
            INFO_BASES -> BattleAccessibility.basesText(s)
            INFO_WAVE -> BattleAccessibility.waveText(s)
            INFO_ARMY -> BattleAccessibility.armyText(s)
            INFO_TUTORIAL -> s.tut?.accessibilityText() ?: ""
            INFO_UPGRADE -> BattleAccessibility.upgradeText(s)
            INFO_RESULT -> BattleAccessibility.resultText(s)
            INFO_ERA -> s.eraIntroAccessibilityText
            STATUS -> statusText.ifEmpty { L["a178_status"] }
            else -> ""
        }
        if (id == INFO_ERA) {
            list.add(Node(id, null, text, "", "", "", false, false, false, true, Rect(l, t, r, bt),
                click = Action(AccessibilityNodeInfo.ACTION_CLICK, L["tut_next"], ACT_DISMISS, 0)))
            return
        }
        list.add(Node(id, null, text, "", "", "", false, false, false, true, Rect(l, t, r, bt),
            info = true, live = id == STATUS))
    }

    // ---------------------------------------------------------------- komunikaty (STATUS)

    private var evScreen: BattleScreen? = null
    private var evPlayerEra = 0
    private var evEnemyEra = 0
    private var evBaseBand = 0
    private var evEnemyLow = false
    private var evBosses = 0
    private var evTiers = 0
    private var evRecords = 0
    private var evChoice = false
    private var evFinished = false
    private val evUsed = BooleanArray(3)
    private var statusText = ""
    private var statusAt = Long.MIN_VALUE / 2
    private var statusPrio = 0
    private var pendingText: String? = null
    private var pendingPrio = 0

    /** Porównanie z poprzednią klatką; alokuje tylko tekst nowego komunikatu. */
    private fun trackEvents(s: BattleScreen, now: Long) {
        val b = s.battle
        val p = b.player
        val e = b.enemy
        val band = baseBand(p.baseHp, p.baseMaxHp)
        val enemyLow = !b.endless && e.baseMaxHp > 0f && e.baseHp <= e.baseMaxHp * 0.25f
        val choice = b.pendingVariant != null || b.pendingChoice != null || b.pendingCards != null
        if (evScreen !== s) {
            evScreen = s
            evPlayerEra = p.era
            evEnemyEra = e.era
            evBaseBand = band
            evEnemyLow = enemyLow
            evBosses = b.bossesSpawned
            evTiers = s.milestones?.tierEvents ?: 0
            evRecords = s.milestones?.recordEvents ?: 0
            evChoice = choice
            evFinished = s.finished
            evUsed.fill(false)
            statusText = ""
            statusAt = Long.MIN_VALUE / 2
            statusPrio = 0
            pendingText = null
            return
        }
        if (p.era > evPlayerEra) announce(L.f("a178_era_up", L[Defs.eraKeys[p.era]]), PRIO_NORMAL, now)
        evPlayerEra = p.era
        if (e.era > evEnemyEra) announce(L.f("a178_enemy_era_up", L[Defs.eraKeys[e.era]]), PRIO_NORMAL, now)
        evEnemyEra = e.era
        if (band > evBaseBand) {
            if (band == 2) announce(L["a178_base_low"], PRIO_HIGH, now)
            else announce(L["a178_base_half"], PRIO_NORMAL, now)
        }
        evBaseBand = band
        if (enemyLow && !evEnemyLow) announce(L["a178_enemy_base_low"], PRIO_NORMAL, now)
        evEnemyLow = enemyLow
        if (b.endless && b.bossesSpawned > evBosses) announce(L["a178_boss"], PRIO_NORMAL, now)
        evBosses = b.bossesSpawned
        // R11-03: próg tygodnia i rekord fali (osobne liczniki: każdy komunikat raz).
        val ms = s.milestones
        if (ms != null) {
            // Oba naraz (lub z bossem tej samej fali): jeden komunikat, żeby kolejka nie zgubiła progu.
            val tierText = if (ms.tierEvents > evTiers && ms.lastTier >= 0)
                L.f("ms198_tier_a11y", L[com.centurywar.screens.BattleScreen.WEEKLY_TIER_NAMES[ms.lastTier]],
                    L.plural("medals_n", com.centurywar.game.Weekly.medalReward(ms.lastTier).toLong())) else null
            val recordText = if (ms.recordEvents > evRecords) L.f("ms198_record_a11y", ms.recordWave) else null
            val text = if (tierText != null && recordText != null) "$tierText $recordText" else tierText ?: recordText
            if (text != null) announce(text, PRIO_NORMAL, now)
            evTiers = ms.tierEvents
            evRecords = ms.recordEvents
        }
        for (i in 0..1) {
            if (p.abilityCd[i] > 0f) evUsed[i] = true
            else if (evUsed[i] && b.abilityReady(p, i)) {
                evUsed[i] = false
                announce(L[if (i == 0) "a178_barrage_ready" else "a178_rally_ready"], PRIO_NORMAL, now)
            }
        }
        if (p.commanderCd != 0f) evUsed[2] = true
        else if (evUsed[2]) {
            evUsed[2] = false
            if (b.commanderAvailable) announce(L["a178_commander_ready"], PRIO_NORMAL, now)
        }
        if (choice && !evChoice) {
            val what = L[when {
                b.pendingVariant != null -> "choose_variant"
                b.pendingCards != null -> "choose_card"
                else -> "choose_doctrine"
            }]
            announce(L.f("a178_choice_open", what), PRIO_NORMAL, now)
        }
        evChoice = choice
        if (s.finished && !evFinished)
            announce(L[if (b.winner == 0) "a178_end_win" else "a178_end_loss"], PRIO_HIGH, now)
        evFinished = s.finished
        val waiting = pendingText
        if (waiting != null && now - statusAt >= STATUS_INTERVAL_MS) {
            pendingText = null
            show(waiting, pendingPrio, now)
        }
    }

    private fun baseBand(hp: Float, max: Float): Int = when {
        max <= 0f -> 0
        hp <= max * 0.25f -> 2
        hp <= max * 0.5f -> 1
        else -> 0
    }

    /** Najwyżej jeden komunikat na 3 s; ważniejszy wchodzi od razu i zastępuje oczekujący. */
    private fun announce(text: String, prio: Int, now: Long) {
        if (now - statusAt >= STATUS_INTERVAL_MS || prio > statusPrio) {
            if (pendingText != null && pendingPrio <= prio) pendingText = null
            show(text, prio, now)
        } else if (pendingText == null || prio >= pendingPrio) {
            pendingText = text
            pendingPrio = prio
        }
    }

    private fun show(text: String, prio: Int, now: Long) {
        statusText = text
        statusAt = now
        statusPrio = prio
    }

    // ---------------------------------------------------------------- zdarzenia

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

    /** Wątek UI: zmiana tekstu jednego węzła (bez odświeżania całego drzewa). */
    private fun sendContentChanged(t: Tree, id: Int) {
        val node = t.nodes.firstOrNull { it.id == id } ?: return
        val parent = view.parent ?: return
        val event = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)
        event.packageName = view.context.packageName
        event.className = classNameOf(node)
        event.contentChangeTypes = if (Build.VERSION.SDK_INT >= 30 && node.state.isNotEmpty() && !node.live)
            AccessibilityEvent.CONTENT_CHANGE_TYPE_STATE_DESCRIPTION or AccessibilityEvent.CONTENT_CHANGE_TYPE_TEXT
            else AccessibilityEvent.CONTENT_CHANGE_TYPE_TEXT
        event.setSource(view, id)
        parent.requestSendAccessibilityEvent(view, event)
    }

    private fun sendNodeEvent(id: Int, type: Int) {
        val node = tree?.nodes?.firstOrNull { it.id == id } ?: return
        val parent = view.parent ?: return
        val event = AccessibilityEvent.obtain(type)
        event.packageName = view.context.packageName
        event.className = classNameOf(node)
        event.contentDescription = node.description
        event.setSource(view, id)
        parent.requestSendAccessibilityEvent(view, event)
    }
}

