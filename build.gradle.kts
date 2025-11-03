plugins {
    kotlin("jvm") version "1.8.20" // Reverting to a slightly older, known stable Kotlin version
}

group = "com.github.YOUR_GITHUB_USERNAME"
version = "1.0.0"

repositories {
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
}

dependencies {
    // --- CRUCIAL FIX: Switching to the officially maintained repository's dependency ---
    // The previous dependency was failing with 401 Unauthorized.
    // We are now using the main repository's master-SNAPSHOT version, which should
    // contain the latest, buildable version of the library.
    // Note: The structure is 'com.github.User:Repo:Version'
    // 'recloudstream' is the user, 'cloudstream' is the main repo, and 'master-SNAPSHOT' is the latest buildable commit.
    implementation("com.github.recloudstream:cloudstream:master-SNAPSHOT")
    
    // Add other necessary dependencies if needed (e.g., coroutines, JSON parsing)
    // The Cloudstream dependency usually handles OkHttp, Gson, and Coroutines internally,
    // but having the Kotlin coroutines dependency is standard practice for providers.
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(11) // Ensuring we use Java 11, standard for Android/JVM projects
}

// Configuration to build the .apk (this is usually handled by the main project structure,
// but included for completeness if the build is meant to run in a standalone context)
tasks.register("buildPlugin") {
    dependsOn(tasks.jar)
    doLast {
        println("Plugin JAR built: ${tasks.jar.get().archiveFile.get().asFile}")
    }
}
