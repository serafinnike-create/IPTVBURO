import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.net.URI
import java.security.MessageDigest
import java.util.zip.ZipInputStream

plugins {
    kotlin("jvm") version "2.3.21"
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.21"
    id("org.jetbrains.compose") version "1.11.0"
}

group = "com.lucasserafin94.iptvburo"
version = "0.2.0-alpha.6"

val vlcVersion = "3.0.23"
val vlcArchiveSha256 = "992d19dbd0b8a7cde9167d2f7780b1ef6f92acc8a71acfa736101a21f35181e1"
val vlcArchive = layout.buildDirectory.file("downloads/vlc-$vlcVersion-win64.zip")
val generatedAppResources = layout.buildDirectory.dir("generated/app-resources")
val bundledVlcDirectory = generatedAppResources.map { it.dir("windows/vlc") }

val prepareBundledVlc by tasks.registering {
    description = "Downloads and verifies the official VLC runtime used by the Windows player."
    notCompatibleWithConfigurationCache("Downloads and extracts a verified third-party runtime.")
    outputs.dir(bundledVlcDirectory)

    doLast {
        val archive = vlcArchive.get().asFile
        val destination = bundledVlcDirectory.get().asFile
        val expectedExecutable = destination.resolve("vlc.exe")
        if (expectedExecutable.isFile) return@doLast

        archive.parentFile.mkdirs()
        if (!archive.isFile || archive.inputStream().use { input ->
                MessageDigest.getInstance("SHA-256").digest(input.readBytes()).joinToString("") { "%02x".format(it) }
            } != vlcArchiveSha256
        ) {
            archive.delete()
            URI("https://download.videolan.org/pub/videolan/vlc/$vlcVersion/win64/vlc-$vlcVersion-win64.zip")
                .toURL()
                .openStream()
                .use { input -> archive.outputStream().use(input::copyTo) }
        }
        val actualHash = archive.inputStream().use { input ->
            MessageDigest.getInstance("SHA-256").digest(input.readBytes()).joinToString("") { "%02x".format(it) }
        }
        check(actualHash == vlcArchiveSha256) { "Official VLC archive checksum did not match." }

        destination.deleteRecursively()
        destination.mkdirs()
        val prefix = "vlc-$vlcVersion/"
        ZipInputStream(archive.inputStream().buffered()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val relative = entry.name.removePrefix(prefix)
                if (relative.isNotBlank() && relative != entry.name) {
                    val output = destination.resolve(relative).canonicalFile
                    check(output.toPath().startsWith(destination.canonicalFile.toPath()))
                    if (entry.isDirectory) output.mkdirs() else {
                        output.parentFile.mkdirs()
                        output.outputStream().buffered().use(zip::copyTo)
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        check(expectedExecutable.isFile) { "VLC runtime extraction did not produce vlc.exe." }
    }
}

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
    implementation("com.google.code.gson:gson:2.13.2")
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
            packageVersion = "0.2.8"
            description = "IPTV BURO desktop player"
            vendor = "IPTV BURO"
            appResourcesRootDir.set(generatedAppResources)
            modules("java.desktop", "java.prefs")

            windows {
                menuGroup = "IPTV BURO"
                shortcut = true
                perUserInstall = true
                // Without this the installer, the shortcut and the taskbar all fall back to the
                // generic Java icon, which is the single most obvious sign of an unfinished app.
                iconFile.set(project.file("src/main/resources/brand/buro.ico"))
                // Stable across versions so Windows upgrades the existing install instead of
                // leaving two entries in Apps & Features.
                upgradeUuid = "5A0F2D5E-6C6B-4B2E-9E1A-2F7C1B9D4A31"
            }
        }
    }
}

tasks.matching {
    it.name in setOf("run", "runDistributable", "createDistributable", "packageMsi", "prepareAppResources")
}.configureEach {
    dependsOn(prepareBundledVlc)
}

tasks.test {
    useJUnitPlatform()
    // Gradle's own -D properties do not reach the forked test JVM, so the live-updater probe
    // opt-in has to be forwarded explicitly.
    System.getProperty("buroLiveUpdaterProbe")?.let { value ->
        systemProperty("buroLiveUpdaterProbe", value)
    }
    testLogging { showStandardStreams = true }
}
