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
    }
}

rootProject.name = "Spark"

// App
include(":app")

// Core modules
include(":core:model")
include(":core:network")
include(":core:database")
include(":core:auth")
include(":core:ui")

// Feature modules
include(":feature:auth")
include(":feature:onboarding")
include(":feature:profile")
include(":feature:discovery")
include(":feature:matching")
include(":feature:chat")
include(":feature:notifications")
include(":feature:settings")
include(":feature:subscription")
include(":feature:safety")
