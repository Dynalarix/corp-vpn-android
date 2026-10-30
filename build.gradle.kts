// Plugins are declared once here so every module shares the same plugin classes.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}

// The tunnel module normally gets these from its own repo's settings plugin.
project(":tunnel") {
    pluginManager.withPlugin("com.android.library") {
        apply(from = rootProject.file("gradle/tunnel-android.gradle"))
    }
}
