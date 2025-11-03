// Provider Module build.gradle.kts
// This file links your code to the CloudStream library.

plugins {
    // Apply the necessary plugins for an Android Library and the CloudStream Provider configuration
    id("com.android.library")
    kotlin("android")
    id("com.lagradost.cloudstream3.provider")
}

android {
    namespace = "com.example.provider" // Use your actual package name here
    compileSdk = 34 // Use a standard recent SDK version

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        // Enforce Java 17 for modern Kotlin/Android development
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
    }
}

dependencies {
    // FIX: Direct dependency on the CloudStream app library (using v4.0.0 as a safe recent version)
    implementation("com.lagradost.cloudstream3:app:4.0.0")

    // Standard Android libraries (Versions matched to your previous request)
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
}

// Provider configuration block
version = 1

cloudstream {
    description = "A multi-source video provider"
    authors = listOf("FlummoxGamer") // FIX: Changed to your username

    /**
    * Status int as one of the following: 0: Down, 1: Ok, 2: Slow, 3: Beta-only
    **/
    status = 1 // 1 = OK

    tvTypes = listOf(
        "Movie",
        "TvSeries",
        "Anime"
    )

    requiresResources = false
    language = "en"

    iconUrl = "https://upload.wikimedia.org/wikipedia/commons/2/2f/Korduene_Logo.png"
}
