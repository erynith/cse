dependencies {
    // FIX: Replaced project(":app") with the external dependency variable,
    // which is the standard way to reference the CloudStream core library 
    // in a detached provider repository.
    implementation(rootProject.extra.get("cloudstream_lib_dependency") as String)

    // Standard Android libraries
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
}

// Use an integer for version numbers
version = 1

cloudstream {
    description = "A multi-source video provider"
    authors = listOf("FlummoxGamer")

    /**
    * Status int as one of the following: 0: Down, 1: Ok, 2: Slow, 3: Beta-only
    **/
    status = 1 

    tvTypes = listOf(
        "Movie",
        "TvSeries",
        "Anime"
    )

    requiresResources = false
    language = "en"

    iconUrl = "https://upload.wikimedia.org/wikipedia/commons/2/2f/Korduene_Logo.png"
}

android {
    buildFeatures {
        buildConfig = true
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
