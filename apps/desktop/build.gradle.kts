import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.gradle.language.jvm.tasks.ProcessResources
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
version = "3.0.2"

val desktopReleaseVersion = version.toString()

/**
 * The version Windows Installer sees, which must increase with every shipped preview.
 *
 * MSI only understands `a.b.c` with numbers, so `2.0.0-alpha.3` cannot be handed over as it is —
 * which is why this used to be the constant "2.0.0". That constant is what deleted a customer's
 * app during an update: every preview declared the same ProductVersion under the same UpgradeCode,
 * so Windows saw no upgrade to perform, the reinstall did nothing, and the script's fallback
 * removed the old product and then failed to put anything back.
 *
 * The preview number becomes the patch field instead, so alpha.3 ships as 2.0.3 and genuinely
 * outranks alpha.2's 2.0.2. A final release keeps its own patch number.
 */
val windowsPackageVersion =
    Regex("""^(\d+)\.(\d+)(?:\.(\d+))?(?:-[A-Za-z]+\.(\d+))?$""")
        .matchEntire(desktopReleaseVersion)
        ?.let { match ->
            val (major, minor, patch, preview) = match.destructured
            // A preview's number wins the patch slot; a final build keeps the patch it declared.
            val effectivePatch = preview.ifEmpty { patch.ifEmpty { "0" } }
            "$major.$minor.$effectivePatch"
        }
        ?: error("Cannot derive an MSI version from '$desktopReleaseVersion'.")

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
 * A developer may opt in to the TMDb key from local.properties for a local `run` only.
 *
 * The default is deliberately empty. In particular, a distributable must never inherit the
 * workstation owner's personal key merely because local.properties happens to exist. Users can
 * add their own key per profile in Settings.
 */
val includeLocalTmdbKey =
    providers.gradleProperty("iptvburo.includeLocalTmdbKey")
        .map { it.equals("true", ignoreCase = true) }
        .getOrElse(false)
val localTmdbKey: String =
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
val bundledTmdbKey = if (includeLocalTmdbKey) localTmdbKey else ""

val generatedBuildConfig = layout.buildDirectory.dir("generated/buildconfig")

val generateBuildConfig by tasks.registering {
    description = "Writes the build-time constants the app reads at runtime."
    val output = generatedBuildConfig
    val key = bundledTmdbKey
    val releaseVersion = desktopReleaseVersion
    inputs.property("tmdbKey", key)
    inputs.property("desktopReleaseVersion", releaseVersion)
    outputs.dir(output)
    doLast {
        val directory =
            output.get().asFile.resolve("com/lucasserafin94/iptvburo/desktop/build").also { it.mkdirs() }
        directory.resolve("BuildKeys.kt").writeText(
            """
            package com.lucasserafin94.iptvburo.desktop.build

            /** Generated at build time; never committed. */
            internal const val BUNDLED_TMDB_KEY: String = "$key"

            /** GitHub release version embedded in this binary. */
            internal const val DESKTOP_RELEASE_VERSION: String = "$releaseVersion"
            """.trimIndent() + System.lineSeparator(),
        )
    }
}

val verifyCleanPublicDesktopBuild by tasks.registering {
    group = "verification"
    description = "Refuses public desktop packages that contain a workstation TMDb key."
    val containsBundledKey = bundledTmdbKey.isNotEmpty()
    inputs.property("bundledTmdbKeyPresent", containsBundledKey)
    doLast {
        require(!containsBundledKey) {
            "A public Windows build cannot embed the local TMDb key. " +
                "Remove -Piptvburo.includeLocalTmdbKey=true and rebuild."
        }
    }
}

kotlin.sourceSets.named("main") { kotlin.srcDir(generateBuildConfig) }

