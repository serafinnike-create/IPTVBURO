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
    // Generates the start-up baseline profile for :apps:android-tv. Never shipped; run on demand
    // against a connected device, see the module's build file.
    ":apps:android-tv-baselineprofile",
    ":packages:domain-model",
    ":packages:playlist-parser",
    ":packages:test-fixtures",
    ":packages:stalker-client",
    ":packages:metadata-client",
    ":packages:media-source-spi",
    ":packages:webdav-client",
    ":packages:xtream-client",
)
