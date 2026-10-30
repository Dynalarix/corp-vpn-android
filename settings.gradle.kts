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

rootProject.name = "corp-vpn-android"

include(":core")
include(":app")

// AmneziaWG tunnel library (WireGuard + AmneziaWG), built from source. It is not
// published as a package. Pinned as a git submodule.
include(":tunnel")
project(":tunnel").projectDir = file("third_party/amneziawg-android/tunnel")
