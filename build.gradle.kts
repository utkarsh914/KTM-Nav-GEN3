plugins {
    id("com.android.application") version "8.13.2" apply false
    id("org.jetbrains.kotlin.android") version "2.3.0" apply false
    // Kotlin 2.0+ moves the Compose compiler into its own plugin, versioned in
    // lockstep with Kotlin (replaces composeOptions.kotlinCompilerExtensionVersion).
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.0" apply false
}
