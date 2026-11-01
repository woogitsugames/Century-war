package com.centurywar.meta

import com.centurywar.BuildConfig
import com.centurywar.engine.L
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Properties

class WhatsNewCatalogTest {
    private val version = "0.1.0-dev"

    @Test fun newInstallNeverInterruptsTutorial() {
        assertFalse(WhatsNewCatalog.shouldShow(false, "", version))
        assertFalse(WhatsNewCatalog.shouldShow(false, "0.0.1", version))
    }

    @Test fun returningPlayerSeesKnownVersionOnlyOnce() {
        assertTrue(WhatsNewCatalog.shouldShow(true, "", version))
        assertTrue(WhatsNewCatalog.shouldShow(true, "0.0.9", version))
        assertFalse(WhatsNewCatalog.shouldShow(true, version, version))
        assertFalse(WhatsNewCatalog.shouldShow(true, version, "unknown-version"))
    }

    @Test fun newReleaseCanTriggerAgainWithoutShowingOldNotes() {
        assertFalse(WhatsNewCatalog.shouldShow(true, version, version))
        assertTrue(WhatsNewCatalog.shouldShow(true, "0.0.9", version))
        assertEquals(null, WhatsNewCatalog.forVersion("0.0.9"))
        assertNotNull(WhatsNewCatalog.forVersion(version))
    }

    @Test fun everyListedTextHasTenTranslations() {
        assertTrue(WhatsNewCatalog.releases.isNotEmpty())
        assertEquals(WhatsNewCatalog.releases.size,
            WhatsNewCatalog.releases.map { it.version }.distinct().size)
        for (release in WhatsNewCatalog.releases) {
            assertTrue(release.version.isNotEmpty())
            assertTrue(release.textKeys.isNotEmpty())
            for (key in release.textKeys) {
                val translations = L.raw(key)
                assertNotNull(key, translations)
                assertEquals(key, L.CODES.size, translations!!.size)
                assertTrue(key, translations.all { it.isNotBlank() })
            }
        }
    }

    /** Wydanie z tagu (np. 0.1.3) musi trafić w notatki linii 0.1 (zadanie 106). */
    @Test fun currentBuildVersionHasNotes() {
        assertNotNull("Brak notatek dla " + BuildConfig.VERSION_NAME,
            WhatsNewCatalog.forVersion(BuildConfig.VERSION_NAME))
        val root = generateSequence(File("").absoluteFile) { it.parentFile }
            .first { File(it, "gradle.properties").isFile && File(it, "settings.gradle.kts").isFile }
        val props = Properties().apply { File(root, "gradle.properties").inputStream().use { load(it) } }
        val configured = props.getProperty("releaseVersionName")
        assertNotNull(configured)
        assertNotNull("Brak notatek dla releaseVersionName=$configured",
            WhatsNewCatalog.forVersion(configured!!))
    }

    @Test fun tagVersionsMapToReleaseLine() {
        assertEquals("0.1", WhatsNewCatalog.notesKey("0.1.0"))
        assertEquals("0.1", WhatsNewCatalog.notesKey("0.1.3"))
        assertEquals("0.1", WhatsNewCatalog.notesKey("v0.1.0-rc1"))
        assertEquals("0.1", WhatsNewCatalog.notesKey("0.1.2+build.7"))
        assertEquals("1.12", WhatsNewCatalog.notesKey("1.12.0"))
        assertEquals(null, WhatsNewCatalog.notesKey("unknown-version"))
        assertEquals(null, WhatsNewCatalog.notesKey(""))
        assertNotNull(WhatsNewCatalog.forVersion("0.1.0"))
        assertNotNull(WhatsNewCatalog.forVersion("0.1.7"))
        // Kto widział notatki linii przy 0.1.0, nie zobaczy ich po poprawce 0.1.1.
        assertTrue(WhatsNewCatalog.shouldShow(true, "", "0.1.1"))
        assertFalse(WhatsNewCatalog.shouldShow(true, "0.1.0", "0.1.1"))
        assertFalse(WhatsNewCatalog.shouldShow(true, "0.1.0-dev", "0.1.0"))
    }

    @Test fun releaseIdentifiersAreReleaseLines() {
        for (release in WhatsNewCatalog.releases) {
            assertEquals(release.version, WhatsNewCatalog.notesKey(release.version))
        }
    }
}
