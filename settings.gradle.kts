// This file must be in the root directory.

pluginManagement {
    repositories {
        // CRITICAL FIX: The repository where the *plugin* is located must be here.
        maven("https://jitpack.io") 
        google()
        mavenCentral()
    }
}

// Defines all modules in your project
rootProject.name = "CloudstreamProvider"
include(":ExampleProvider")
