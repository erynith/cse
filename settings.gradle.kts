// This file must be in the root directory.

pluginManagement {
    repositories {
        // CRITICAL FIX: Google and Maven Central MUST be here to find the Android plugins (com.android.application)
        google()
        mavenCentral()
        // The CloudStream provider plugin is also found on JitPack, include it here
        maven("https://jitpack.io")
        gradlePluginPortal()
    }
}

// Defines all modules in your project
rootProject.name = "CloudstreamProvider"
include(":ExampleProvider")
