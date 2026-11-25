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

rootProject.name = "android-kladde"

// ADX is built from source next to this repo; its artifacts replace the de.steppicrew.adx dependencies.
includeBuild("../app-adx")

include(":app")
include(":core:model")
include(":core:data")
include(":integration:adx")
