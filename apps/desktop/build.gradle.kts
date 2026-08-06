import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.net.URI
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import java.util.zip.ZipInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream

buildscript {
    repositories { mavenCentral() }
    // Gradle bundles commons-compress, but not on the build script classpath; the JCEF runtime
    // ships as a .tar.gz and the JDK can only unpack the gzip layer, not the tar inside it.
    dependencies { classpath("org.apache.commons:commons-compress:1.27.1") }
}

plugins {
    kotlin("jvm") version "2.3.21"
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.21"
    id("org.jetbrains.compose") version "1.11.0"
}

group = "com.lucasserafin94.iptvburo"
version = "1.1"

val vlcVersion = "3.0.23"
val vlcArchiveSha256 = "992d19dbd0b8a7cde9167d2f7780b1ef6f92acc8a71acfa736101a21f35181e1"
val vlcArchive = layout.buildDirectory.file("downloads/vlc-$vlcVersion-win64.zip")
val generatedAppResources = layout.buildDirectory.dir("generated/app-resources")

// The Chromium runtime behind the trailer player.
//
// It has to come from a JetBrains Runtime build, not from the upstream java-cef natives on Maven
// Central. The `dev.datlag:jcef` wrapper is the JetBrains fork, whose CefApp declares
// N_Initialize(CefAppHandler, CefSettings, boolean) while upstream declares the same method with
// two arguments. JNI binds on name *and* signature, so upstream natives link but fail the moment
// the browser starts. This bundle is the one the wrapper's JNI actually matches.
val jbrJcefVersion = "21.0.7-windows-x64-b1038.58"
val jbrJcefArchiveSha256 = "60cc64adcdd506d202a1ed3335897dee23975e6401e7fd240b7e9ce9040c5835"
val jbrJcefArchive = layout.buildDirectory.file("downloads/jbr_jcef-$jbrJcefVersion.tar.gz")

/**
 * The TMDb key baked into this build, read from local.properties.
 *
 * Deliberately not a source constant: this repository is public, and a key committed to it is
 * scraped and revoked within days. Empty when the file has no entry, in which case the app simply
 * behaves as if no key were configured and the user can paste their own in settings.
 */
val bundledTmdbKey: String =
    providers
        .fileContents(layout.projectDirectory.file("../../local.properties"))
        .asText
        .map { text ->
            text.lineSequence()
                .firstOrNull { line -> line.trimStart().startsWith("tmdb.apiKey=") }
                ?.substringAfter("=")
                ?.trim()
                .orEmpty()
        }.getOrElse("")

val generatedBuildConfig = layout.buildDirectory.dir("generated/buildconfig")

val generateBuildConfig by tasks.registering {
    description = "Writes the build-time constants the app reads at runtime."
    val output = generatedBuildConfig
    val key = bundledTmdbKey
    inputs.property("tmdbKey", key)
    outputs.dir(output)
    doLast {
        val directory =
            output.get().asFile.resolve("com/lucasserafin94/iptvburo/desktop/build").also { it.mkdirs() }
        directory.resolve("BuildKeys.kt").writeText(
            """
            package com.lucasserafin94.iptvburo.desktop.build

            /** Generated at build time from local.properties; never committed. */
            internal const val BUNDLED_TMDB_KEY: String = "$key"
            """.trimIndent() + System.lineSeparator(),
        )
    }
}

kotlin.sourceSets.named("main") { kotlin.srcDir(generateBuildConfig) }
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

val bundledJcefDirectory = generatedAppResources.map { it.dir("windows/jcef") }

