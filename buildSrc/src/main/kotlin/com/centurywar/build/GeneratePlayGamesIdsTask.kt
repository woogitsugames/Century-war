package com.centurywar.build

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/** Wytwarza odrębne zasoby dla każdego wariantu bez pliku XML w src/main/res. */
abstract class GeneratePlayGamesIdsTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val configurationFiles: ConfigurableFileCollection

    @get:Input
    abstract val releaseVariant: Property<Boolean>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        val content = configurationFiles.files.singleOrNull()?.takeIf { it.isFile }?.readText()
        val xml = PlayGamesIdsGenerator.render(content, releaseVariant.get())
        val output = outputDirectory.file("values/games_ids.xml").get().asFile
        output.parentFile.mkdirs()
        output.writeText(xml, Charsets.UTF_8)
    }
}
