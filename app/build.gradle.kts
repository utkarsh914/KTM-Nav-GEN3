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

        // Navigation SDK / Places API key, read from local.properties (gitignored).
        // Applied at runtime via NavigationApi.setApiKey() rather than a manifest
        // meta-data, so a KEYLESS build compiles and runs the notification
        // fallback with nothing to fail on. Empty string = no key = fallback only.
        buildConfigField(
            "String",
            "NAV_SDK_API_KEY",
            "\"${localProps.getProperty("NAV_SDK_API_KEY", "")}\"",
        )

        // Size lever (minify stays off - see buildTypes). The Navigation SDK
        // ships native libs for every ABI; the app targets modern 64-bit
        // bikes/phones only. (Locale stripping is in androidResources below.)
        ndk { abiFilters += "arm64-v8a" }

        // The bundled Maps (MapView, used by the map-pin picker) reads its key
        // from this manifest meta-data. Same key as NAV_SDK_API_KEY; empty in a
        // keyless build (the map UI is gated on the key being present anyway).
        manifestPlaceholders["MAPS_API_KEY"] = localProps.getProperty("NAV_SDK_API_KEY", "")
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
    // Size lever: the Navigation SDK ships strings for every locale; the app
    // authors English-only, so drop the rest. (Modern replacement for the
    // deprecated defaultConfig.resourceConfigurations.)
    androidResources {
        localeFilters += listOf("en")
    }
    // composeOptions.kotlinCompilerExtensionVersion is obsolete under Kotlin 2.x;
    // the Compose compiler now comes from the org.jetbrains.kotlin.plugin.compose plugin.
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        // 16 KB page-size compliance: store the bundled .so uncompressed and
        // page-aligned in the APK (AGP 8.13 aligns to 16 KB). This is the APK-side
        // half of the fix - the ELF LOAD segments are aligned by the libs
        // themselves (Nav SDK / LiteRT / graphics-path are all 16 KB). It also
        // keeps the large native libs (Nav SDK ~9 MB, LiteRT ~4 MB) out of the
        // packager's deflate path, which is what OOM'd packageDebug.
        jniLibs {
            useLegacyPackaging = false
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

// The Navigation SDK bundles the Google Maps SDK, and the two cannot coexist.
// Strip play-services-maps from ALL configurations so no transitive dependency
// drags it back in (required by the Nav SDK setup guide).
configurations.all {
    exclude(group = "com.google.android.gms", module = "play-services-maps")
}

dependencies {
    // Google Navigation SDK for Android: runs Google's own turn-by-turn engine
    // in-app and streams a NavInfo/StepInfo feed we normalise for the KTM dash.
    // Compiled in unconditionally; activated at runtime only when a key + Play
    // Services are present (see provider selection in the revamp plan).
    implementation("com.google.android.libraries.navigation:navigation:7.9.0")

    // GoogleApiAvailability / ConnectionResult for the Play-services runtime check.
    // The Nav SDK bundles play-services-basement but not -base, so add it explicitly.
    implementation("com.google.android.gms:play-services-base:18.7.2")

    // On-device maneuver-icon classifier (ported from the KTM Gen-3 companion
    // app's approach): a small TFLite CNN recognises the nav app's turn-icon
    // bitmap and outputs the dash turn-icon code.
    //
    // LiteRT is Google's continuation of TensorFlow Lite (org.tensorflow:tensorflow-lite
    // is frozen at 2.14.0). It's a drop-in: same libtensorflowlite_jni.so and the same
    // org.tensorflow.lite.Interpreter API (Interpreter(ByteBuffer) + run()), so
    // ManeuverClassifier is unchanged. The reason for the move is 16 KB page-size
    // compliance: TFLite 2.14.0's .so has 4 KB-aligned ELF LOAD segments (align 2**12),
    // which won't load on Android 15+ 16 KB-page devices; LiteRT's .so is 16 KB-aligned
    // (align 2**14). All other bundled natives (Nav SDK libgmm-jni, graphics-path) are
    // already 16 KB-aligned, and AGP 8.13 page-aligns the uncompressed .so in the APK.
    implementation("com.google.ai.edge.litert:litert:1.4.2")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-service:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    // Provides androidx.lifecycle.compose.LocalLifecycleOwner - the non-deprecated
    // home of the composition local (moved out of androidx.compose.ui.platform).
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")

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

    // JVM unit tests for the pure-Kotlin navigation core (model, formatters,
    // maneuver map, encoder) - no Android/SDK dependencies, run on the JVM.
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
}
