plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-library`
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    // The streaming catalogue speaks in domain types (ExternalTitle, StreamingOffer) so both
    // platforms consume one implementation instead of each mapping TMDb responses themselves.
    api(project(":packages:domain-model"))
    api(libs.okhttp)
    implementation(libs.gson)

    testImplementation(kotlin("test"))
    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
}
