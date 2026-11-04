package com.centurywar.testloop

import android.app.Application
import android.os.Build
import java.io.File

/**
 * R11-04: pętla gry działa w osobnym procesie `:testloop` (manifest). Tam aplikacja nie
 * instaluje dziennika awarii gracza ani nie uruchamia Play Games: awarie i tak widzi raport
 * Google, a w `filesDir` nie powstaje nic nowego.
 */
internal object TestLoopProcess {
    const val SUFFIX = ":testloop"

    fun isTestLoop(processName: String?): Boolean = processName?.endsWith(SUFFIX) == true

    fun current(): String? =
        if (Build.VERSION.SDK_INT >= 28) runCatching { Application.getProcessName() }.getOrNull()
        else runCatching {
            File("/proc/self/cmdline").readBytes().takeWhile { it != 0.toByte() }
                .toByteArray().toString(Charsets.UTF_8)
        }.getOrNull()
}
