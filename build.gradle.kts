// Root level build.gradle.kts - simplified

plugins {
    // Define the base versions for Android and Kotlin
    id("com.android.application") version "8.5.1" apply false
    id("com.android.library") version "8.5.1" apply false
    kotlin("android") version "1.9.24" apply false
    id("com.lagradost.cloudstream3.provider") version "1.4.2" apply false
}

// Define repositories for dependencies (not plugins)
allprojects {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}
