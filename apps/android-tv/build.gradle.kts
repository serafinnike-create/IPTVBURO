import groovy.json.JsonSlurper

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

val androidPlatformCapabilities =
    JsonSlurper().parseText(
        providers.fileContents(
            rootProject.layout.projectDirectory.file(
                "packages/platform-capabilities/android-adaptive.json",
            ),
        ).asText.get(),
    )
        as? Map<*, *> ?: error("Android platform capabilities must be a JSON object")
val androidOfflineCapabilities =
    androidPlatformCapabilities["offline"] as? Map<*, *>
        ?: error("Android platform capabilities must declare offline")
val androidOfflineSupported =
    androidOfflineCapabilities["supported"] as? Boolean
        ?: error("Android platform capabilities must declare offline.supported")

/**
 * TMDB key baked into the build, read from `local.properties` exactly as the desktop does.
 *
 * Deliberately not a source constant: this repository is public, and a key committed to it is
 * scraped and revoked within days. Empty when the file has no entry, in which case the app behaves
 * as if no key were configured and the user can paste their own in settings.
 */
val bundledTmdbKey: String =
    providers
        .fileContents(rootProject.layout.projectDirectory.file("local.properties"))
        .asText
        .map { text ->
            text.lineSequence()
                .firstOrNull { line -> line.trimStart().startsWith("tmdb.apiKey=") }
                ?.substringAfter("=")
                ?.trim()
                .orEmpty()
        }.getOrElse("")

// Release credentials are supplied only by the protected build environment. An unsigned debug
// APK remains available to developers, but production tasks are gated below so an unsigned AAB or
// APK cannot accidentally be called an official IPTV BURO release.
val releaseKeystorePath = providers.environmentVariable("IPTVBURO_ANDROID_KEYSTORE").orNull?.trim()
val releaseKeyAlias = providers.environmentVariable("IPTVBURO_ANDROID_KEY_ALIAS").orNull?.trim()
val releaseStorePassword = providers.environmentVariable("IPTVBURO_ANDROID_STORE_PASSWORD").orNull
val releaseKeyPassword = providers.environmentVariable("IPTVBURO_ANDROID_KEY_PASSWORD").orNull
val releaseSigningComplete =
    listOf(releaseKeystorePath, releaseKeyAlias, releaseStorePassword, releaseKeyPassword)
        .all { !it.isNullOrBlank() }

android {
    namespace = "com.lucasserafin94.iptvburo"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.lucasserafin94.iptvburo"
        minSdk = 23
        targetSdk = 36
        versionCode = 6
        versionName = "0.2.0-alpha.6"

        buildConfigField("boolean", "OFFLINE_SUPPORTED", androidOfflineSupported.toString())
        buildConfigField("String", "BUNDLED_TMDB_KEY", "\"$bundledTmdbKey\"")
        buildConfigField("String", "GOOGLE_PLAY_PRODUCT_ID", "\"iptvburo_730_days\"")
        buildConfigField("String", "GOOGLE_PLAY_PURCHASE_OPTION_ID", "\"rent_730_days\"")
        buildConfigField("String", "GOOGLE_PLAY_RENTAL_PERIOD", "\"P730D\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    signingConfigs {
        if (releaseSigningComplete) {
            create("release") {
                storeFile = file(requireNotNull(releaseKeystorePath))
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
                enableV4Signing = true
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            if (releaseSigningComplete) signingConfig = signingConfigs.getByName("release")
            // R8 was disabled, so the shipping build carried every unused class and method from
            // Compose, Media3, Room and Hilt. Enabling it is the single largest win available on
            // install size and class-loading time at startup.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    // The app switches among PT-BR, EN, DE and IT at runtime. Keep every shipped locale in the
    // base bundle so a language selected inside the app is never missing after an AAB install.
    bundle {
        language {
            enableSplit = false
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "META-INF/DEPENDENCIES",
        )
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    sourceSets.getByName("androidTest").assets.srcDir("$projectDir/schemas")
}

val verifyReleaseSigning by tasks.registering {
    group = "verification"
    description = "Fails before release compilation when production signing is unavailable."
    doLast {
        val requiredVariables =
            listOf(
                "IPTVBURO_ANDROID_KEYSTORE",
                "IPTVBURO_ANDROID_KEY_ALIAS",
                "IPTVBURO_ANDROID_STORE_PASSWORD",
                "IPTVBURO_ANDROID_KEY_PASSWORD",
            )
        require(requiredVariables.all { !System.getenv(it).isNullOrBlank() }) {
            "Release signing is required. Set IPTVBURO_ANDROID_KEYSTORE, " +
                "IPTVBURO_ANDROID_KEY_ALIAS, IPTVBURO_ANDROID_STORE_PASSWORD and " +
                "IPTVBURO_ANDROID_KEY_PASSWORD in the protected build environment."
        }
    }
}
tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    dependsOn(verifyReleaseSigning)
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    implementation(project(":packages:domain-model"))
    implementation(project(":packages:playlist-parser"))
    implementation(project(":packages:metadata-client"))
    implementation(project(":packages:stalker-client"))
    implementation(project(":packages:xtream-client"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.tv.material)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.media3.common)
    implementation(libs.androidx.media3.datasource.okhttp)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.ui)
    implementation(libs.okhttp)
    implementation(libs.gson)
    implementation(libs.bouncycastle.provider)
    implementation(libs.google.play.billing)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.kotlinx.coroutines.android)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.junit)
}
