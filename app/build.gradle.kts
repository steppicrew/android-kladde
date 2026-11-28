import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.play.publisher)
}

/*
 * Signing and publishing secrets live in .env (gitignored, see .env.sample). ./gw exports it;
 * reading the file as well keeps a bare ./gradlew working. Without a keystore (a fresh clone, CI)
 * release falls back to debug signing so the project always builds; Play rejects such a bundle,
 * and ./scripts/check-keystore.sh verifies the real key before a release.
 */
val dotEnv =
    Properties().apply {
        rootProject
            .file(".env")
            .takeIf { it.isFile }
            ?.inputStream()
            ?.use { load(it) }
    }

// Quotes stripped as the shell does when ./gw sources .env, which Properties would keep.
// $HOME is expanded too, as the shell would, for a bare ./gradlew.
fun env(key: String): String? =
    (
        System.getenv(key) ?: dotEnv
            .getProperty(key)
            ?.trim()
            ?.removeSurrounding("\"")
            ?.removeSurrounding("'")
    )?.replace("\$HOME", System.getProperty("user.home"))
        ?.takeIf { it.isNotBlank() }

val releaseKeystore = env("KEYSTORE_PATH")?.let(::file)?.takeIf { it.isFile }

play {
    // Uploads are opt-in: without a service account this stays inert rather than failing.
    val serviceAccount = env("PLAY_SERVICE_ACCOUNT_JSON")
    enabled.set(serviceAccount != null)
    if (serviceAccount != null) serviceAccountCredentials.set(file(serviceAccount))
    defaultToAppBundles.set(true)
    // Internal unless overridden (-Pplay.track=production), so a bare publish never goes public.
    track.set(providers.gradleProperty("play.track").orElse("internal"))
}

android {
    namespace = "de.steppicrew.kladde"
    compileSdk {
        // 37.1 or later: current AndroidX (Compose 1.13) needs it.
        version = release(37) { minorApiLevel = 2 }
    }

    defaultConfig {
        applicationId = "de.steppicrew.kladde"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = env("KEYSTORE_PASSWORD")
                keyAlias = env("KEY_ALIAS")
                keyPassword = env("KEY_PASSWORD") ?: env("KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // Required by ADX (java.time below API 26 in its own minSdk).
        isCoreLibraryDesugaringEnabled = true
    }

    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":integration:adx"))
    implementation(project(":feature:routines"))
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.compose)
    debugImplementation(libs.compose.ui.tooling)
}
