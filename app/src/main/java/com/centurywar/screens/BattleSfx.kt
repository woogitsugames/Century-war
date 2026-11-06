package com.centurywar.screens

import com.centurywar.engine.Game
import com.centurywar.engine.Music
import com.centurywar.engine.SfxSink
import com.centurywar.engine.Sound
import com.centurywar.game.Battle
import com.centurywar.game.Body
import com.centurywar.game.Defs
import com.centurywar.game.EraArt
import com.centurywar.game.FxBuffer
import com.centurywar.game.Proj
import com.centurywar.game.Role
import kotlin.math.min

/** Wspólne mapowanie zdarzeń bitwy na dźwięki epok dla rozgrywki i powtórki. */
object BattleSfx {
    /** Ładowanie odpowiednich wariantów wyłącznie w wątku Sound, bez I/O gry. */
    fun prepareEra(g: Game, era: Int, variant: Int = -1) {
        val sound = g.sound ?: return
        val style = Defs.sideArt(era, variant)
        // impactSound wybiera materiał po typie celu, więc trafienia potrzebują wszystkich czterech.
        sound.prewarm(Sound.HIT_WOOD)
        sound.prewarm(Sound.HIT_BRONZE)
        sound.prewarm(Sound.HIT_STEEL)
        sound.prewarm(Sound.HIT_ENERGY)
        sound.prewarm(Sound.HIT_POWDER)
        sound.prewarm(barrageSound(style))
        sound.prewarm(evolveSound(style))
        sound.prewarm(Sound.DEATH_HUMAN)
        sound.prewarm(Sound.DEATH_BEAST)
        sound.prewarm(Sound.DEATH_MACHINE)
        sound.prewarm(explosionSound(style))
        sound.prewarm(Sound.THUMP)
        sound.prewarm(Sound.BOSS)
        for (role in Role.entries) {
            val unit = Defs.unit(era, role, variant)
            if (unit.projectile != Proj.NONE) sound.prewarm(shotSound(unit.projectile, style, false))
        }
        sound.prewarm(shotSound(Defs.turret(era, variant).projectile, style, true))
    }

    /** Styl bierze się z definicji epoki, a nie z numeru tekstury lub muzyki. */
    private fun fxStyle(battle: Battle, eraId: Int, sideId: Int): EraArt {
        val side = battle.sides[sideId.coerceIn(0, 1)]
        val e = if (eraId in 0 until Defs.ERA_COUNT) eraId else side.era
        // Wariant epoki (zlecenie 28) ma własny styl dźwięku, gdy zdarzenie dotyczy bieżącej epoki strony.
        return if (e == side.era) Defs.sideArt(e, side.eraVariant) else Defs.specs[e].art
    }

    internal fun hitSound(style: EraArt): Int = when (style) {
        EraArt.TRIBAL -> Sound.HIT_WOOD
        EraArt.BRONZE -> Sound.HIT_BRONZE
        EraArt.IRON, EraArt.FEUDAL -> Sound.HIT_STEEL
        // D63: epoki prochu i stali — trafienie pociskiem (wcześniej to samo ostrze co w żelazie).
        EraArt.POWDER, EraArt.STEAM, EraArt.COMBUSTION, EraArt.GALVANIC, EraArt.STEEL -> Sound.HIT_POWDER
        EraArt.SILICON, EraArt.SWARM, EraArt.BIOTECH, EraArt.ORBITAL -> Sound.HIT_ENERGY
    }

    /** Materiał trafienia uwzględnia typ celu, nie tylko epokę atakującego. */
    internal fun impactSound(style: EraArt, body: Int): Int = when (body) {
        Body.BEAST -> Sound.HIT_WOOD
        Body.SIEGE, Body.CANNON, Body.VEHICLE -> Sound.HIT_STEEL
        Body.MECH, Body.HOVER -> Sound.HIT_ENERGY
        else -> hitSound(style)
    }

    /** Odrębny charakter akustyczny epok i ich wariantów bez nowych plików WAV. */
    internal fun eraPitch(style: EraArt): Float = when (style) {
        EraArt.TRIBAL -> 0.93f
        EraArt.BRONZE -> 1.00f
        EraArt.IRON -> 0.95f
        EraArt.FEUDAL -> 1.02f
        EraArt.POWDER -> 0.96f
        EraArt.STEAM -> 0.87f
        EraArt.COMBUSTION -> 1.03f
        EraArt.GALVANIC -> 1.09f
        EraArt.STEEL -> 0.98f
        EraArt.SILICON -> 1.05f
        EraArt.SWARM -> 1.13f
        EraArt.BIOTECH -> 0.91f
        EraArt.ORBITAL -> 0.83f
    }

