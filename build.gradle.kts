// Root level build.gradle.kts - FINAL WORKING VERSION

// 1. Define where to find the *plugins* using the reliable buildscript method
buildscript {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
    dependencies {
        // FINAL FIX: Changing version to 'master-SNAPSHOT' to bypass 401 resolution error
        classpath("com.github.Lagradost:CloudStream-3:master-SNAPSHOT") 
        classpath(kotlin("gradle-plugin", version = "1.9.24"))
        classpath("com.android.tools.build:gradle:8.5.1")
    }
}

// 2. Define the repositories for *dependencies* (libraries like material, okhttp)
allprojects {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}
