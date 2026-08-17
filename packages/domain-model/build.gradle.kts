plugins {
    // Applied by id without a version, deliberately. The root `buildscript` already puts the Kotlin
    // Gradle plugin on the classpath — pinned there for AGP 9 — so requesting a version here fails
    // with "already on the classpath with an unknown version". The version comes from that pin.
    id("org.jetbrains.kotlin.multiplatform")
}

/**
 * The shared domain, compiled for the JVM and for iOS.
 *
 * Multiplatform rather than `kotlin.jvm`, which is what this was: the rules in here — what a title
 * *is*, when a licence expires, what a reminder is waiting on — are the same rules on every
 * platform, and an iPhone target cannot link a JVM library. The apps keep their own plugins; only
 * the packages below are expected to leave the JVM.
 *
 * Android and Windows consume the `jvm` target and see no difference: same artefacts, same
 * behaviour. That is deliberate — this conversion must not be a rewrite of shipping code.
 */
kotlin {
    jvm {
        compilerOptions {
            jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
        }
    }

    // The three iOS architectures: device, and both simulator kinds. A Mac is still required to
    // *build* these — the declaration is what makes the code compile-checked for them.
    iosArm64()
    iosX64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.datetime)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        jvmTest.dependencies {
            implementation(libs.junit)
        }
    }
}
