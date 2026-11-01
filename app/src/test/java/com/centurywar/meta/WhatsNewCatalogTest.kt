package com.centurywar.meta

import com.centurywar.engine.L
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

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
}