val prepareBundledJcef by tasks.registering {
    description = "Downloads and verifies the Chromium runtime the trailer player embeds."
    notCompatibleWithConfigurationCache("Downloads and extracts a verified third-party runtime.")
    outputs.dir(bundledJcefDirectory)

    doLast {
        val archive = jbrJcefArchive.get().asFile
        val destination = bundledJcefDirectory.get().asFile
        val expectedLibrary = destination.resolve("libcef.dll")
        if (expectedLibrary.isFile) return@doLast

        fun File.sha256(): String =
            inputStream().buffered().use { input ->
                val digest = MessageDigest.getInstance("SHA-256")
                val buffer = ByteArray(1 shl 20)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
                digest.digest().joinToString("") { "%02x".format(it) }
            }

        archive.parentFile.mkdirs()
        if (!archive.isFile || archive.sha256() != jbrJcefArchiveSha256) {
            archive.delete()
            URI("https://cache-redirector.jetbrains.com/intellij-jbr/jbr_jcef-$jbrJcefVersion.tar.gz")
                .toURL()
                .openStream()
                .use { input -> archive.outputStream().buffered().use(input::copyTo) }
        }
        check(archive.sha256() == jbrJcefArchiveSha256) { "Official JBR JCEF archive checksum did not match." }

        // Only the Chromium payload, not the JDK wrapped around it: the app already ships its own
        // runtime, and copying the whole JBR would add a second one for no benefit. JetBrains splits
        // these across bin/ and lib/, while JCefAppConfig expects one flat directory with locales/
        // beneath it, so the two source directories are collapsed into that shape here.
        val root = "jbr_jcef-$jbrJcefVersion"
        val wanted =
            setOf(
                "bin/libcef.dll", "bin/chrome_elf.dll", "bin/jcef.dll", "bin/jcef_helper.exe",
                "bin/icudtl.dat", "bin/snapshot_blob.bin", "bin/v8_context_snapshot.bin",
                "bin/libEGL.dll", "bin/libGLESv2.dll", "bin/vk_swiftshader.dll",
                "bin/vulkan-1.dll", "bin/d3dcompiler_47.dll",
                "lib/resources.pak", "lib/chrome_100_percent.pak", "lib/chrome_200_percent.pak",
                "lib/vk_swiftshader_icd.json",
            )

        destination.deleteRecursively()
        destination.mkdirs()
        var extracted = 0
        TarArchiveInputStream(GZIPInputStream(archive.inputStream().buffered())).use { tar ->
            var entry = tar.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val relative = entry.name.removePrefix("$root/")
                    val target =
                        when {
                            relative in wanted -> relative.substringAfterLast('/')
                            relative.startsWith("lib/locales/") -> "locales/" + relative.substringAfterLast('/')
                            else -> null
                        }
                    if (target != null) {
                        val output = destination.resolve(target).canonicalFile
                        check(output.toPath().startsWith(destination.canonicalFile.toPath()))
                        output.parentFile.mkdirs()
                        output.outputStream().buffered().use(tar::copyTo)
                        extracted++
                    }
                }
                entry = tar.nextEntry
            }
        }
        check(expectedLibrary.isFile) { "JCEF runtime extraction did not produce libcef.dll." }
        check(destination.resolve("jcef_helper.exe").isFile) { "JCEF runtime extraction did not produce jcef_helper.exe." }
        check(destination.resolve("locales/en-US.pak").isFile) { "JCEF runtime extraction did not produce locales." }
        logger.lifecycle("Bundled JCEF runtime: $extracted files, libcef.dll ${expectedLibrary.length() / 1024 / 1024} MB.")
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
    implementation(project(":packages:metadata-client"))
    // Chromium, for playing trailers inside the app. YouTube refuses to play anywhere except a real
    // browser engine: VLC's own youtube module reports "Couldn't extract youtube video URL" against
    // the current site, and no amount of URL handling gets around that.
    implementation("dev.datlag:jcef:2025.03.23")

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
            packageVersion = "1.1.0"
            description = "IPTV BURO desktop player"
            vendor = "IPTV BURO"
            appResourcesRootDir.set(generatedAppResources)
            // jdk.httpserver carries com.sun.net.httpserver, which serves the one loopback page a
            // YouTube embed needs to have a real origin. Without it the class is simply absent from
            // the packaged runtime, TrailerHostServer fails to start, and trailers fall back to the
            // system browser — a failure that looks like a Chromium problem and is not.
            modules("java.desktop", "java.prefs", "jdk.httpserver")

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
    dependsOn(prepareBundledVlc, prepareBundledJcef)
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
