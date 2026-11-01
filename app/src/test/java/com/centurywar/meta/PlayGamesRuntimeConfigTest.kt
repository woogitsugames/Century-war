package com.centurywar.meta

import com.centurywar.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Weryfikacja inicjalizacji z zasobów wygenerowanych przez Gradle. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PlayGamesRuntimeConfigTest {
    @Test fun isConfiguredMatchesGeneratedResources() {
        val context = RuntimeEnvironment.getApplication()
        val enabledInResources = context.resources.getBoolean(R.bool.play_games_configured)
        assertEquals(enabledInResources, PlayGamesLeaderboards.isConfigured(context))
    }
}
