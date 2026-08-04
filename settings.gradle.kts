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

rootProject.name = "IPTVBURO"

include(
    ":apps:desktop",
    ":apps:android-tv",
    ":packages:domain-model",
    ":packages:playlist-parser",
    ":packages:test-fixtures",
    ":packages:stalker-client",
    ":packages:metadata-client",
    ":packages:xtream-client",
)
