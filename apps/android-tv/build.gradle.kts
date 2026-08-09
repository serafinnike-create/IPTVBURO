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

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
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

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(project(":packages:domain-model"))
    implementation(project(":packages:playlist-parser"))
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
