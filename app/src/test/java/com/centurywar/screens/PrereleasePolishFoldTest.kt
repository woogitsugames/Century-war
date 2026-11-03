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

/**
 * Przegląd przed premierą na zewnętrznym i wewnętrznym ekranie Folda: ta sama ścieżka co
 * [PrereleasePolishTest.prereleaseTour], wydzielona w nocy 9 do osobnej klasy (czas testów).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class PrereleasePolishFoldTest : PrereleasePolishBase() {
    @Test
    fun prereleaseTour() = prereleaseTourOn(listOf("foldOuter", "foldInner"), "pre10_audit_fold.txt")
}
