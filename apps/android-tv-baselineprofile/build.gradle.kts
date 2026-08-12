/*
 * Generates the start-up profile for :apps:android-tv.
 *
 * A separate module because a baseline profile is produced by *driving the real app on a real
 * device* and recording which classes and methods the run touches. That needs the macrobenchmark
 * plugin and UI Automator, neither of which belongs in the shipped APK.
 *
 * Nothing here is part of a release build. The output — `app/src/main/baseline-prof.txt` — is, and
 * it is generated on demand rather than on every build: see the module README for the command.
 */
// No Kotlin plugin applied here, matching :apps:android-tv — AGP 9 brings Kotlin support with it,
// and adding the standalone plugin on top conflicts with the version AGP already contributes.
plugins {
    // Applied without a version: AGP is already on the build classpath via :apps:android-tv, and
    // restating the version makes Gradle refuse the request as unverifiable.
    id("com.android.test")
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "com.lucasserafin94.iptvburo.baselineprofile"
    compileSdk = 36

    defaultConfig {
        // 28 is the floor for the profile *generator*, not for the app: Android only supports
        // installing a baseline profile from P onwards. The app itself still ships minSdk 23, and
        // on older devices the profile is simply absent rather than a problem.
        minSdk = 28
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    targetProjectPath = ":apps:android-tv"
}

baselineProfile {
    // One run per generation invocation. The default is several, which makes sense for a benchmark
    // measuring variance; here the output is a *union* of classes touched, so repeats mostly cost
    // wall-clock time on the phone.
    //
    // Raised deliberately if the home ever loads different rails on different runs: more iterations
    // then widen the recorded set rather than sharpening a number.
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.test.junit)
    implementation(libs.androidx.test.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}
