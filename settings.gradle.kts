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

val serverOnly = providers.gradleProperty("serverOnly").orNull == "true"
if (!serverOnly) include(":app")
include(":sim", ":server")
