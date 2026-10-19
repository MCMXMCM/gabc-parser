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
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "gabc-parser-kotlin"

// The Swift package's libraries, as Kotlin modules: GABCParser, GABCLayout,
// GABCLyricFont and GABCSwiftUI.
include(":gabc-parser", ":gabc-layout", ":gabc-lyric-font", ":gabc-compose")
