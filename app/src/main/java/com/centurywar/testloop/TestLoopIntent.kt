package com.centurywar.testloop

import android.content.Intent
import android.net.Uri
import java.io.File

/**
 * R11-04: odczyt intencji pętli gry (standard Firebase Test Lab „Game Loop”, z którego korzysta
 * też raport przedpremierowy Google Play). Filtr jest eksportowany, więc wszystko z intencji
 * traktujemy jak dane z zewnątrz: numer scenariusza i adres pliku wyników sprawdzamy tutaj.
 */
internal object TestLoopIntent {
    const val ACTION = "com.google.intent.action.TEST_LOOP"
    const val EXTRA_SCENARIO = "scenario"
    /** Liczba scenariuszy; ta sama wartość jest w manifeście (`com.google.test.loops`). */
    const val SCENARIOS = 4

    fun isTestLoop(intent: Intent?): Boolean = intent?.action == ACTION

    /**
     * Numer scenariusza. Test Lab numeruje pętle od 1; brak numeru (0 w dokumentacji Firebase)
     * oznacza pierwszy scenariusz. Poza zakresem: wartość bez zmian, runner zgłosi „error”.
     */
    fun scenario(intent: Intent): Int {
        val raw = try { intent.getIntExtra(EXTRA_SCENARIO, 0) } catch (_: RuntimeException) { -1 }
        return if (raw == 0) 1 else raw
    }

    fun validScenario(n: Int): Boolean = n in 1..SCENARIOS

    /**
     * Adres pliku wyników albo null (wtedy wynik tylko w logcat `CWTestLoop`). Nigdy nie piszemy
     * do własnych plików aplikacji: odrzucamy `content://` naszych dostawców i `file://` w katalogach
     * danych gry (prywatnych i zewnętrznych), poza katalogiem pętli. Inne schematy odrzucamy.
     */
    fun resultUri(
        intent: Intent, packageName: String, protectedDirs: List<File>, loopDir: File,
    ): Uri? {
        val uri = try { intent.data } catch (_: RuntimeException) { null } ?: return null
        return if (allowedResultUri(uri.scheme, uri.authority, uri.path, packageName, protectedDirs, loopDir)) uri
        else null
    }

    fun allowedResultUri(
        scheme: String?, authority: String?, path: String?,
        packageName: String, protectedDirs: List<File>, loopDir: File,
    ): Boolean = when (scheme?.lowercase()) {
        "content" -> {
            val auth = authority.orEmpty()
            auth.isNotEmpty() && auth != packageName && !auth.startsWith("$packageName.")
        }
        "file" -> {
            val target = path?.takeIf { it.isNotEmpty() }?.let { canonical(File(it)) }
            target != null && (inside(target, canonical(loopDir)) ||
                protectedDirs.none { dir -> inside(target, canonical(dir)) })
        }
        else -> false
    }

    private fun canonical(f: File): File = try { f.canonicalFile } catch (_: Exception) { f.absoluteFile }

    private fun inside(file: File, dir: File): Boolean {
        val d = dir.path.trimEnd('/')
        return file.path == d || file.path.startsWith("$d/")
    }
}
