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
        // jMRTD / SCUBA are published to the EJBCA/JMRTD repository.
        maven("https://repo.jmrtd.org") {
            content { includeGroupByRegex("org\\.jmrtd.*|net\\.sf\\.scuba.*") }
        }
    }
}

rootProject.name = "PodPassportScanner"
include(":app")
