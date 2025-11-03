pluginManagement {
    repositories {
        google()
        mavenCentral()
        // CRITICAL: This is for the PLUGIN (com.lagacy.cloudstream.plugin)
        // It needs the correct path suffix: /maven/cloudstream
        maven {
            name = "cloudstream"
            url = uri("https://api.github.com/repos/recloudstream/cloudstream-3/packages/maven/cloudstream")
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
