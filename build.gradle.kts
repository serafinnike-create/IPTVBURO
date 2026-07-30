buildscript {
    dependencies {
        // AGP 9 ships Kotlin 2.2.10. Pin the compatible newer compiler for
        // built-in Kotlin without applying org.jetbrains.kotlin.android.
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.21")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.ksp) apply false
}
