package com.centurywar.screens

import com.centurywar.engine.SecureLinks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AboutScreenTest {
    @Test fun externalLinksRequireHttps() {
        assertTrue(SecureLinks.isHttps("https://example.org/privacy"))
        assertFalse(SecureLinks.isHttps(""))
        assertFalse(SecureLinks.isHttps("http://example.org/privacy"))
        assertFalse(SecureLinks.isHttps("javascript:alert(1)"))
        assertFalse(SecureLinks.isHttps("https://user:pass@example.org/"))
        assertFalse(SecureLinks.isHttps("https://"))
    }

    @Test fun licenseEntriesHaveUniqueNamesAndSafeLinks() {
        val entries = LicenseCatalog.app
        assertTrue(entries.isNotEmpty())
        assertEquals(entries.size, entries.map { it.name }.toSet().size)
        entries.forEach {
            assertTrue(it.name.isNotBlank())
            assertTrue(it.owner.isNotBlank())
            assertTrue(it.license.isNotBlank())
            assertTrue(SecureLinks.isHttps(it.url))
        }
    }
}
