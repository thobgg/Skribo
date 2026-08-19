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
// desktop/ — Planungs-Client (Compose Multiplatform, Linux/Windows/macOS)
include(":shared")
include(":android")
include(":desktop")
