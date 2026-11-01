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
 * aż gracz wykona akcję. Każdy widoczny krok zatrzymuje symulację ([timeScale]),
 * a każdy ma limit czasu, więc samouczek nigdy się nie zablokuje.
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
        val armoryBtn: Button,
        val stanceBtn: Button,
        val rallyBtn: Button,
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
        val barrageAiming: () -> Boolean,
        val screenX: (Float) -> Float,
        val groundY: () -> Float,
        val worldScale: () -> Float,
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
        const val S_ARMORY = 7
        const val S_STANCE = 8
        const val S_RALLY = 9
        const val S_BARRAGE = 10
        const val S_CAMERA = 11
        const val S_DONE = 12
        const val STEPS = 12 // numerowane kroki (bez końcowego "Gotowe")

        private val TITLES = arrayOf(
            "tut_melee_t", "tut_income_t", "tut_ranged_t", "tut_kill_t", "tut_evolve_t",
            "tut_doctrine_t", "tut_turret_t", "tut_armory54_t", "tut_stance54_t",
            "tut_rally54_t", "tut_barrage_t", "tut_camera_t", "tut_done_t",
        )
        private val SIZES = floatArrayOf(17f, 15.5f, 14f, 12.5f, 11.5f)
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
    private var previousHold = battle.player.hold
    private var stanceUsed = battle.player.hold
    /** Krok postawy: gracz ustawił Trzymaj; zaliczenie dopiero po powrocie do Naprzód. */
    private var stanceHeld = false

    private val dimPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val handFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val handLine = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND }
    private val path = Path().apply { fillType = Path.FillType.EVEN_ODD }
    private val handPath = Path()
    private val tmp = RectF()
    private val barrageBins = IntArray(10)

    // Osobny cache dla czterech położeń: brak alokacji podczas kolejnych klatek.
    private val lines = Array(4) { Array(SIZES.size) { arrayOfNulls<String>(MAX_LINES) } }
    private val lineCount = Array(4) { IntArray(SIZES.size) }
    private val wrapKey = arrayOfNulls<String>(4)
    private val wrapW = FloatArray(4) { -1f }
    private val wrapLang = IntArray(4) { -1 }
    private val wrapScale = FloatArray(4) { -1f }
    private val regions = Array(4) { RectF() }
    private val confirmLines = arrayOfNulls<String>(MAX_LINES)
    private var confirmKey: String? = null
    private var confirmW = -1f
    private var confirmScale = -1f
    private var confirmCount = 0
    private var selectedRegion = 0
    private var selectedFont = 0
    private var selectedCounter = true
    private var selectedHand = false
    private var skipPending = false
    val skipConfirmationVisible: Boolean get() = skipPending
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

    /** Aktualizuje teksty bieżącego kroku bez restartowania samouczka. */
    fun onLanguageChanged() {
        wrapKey.fill(null)
        confirmKey = null
        title = L[TITLES[step]]
        counter = if (step < STEPS) L.f("tut_step", step + 1, STEPS) else ""
        nextBtn.label = if (skipPending) L["tut85_cancel"] else if (step == S_DONE) L["tut_go"] else L["tut_next"]
        nextBtn.iconId = if (skipPending) -1 else Icons.PLAY
        skipBtn.label = L[if (skipPending) "tut85_skip_yes" else "tut_skip"]
    }

    // ---------------------------------------------------------------- logika

    /** Mnożnik tempa symulacji: 0 = stoi i czeka na gracza, < 1 = zwolnione na czas czytania. */
    val timeScale: Float
        get() {
            if (finished) return 1f
            if (skipPending) return 0f
            if (!showing) return 1f
            // Każdy widoczny dymek zatrzymuje bitwę: gracz czyta we własnym tempie
            // i nie traci jednostek ani bazy, zanim przeczyta i wykona polecenie.
            return 0f
        }

    private val gating: Boolean
        get() = showing && !finished && when (step) {
            S_MELEE, S_RANGED, S_TURRET, S_ARMORY, S_STANCE, S_RALLY, S_BARRAGE -> true
            S_EVOLVE -> battle.canEvolve(p)
            else -> false
        }

    fun update(dt: Float) {
        clock += dt
        if (p.hold != previousHold) { previousHold = p.hold; stanceUsed = true }
        if (step == S_STANCE && showing && p.hold) stanceHeld = true
        if (finished) return
        if (battle.over) { skipPending = false; hideButtons(); return }
        if (skipPending) return
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
        S_RANGED -> countRanged() > 0
        S_EVOLVE -> p.era > battle.cfg.startEra || battle.cfg.maxEra <= battle.cfg.startEra
        S_DOCTRINE -> battle.pendingChoice == null && (p.doctrines.isNotEmpty() || p.era == battle.cfg.startEra)
        S_TURRET -> p.slots == 0 || anyTurret()
        S_ARMORY -> p.armory > 0
        S_STANCE -> stanceUsed
        S_RALLY -> !p.rallyUnlocked || p.ralliesUsed > 0
        S_BARRAGE -> p.barragesUsed > 0
        else -> false
    }

    private fun trigger(waited: Float): Boolean = when (step) {
        S_INCOME -> p.spawned >= 1
        S_KILL -> p.kills >= 1
        S_EVOLVE -> battle.canEvolve(p)
        S_DOCTRINE -> battle.pendingChoice != null
        S_TURRET -> waited >= 2f
        S_ARMORY -> p.armory < 15 && (p.aliveCount >= p.cap || p.gold >= battle.armoryCost(p) * 1.35f)
        S_STANCE -> !p.hold && enemiesNear(700f) >= 2
        S_RALLY -> p.rallyUnlocked && battle.abilityReady(p, 1) && enemiesNear(700f) >= 2
        S_BARRAGE -> battle.abilityReady(p, 0) && enemiesNear(1350f) >= 3
        S_CAMERA -> waited >= 3f
        else -> true
    }

    /** Ile sekund symulacji krok może czekać na swój warunek. */
    private fun waitLimit(): Float = when (step) {
        S_INCOME -> 15f
        S_KILL -> 90f
        S_EVOLVE -> 420f
        S_ARMORY -> 30f
        S_STANCE, S_RALLY -> 25f
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
        S_ARMORY -> p.armory > 0
        S_STANCE -> stanceHeld && !p.hold
        S_RALLY -> p.ralliesUsed > 0
        S_BARRAGE -> p.barragesUsed > 0
        S_CAMERA -> nextPressed || (cameraMoved && phaseT > 1.2f)
        else -> nextPressed
    }

    /** Limity czasu rzeczywistego: po nich krok znika sam (zabezpieczenie przed blokadą). */
    private fun timedOut(): Boolean = when (step) {
        // Bitwa stoi, więc limity są długie; to tylko zabezpieczenie przed zablokowaniem.
        S_INCOME, S_KILL -> phaseT > 90f
        S_EVOLVE -> readyT > 120f || phaseT > 240f
        S_DOCTRINE -> phaseT > 120f
        S_CAMERA -> phaseT > 90f
        S_DONE -> phaseT > 60f
        else -> phaseT > 120f
    }

    private fun show() {
        showing = true
        phaseT = 0f; readyT = 0f
        nextPressed = false; cameraMoved = false
        when (step) {
            S_MELEE -> ensureGold(Defs.unit(p.era, Role.MELEE).cost)
            S_RANGED -> { ensureGold(Defs.unit(p.era, Role.RANGED).cost); baseline = countRanged() }
            S_TURRET -> ensureGold(p.turretCost())
            S_ARMORY -> { t.closeTurrets(); ensureGold(battle.armoryCost(p)) }
            S_STANCE, S_RALLY, S_BARRAGE, S_CAMERA -> t.closeTurrets() // odsłaniamy HUD i pole bitwy
            S_DONE -> markDone()
        }
        title = L[TITLES[step]]
        counter = if (step < STEPS) L.f("tut_step", step + 1, STEPS) else ""
        nextBtn.label = if (step == S_DONE) L["tut_go"] else L["tut_next"]
        skipBtn.label = L["tut_skip"]
    }

    private fun advance() {
        if (step >= S_DONE) { finished = true; markDone(); hideButtons(); return }
        // Krok postawy nie zostawia armii w Trzymaj (np. po limicie czasu).
        if (step == S_STANCE && showing && p.hold) commands.stance(false)
        step++
        showing = false
        phaseT = 0f
        waitStartSim = battle.time
        nextPressed = false
    }

    private fun skip() {
        skipPending = false
        finished = true
        markDone()
        hideButtons()
    }

    private fun requestSkip() {
        skipPending = true
        skipBtn.label = L["tut85_skip_yes"]
        skipBtn.color = Ui.BTN_RED
        nextBtn.label = L["tut85_cancel"]
        nextBtn.iconId = -1
        nextBtn.enabled = true
    }

    private fun cancelSkip() {
        skipPending = false
        skipBtn.label = L["tut_skip"]
        skipBtn.color = Ui.BTN
        nextBtn.label = L[if (step == S_DONE) "tut_go" else "tut_next"]
        nextBtn.iconId = Icons.PLAY
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
        if (skipPending) return b === skipBtn || b === nextBtn
        if (!gating || b !in t.hud) return true
        return when (step) {
            S_MELEE -> b === t.unitBtn[0]
            S_RANGED -> b === t.unitBtn[1]
            S_TURRET -> b === t.turretBtn
            S_ARMORY -> b === t.armoryBtn
            S_STANCE -> b === t.stanceBtn
            S_RALLY -> b === t.rallyBtn
            S_BARRAGE -> b === t.barrageBtn
            S_EVOLVE -> b === t.evolveBtn
            else -> true
        }
    }

    /** Obsługa własnych przycisków; true = kliknięcie zużyte. */
    fun onClick(b: Button): Boolean = when (b.id) {
        SKIP_ID -> {
            if (!finished && !battle.over) {
                if (skipPending) skip() else requestSkip()
            }
            true
        }
        NEXT_ID -> {
            if (skipPending) cancelSkip()
            else if (phaseT > 0.5f) nextPressed = true
            true
        }
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
        S_ARMORY -> "tut_armory54"
        S_STANCE -> "tut_stance54"
        S_RALLY -> "tut_rally54"
        S_BARRAGE -> if (t.barrageAiming()) "tut_barrage_drag54" else "tut_barrage54"
        S_CAMERA -> "tut_camera"
        else -> "tut_done"
    }

    private fun hasNext() = step == S_INCOME || step == S_KILL || step == S_CAMERA || step == S_DONE

    /** Obejmuje najliczniejsze widoczne skupisko przeciwników w zasięgu ostrzału. */
    private fun barrageTargetHole() {
        val near = p.baseFront + 220f
        val far = p.baseFront + 1370f
        barrageBins.fill(0)
        for (u in battle.units) {
            if (!u.alive || u.side != 1 || u.x < near || u.x > far) continue
            val x = t.screenX(u.x)
            if (x < t.area.left + 12f * dp || x > t.area.right - 12f * dp) continue
            val bin = ((u.x - near) / 115f).toInt().coerceIn(0, barrageBins.lastIndex)
            barrageBins[bin]++
        }
        var best = -1
        var count = 0
        for (i in barrageBins.indices) {
            if (barrageBins[i] > count) { count = barrageBins[i]; best = i }
        }
        val focusWorld = if (best >= 0) near + (best + 0.5f) * 115f else near + 575f
        val halfW = min(110f * dp, max(40f * dp, 120f * t.worldScale()))
        val l = t.area.left + halfW + 8f * dp
        val rr = t.area.right - halfW - 8f * dp
        val x = if (l <= rr) t.screenX(focusWorld).coerceIn(l, rr) else t.area.centerX()
        val ground = t.groundY()
        hole.set(x - halfW, max(t.area.top + 12f * dp, ground - 82f * dp),
            x + halfW, min(t.area.bottom - 8f * dp, ground + 10f * dp))
    }

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
            S_ARMORY -> hole.set(t.armoryBtn.r)
            S_STANCE -> hole.set(t.stanceBtn.r)
            S_RALLY -> hole.set(t.rallyBtn.r)
            S_BARRAGE -> if (t.barrageAiming()) barrageTargetHole() else hole.set(t.barrageBtn.r)
            S_CAMERA -> { if (t.minimap.width() <= 0f) return false; hole.set(t.minimap) }
            else -> return false
        }
        if (hole.width() <= 0f) return false
        hole.inset(-5 * dp, -5 * dp)
        return true
    }

    /** Prostokąty są rozłączne również po uwzględnieniu marginesu. */
    private fun overlaps(l: Float, t0: Float, r: Float, b: Float, other: RectF, gap: Float): Boolean =
        other.width() > 0f && l < other.right + gap && r > other.left - gap &&
            t0 < other.bottom + gap && b > other.top - gap

    /** Najpierw wybiera miejsce obok celu; tekst może przejść do mniejszej czcionki. */
    private fun layoutPanel(sl: Float, sr: Float, st: Float, sb: Float, hasHole: Boolean, wantsHand: Boolean) {
        val ui = g.ui
        val padX = 22 * dp
        val body = bodyKey()
        val firstGap = if (wantsHand) 62 * dp else if (step == S_DOCTRINE) 12 * dp else 18 * dp
        var best = -1
        var bestFont = SIZES.lastIndex
        var bestCounter = false
        var bestWidth = 0f
        var bestHeight = 0f
        var bestScore = -1f
        var handSpace = false

        for (pass in 0..1) {
            val gap = if (pass == 0) firstGap else 8 * dp
            if (hasHole) {
                regions[0].set(sl, st + 8 * dp, sr, hole.top - gap)
                regions[1].set(sl, hole.bottom + gap, sr, sb)
                regions[2].set(sl, st + 8 * dp, hole.left - gap, sb)
                regions[3].set(hole.right + gap, st + 8 * dp, sr, sb)
            } else {
                regions[0].set(sl, max(st + 8 * dp, t.area.top + 8 * dp), sr,
                    min(sb, t.area.bottom - 8 * dp))
                for (i in 1..3) regions[i].setEmpty()
            }
            for (i in 0..3) {
                val rect = regions[i]
                val pw = min(560 * dp, rect.width())
                // Bardzo wąskie kolumny nie nadają się na opis.
                if (pw < 185 * dp || rect.height() < 44 * dp) continue
                prepare(body, pw - padX * 2, i)
                for (si in SIZES.indices) {
                    val lh = SIZES[si] * dp * 1.32f
                    for (withCounter in 1 downTo 0) {
                        if (withCounter == 1 && counter.isEmpty()) continue
                        val showCount = withCounter == 1
                        val fixed = (30 + (if (showCount) 14 else 0) +
                            (if (hasNext()) 72 else 0)) * dp
                        val ph = fixed + lineCount[i][si] * lh
                        if (ph > rect.height()) continue
                        // Czytelność przed zapasem miejsca; pionowy panel jest bliżej celu.
                        val score = (SIZES.size - si) * 1000f + (if (showCount) 100f else 0f) +
                            (if (i < 2) 40f else 0f) + pw / dp * 0.05f +
                            min(100f, (rect.height() - ph) / dp) * 0.1f
                        if (score > bestScore) {
                            bestScore = score
                            best = i; bestFont = si; bestCounter = showCount
                            bestWidth = pw; bestHeight = ph
                            handSpace = pass == 0 && wantsHand && i < 2
                        }
                        break
                    }
                    if (best == i && bestFont == si) break
                }
            }
            if (best >= 0) break
        }

        if (best < 0) {
            // Ekstremalnie mały ekran / duża czcionka: pozostań poza celem.
            best = 0
            for (i in 1..3) if (regions[i].height() > regions[best].height() &&
                regions[i].width() >= 185 * dp) best = i
            val rect = regions[best]
            bestWidth = min(560 * dp, max(0f, rect.width()))
            prepare(body, max(1f, bestWidth - padX * 2), best)
            bestFont = SIZES.lastIndex
            bestCounter = false
            bestHeight = max(0f, rect.height())
        }

        selectedRegion = best
        selectedFont = bestFont
        selectedCounter = bestCounter
        selectedHand = handSpace
        // Regiony z ostatniego przebiegu ponownie obliczamy dla wybranego odstępu.
        val gap = if (handSpace) firstGap else if (hasHole) 8 * dp else 0f
        if (hasHole) {
            regions[0].set(sl, st + 8 * dp, sr, hole.top - gap)
            regions[1].set(sl, hole.bottom + gap, sr, sb)
            regions[2].set(sl, st + 8 * dp, hole.left - gap, sb)
            regions[3].set(hole.right + gap, st + 8 * dp, sr, sb)
        }
        val r = regions[best]
        val left = when (best) {
            2 -> r.right - bestWidth
            3 -> r.left
            else -> ((if (hasHole) hole.centerX() else (sl + sr) / 2f) - bestWidth / 2)
                .coerceIn(r.left, max(r.left, r.right - bestWidth))
        }
        val top = when {
            !hasHole -> r.top + max(0f, (r.height() - bestHeight) / 2)
            best == 0 -> r.bottom - bestHeight
            best == 1 -> r.top
            else -> (hole.centerY() - bestHeight / 2).coerceIn(r.top, max(r.top, r.bottom - bestHeight))
        }
        panel.set(left, top, left + bestWidth, top + bestHeight)
    }

    /** Pomijanie nie sąsiaduje z Dalej i nie przykrywa panelu ani podświetlenia. */
    private fun layoutSkip(sl: Float, sr: Float, st: Float, sb: Float, hasHole: Boolean) {
        val ui = g.ui
        val bw = min(220 * dp, max(126 * dp, ui.measure(skipBtn.label, 13f, true) + 28 * dp))
        val bh = 48 * dp
        val left = sl
        val right = sr - bw
        val fieldTop = max(st + 8 * dp, t.area.top + 8 * dp)
        val fieldBottom = min(sb - bh, t.area.bottom - bh - 8 * dp)
        var chosenX = right
        var chosenY = fieldTop
        for (i in 0..7) {
            val x = if (i % 2 == 0) right else left
            val y = when (i / 2) {
                0 -> fieldTop
                1 -> sb - bh
                2 -> st + 8 * dp
                else -> max(fieldTop, fieldBottom)
            }
            if (hasHole && overlaps(x, y, x + bw, y + bh, hole, 8 * dp)) continue
            if (overlaps(x, y, x + bw, y + bh, panel, 8 * dp)) continue
            if (nextBtn.visible && overlaps(x, y, x + bw, y + bh, nextBtn.r, 30 * dp)) continue
            chosenX = x; chosenY = y
            break
        }
        skipBtn.set(chosenX, chosenY, bw, bh)
    }

    /** Dialog blokuje HUD i symulację; dopiero drugi, wyraźny wybór zapisuje pominięcie. */
    private fun drawSkipConfirmation(c: Canvas, sl: Float, sr: Float, st: Float, sb: Float) {
        val ui = g.ui
        dimPaint.color = Color.argb(224, 4, 6, 14)
        c.drawRect(0f, 0f, g.width.toFloat(), g.height.toFloat(), dimPaint)
        val pw = min(500 * dp, sr - sl)
        val text = L["tut85_skip_body"]
        val tw = pw - 36 * dp
        if (text != confirmKey || tw != confirmW || confirmScale != ui.textScale) {
            confirmKey = text; confirmW = tw; confirmScale = ui.textScale
            confirmCount = HintFrame.wrapInto(ui, text, tw, 15f, confirmLines)
        }
        val lh = 20 * dp
        val ph = (114 * dp + confirmCount * lh).coerceAtMost(sb - st - 8 * dp)
        val top = (st + (sb - st - ph) / 2).coerceIn(st, max(st, sb - ph))
        panel.set((sl + sr - pw) / 2, top, (sl + sr + pw) / 2, top + ph)
        HintFrame.chrome(c, ui, panel, L["tut85_skip_title"], dp)
        var y = panel.top + 30 * dp
        for (i in 0 until confirmCount) {
            y += lh
            ui.text(c, confirmLines[i]!!, panel.centerX(), y, 15f, Ui.TEXT, Paint.Align.CENTER, false, true)
        }
        // Przyciski z odstępem od złotej lamówki ramki (Ui.PANEL_TRIM_INSET + margines).
        val bw = min(190 * dp, (pw - 64 * dp) / 2)
        val by = panel.bottom - 70 * dp
        nextBtn.visible = true
        skipBtn.visible = true
        nextBtn.set(panel.left + 22 * dp, by, bw, 48 * dp)
        skipBtn.set(panel.right - 22 * dp - bw, by, bw, 48 * dp)
        ui.button(c, nextBtn, 14f)
        ui.button(c, skipBtn, 14f)
    }

    fun draw(c: Canvas) {
        if (finished || battle.over) return
        val ui = g.ui
        val sl = g.insetL + 12 * dp
        val sr = g.width - g.insetR - 12 * dp
        val st = g.insetT + 8 * dp
        val sb = g.height - g.insetB - 8 * dp
        if (skipPending) {
            drawSkipConfirmation(c, sl, sr, st, sb)
            return
        }
        if (!showing) {
            nextBtn.visible = false
            skipBtn.visible = true
            panel.setEmpty()
            layoutSkip(sl, sr, st, sb, false)
            ui.button(c, skipBtn, 13f)
            return
        }
        val hasHole = target()
        val big = hasHole && hole.height() > (sb - st) * 0.35f
        val wantsHand = hasHole && !big && step != S_DOCTRINE &&
            !(step == S_BARRAGE && t.barrageAiming())

        val dimA = if (gating) 170 else if (step == S_DOCTRINE) 110 else 120
        dimPaint.color = Color.argb(dimA, 4, 6, 14)
        path.rewind()
        path.addRect(0f, 0f, g.width.toFloat(), g.height.toFloat(), Path.Direction.CW)
        val rad = 12 * dp
        if (hasHole) path.addRoundRect(hole, rad, rad, Path.Direction.CW)
        c.drawPath(path, dimPaint)

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

        layoutPanel(sl, sr, st, sb, hasHole, wantsHand)
        HintFrame.chrome(c, ui, panel, title, dp)
        val lh = SIZES[selectedFont] * dp * 1.32f
        var y = panel.top + 20 * dp
        if (selectedCounter) {
            ui.textO(c, counter, panel.centerX(), y + 10 * dp, 11f, Ui.DIM, Paint.Align.CENTER, 1.6f)
            y += 14 * dp
        }
        val count = lineCount[selectedRegion][selectedFont]
        val words = lines[selectedRegion][selectedFont]
        // Przy skrajnie małym miejscu nie rysuj opisu na przyciskach ani na celu.
        val saved = c.save()
        c.clipRect(panel.left + 8 * dp, panel.top + 18 * dp,
            panel.right - 8 * dp, panel.bottom - (if (hasNext()) 72 * dp else 5 * dp))
        for (i in 0 until count) {
            y += lh
            ui.text(c, words[i]!!, panel.centerX(), y - lh * 0.25f,
                SIZES[selectedFont], Ui.TEXT, Paint.Align.CENTER, false, true)
        }
        c.restoreToCount(saved)
        nextBtn.visible = hasNext()
        if (nextBtn.visible) {
            val bw = min(160 * dp, panel.width() - 44 * dp)
            nextBtn.enabled = phaseT > 0.5f
            nextBtn.set(if (step == S_DONE) panel.centerX() - bw / 2 else panel.right - 22 * dp - bw,
                panel.bottom - 70 * dp, bw, 48 * dp)
            ui.button(c, nextBtn, 15f)
        }
        skipBtn.visible = step != S_DONE
        if (skipBtn.visible) {
            layoutSkip(sl, sr, st, sb, hasHole)
            ui.button(c, skipBtn, 13f)
        }

        if (selectedHand) {
            val bob = (sin(clock * 5f) + 1f) * 4 * dp
            val down = panel.bottom <= hole.top
            val tipY = if (down) hole.top - 6 * dp - bob else hole.bottom + 6 * dp + bob
            drawHand(c, hole.centerX(), tipY, 46 * dp, down)
        }
        if (hasHole && step == S_BARRAGE && t.barrageAiming()) drawBarrageDrag(c)
    }

    /** Tor palca nad celem; znacznik ostrzału rysuje ekran bitwy. */
    private fun drawBarrageDrag(c: Canvas) {
        val x1 = hole.left + 18f * dp
        val x2 = hole.right - 18f * dp
        if (x2 <= x1) return
        val y = hole.centerY()
        handLine.color = Ui.GOLD
        handLine.strokeWidth = 2.5f * dp
        c.drawLine(x1, y, x2, y, handLine)
        handFill.color = Ui.GOLD
        c.drawCircle(x2, y, 4f * dp, handFill)
        val travel = (sin(clock * 2.5f) + 1f) * 0.5f
        drawHand(c, x1 + (x2 - x1) * travel, y, 27f * dp, true)
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

    /** Każde z czterech położeń ma osobny cache tekstu i skali. */
    private fun prepare(key: String, width: Float, slot: Int) {
        if (key == wrapKey[slot] && width == wrapW[slot] &&
            wrapLang[slot] == L.lang && wrapScale[slot] == g.ui.textScale) return
        wrapKey[slot] = key; wrapW[slot] = width
        wrapLang[slot] = L.lang; wrapScale[slot] = g.ui.textScale
        val s = L[key]
        for (i in SIZES.indices)
            lineCount[slot][i] = HintFrame.wrapInto(g.ui, s, width, SIZES[i], lines[slot][i])
    }

}
