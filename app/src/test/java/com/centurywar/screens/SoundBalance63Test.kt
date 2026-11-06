package com.centurywar.screens

import com.centurywar.engine.AudioMeter
import com.centurywar.engine.RecordedMixer
import com.centurywar.engine.SfxMixModel
import com.centurywar.engine.Sound
import com.centurywar.engine.SoundSynth
import com.centurywar.game.Ai
import com.centurywar.game.Battle
import com.centurywar.game.BattleConfig
import com.centurywar.game.Defs
import com.centurywar.game.EraArt
import com.centurywar.game.Mode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.log10

/**
 * D63: wyrównana głośność rodzin efektów, brak trzasków na granicach nut, różne barwy epok
 * (trafienie, wybuch) i miks bitwy na tym samym poziomie co przed zmianą (muzyka ~7 dB niżej).
 */
class SoundBalance63Test {
    private val rate = SoundSynth.SAMPLE_RATE

    private val families = mapOf(
        "trafienia" to intArrayOf(Sound.HIT_WOOD, Sound.HIT_BRONZE, Sound.HIT_STEEL, Sound.HIT_ENERGY, Sound.HIT_POWDER),
        "strzały" to IntArray(SoundSynth.SHOT_PLASMA - SoundSynth.SHOT_SLING + 1) { SoundSynth.SHOT_SLING + it },
        "wieże" to IntArray(5) { SoundSynth.TURRET_PRIMITIVE + it },
        "śmierci" to intArrayOf(Sound.DEATH_HUMAN, Sound.DEATH_BEAST, Sound.DEATH_MACHINE),
        "wybuchy" to intArrayOf(Sound.EXPLOSION, Sound.EXPLOSION_PRIMITIVE, Sound.EXPLOSION_ENERGY),
        "ostrzał" to IntArray(5) { SoundSynth.BARRAGE_STONE + it },
        "ewolucje" to IntArray(4) { SoundSynth.EVOLVE_PRIMITIVE + it },
        "karty" to IntArray(4) { SoundSynth.CARD_COMMON + it },
    )

    /** Przed D63 rozrzut w rodzinie wynosił do 3,8 LU (RMS zamiast głośności K-ważonej). */
    @Test fun familiesHaveEvenLoudness() {
        for ((name, ids) in families) {
            var lo = Double.MAX_VALUE
            var hi = -Double.MAX_VALUE
            for (id in ids) for (v in 0 until SoundSynth.VARIANTS) {
                val l = AudioMeter.eventLoudness(SoundSynth.render(id, v), rate)
                lo = minOf(lo, l); hi = maxOf(hi, l)
            }
            assertTrue("Rozrzut głośności w rodzinie $name: ${hi - lo} LU", hi - lo <= 1.0)
        }
    }

    /** Kolejne nuty (ewolucja, zwycięstwo, karty, monety) wchodzą bez skoku fazy/obwiedni. */
    @Test fun noteBoundariesDoNotClick() {
        for (id in intArrayOf(Sound.COIN, Sound.PURCHASE, Sound.ERROR, Sound.CARD_EPIC, Sound.CARD_LEGENDARY,
                Sound.STAR, Sound.VICTORY, Sound.DEFEAT, Sound.MEDALS, Sound.RANK_UP)) {
            for (v in 0 until SoundSynth.VARIANTS) {
                val pcm = SoundSynth.render(id, v)
                // Faza ataku (szum) ma swoje ostre zbocza; liczymy od 45 ms.
                val score = AudioMeter.clickScore(pcm, (rate * 0.045).toInt())
                assertTrue("Trzask w efekcie $id/$v: $score", score < 6.0)
                assertEquals("Efekt $id/$v nie kończy się ciszą", 0f, pcm.last(), 0.002f)
                assertTrue("Urwany ogon $id/$v", AudioMeter.tail(pcm, rate) < -18.0)
            }
        }
    }

    /** Epoki kamienia, żelaza, prochu i krzemu mają różne barwy trafień i wybuchów (nie tylko wysokość). */
    @Test fun eraGroupsSoundDifferent() {
        val stone = EraArt.TRIBAL
        val iron = EraArt.IRON
        val powder = EraArt.POWDER
        val silicon = EraArt.SILICON
        val hits = listOf(stone, iron, powder, silicon).map { BattleSfx.impactSound(it, com.centurywar.game.Body.HUMAN) }
        assertEquals("Trafienia czterech grup epok to różne próbki", 4, hits.toSet().size)
        val booms = listOf(stone, powder, silicon).map { BattleSfx.explosionSound(it) }
        assertEquals("Wybuchy trzech grup epok to różne próbki", 3, booms.toSet().size)
        for ((label, ids) in listOf("trafienia" to hits, "wybuchy" to booms)) {
            val bands = ids.map { AudioMeter.bands(SoundSynth.render(it), rate) }
            for (i in bands.indices) for (j in i + 1 until bands.size) {
                val d = AudioMeter.spectralDistance(bands[i], bands[j])
                assertTrue("$label ${ids[i]} i ${ids[j]} brzmią prawie tak samo: $d dB", d >= 3.0)
            }
        }
    }

    /**
     * Miks efektów prawdziwej bitwy (seed 12, 4 min, suwak 0,8) zostaje ok. −35 LUFS, więc pętle
     * muzyki (pliki −16 LUFS × suwak 0,5 × [RecordedMixer.LOOP_GAIN]) grają ~7 dB pod efektami.
     */
    @Test fun battleMixKeepsMusicSevenDbBelow() {
        val seconds = 240
        val model = SfxMixModel(rate, seconds, 0.8f)
        val b = Battle(BattleConfig(Mode.SKIRMISH, 12L, 1f))
        b.playerAi = Ai(b, 0)
        var frame = 0
        val visible = 640f
        while (frame < seconds * 30 && !b.over) {
            b.step(); b.step()
            var ally = -1e9f
            var enemy = 1e9f
            for (u in b.units) {
                if (!u.alive) continue
                if (u.side == 0) ally = maxOf(ally, u.x) else enemy = minOf(enemy, u.x)
            }
            val mid = if (ally > -1e8f && enemy < 1e8f) (ally + enemy) * 0.5f else Defs.LANE * 0.5f
            model.now = frame * 1000L / 30
            BattleSfx.playTo(model, null, b, (mid - visible * 0.5f).coerceIn(-120f, Defs.LANE + 120f - visible), visible)
            b.fx.count = 0
            model.renderUntil(frame + 1, 30)
            frame++
        }
        val sfx = AudioMeter.integrated(model.left, model.right, rate)
        val music = -16.0 + 20.0 * log10(0.5 * RecordedMixer.LOOP_GAIN)
        println("D63 miks bitwy: efekty %.1f LUFS, muzyka %.1f LUFS, odstęp %.1f dB".format(sfx, music, sfx - music))
        assertTrue("Miks efektów zmienił poziom: $sfx LUFS (było −35,3)", abs(sfx + 35.3) <= 1.0)
        assertTrue("Muzyka nie jest ok. 7 dB pod efektami: ${sfx - music}", sfx - music in 6.0..8.0)
        assertTrue("Przester miksu", AudioMeter.peak(model.left) < 0.9 && AudioMeter.peak(model.right) < 0.9)
    }
}
