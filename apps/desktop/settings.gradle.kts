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
    versionCatalogs {
        create("libs") {
            from(files("../../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "iptv-buro-desktop"

include(
    ":packages:domain-model",
    ":packages:playlist-parser",
    ":packages:test-fixtures",
    ":packages:xtream-client",
)

project(":packages:domain-model").projectDir = file("../../packages/domain-model")
project(":packages:playlist-parser").projectDir = file("../../packages/playlist-parser")
project(":packages:test-fixtures").projectDir = file("../../packages/test-fixtures")
project(":packages:xtream-client").projectDir = file("../../packages/xtream-client")
project(":packages").projectDir = file("../../packages")
