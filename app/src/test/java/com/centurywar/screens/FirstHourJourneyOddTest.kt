package com.centurywar.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import com.centurywar.engine.Button
import com.centurywar.engine.Game
import com.centurywar.engine.Host
import com.centurywar.engine.L
import com.centurywar.engine.Quality
import com.centurywar.engine.Screen
import com.centurywar.engine.TouchEvent
import com.centurywar.game.Ai
import com.centurywar.game.Campaign
import com.centurywar.game.Mode
import com.centurywar.game.Research
import com.centurywar.game.SnapshotStore
import com.centurywar.meta.BattleHistory
import com.centurywar.meta.Profile
import com.centurywar.meta.ReplayStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.Locale

/** Pierwsza godzina na telefonie (języki z nieparzystych pozycji, domyślnie en); ścieżka: [FirstHourJourneyBase]. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class FirstHourJourneyOddTest : FirstHourJourneyBase() {
    @Test
    fun firstHour() = firstHourOn("phone", 1)
}
