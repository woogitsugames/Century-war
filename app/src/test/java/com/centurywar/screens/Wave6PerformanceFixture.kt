package com.centurywar.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import com.centurywar.engine.Game
import com.centurywar.engine.Host
import com.centurywar.engine.Quality
import com.centurywar.game.Battle
import com.centurywar.game.BattleConfig
import com.centurywar.game.Defs
import com.centurywar.game.Mode
import com.centurywar.game.Role
import com.centurywar.meta.Profile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import java.io.File

/** Fala 55: jeden dowódca na stronę, 12 na 12 i osiem wież. */
internal class Wave6PerformanceFixture(
    quality: Int = Quality.LOW,
    fullResolution: Boolean = false,
) {
    private val host = object : Host {
        override fun vibrate(ms: Long) {}
        override fun requestText(title: String, current: String, onResult: (String) -> Unit) {}
        override fun applyRenderScale(scale: Float) {}
        override fun applyFrameRate(fps: Int) {}
        override fun exit() {}
    }

    private val file = File.createTempFile("cw-wave6-", ".json").apply { delete() }
    val game = Game(host, Profile(file).apply {
        tutorialDone = true
        hintsEnabled = false
        hintsSeen = Profile.HINT_VALID_MASK
    }, quality).apply {
        density = 2f
        bitmapCacheMemoryClassMb = 64
        resize(1600, 720, 1600, 720)
        finishScreenTransition()
    }
    val screen = BattleScreen(game,
        BattleConfig(Mode.ENDLESS, 15255L, 1f, startEra = 8, maxEra = 8))
    val battle = screen.battle
    val bitmap = Bitmap.createBitmap(if (fullResolution) 1600 else 32,
        if (fullResolution) 720 else 32, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    init {
        game.setScreen(screen)
        game.finishScreenTransition()
        battle.wave = 55
        battle.waveTimer = 100_000f
        battle.nextBossToSpawn = 60
        for (side in battle.sides) {
            side.gold = 1_000_000_000f
            side.cap = 12
            side.slots = 4
            side.hold = true
            for (slot in 0 until Defs.MAX_TURRET_SLOTS) {
                // Parzyste epoki: rozprysk; nieparzyste: podpalenie na III stopniu.
                side.turretEra[slot] = if (slot % 2 == 0) 8 else 7
                side.turretVariant[slot] = -1
                side.turretTier[slot] = 3
                side.turretCd[slot] = 0f
            }
        }
        assertTrue(screen.commands.summonCommander())
        assertTrue(battle.summonCommander(battle.enemy))
        repeat(11) { index ->
            val role = if (index % 3 == 0) Role.RANGED else Role.HEAVY
            assertTrue(battle.spawn(battle.player, Defs.unit(8, role)))
            assertTrue(battle.spawn(battle.enemy, Defs.unit(8, role)))
        }
        val sideIndices = IntArray(2)
        for (fighter in battle.units) {
            if (!fighter.alive) continue
            val index = sideIndices[fighter.side]++
            fighter.x = if (fighter.side == 0) 700f + index * 5f else 830f + index * 5f
            fighter.prevX = fighter.x
            // Żywe oddziały nie znikają podczas pomiaru; impuls i podpalanie nadal działają.
            fighter.maxHp = 1_000_000_000f
            fighter.hp = fighter.maxHp
            fighter.dmg = if (fighter.isCommander) 80f else 1f
            fighter.cd = if (fighter.isCommander) 0f else 1f
            if (fighter.side == 1) {
                fighter.burnDps = 5f
                fighter.burnLeft = 30f
            }
        }
        assertEquals(12, sideIndices[0])
        assertEquals(12, sideIndices[1])
        assertEquals(2, battle.units.count { it.alive && it.isCommander })
        assertEquals(8, battle.sides.sumOf { it.turretTier.count { tier -> tier == 3 } })
    }

    fun update() = screen.update(Battle.STEP)
    fun draw() = screen.render(canvas)
    fun frame() { update(); draw() }
    fun close() { bitmap.recycle() }
}
