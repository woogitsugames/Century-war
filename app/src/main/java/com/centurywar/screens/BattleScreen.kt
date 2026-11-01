package com.centurywar.screens

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.centurywar.crash.CrashReports
import com.centurywar.engine.Button
import com.centurywar.engine.Game
import com.centurywar.engine.BattleMusicIntensity
import com.centurywar.engine.Icons
import com.centurywar.engine.L
import com.centurywar.engine.Screen
import com.centurywar.engine.Sound
import com.centurywar.engine.TouchEvent
import com.centurywar.engine.TooltipBubble
import com.centurywar.game.FxBuffer
import com.centurywar.game.Body
import com.centurywar.game.EraArt
import com.centurywar.game.Proj
import com.centurywar.engine.Ui
import com.centurywar.game.Battle
import com.centurywar.game.Campaign
import com.centurywar.game.CampaignDifficulty
import com.centurywar.game.BattleController
import com.centurywar.game.BattleConfig
import com.centurywar.game.BattleRenderer
import com.centurywar.game.BattleSnapshot
import com.centurywar.game.ReplayRecord
import com.centurywar.game.RestoredBattle
import com.centurywar.game.Daily
import com.centurywar.game.Defs
import com.centurywar.game.Doctrine
import com.centurywar.game.EndlessCard
import com.centurywar.game.EndlessBosses
import com.centurywar.game.EndlessRarity
import com.centurywar.game.Mode
import com.centurywar.game.Weekly
import com.centurywar.game.Role
import com.centurywar.game.Skirmish
import com.centurywar.game.Weather
import com.centurywar.meta.Achievements
import com.centurywar.meta.AchievementBattleTracker
import com.centurywar.meta.BattleHistory
import com.centurywar.meta.Profile
import com.centurywar.meta.SkirmishSetup
import com.centurywar.meta.Missions
import kotlin.math.max
import kotlin.math.min

/**
 * [restored] != null: bitwa wznowiona ze zrzutu (aplikacja została zabita w tle).
 * Startuje wtedy w pauzie, a [cfg] musi być konfiguracją odtworzonej bitwy.
 */
