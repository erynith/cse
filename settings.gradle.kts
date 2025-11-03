// This file must be in the root directory.

pluginManagement {
    repositories {
        // CRITICAL: Tells Gradle where to find the Android/Kotlin plugins
        google()
        mavenCentral()
        // Tells Gradle where to find the CloudStream provider plugin
        maven("https://jitpack.io")
    }
}

// Defines all modules in your project
rootProject.name = "CloudstreamProvider"
include(":ExampleProvider")
