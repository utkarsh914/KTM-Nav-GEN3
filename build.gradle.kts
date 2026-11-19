plugins {
    // AGP 9 compiles Kotlin itself (built-in Kotlin), so there is no separate
    // org.jetbrains.kotlin.android plugin any more.
    id("com.android.application") version "9.5.1" apply false
    // Kotlin 2.0+ moves the Compose compiler into its own plugin, versioned in
    // lockstep with Kotlin (replaces composeOptions.kotlinCompilerExtensionVersion).
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
}
