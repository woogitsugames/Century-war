package com.centurywar.screens

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.centurywar.engine.Button
import com.centurywar.engine.Game
import com.centurywar.engine.Icons
import com.centurywar.engine.L
import com.centurywar.engine.Ui
import com.centurywar.game.Battle
import com.centurywar.game.BattleController
import com.centurywar.game.Defs
import com.centurywar.game.Role
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Interaktywny samouczek pierwszego etapu kampanii. Maszyna stanów sterowana stanem
 * bitwy: każdy krok czeka na swój warunek (np. pierwsze zabójstwo), potem przyciemnia
 * ekran z wycięciem na podświetlony element, pokazuje rękę, ramkę z tekstem i czeka,
 * aż gracz wykona akcję. Kroki "zadaniowe" zatrzymują albo spowalniają symulację
 * ([timeScale]), a każdy ma limit czasu, więc samouczek nigdy się nie zablokuje.
 * Przycisk "Pomiń samouczek" jest dostępny przez cały czas.
 *
 * Rysowanie nie alokuje: teksty są łamane tylko przy zmianie kroku albo szerokości.
 */
class TutorialOverlay(
    private val g: Game,
    private val battle: Battle,
    private val commands: BattleController,
    private val t: Targets,
) {
    /** Elementy ekranu bitwy, na które samouczek wskazuje (referencje, nie kopie). */
    class Targets(
        val unitBtn: Array<Button>,
        val evolveBtn: Button,
        val turretBtn: Button,
        val barrageBtn: Button,
        val slotBuy: Array<Button>,
        val doctrineBtn: Array<Button>,
        /** Przyciski HUD, które krok "zadaniowy" blokuje (poza swoim celem). */
        val hud: Array<Button>,
        val goldRect: RectF,
        val xpRect: RectF,
        val minimap: RectF,
        /** Górna krawędź panelu HUD (przyciski) i dolna paska górnego. */
        val area: RectF,
        val turretsOpen: () -> Boolean,
        val closeTurrets: () -> Unit,
    )

    companion object {
        const val SKIP_ID = 900
        const val NEXT_ID = 901

        const val S_MELEE = 0
        const val S_INCOME = 1
        const val S_RANGED = 2
        const val S_KILL = 3
        const val S_EVOLVE = 4
        const val S_DOCTRINE = 5
        const val S_TURRET = 6
        const val S_BARRAGE = 7
        const val S_CAMERA = 8
        const val S_DONE = 9
        const val STEPS = 9 // numerowane kroki (bez końcowego "Gotowe")

        private val TITLES = arrayOf(
            "tut_melee_t", "tut_income_t", "tut_ranged_t", "tut_kill_t", "tut_evolve_t",
            "tut_doctrine_t", "tut_turret_t", "tut_barrage_t", "tut_camera_t", "tut_done_t",
        )
        private val SIZES = floatArrayOf(17f, 15.5f, 14f, 12.5f)
        private const val MAX_LINES = 12
    }

    val skipBtn = Button(SKIP_ID)
    val nextBtn = Button(NEXT_ID)

    /** Bieżący krok (0..9) i czy jest pokazany (false = czeka na swój warunek). */
    var step = S_MELEE; private set
    var showing = false; private set
    var finished = false; private set
    /** Wycięcie (podświetlony element) w bieżącej klatce; puste, gdy brak. */
    val hole = RectF()
    val panel = RectF()

    private var phaseT = 0f
    private var readyT = 0f
    private var clock = 0f
    private var waitStartSim = 0f
    private var baseline = 0
    private var nextPressed = false
    private var cameraMoved = false

    private val dimPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val handFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val handLine = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND }
    private val path = Path().apply { fillType = Path.FillType.EVEN_ODD }
    private val handPath = Path()
    private val tmp = RectF()

    private val lines = Array(SIZES.size) { arrayOfNulls<String>(MAX_LINES) }
    private val lineCount = IntArray(SIZES.size)
    private var wrapKey: String? = null
    private var wrapW = -1f
    private var title = ""
    private var counter = ""

    private val p get() = battle.player
    private val dp get() = g.ui.dp

    init {
        skipBtn.visible = false
        nextBtn.visible = false
        nextBtn.highlight = true
        nextBtn.iconId = Icons.PLAY
        skipBtn.label = L["tut_skip"]
    }

    // ---------------------------------------------------------------- logika

    /** Mnożnik tempa symulacji: 0 = stoi i czeka na gracza, < 1 = zwolnione na czas czytania. */
    val timeScale: Float
        get() {
            if (finished || !showing) return 1f
            return when (step) {
                S_MELEE, S_RANGED, S_TURRET, S_BARRAGE -> 0f
                S_INCOME, S_KILL -> 0.3f
                S_EVOLVE -> if (battle.canEvolve(p)) 0f else 1f
                S_CAMERA -> 0.5f
                else -> 1f
            }
        }

    private val gating: Boolean
        get() = showing && !finished && when (step) {
            S_MELEE, S_RANGED, S_TURRET, S_BARRAGE -> true
            S_EVOLVE -> battle.canEvolve(p)
            else -> false
        }

    fun update(dt: Float) {
        clock += dt
        if (finished) return
        if (battle.over) { skipBtn.visible = false; nextBtn.visible = false; return }
        phaseT += dt
        if (!showing) {
            val waited = battle.time - waitStartSim
            when {
                alreadyDone() -> advance()
                trigger(waited) -> show()
                waited > waitLimit() -> if (forceable()) show() else advance()
            }
        } else {
            if (step == S_EVOLVE && battle.canEvolve(p)) readyT += dt
            if (completed() || timedOut()) advance()
        }
    }

    private fun alreadyDone(): Boolean = when (step) {
        S_MELEE -> p.queueSize > 0 || p.spawned > 0
        S_EVOLVE -> p.era > battle.cfg.startEra || battle.cfg.maxEra <= battle.cfg.startEra
        S_DOCTRINE -> battle.pendingChoice == null && (p.doctrines.isNotEmpty() || p.era == battle.cfg.startEra)
        S_TURRET -> p.slots == 0 || anyTurret()
        S_BARRAGE -> p.abilityCd[0] > 0f
        else -> false
    }

    private fun trigger(waited: Float): Boolean = when (step) {
        S_INCOME -> p.spawned >= 1
        S_KILL -> p.kills >= 1
        S_EVOLVE -> battle.canEvolve(p) || p.xp >= Defs.evolveXp[p.era] * 0.8f
        S_DOCTRINE -> battle.pendingChoice != null
        S_TURRET -> waited >= 2f
        S_BARRAGE -> battle.abilityReady(p, 0) && enemiesNear(1350f) >= 3
        S_CAMERA -> waited >= 3f
        else -> true
    }

    /** Ile sekund symulacji krok może czekać na swój warunek. */
    private fun waitLimit(): Float = when (step) {
        S_INCOME -> 15f
        S_KILL -> 90f
        S_EVOLVE -> 420f
        S_BARRAGE -> 70f
        else -> 30f
    }

    /** Czy po upływie [waitLimit] pokazać krok mimo braku warunku (inaczej: pomiń go). */
    private fun forceable(): Boolean = when (step) {
        S_INCOME, S_KILL -> true
        S_BARRAGE -> battle.abilityReady(p, 0)
        else -> false
    }

    private fun completed(): Boolean = when (step) {
        S_MELEE -> p.queueSize > 0 || p.spawned > 0
        S_RANGED -> countRanged() > baseline
        S_EVOLVE -> p.era > battle.cfg.startEra
        S_DOCTRINE -> battle.pendingChoice == null
        S_TURRET -> anyTurret()
        S_BARRAGE -> p.abilityCd[0] > 0f
        S_CAMERA -> nextPressed || (cameraMoved && phaseT > 1.2f)
        else -> nextPressed
    }

    /** Limity czasu rzeczywistego: po nich krok znika sam (zabezpieczenie przed blokadą). */
    private fun timedOut(): Boolean = when (step) {
        S_INCOME, S_KILL -> phaseT > 16f
        S_EVOLVE -> readyT > 40f || phaseT > 240f
        S_DOCTRINE -> phaseT > 90f
        S_CAMERA -> phaseT > 18f
        S_DONE -> phaseT > 7f
        else -> phaseT > 45f
    }

    private fun show() {
        showing = true
        phaseT = 0f; readyT = 0f
        nextPressed = false; cameraMoved = false
        when (step) {
            S_MELEE -> ensureGold(Defs.unit(p.era, Role.MELEE).cost)
            S_RANGED -> { ensureGold(Defs.unit(p.era, Role.RANGED).cost); baseline = countRanged() }
            S_TURRET -> ensureGold(p.turretCost())
            S_BARRAGE, S_CAMERA -> t.closeTurrets() // panel wież zasłaniałby pole bitwy
            S_DONE -> markDone()
        }
        title = L[TITLES[step]]
        counter = if (step < STEPS) L.f("tut_step", step + 1, STEPS) else ""
        nextBtn.label = if (step == S_DONE) L["tut_go"] else L["tut_next"]
        skipBtn.label = L["tut_skip"]
    }

    private fun advance() {
        if (step >= S_DONE) { finished = true; markDone(); hideButtons(); return }
        step++
        showing = false
        phaseT = 0f
        waitStartSim = battle.time
        nextPressed = false
    }

    fun skip() {
        finished = true
        markDone()
        hideButtons()
    }

    private fun hideButtons() { skipBtn.visible = false; nextBtn.visible = false }

    private fun markDone() {
        val pr = g.profile
        if (!pr.tutorialDone) { pr.tutorialDone = true; pr.save() }
    }

    /** Samouczek ma nauczyć, nie karać: brakujące złoto na wymaganą akcję dopłacamy. */
    private fun ensureGold(cost: Int) { commands.tutorialGold(cost) }

    private fun anyTurret(): Boolean {
        for (i in 0 until p.slots) if (p.turretEra[i] >= 0) return true
        return false
    }

    private fun countRanged(): Int {
        var n = 0
        for (i in 0 until p.queueSize) if (Defs.allUnits[p.queue[i]].role == Role.RANGED) n++
        for (u in battle.units) if (u.alive && u.side == 0 && u.def.role == Role.RANGED) n++
        return n
    }

    private fun enemiesNear(dist: Float): Int {
        var n = 0
        for (u in battle.units) if (u.alive && u.side == 1 && abs(u.x - p.baseFront) < dist) n++
        return n
    }

    /** Przeciągnięcie kamery albo dotknięcie minimapy. */
    fun onCamera() { cameraMoved = true }

    /** Czy przycisk ekranu bitwy może teraz zadziałać (krok zadaniowy blokuje resztę HUD). */
    fun allows(b: Button): Boolean {
        if (!gating || b !in t.hud) return true
        return when (step) {
            S_MELEE -> b === t.unitBtn[0]
            S_RANGED -> b === t.unitBtn[1]
            S_TURRET -> b === t.turretBtn
            S_BARRAGE -> b === t.barrageBtn
            S_EVOLVE -> b === t.evolveBtn
            else -> true
        }
    }

    /** Obsługa własnych przycisków; true = kliknięcie zużyte. */
    fun onClick(b: Button): Boolean = when (b.id) {
        SKIP_ID -> { skip(); true }
        NEXT_ID -> { if (phaseT > 0.5f) nextPressed = true; true }
        else -> false
    }

    // ---------------------------------------------------------------- rysowanie

    private fun bodyKey(): String = when (step) {
        S_MELEE -> "tut_melee"
        S_INCOME -> "tut_income"
        S_RANGED -> "tut_ranged"
        S_KILL -> "tut_kill"
        S_EVOLVE -> if (battle.canEvolve(p)) "tut_evolve" else "tut_evolve_wait"
        S_DOCTRINE -> "tut_doctrine"
        S_TURRET -> if (t.turretsOpen()) "tut_turret_buy" else "tut_turret_open"
        S_BARRAGE -> "tut_barrage"
        S_CAMERA -> "tut_camera"
        else -> "tut_done"
    }

    private fun hasNext() = step == S_INCOME || step == S_KILL || step == S_CAMERA || step == S_DONE

    /** Ustawia [hole] na bieżący cel; false = brak celu (panel na środku). */
    private fun target(): Boolean {
        when (step) {
            S_MELEE -> hole.set(t.unitBtn[0].r)
            S_RANGED -> hole.set(t.unitBtn[1].r)
            S_INCOME -> hole.set(t.goldRect)
            S_KILL -> { hole.set(t.goldRect); hole.union(t.xpRect) }
            S_EVOLVE -> hole.set(t.evolveBtn.r)
            S_DOCTRINE -> {
                if (!t.doctrineBtn[0].visible) return false
                hole.set(t.doctrineBtn[0].r)
                for (i in 1 until t.doctrineBtn.size) hole.union(t.doctrineBtn[i].r)
            }
            S_TURRET -> if (t.turretsOpen()) {
                val s = battle.freeSlot(p)
                hole.set(t.slotBuy[if (s >= 0) s else 0].r)
            } else hole.set(t.turretBtn.r)
            S_BARRAGE -> hole.set(t.barrageBtn.r)
            S_CAMERA -> { if (t.minimap.width() <= 0f) return false; hole.set(t.minimap) }
            else -> return false
        }
        if (hole.width() <= 0f) return false
        hole.inset(-5 * dp, -5 * dp)
        return true
    }

    fun draw(c: Canvas) {
        if (finished || battle.over) return
        val ui = g.ui
        val sl = g.insetL + 12 * dp
        val sr = g.width - g.insetR - 12 * dp
        val st = g.insetT + 8 * dp
        val sb = g.height - g.insetB - 8 * dp
        if (!showing) {
            // czekamy na warunek kroku: tylko mały przycisk pominięcia w rogu
            nextBtn.visible = false
            skipBtn.visible = true
            val bw = min(170 * dp, ui.measure(skipBtn.label, 13f, true) + 30 * dp)
            skipBtn.set(sr - bw, t.area.top + 10 * dp, bw, 34 * dp)
            ui.button(c, skipBtn, 13f)
            return
        }
        val hasHole = target()
        val big = hasHole && hole.height() > (sb - st) * 0.35f
        val hand = hasHole && !big && step != S_DOCTRINE

        // 1. przyciemnienie z wycięciem
        val dimA = when (timeScale) { 0f -> 170; 1f -> if (step == S_DOCTRINE) 110 else 120; else -> 140 }
        dimPaint.color = Color.argb(dimA, 4, 6, 14)
        path.rewind()
        path.addRect(0f, 0f, g.width.toFloat(), g.height.toFloat(), Path.Direction.CW)
        val rad = 12 * dp
        if (hasHole) path.addRoundRect(hole, rad, rad, Path.Direction.CW)
        c.drawPath(path, dimPaint)

        // 2. pulsujący pierścień wokół celu
        if (hasHole) {
            val pulse = (sin(clock * 6f) + 1f) * 0.5f
            val wave = (clock * 1.1f) % 1f
            ringPaint.strokeWidth = 3 * dp
            ringPaint.color = Color.argb(((1f - wave) * 200).toInt(), 255, 220, 120)
            tmp.set(hole); tmp.inset(-(4 + wave * 18) * dp, -(4 + wave * 18) * dp)
            c.drawRoundRect(tmp, rad + wave * 18 * dp, rad + wave * 18 * dp, ringPaint)
            ringPaint.strokeWidth = 5 * dp
            ringPaint.color = Ui.OUTLINE
            tmp.set(hole); tmp.inset(-(1 + pulse * 4) * dp, -(1 + pulse * 4) * dp)
            c.drawRoundRect(tmp, rad, rad, ringPaint)
            ringPaint.strokeWidth = 3 * dp
            ringPaint.color = Ui.blend(Ui.GOLD, Color.WHITE, pulse * 0.5f)
            c.drawRoundRect(tmp, rad, rad, ringPaint)
        }

        // 3. panel z tekstem: nad albo pod celem, tam gdzie jest więcej miejsca
        val pw = min(560 * dp, sr - sl)
        val padX = 26 * dp
        prepare(bodyKey(), pw - padX * 2)
        val fixed = 24 * dp + (if (counter.isEmpty()) 0f else 18 * dp) + 12 * dp + 40 * dp + 16 * dp
        val ribbonOut = 18 * dp
        // przy doktrynach zostaw miejsce na ich własną wstęgę nad kartami
        val handGap = if (hand) 62 * dp else if (step == S_DOCTRINE) 52 * dp else 16 * dp
        var top0 = st + ribbonOut
        var bot0 = sb
        var center = false
        if (hasHole) {
            val aboveB = hole.top - handGap
            val belowT = hole.bottom + handGap + ribbonOut
            if (aboveB - top0 >= sb - belowT) bot0 = aboveB else top0 = belowT
        } else {
            top0 = max(top0, t.area.top + ribbonOut + 6 * dp)
            bot0 = min(bot0, t.area.bottom - 6 * dp)
            center = true
        }
        val avail = bot0 - top0
        var si = SIZES.size - 1
        for (i in SIZES.indices) {
            if (fixed + lineCount[i] * SIZES[i] * dp * 1.32f <= avail) { si = i; break }
        }
        val sz = SIZES[si]
        val lh = sz * dp * 1.32f
        val ph = fixed + lineCount[si] * lh
        val cx = if (hasHole) hole.centerX() else g.width / 2f
        val left = (cx - pw / 2).coerceIn(sl, max(sl, sr - pw))
        val top = when {
            center -> top0 + max(0f, (avail - ph) / 2)
            hasHole && bot0 < hole.top -> max(st + ribbonOut, bot0 - ph)
            else -> min(top0, sb - ph)
        }
        panel.set(left, top, left + pw, top + ph)
        ui.panel(c, panel)
        ui.ribbon(c, panel.centerX(), panel.top + 4 * dp, title, 18f, Ui.RIBBON, pw * 0.45f, Ui.GOLD, pw - 20 * dp)
        var y = panel.top + 24 * dp
        if (counter.isNotEmpty()) {
            y += 13 * dp
            ui.textO(c, counter, panel.centerX(), y, 11f, Ui.DIM, Paint.Align.CENTER, 1.6f)
            y += 5 * dp
        }
        y += 6 * dp
        val ln = lines[si]
        for (i in 0 until lineCount[si]) {
            y += lh
            ui.text(c, ln[i]!!, panel.centerX(), y - lh * 0.28f, sz, Ui.TEXT, Paint.Align.CENTER, false, true)
        }
        // stopka: pominięcie po lewej, "Dalej" po prawej
        val by = panel.bottom - 16 * dp - 38 * dp
        val skipW = min(ui.measure(skipBtn.label, 13f, true) + 28 * dp, pw * 0.48f - padX)
        skipBtn.visible = step != S_DONE
        skipBtn.set(panel.left + 18 * dp, by + 3 * dp, skipW, 34 * dp)
        if (skipBtn.visible) ui.button(c, skipBtn, 13f)
        nextBtn.visible = hasNext()
        if (nextBtn.visible) {
            val nw = min(150 * dp, pw * 0.42f)
            nextBtn.enabled = phaseT > 0.5f
            nextBtn.set(if (step == S_DONE) panel.centerX() - nw / 2 else panel.right - 18 * dp - nw, by, nw, 40 * dp)
            ui.button(c, nextBtn, 15f)
        }

        // 4. ręka wskazująca cel
        if (hand) {
            val bob = (sin(clock * 5f) + 1f) * 4 * dp
            val down = panel.bottom <= hole.top
            val tipY = if (down) hole.top - 6 * dp - bob else hole.bottom + 6 * dp + bob
            drawHand(c, hole.centerX(), tipY, 46 * dp, down)
        }
    }

    /** Kreskówkowa dłoń z wyciągniętym palcem; czubek palca w ([x], [y]). */
    private fun drawHand(c: Canvas, x: Float, y: Float, s: Float, down: Boolean) {
        c.save()
        c.translate(x, y)
        if (down) c.rotate(180f)
        c.rotate(-14f)
        handPath.rewind()
        handPath.addRoundRect(-0.11f * s, 0f, 0.11f * s, 0.62f * s, 0.11f * s, 0.11f * s, Path.Direction.CW)
        handPath.addRoundRect(-0.30f * s, 0.38f * s, 0.27f * s, 0.88f * s, 0.15f * s, 0.15f * s, Path.Direction.CW)
        handPath.addRoundRect(-0.44f * s, 0.44f * s, -0.16f * s, 0.64f * s, 0.10f * s, 0.10f * s, Path.Direction.CW)
        // cień
        c.save(); c.translate(0.05f * s, 0.06f * s)
        handFill.color = Color.argb(90, 0, 0, 0); c.drawPath(handPath, handFill)
        c.restore()
        handLine.color = Ui.OUTLINE; handLine.strokeWidth = 0.09f * s
        c.drawPath(handPath, handLine)
        handFill.color = Color.rgb(255, 214, 172); c.drawPath(handPath, handFill)
        // zgięte palce (kreski) i połysk
        handLine.strokeWidth = 0.035f * s
        handLine.color = Color.rgb(200, 140, 100)
        c.drawLine(0.12f * s, 0.52f * s, 0.25f * s, 0.52f * s, handLine)
        c.drawLine(0.12f * s, 0.64f * s, 0.25f * s, 0.64f * s, handLine)
        c.drawLine(0.12f * s, 0.76f * s, 0.25f * s, 0.76f * s, handLine)
        handFill.color = Color.argb(140, 255, 255, 255)
        c.drawRoundRect(-0.06f * s, 0.06f * s, -0.01f * s, 0.32f * s, 0.03f * s, 0.03f * s, handFill)
        // mankiet
        handLine.color = Ui.OUTLINE; handLine.strokeWidth = 0.07f * s
        c.drawRoundRect(-0.34f * s, 0.84f * s, 0.31f * s, 1.04f * s, 0.05f * s, 0.05f * s, handLine)
        handFill.color = Ui.PLAYER
        c.drawRoundRect(-0.34f * s, 0.84f * s, 0.31f * s, 1.04f * s, 0.05f * s, 0.05f * s, handFill)
        c.restore()
    }

    /** Łamie tekst kroku dla wszystkich rozmiarów; tylko przy zmianie tekstu lub szerokości. */
    private fun prepare(key: String, width: Float) {
        if (key == wrapKey && width == wrapW) return
        wrapKey = key; wrapW = width
        val s = L[key]
        for (i in SIZES.indices) lineCount[i] = wrapInto(s, width, SIZES[i], lines[i])
    }

    private fun wrapInto(s: String, width: Float, size: Float, out: Array<String?>): Int {
        var n = 0
        var start = 0
        var lastFit = -1
        var i = 0
        while (i <= s.length && n < MAX_LINES) {
            if (i == s.length || s[i] == ' ') {
                val wpx = g.ui.measure(s.substring(start, i), size)
                if (wpx > width && lastFit > start) {
                    out[n++] = s.substring(start, lastFit)
                    start = lastFit + 1
                    lastFit = -1
                    continue // ponownie sprawdź to samo słowo w nowej linii
                }
                if (i == s.length) { out[n++] = s.substring(start); break }
                lastFit = i
            }
            i++
        }
        return n
    }
}
