package com.centurywar

import com.centurywar.engine.L
import java.io.File
import java.util.Properties
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

class ReleaseConfigTest {
    private fun xml(path: String): Element =
        DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File("src/main/$path")).documentElement

    /**
     * Kopia Androida obejmuje wyłącznie postęp gracza. Reguły include wykluczają resztę:
     * kolejkę wyników (score-queue*), dzienniki awarii, zapis bitwy, powtórki i kopie importu.
     */
    private fun assertOnlyProgressBackedUp(element: Element) {
        assertEquals(0, element.getElementsByTagName("exclude").length)
        val nodes = element.getElementsByTagName("include")
        val included = (0 until nodes.length).map { i ->
            val rule = nodes.item(i) as Element
            assertEquals("file", rule.getAttribute("domain"))
            rule.getAttribute("path")
        }
        assertEquals(listOf("profile.json", "history.json"), included)
        for (path in included) {
            assertFalse(path, path.startsWith("score-queue"))
            assertFalse(path, path.startsWith("crash"))
            assertFalse(path, path.endsWith(".snap"))
            assertFalse(path, path == ".")
        }
    }

    @Test fun localesMatchInGameLanguages() {
        val root = xml("res/xml/locales_config.xml")
        assertEquals("locale-config", root.tagName)
        val locales = root.getElementsByTagName("locale")
        val names = (0 until locales.length).map {
            (locales.item(it) as Element).getAttribute("android:name")
        }
        assertEquals(L.CODES.toList(), names)
    }

    /** Postęp wraca po zmianie telefonu (114); sekret rankingu tylko w szyfrowanej kopii. */
    @Test fun onlyPlayerProgressIsAutomaticallyBackedUp() {
        val legacy = xml("res/xml/backup_rules.xml")
        assertOnlyProgressBackedUp(legacy)
        val legacyIncludes = legacy.getElementsByTagName("include")
        for (i in 0 until legacyIncludes.length) {
            assertEquals("clientSideEncryption",
                (legacyIncludes.item(i) as Element).getAttribute("requireFlags"))
        }
        val rules = xml("res/xml/data_extraction_rules.xml")
        for (tag in listOf("cloud-backup", "device-transfer")) {
            val elements = rules.getElementsByTagName(tag)
            assertEquals(1, elements.length)
            assertOnlyProgressBackedUp(elements.item(0) as Element)
        }
        val cloud = rules.getElementsByTagName("cloud-backup").item(0) as Element
        assertEquals("true", cloud.getAttribute("disableIfNoEncryptionCapabilities"))
    }

    @Test fun manifestContainsOnlyRequiredPermissionAndReleaseSettings() {
        val manifest = xml("AndroidManifest.xml")
        val permissions = manifest.getElementsByTagName("uses-permission")
        val names = (0 until permissions.length)
            .map { (permissions.item(it) as Element).getAttribute("android:name") }.toSet()
        // Wibracje, przypomnienie o wyzwaniu dnia (gpt/15) i Play Games (gpt/01). Nic ponad to.
        assertEquals(
            setOf(
                "android.permission.VIBRATE",
                "android.permission.POST_NOTIFICATIONS",
                "android.permission.RECEIVE_BOOT_COMPLETED",
                "android.permission.INTERNET",
            ),
            names,
        )
        assertEquals(0, manifest.getElementsByTagName("queries").length)
        val app = manifest.getElementsByTagName("application").item(0) as Element
        assertEquals("game", app.getAttribute("android:appCategory"))
        assertEquals("true", app.getAttribute("android:isGame"))
        assertEquals("@xml/locales_config", app.getAttribute("android:localeConfig"))
        assertEquals("@xml/backup_rules", app.getAttribute("android:fullBackupContent"))
        assertEquals("@xml/data_extraction_rules", app.getAttribute("android:dataExtractionRules"))
        assertEquals("true", app.getAttribute("android:allowBackup"))
        assertEquals("false", app.getAttribute("android:usesCleartextTraffic"))
        assertEquals("@xml/network_security_config", app.getAttribute("android:networkSecurityConfig"))
    }

    @Test fun networkConfigRequiresSystemTlsAndDisallowsHttp() {
        val network = xml("res/xml/network_security_config.xml")
        assertEquals("network-security-config", network.tagName)
        assertEquals(0, network.getElementsByTagName("domain-config").length)
        val configs = network.getElementsByTagName("base-config")
        assertEquals(1, configs.length)
        assertEquals("false", (configs.item(0) as Element).getAttribute("cleartextTrafficPermitted"))
        val authorities = network.getElementsByTagName("certificates")
        assertEquals(1, authorities.length)
        assertEquals("system", (authorities.item(0) as Element).getAttribute("src"))
    }

    @Test fun onlyReplayViewerActivityIsPublicAndReceiversArePrivate() {
        val manifest = xml("AndroidManifest.xml")
        val activities = manifest.getElementsByTagName("activity")
        assertEquals(1, activities.length)
        val main = activities.item(0) as Element
        assertEquals(".MainActivity", main.getAttribute("android:name"))
        assertEquals("true", main.getAttribute("android:exported"))
        val filters = main.getElementsByTagName("intent-filter")
        assertTrue(filters.length >= 2)
        val receivers = manifest.getElementsByTagName("receiver")
        assertEquals(1, receivers.length)
        val reminder = receivers.item(0) as Element
        assertEquals(".notifications.DailyReminderReceiver", reminder.getAttribute("android:name"))
        assertEquals("false", reminder.getAttribute("android:exported"))
        val providers = manifest.getElementsByTagName("provider")
        val share = (0 until providers.length).map { providers.item(it) as Element }
            .first { it.getAttribute("android:name") == ".meta.ReplayShareProvider" }
        assertEquals("false", share.getAttribute("android:exported"))
        assertEquals("true", share.getAttribute("android:grantUriPermissions"))
        val playGames = (0 until providers.length).map { providers.item(it) as Element }
            .first { it.getAttribute("android:name") ==
                "com.google.android.gms.games.provider.PlayGamesInitProvider" }
        assertEquals("remove", playGames.getAttribute("tools:node"))
        assertEquals(0, manifest.getElementsByTagName("service").length)
    }

    @Test fun activityHandlesLocaleWithoutRestart() {
        val activities = xml("AndroidManifest.xml").getElementsByTagName("activity")
        val main = (0 until activities.length).map { activities.item(it) as Element }
            .first { it.getAttribute("android:name") == ".MainActivity" }
        val flags = main.getAttribute("android:configChanges").split('|').toSet()
        assertTrue("Brak locale w configChanges", "locale" in flags)
        assertTrue("Brak layoutDirection w configChanges", "layoutDirection" in flags)
    }

    @Test fun baselineProfileContainsGameLoopAndBattleRules() {
        val lines = File("src/main/baseline-prof.txt").readLines()
            .map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
        val rule = Regex("""^(?:[HSP]{1,3})?L[A-Za-z0-9_/$]+;(?:->\S+)?$""")
        assertTrue(lines.isNotEmpty())
        assertTrue("Niepoprawna reguła profilu", lines.all { rule.matches(it) })
        assertTrue(lines.any { it.contains("GameView;->run()V") })
        assertTrue(lines.any { it.contains("Battle;->step()V") })
        assertTrue(lines.any { it.contains("BattleScreen;->update(F)V") })
    }

    @Test fun playVersionCodeIsPositive() {
        val props = Properties()
        File("../gradle.properties").inputStream().use { props.load(it) }
        val code = props.getProperty("releaseVersionCode")?.toIntOrNull() ?: 0
        assertTrue(code > 0)
        assertFalse(props.getProperty("releaseVersionName").isNullOrBlank())
    }
}
