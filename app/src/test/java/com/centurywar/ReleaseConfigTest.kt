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

    private fun includes(element: Element): List<String> {
        val nodes = element.getElementsByTagName("include")
        return (0 until nodes.length).map { i ->
            val rule = nodes.item(i) as Element
            assertEquals("file", rule.getAttribute("domain"))
            rule.getAttribute("path")
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

    /** Kopia tylko profilu; battle.snap (przerwana bitwa) jest przejściowy i nie trafia do backupu. */
    @Test fun onlyPlayerProfileIsBackedUp() {
        assertEquals(
            listOf("profile.json"),
            includes(xml("res/xml/backup_rules.xml")),
        )
        val rules = xml("res/xml/data_extraction_rules.xml")
        for (tag in listOf("cloud-backup", "device-transfer")) {
            val elements = rules.getElementsByTagName(tag)
            assertEquals(1, elements.length)
            assertEquals(listOf("profile.json"), includes(elements.item(0) as Element))
        }
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
