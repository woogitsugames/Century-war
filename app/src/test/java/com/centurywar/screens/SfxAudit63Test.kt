package com.centurywar.screens

import com.centurywar.engine.AudioMeter
import com.centurywar.engine.SfxMixModel
import com.centurywar.engine.SoundSynth
import com.centurywar.game.Ai
import com.centurywar.game.Battle
import com.centurywar.game.BattleConfig
import com.centurywar.game.Body
import com.centurywar.game.Defs
import com.centurywar.game.EraArt
import com.centurywar.game.Mode
import com.centurywar.game.Role
import org.junit.Test
import java.io.File
import java.util.Locale

/**
 * D63: przegląd dźwięku na żądanie (`D63_AUDIO_OUT=/katalog ./gradlew ... --tests '*SfxAudit63*'`).
 * Zapisuje wszystkie efekty do WAV, raport głośności/kliknięć/podobieństwa epok oraz 4-minutowy
 * miks efektów prawdziwej bitwy AI (seed 12) przez [BattleSfx.playTo] i model [SfxMixModel].
 */
class SfxAudit63Test {
    private val rate = SoundSynth.SAMPLE_RATE

    @Test fun exportAudit() {
        val outPath = System.getenv("D63_AUDIO_OUT") ?: return
        val out = File(outPath).apply { mkdirs() }
        val report = StringBuilder()
        effectTable(out, report)
        eraTable(out, report)
        battleMix(out, report)
        File(out, "raport.txt").writeText(report.toString())
        println(report)
    }

    private fun names(): Array<String> {
        val n = Array(SoundSynth.COUNT) { "#$it" }
        for (f in SoundSynth::class.java.declaredFields) {
            if (f.type != Int::class.javaPrimitiveType || !java.lang.reflect.Modifier.isStatic(f.modifiers)) continue
            val v = f.getInt(null)
            if (f.name in setOf("SAMPLE_RATE", "VERSION", "VARIANTS", "MAX_CACHE_BYTES", "COUNT")) continue
            if (v in n.indices && n[v].startsWith("#")) n[v] = f.name
        }
        return n
    }

    /** Typowa głośność wywołania (vol) w grze; efektywny poziom = LUFS + 20·log(vol·0,8·0,42). */
    private fun typicalVol(id: Int): Float = when (id) {
        in SoundSynth.HIT_WOOD..SoundSynth.HIT_ENERGY, SoundSynth.HIT, SoundSynth.HIT_POWDER -> 0.35f
        in SoundSynth.DEATH_HUMAN..SoundSynth.DEATH_MACHINE, SoundSynth.DEATH -> 0.46f
        in SoundSynth.SHOT_SLING..SoundSynth.SHOT_PLASMA, SoundSynth.SHOT, SoundSynth.ARROW, SoundSynth.LASER -> 0.40f
        in SoundSynth.TURRET_PRIMITIVE..SoundSynth.TURRET_ENERGY -> 0.54f
        SoundSynth.EXPLOSION, SoundSynth.EXPLOSION_PRIMITIVE, SoundSynth.EXPLOSION_ENERGY -> 0.75f
        in SoundSynth.BARRAGE_STONE..SoundSynth.BARRAGE_ORBITAL -> 0.82f
        SoundSynth.THUMP -> 0.74f
        in SoundSynth.EVOLVE_PRIMITIVE..SoundSynth.EVOLVE_FUTURE, SoundSynth.EVOLVE, SoundSynth.BOSS -> 0.8f
        SoundSynth.COIN -> 0.22f
        SoundSynth.CLICK -> 0.6f
        SoundSynth.ERROR -> 0.5f
        SoundSynth.PURCHASE -> 0.5f
        SoundSynth.STAR -> 0.46f
        SoundSynth.ERA_UNLOCK -> 0.52f
        SoundSynth.RANK_UP -> 0.62f
        SoundSynth.MEDALS, SoundSynth.ACHIEVEMENT, SoundSynth.REWARD -> 0.78f
        in SoundSynth.CARD_COMMON..SoundSynth.CARD_LEGENDARY -> 0.48f
        SoundSynth.VICTORY, SoundSynth.DEFEAT -> 0.85f
        SoundSynth.ALERT_BASE -> 0.85f
        SoundSynth.ALERT_ENEMY_ERA -> 0.85f
        SoundSynth.ALERT_READY -> 0.45f
        else -> 0.5f
    }

