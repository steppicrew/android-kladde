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

// ADX is built from source; its artifacts replace the de.steppicrew.adx dependencies.
// Where it lives comes from ADX_DIR in the environment or in .env (see .env.sample).
val adxDir =
    System.getenv("ADX_DIR")
        ?: file(".env")
            .takeIf { it.isFile }
            ?.readLines()
            ?.map { it.trim() }
            ?.firstOrNull { it.startsWith("ADX_DIR=") }
            ?.substringAfter('=')
            ?.trim('"', '\'')
        ?: "../android-apx"
includeBuild(adxDir)

include(":app")
include(":core:model")
include(":core:data")
include(":integration:adx")
include(":feature:routines")
