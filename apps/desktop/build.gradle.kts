import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.3.21"
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.21"
    id("org.jetbrains.compose") version "1.11.0"
}

group = "com.lucasserafin94.iptvburo"
version = "0.2.0-alpha.1"

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
        freeCompilerArgs.add("-Xconsistent-data-class-copy-visibility")
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    implementation(project(":packages:domain-model"))
    implementation(project(":packages:playlist-parser"))
    implementation(project(":packages:xtream-client"))

    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.okhttp)
    implementation("net.java.dev.jna:jna-platform:5.17.0")
    implementation("org.openjfx:javafx-base:17.0.14:win")
    implementation("org.openjfx:javafx-graphics:17.0.14:win")
    implementation("org.openjfx:javafx-media:17.0.14:win")
    implementation("org.openjfx:javafx-swing:17.0.14:win")

    testImplementation(kotlin("test"))
    testImplementation(libs.okhttp.mockwebserver)
}

compose.desktop {
    application {
        mainClass = "com.lucasserafin94.iptvburo.desktop.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Dmg, TargetFormat.Deb)
            packageName = "IPTVBURO"
            packageVersion = "0.2.0"
            description = "IPTV BURO desktop player"
            vendor = "IPTV BURO"
            modules("java.desktop", "java.prefs")

            windows {
                menuGroup = "IPTV BURO"
                shortcut = true
                perUserInstall = true
            }
        }
    }
}

tasks.test {
    useJUnitPlatform()
}
