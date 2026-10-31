package com.centurywar.screens

import com.centurywar.engine.Game
import com.centurywar.engine.Host
import com.centurywar.engine.Quality
import com.centurywar.engine.Sound
import com.centurywar.game.Battle
import com.centurywar.game.BattleConfig
import com.centurywar.game.FxBuffer
import com.centurywar.game.Mode
import com.centurywar.meta.Profile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.atomic.AtomicIntegerArray

/** Powtórka i bitwa używają tej samej ścieżki BattleSfx -> Game.sfx -> Sound. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReplaySfxTest {
    @Test fun replayEffectsObeyMuteAndSfxVolume() {
        val host = object : Host {
            override fun vibrate(ms: Long) {}
            override fun requestText(title: String, current: String, onResult: (String) -> Unit) {}
            override fun applyRenderScale(scale: Float) {}
            override fun applyFrameRate(fps: Int) {}
            override fun exit() {}
        }
        val profileFile = File.createTempFile("replay-sfx", ".json").apply { delete() }
        val profile = Profile(profileFile)
        val game = Game(host, profile, Quality.LOW)
        val sound = Sound(RuntimeEnvironment.getApplication())
        game.sound = sound
        try {
            val requests = Sound::class.java.getDeclaredField("requested").apply { isAccessible = true }
                .get(sound) as AtomicIntegerArray
            val battle = Battle(BattleConfig(Mode.SKIRMISH, 73L, 1f))
            // Ten wariant nie jest częścią początkowego prewarm UI.
            battle.fx.add(FxBuffer.BARRAGE, 100f, 0f, 0, eraId = 8)

            profile.sound = false
            profile.sfxVolume = 0.8f
            game.applySfxVolume()
            BattleSfx.play(game, battle, 0f, 600f)
            assertEquals(0, requests.get(Sound.BARRAGE_ORBITAL * com.centurywar.engine.SoundSynth.VARIANTS))

            profile.sound = true
            profile.sfxVolume = 0f
            game.applySfxVolume()
            BattleSfx.play(game, battle, 0f, 600f)
            assertEquals(0, requests.get(Sound.BARRAGE_ORBITAL * com.centurywar.engine.SoundSynth.VARIANTS))

            profile.sfxVolume = 0.25f
            game.applySfxVolume()
            BattleSfx.play(game, battle, 0f, 600f)
            assertTrue("Brak odtworzenia przy włączonych efektach",
                requests.get(Sound.BARRAGE_ORBITAL * com.centurywar.engine.SoundSynth.VARIANTS) > 0)
            assertEquals(0.25f, sound.volume, 0f)
            assertTrue(sound.enabled)
        } finally {
            sound.release()
        }
    }

    @Test fun eraPrewarmUsesSelectedVariantWeapons() {
        val host = object : Host {
            override fun vibrate(ms: Long) {}
            override fun requestText(title: String, current: String, onResult: (String) -> Unit) {}
            override fun applyRenderScale(scale: Float) {}
            override fun applyFrameRate(fps: Int) {}
            override fun exit() {}
        }
        val f = File.createTempFile("era-sfx", ".json").apply { delete() }
        val game = Game(host, Profile(f), Quality.LOW)
        val sound = Sound(RuntimeEnvironment.getApplication())
        game.sound = sound
        try {
            val requests = Sound::class.java.getDeclaredField("requested").apply { isAccessible = true }
                .get(sound) as AtomicIntegerArray
            // Styl galwaniczny i jego uzbrojenie powinny zamówić laser i plazmę.
            BattleSfx.prepareEra(game, 5, 1)
            assertTrue(requests.get(Sound.SHOT_LASER * com.centurywar.engine.SoundSynth.VARIANTS) > 0)
            assertTrue(requests.get(Sound.SHOT_PLASMA * com.centurywar.engine.SoundSynth.VARIANTS) > 0)
        } finally {
            sound.release()
        }
    }
}
