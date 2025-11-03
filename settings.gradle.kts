pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()
        maven {
            name = "cloudstream"
            url = uri("https://maven.pkg.github.com/recloudstream/cloudstream")
            credentials {
                username = System.getenv("CS_USERNAME")
                password = System.getenv("CS_TOKEN")
            }
        }
    }
}

rootProject.name = "FLUMMOX-Repo"
include("ExampleProvider")
include("GhostStreamProvider")
