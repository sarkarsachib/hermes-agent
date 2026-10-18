pluginManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "HermesAgent"

include(":android")
project(":android").projectDir = file("apps/android")