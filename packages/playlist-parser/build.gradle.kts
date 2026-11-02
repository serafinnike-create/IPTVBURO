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
    api(project(":packages:domain-model"))
    // The XmlPull interface only. Android provides the implementation in the platform, and
    // bundling one there would clash with it; JVM consumers add kXML2 at runtime instead.
    compileOnly(libs.xmlpull.api)

    testImplementation(project(":packages:test-fixtures"))
    testImplementation(libs.junit)
    testRuntimeOnly(libs.kxml2)
}
