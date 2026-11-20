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

/** Output of a git command run in the repo root, or null when git/the repo is unavailable. */
// providers.exec rather than ProcessBuilder: Gradle 9 deprecates starting raw processes at
// configuration time (an error in Gradle 11).
fun gitOutput(vararg args: String): String? = runCatching {
    val exec = providers.exec {
        commandLine(listOf("git") + args)
        workingDir = rootProject.projectDir
        isIgnoreExitValue = true
    }
    if (exec.result.get().exitValue == 0) exec.standardOutput.asText.get().trim() else null
}.getOrNull()

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.navigator.app"
    // 37 is required by Compose 1.12 (BOM 2026.08+) and core-ktx 1.19. It also keeps the
    // 36.1 promoted-ongoing / Live Update notification APIs. targetSdk stays 36 (no Android 17
    // runtime-behavior opt-in yet).
    compileSdk {
        version = release(37) { minorApiLevel = 2 }
    }

    defaultConfig {
        // App identity on the device / Play Store. Kept separate from the code
        // namespace (still com.navigator.app) - Google recommends a stable
        // namespace, and the API key + install identity key off applicationId.
        applicationId = "com.navigator.ktm"
        minSdk = 36
        targetSdk = 36
        // Deterministic and monotonic: 100_000 + the git commit count. The offset keeps it
        // above every older days-since-epoch versionCode (~20_000), so existing installs
        // still upgrade in place. Without git it falls back to the old day-based value.
        // (A shallow clone reports a truncated, too-small count: ignore it rather than risk a downgrade.)
        val shallow = gitOutput("rev-parse", "--is-shallow-repository") == "true"
        val commitCount = if (shallow) null else gitOutput("rev-list", "--count", "HEAD")?.toIntOrNull()
        versionCode = if (commitCount != null) 100_000 + commitCount
        else maxOf(100_000, (System.currentTimeMillis() / 86_400_000L).toInt())
        // Public beta line + the commit it was built from (and "-dirty" for uncommitted
        // changes), so the installed build can be identified (Settings > Diagnostics).
        val shortHash = gitOutput("rev-parse", "--short", "HEAD")
        val dirty = gitOutput("status", "--porcelain")?.isNotBlank() == true
        versionName = "0.6.0+" + (shortHash?.plus(if (dirty) "-dirty" else "")
            ?: SimpleDateFormat("MMdd-HHmm").format(Date()))

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
        // Required by the Navigation SDK AAR.
        isCoreLibraryDesugaringEnabled = true
        // Built-in Kotlin (AGP 9) takes its jvmTarget from targetCompatibility, so
        // Kotlin and Java both emit Java 17 bytecode.
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
        // Stored uncompressed so the TFLite model can be memory-mapped straight from the APK
        // (ManeuverClassifier) instead of being copied onto the heap.
        noCompress += "tflite"
    }
    testOptions {
        // Robolectric (RideStore etc. need a real Context + filesystem) needs merged resources/manifest.
        unitTests.isIncludeAndroidResources = true
    }
    // composeOptions.kotlinCompilerExtensionVersion is obsolete under Kotlin 2.x;
    // the Compose compiler now comes from the org.jetbrains.kotlin.plugin.compose plugin.
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        // 16 KB page-size compliance: store the bundled .so uncompressed and
        // page-aligned in the APK (AGP aligns to 16 KB). This is the APK-side
        // half of the fix - the ELF LOAD segments are aligned by the libs
        // themselves (Nav SDK / LiteRT / graphics-path are all 16 KB). It also
        // keeps the large native libs (Nav SDK ~9 MB, LiteRT ~4 MB) out of the
        // packager's deflate path, which is what OOM'd packageDebug.
        jniLibs {
            useLegacyPackaging = false
        }
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
    implementation("com.google.android.libraries.navigation:navigation:8.1.0")

    // GoogleApiAvailability / ConnectionResult for the Play-services runtime check.
    // The Nav SDK bundles play-services-basement but not -base, so add it explicitly.
    implementation("com.google.android.gms:play-services-base:18.11.1")

    // On-device maneuver-icon classifier (ported from the KTM Gen-3 companion
    // app's approach): a small TFLite CNN recognises the nav app's turn-icon
    // bitmap and outputs the dash turn-icon code.
    //
    // LiteRT is Google's continuation of TensorFlow Lite (org.tensorflow:tensorflow-lite
    // is frozen at 2.14.0). It keeps the org.tensorflow.lite.Interpreter API
    // (Interpreter(ByteBuffer) + run()), so ManeuverClassifier is unchanged; since 2.x
    // the native code ships as libLiteRt.so + liblitert_jni.so. The reason for the move is 16 KB page-size
    // compliance: TFLite 2.14.0's .so has 4 KB-aligned ELF LOAD segments (align 2**12),
    // which won't load on Android 15+ 16 KB-page devices; LiteRT's .so is 16 KB-aligned
    // (align 2**14). All other bundled natives (Nav SDK libgmm-jni, graphics-path) are
    // already 16 KB-aligned, and AGP page-aligns the uncompressed .so in the APK.
    implementation("com.google.ai.edge.litert:litert:2.3.2")
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.appcompat:appcompat:1.8.0")
    implementation("com.google.android.material:material:1.14.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("androidx.lifecycle:lifecycle-service:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    // Provides androidx.lifecycle.compose.LocalLifecycleOwner - the non-deprecated
    // home of the composition local (moved out of androidx.compose.ui.platform).
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")

    // 2026.11.01 is the first BOM whose material3 (1.5.0) exposes MaterialExpressiveTheme and
    // MotionScheme; 2026.09 pinned 1.4.0, where they are internal.
    implementation(platform("androidx.compose:compose-bom:2026.11.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    // Compose rule for the Robolectric smoke test that composes the galleries (manifest hosts the test activity).
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    implementation("androidx.compose.material3:material3")
    // Bar in portrait / rail in landscape for the four-tab shell (version from the BOM).
    implementation("androidx.compose.material3:material3-adaptive-navigation-suite")
    // Type-safe routes (@Serializable) for the app shell.
    implementation("androidx.navigation:navigation-compose:2.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.13.0")
    // HCT colour maths for accent tones (Google's own copy is @RestrictTo, so lint rejects it).
    implementation("com.materialkolor:material-color-utilities:6.0.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")

    // Required by the Navigation SDK AAR (it enables core-library desugaring itself).
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs_nio:2.1.5")

    // JVM unit tests for the pure-Kotlin navigation core (model, formatters,
    // maneuver map, encoder) - no Android/SDK dependencies, run on the JVM.
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
    // Real org.json for JVM unit tests: android.jar ships stubs that throw
    // "not mocked", so tests parsing JSON (e.g. RoutesClient) need the actual impl.
    testImplementation("org.json:json:20260814")
    // Android-framework unit tests on the JVM (RideStore needs a Context + files): no device.
    testImplementation("org.robolectric:robolectric:4.18.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
}
