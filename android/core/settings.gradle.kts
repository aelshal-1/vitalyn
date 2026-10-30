// Lets the pure-Kotlin core build and test on its own (`gradle -p android/core test`),
// without the Android SDK. Inside the full build, android/settings.gradle.kts includes it as :core.
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
    plugins {
        kotlin("jvm") version "2.0.21"
        kotlin("plugin.serialization") version "2.0.21"
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

rootProject.name = "core"