class BattleScreen(
    g: Game,
    internal val cfg: BattleConfig,
    private val rankedDaily: Boolean = false,
    restored: RestoredBattle? = null,
) : Screen(g) {
    override val crashName get() = "BattleScreen"
    // Podczas walki i samouczka baner zasłaniałby pole i teksty, a na wynikach — zakładki
    // „Wynik/Wykres”. Kolejka czeka do następnego ekranu (menu, kampania).
    override val blocksNotices: Boolean get() = true

    internal val battle = restored?.battle ?: Battle(cfg)
    internal val achievementEvents = AchievementBattleTracker(battle)
    init {
        CrashReports.battleStarted(if (cfg.mode == Mode.CAMPAIGN) cfg.stageIndex + 1 else 0)
        g.music?.setThemeSeed((cfg.seed xor (cfg.seed ushr 32)).toInt())
    }
    internal val commands = battle.commandController ?: BattleController(battle)
    internal val haptics = BattleHaptics(g, battle)
    internal val r = BattleRenderer(g, battle)
    internal val battleSummary = BattleSummarySamples().apply { capture(battle) }
    /** Bitwa wznowiona ze zrzutu nie ma próbek sprzed przerwania: powtórka odbuduje je z logu. */
    private val summaryComplete = restored == null
    internal val resultChart = BattleSummaryChart()
    internal val resultChartArea = RectF()
    internal var showResultChart = false
    private var acc = 0f
    internal var speed = 1
    /** Tempo wyświetlania; nie wchodzi do stanu bitwy ani powtórki. */
    internal var effectiveSpeed = 1f
    private var avgStepNs = 0f
    private var sampledStepFrames = 0
    internal var paused = restored != null
    /** Czas bitwy ostatniego autozapisu. */
    private var lastAutosave = battle.time
    /** Ekran porzucony (restart): nie zapisuj już jego zrzutu. */
    private var closed = false
    private var manualCam = 0f
    /** Stan wyłącznie interfejsu, nie wchodzi do zapisu bitwy. */
    internal var aimingBarrage = false
    internal var aimWorldX = 0f
    internal var aimScreenX = 0f
    internal var aimDownX = 0f
    internal var aimDownY = 0f
    internal var aimStartedInField = false
    internal var aimMoved = false
    private var preparedPlayerEra = -1
    private var preparedEnemyEra = -1
    private var preparedPlayerVariant = -2
    private var preparedEnemyVariant = -2
    internal var showTurrets = false
    /** Indeks roli z otwartym panelem ulepszenia (-1 = zamknięty). */
    internal var showUpgrade = -1
    internal var finished = false
    private var surrendered = false
    internal var resultTime = 0f
    internal var replayBytes: ByteArray? = null
    private var replayId: String? = null


    // wynik
    internal var reward = 0
    internal var rewardText = ""
    internal var totalMedalsGained = 0
    internal var missionsAdvanced = 0
    internal var retainedProgressText = ""
    internal val defeatReasons = arrayOfNulls<String>(2)
    internal var defeatReasonCount = 0
    internal var newRecord = false
    internal var tipKey = ""
    internal var tipText = ""
    internal var resultReasonText1 = ""
    internal var resultReasonText2 = ""
    internal var tipIcon = Icons.BOLT
    internal var weeklyResultLabel = ""
    internal var weeklyResultKey = ""
    internal var resultTitle = ""
    internal var resultTimeText = ""
    internal var resultKillsText = ""
    internal var resultWaveLabel = ""
    internal var resultWaveText = ""
    internal var resultBossesText = ""
    internal var resultScoreText = ""
    internal var resultUnrankedText = ""
    internal var resultTimeLabel = ""
    internal var resultKillsLabel = ""
    internal var resultBossesLabel = ""
    internal var resultWeeklyTiersLabel = ""
    internal var resultScoreLabel = ""
    internal var resultRewardLabel = ""
    internal var resultNewRecordLabel = ""
    internal var resultStars = 0
    internal var resultCustomSkirmish = false
    internal val resultTipLines = arrayOfNulls<String>(16)
    internal var resultTipCount = 0

    internal companion object {
        /** Kolory rzadkości kart Endless: własna paleta, niezależna od liczby epok. */
        val CARD_RARITY = intArrayOf(
            android.graphics.Color.rgb(150, 104, 60), android.graphics.Color.rgb(84, 112, 150),
            android.graphics.Color.rgb(140, 112, 64), android.graphics.Color.rgb(40, 140, 160),
        )
        const val UNIT0 = 0 // 0..3
        const val TURRET = 10
        const val ARMORY = 11
        const val BARRAGE = 12
        const val RALLY = 13
        const val STANCE = 14
        const val SPEED = 15
        const val EVOLVE = 16
        const val PAUSE = 17
        const val RESUME = 20
        const val RESTART = 21
        const val QUIT = 22
        const val CONTINUE = 23
        const val WATCH_REPLAY = 24
        const val PAUSE_VIBRATION = 25
        const val PAUSE_SPEED = 26
        const val DOCTRINE0 = 30 // 30..32
        const val VARIANT0 = 80 // 80..81
        const val SLOT_BUY0 = 40 // 40..43
        const val SLOT_SELL0 = 50 // 50..53
        const val CLOSE = 60
        const val UNIT_UPGRADE0 = 70 // 70..73
        const val UNIT_UPGRADE_BUY = 74
        const val UNIT_UPGRADE_CLOSE = 75
        const val RESULT_STATS_TAB = 201
        const val RESULT_CHART_TAB = 202
        /** Autozapis co tyle sekund czasu bitwy (zapis pliku w tle). */
        const val AUTOSAVE_EVERY = 30f
        /** Szerokość bloku liczebności armii (ikona, liczby, dwukropek) w dp. */
        const val ARMY_COUNTS_W = 90f
        /** Poniżej tej szerokości (dp) nazwa epoki wroga przechodzi do drugiego rzędu. */
        const val ENEMY_ERA_MIN_W = 60f
    }

    /** Role.values() alokuje nową tablicę przy każdym wywołaniu, więc trzymamy kopię. */
    internal val roles = Role.values()
    /** Klucze opisów są składane raz, nie przy każdym renderowaniu modala. */
    private val doctrineDescKeys = Doctrine.values().map { it.key + "_d" }.toTypedArray()
    private val cardDescKeys = EndlessCard.values().map { it.textKey + "_d" }.toTypedArray()

    private fun choiceDescriptionKey(card: EndlessCard?, doctrine: Doctrine?): String {
        // Karty Endless zachowują pierwotną siłę pierwszego stosu.
        when (card) {
            EndlessCard.BULWARK -> return "d_bulwark_endless_d"
            EndlessCard.FURY -> return "d_fury_endless_d"
            EndlessCard.PLUNDER -> return "d_plunder_endless_d"
            else -> Unit
        }
        if ((card?.doctrine ?: doctrine) == Doctrine.VIGILANCE && !battle.fog)
            return "d_vigilance_clear_d"
        return if (card != null) cardDescKeys[card.ordinal]
            else doctrineDescKeys[doctrine!!.ordinal]
    }
    internal val unitBtn = Array(4) { i -> btn(UNIT0 + i) }
    internal val unitUpgrade = Array(4) { i -> btn(UNIT_UPGRADE0 + i) }
    internal val upgradeBuy = btn(UNIT_UPGRADE_BUY)
    internal val upgradeClose = btn(UNIT_UPGRADE_CLOSE)
    internal val turretBtn = btn(TURRET)
    internal val armoryBtn = btn(ARMORY)
    internal val barrageBtn = btn(BARRAGE)
    internal val rallyBtn = btn(RALLY)
    internal val stanceBtn = btn(STANCE)
    internal val speedBtn = btn(SPEED)
    internal val evolveBtn = btn(EVOLVE)
    internal val pauseBtn = btn(PAUSE)
    internal val pauseMenu = listOf(btn(RESUME), btn(RESTART), btn(QUIT))
    internal val pauseVibrationBtn = btn(PAUSE_VIBRATION)
    internal val pauseSpeedBtn = btn(PAUSE_SPEED)
    private val pauseSfxRect = RectF()
    private val pauseMusicRect = RectF()
    private var pauseWide = false
    private var pauseSliderPointer = -1
    private var pauseSliderMusic = false
    internal val continueBtn = btn(CONTINUE)
    internal val restartEnd = btn(RESTART + 100)
    internal val replayEnd = btn(WATCH_REPLAY)
    internal val resultStatsTab = btn(RESULT_STATS_TAB)
    internal val resultChartTab = btn(RESULT_CHART_TAB)
    internal val doctrineBtn = Array(3) { btn(DOCTRINE0 + it) }
    internal val variantBtn = Array(2) { btn(VARIANT0 + it) }
    internal val slotBuy = Array(Defs.MAX_TURRET_SLOTS) { btn(SLOT_BUY0 + it) }
    internal val slotSell = Array(Defs.MAX_TURRET_SLOTS) { btn(SLOT_SELL0 + it) }
    internal val closeBtn = btn(CLOSE)
    /** Niewidoczny obszar nad ikonami kolejki: opis bez akcji przy krótkim tapnięciu. */
    internal val queueInfoBtn = btn(90)
    private val introTooltip = TooltipBubble()
    private val missingXpTooltip = TooltipBubble()
    private var missingXpRemaining = 0f
    private var introDelay = 0f
    private var introRemaining = 0f
    private var introChecked = false

    internal val topBar = RectF()
    internal val mini = RectF()
    internal val goldRect = RectF()
    internal val xpRect = RectF()
    internal val fieldRect = RectF()
    internal val weatherRect = RectF()
    /** Obszar liczebności armii w drugim rzędzie paska (pusty w układzie szerokim). */
    internal val armyRect = RectF()
    internal val panel = RectF()
    internal val upgradePanel = RectF()
    internal val upgradeRoleKeys = arrayOf("unit_upgrade_melee", "unit_upgrade_ranged", "unit_upgrade_heavy", "unit_upgrade_elite")
    internal var upgradeRoleShown = -1
    internal var upgradeTierShown = -1
    internal var upgradeTierText = ""
    private val variantDescLines = Array(2) { arrayOfNulls<String>(16) }
    private val variantDescCount = IntArray(2)
    private val variantDescKey = IntArray(2) { -1 }
    private var variantDescWidth = -1f
    private var variantDescScale = -1f
    private var variantDescLanguage = -1
    private val choiceDescLines = Array(3) { arrayOfNulls<String>(16) }
    private val choiceDescCount = IntArray(3)
    private val choiceDescKey = IntArray(3) { -1 }
    private var choiceDescWidth = -1f
    private var choiceDescScale = -1f
    private var choiceDescLanguage = -1
    internal var hudH = 0f
    internal var twoRowHud = false
    internal var threeRowHud = false
    internal var topH = 0f
    internal var compactTop = false
    internal var enemyEraShown = -1
    internal var enemyEraLanguage = -1
    internal var enemyEraText = ""
    // Komponenty powstają raz; bez instancjonowania w render()/update().
    private val topBarView = BattleTopBar(this)
    private val bottomHudView = BattleBottomHud(this)
    private val resultsView = BattleResultsPanel(this)
    internal val upgradesView = BattleUpgradePanel(this)
    private val barrageView = BarrageAiming(this)

    init {
        // Obie strony zamawiają swoje pierwsze próbki przed pierwszym krokiem bitwy.
        BattleSfx.prepareEra(g, battle.player.era, battle.player.eraVariant)
        BattleSfx.prepareEra(g, battle.enemy.era, battle.enemy.eraVariant)
        preparedPlayerEra = battle.player.era
        preparedEnemyEra = battle.enemy.era
        preparedPlayerVariant = battle.player.eraVariant
        preparedEnemyVariant = battle.enemy.eraVariant
        armoryBtn.icon = { c, area ->
            // Podpis Zbrojowni pozostaje pod ikoną; liczba ulepszeń jest w rogu.
            Icons.draw(c, Icons.ANVIL, area.centerX(), area.top + area.height() * 0.24f,
                area.height() * 0.34f, dp = dp)
            val text = L["battle84_armory_short"]
            ui.textO(c, text, area.centerX(), area.bottom - 22 * dp,
                ui.fit(text, 10f, area.width() - 8 * dp), Ui.TEXT, Paint.Align.CENTER, 1.3f)
            if (armoryBtn.label.isNotEmpty()) ui.cornerTag(c, armoryBtn.label,
                area.right - 4 * dp, area.top + 4 * dp)
        }
        for (i in 0..3) {
            val role = roles[i]
            unitBtn[i].icon = { c, rr ->
                val def = battle.player.unit(role)
                r.drawUnitIcon(c, def, rr.centerX(), rr.bottom - 18 * dp, rr.height() - 26 * dp,
                    Ui.PLAYER, battle.player.unitTier[role.ordinal])
            }
        }
        // Strzałka ma pierwszeństwo przed większym przyciskiem jednostki pod spodem.
        for (i in 3 downTo 0) {
            buttons.remove(unitUpgrade[i])
            buttons.add(0, unitUpgrade[i])
        }
        r.camX = restored?.camX ?: -120f
    }

    /** Interaktywny samouczek (tylko etap 1 kampanii, dopóki gracz go nie ukończy/pominie). */
    internal val tut: TutorialOverlay? =
        if (restored == null && cfg.tutorial && cfg.mode == Mode.CAMPAIGN && !g.profile.tutorialDone) TutorialOverlay(g, battle, commands, TutorialOverlay.Targets(
            unitBtn, evolveBtn, turretBtn, armoryBtn, stanceBtn, rallyBtn, barrageBtn, slotBuy, doctrineBtn,
            arrayOf(unitBtn[0], unitBtn[1], unitBtn[2], unitBtn[3], turretBtn, armoryBtn, barrageBtn, rallyBtn, stanceBtn, speedBtn, evolveBtn),
            goldRect, xpRect, mini, fieldRect, { showTurrets }, { showTurrets = false },
            { aimingBarrage }, { x -> r.screenX(x) }, { r.groundY }, { r.k },
        )) else null

    /** Dymek nie wykonuje komend i nie modyfikuje tempa symulacji. */
    private val hints = HintOverlay(g, battle, HintOverlay.Targets(
        unitUpgrade, armoryBtn, rallyBtn, stanceBtn, evolveBtn, turretBtn,
        goldRect, xpRect, mini, fieldRect, weatherRect, variantBtn,
    ), rankedDaily)

    /** Plansza nie zapisuje komend i nie wykonuje kroków symulacji. */
    private var eraIntro: EraIntroOverlay? = null
    private var lastIntroEra = battle.player.era

    private fun maybeShowEraIntro() {
        val current = battle.player.era
        if (current == lastIntroEra) return
        lastIntroEra = current
        eraIntro = EraIntroOverlay.showIfFirst(g, current, battle, r)
        eraIntro?.layout()
    }

    init {
        // OK oraz przyciski samouczka mają pierwszeństwo przed HUD.
        tut?.let { buttons.add(0, it.nextBtn); buttons.add(0, it.skipBtn) }
        buttons.add(0, hints.okBtn)
    }

    override fun layout() {
        val pad = 6 * dp
        val l = g.insetL + pad
        val rr = w - g.insetR - pad
        val avail = rr - l
        compactTop = avail < 790 * dp
        topH = if (compactTop) 108 * dp else 58 * dp
        bottomHudView.layout(l, rr, avail, pad)

        topBarView.layout(l, rr)

        if (g.profile.leftHandedHud) {
            // Lustrzane są wyłącznie kontrolki; kamera i pole bitwy zachowują kierunek.
            for (b in unitBtn) mirrorHudButton(b, l, rr)
            for (b in actionBtns) mirrorHudButton(b, l, rr)
            for (b in unitUpgrade) {
                mirrorHudButton(b, l, rr)
                b.hitOffsetX = -b.hitOffsetX
            }
            mirrorHudButton(queueInfoBtn, l, rr)
            queueInfoBtn.hitOffsetX = -queueInfoBtn.hitOffsetX
            mirrorHudButton(evolveBtn, l, rr)
            mirrorHudButton(pauseBtn, l, rr)
            mirrorHudRect(goldRect, l, rr)
            mirrorHudRect(xpRect, l, rr)
            mirrorHudRect(mini, l, rr)
            mirrorHudRect(weatherRect, l, rr)
        }
        topBarView.layoutArmy()

        r.layout(w, h, topH + g.insetT + 10 * dp, hudH)

        layoutPause()
        pauseModifierWidth = -1f
        resultsView.layoutResults()
        val cw = min(230 * dp, (w - g.insetL - g.insetR - 60 * dp) / 3)
        val chH = min(218 * dp, (h - g.insetT - g.insetB) * 0.48f)
        for (i in 0..2) doctrineBtn[i].set(w / 2 - cw * 1.5f - 14 * dp + i * (cw + 14 * dp), h * 0.5f - chH * 0.4f, cw, chH)
        val freeW = w - g.insetL - g.insetR - 50 * dp
        val vcw = min(285 * dp, (freeW - 12 * dp) / 2f)
        val vch = min(305 * dp, (h - g.insetT - g.insetB) * 0.77f)
        for (i in 0..1) variantBtn[i].set(w / 2 - vcw - 6 * dp + i * (vcw + 12 * dp),
            (h + g.insetT - g.insetB - vch) / 2f, vcw, vch)
        upgradesView.layoutTurretPanel()
        upgradesView.layoutUpgradePanel()
        // Szerokość paneli zmienia łamanie opisów wariantów i kart.
        variantDescWidth = -1f
        choiceDescWidth = -1f
        eraIntro?.layout()
    }

    /** Odbicie na obszarze bez wycięć ekranu, bez nowych obiektów. */
    private fun mirrorHudRect(rect: RectF, left: Float, right: Float) {
        if (rect.width() <= 0f) return
        val oldLeft = rect.left
        val oldRight = rect.right
        rect.set(left + right - oldRight, rect.top, left + right - oldLeft, rect.bottom)
    }

    private fun mirrorHudButton(button: Button, left: Float, right: Float) {
        mirrorHudRect(button.r, left, right)
    }

    /**
     * Pauza liczona z dostępnej szerokości i wysokości. Poziomo (także 640×360 dp na 16:9)
     * dwie kolumny: dane bitwy i szybkie ustawienia; pionowo jedna kolumna. Odstępy wierszy
     * wynikają z wysokości panelu, więc nic nie wchodzi na rząd przycisków.
     */
    private fun layoutPause() {
        val freeW = w - g.insetL - g.insetR - 24 * dp
        val freeH = h - g.insetT - g.insetB - 20 * dp
        pauseWide = freeW >= 560 * dp || freeW > freeH
        val pw = min(if (pauseWide) 780 * dp else 520 * dp, freeW)
        val ph = min(if (pauseWide) 354 * dp else 560 * dp, freeH)
        val cx = (g.insetL + w - g.insetR) * 0.5f
        val cy = (g.insetT + h - g.insetB) * 0.5f
        pausePanel.set(cx - pw / 2f, cy - ph / 2f, cx + pw / 2f, cy + ph / 2f)

        val buttonH = if (ph < 380 * dp) 38 * dp else 42 * dp
        val buttonTop = pausePanel.bottom - 12 * dp - buttonH
        val contentTop = pausePanel.top + 48 * dp
        val contentBottom = buttonTop - 10 * dp
        val avail = contentBottom - contentTop
        val info: Float // wysokość bloku danych bitwy
        if (pauseWide) {
            pauseStep = ((avail - 18 * dp) / 9.81f).coerceIn(12 * dp, 24 * dp)
            pauseInfoW = pw * 0.53f - 45 * dp
            pauseCtrlX = pausePanel.left + pw * 0.56f
            pauseQuickY = contentTop + 14 * dp
            info = 0f
        } else {
            pauseStep = ((avail - 58 * dp) / 18.61f).coerceIn(11 * dp, 22 * dp)
            pauseInfoW = pw - 44 * dp
            pauseCtrlX = pausePanel.left + 20 * dp
            info = 18 * dp + 9.81f * pauseStep
            pauseQuickY = contentTop + info + 16 * dp + 14 * dp
        }
        pauseInfoX = pausePanel.left + 22 * dp
        pauseSummaryY = contentTop + 14 * dp
        pauseLineY0 = pauseSummaryY + pauseStep * 1.25f
        pauseModY = pauseLineY0 + pauseStep * 5.95f
        pauseModStep = pauseStep * 0.78f
        pauseOpponentY = pauseModY + pauseModStep * 2f + pauseStep * 1.05f
        pauseStatSize = (pauseStep / dp * 0.62f).coerceIn(10f, 13f)
        pauseSepTop = contentTop + 3 * dp
        pauseSepBottom = contentBottom

        val controlsRight = pausePanel.right - 20 * dp
        val rowsTop = pauseQuickY + 10 * dp
        val rowStep = min(if (pauseWide) 54 * dp else 48 * dp, (contentBottom - rowsTop) / 4f)
        val ctrlH = max(18 * dp, min(36 * dp, rowStep - 8 * dp))
        pauseCtrlLabelW = if (pauseWide) 85 * dp else 103 * dp
        val sliderX = pauseCtrlX + pauseCtrlLabelW + 5 * dp
        fun rowTop(i: Int) = rowsTop + rowStep * (i + 0.5f) - ctrlH / 2f
        pauseSfxRect.set(sliderX, rowTop(0), controlsRight, rowTop(0) + ctrlH)
        pauseMusicRect.set(sliderX, rowTop(1), controlsRight, rowTop(1) + ctrlH)
        pauseVibrationBtn.set(controlsRight - 96 * dp, rowTop(2), 96 * dp, ctrlH)
        pauseSpeedBtn.set(controlsRight - 96 * dp, rowTop(3), 96 * dp, ctrlH)
        pauseVibrationBtn.hitSlop = (48 * dp - ctrlH).coerceAtLeast(0f) / 2f
        pauseSpeedBtn.hitSlop = pauseVibrationBtn.hitSlop

        val buttonGap = 12 * dp
        val buttonW = (pw - 36 * dp - buttonGap * 2) / 3f
        for (i in pauseMenu.indices) {
            pauseMenu[i].set(pausePanel.left + 18 * dp + i * (buttonW + buttonGap),
                buttonTop, buttonW, buttonH)
            pauseMenu[i].hitSlop = (48 * dp - buttonH).coerceAtLeast(0f) / 2f
        }
    }





    // ---------------------------------------------------------------- logika

    override fun onLanguageChanged() {
        // Reset cache'u napisów zależnych od języka (w tym otwartych paneli).
        waveShown = -1
        nextThemeShown = -1
        bossShownKind = -1
        siegeKey = -1
        upgradeRoleShown = -1
        upgradeTierLanguage = -1
        turretHeadingEra = -1
        slotLabelLanguage = -1
        pauseLanguage = -1
        variantDescLanguage = -1
        choiceDescLanguage = -1
        enemyEraLanguage = -1
        resultChart.refreshLanguage()
        if (missingXpRemaining > 0f && battle.player.era < cfg.maxEra) {
            val missing = kotlin.math.ceil(
                (Defs.evolveXp[battle.player.era] - battle.player.xp).coerceAtLeast(0f).toDouble()).toInt()
            missingXpTooltip.set(L["evolve"], L.f("battle84_missing_xp", missing))
        }
        if (introRemaining > 0f) introTooltip.set(L["hint_title"], L["tip53_intro"])
        tut?.onLanguageChanged()
        super.onLanguageChanged()
        eraIntro?.onLanguageChanged()
        if (finished) resultsView.refreshResultTexts()
    }

    private var frameDt = 0f

    override fun update(dt: Float) {
        frameDt = dt
        eraIntro?.update(dt)
        maybeShowEraIntro()
        if (aimingBarrage && (paused || finished || battle.over ||
                battle.pendingChoice != null || battle.pendingCards != null ||
                showUpgrade >= 0 || !battle.abilityReady(battle.player, 0))) {
            aimingBarrage = false
        }
        val tutScale = tut?.timeScale ?: 1f
        if (!paused && !finished && eraIntro?.active != true) {
            tut?.update(dt)
            if (battle.pendingChoice == null && battle.pendingCards == null && battle.pendingVariant == null) {
                // Zamiast porzucać czas przy ×3/×5, ograniczamy tempo do budżetu CPU.
                val budgetMs = (g.cpuBudgetMs * 0.75f - g.frameMetrics.avgRenderMs).coerceAtLeast(1.5f)
                val availableSteps = if (sampledStepFrames < 30 || avgStepNs <= 0f) 20f
                    else (budgetMs * 1_000_000f / avgStepNs).coerceIn(1f, 20f)
                val allowedSpeed = availableSteps * Battle.STEP / dt.coerceAtLeast(0.001f)
                effectiveSpeed = min(speed.toFloat(), allowedSpeed.coerceAtLeast(1f))
                acc += dt * effectiveSpeed * tutScale
                var steps = 0
                val startedNs = System.nanoTime()
                while (acc >= Battle.STEP && steps < 20) {
                    battle.step()
                    battleSummary.capture(battle)
                    achievementEvents.afterStep()
                    acc -= Battle.STEP
                    steps++
                    // Nie wykonuj dalszych kroków po pierwszym awansie w tej klatce.
                    maybeShowEraIntro()
                    if (eraIntro?.active == true) break
                }
                if (steps > 0) {
                    val sample = (System.nanoTime() - startedNs).toFloat() / steps
                    avgStepNs = if (sampledStepFrames == 0) sample else avgStepNs * 0.9f + sample * 0.1f
                    sampledStepFrames++
                }
                var discarded = 0
                if (acc >= Battle.STEP) {
                    discarded = (acc / Battle.STEP).toInt()
                    acc -= discarded * Battle.STEP
                }
                g.reportSimSteps(steps, discarded)
                if (steps > 0) haptics.onSimulationStep()
            }
            if (battle.over) onFinished()
            else if (battle.time - lastAutosave >= AUTOSAVE_EVERY) {
                // Bajty powstają tu, na wątku gry (spójny stan); plik zapisuje wątek w tle.
                lastAutosave = battle.time
                g.snapshots?.saveAsync(snapshotBytes())
            }
        }
        maybeShowEraIntro()
        if (paused || finished || eraIntro?.active == true || battle.pendingVariant != null || battle.pendingChoice != null || battle.pendingCards != null) effectiveSpeed = speed.toFloat()
        // Awans rangi jest zdarzeniem interfejsu, nie modyfikuje bitwy ani powtórki.
        val newRank = battle.player.rank
        if (newRank > lastSfxRank) g.sfx(Sound.RANK_UP, 0.62f, 250, priority = 2)
        lastSfxRank = newRank
        // Przy zmianie epoki zamawiamy nowe próbki przed pierwszym ostrzałem.
        if (preparedPlayerEra != battle.player.era || preparedPlayerVariant != battle.player.eraVariant) {
            BattleSfx.prepareEra(g, battle.player.era, battle.player.eraVariant)
            preparedPlayerEra = battle.player.era
            preparedPlayerVariant = battle.player.eraVariant
        }
        if (preparedEnemyEra != battle.enemy.era || preparedEnemyVariant != battle.enemy.eraVariant) {
            BattleSfx.prepareEra(g, battle.enemy.era, battle.enemy.eraVariant)
            preparedEnemyEra = battle.enemy.era
            preparedEnemyVariant = battle.enemy.eraVariant
        }
        // Jeden parametr 0..1 steruje spokój / starcie / zagrożenie bazy.
        val music = g.music
        if (music != null) {
            music.setEra(battle.player.era)
            var tension = 0f
            if (!paused && !finished && !battle.over &&
                battle.pendingChoice == null && battle.pendingCards == null &&
                battle.pendingVariant == null) {
                var allyFront = Float.NEGATIVE_INFINITY
                var enemyFront = Float.POSITIVE_INFINITY
                for (u in battle.units) {
                    if (!u.alive) continue
                    if (u.side == 0) {
                        if (u.x > allyFront) allyFront = u.x
                    } else if (u.x < enemyFront) enemyFront = u.x
                }
                val hp = battle.player
                val hpRatio = if (hp.baseMaxHp > 0f) hp.baseHp / hp.baseMaxHp else 1f
                tension = BattleMusicIntensity.level(
                    allyFront, enemyFront, hp.baseFront, Defs.LANE, hpRatio
                )
            }
            music.setIntensity(tension)
        }
        BattleSfx.play(g, battle, r.camX, r.worldVisible)
        if (finished) resultTime += dt
        if (manualCam > 0f) manualCam -= dt else if (!finished) r.follow(dt)
        bottomHudView.refreshButtons()
        if (missingXpRemaining > 0f) {
            if (finished || paused || battle.canEvolve(battle.player)) missingXpRemaining = 0f
            else missingXpRemaining = (missingXpRemaining - dt).coerceAtLeast(0f)
        }
        // Nie zużywamy czasu ani bitu wskazówki, gdy zasłania ją inna podpowiedź.
        if (!paused && !finished && hints.activeId < 0 && !isTooltipHeld &&
            showUpgrade < 0 && !showTurrets &&
            battle.pendingVariant == null && battle.pendingChoice == null &&
            battle.pendingCards == null && tut == null && g.profile.hintsEnabled) {
            if (introRemaining > 0f) introRemaining = (introRemaining - dt).coerceAtLeast(0f)
            if (!introChecked) {
                introDelay += dt
                if (introDelay >= 0.12f) {
                    introChecked = true
                    if (g.profile.markHintSeen(Profile.HINT_LONG_PRESS)) {
                        introTooltip.set(L["hint_title"], L["tip53_intro"])
                        introRemaining = 6f
                    }
                }
            }
        }
        val criticalDaily = rankedDaily && (battle.player.baseHp <= battle.player.baseMaxHp * 0.18f ||
            battle.enemy.baseHp <= battle.enemy.baseMaxHp * 0.18f)
        hints.update(dt, finished || paused || eraIntro?.active == true || (tut != null && !tut.finished) ||
            battle.pendingChoice != null || battle.pendingCards != null ||
            showTurrets || showUpgrade >= 0 || criticalDaily || introRemaining > 0f)

    }

    internal var turretSlotsLaid = -1


    /** Teksty kosztów są cache'owane, żeby nie tworzyć nowych Stringów w każdej klatce. */
    private val costCache = IntArray(6) { -1 }
    private val costText = Array(6) { "" }
    internal var armoryLv = -1
    internal var turretHeadingEra = -1
    internal var turretHeadingVariant = -1
    internal var turretHeadingLanguage = -1
    internal var turretHeading = ""
    internal val slotLabels = Array(Defs.MAX_TURRET_SLOTS) { "" }
    internal val slotNumbers = Array(Defs.MAX_TURRET_SLOTS) { (it + 1).toString() }
    private var lastSfxRank = battle.player.rank
    internal var upgradeTierLanguage = -1
    internal var shownRank = -1
    internal var shownRankLanguage = -1
    internal var shownRankText = ""
    internal val slotBuyKey = IntArray(Defs.MAX_TURRET_SLOTS) { Int.MIN_VALUE }
    internal val slotSellKey = IntArray(Defs.MAX_TURRET_SLOTS) { Int.MIN_VALUE }
    internal var slotLabelLanguage = -1
    /** Indeks = mnożnik prędkości (1, 3, 5; zob. onClick SPEED). */
    internal val speedLabels = arrayOf("", "×1", "×2", "×3", "×4", "×5")
    internal val limitedSpeedLabels = Array(6) { target ->
        Array(6) { actual -> "×$target → ×$actual" }
    }

    internal fun costStr(slot: Int, v: Int): String {
        if (costCache[slot] != v) { costCache[slot] = v; costText[slot] = v.toString() }
        return costText[slot]
    }


    /** Opisy wyliczamy tylko przy otwarciu dymka, nie podczas renderowania. */
    override fun onLongPress(b: Button): Boolean {
        if (paused || finished || (tut != null && !tut.finished)) return false
        val p = battle.player
        val title: String
        val body: String
        when (b.id) {
            in UNIT0..UNIT0 + 3 -> {
                val i = b.id - UNIT0
                val def = p.unit(roles[i])
                title = L[def.tierNameKeys[(p.unitTier[i] - 1).coerceIn(0, 2)]]
                body = if (i == 3) L.f("tip53_unit_elite", def.cost, p.queueSize)
                    else L.f("tip53_unit", L["bh_role_" + i],
                        L["bh_role_" + when (i) { 0 -> 1; 1 -> 2; else -> 0 }],
                        def.cost, p.queueSize)
            }
            in UNIT_UPGRADE0..UNIT_UPGRADE0 + 3, UNIT_UPGRADE_BUY -> {
                val i = if (b.id == UNIT_UPGRADE_BUY) showUpgrade else b.id - UNIT_UPGRADE0
                if (i !in 0..3) return false
                title = L["unit_upgrade_title"]
                val cost = battle.unitUpgradeCost(p, roles[i])
                body = if (cost < 0) L["unit_upgrade_max"]
                    else L.f("tip53_upgrade", p.unitTier[i], Defs.MAX_UNIT_TIER, cost)
            }
            TURRET -> { title = L["turrets"]; body = L.f("tip53_turrets", p.slots) }
            ARMORY -> {
                title = L["armory"]
                body = L.f("tip53_armory", p.armory, battle.armoryCost(p))
            }
            BARRAGE -> {
                title = L["tip53_barrage_title"]
                body = L.f("tip53_barrage", kotlin.math.ceil(p.abilityCd[0].toDouble()).toInt())
            }
            RALLY -> {
                title = L["rally"]
                body = L.f("tip53_rally", kotlin.math.ceil(p.abilityCd[1].toDouble()).toInt())
            }
            STANCE -> {
                title = L[if (p.hold) "hold" else "advance"]
                body = L[if (p.hold) "tip53_hold" else "tip53_advance"]
            }
            SPEED -> { title = L["tip53_speed_title"]; body = L.f("tip53_speed", speed) }
            EVOLVE -> {
                title = L[if (p.era >= cfg.maxEra) "rank" else "evolve"].let {
                    if (p.era >= cfg.maxEra) L.f("rank", p.rank) else it
                }
                body = when {
                    p.era >= cfg.maxEra -> L["tip53_veteran"]
                    !battle.canEvolve(p) -> L.f("battle84_missing_xp",
                        kotlin.math.ceil((Defs.evolveXp[p.era] - p.xp).coerceAtLeast(0f).toDouble()).toInt())
                    else -> L.f("tip53_evolve", Defs.evolveXp[p.era])
                }
            }
            PAUSE -> { title = L["paused"]; body = L["tip53_pause"] }
            90 -> { title = L["tip53_queue_title"]; body = L.f("tip53_queue", p.queueSize) }
            in SLOT_BUY0 until SLOT_BUY0 + Defs.MAX_TURRET_SLOTS -> {
                val i = b.id - SLOT_BUY0
                title = L[if (p.turretEra[i] < 0) "buy" else "upgrade"]
                val cost = if (p.turretEra[i] < 0) p.turretCost() else battle.upgradeCost(p, i)
                body = if (cost < 0) L["unit_upgrade_max"] else L.f(
                    if (p.turretEra[i] < 0) "tip53_slot_buy" else "tip53_slot_upgrade", cost)
            }
            in SLOT_SELL0 until SLOT_SELL0 + Defs.MAX_TURRET_SLOTS -> {
                val i = b.id - SLOT_SELL0
                if (p.turretEra[i] < 0) return false
                title = L["sell"]
                body = L.f("tip53_slot_sell", p.turretCost(p.turretEra[i], p.turretVariant[i]) / 2)
            }
            CLOSE, UNIT_UPGRADE_CLOSE -> {
                title = L["back"]
                body = L["tip53_close"]
            }
            in DOCTRINE0..DOCTRINE0 + 2 -> {
                val i = b.id - DOCTRINE0
                val card = battle.pendingCards?.get(i)
                val doctrine = battle.pendingChoice?.get(i)
                if (card == null && doctrine == null) return false
                title = L[card?.textKey ?: doctrine!!.key]
                body = L[choiceDescriptionKey(card, doctrine)]
            }
            in VARIANT0..VARIANT0 + 1 -> {
                val era = battle.pendingVariant ?: return false
                val variant = Defs.spec(era).variants[b.id - VARIANT0]
                title = L[variant.nameKey]
                body = L[variant.descKey]
            }
            else -> return false
        }
        b.tooltipTitle = title
        b.tooltipBody = body
        if (!super.onLongPress(b)) return false
        introRemaining = 0f
        introChecked = true
        g.profile.markHintSeen(Profile.HINT_LONG_PRESS)
        return true
    }

    override fun onClick(b: Button) {
        if (eraIntro?.active == true) return
        if (aimingBarrage && b.id != BARRAGE) aimingBarrage = false
        val p = battle.player
        if (hints.onClick(b)) return
        val t = tut
        if (t != null && (t.onClick(b) || !t.allows(b))) return
        hints.onAction()
        when (b.id) {
            in UNIT0..UNIT0 + 3 -> commands.queue(roles[b.id - UNIT0])
            in UNIT_UPGRADE0..UNIT_UPGRADE0 + 3 -> {
                showTurrets = false
                showUpgrade = b.id - UNIT_UPGRADE0
            }
            UNIT_UPGRADE_BUY -> if (showUpgrade in 0..3 && commands.upgradeUnit(roles[showUpgrade])) haptics.onUpgrade()
            UNIT_UPGRADE_CLOSE -> showUpgrade = -1
            TURRET -> showTurrets = !showTurrets
            CLOSE -> showTurrets = false
            ARMORY -> commands.armory()
            BARRAGE -> barrageView.onButton()
            RALLY -> commands.rally()
            STANCE -> commands.stance(!p.hold)
            SPEED, PAUSE_SPEED -> speed = when (speed) { 1 -> 3; 3 -> 5; else -> 1 }
            PAUSE_VIBRATION -> {
                g.profile.vibrationLevel = (g.profile.vibrationLevel + 1) % 3
                g.profile.save()
            }
            EVOLVE -> if (commands.evolve()) {
                missingXpRemaining = 0f
                haptics.onEvolve()
                maybeShowEraIntro()
            }
            PAUSE -> paused = true
            RESUME -> paused = false
            RESTART, RESTART + 100 -> {
                closed = true
                g.snapshots?.deleteAsync()
                g.setScreen(BattleScreen(g, cfg.copyWithSeed(), false))
            }
            QUIT -> { paused = false; surrendered = commands.surrender() }
            CONTINUE -> g.setScreen(nextScreen())
            WATCH_REPLAY -> replayBytes?.let {
                g.push(if (summaryComplete) ReplayScreen(g, replayId, it, battleSummary)
                    else ReplayScreen(g, replayId, it))
            }
            RESULT_STATS_TAB -> showResultChart = false
            RESULT_CHART_TAB -> showResultChart = true
            in DOCTRINE0..DOCTRINE0 + 2 -> {
                val cards = battle.pendingCards
                if (cards != null) {
                    val rarity = cards[b.id - DOCTRINE0].rarity.ordinal
                    val id = when (rarity) {
                        0 -> Sound.CARD_COMMON
                        1 -> Sound.CARD_RARE
                        2 -> Sound.CARD_EPIC
                        else -> Sound.CARD_LEGENDARY
                    }
                    commands.card(b.id - DOCTRINE0)
                    g.sfx(id, 0.48f, 0)
                } else commands.doctrine(b.id - DOCTRINE0)
            }
            in VARIANT0..VARIANT0 + 1 -> if (commands.variant(b.id - VARIANT0)) {
                haptics.onEvolve()
                BattleSfx.prepareEra(g, p.era, p.eraVariant)
                preparedPlayerEra = p.era
                preparedPlayerVariant = p.eraVariant
            }
            in SLOT_BUY0 until SLOT_BUY0 + Defs.MAX_TURRET_SLOTS -> {
                val i = b.id - SLOT_BUY0
                if (p.turretEra[i] < 0) commands.buyTurret(i) else commands.upgradeTurret(i)
            }
            in SLOT_SELL0 until SLOT_SELL0 + Defs.MAX_TURRET_SLOTS -> commands.sellTurret(b.id - SLOT_SELL0)
        }
    }

    internal fun BattleConfig.copyWithSeed() = BattleConfig(
        mode, if (mode == Mode.DAILY || weekly >= 0L) seed else seed + 1, aiPower,
        startEra, maxEra, modifiers, playerBonuses, stageIndex,
        tutorial && !g.profile.tutorialDone, aiPersonality, variantsEnabled, weather, weekly, weeklyCard,
        dailyEvent,
    )

    private fun nextScreen(): Screen = when (cfg.mode) {
        Mode.CAMPAIGN -> CampaignScreen(g)
        Mode.ENDLESS -> if (cfg.weekly >= 0L) ModeScreen(g, Mode.DAILY, true) else ModeScreen(g, Mode.ENDLESS)
        Mode.DAILY -> ModeScreen(g, cfg.mode)
        Mode.SKIRMISH -> SkirmishSetupScreen(g)
    }

    private fun setPauseVolume(x: Float) {
        if (pauseSliderMusic) {
            g.profile.musicVolume = ui.volumeFromX(pauseMusicRect, x)
            g.applyMusicVolume()
        } else {
            g.profile.sfxVolume = ui.volumeFromX(pauseSfxRect, x)
            g.applySfxVolume()
        }
    }

    override fun onTouch(e: TouchEvent): Boolean {
        if (eraIntro?.active == true) {
            if (e.action == TouchEvent.UP) eraIntro?.dismiss()
            return true
        }
        if (pauseSliderPointer >= 0) {
            if (e.action == TouchEvent.CANCEL) {
                pauseSliderPointer = -1
                g.profile.save()
                return true
            }
            if (e.pointer != pauseSliderPointer) return true
            if (e.action == TouchEvent.MOVE || e.action == TouchEvent.UP) {
                setPauseVolume(e.x)
                if (e.action == TouchEvent.UP) {
                    pauseSliderPointer = -1
                    g.profile.save()
                }
            }
            return true
        }
        if (paused && !finished && e.action == TouchEvent.DOWN) {
            // Strefy suwaków nie obejmują przycisków, także przy multi-touch.
            val rect = when {
                pauseSfxRect.contains(e.x, e.y) -> pauseSfxRect
                pauseMusicRect.contains(e.x, e.y) -> pauseMusicRect
                else -> null
            }
            if (rect != null) {
                pauseSliderPointer = e.pointer
                pauseSliderMusic = rect === pauseMusicRect
                setPauseVolume(e.x)
                return true
            }
        }
        return barrageView.onTouch(e)
    }



    override fun onDrag(dx: Float, dy: Float) {
        if (finished || paused) return
        // Podczas celowania palec przesuwa celownik, a nie kamerę.
        if (aimingBarrage && aimStartedInField) return
        r.camX -= dx / r.k
        r.clampCam()
        if (aimingBarrage) aimWorldX = barrageView.snapBarrageTarget(r.worldX(aimScreenX))
        manualCam = 4f
        tut?.onCamera()
    }

    override fun onTap(x: Float, y: Float) {
        // Krótki tap wyłączonego awansu nie wydaje komendy i podaje brakujące XP.
        if (!paused && !finished && !aimingBarrage && evolveBtn.visible &&
            !evolveBtn.enabled && battle.player.era < cfg.maxEra &&
            (tut == null || tut.finished) && evolveBtn.contains(x, y)) {
            val p = battle.player
            val missing = kotlin.math.ceil(
                (Defs.evolveXp[p.era] - p.xp).coerceAtLeast(0f).toDouble()).toInt()
            missingXpTooltip.set(L["evolve"], L.f("battle84_missing_xp", missing))
            missingXpRemaining = 3.5f
            return
        }
        if (aimingBarrage) {
            if (!paused && !finished && !aimMoved && aimStartedInField &&
                battle.abilityReady(battle.player, 0) &&
                fieldRect.contains(x, y) && (!showTurrets || !panel.contains(x, y))) {
                barrageView.fireAimedBarrage(barrageView.snapBarrageTarget(r.worldX(x)))
            }
            aimingBarrage = false // Dotknięcie poza polem walki anuluje.
            return
        }
        if (showUpgrade >= 0) {
            if (!upgradePanel.contains(x, y)) showUpgrade = -1
            return
        }
        if (mini.width() > 0f && x in mini.left..mini.right && y in (mini.top - 10 * dp)..(mini.bottom + 10 * dp)) {
            r.camX = (x - mini.left) / mini.width() * Defs.LANE - r.worldVisible / 2
            r.clampCam(); manualCam = 5f
            tut?.onCamera()
            return
        }
        if (showTurrets && !panel.contains(x, y)) showTurrets = false
    }

    /**
     * W bitwie tap na wyłączonym pasku pomocniczym (informacja o kolejce) trafia do onTap:
     * podczas celowania ostrzału musi go odpalić albo anulować, a nie zniknąć.
     */
    override fun swallowsInactiveTap(b: Button): Boolean = false

    override fun resetTouch() {
        super.resetTouch()
        if (pauseSliderPointer >= 0) {
            pauseSliderPointer = -1
            g.profile.save()
        }
        aimStartedInField = false
        aimMoved = false
    }

    override fun onBack(): Boolean {
        if (eraIntro?.active == true) { eraIntro?.dismiss(); return true }
        if (aimingBarrage) { aimingBarrage = false; return true }
        if (finished) { g.setScreen(nextScreen()); return true }
        if (showUpgrade >= 0) { showUpgrade = -1; return true }
        if (showTurrets) { showTurrets = false; return true }
        if (pauseSliderPointer >= 0) {
            pauseSliderPointer = -1
            g.profile.save()
        }
        paused = !paused
        return true
    }

    /** Przy pauzie serializujemy stan na wątku gry, a fsync wykonuje wątek I/O. */
    override fun onPause() {
        aimingBarrage = false
        clearHeldTooltip()
        if (finished || closed || battle.over) return
        paused = true
        g.snapshots?.saveAsync(snapshotBytes())
    }

    private fun snapshotBytes(): ByteArray = BattleSnapshot.encode(battle, rankedDaily, r.camX)

    // ---------------------------------------------------------------- koniec bitwy

    private fun onFinished() {
        aimingBarrage = false
        finished = true
        battleSummary.finish(battle)
        resultChart.prepare(battleSummary, resultChartArea, dp)
        // Kolejka I/O usuwa zrzut w tej samej kolejności co wcześniejsze zapisy.
        g.snapshots?.deleteAsync()
        val pr = g.profile
        val medalsBefore = pr.medals
        val missionBefore = (pr.missionState.daily + pr.missionState.weekly)
            .associate { it.id to it.progress }
        val won = battle.winner == 0
        // Statystyki i osiągnięcia rozlicza Achievements po nagrodzie bazowej.
        val day = Daily.dayNumber()
        val score = battle.score()
        // Ten sam log służy do zapisu powtórki i zliczania zaakceptowanych rozkazów.
        val missionLog = commands.log()
        try {
            val id = g.replays?.newId()
            val now = System.currentTimeMillis()
            val bytes = ReplayRecord.encode(battle.cfg, missionLog,
                ReplayRecord.Meta(battle.cfg.mode, battle.cfg.stageIndex, battle.winner,
                    score, battle.time, battle.wave, now, pr.name.take(32)))
            replayBytes = bytes
            replayId = id
            if (id != null) g.replays?.saveAsync(id, bytes)
        } catch (_: IllegalArgumentException) {
            replayBytes = null
            replayId = null
        }
        var earnedStars = 0
        // Nagroda bazowa za porażkę/trening rośnie z czasem bitwy (pełna po 4 min symulacji),
        // inaczej natychmiastowa kapitulacja w pętli byłaby najszybszym sposobem na medale.
        val effort = (battle.time / 240f).coerceIn(0f, 1f)
        when (cfg.mode) {
            Mode.CAMPAIGN -> {
                val stage = cfg.stageIndex
                val difficulty = Campaign.difficulty(cfg) ?: CampaignDifficulty.NORMAL
                val old = pr.stars[stage]
                val st = battle.stars()
                val baseReward = if (won) 20 + stage * 6 + st * 5 + max(0, st - old) * 12
                    else ((5 + stage * 2) * effort).toInt() + min(20, battle.player.kills / 8)
                reward = (baseReward * difficulty.medalMultiplier).toInt()
                if (st > old) {
                    earnedStars = st - old
                    pr.stars[stage] = st
                }
                // Gwiazdki odblokowują etapy niezależnie od poziomu, ranking wymaga Normalnego.
                if (!cfg.tutorial && difficulty == CampaignDifficulty.NORMAL && st > pr.normalStars[stage]) {
                    pr.normalStars[stage] = st
                    newRecord = pr.submit(Profile.BOARD_CAMPAIGN, pr.normalStarTotal().toLong(), day, replayId)
                    g.online.submit(Profile.BOARD_CAMPAIGN, pr.normalStarTotal().toLong())
                }
                if (won && !cfg.tutorial && difficulty == CampaignDifficulty.HARD) pr.recordHardVictory(stage)
                if (won) pr.tutorialDone = true
            }
            Mode.SKIRMISH -> {
                pr.recordSkirmish(cfg, score, day, won)
                reward = SkirmishSetup.reward(cfg, won, battle.time, battle.player.kills)
                if (won && SkirmishSetup.isRanked(cfg)) {
                    newRecord = pr.submit(Profile.BOARD_SKIRMISH, score, day, replayId)
                    g.online.submit(Profile.BOARD_SKIRMISH, score)
                }
            }
            Mode.ENDLESS -> {
                if (cfg.weekly >= 0L) {
                    val currentWeek = Weekly.weekNumber(day)
                    // Tydzień wyniku pochodzi z konfiguracji, nie z chwili zakończenia.
                    val bonus = pr.claimWeekly(cfg.weekly, battle.wave, currentWeek)
                    reward = (battle.wave * 3 + battle.player.kills / 10) / 2 + bonus
                    newRecord = pr.submit(Profile.weeklyBoard(cfg.weekly), score, day, replayId)
                    pr.syncWeekly(currentWeek)
                    weeklyResultKey = when {
                        battle.wave >= Weekly.threshold(2) -> "weekly_gold"
                        battle.wave >= Weekly.threshold(1) -> "weekly_silver"
                        battle.wave >= Weekly.threshold(0) -> "weekly_bronze"
                        else -> "weekly_no_tier"
                    }
                } else {
                    reward = battle.wave * 3 + battle.player.kills / 10
                    if (battle.wave > pr.bestWave) pr.bestWave = battle.wave
                    newRecord = pr.submit(Profile.BOARD_ENDLESS, score, day, replayId)
                    g.online.submit(Profile.BOARD_ENDLESS, score)
                }
            }
            Mode.DAILY -> {
                if (rankedDaily) {
                    reward = min(90, (score / 350).toInt()) + 10
                    newRecord = pr.submit(Profile.dailyBoard(cfg.seed), score, day, replayId)
                    g.online.submit("daily", score)
                } else reward = (5 * effort).toInt()
            }
        }
        // Przypinamy tylko wynik, który pozostał w lokalnym TOP 20.
        replayId?.let { id ->
            if (pr.boards.values.any { list -> list.any { it.replay == id } }) g.replays?.pinAsync(id)
        }
        pr.medals = (pr.medals.toLong() + reward).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val finishedAt = System.currentTimeMillis()
        Missions(pr).onBattleEnd(battle, finishedAt, persist = false, newStars = earnedStars, playerCommands = missionLog)
        missionsAdvanced = (pr.missionState.daily + pr.missionState.weekly)
            .count { it.progress > (missionBefore[it.id] ?: it.progress) }
        g.showAchievements(Achievements.onBattleEnd(pr, battle, cfg, won, g.onlineAchievements, achievementEvents))
        totalMedalsGained = (pr.medals - medalsBefore).coerceAtLeast(0)
        tipKey = if (won) "" else when {
            !battle.endless && battle.time >= Battle.SIEGE_START + 35f -> "tip_siege"
            battle.player.aliveCount >= battle.player.cap &&
                battle.player.gold >= battle.armoryCost(battle.player) * 1.3f -> "tip_cap"
            counterRoleLoss() -> "tip_counter"
            battle.player.spawned >= 6 && battle.player.unitTier.all { it == 1 } -> "tip_upgrade"
            battle.enemy.era > battle.player.era -> "tip_era"
            battle.enemy.goldEarned > battle.player.goldEarned * 1.25f -> "tip_eco"
            battle.player.turretEra.none { it >= 0 } || battle.player.armory == 0 -> "tip_turret"
            else -> "tip_ability"
        }
        if (!won) collectDefeatReasons()
        pr.recordBattle(cfg.mode, won, battle.time.toInt())
        if (cfg.mode == Mode.DAILY && rankedDaily && battle.stepCount >= 60 && !surrendered)
            pr.recordRankedDailyStreak(cfg.seed)
        pr.refreshBanners()
        g.history?.add(BattleHistory.capture(battle, finishedAt, reward, tipKey, surrendered))
        tipIcon = when (tipKey) {
            "tip_siege" -> Icons.HOURGLASS
            "tip_cap" -> Icons.HELMET
            "tip_counter" -> Icons.CROSSED_SWORDS
            "tip_upgrade" -> Icons.CHEVRONS
            "tip_era" -> Icons.EVOLVE
            "tip_eco" -> Icons.COINS
            "tip_turret" -> Icons.TOWER
            else -> Icons.BOLT
        }
        // Sam zapis powtórki i HTTPS wykonuje osobny wątek, nigdy pętla gry.
        // Wyniki z podejść treningowych daily i wczesne kapitulacje pomijamy.
        // Potyczka własna (zlecenie 35) nie trafia do rankingu; poziom trudności wynika z konfiguracji bitwy.
        val skirmishDiff = if (cfg.mode == Mode.SKIRMISH)
            (0..3).firstOrNull { kotlin.math.abs(Skirmish.power(it) - cfg.aiPower) < 0.0001f } ?: -1 else 0
        val rankedSkirmish = cfg.mode != Mode.SKIRMISH || (SkirmishSetup.isRanked(cfg) && skirmishDiff >= 0)
        val rankedCampaign = cfg.mode != Mode.CAMPAIGN ||
            (!cfg.tutorial && Campaign.difficulty(cfg) == CampaignDifficulty.NORMAL)
        if (pr.globalRanking && battle.stepCount >= 60 && (cfg.mode != Mode.DAILY || rankedDaily) &&
            rankedSkirmish && rankedCampaign) {
            g.scores?.submit(cfg, commands.log(), score, rankedDaily, pr.name, pr.playerId,
                pr.researchLevels(), skirmishDiff)
        }
        // onBattleEnd zapisuje profil tylko przy pierwszym rozliczeniu; gwiazdki i nagroda muszą trafić na dysk zawsze.
        pr.save()
        if (won) g.host.onBattleWon()
        haptics.onEnd(won)
        g.music?.duck(if (won) 0.24f else 0.40f, 1.8f)
        g.sfx(if (won || cfg.mode == Mode.ENDLESS) Sound.VICTORY else Sound.DEFEAT, 0.85f, 0, priority = 3)
        resultsView.refreshResultTexts()
        if (reward > 0) g.sfx(Sound.REWARD, 0.4f, 0)
        if (earnedStars > 0) g.sfx(Sound.STAR, 0.46f, 0, pitch = 1f + earnedStars * 0.06f)
    }

    /** Dwie sprawdzalne przyczyny wybierane tylko z zakończonej bitwy. */
    private fun collectDefeatReasons() {
        defeatReasonCount = 0
        val p = battle.player
        val e = battle.enemy
        fun add(key: String) {
            if (defeatReasonCount < defeatReasons.size) defeatReasons[defeatReasonCount++] = key
        }
        if (surrendered) add("battle84_cause_surrender")
        if (e.era > p.era) add("battle84_cause_era")
        if (p.gold >= max(250f, battle.armoryCost(p) * 1.2f)) add("battle84_cause_gold")
        if (p.slots > 0 && p.turretEra.none { it >= 0 }) add("battle84_cause_turrets")
        if (e.spawned >= p.spawned + 4 && e.spawned > p.spawned * 1.25f)
            add("battle84_cause_army")
        if (e.goldEarned >= 200f && e.goldEarned > p.goldEarned * 1.3f)
            add("battle84_cause_eco")
        // Przy braku wymiernej przewagi wracamy do ogólnej wskazówki historycznej.
        if (defeatReasonCount == 0 && tipKey.isNotEmpty()) add(tipKey)
    }

    internal fun defeatReasonText(key: String): String = when (key) {
        "battle84_cause_gold" -> L.f(key, battle.player.gold.toInt())
        "battle84_cause_army" -> L.f(key, battle.player.spawned, battle.enemy.spawned)
        "battle84_cause_eco" -> L.f(key, battle.player.goldEarned.toInt(), battle.enemy.goldEarned.toInt())
        else -> L[key]
    }

    /** Rozpoznaj kontrę wobec najczęściej szkolonej roli; tylko liczniki z bitwy. */
    private fun counterRoleLoss(): Boolean {
        val own = battle.player.spawnedByRole
        val foe = battle.enemy.spawnedByRole
        var lead = 0
        for (i in 1..2) if (own[i] > own[lead]) lead = i
        if (own[lead] < 6) return false
        val counter = when (lead) { 0 -> 2; 1 -> 0; 2 -> 1; else -> return false }
        return foe[counter] >= 5 && foe[counter] * 2 >= battle.enemy.spawned &&
            Defs.counterMultiplier(roles[counter], roles[lead]) > 1f
    }

    // ---------------------------------------------------------------- rysowanie

    private var pauseLanguage = -1
    private var pauseSeconds = -1
    private var pauseKills = -1
    private var pauseGold = -1
    private var pausePlayerEra = -1
    private var pauseEnemyEra = -1
    private var pauseWeather = -1
    private var pauseTitleText = ""
    private var pauseSummaryLabel = ""
    private var pauseQuickLabel = ""
    private var pauseSfxLabel = ""
    private var pauseMusicLabel = ""
    private var pauseVibrationLabel = ""
    private var pauseSpeedLabel = ""
    private var pauseTimeText = ""
    private var pauseKillsText = ""
    private var pauseGoldText = ""
    private var pausePlayerEraText = ""
    private var pauseEnemyEraText = ""
    private var pauseWeatherText = ""
    private var pauseOpponentText = ""
    private var pauseModifierText = ""
    private val pauseModifierLines = arrayOfNulls<String>(12)
    private var pauseModifierCount = 0
    private var pauseModifierWidth = -1f

    /** Aktualizacja rzadkich napisów poza render; liczby tylko, gdy stan się zmieni. */
    internal fun refreshPauseTexts() {
        if (!paused || finished) return
        val changedLanguage = pauseLanguage != L.lang
        if (changedLanguage) {
            pauseLanguage = L.lang
            pauseTitleText = L["paused"]
            pauseSummaryLabel = L["pause_summary"]
            pauseQuickLabel = L["pause_quick"]
            pauseSfxLabel = L["sfx_volume"]
            pauseMusicLabel = L["music_volume"]
            pauseVibrationLabel = L["vibration"]
            pauseSpeedLabel = L["cx_speed"]
            pauseModifierText = L["modifiers"] + ": " +
                (if (cfg.modifiers.isEmpty()) L["sk_none"] else
                    cfg.modifiers.sortedBy { it.ordinal }.joinToString(", ") { L[it.key] })
            pauseOpponentText = if (cfg.mode == Mode.SKIRMISH) {
                val personality = battle.ai?.personality
                val person = L["sk_opponent"] + ": " +
                    (if (personality == null) L["sk_random"] else L["sk_person_" + personality.ordinal])
                person + " · " + L.f("ai_power", (cfg.aiPower * 100f + 0.5f).toInt())
            } else ""
            pauseSeconds = -1
            pauseKills = -1
            pauseGold = -1
            pausePlayerEra = -1
            pauseEnemyEra = -1
            pauseWeather = -1
            pauseModifierWidth = -1f
        }
        val seconds = battle.time.toInt()
        if (pauseSeconds != seconds) {
            pauseSeconds = seconds
            val remainder = seconds % 60
            pauseTimeText = L["time"] + ": " + (seconds / 60) + ":" +
                (if (remainder < 10) "0" else "") + remainder
        }
        val kills = battle.player.kills
        if (pauseKills != kills) {
            pauseKills = kills
            pauseKillsText = L["kills"] + ": " + kills
        }
        val gold = battle.player.goldEarned.toInt()
        if (pauseGold != gold) {
            pauseGold = gold
            pauseGoldText = L["pause_gold_earned"] + ": " + gold
        }
        if (pausePlayerEra != battle.player.era) {
            pausePlayerEra = battle.player.era
            pausePlayerEraText = L["pause_player_era"] + ": " + L[Defs.eraKeys[pausePlayerEra]]
        }
        if (pauseEnemyEra != battle.enemy.era) {
            pauseEnemyEra = battle.enemy.era
            pauseEnemyEraText = L["pause_enemy_era"] + ": " + L[Defs.eraKeys[pauseEnemyEra]]
        }
        if (pauseWeather != battle.weather.ordinal) {
            pauseWeather = battle.weather.ordinal
            pauseWeatherText = L["pause_weather"] + ": " + L[battle.weather.key]
        }
        val width = pauseInfoW
        if (width > 0f && pauseModifierWidth != width) {
            pauseModifierWidth = width
            pauseModifierCount = HintFrame.wrapInto(ui, pauseModifierText, width, 12f, pauseModifierLines)
        }
    }
    private val pausePanel = RectF()
    private var pauseInfoX = 0f
    private var pauseInfoW = 0f
    private var pauseSummaryY = 0f
    private var pauseLineY0 = 0f
    private var pauseStep = 0f
    private var pauseStatSize = 13f
    private var pauseModY = 0f
    private var pauseModStep = 0f
    private var pauseOpponentY = 0f
    private var pauseSepTop = 0f
    private var pauseSepBottom = 0f
    private var pauseCtrlX = 0f
    private var pauseCtrlLabelW = 0f
    private var pauseQuickY = 0f
    internal val resultPanel = RectF()
    internal val tmpR = RectF()
    internal val actionBtns get() = actionArr
    private val actionArr = arrayOf(turretBtn, armoryBtn, barrageBtn, rallyBtn, stanceBtn, speedBtn)
    internal var goldShown = -1
    internal var goldText = ""
    internal var capKey = -1
    internal var capText = ""
    internal var hpP = -1
    internal var hpPText = ""
    internal var hpE = -1
    internal var hpEText = ""

    internal fun doctrineIcon(d: Doctrine) = Icons.doctrineIcon(d)

    private fun endlessIcon(card: EndlessCard): Int = when (card) {
        EndlessCard.ECONOMY, EndlessCard.EXTRA_GOLD -> Icons.COINS
        EndlessCard.SIEGE, EndlessCard.AUTO_BARRAGE -> Icons.BOMB
        EndlessCard.BULWARK, EndlessCard.FORTRESS, EndlessCard.FRONTLINE_PLATE -> Icons.SHIELD
        EndlessCard.SWIFT, EndlessCard.MARCH, EndlessCard.DRILL_SERGEANT -> Icons.BOLT
        EndlessCard.VETERAN, EndlessCard.ELITE_SCHOOL -> Icons.CHEVRONS
        EndlessCard.FURY, EndlessCard.BERSERK -> Icons.FLAME
        EndlessCard.ARSENAL, EndlessCard.TOWER_SLOT, EndlessCard.RAPID_TOWERS -> Icons.TOWER
        EndlessCard.TACTICS, EndlessCard.RALLY_MASTER -> Icons.HOURGLASS
        EndlessCard.PLUNDER, EndlessCard.BLOOD_GOLD, EndlessCard.SCAVENGER -> Icons.BAG
        EndlessCard.FREE_ELITE, EndlessCard.MOBILIZATION -> Icons.HELMET
        EndlessCard.BURNING_TOWERS -> Icons.METEOR
        EndlessCard.CLONING -> Icons.PERSON
        EndlessCard.MEDIC, EndlessCard.REPAIR_CREW -> Icons.HEART
        EndlessCard.SHARPSHOOTERS -> Icons.CROSSHAIR
        EndlessCard.LAST_STAND -> Icons.CROWN
        EndlessCard.BANNER -> Icons.BANNER
        EndlessCard.SUPPLY -> Icons.SUPPLY_CRATE
        EndlessCard.SIEGE_MASTERS -> Icons.SIEGE_HAMMER
        EndlessCard.VIGILANCE -> Icons.WATCH_EYE
        EndlessCard.SUPPLY_LINE -> Icons.CHEST
        EndlessCard.WAR_DRUMS -> Icons.FLAG
        EndlessCard.IRON_CURTAIN -> Icons.WALL
        EndlessCard.TOWER_NETWORK -> Icons.TOWER
        EndlessCard.MUSTER_BANNER -> Icons.TENT
        EndlessCard.LAST_BASTION -> Icons.CROWN
        EndlessCard.EMBER_FORGE -> Icons.ANVIL
        EndlessCard.CITADEL_HEART -> Icons.HEART
        EndlessCard.FIELD_MEDICS -> Icons.FIELD_MEDICS
        EndlessCard.FINAL_SPARK -> Icons.FINAL_SPARK
        EndlessCard.FIRST_GUARD -> Icons.FIRST_GUARD
        EndlessCard.BOSS_RECALL -> Icons.BOSS_RECALL
    }

    private fun rarityLabel(r: EndlessRarity): String = L[when (r) {
        EndlessRarity.COMMON -> "er_common"
        EndlessRarity.UNCOMMON -> "er_uncommon"
        EndlessRarity.RARE -> "er_rare"
        EndlessRarity.LEGENDARY -> "er_legendary"
    }]

    override fun render(c: Canvas) {
        // Interpolacja między krokami symulacji (płynny ruch przy 90/120 Hz).
        r.lerp = (acc / Battle.STEP).coerceIn(0f, 1f)
        r.render(c, if (paused || eraIntro?.active == true || battle.pendingVariant != null || battle.pendingChoice != null || battle.pendingCards != null) 0f else frameDt * effectiveSpeed * (tut?.timeScale ?: 1f), w, h)
        if (aimingBarrage) barrageView.drawAim(c)
        // HUD zachowuje czytelność i nie zasłania pola gry przy 130%.
        val regularTextScale = ui.textScale
        ui.textScale = min(regularTextScale, 1.15f)
        topBarView.drawTop(c)
        if (aimingBarrage) barrageView.drawHint(c)
        bottomHudView.drawHudBack(c)
        for (b in actionArr) ui.button(c, b, 13f)
        for (b in unitBtn) ui.button(c, b, 13f)
        bottomHudView.drawUnitTiers(c)
        for (b in unitUpgrade) ui.button(c, b, 9f)
        bottomHudView.drawQueue(c)
        ui.button(c, evolveBtn, 15f)
        ui.button(c, pauseBtn, 15f)
        if (showTurrets && battle.pendingChoice == null && battle.pendingCards == null && battle.pendingVariant == null && !paused && !finished) upgradesView.drawTurretPanel(c)
        if (showUpgrade >= 0 && battle.pendingChoice == null && battle.pendingCards == null && battle.pendingVariant == null && !paused && !finished) upgradesView.drawUpgradePanel(c)
        if ((battle.pendingVariant != null || battle.pendingChoice != null || battle.pendingCards != null) && !finished) drawChoices(c)
        if (!finished) {
            tut?.draw(c)
            hints.draw(c)
        }
        if (paused && !finished) drawPausePanel(c)
        if (finished) resultsView.drawResults(c)
        if (!finished && !paused && introRemaining > 0f && !isTooltipHeld &&
            showUpgrade < 0 && !showTurrets && hints.activeId < 0 && tut == null) {
            ui.tooltip(c, unitBtn[0].r, introTooltip, g.insetL, g.insetT,
                w - g.insetR, h - g.insetB)
        }
        if (!paused && !finished && eraIntro?.active != true && missingXpRemaining > 0f &&
            hints.activeId < 0 && !isTooltipHeld) {
            ui.tooltip(c, evolveBtn.r, missingXpTooltip, g.insetL, g.insetT,
                w - g.insetR, h - g.insetB)
        }
        if (!paused && !finished && eraIntro?.active != true) drawHeldTooltip(c)
        eraIntro?.draw(c)
        ui.textScale = regularTextScale
    }

    /** Wszystkie widoczne napisy pauzy zostały przygotowane przez update(). */
    private fun pauseLine(c: Canvas, value: String, x: Float, y: Float, width: Float,
                          color: Int = Ui.TEXT, size: Float = 13f) {
        if (value.isNotEmpty()) ui.textO(c, value, x, y, ui.fit(value, size, width, 9f), color)
    }

    private fun drawPausePanel(c: Canvas) {
        c.drawColor(Color.argb(150, 0, 0, 0))
        ui.panel(c, pausePanel)
        ui.ribbon(c, pausePanel.centerX(), pausePanel.top + 4 * dp, pauseTitleText,
            21f, Ui.RIBBON, pausePanel.width() * 0.38f, Ui.GOLD, pausePanel.width() - 20 * dp)
        val x = pauseInfoX
        val infoWidth = pauseInfoW
        pauseLine(c, pauseSummaryLabel, x, pauseSummaryY, infoWidth, Ui.GOLD, 16f)
        val step = pauseStep
        val size = pauseStatSize
        pauseLine(c, pauseTimeText, x, pauseLineY0, infoWidth, size = size)
        pauseLine(c, pauseKillsText, x, pauseLineY0 + step, infoWidth, size = size)
        pauseLine(c, pauseGoldText, x, pauseLineY0 + step * 2, infoWidth, size = size)
        pauseLine(c, pausePlayerEraText, x, pauseLineY0 + step * 3, infoWidth, size = size)
        pauseLine(c, pauseEnemyEraText, x, pauseLineY0 + step * 4, infoWidth, size = size)
        pauseLine(c, pauseWeatherText, x, pauseLineY0 + step * 5, infoWidth, size = size)
        val modSize = min(12f, size)
        for (i in 0 until min(3, pauseModifierCount)) {
            val line = pauseModifierLines[i] ?: continue
            pauseLine(c, line, x, pauseModY + i * pauseModStep, infoWidth, Ui.DIM, modSize)
        }
        if (pauseOpponentText.isNotEmpty())
            pauseLine(c, pauseOpponentText, x, pauseOpponentY, infoWidth, Ui.GOLD, min(11f, size))

        if (pauseWide) {
            ui.fill.color = Ui.FRAME_L
            val sep = pausePanel.left + pausePanel.width() * 0.53f
            c.drawRect(sep, pauseSepTop, sep + 1 * dp, pauseSepBottom, ui.fill)
        }
        val controlsX = pauseCtrlX
        val controlsWidth = pauseCtrlLabelW
        pauseLine(c, pauseQuickLabel, controlsX, pauseQuickY,
            pausePanel.right - controlsX - 20 * dp, Ui.GOLD, 16f)
        pauseLine(c, pauseSfxLabel, controlsX, pauseSfxRect.centerY() + 5 * dp, controlsWidth)
        pauseLine(c, pauseMusicLabel, controlsX, pauseMusicRect.centerY() + 5 * dp, controlsWidth)
        ui.volumeSlider(c, pauseSfxRect, g.profile.sfxVolume)
        ui.volumeSlider(c, pauseMusicRect, g.profile.musicVolume)
        pauseLine(c, pauseVibrationLabel, controlsX, pauseVibrationBtn.r.centerY() + 5 * dp,
            pauseVibrationBtn.r.left - controlsX - 8 * dp)
        pauseLine(c, pauseSpeedLabel, controlsX, pauseSpeedBtn.r.centerY() + 5 * dp,
            pauseSpeedBtn.r.left - controlsX - 8 * dp)
        ui.button(c, pauseVibrationBtn, 14f)
        ui.button(c, pauseSpeedBtn, 14f)
        for (i in pauseMenu.indices) ui.button(c, pauseMenu[i], 17f)
    }


    internal var timeSec = -1
    internal var timeText = ""
    internal var siegeKey = -1
    internal var siegeText = ""
    internal var waveShown = -1
    internal var waveText = ""
    internal var nextThemeShown = -1
    internal var nextThemeText = ""
    internal var bossShownKind = -1
    internal var bossShownSecondary = -1
    internal var bossTitleText = ""
    internal var bossDescText = ""
    internal var bossSecondDescText = ""








    /** Modal dwóch wariantów: ikonki tych samych jednostek co na polu bitwy. */
    private fun drawVariants(c: Canvas) {
        val era = battle.pendingVariant ?: return
        val variants = Defs.spec(era).variants
        c.drawColor(Color.argb(175, 0, 0, 0))
        ui.ribbon(c, w / 2, variantBtn[0].r.top - 27 * dp,
            L["choose_variant"], 21f, Ui.RIBBON, min(340 * dp, w * 0.7f))
        for (i in variants.indices) {
            val v = variants[i]
            val b = variantBtn[i]
            val off = if (b.pressed) 3 * dp else 0f
            tmpR.set(b.r.left, b.r.top + off, b.r.right, b.r.bottom + off)
            ui.panel(c, tmpR, Color.argb(247, 40, 48, 70), 14f)
            val name = L[v.nameKey]
            ui.textO(c, name, tmpR.centerX(), tmpR.top + 34 * dp,
                ui.fit(name, 19f, tmpR.width() - 16 * dp), Ui.GOLD, Paint.Align.CENTER)
            val wrapWidth = tmpR.width() - 18 * dp
            val key = era * 2 + i
            if (variantDescKey[i] != key || variantDescLanguage != L.lang ||
                variantDescWidth != wrapWidth || variantDescScale != ui.textScale) {
                variantDescKey[i] = key
                variantDescCount[i] = HintFrame.wrapInto(ui, L[v.descKey],
                    wrapWidth, 12.5f, variantDescLines[i])
            }
            drawWrappedLines(c, variantDescLines[i], variantDescCount[i],
                tmpR.centerX(), tmpR.top + 58 * dp, 12.5f, Ui.TEXT, Paint.Align.CENTER)
            val imgH = min(72 * dp, tmpR.height() * 0.32f)
            val iy = tmpR.bottom - 28 * dp
            // Każda sylwetka ma własny obszar. Pojazdy skalujemy także po szerokości.
            val slotW = tmpR.width() / 4f
            for (role in 0..3) {
                val def = Defs.unit(era, roles[role], i)
                val xx = tmpR.left + (role + 0.5f) * slotW
                c.save()
                c.clipRect(tmpR.left + role * slotW + 4 * dp, tmpR.top,
                    tmpR.left + (role + 1) * slotW - 4 * dp, tmpR.bottom - 4 * dp)
                r.drawUnitIcon(c, def, xx, iy, imgH, Ui.PLAYER,
                    maxWidth = slotW - 12 * dp)
                c.restore()
            }
        }
        variantDescLanguage = L.lang
        variantDescWidth = variantBtn[0].r.width() - 18 * dp
        variantDescScale = ui.textScale
    }

    private fun drawChoices(c: Canvas) {
        if (battle.pendingVariant != null) { drawVariants(c); return }
        val cards = battle.pendingCards
        val doctrines = battle.pendingChoice
        if (cards == null && doctrines == null) return
        c.drawColor(Color.argb(160, 0, 0, 0))
        ui.ribbon(c, w / 2, doctrineBtn[0].r.top - 34 * dp,
            if (cards == null) L["choose_doctrine"] else L["choose_card"],
            22f, Ui.RIBBON, 300 * dp)
        for (i in 0..2) {
            val b = doctrineBtn[i]
            val off = if (b.pressed) 3 * dp else 0f
            tmpR.set(b.r.left, b.r.top + off, b.r.right, b.r.bottom + off)
            val rarity = cards?.get(i)?.rarity
            val trim = if (rarity == null) Ui.GOLD else CARD_RARITY[rarity.ordinal.coerceIn(0, CARD_RARITY.size - 1)]
            ui.panel(c, tmpR, Color.argb(245, 40, 48, 70), 14f)
            val mr = min(33 * dp, min(b.r.width() * 0.2f, b.r.height() * 0.18f))
            val my = tmpR.top + 10 * dp + mr
            val icon = if (cards == null) doctrineIcon(doctrines!![i]) else endlessIcon(cards[i])
            ui.medallion(c, tmpR.centerX(), my, mr, trim, icon)
            val key = if (cards == null) doctrines!![i].key else cards[i].textKey
            val name = L[key]
            ui.textO(c, name, tmpR.centerX(), my + mr + 23 * dp,
                ui.fit(name, 17f, tmpR.width() - 18 * dp), Ui.GOLD, Paint.Align.CENTER)
            if (rarity != null) {
                ui.textO(c, rarityLabel(rarity), tmpR.centerX(), my + mr + 40 * dp,
                    10f, Ui.DIM, Paint.Align.CENTER, 1.6f)
            }
            val descKey = choiceDescriptionKey(cards?.get(i), doctrines?.get(i))
            val wrapWidth = tmpR.width() - 22 * dp
            val choiceKey = if (cards == null) doctrines!![i].ordinal else 100 + cards[i].ordinal
            if (choiceDescKey[i] != choiceKey || choiceDescLanguage != L.lang ||
                choiceDescWidth != wrapWidth || choiceDescScale != ui.textScale) {
                choiceDescKey[i] = choiceKey
                choiceDescCount[i] = HintFrame.wrapInto(ui, L[descKey],
                    wrapWidth, 12f, choiceDescLines[i])
            }
            drawWrappedLines(c, choiceDescLines[i], choiceDescCount[i],
                tmpR.centerX(), my + mr + (if (rarity != null) 56f else 45f) * dp,
                12f, Ui.TEXT, Paint.Align.CENTER)
        }
        choiceDescLanguage = L.lang
        choiceDescWidth = doctrineBtn[0].r.width() - 22 * dp
        choiceDescScale = ui.textScale
    }



    /** Rysowanie wcześniej złamanych wierszy nie tworzy nowych ciągów znaków. */
    internal fun drawWrappedLines(c: Canvas, lines: Array<String?>, count: Int,
                                 x: Float, y: Float, sizeDp: Float, color: Int, align: Paint.Align) {
        var yy = y
        val lineH = sizeDp * dp * ui.textScale * 1.3f
        for (i in 0 until count) {
            val line = lines[i] ?: continue
            ui.text(c, line, x, yy, sizeDp, color, align)
            yy += lineH
        }
    }

    /** Wysokość wiersza wyniku (26 dp, ściskana na 720p, gdy wierszy jest dużo). */
    internal var resultRowH = 0f
    /** Wiersze pominięte z braku miejsca w ostatniej klatce (kontrola zrzutów 720p). */
    internal var resultRowsClipped = 0


    internal var armyP = -1
    internal var armyE = -1
    internal var armyPText = ""
    internal var armyEText = ""



}
