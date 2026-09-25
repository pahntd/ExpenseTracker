pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // MPAndroidChart is only published on JitPack; restrict JitPack to that artifact.
        maven("https://jitpack.io") {
            content { includeModule("com.github.PhilJay", "MPAndroidChart") }
        }
    }
}

rootProject.name = "Expense Tracker"
include(":app")
 