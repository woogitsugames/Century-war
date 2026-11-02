package com.centurywar.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import com.centurywar.engine.Game
import com.centurywar.engine.Host
import com.centurywar.engine.L
import com.centurywar.engine.Quality
import com.centurywar.game.Campaign
import com.centurywar.game.Defs
import com.centurywar.meta.Profile
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Tura 9 (UX dowódcy): gracz pierwszy raz spotyka Wodza (etap 8 kampanii) i stopnie wież.
 * Zrzuty: build/screenshots/cj_<urządzenie>_<ręka>_<lang>_<stan>.png, raport: cj_audit.txt.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class CommanderJourneyTest {
    private val out = File("build/screenshots").apply { mkdirs() }
    private class Device(val w: Int, val h: Int, val density: Float, val insetT: Int)
    private val devices = linkedMapOf(
        "phone" to Device(1600, 720, 2f, 0),
        "portrait" to Device(720, 1280, 2f, 0),
        "foldOuter" to Device(904, 2316, 2.7f, 88),
        "foldInner" to Device(2184, 1968, 2.7f, 70),
    )
    private val host = object : Host {
        override fun vibrate(ms: Long) {}
        override fun requestText(title: String, current: String, onResult: (String) -> Unit) {}
        override fun applyRenderScale(scale: Float) {}
        override fun applyFrameRate(fps: Int) {}
        override fun exit() {}
    }
    private val tiny = Canvas(Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888))
    private val audit = StringBuilder()
    private val problems = StringBuilder()

    private fun game(d: Device, lang: String, left: Boolean, unseen: Long): Game {
        val f = File.createTempFile("cjourney", ".json").apply { delete() }
        val profile = Profile(f).apply {
            this.lang = lang
            tutorialDone = true
            hintsSeen = Profile.HINT_VALID_MASK and unseen.inv()
            leftHandedHud = left
        }
        return Game(host, profile, Quality.LOW).apply {
            density = d.density
            resize(d.w, d.h, d.w, d.h)
            setInsets(0, d.insetT, 0, 0)
        }
    }

    private fun hintsOf(bs: BattleScreen): HintOverlay =
        BattleScreen::class.java.getDeclaredField("hints").apply { isAccessible = true }.get(bs) as HintOverlay

    private fun bodyOf(h: HintOverlay): String =
        HintOverlay::class.java.getDeclaredField("body").apply { isAccessible = true }.get(h) as String

    private fun shot(g: Game, name: String) {
        g.finishScreenTransition()
        val bmp = Bitmap.createBitmap(g.width, g.height, Bitmap.Config.ARGB_8888)
        try {
            g.frame(Canvas(bmp), 0f)
            File(out, "cj_$name.png").outputStream().use { check(bmp.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } finally { bmp.recycle() }
    }

    /** Dymek nie może zasłaniać żadnego widocznego przycisku HUD poza własnym OK. */
    private fun checkHint(bs: BattleScreen, hints: HintOverlay, target: RectF, where: String) {
        val p = hints.panelRect
        if (hints.activeId < 0 || p.isEmpty) { problems.append("$where: brak dymka\n"); return }
        for (b in bs.buttons) if (b.visible && b.id != HintOverlay.OK_ID && RectF.intersects(p, b.r))
            problems.append("$where: dymek $p zasłania przycisk #${b.id} '${b.label}' ${b.r}\n")
        audit.append("$where: dymek $p cel $target\n")
    }

    @Test
    fun commanderAndTurretTierJourney() {
        val old = L.lang
        try {
            for ((tag, d) in devices) for (left in listOf(false, true)) for (lang in listOf("pl", "en", "de", "tr", "uk")) {
                if (left && lang != "pl") continue
                val hand = if (left) "L" else "R"
                val key = "${tag}_${hand}_$lang"
                // 1. Etap 8: pierwszy kontakt z Wodzem.
                val g = game(d, lang, left, 1L shl HintOverlay.COMMANDER)
                val bs = BattleScreen(g, Campaign.config(Campaign.stages[7], g.profile.bonuses(), 8L))
                g.setScreen(bs)
                g.finishScreenTransition()
                val p = bs.battle.player
                p.gold = 0f
                repeat(5) { g.frame(tiny, 1f / 30f) }
                val hints = hintsOf(bs)
                if (hints.activeId == HintOverlay.COMMANDER) problems.append("$key: dymek bez złota\n")
                if (lang == "pl") shot(g, "${key}_1_disabled")
                p.gold = bs.battle.commanderCost(p) + 5f
                var n = 0
                while (hints.activeId != HintOverlay.COMMANDER && n++ < 60) g.frame(tiny, 1f / 30f)
                shot(g, "${key}_2_hint")
                checkHint(bs, hints, bs.commanderBtn.r, "$key/comm")
                audit.append("$key: Wódz '${bs.commanderBtn.label}' sub '${bs.commanderBtn.sub}' ${bs.commanderBtn.r} " +
                    "${"%.0f".format(bs.commanderBtn.r.width() / g.ui.dp)}x${"%.0f".format(bs.commanderBtn.r.height() / g.ui.dp)} dp\n")
                // Dymek nad przyciskiem Wodza, a w kampanii z informacją o etapie 8.
                val cb = bs.commanderBtn.r
                if (hints.panelRect.bottom > cb.top + 0.5f) problems.append("$key: dymek nie stoi nad Wodzem\n")
                if (bodyOf(hints) != L["comm125_hint"]) problems.append("$key: tekst dymka kampanii\n")
                if (lang == "pl") {
                hints.dismiss()
                bs.commands.summonCommander()
                repeat(3) { g.frame(tiny, 1f / 30f) }
                shot(g, "${key}_3_alive")
                if (bs.commanderBtn.sub != L["comm145_active"] || bs.commanderBtn.subIcon >= 0)
                    problems.append("$key: żywy dowódca pokazuje '${bs.commanderBtn.sub}' zamiast stanu\n")

                // Odnowienie: osobna bitwa bez żywego dowódcy, licznik sekund z klepsydrą.
                val gc = game(d, lang, left, 0L)
                val bc = BattleScreen(gc, Campaign.config(Campaign.stages[7], gc.profile.bonuses(), 10L))
                gc.setScreen(bc)
                bc.battle.player.commanderCd = 60f
                bc.battle.player.gold = 1000f
                repeat(2) { gc.frame(tiny, 1f / 60f) }
                shot(gc, "${key}_4_cooldown")
                val sub = bc.commanderBtn.sub.toIntOrNull()
                if (sub == null || sub !in 59..60 || bc.commanderBtn.subIcon != com.centurywar.engine.Icons.HOURGLASS ||
                    bc.commanderBtn.enabled) problems.append("$key: odnowienie '${bc.commanderBtn.sub}'\n")

                // Potyczka: dymek bez wzmianki o etapie kampanii.
                val gs = game(d, lang, left, 1L shl HintOverlay.COMMANDER)
                val bsk = BattleScreen(gs, com.centurywar.game.BattleConfig(com.centurywar.game.Mode.SKIRMISH, 11L, 1f))
                gs.setScreen(bsk)
                bsk.battle.player.gold = 1000f
                val hs = hintsOf(bsk)
                n = 0
                while (hs.activeId != HintOverlay.COMMANDER && n++ < 60) gs.frame(tiny, 1f / 30f)
                if (bodyOf(hs) != L["comm145_hint"]) problems.append("$key: dymek potyczki '${bodyOf(hs)}'\n")
                checkHint(bsk, hs, bsk.commanderBtn.r, "$key/skirmish")
                shot(gs, "${key}_4b_skirmish")
                }

                // 2. Stopnie wież: wieża w dwóch slotach, stać na stopień.
                val g2 = game(d, lang, left, 1L shl HintOverlay.TURRET_TIER)
                val bs2 = BattleScreen(g2, Campaign.config(Campaign.stages[7], g2.profile.bonuses(), 9L))
                g2.setScreen(bs2)
                g2.finishScreenTransition()
                val p2 = bs2.battle.player
                p2.gold = 5000f
                p2.slots = Defs.MAX_TURRET_SLOTS
                bs2.commands.buyTurret(0)
                bs2.commands.buyTurret(1)
                bs2.commands.upgradeTurretTier(1)
                bs2.commands.upgradeTurretTier(1)
                val h2 = hintsOf(bs2)
                n = 0
                while (h2.activeId != HintOverlay.TURRET_TIER && n++ < 60) g2.frame(tiny, 1f / 30f)
                shot(g2, "${key}_5_tierhint")
                checkHint(bs2, h2, bs2.turretBtn.r, "$key/tier")
                if (bodyOf(h2) != L["tier145_hint"]) problems.append("$key: tekst dymka stopnia\n")
                h2.dismiss()
                bs2.showTurrets = true
                repeat(3) { g2.frame(tiny, 1f / 30f) }
                shot(g2, "${key}_6_panel")
                val dp = g2.ui.dp
                for (b in bs2.buttons) if (b.visible && b.id in BattleScreen.SLOT_TIER0 until BattleScreen.SLOT_TIER0 + Defs.MAX_TURRET_SLOTS) {
                    audit.append("$key: stopień #${b.id} '${b.label}' ${"%.0f".format(b.r.width() / dp)}x${"%.0f".format(b.r.height() / dp)} dp\n")
                    if (b.r.width() < 47.5f * dp || b.r.height() < 47.5f * dp) problems.append("$key: mały cel stopnia #${b.id}\n")
                    if (b.label == "MAX" || b.iconId != com.centurywar.engine.Icons.CHEVRONS) problems.append("$key: przycisk stopnia '${b.label}'\n")
                    for (o in bs2.buttons) if (o !== b && o.visible && o.id >= 0 && RectF.intersects(o.r, b.r))
                        problems.append("$key: stopień #${b.id} nachodzi na #${o.id}\n")
                }
                if (bs2.slotTier[1].label != L["tier145_max"]) problems.append("$key: brak 'maks.' (${bs2.slotTier[1].label})\n")
                if (bs2.slotTier[0].label != L.f("tier145_btn", 2, bs2.battle.turretTierCost(p2, 0)))
                    problems.append("$key: etykieta stopnia '${bs2.slotTier[0].label}'\n")
            }
        } finally {
            L.lang = old
            File(out, "cj_audit.txt").writeText(audit.toString() + "\n--- PROBLEMY ---\n" + problems)
        }
        assertTrue(problems.toString(), problems.isEmpty())
    }
}
