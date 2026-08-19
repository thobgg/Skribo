pluginManagement {
    repositories {
        google()
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

rootProject.name = "Skribo"

// shared/  — plattformfreier Kern (Modell, Schema, Sync, Strich-Mathematik)
// android/ — Board-Client für die CTOUCH-Boards
// desktop/ — Planungs-Client (Compose Multiplatform), folgt in M3
include(":shared")
include(":android")
