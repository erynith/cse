import org.jetbrains.kotlin.konan.properties.Properties

// use an integer for version numbers
version = 1

android {
    buildFeatures {
        buildConfig = true
        viewBinding = true
    }
    defaultConfig {
        val properties = Properties()
        properties.load(project.rootProject.file("local.properties").inputStream())
        android.buildFeatures.buildConfig=true
        buildConfigField("String", "TMDB_API", "\"${properties.getProperty("TMDB_API")}\"")
    }
}

dependencies {
    implementation("com.google.android.material:material:1.13.0")
}

cloudstream {
    language = "en"

     description = "Use extension settings to paste the stremio addon manifest url"
     authors = listOf("Hexated,phisher98,erynith")

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
     * */
    status = 1 // will be 3 if unspecified
    tvTypes = listOf(
        "TvSeries",
        "Movie",
    )
    requiresResources = true
    iconUrl = "https://files.catbox.moe/ol63rm.png"
}
