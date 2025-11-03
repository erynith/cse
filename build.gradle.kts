// This file is in the root directory.

// ... existing code ...

// Add this block if it doesn't exist, or ensure it contains 'mavenCentral()' and 'jitpack.io'
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    // ... existing dependencies block ...
}

allprojects {
    repositories {
        google()
        mavenCentral()
        // CRITICAL FIX: The CloudStream library is hosted on JitPack.
        // This MUST be present for your dependencies to resolve.
        maven("https://jitpack.io")
    }
}
