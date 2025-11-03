pluginManagement {
    repositories {
        google()
        mavenCentral()
        // CRITICAL: Adding the repository and credentials for the plugin
        maven {
            name = "cloudstream"
            url = uri("https://api.github.com/repos/recloudstream/cloudstream-3/packages/maven")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "FLUMMOX-Repo"
include("ExampleProvider")
