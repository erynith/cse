buildscript {
    repositories {
        // CRITICAL: JitPack must be here to find the provider plugin artifact
        maven { url = uri("https://jitpack.io") }
        google()
        mavenCentral()
    }
    dependencies {
        // Defines the provider plugin on the classpath using the correct JitPack coordinates
        classpath("com.github.Lagradost:CloudStream-3:4.0.0")

        // Standard build plugins
        classpath("com.android.tools.build:gradle:8.5.1")
        classpath(kotlin("gradle-plugin", version = "1.9.24"))
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
    }
}