// The UI reads the same capability contract that release tooling and documentation inspect. Keeping
// a copied Boolean in Kotlin caused preview-only features to appear even while this manifest said
// they were unavailable. Gradle packages the canonical JSON as an application resource instead.
tasks.named<ProcessResources>("processResources") {
    from(rootProject.file("packages/platform-capabilities/windows-preview.json")) {
        into("capabilities")
    }
}
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

        // The plugin index is deliberately NOT built here.
        //
        // It was, and it did nothing: VLC's plugins.dat records each module's absolute path and
        // modification time, so an index built against this build directory is rejected wholesale
        // once the installer copies the tree to %LOCALAPPDATA% with fresh timestamps. The shipped
        // index was stale on arrival — verified in the installed app's own log, which reported
        // `stale plugins cache` once per module.
        //
        // VlcPluginCache builds it at runtime instead, in the directory the plugins actually live
        // in, once per install. See that file for what the optimisation is measured to be worth.
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
    // The sidebar's icons, and the same set Android already draws from, so the two platforms
    // cannot end up with different marks for the same destination.
    @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
    implementation(compose.materialIconsExtended)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.okhttp)
    implementation("com.google.code.gson:gson:2.13.2")
    // The QR code on the activation screen. `core` only — the javase artifact adds image encoding
    // and camera decoding, neither of which this needs.
    //
    // Written by hand first, on the reasoning that this is one small thing. The result produced
    // correct data and correct error correction and still could not be read by any decoder, which
    // is the failure mode that matters: it looks perfect and the customer finds out by pointing a
    // phone at a screen that never responds.
    implementation("com.google.zxing:core:3.5.3")
    implementation("net.java.dev.jna:jna-platform:5.17.0")
    // JavaFX is gone with JavaFxDesktopPlayer, the media player it existed for. That class was
    // replaced by the VLC engine and then kept, unreferenced, along with four platform-specific
    // artefacts it dragged into the installer.

    testImplementation(kotlin("test"))
    testImplementation(libs.okhttp.mockwebserver)
    // Compose UI testing.
    //
    // Every test in this suite until now covered logic and data, and every bug a user reported this
    // week lived somewhere else: switches that changed the stored value and never redrew, a poster
    // that never loaded, a settings panel whose lower half could not be reached. Those are all
    // composition-level failures, and nothing here could see them.
    testImplementation(compose.desktop.uiTestJUnit4)
}