    private fun effectTable(out: File, r: StringBuilder) {
        val names = names()
        r.append("== Efekty (wariant 0; LUFS-M = maks. głośność chwilowa 400 ms; LUFS-z = głośność K-ważona samego zdarzenia; ef. = LUFS-M przy typowym vol i suwaku 0,8; start = szczyt 1. ms wzgl. szczytu; ogon = 40 ms przed wyciszeniem wzgl. RMS; klik = pik 2. różnicy / p95 po 15 ms)\n")
        r.append(String.format(Locale.ROOT, "%-3s %-18s %5s %7s %7s %7s %7s %7s %6s %7s %7s %6s %6s\n",
            "id", "nazwa", "s", "RMSdB", "pikdB", "LUFS-M", "LUFS-z", "ef.LUFS", "vol", "start", "koniec", "ogon", "klik"))
        val all = ArrayList<FloatArray>()
        for (id in 0 until SoundSynth.COUNT) {
            for (v in 0 until SoundSynth.VARIANTS) {
                val pcm = SoundSynth.render(id, v)
                AudioMeter.writeWav(File(out, "efekty/%02d_%s_%d.wav".format(id, names[id].lowercase(), v)), rate, pcm)
                all.add(pcm)
                if (v != 0) continue
                val peak = AudioMeter.peak(pcm)
                val m = AudioMeter.momentaryMax(pcm, rate)
                val vol = typicalVol(id)
                val eff = m + AudioMeter.db(vol * 0.8 * 0.42)
                val ms = rate / 1000
                // start: szczyt pierwszej 1 ms względem szczytu klipu; koniec: ostatnie 3 ms.
                val start = AudioMeter.db(AudioMeter.peak(pcm, 0, ms)) - AudioMeter.db(peak)
                val end = AudioMeter.db(AudioMeter.peak(pcm, pcm.size - 3 * ms)) - AudioMeter.db(peak)
                r.append(String.format(Locale.ROOT, "%-3d %-18s %5.2f %7.1f %7.1f %7.1f %7.1f %7.1f %6.2f %7.1f %7.1f %6.1f %6.1f\n",
                    id, names[id], pcm.size / rate.toFloat(), AudioMeter.db(AudioMeter.rms(pcm)),
                    AudioMeter.db(peak), m, AudioMeter.eventLoudness(pcm, rate), eff, vol, start, end,
                    AudioMeter.tail(pcm, rate), AudioMeter.clickScore(pcm, 15 * ms)))
            }
        }
        val gap = FloatArray((rate * 0.12f).toInt())
        AudioMeter.writeWav(File(out, "wszystkie_efekty.wav"), rate, concat(all, gap))
    }

    private fun concat(list: List<FloatArray>, gap: FloatArray): FloatArray {
        var n = 0
        for (a in list) n += a.size + gap.size
        val o = FloatArray(n)
        var p = 0
        for (a in list) { a.copyInto(o, p); p += a.size + gap.size }
        return o
    }

    private class EraSounds(val label: String, val style: EraArt, val pitch: Float, val hit: Int,
                            val explosion: Int, val turret: Int, val barrage: Int, val evolve: Int, val shots: List<Int>)

    private fun eras(): List<EraSounds> {
        val list = ArrayList<EraSounds>()
        for (era in 0 until Defs.ERA_COUNT) {
            for (variant in -1 until Defs.specs[era].variants.size) {
                val style = Defs.sideArt(era, variant)
                val shots = LinkedHashSet<Int>()
                for (role in Role.entries) {
                    val u = Defs.unit(era, role, variant)
                    if (u.projectile != com.centurywar.game.Proj.NONE) shots.add(BattleSfx.shotSound(u.projectile, style, false))
                }
                list.add(EraSounds("e$era" + (if (variant >= 0) "v$variant" else "") + " " + style.name,
                    style, BattleSfx.eraPitch(style), BattleSfx.impactSound(style, Body.HUMAN),
                    BattleSfx.explosionSound(style), BattleSfx.shotSound(Defs.turret(era, variant).projectile, style, true),
                    BattleSfx.barrageSound(style), BattleSfx.evolveSound(style), shots.toList()))
            }
        }
        return list
    }

