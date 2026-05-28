pluginManagement {
    plugins {
        // Declare the Kotlin JVM plugin version once for the whole build
        id("org.jetbrains.kotlin.jvm") version "2.0.21"
    }
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
        mavenLocal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        mavenLocal()
    }
}

rootProject.name = "kosherjava-compute-engine"
include(":core-engine", ":profiles", ":rest-api")