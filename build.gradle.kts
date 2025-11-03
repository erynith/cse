// Root level build.gradle.kts - defines global versions.

plugins {
    // Standard Android and Kotlin plugins
    id("com.android.application") version "8.5.1" apply false
    id("com.android.library") version "8.5.1" apply false
    kotlin("android") version "1.9.24" apply false
    
    // CloudStream provider plugin (version now ignored due to settings.gradle.kts fix)
    id("com.lagradost.cloudstream3.provider") version "4.0.0" apply false
}

// Define the repositories for *dependencies* (libraries used by the code).
allprojects {
    repositories {
        google()
        mavenCentral()
    }
}
