package com.centurywar.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import com.centurywar.engine.Game
import com.centurywar.engine.Host
import com.centurywar.engine.L
import com.centurywar.engine.Quality
import com.centurywar.engine.Screen
import com.centurywar.engine.TouchEvent
import com.centurywar.game.Ai
import com.centurywar.game.Battle
import com.centurywar.game.BattleConfig
import com.centurywar.game.Campaign
import com.centurywar.game.Defs
import com.centurywar.game.Doctrine
import com.centurywar.game.EraArt
import com.centurywar.game.Mode
import com.centurywar.meta.BattleHistory
import com.centurywar.meta.Profile
import com.centurywar.meta.ReplayStore
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Przegląd przed premierą na telefonie 20:9; ścieżka i narzędzia: [PrereleasePolishBase]. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class PrereleasePolishPhone20x9Test : PrereleasePolishBase() {
    @Test
    fun prereleaseTour() = prereleaseTourOn("phone20x9")
}
