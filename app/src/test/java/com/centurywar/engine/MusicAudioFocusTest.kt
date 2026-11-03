package com.centurywar.engine

import android.media.AudioManager
import org.robolectric.RuntimeEnvironment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Fokus audio: gra z własną muzyką bierze AUDIOFOCUS_GAIN, ale gdy cudza muzyka już gra, nie przerywa jej;
 * ale przy głośności muzyki 0 w ogóle o niego nie prosi i oddaje go od razu po ściszeniu,
 * więc Spotify i inne aplikacje grają dalej.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class MusicAudioFocusTest {
    private val am: AudioManager =
        RuntimeEnvironment.getApplication().getSystemService(AudioManager::class.java)

    /** Synteza bez AudioTracka (bufor -1): wątek kończy się od razu, liczy się tylko fokus. */
    private fun music(): Music = Music(MusicCatalog.EMPTY, null, am) { _, _, _ -> -1 }

    @Test fun mutedMusicNeverRequestsFocus() {
        val m = music()
        try {
            m.setVolume(0f)
            m.resume()
            m.setEra(3)
            m.setIntensity(1f)
            m.pause()
            m.resume()
            assertNull("głośność 0: brak żądania fokusu", shadowOf(am).lastAudioFocusRequest)
        } finally { m.release() }
    }

    @Test fun audibleMusicTakesGainAndMutingReleasesIt() {
        val m = music()
        try {
            m.setVolume(0f)
            m.resume()
            assertNull(shadowOf(am).lastAudioFocusRequest)
            m.setVolume(0.5f)
            val req = shadowOf(am).lastAudioFocusRequest
            assertNotNull("słyszalna muzyka prosi o fokus", req)
            assertEquals(AudioManager.AUDIOFOCUS_GAIN, req!!.audioFocusRequest.focusGain)
            // Suwak na 0: fokus oddany od razu (Spotify może wznowić).
            m.setVolume(0f)
            assertSame(req.audioFocusRequest, shadowOf(am).lastAbandonedAudioFocusRequest)
        } finally { m.release() }
    }

    @Test fun lostFocusIsNotStolenBackBySlider() {
        val m = music()
        try {
            m.setVolume(0.5f)
            m.resume()
            val first = shadowOf(am).lastAudioFocusRequest!!
            // Gracz włącza Spotify: gra traci fokus i milknie.
            first.listener.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS)
            m.setVolume(0.7f)
            m.setEra(2)
            assertSame("bez ponownego żądania w tej samej sesji", first, shadowOf(am).lastAudioFocusRequest)
            // Powrót do gry z tła prosi o fokus na nowo.
            m.pause()
            m.resume()
            assertNotSame(first, shadowOf(am).lastAudioFocusRequest)
        } finally { m.release() }
    }

    @Test fun pauseAbandonsFocusAndResumeAsksAgain() {
        val m = music()
        try {
            m.setVolume(0.5f)
            assertNull("w tle (przed resume) bez fokusu", shadowOf(am).lastAudioFocusRequest)
            m.resume()
            val req = shadowOf(am).lastAudioFocusRequest!!.audioFocusRequest
            m.pause()
            assertSame(req, shadowOf(am).lastAbandonedAudioFocusRequest)
        } finally { m.release() }
    }

    @Test fun otherAppMusicIsNotInterrupted() {
        val m = music()
        try {
            m.setVolume(0.5f)
            shadowOf(am).setIsMusicActive(true)
            m.resume()
            m.setEra(2)
            m.setVolume(0.7f)
            assertNull("cudza muzyka gra: bez żądania fokusu", shadowOf(am).lastAudioFocusRequest)
            // Gracz wyłączył Spotify i wrócił do gry: muzyka gry wraca.
            m.pause()
            shadowOf(am).setIsMusicActive(false)
            m.resume()
            assertNotNull(shadowOf(am).lastAudioFocusRequest)
        } finally { m.release() }
    }
}
