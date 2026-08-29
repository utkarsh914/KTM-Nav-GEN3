import java.text.SimpleDateFormat
import java.util.Date
import java.util.Properties

// Release signing credentials live in local.properties (gitignored) so the
// keystore identity stays stable across builds - a consistent, non-debug
// signature is what keeps Play Protect from hard-blocking sideloaded installs.
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.navigator.app"
    compileSdk = 36

    defaultConfig {
        // App identity on the device / Play Store. Kept separate from the code
        // namespace (still com.navigator.app) - Google recommends a stable
        // namespace, and the API key + install identity key off applicationId.
        applicationId = "com.navigator.ktm"
        minSdk = 26
        targetSdk = 36
        // Navigation SDK (Phase 2) pushes the method count past 64K; enable now
        // so the toolchain state is final before the SDK lands.
        multiDexEnabled = true
        // Days-since-epoch: monotonically increasing across builds, so every
        // new APK is a clean in-place update (Play Protect also treats a
        // never-incrementing versionCode as a suspicion signal).
        versionCode = (System.currentTimeMillis() / 86_400_000L).toInt()
        // Public beta line, with a build stamp appended so it's easy to confirm
        // which build is actually installed on-device (Settings > Diagnostics).
        versionName = "0.2.0-beta+" + SimpleDateFormat("MMdd-HHmm").format(Date())
    }

    signingConfigs {
        create("release") {
            localProps.getProperty("RELEASE_STORE_FILE")?.let { path ->
                storeFile = file(path)
                storePassword = localProps.getProperty("RELEASE_STORE_PASSWORD")
                keyAlias = localProps.getProperty("RELEASE_KEY_ALIAS")
                keyPassword = localProps.getProperty("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            // No minification: TFLite + reflection-heavy BLE code isn't worth
            // proguard risk for a sideloaded app; signing is what matters here.
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        // Required by the Navigation SDK (Phase 2); harmless to enable now.
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    // composeOptions.kotlinCompilerExtensionVersion is obsolete under Kotlin 2.x;
    // the Compose compiler now comes from the org.jetbrains.kotlin.plugin.compose plugin.
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// Kotlin 2.x: jvmTarget moved out of android.kotlinOptions into the top-level
// compilerOptions DSL. Matches the Java 17 bytecode target in compileOptions above.
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // On-device maneuver-icon classifier (ported from the KTM Gen-3 companion
    // app's approach): a small TFLite CNN recognises the nav app's turn-icon
    // bitmap and outputs the dash turn-icon code.
    implementation("org.tensorflow:tensorflow-lite:2.14.0")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-service:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")

    implementation(platform("androidx.compose:compose-bom:2026.06.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Backports java.time/java.nio APIs the Navigation SDK relies on to minSdk 26.
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs_nio:2.1.5")
}