    /**
     * Wybuch zawsze brzmi jak wybuch (rozpoznawalny we wszystkich epokach); próbki ostrzału
     * (BARRAGE_*) zostają zarezerwowane dla ostrzału. D63: trzy barwy wybuchu zamiast jednej
     * (przedtem 13 stylów różniła tylko wysokość): łomot kamienia/drewna przed prochem,
     * prochowy huk, opadający „zap” epok krzemu; dalej różnicuje je eraPitch.
     */
    internal fun explosionSound(style: EraArt): Int = when (style) {
        EraArt.TRIBAL, EraArt.BRONZE, EraArt.IRON, EraArt.FEUDAL -> Sound.EXPLOSION_PRIMITIVE
        EraArt.POWDER, EraArt.STEAM, EraArt.COMBUSTION, EraArt.GALVANIC, EraArt.STEEL -> Sound.EXPLOSION
        EraArt.SILICON, EraArt.SWARM, EraArt.BIOTECH, EraArt.ORBITAL -> Sound.EXPLOSION_ENERGY
    }

    internal fun shotSound(projectile: Int, style: EraArt, turret: Boolean): Int {
        if (turret) return when (style) {
            EraArt.TRIBAL, EraArt.BRONZE -> Sound.TURRET_PRIMITIVE
            EraArt.IRON, EraArt.FEUDAL -> Sound.TURRET_MEDIEVAL
            EraArt.POWDER, EraArt.STEAM, EraArt.COMBUSTION, EraArt.GALVANIC -> Sound.TURRET_POWDER
            EraArt.STEEL -> Sound.TURRET_MODERN
            EraArt.SILICON, EraArt.SWARM, EraArt.BIOTECH, EraArt.ORBITAL -> Sound.TURRET_ENERGY
        }
        return when (projectile) {
            Proj.STONE, Proj.BOULDER -> Sound.SHOT_SLING
            Proj.JAVELIN, Proj.ARROW -> Sound.SHOT_BOW
            Proj.BOLT -> Sound.SHOT_CROSSBOW
            Proj.BALL, Proj.SHELL -> Sound.SHOT_CANNON
            Proj.BULLET -> if (style == EraArt.POWDER || style == EraArt.STEAM || style == EraArt.GALVANIC) Sound.SHOT_MUSKET else Sound.SHOT_RIFLE
            Proj.ROCKET -> Sound.SHOT_GUN
            Proj.BEAM -> Sound.SHOT_LASER
            Proj.PLASMA -> Sound.SHOT_PLASMA
            else -> Sound.SHOT_SLING
        }
    }

    internal fun barrageSound(style: EraArt): Int = when (style) {
        EraArt.TRIBAL, EraArt.BRONZE, EraArt.IRON -> Sound.BARRAGE_STONE
        EraArt.FEUDAL -> Sound.BARRAGE_FIRE
        EraArt.POWDER, EraArt.STEAM, EraArt.COMBUSTION, EraArt.GALVANIC -> Sound.BARRAGE_CANNON
        EraArt.STEEL, EraArt.SILICON, EraArt.SWARM, EraArt.BIOTECH -> Sound.BARRAGE_ROCKET
        EraArt.ORBITAL -> Sound.BARRAGE_ORBITAL
    }

    internal fun evolveSound(style: EraArt): Int = when (style) {
        EraArt.TRIBAL, EraArt.BRONZE, EraArt.IRON -> Sound.EVOLVE_PRIMITIVE
        EraArt.FEUDAL -> Sound.EVOLVE_MEDIEVAL
        EraArt.POWDER, EraArt.STEAM, EraArt.COMBUSTION, EraArt.GALVANIC, EraArt.STEEL -> Sound.EVOLVE_INDUSTRIAL
        EraArt.SILICON, EraArt.SWARM, EraArt.BIOTECH, EraArt.ORBITAL -> Sound.EVOLVE_FUTURE
    }

    /**
     * Dwie kolejki priorytetu, panoramowanie według kamery, limit drobnych głosów.
     * Zwraca true, gdy w tej klatce zabrzmiał [Sound.BOSS] (R10-03: sygnały go nie dublują).
     */
    fun play(g: Game, battle: Battle, camX: Float, worldVisible: Float): Boolean {
        if (g.sound == null) return false
        return playTo(g, g.music, battle, camX, worldVisible)
    }

