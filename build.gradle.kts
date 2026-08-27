// Top-level build file — no code here, only shared plugin configuration
plugins {
    alias(libs.plugins.android.application)  apply false
    alias(libs.plugins.android.library)      apply false
    alias(libs.plugins.kotlin.android)       apply false
    alias(libs.plugins.kotlin.jvm)           apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.compose)       apply false
    alias(libs.plugins.ksp)                  apply false
    alias(libs.plugins.hilt)                 apply false
    alias(libs.plugins.google.services)      apply false
    alias(libs.plugins.firebase.crashlytics) apply false
    alias(libs.plugins.detekt)               apply false
    alias(libs.plugins.ktlint)               apply false
}

// Force AndroidX flags at the project-extension level as a safeguard,
// in case gradle.properties / -P command-line properties aren't being
// picked up by this AGP version's dependency check.
allprojects {
    extra.set("android.useAndroidX", true)
    extra.set("android.enableJetifier", false)
}

// Detekt for all subprojects
subprojects {
    apply(plugin = "io.gitlab.arturbosch.detekt")
    configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
        config.setFrom(files("$rootDir/config/detekt.yml"))
        buildUponDefaultConfig = true
    }
}
