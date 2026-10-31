package com.centurywar.screens

import com.centurywar.engine.Game
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
        sound.prewarm(hitSound(style))
        sound.prewarm(barrageSound(style))
        sound.prewarm(evolveSound(style))
        sound.prewarm(Sound.DEATH_HUMAN)
        sound.prewarm(Sound.DEATH_BEAST)
        sound.prewarm(Sound.DEATH_MACHINE)
        sound.prewarm(Sound.EXPLOSION)
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

    private fun hitSound(style: EraArt): Int = when (style) {
        EraArt.TRIBAL -> Sound.HIT_WOOD
        EraArt.BRONZE -> Sound.HIT_BRONZE
        EraArt.IRON, EraArt.FEUDAL, EraArt.POWDER, EraArt.STEAM, EraArt.COMBUSTION, EraArt.GALVANIC, EraArt.STEEL -> Sound.HIT_STEEL
        EraArt.SILICON, EraArt.SWARM, EraArt.BIOTECH, EraArt.ORBITAL -> Sound.HIT_ENERGY
    }

    private fun shotSound(projectile: Int, style: EraArt, turret: Boolean): Int {
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

    private fun barrageSound(style: EraArt): Int = when (style) {
        EraArt.TRIBAL, EraArt.BRONZE, EraArt.IRON -> Sound.BARRAGE_STONE
        EraArt.FEUDAL -> Sound.BARRAGE_FIRE
        EraArt.POWDER, EraArt.STEAM, EraArt.COMBUSTION, EraArt.GALVANIC -> Sound.BARRAGE_CANNON
        EraArt.STEEL, EraArt.SILICON, EraArt.SWARM, EraArt.BIOTECH -> Sound.BARRAGE_ROCKET
        EraArt.ORBITAL -> Sound.BARRAGE_ORBITAL
    }

    private fun evolveSound(style: EraArt): Int = when (style) {
        EraArt.TRIBAL, EraArt.BRONZE, EraArt.IRON -> Sound.EVOLVE_PRIMITIVE
        EraArt.FEUDAL -> Sound.EVOLVE_MEDIEVAL
        EraArt.POWDER, EraArt.STEAM, EraArt.COMBUSTION, EraArt.GALVANIC, EraArt.STEEL -> Sound.EVOLVE_INDUSTRIAL
        EraArt.SILICON, EraArt.SWARM, EraArt.BIOTECH, EraArt.ORBITAL -> Sound.EVOLVE_FUTURE
    }

    /** Dwie kolejki priorytetu, panoramowanie według kamery, limit drobnych głosów. */
    fun play(g: Game, battle: Battle, camX: Float, worldVisible: Float) {
        val fx = battle.fx
        if (g.sound == null) return
        if (fx.count == 0) return
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
                        g.sfx(hitSound(style), vol * 0.35f, 35, pan = pan)
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
                        g.sfx(Sound.EXPLOSION, vol * min(1f, fx.value[i]) * 0.75f, 100, pan = pan, priority = 3)
                        if (fx.value[i] > 1.1f) g.music?.duck(0.52f, 0.45f)
                    }
                    FxBuffer.MUZZLE -> {
                        if (++shots > 4) continue
                        val id = shotSound(fx.value[i].toInt(), style, fx.turret[i])
                        g.sfx(id, vol * (if (fx.turret[i]) 0.54f else 0.40f),
                            45, pan = pan, priority = if (fx.turret[i]) 2 else 1)
                    }
                    FxBuffer.BASE_HIT -> {
                        g.sfx(Sound.THUMP, vol * 0.74f, 80, pan = pan, priority = 3)
                        g.music?.duck(0.55f, 0.35f)
                    }
                    FxBuffer.EVOLVE -> {
                        if (fx.value[i] >= 2f) {
                            g.sfx(Sound.BOSS, 0.8f, 500, pan = pan, priority = 3)
                            g.music?.duck(0.36f, 1.3f)
                        } else {
                            g.sfx(evolveSound(style), if (fx.side[i] == 0) 0.8f else 0.4f,
                                250, pan = pan, priority = 3)
                            if (fx.side[i] == 0) g.music?.duck(0.35f, 1f)
                        }
                    }
                    FxBuffer.GOLD -> g.sfx(Sound.COIN, 0.22f, 120, pan = pan)
                    FxBuffer.BARRAGE -> {
                        g.sfx(barrageSound(style), 0.82f, 170, pan = pan, priority = 3)
                        g.music?.duck(0.34f, 1f)
                    }
                }
            }
        }
    }
}