    private fun eraTable(out: File, r: StringBuilder) {
        val names = names()
        val eras = eras()
        r.append("\n== Epoki: próbka (wysokość odtwarzania)\n")
        for (e in eras) {
            r.append(String.format(Locale.ROOT, "%-18s p=%.2f trafienie=%s wybuch=%s wieża=%s ostrzał=%s ewolucja=%s strzały=%s\n",
                e.label, e.pitch, names[e.hit], names[e.explosion], names[e.turret], names[e.barrage],
                names[e.evolve], e.shots.joinToString(",") { names[it] }))
        }
        // Podobieństwo: ta sama próbka w różnych stylach (różni je tylko wysokość) i odległość widma.
        val styles = eras.distinctBy { it.style }
        for ((label, pick) in listOf<Pair<String, (EraSounds) -> Int>>(
            "trafienie" to { it.hit }, "wybuch" to { it.explosion }, "wieża" to { it.turret })) {
            r.append("\n== Podobieństwo epok: $label (odległość kształtu widma w dB; <2 = prawie to samo)\n")
            val spec = styles.map { AudioMeter.bands(AudioMeter.resample(SoundSynth.render(pick(it)), it.pitch), rate) }
            val clips = ArrayList<FloatArray>()
            for (i in styles.indices) {
                clips.add(AudioMeter.resample(SoundSynth.render(pick(styles[i])), styles[i].pitch))
                var nearest = Double.MAX_VALUE
                var who = ""
                for (j in styles.indices) if (j != i) {
                    val d = AudioMeter.spectralDistance(spec[i], spec[j])
                    if (d < nearest) { nearest = d; who = styles[j].style.name }
                }
                r.append(String.format(Locale.ROOT, "%-11s %-16s najbliżej %-11s %.1f dB\n",
                    styles[i].style.name, names[pick(styles[i])], who, nearest))
            }
            var cross = Double.MAX_VALUE
            var pair = ""
            for (i in styles.indices) for (j in i + 1 until styles.size) {
                if (pick(styles[i]) == pick(styles[j])) continue
                val d = AudioMeter.spectralDistance(spec[i], spec[j])
                if (d < cross) { cross = d; pair = styles[i].style.name + "/" + styles[j].style.name }
            }
            r.append(String.format(Locale.ROOT, "próbek: %d; najmniejsza odległość między różnymi próbkami: %.1f dB (%s)\n",
                styles.map(pick).toSet().size, if (cross == Double.MAX_VALUE) 0.0 else cross, pair))
            AudioMeter.writeWav(File(out, "epoki_$label.wav".replace('ż', 'z')), rate,
                concat(clips, FloatArray((rate * 0.35f).toInt())))
        }
    }

    private fun battleMix(out: File, r: StringBuilder) {
        val seconds = 240
        val model = SfxMixModel(rate, seconds, 0.8f)
        val b = Battle(BattleConfig(Mode.SKIRMISH, 12L, 1f))
        b.playerAi = Ai(b, 0)
        var frame = 0
        val frames = seconds * 30
        val visible = 640f
        while (frame < frames && !b.over) {
            b.step(); b.step()
            var ally = -1e9f
            var enemy = 1e9f
            for (u in b.units) {
                if (!u.alive) continue
                if (u.side == 0) ally = maxOf(ally, u.x) else enemy = minOf(enemy, u.x)
            }
            val mid = if (ally > -1e8f && enemy < 1e8f) (ally + enemy) * 0.5f else Defs.LANE * 0.5f
            val camX = (mid - visible * 0.5f).coerceIn(-120f, Defs.LANE + 120f - visible)
            model.now = frame * 1000L / 30
            BattleSfx.playTo(model, null, b, camX, visible)
            b.fx.count = 0
            model.renderUntil(frame + 1, 30)
            frame++
        }
        val lufs = AudioMeter.integrated(model.left, model.right, rate)
        AudioMeter.writeWav(File(out, "bitwa_seed12_efekty.wav"), rate, model.left, model.right)
        r.append(String.format(Locale.ROOT,
            "\n== Miks bitwy AI (seed 12, %.0f s, suwak efektów 0,8): %.1f LUFS, szczyt %.1f dBFS, głosów %d, odrzuconych %d, epoka gracza %d\n",
            frame / 30f, lufs, AudioMeter.db(maxOf(AudioMeter.peak(model.left), AudioMeter.peak(model.right))),
            model.played, model.rejected, b.player.era))
        r.append("   udział zdarzeń: ").append(model.histogram(names())).append('\n')
    }

}
