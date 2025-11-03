allprojects {
    repositories {
        google()
        mavenCentral()
    }
}

// CRITICAL: Adding GitHub credentials for all projects
subprojects {
    repositories {
        maven {
            name = "cloudstream"
            url = uri("https://api.github.com/repos/recloudstream/cloudstream-3/packages/maven")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }
}
