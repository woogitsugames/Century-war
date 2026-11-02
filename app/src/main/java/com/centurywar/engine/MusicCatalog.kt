package com.centurywar.engine

import android.content.res.AssetManager
import com.centurywar.game.Defs
import java.io.IOException

/**
 * Lista nagranych utworów w `assets/music/<nazwa>.ogg`, odczytana raz przy starcie.
 * Brak pliku oznacza zapasową muzykę proceduralną dla danego utworu.
 * Odczyty są tanie (tablica) i bezpieczne z dowolnego wątku.
 */
class MusicCatalog(names: Collection<String>) {
    companion object {
        const val DIR = "music"
        const val EXT = ".ogg"
        const val MENU_TRACK = "menu"
        const val VICTORY_TRACK = "victory"
        const val DEFEAT_TRACK = "defeat"

        fun calmName(era: Int): String = "era${era}_calm"
        fun battleName(era: Int): String = "era${era}_battle"

        /** Wszystkie utwory z docs/music/SUNO_PROMPTS.md (bez rozszerzenia). */
        val ALL: List<String> = buildList {
            add(MENU_TRACK); add(VICTORY_TRACK); add(DEFEAT_TRACK)
            for (e in 0 until Defs.ERA_COUNT) { add(calmName(e)); add(battleName(e)) }
        }

        val EMPTY = MusicCatalog(emptyList())

        /** Nazwy plików z katalogu (np. "menu.ogg"); inne pliki są pomijane. */
        fun fromFileNames(files: Array<String>?): MusicCatalog =
            MusicCatalog(files.orEmpty().filter { it.endsWith(EXT) }.map { it.removeSuffix(EXT) })

        /** Jedno wywołanie AssetManager.list przy starcie; błędy = brak nagrań. */
        fun fromAssets(assets: AssetManager?): MusicCatalog {
            if (assets == null) return EMPTY
            return try {
                fromFileNames(assets.list(DIR))
            } catch (_: IOException) {
                EMPTY
            } catch (_: RuntimeException) {
                EMPTY
            }
        }

        fun path(name: String): String = "$DIR/$name$EXT"
    }

    private val present = BooleanArray(ALL.size) { ALL[it] in names }

    val isEmpty: Boolean get() = present.none { it }

    fun has(name: String): Boolean {
        val i = ALL.indexOf(name)
        return i >= 0 && present[i]
    }

    fun hasCalm(era: Int): Boolean = era in 0 until Defs.ERA_COUNT && present[3 + era * 2]
    fun hasBattle(era: Int): Boolean = era in 0 until Defs.ERA_COUNT && present[4 + era * 2]
    val hasMenu: Boolean get() = present[0]

    /** Czy dla menu ([Music.MENU]) albo epoki gra nagranie zamiast syntezy. */
    fun covers(era: Int): Boolean = if (era == Music.MENU) hasMenu else hasCalm(era) || hasBattle(era)

    /** Uszkodzony plik (nie da się otworzyć) wraca do syntezy aż do restartu aplikacji. */
    fun markBroken(name: String) {
        val i = ALL.indexOf(name)
        if (i >= 0) present[i] = false
    }
}
