package com.centurywar.screens

import com.centurywar.engine.Button
import com.centurywar.engine.L
import com.centurywar.engine.Quality
import com.centurywar.game.Defs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Dzień 70: Kodeks pokazuje nowe jednostki epok (Tarczownik, Lekki wóz, Taran, Strzelec
 * z osłoną) na stronie wariantów, na telefonie 720p i zewnętrznym Foldzie, tekst 130%.
 * Pliki: build/screenshots/codex70_*.png
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class EraRecruits70CodexScreenshotTest : ScreenshotTestBase() {
    @Test
    fun codexShowsEraRecruits() {
        val originalLang = L.lang
        try {
            for ((tag, dims) in mapOf(
                "phone720" to intArrayOf(1600, 720, 2),
                "foldOuter" to intArrayOf(904, 2316, 3),
            )) for (lang in arrayOf("pl", "de")) {
                val g = game(dims[0], dims[1], dims[2] * 0.9f,
                    if (tag == "phone720") Quality.LOW else Quality.HIGH)
                g.profile.lang = lang
                g.profile.textScale = 130
                g.profile.hintsEnabled = false
                g.onLanguageChanged()
                g.applyAccessibility()
                for (era in 0 until Defs.ERA_COUNT) {
                    val recruit = Defs.spec(era).recruit ?: continue
                    val key = "$tag/$lang/era$era"
                    val codex = CodexScreen(g, era)
                    g.setScreen(codex)
                    codex.showCategory(2)
                    @Suppress("UNCHECKED_CAST")
                    val buttons = field(codex, "variantBtn").get(codex) as Array<Button>
                    assertTrue(key, buttons[0].visible && buttons[1].visible)
                    assertEquals(key, L["v_base"], buttons[0].label)
                    assertEquals(key, L[recruit.nameKey], buttons[1].label)
                    // Domyślnie wybrana nowa jednostka: jej nazwa na karcie roli i opis cechy w nagłówku.
                    assertTrue(key, buttons[1].selected)
                    assertEquals(key, L[recruit.nameKey], codex.unitTitleForTest(recruit.role.ordinal))
                    assertEquals(key, L[recruit.descKey], codex.variantHeaderForTest())
                    shot(g, "codex70_${tag}_${lang}_era${era}", 1)
                    assertNoCollisions(g, codex, "codex70_$key", "codex")
                    if (lang == "pl" && era == 0) {
                        // Przewinięcie do statystyk kart i spisu wariantów (z nowymi jednostkami).
                        codex.scrollForTest(0.6f)
                        shot(g, "codex70_${tag}_${lang}_era${era}_scroll", 1)
                        codex.scrollForTest(0f)
                    }
                    codex.onClick(buttons[0])
                    assertEquals(key, L[Defs.unit(era, recruit.role).nameKey],
                        codex.unitTitleForTest(recruit.role.ordinal))
                    assertTrue(key, L[Defs.unit(era, recruit.role).nameKey] in codex.variantHeaderForTest())
                    if (lang == "pl") shot(g, "codex70_${tag}_${lang}_era${era}_base", 1)
                }
            }
        } finally {
            L.lang = originalLang
        }
    }

    /** Dymek okna wyboru w epoce jednej roli: tekst o nowej jednostce, mieści się przy 130%. */
    @Test
    fun recruitChoiceHint() {
        val originalLang = L.lang
        try {
            for ((tag, dims) in mapOf(
                "phone720" to intArrayOf(1600, 720, 2),
                "foldOuter" to intArrayOf(904, 2316, 3),
            )) for (lang in arrayOf("pl", "de", "tr")) {
                val g = game(dims[0], dims[1], dims[2] * 0.9f,
                    if (tag == "phone720") Quality.LOW else Quality.HIGH)
                g.profile.lang = lang
                g.profile.textScale = 130
                g.profile.hintsEnabled = true
                g.profile.eraIntrosEnabled = false
                g.profile.hintsSeen = com.centurywar.meta.Profile.HINT_VALID_MASK and
                    (1L shl HintOverlay.RECRUIT).inv()
                g.onLanguageChanged()
                g.applyAccessibility()
                val bs = BattleScreen(g, com.centurywar.game.BattleConfig(com.centurywar.game.Mode.SKIRMISH,
                    7000L, 1f, startEra = 0, maxEra = 0, eraRecruits = true))
                g.setScreen(bs)
                val hints = field(bs, "hints").get(bs) as HintOverlay
                shot(g, "codex70_hint_${tag}_$lang", 24)
                assertEquals("$tag/$lang", HintOverlay.RECRUIT, hints.activeId)
                val panel = android.graphics.RectF(hints.panelRect)
                assertTrue("$tag/$lang: dymek poza ekranem $panel",
                    panel.width() > 0f && panel.left >= 0f && panel.right <= g.width && panel.bottom <= g.height)
            }
        } finally {
            L.lang = originalLang
        }
    }
}
