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
        // JCEF's native OpenGL dependencies (gluegen, jogl) are published here and nowhere else.
        maven("https://jogamp.org/deployment/maven") {
            content { includeGroupByRegex("org[.]jogamp.*") }
        }
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
