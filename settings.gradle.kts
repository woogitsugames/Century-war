pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "CenturyWar"

val requestedTasks = gradle.startParameter.taskNames
val onlyServerTasks = requestedTasks.isNotEmpty() &&
    requestedTasks.all { it.startsWith(":server:") || it.startsWith("server:") }
val serverOnly = providers.gradleProperty("serverOnly").orNull == "true" || onlyServerTasks
if (!serverOnly) include(":app")
include(":sim", ":server")
