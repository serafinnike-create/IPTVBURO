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
    ":apps:android-tv",
    ":packages:domain-model",
    ":packages:playlist-parser",
    ":packages:test-fixtures",
)
