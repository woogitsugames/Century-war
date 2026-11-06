package com.centurywar.server

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class ApiRoutesDocTest {
    private fun file(vararg candidates: String): File = candidates.map(::File).firstOrNull { it.isFile }
        ?: error("Nie znaleziono pliku: ${candidates.joinToString()}")

    private fun sourceRoutes(source: String): Set<Pair<String, String>> =
        Regex("""(?m)^\s*(get|post|put|patch|delete|head|options)\s*\(\s*\"(/[^\"]*)\"""")
            .findAll(source).map { it.groupValues[1].uppercase() to it.groupValues[2] }.toSet()

    private fun documentedRoutes(readme: String): Set<Pair<String, String>> =
        Regex("""(?m)^\|\s*(GET|POST|PUT|PATCH|DELETE|HEAD|OPTIONS)\s*\|\s*`(/[^`]*)`\s*\|""")
            .findAll(readme).map { it.groupValues[1] to it.groupValues[2] }.toSet()

    @Test fun everyLiteralApiRouteHasMethodAndPathInReadmeTable() {
        val source = file("src/main/kotlin/com/centurywar/server/Api.kt",
            "server/src/main/kotlin/com/centurywar/server/Api.kt").readText()
        val readme = file("README.md", "server/README.md").readText()
        val actual = sourceRoutes(source)
        assertTrue(actual.isNotEmpty(), "Nie rozpoznano żadnej trasy — parser wymaga aktualizacji")
        assertEquals(emptySet<Pair<String, String>>(), actual - documentedRoutes(readme),
            "Trasy bez wiersza w tabeli README")
    }

    @Test fun newUnlistedMethodAndRouteAreDetected() {
        val source = "get(\"/health\") {}\npost(\"/future\") {}"
        val docs = "| GET | `/health` | brak |"
        assertEquals(setOf("POST" to "/future"), sourceRoutes(source) - documentedRoutes(docs))
        assertEquals(setOf("POST" to "/health"),
            sourceRoutes("post(\"/health\") {}") - documentedRoutes(docs))
    }
}
