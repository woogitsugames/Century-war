package com.centurywar.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Canvas
import android.graphics.RectF
import com.centurywar.engine.Game
import com.centurywar.engine.Button
import com.centurywar.engine.Screen
import com.centurywar.engine.L
import com.centurywar.engine.Host
import com.centurywar.engine.Quality
import com.centurywar.engine.Palette
import com.centurywar.engine.TouchEvent
import com.centurywar.game.Ai
import com.centurywar.game.AiPersonality
import com.centurywar.game.Modifier
import com.centurywar.meta.SkirmishSetup
import com.centurywar.game.Battle
import com.centurywar.game.BattleConfig
import com.centurywar.game.BattleController
import com.centurywar.game.ReplayRecord
import com.centurywar.game.ReplayPlayer
import com.centurywar.game.BattleRenderer
import com.centurywar.game.EndlessWaveTheme
import com.centurywar.game.Bonuses
import com.centurywar.game.Campaign
import com.centurywar.game.Stage
import com.centurywar.game.CampaignDifficulty
import com.centurywar.game.Defs
import com.centurywar.game.Role
import com.centurywar.game.Mode
import com.centurywar.meta.MissionItem
import com.centurywar.meta.MissionType
import com.centurywar.meta.Missions
import com.centurywar.game.Weather
import com.centurywar.game.Daily
import com.centurywar.game.DailyEvent
import com.centurywar.game.Weekly
import com.centurywar.meta.OnlineLeaderboards
import com.centurywar.meta.OnlineState
import com.centurywar.meta.Profile
import com.centurywar.meta.ReplayStore
import com.centurywar.meta.BattleHistory
import com.centurywar.meta.ScoreClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Renderuje ekrany gry do PNG (Robolectric + natywna Skia), żeby obejrzeć UI
 * bez telefonu. Rozdzielczości: tani telefon 720p, flagowiec 20:9, rozłożony Fold.
 * Pliki trafiają do build/screenshots/.
 *
 * Część klasy dawnego ScreenshotTest (podział mechaniczny, noc 9); narzędzia:
 * [ScreenshotTestBase].
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class ScreenshotTestBattle : ScreenshotTestBase() {
    /** Zrzuty przytrzymanych przycisków na telefonie 720p i rozłożonym Foldzie. */
    @Test
    fun hudTooltips() {
        val oldLang = L.lang
        try {
            L.lang = L.PL
            for ((tag, dims) in mapOf(
                "phone720" to intArrayOf(1600, 720, 2),
                "foldPortrait" to intArrayOf(2184, 1968, 3),
            )) {
                val g = game(dims[0], dims[1], dims[2] * 0.9f,
                    if (tag == "phone720") Quality.LOW else Quality.HIGH)
                g.profile.hintsSeen = Profile.HINT_VALID_MASK
                val bs = BattleScreen(g, BattleConfig(Mode.SKIRMISH, 535L, 1f))
                g.setScreen(bs)
                val clear = Canvas(Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888))
                g.frame(clear, 1f / 60f)
                val armory = field(bs, "armoryBtn").get(bs) as Button
                g.pushTouch(TouchEvent.DOWN, armory.r.centerX(), armory.r.centerY(), 0)
                repeat(30) { g.frame(clear, 1f / 60f) }
                shot(g, "${tag}_tip53_armory", 1)
                g.pushTouch(TouchEvent.UP, armory.r.centerX(), armory.r.centerY(), 0)
                g.frame(clear, 1f / 60f)
                val unit = field(bs, "unitBtn").get(bs) as Array<*>
                val melee = unit[0] as Button
                g.pushTouch(TouchEvent.DOWN, melee.r.centerX(), melee.r.centerY(), 0)
                repeat(30) { g.frame(clear, 1f / 60f) }
                shot(g, "${tag}_tip53_unit", 1)
                g.pushTouch(TouchEvent.UP, melee.r.centerX(), melee.r.centerY(), 0)
                g.frame(clear, 1f / 60f)
            }
        } finally {
            L.lang = oldLang
        }
    }

    @Test fun turretTiers140() {
        val devices = mapOf("phone720" to intArrayOf(1600, 720, 2),
            "foldOpen" to intArrayOf(2184, 1968, 3), "foldInner" to intArrayOf(1802, 2000, 4),
            "foldOuter" to intArrayOf(904, 2316, 3))
        for ((tag, d) in screenshotDevices(devices)) for (leftHanded in listOf(false, true)) {
            val g = game(d[0], d[1], d[2].toFloat(), Quality.LOW)
            g.profile.leftHandedHud = leftHanded
            val bs = BattleScreen(g, BattleConfig(Mode.SKIRMISH, 14L, 1f))
            g.setScreen(bs)
            val battle = field(bs, "battle").get(bs) as Battle
            battle.player.gold = 10000f
            battle.player.slots = 4
            for (i in 0 until battle.player.slots) {
                assertTrue(battle.buyTurret(battle.player, i))
                if (i > 0) assertTrue(battle.upgradeTurretTier(battle.player, i))
            }
            field(bs, "showTurrets").set(bs, true)
            shot(g, "${tag}_turret_tiers_140_${if (leftHanded) "left" else "right"}")
            for (i in 0 until battle.player.slots) {
                val tier = bs.slotTier[i]
                assertTrue(tier.visible)
                // 146: na ciasnym 720p sloty są w siatce 2x2; cel dotyku z marginesem hitSlop.
                assertTrue(tier.r.width() + tier.hitSlop * 2f >= 48 * g.ui.dp - 0.01f)
                assertTrue(tier.r.height() + tier.hitSlop * 2f >= 48 * g.ui.dp - 0.01f)
                assertTrue(tier.r.right <= bs.slotSell[i].r.left)
                assertTrue(bs.slotBuy[i].r.right <= tier.r.left)
                for (j in 0 until i) for (other in listOf(bs.slotBuy[j], bs.slotTier[j], bs.slotSell[j]))
                    org.junit.Assert.assertFalse("$tag slot $i nachodzi na slot $j",
                        RectF.intersects(tier.r, other.r))
            }
        }
    }

    /** Czytelność salwy i znaczników na małym telefonie oraz rozłożonym Foldzie. */
    @Test
    fun barrageDuringBattle() {
        for ((tag, dims) in mapOf(
            "phone720" to intArrayOf(1600, 720, 2),
            "foldOpen" to intArrayOf(2184, 1968, 3),
            // Wewnętrzny ekran Folda z dużą skalą: minimapa w drugim rzędzie.
            "foldInner" to intArrayOf(1802, 2000, 4),
        )) {
            val g = game(dims[0], dims[1], dims[2] * 0.9f,
                if (tag == "phone720") Quality.LOW else Quality.HIGH)
            g.profile.screenShake = 2
            val bs = BattleScreen(g, BattleConfig(Mode.SKIRMISH, 77L, 1f, startEra = 6))
            g.setScreen(bs)
            val battle = field(bs, "battle").get(bs) as Battle
            assertTrue(battle.commandController!!.barrage(560f))
            assertTrue(battle.castBarrage(battle.sides[1], 560f))
            val bmp = Bitmap.createBitmap(g.width, g.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            repeat(42) { g.frame(canvas, Battle.STEP) }
            File(out, tag + "_battle_barrage.png").outputStream().use {
                bmp.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            bmp.recycle()
        }
    }

    /** Odtwarzacz i lista w telefonach oraz obu orientacjach rozłożonego Folda. */
    @Test
    fun replays() {
        val cfg = BattleConfig(Mode.SKIRMISH, 299L, 0.65f, startEra = 2, maxEra = 4,
            playerBonuses = Bonuses(baseHpMul = 15f))
        val original = Battle(cfg)
        val commands = BattleController(original)
        original.playerAi = Ai(original, 0)
        repeat(60 * 32) {
            if (!original.over) {
                original.step()
                original.fx.count = 0
            }
        }
        if (!original.over) commands.surrender()
        val bytes = ReplayRecord.encode(cfg, commands.log(), ReplayRecord.Meta(
            cfg.mode, cfg.stageIndex, original.winner, original.score(),
            original.time, original.wave, 1793461200000L, "Dowódca",
        ))
        for ((tag, dims) in screenshotDevices(mapOf(
            "phone720" to intArrayOf(1600, 720, 2),
            "phone20x9" to intArrayOf(2400, 1080, 3),
            "foldOpen" to intArrayOf(2184, 1968, 3),
            "foldPortrait" to intArrayOf(1968, 2184, 3),
            "foldInner" to intArrayOf(1802, 2000, 4),
            "foldOuter" to intArrayOf(904, 2316, 3),
        ))) {
            val g = game(dims[0], dims[1], dims[2] * 0.9f,
                if (tag == "phone720") Quality.LOW else Quality.HIGH)
            val store = ReplayStore(File.createTempFile("cw-replay", "").apply { delete(); mkdirs() })
            g.replays = store
            val id = store.newId()
            store.saveAsync(id, bytes)
            val ready = CountDownLatch(1)
            store.listAsync { ready.countDown() }
            assertTrue(ready.await(8, TimeUnit.SECONDS))

            g.setScreen(ReplayListScreen(g))
            // Lista trafia na wątek gry przez Game.post.
            shot(g, "${tag}_replay_list", 5)

            val screen = ReplayScreen(g, bytes)
            g.setScreen(screen)
            val player = field(screen, "player").get(screen) as ReplayPlayer
            player.advance(minOf(900, player.totalSteps))
            shot(g, "${tag}_replay_viewer", 5)
        }
    }

    @Test
    fun intermediateFramesProduceTheSameBattleState() {
        fun simulate(compact: Boolean): Battle {
            val g = game(1600, 720, 1.8f, Quality.LOW)
            val screen = BattleScreen(g, BattleConfig(Mode.SKIRMISH, 9_751L, 1f,
                startEra = 0, maxEra = 2))
            g.setScreen(screen)
            val battle = field(screen, "battle").get(screen) as Battle
            battle.playerAi = Ai(battle, 0)
            val bitmap = Bitmap.createBitmap(
                if (compact) 4 else g.width,
                if (compact) 4 else g.height,
                Bitmap.Config.ARGB_8888,
            )
            try {
                val canvas = Canvas(bitmap)
                repeat(90) { g.frame(canvas, 1f / 60f) }
            } finally {
                bitmap.recycle()
            }
            return battle
        }

        val full = simulate(false)
        val tiny = simulate(true)
        assertEquals(full.time, tiny.time, 0f)
        assertEquals(full.player.gold, tiny.player.gold, 0f)
        assertEquals(full.player.kills, tiny.player.kills)
        assertEquals(full.enemy.kills, tiny.enemy.kills)
    }

    @Test
    fun screens() {
        for ((tag, dims) in screenshotDevices(mapOf(
            "phone720" to intArrayOf(1600, 720, 2),
            "phone20x9" to intArrayOf(2400, 1080, 3),
            "foldOpen" to intArrayOf(2184, 1968, 3),
        ))) {
            val g = game(dims[0], dims[1], dims[2] / 1.0f * 0.9f, if (tag == "phone720") Quality.LOW else Quality.HIGH)
            shot(g, "${tag}_menu")
            g.setScreen(CampaignScreen(g)); shot(g, "${tag}_campaign")
            g.setScreen(ResearchScreen(g)); shot(g, "${tag}_research")

            for (era in intArrayOf(0, 3, 5, 7)) {
                val bs = BattleScreen(g, BattleConfig(Mode.SKIRMISH, 5L + era, 1f, startEra = era, maxEra = 7))
                g.setScreen(bs)
                val f = BattleScreen::class.java.getDeclaredField("battle").apply { isAccessible = true }
                val b = f.get(bs) as com.centurywar.game.Battle
                b.playerAi = Ai(b, 0)
                val bmp = Bitmap.createBitmap(g.width, g.height, Bitmap.Config.ARGB_8888)
                val tiny = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
                try {
                    val tickCanvas = Canvas(tiny)
                    repeat(60 * 70 - 1) { g.frame(tickCanvas, 1f / 60f) }
                    g.frame(Canvas(bmp), 1f / 60f)
                    File(out, "${tag}_battle_era$era.png").outputStream().use {
                        check(bmp.compress(Bitmap.CompressFormat.PNG, 100, it))
                    }
                } finally {
                    tiny.recycle()
                    bmp.recycle()
                }
            }
        }
    }

    /**
     * Skryptowana bitwa samouczka (etap 1 kampanii, pierwszy raz): gracz wykonuje polecenia
     * z nakładki, a test robi zrzuty kolejnych kroków. phone20x9 ma wcięcia (wycięcie aparatu,
     * pasek gestów), żeby sprawdzić, że ramki i wycięcia je respektują.
     */
    @Test
    fun tutorial() {
        for ((tag, dims) in screenshotDevices(mapOf(
            "phone720" to intArrayOf(1600, 720, 2),
            "phone20x9" to intArrayOf(2400, 1080, 3),
            "foldOpen" to intArrayOf(2184, 1968, 3),
        ))) {
            val g = game(dims[0], dims[1], dims[2] / 1.0f * 0.9f, if (tag == "phone720") Quality.LOW else Quality.HIGH)
            g.profile.tutorialDone = false
            if (tag == "phone20x9") g.setInsets(110, 0, 70, 40)
            val bs = BattleScreen(g, Campaign.config(Campaign.stages[0], Bonuses(), 11L, tutorial = true))
            g.setScreen(bs)
            val b = field(bs, "battle").get(bs) as Battle
            val tut = field(bs, "tut").get(bs) as TutorialOverlay
            val speed = field(bs, "speed")
            // Klatki oczekiwania nie wymagają rasteryzacji całego ekranu.
            val c = Canvas(Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888))
            var frame = 0
            fun until(step: Int) {
                var i = 0
                while (!(tut.step == step && tut.showing) && !tut.finished && !b.over && i < 60 * 900) {
                    // czekając na warunek kroku "gramy": szybko i z dosyłaniem piechoty
                    speed.set(bs, if (tut.showing) 1 else 5)
                    if (!tut.showing && tut.step >= TutorialOverlay.S_KILL && frame % 90 == 0) b.queueUnit(b.player, if (frame % 180 == 0) Role.MELEE else Role.RANGED)
                    g.frame(c, 1f / 60f); i++; frame++
                }
                speed.set(bs, 1)
                repeat(40) { g.frame(c, 1f / 60f) } // animacja pierścienia, odblokowanie "Dalej"
                assertTrue("$tag: krok $step (jest ${tut.step}, showing=${tut.showing}, over=${b.over})", tut.step == step && tut.showing)
            }
            fun tutorialShot(name: String) {
                shot(g, "${tag}_$name")
                assertTrue("$tag/$name: panel poza ekranem",
                    tut.panel.left >= g.insetL - 1f && tut.panel.right <= g.width - g.insetR + 1f &&
                        tut.panel.top >= g.insetT - 1f && tut.panel.bottom <= g.height - g.insetB + 1f)
                if (tut.hole.width() > 0f && tut.hole.height() > 0f && tut.step != TutorialOverlay.S_DONE)
                    assertTrue("$tag/$name: panel zasłania cel", !RectF.intersects(tut.panel, tut.hole))
                if (tut.skipBtn.visible) {
                    assertTrue("$tag/$name: pomiń mniejsze niż 48 dp",
                        tut.skipBtn.r.height() + 0.5f >= 48 * g.ui.dp)
                    assertTrue("$tag/$name: pomiń na panelu",
                        !RectF.intersects(tut.skipBtn.r, tut.panel))
                    if (tut.hole.width() > 0f && tut.step != TutorialOverlay.S_DONE)
                        assertTrue("$tag/$name: pomiń na celu",
                            !RectF.intersects(tut.skipBtn.r, tut.hole))
                }
                if (tut.nextBtn.visible)
                    assertTrue("$tag/$name: dalej mniejsze niż 48 dp",
                        tut.nextBtn.r.height() + 0.5f >= 48 * g.ui.dp)
            }

            fun tapHole() { tap(g, tut.hole.centerX(), tut.hole.centerY()); g.frame(c, 1f / 60f) }
            fun next() { tap(g, tut.nextBtn.r.centerX(), tut.nextBtn.r.centerY()); g.frame(c, 1f / 60f) }

            until(TutorialOverlay.S_MELEE); tutorialShot("tut01_melee")
            val skipX = tut.skipBtn.r.centerX()
            val skipY = tut.skipBtn.r.centerY()
            tap(g, skipX, skipY); g.frame(c, 1f / 60f)
            assertTrue("$tag: pierwsze kliknięcie tylko otwiera potwierdzenie",
                tut.skipConfirmationVisible && !tut.finished && !g.profile.tutorialDone)
            shot(g, "${tag}_tut85_skip_confirmation", 1)
            assertTrue("$tag: przyciski potwierdzenia mają 48 dp",
                tut.nextBtn.visible && tut.skipBtn.r.height() + 0.01f >= 48 * g.ui.dp &&
                    tut.nextBtn.r.height() + 0.01f >= 48 * g.ui.dp)
            tap(g, tut.nextBtn.r.centerX(), tut.nextBtn.r.centerY()); g.frame(c, 1f / 60f)
            assertTrue("$tag: anulowanie nie kończy samouczka",
                !tut.skipConfirmationVisible && !tut.finished && !g.profile.tutorialDone)
            tapHole()
            until(TutorialOverlay.S_INCOME); tutorialShot("tut02_income"); next()
            until(TutorialOverlay.S_RANGED); tutorialShot("tut03_ranged"); tapHole()
            until(TutorialOverlay.S_KILL); tutorialShot("tut04_kill"); next()
            b.player.xp = Defs.evolveXp[0] * 0.85f
            until(TutorialOverlay.S_EVOLVE); tutorialShot("tut05_evolve_wait")
            b.player.xp = Defs.evolveXp[0].toFloat()
            repeat(10) { g.frame(c, 1f / 60f) }
            tutorialShot("tut05_evolve"); tapHole()
            until(TutorialOverlay.S_DOCTRINE); tutorialShot("tut06_doctrine"); tapHole()
            until(TutorialOverlay.S_TURRET); tutorialShot("tut07_turret"); tapHole()
            repeat(5) { g.frame(c, 1f / 60f) }
            tutorialShot("tut07_turret_buy"); tapHole()
            b.player.gold = maxOf(b.player.gold, b.armoryCost(b.player) * 2f)
            until(TutorialOverlay.S_ARMORY); tutorialShot("tut08_armory"); tapHole()
            assertTrue("$tag: zbrojownia", b.player.armory > 0)
            // Bot wystawia przeciwników blisko bazy: oba kroki obronne dają się aktywować.
            fun enemyPush() {
                for (i in 0..2) {
                    val enemy = b.units[i]
                    enemy.active = true; enemy.dying = -1f; enemy.side = 1
                    enemy.x = b.player.baseFront + 140f + i * 20f
                }
                b.enemy.aliveCount = maxOf(b.enemy.aliveCount, 3)
            }
            enemyPush()
            until(TutorialOverlay.S_STANCE); tutorialShot("tut09_stance"); tapHole()
            assertTrue("$tag: postawa Trzymaj", b.player.hold)
            enemyPush()
            until(TutorialOverlay.S_RALLY); tutorialShot("tut10_rally"); tapHole()
            assertTrue("$tag: Zryw", b.player.abilityCd[1] > 0f)
            until(TutorialOverlay.S_BARRAGE); tutorialShot("tut11_barrage"); tapHole()
            tutorialShot("tut11_barrage_aim")
            val startX = tut.hole.centerX() - tut.hole.width() * 0.2f
            val finishX = tut.hole.centerX() + tut.hole.width() * 0.2f
            val aimY = tut.hole.centerY()
            g.pushTouch(TouchEvent.DOWN, startX, aimY, 0)
            g.pushTouch(TouchEvent.MOVE, finishX, aimY, 0)
            g.pushTouch(TouchEvent.UP, finishX, aimY, 0)
            g.frame(c, 1f / 60f)
            // Puszczenie palca po przeciągnięciu od razu odpala ostrzał (celowanie przeciąganiem).
            shot(g, "${tag}_tut11_barrage_drag")
            if (b.player.barragesUsed == 0) tapHole()
            assertTrue("$tag: ostrzał samouczka", b.player.barragesUsed > 0)
            until(TutorialOverlay.S_CAMERA); tutorialShot("tut12_camera"); next()
            until(TutorialOverlay.S_DONE); tutorialShot("tut13_done"); next()
            repeat(5) { g.frame(c, 1f / 60f) }
            assertTrue("$tag: samouczek zakończony", tut.finished && g.profile.tutorialDone)
            println("$tag tutorial ok: t=${b.time} era=${b.player.era} turrets=${b.player.turretEra.count { it >= 0 }} kills=${b.player.kills}")
            val settings = HintSettingsScreen(g)
            g.setScreen(settings)
            shot(g, "${tag}_tut85_replay_settings")
            val replay = field(settings, "tutorial").get(settings) as Button
            assertTrue("$tag: powtórz samouczek jest widoczne",
                replay.visible && replay.r.width() > 0f && replay.r.height() > 0f &&
                    replay.r.left >= g.insetL && replay.r.right <= g.width - g.insetR)
            tap(g, replay.r.centerX(), replay.r.centerY()); g.frame(c, 1f / 60f)
            tap(g, replay.r.centerX(), replay.r.centerY()); g.frame(c, 1f / 60f)
            assertTrue("$tag: ustawienia nie uruchomiły powtórki", !g.profile.tutorialDone)
            val restart = BattleScreen(g, Campaign.config(Campaign.stages[0], Bonuses(), 12L, tutorial = true))
            assertTrue("$tag: etap 1 nie odtwarza samouczka", field(restart, "tut").get(restart) != null)
        }
    }

    /** Przegląd nowych dymków bitwy i pierwszego dymka Badań: 720p, 20:9, Fold. */
    @Test
    fun contextualHints() {
        for ((tag, dims) in mapOf(
            "phone720" to intArrayOf(1600, 720, 2),
            "phone20x9" to intArrayOf(2400, 1080, 3),
            "foldOpen" to intArrayOf(2184, 1968, 3),
            "foldPortrait" to intArrayOf(1968, 2184, 3),
        )) {
            val g = game(dims[0], dims[1], dims[2] * 0.9f,
                if (tag == "phone720") Quality.LOW else Quality.HIGH)
            // Ukryj powitalny baner z nagrodą za logowanie przed kadrami dymków.
            val clear = Canvas(Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888))
            repeat(210) { g.frame(clear, 1f / 60f) }
            g.setScreen(ResearchScreen(g))
            shot(g, tag + "_hint_research", 1)
            g.setScreen(MissionsScreen(g))
            shot(g, tag + "_hint_missions", 1)
            g.setScreen(ModeScreen(g, Mode.DAILY))
            shot(g, tag + "_hint_daily_menu", 1)
            g.setScreen(ModeScreen(g, Mode.ENDLESS))
            shot(g, tag + "_hint_endless_menu", 1)

            val bs = BattleScreen(g, BattleConfig(Mode.SKIRMISH, 560L, 1f))
            g.setScreen(bs)
            val b = field(bs, "battle").get(bs) as Battle
            b.player.gold = 50000f
            val hints = field(bs, "hints").get(bs) as HintOverlay
            val show = hints.javaClass.getDeclaredMethod("activate",
                Int::class.javaPrimitiveType, String::class.java).apply { isAccessible = true }
            fun capture(overlay: HintOverlay, id: Int, key: String, name: String) {
                g.profile.hintsSeen = 0L
                show.invoke(overlay, id, key)
                shot(g, tag + "_hint_" + name, 1)
                overlay.dismiss()
            }
            capture(hints, HintOverlay.UPGRADE, "hint_upgrade", "upgrade")
            b.player.aliveCount = b.player.cap
            capture(hints, HintOverlay.CAP, "hint_cap", "armory")
            capture(hints, HintOverlay.RALLY, "hint_rally", "rally")
            capture(hints, HintOverlay.HOLD, "hint_hold", "hold")
            capture(hints, HintOverlay.VETERAN, "hint_veteran", "veteran")
            capture(hints, HintOverlay.MODIFIERS, "hint_modifiers", "modifiers")
            capture(hints, HintOverlay.FOG, "hint_fog", "fog")
            capture(hints, HintOverlay.OLD_TURRET, "hint_old_turret", "old_turret")

            val dailyScreen = BattleScreen(g, BattleConfig(Mode.DAILY, 565L, 1f))
            g.setScreen(dailyScreen)
            val dailyHint = field(dailyScreen, "hints").get(dailyScreen) as HintOverlay
            capture(dailyHint, HintOverlay.DAILY, "hint_daily_battle", "daily_battle")

            val weatherScreen = BattleScreen(g, BattleConfig(Mode.SKIRMISH, 562L, 1f,
                weather = Weather.WIND))
            g.setScreen(weatherScreen)
            val weatherHint = field(weatherScreen, "hints").get(weatherScreen) as HintOverlay
            capture(weatherHint, HintOverlay.WEATHER, "hint_weather54", "weather")

            val variantScreen = BattleScreen(g, BattleConfig(Mode.SKIRMISH, 563L, 1f,
                startEra = 5, maxEra = 6, weather = Weather.RAIN))
            g.setScreen(variantScreen)
            val variantHint = field(variantScreen, "hints").get(variantScreen) as HintOverlay
            capture(variantHint, HintOverlay.VARIANT, "hint_variant54", "variant")

            val endlessScreen = BattleScreen(g, BattleConfig(Mode.ENDLESS, 561L, 1f))
            g.setScreen(endlessScreen)
            val eh = field(endlessScreen, "hints").get(endlessScreen) as HintOverlay
            val eb = field(endlessScreen, "battle").get(endlessScreen) as Battle
            eb.wave = 4
            eb.waveTimer = 5f
            capture(eh, HintOverlay.CARD, "hint_card", "endless_card")
            capture(eh, HintOverlay.THEME, "hint_theme", "endless_theme")
            eb.wave = 10
            eb.units[0].active = true
            eb.units[0].dying = -1f
            eb.units[0].side = 1
            eb.units[0].bossKind = 1
            eb.units[0].hp = 100000f
            eb.units[0].maxHp = 100000f
            eb.units[0].x = 1800f
            capture(eh, HintOverlay.BOSS, "hint_boss_push", "boss")

            val siegeScreen = BattleScreen(g, BattleConfig(Mode.SKIRMISH, 562L, 1f))
            g.setScreen(siegeScreen)
            val siege = field(siegeScreen, "battle").get(siegeScreen) as Battle
            siege.time = Battle.SIEGE_START + 7f
            val sh = field(siegeScreen, "hints").get(siegeScreen) as HintOverlay
            capture(sh, HintOverlay.SIEGE, "hint_siege", "siege")
        }
    }

    /** Wszystkie warunki pogody na czterech formatach ekranu, także noc na LOW. */
    @Test
    fun weatherScreens() {
        for ((tag, dims) in mapOf(
            "phone720" to intArrayOf(1600, 720, 2),
            "phone20x9" to intArrayOf(2400, 1080, 3),
            "foldOpen" to intArrayOf(2184, 1968, 3),
            "foldPortrait" to intArrayOf(1968, 2184, 3),
        )) {
            val g = game(dims[0], dims[1], dims[2] * 0.9f,
                if (tag == "phone720") Quality.LOW else Quality.HIGH)
            if (tag == "phone20x9") g.setInsets(110, 0, 70, 40)
            for (weather in Weather.entries) {
                val era = when (weather) {
                    Weather.CLEAR -> 0
                    Weather.RAIN -> 2
                    Weather.WIND -> 5
                    Weather.NIGHT -> 8
                }
                val bs = BattleScreen(g, BattleConfig(Mode.SKIRMISH, 5L + weather.ordinal, 1f,
                    startEra = era, maxEra = era, weather = weather))
                g.setScreen(bs)
                val battle = field(bs, "battle").get(bs) as Battle
                battle.player.gold = 200000f
                battle.enemy.gold = 200000f
                battle.spawn(battle.player, Defs.unit(era, Role.RANGED))
                battle.spawn(battle.enemy, Defs.unit(era, Role.HEAVY))
                battle.player.turretEra[0] = era
                battle.enemy.turretEra[0] = era
                battle.player.turretTier[0] = 1
                battle.enemy.turretTier[0] = 1
                if (weather == Weather.NIGHT) battle.player.baseFlash = 0.13f
                shot(g, "${tag}_weather_${weather.name.lowercase()}")
            }

            val cs = CampaignScreen(g)
            g.setScreen(cs)
            field(cs, "selected").set(cs, Campaign.stages[4])
            shot(g, "${tag}_weather_stage_info")
        }
    }

    /** Bossowie, podwójna zdolność, zapowiedzi i Kodeks na wszystkich formatach. */
    @Test
    fun endlessBossVariants() {
        for ((tag, dims) in mapOf(
            "phone720" to intArrayOf(1600, 720, 2),
            "phone20x9" to intArrayOf(2400, 1080, 3),
            "foldOpen" to intArrayOf(2184, 1968, 3),
            "foldPortrait" to intArrayOf(1968, 2184, 3),
        )) {
            val g = game(dims[0], dims[1], dims[2] * 0.9f,
                if (tag == "phone720") Quality.LOW else Quality.HIGH)
            for (w in intArrayOf(10, 20, 30, 40, 50, 60)) {
                val screen = BattleScreen(g, BattleConfig(Mode.ENDLESS, 802L + w, 1f))
                g.setScreen(screen)
                val b = field(screen, "battle").get(screen) as Battle
                b.wave = w - 1
                b.nextBossToSpawn = w
                b.waveTimer = 0f
                b.step()
                b.pendingCards?.let { b.chooseEndlessCard(0) }
                val boss = b.currentBoss()!!
                val renderer = field(screen, "r").get(screen) as BattleRenderer
                boss.x = b.player.baseFront + renderer.worldVisible * 0.54f
                renderer.camX = boss.x - renderer.worldVisible * 0.5f
                renderer.clampCam()
                shot(g, "${tag}_endless_boss_${w}")
            }
            val preview = BattleScreen(g, BattleConfig(Mode.ENDLESS, 901L, 1f))
            g.setScreen(preview)
            val battle = field(preview, "battle").get(preview) as Battle
            battle.wave = 9
            battle.nextBossToSpawn = 10
            shot(g, "${tag}_endless_next_boss_kind")
            battle.wave = 10
            battle.nextWaveTheme = EndlessWaveTheme.SIEGE_TRAIN
            shot(g, "${tag}_endless_siege_train_preview")
            battle.nextWaveTheme = EndlessWaveTheme.SKIRMISHERS
            field(preview, "nextThemeShown").setInt(preview, -1)
            shot(g, "${tag}_endless_skirmishers_preview")
            val codex = CodexScreen(g, 0)
            g.setScreen(codex)
            codex.showCategory(9)
            val end = field(codex, "maxScroll").getFloat(codex)
            field(codex, "scroll").setFloat(codex, end * 0.5f)
            shot(g, "${tag}_codex_endless_bosses")
            field(codex, "scroll").setFloat(codex, end)
            shot(g, "${tag}_codex_endless_waves")
        }
    }

    /** Nakładka metryk na tanim telefonie i rozłożonym Foldzie. */
    @Test
    fun performanceOverlay() {
        for ((tag, dims) in mapOf(
            "phone720" to intArrayOf(1600, 720, 2),
            "foldOpen" to intArrayOf(2184, 1968, 3),
        )) {
            val quality = if (tag == "phone720") Quality.LOW else Quality.HIGH
            val g = game(dims[0], dims[1], dims[2] * 0.9f, quality)
            g.profile.showFps = true
            g.profile.quality = quality
            g.setQuality(quality)
            g.setScreen(BattleScreen(g, BattleConfig(Mode.SKIRMISH, 27037L, 1f)))
            shot(g, "${tag}_performance_overlay", 30)
        }
    }

    /** Zrzuty bitew w pięciu epokach: 720p i rozłożony Fold. */
    @Test
    fun eraBackdropBattleScreens() {
        for ((tag, dims) in mapOf(
            "phone720" to intArrayOf(1600, 720, 2),
            "foldOpen" to intArrayOf(2184, 1968, 3),
        )) {
            val quality = if (tag == "phone720") Quality.LOW else Quality.HIGH
            val g = game(dims[0], dims[1], dims[2] * 0.9f, quality)
            for (era in intArrayOf(0, 2, 4, 6, 8)) {
                g.setScreen(BattleScreen(g, BattleConfig(Mode.SKIRMISH, 5600L + era, 1f,
                    startEra = era, maxEra = Defs.ERA_COUNT - 1)))
                shot(g, tag + "_backdrop_era" + era, 2)
            }
        }
    }

    /**
     * Pauza z danymi bitwy i ustawieniami: telefony 16:9 i 20:9 (640×360 i 800×360 dp),
     * 1080p, oba ułożenia rozłożonego Folda i telefon pionowo. Gęstość bez skali 0,9,
     * czyli najciaśniejszy wariant danego ekranu.
     */
    @Test
    fun quickPauseSettings() {
        for ((tag, dims) in screenshotDevices(mapOf(
            "phone720" to intArrayOf(1600, 720, 2),
            "phone1280" to intArrayOf(1280, 720, 2),
            "phone1080" to intArrayOf(2400, 1080, 3),
            "foldOpen" to intArrayOf(2184, 1968, 3),
            "foldPortrait" to intArrayOf(1968, 2184, 3),
            "foldInner" to intArrayOf(1802, 2000, 4),
            "foldOuter" to intArrayOf(904, 2316, 3),
            "phonePortrait" to intArrayOf(1080, 2400, 3),
        ))) {
            val g = game(dims[0], dims[1], dims[2].toFloat(),
                if (tag == "phone720" || tag == "phone1280") Quality.LOW else Quality.HIGH)
            g.profile.musicVolume = 0.65f
            g.profile.sfxVolume = 0.35f
            g.profile.vibration = false
            val bs = BattleScreen(g, BattleConfig(Mode.SKIRMISH, 6701L, 1.2f,
                startEra = 2, maxEra = 7, weather = Weather.NIGHT,
                modifiers = setOf(Modifier.FOG_OF_WAR, Modifier.FAST_UNITS),
                aiPersonality = AiPersonality.ENGINEER))
            g.setScreen(bs)
            val battle = field(bs, "battle").get(bs) as Battle
            battle.time = 125f
            battle.player.kills = 17
            battle.player.goldEarned = 1426f
            bs.onClick(field(bs, "pauseBtn").get(bs) as Button)
            // Baner serii logowań zasłaniałby suwaki; zrzut dokumentuje sam panel pauzy.
            (field(g, "noticeQueue").get(g) as MutableCollection<*>).clear()
            (field(g, "noticeIcons").get(g) as MutableCollection<*>).clear()
            field(g, "achievementSeconds").setFloat(g, 0f)
            shot(g, "${tag}_pause_quick_settings")
        }
    }

    /** Zlecenie 84: ekran bitwy, dymek awansu i porażka na pięciu proporcjach. */
    @Test
    fun battleUx84() {
        val oldLang = L.lang
        try {
            L.lang = L.PL
            val devices = linkedMapOf(
                "phone720" to intArrayOf(1600, 720, 2),
                "phone1280" to intArrayOf(1280, 720, 2),
                "phone20x9" to intArrayOf(2400, 1080, 3),
                "foldOpen" to intArrayOf(2184, 1968, 3),
                "foldPortrait" to intArrayOf(1968, 2184, 3),
                "foldInner" to intArrayOf(1802, 2000, 4),
                "foldOuter" to intArrayOf(904, 2316, 3),
            )
            for ((tag, dimensions) in screenshotDevices(devices)) {
                val g = game(dimensions[0], dimensions[1], dimensions[2] * 0.9f,
                    if (tag == "phone720" || tag == "phone1280") Quality.LOW else Quality.HIGH)
                g.profile.lang = "pl"
                g.onLanguageChanged()
                g.profile.hintsSeen = Profile.HINT_VALID_MASK
                // Baner dziennego logowania nie może zasłaniać wyniku bitwy na zrzutach.
                val warm = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
                val warmCanvas = Canvas(warm)
                repeat(210) { g.frame(warmCanvas, 1f / 60f) }
                warm.recycle()
                val bs = BattleScreen(g, BattleConfig(Mode.SKIRMISH, 84084L, 1f,
                    startEra = 1, maxEra = 3))
                g.setScreen(bs)
                val battle = field(bs, "battle").get(bs) as Battle
                battle.player.gold = 5000f
                shot(g, tag + "_battle84_hud")
                val evolve = field(bs, "evolveBtn").get(bs) as Button
                bs.onTap(evolve.r.centerX(), evolve.r.centerY())
                shot(g, tag + "_battle84_missing_xp")
                battle.player.kills = 7
                battle.surrender()
                shot(g, tag + "_battle84_defeat", 90)
            }
        } finally {
            L.lang = oldLang
        }
    }
}
