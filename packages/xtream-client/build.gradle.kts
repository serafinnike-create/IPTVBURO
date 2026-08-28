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
    api(libs.okhttp)
    implementation(libs.gson)
    implementation(libs.kotlinx.serialization.json)
    // The XMLTV guide parser: a provider's xtream.php EPG is standard XMLTV, and this package
    // already has a streaming, XXE-hardened parser for exactly that format.
    implementation(project(":packages:playlist-parser"))

    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
}