    /**
     * D63: ta sama decyzja co [play], ale do dowolnego [SfxSink] — pomiar głośności miksu
     * bitwy w testach JVM (bez SoundPool). Bez alokacji: Game jest swoim własnym ujściem.
     */
    internal fun playTo(g: SfxSink, music: Music?, battle: Battle, camX: Float, worldVisible: Float): Boolean {
        val fx = battle.fx
        if (fx.count == 0) return false
        var boss = false
        val center = camX + worldVisible * 0.5f
        val range = worldVisible.coerceAtLeast(1f) * 0.9f
        val half = worldVisible.coerceAtLeast(1f) * 0.5f
        var hits = 0
        var deaths = 0
        var shots = 0
        for (pass in 0..1) {
            for (i in 0 until fx.count) {
                val type = fx.type[i]
                val important = type == FxBuffer.EXPLOSION || type == FxBuffer.BASE_HIT ||
                    type == FxBuffer.BARRAGE || type == FxBuffer.EVOLVE
                if (important != (pass == 0)) continue
                val style = fxStyle(battle, fx.era[i], fx.side[i])
                val vol = (1f - kotlin.math.abs(fx.x[i] - center) / range).coerceIn(0.15f, 1f)
                val pan = ((fx.x[i] - center) / half).coerceIn(-1f, 1f)
                when (type) {
                    FxBuffer.HIT -> {
                        if (++hits > 3) continue
                        g.sfx(impactSound(style, fx.body[i]), vol * 0.35f, 35,
                            pitch = eraPitch(style), pan = pan)
                    }
                    FxBuffer.DEATH -> {
                        if (++deaths > 2) continue
                        val id = when (fx.body[i]) {
                            Body.BEAST -> Sound.DEATH_BEAST
                            Body.SIEGE, Body.CANNON, Body.VEHICLE, Body.MECH, Body.HOVER -> Sound.DEATH_MACHINE
                            else -> Sound.DEATH_HUMAN
                        }
                        g.sfx(id, vol * 0.46f, 75, pan = pan)
                    }
                    FxBuffer.EXPLOSION -> {
                        g.sfx(explosionSound(style), vol * min(1f, fx.value[i]) * 0.75f, 100,
                            pitch = eraPitch(style), pan = pan, priority = 3)
                        if (fx.value[i] > 1.1f) music?.duck(0.52f, 0.45f)
                    }
                    FxBuffer.MUZZLE -> {
                        if (++shots > 4) continue
                        val id = shotSound(fx.value[i].toInt(), style, fx.turret[i])
                        g.sfx(id, vol * (if (fx.turret[i]) 0.54f else 0.40f),
                            45, pitch = eraPitch(style), pan = pan, priority = if (fx.turret[i]) 2 else 1)
                    }
                    FxBuffer.BASE_HIT -> {
                        g.sfx(Sound.THUMP, vol * 0.74f, 80, pan = pan, priority = 3)
                        music?.duck(0.55f, 0.35f)
                    }
                    FxBuffer.EVOLVE -> {
                        if (fx.value[i] >= 2f) {
                            g.sfx(Sound.BOSS, 0.8f, 500, pan = pan, priority = 3)
                            boss = true
                            music?.duck(0.36f, 1.3f)
                        } else {
                            g.sfx(evolveSound(style), if (fx.side[i] == 0) 0.8f else 0.4f,
                                250, pan = pan, priority = 3)
                            if (fx.side[i] == 0) music?.duck(0.35f, 1f)
                        }
                    }
                    FxBuffer.GOLD -> g.sfx(Sound.COIN, 0.22f, 120, pan = pan)
                    FxBuffer.BARRAGE -> {
                        g.sfx(barrageSound(style), 0.82f, 170,
                            pitch = eraPitch(style), pan = pan, priority = 3)
                        music?.duck(0.34f, 1f)
                    }
                }
            }
        }
        return boss
    }

    /** R10-03: próbki sygnałów zamawiane przy starcie bitwy gracza (nie w powtórce ani w menu). */
    fun prepareAlerts(g: Game) {
        val sound = g.sound ?: return
        sound.prewarm(Sound.ALERT_BASE)
        sound.prewarm(Sound.ALERT_ENEMY_ERA)
        sound.prewarm(Sound.ALERT_READY)
    }

    /**
     * R10-03: sygnały ważnych zdarzeń bitwy gracza ([BattleAlerts]). Głośność i wyłącznik jak
     * efekty (Game.sfx). [fast] = tempo ×5: bez sygnału gotowości. [bossNow] = w tej sekundzie
     * zabrzmiał już Sound.BOSS, więc epoka wroga i presja milczą.
     */
    fun playAlerts(g: Game, mask: Int, fast: Boolean, bossNow: Boolean) {
        if (mask == 0 || g.sound == null) return
        if (mask and BattleAlerts.BASE_LOW != 0) {
            g.sfx(Sound.ALERT_BASE, 1f, 4_000, pitch = 0.94f, priority = 3)
            g.music?.duck(0.5f, 0.6f)
        } else if (mask and BattleAlerts.BASE_HALF != 0) {
            g.sfx(Sound.ALERT_BASE, 0.85f, 4_000, priority = 3)
        }
        if (!bossNow) {
            // Presja oblężnicza: te same dwa tony co epoka wroga, niżej i ciszej.
            if (mask and BattleAlerts.ENEMY_ERA != 0) g.sfx(Sound.ALERT_ENEMY_ERA, 0.85f, 1_500, priority = 3)
            else if (mask and BattleAlerts.SIEGE != 0)
                g.sfx(Sound.ALERT_ENEMY_ERA, 0.7f, 1_500, pitch = 0.8f, priority = 3)
        }
        if (!fast && mask and BattleAlerts.ABILITY_READY != 0)
            g.sfx(Sound.ALERT_READY, 0.45f, 2_000, priority = 1)
    }
}