compose.desktop {
    application {
        mainClass = "com.lucasserafin94.iptvburo.desktop.MainKt"

        // Memory, bounded rather than left to the JVM's defaults.
        //
        // Unset, HotSpot sizes the heap from physical RAM — a quarter of it — so on a 32 GB machine
        // it will happily grow to 8 GB and never give any back. That is why the app sat at 700 MB
        // resident while doing nothing: not a leak, just a collector with no reason to tidy up.
        //
        // 768 MB is comfortably above what the catalogue needs. CompactXtreamCatalog stores 41,698
        // items columnar precisely so the whole list is tens of megabytes rather than hundreds, and
        // the images are Coil's own bounded cache.
        jvmArgs += listOf(
            "-Xmx768m",
            // G1 with a short pause target: this is an interactive app, and a long collection while
            // the user is scrolling a wall of posters is the one thing they would actually feel.
            "-XX:+UseG1GC",
            "-XX:MaxGCPauseMillis=100",
            // Return unused heap to the OS instead of holding the high-water mark for ever. Without
            // this the app keeps whatever it needed during the catalogue load — the busiest moment
            // of its life — for the entire session.
            "-XX:+ShrinkHeapInSteps",
            "-XX:MinHeapFreeRatio=15",
            "-XX:MaxHeapFreeRatio=35",
        )

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Dmg, TargetFormat.Deb)
            packageName = "IPTVBURO"
            packageVersion = windowsPackageVersion
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
                // `--win-shortcut` in jpackage's own terms, which is the *desktop* icon; the Start
                // menu entry is `--win-menu`, requested by menuGroup above. The two are separate
                // flags and it is easy to assume one implies the other — it does not.
                //
                // Neither appears when the app is installed by copying the app image into place
                // rather than by running the MSI, which is how a development build is usually
                // deployed. A missing desktop icon after such a copy is the copy, not this setting.
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

tasks.matching {
    it.name in
        setOf(
            "createDistributable",
            "packageMsi",
            "packageReleaseMsi",
            "packageDistributionForCurrentOS",
            "packageReleaseDistributionForCurrentOS",
        )
}.configureEach {
    dependsOn(verifyCleanPublicDesktopBuild)
}

// The signed release script sets this marker only for the short packaging step between signing the
// generated launcher and signing the final MSI. Preview automation may explicitly opt into an
// unsigned MSI; final releases remain outside the preview workflow and still require signing.
val windowsSignedPipeline =
    providers.environmentVariable("IPTVBURO_WINDOWS_SIGNING_PIPELINE")
        .map { it.equals("true", ignoreCase = true) }
        .getOrElse(false)
val windowsUnsignedPreview =
    providers.environmentVariable("IPTVBURO_ALLOW_UNSIGNED_WINDOWS_PREVIEW")
        .map { it.equals("true", ignoreCase = true) }
        .getOrElse(false)
val windowsSignTool = providers.environmentVariable("IPTVBURO_SIGNTOOL").orNull
val windowsCertificateThumbprint =
    providers.environmentVariable("IPTVBURO_WINDOWS_CERT_THUMBPRINT").orNull
val windowsTimestampUrl = providers.environmentVariable("IPTVBURO_TIMESTAMP_URL").orNull
val verifyWindowsReleaseSigning by tasks.registering {
    group = "verification"
    description = "Requires the protected signing pipeline or an explicit unsigned-preview opt-in."
    doLast {
        val requiredVariables =
            listOf(
                "IPTVBURO_SIGNTOOL",
                "IPTVBURO_WINDOWS_CERT_THUMBPRINT",
                "IPTVBURO_TIMESTAMP_URL",
            )
        val signedPipelineReady =
            System.getenv("IPTVBURO_WINDOWS_SIGNING_PIPELINE").equals("true", ignoreCase = true) &&
                requiredVariables.all { !System.getenv(it).isNullOrBlank() }
        val unsignedPreviewAllowed =
            System.getenv("IPTVBURO_ALLOW_UNSIGNED_WINDOWS_PREVIEW").equals("true", ignoreCase = true)
        require(signedPipelineReady || unsignedPreviewAllowed) {
            "Windows MSI packaging requires scripts/sign-windows-release.ps1 or the explicit " +
                "IPTVBURO_ALLOW_UNSIGNED_WINDOWS_PREVIEW=true preview opt-in."
        }
    }
}
tasks.matching { it.name in setOf("packageMsi", "packageReleaseMsi") }.configureEach {
    dependsOn(verifyWindowsReleaseSigning)
    notCompatibleWithConfigurationCache("Authenticode signing uses the protected Windows certificate store.")
    doFirst {
        if (windowsUnsignedPreview) {
            logger.warn("Building an unsigned Windows preview. Windows may display an unknown-publisher warning.")
            return@doFirst
        }
        require(
            windowsSignedPipeline &&
                !windowsSignTool.isNullOrBlank() &&
                !windowsCertificateThumbprint.isNullOrBlank() &&
                !windowsTimestampUrl.isNullOrBlank(),
        ) {
            "Windows release signing is not configured. Use scripts/sign-windows-release.ps1."
        }
        val launcher =
            layout.buildDirectory.file("compose/binaries/main/app/IPTVBURO/IPTVBURO.exe")
                .get().asFile
        require(launcher.isFile) { "The Windows launcher was not generated before packaging." }
        providers.exec {
            commandLine(
                windowsSignTool,
                "sign", "/fd", "SHA256", "/sha1", windowsCertificateThumbprint,
                "/s", "My", "/tr", windowsTimestampUrl, "/td", "SHA256",
                launcher.absolutePath,
            )
        }.result.get().assertNormalExitValue()
        providers.exec {
            commandLine(windowsSignTool, "verify", "/pa", "/all", launcher.absolutePath)
        }.result.get().assertNormalExitValue()
    }
}

tasks.test {
    useJUnitPlatform()
    // Gradle's own -D properties do not reach the forked test JVM, so the live-updater probe
    // opt-in has to be forwarded explicitly.
    System.getProperty("buroLiveUpdaterProbe")?.let { value ->
        systemProperty("buroLiveUpdaterProbe", value)
    }
    // Same opt-in shape: a probe that talks to the machine's own configured provider, so it must
    // never run as part of an ordinary suite.
    System.getProperty("buroSeriesProbe")?.let { value ->
        systemProperty("buroSeriesProbe", value)
    }
    // Talks to the production licence server with this machine's identity, to tell apart the four
    // causes of "could not verify your licence" that the shipping client deliberately conflates.
    System.getProperty("buroLicenceProbe")?.let { value ->
        systemProperty("buroLicenceProbe", value)
    }
    testLogging { showStandardStreams = true }
}
